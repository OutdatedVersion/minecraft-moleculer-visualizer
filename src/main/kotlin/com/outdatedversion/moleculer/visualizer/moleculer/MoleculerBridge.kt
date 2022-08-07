package com.outdatedversion.moleculer.visualizer.moleculer

import com.google.common.cache.CacheBuilder
import com.google.common.cache.RemovalCause
import com.outdatedversion.moleculer.visualizer.Plugin
import com.outdatedversion.moleculer.visualizer.model.MoleculerRequestData
import com.outdatedversion.moleculer.visualizer.model.MoleculerServiceData
import com.outdatedversion.moleculer.visualizer.moleculer.event.EventEmittedEvent
import com.outdatedversion.moleculer.visualizer.moleculer.event.NodeConnectedEvent
import com.outdatedversion.moleculer.visualizer.moleculer.event.NodeDisconnectedEvent
import com.outdatedversion.moleculer.visualizer.moleculer.event.NodeDiscoveredEvent
import com.outdatedversion.moleculer.visualizer.moleculer.event.NodeDiscoveryEvent
import com.outdatedversion.moleculer.visualizer.moleculer.event.NodeHeartbeatEvent
import com.outdatedversion.moleculer.visualizer.moleculer.event.RequestInitiatedEvent
import com.outdatedversion.moleculer.visualizer.moleculer.event.RequestTimeoutEvent
import com.outdatedversion.moleculer.visualizer.moleculer.event.ResponseEvent
import io.datatree.Tree
import io.nats.client.Connection
import io.nats.client.Message
import io.nats.client.Nats
import org.bukkit.Bukkit
import org.bukkit.configuration.ConfigurationSection
import org.slf4j.LoggerFactory
import services.moleculer.ServiceBroker
import services.moleculer.config.ServiceBrokerConfig
import services.moleculer.context.CallOptions
import services.moleculer.transporter.NatsTransporter
import java.nio.charset.StandardCharsets
import java.util.*
import java.util.concurrent.CompletableFuture
import java.util.concurrent.TimeUnit

class MoleculerBridge(private val plugin: Plugin) {
    private val logger = LoggerFactory.getLogger(MoleculerBridge::class.java)

    private val moleculerRequestCache =
        CacheBuilder.newBuilder()
            .expireAfterWrite(60, TimeUnit.SECONDS)
            .removalListener<String, MoleculerRequestData> {
                if (it.cause != RemovalCause.EXPIRED) {
                    return@removalListener
                }
                val request = it.value!!

                RequestTimeoutEvent(
                    requestId = request.requestId,
                    sourceNodeId = request.sourceNodeId,
                    sourceServiceName = request.sourceServiceName,
                    targetServiceName = request.targetServiceName,
                    targetActionName = request.targetActionName
                ).callEvent()
            }
            .build<String, MoleculerRequestData>()

    private val nodeIdsToServices = mutableMapOf<String, Set<MoleculerServiceData>>()

    // used to supplement data picked up over the NATS wire
    private var broker: ServiceBroker? = null
    private var natsConnection: Connection? = null
    private val trackedNodes = mutableMapOf<String, Boolean>()

    fun connect(config: ConfigurationSection) {
        // TODO(ben): guards on configuration value
        val transportUrl = config.getString("transport.url")!!
        this.logger.info("Connecting to NATS at '{}'", transportUrl)
        this.connectToBroker(transportUrl)
        this.connectToNats(transportUrl)
    }

    fun disconnect() {
        this.moleculerRequestCache.invalidateAll()
        this.nodeIdsToServices.clear()
        this.trackedNodes.clear()

        this.broker?.stop()

        try {
            this.natsConnection?.close()
            this.logger.info("disconnected from NATS")
        } catch (ex: Exception) {
            this.logger.error("Failed to disconnect from NATS", ex)
        }
    }

    fun getActiveNodes(): Set<String> {
        return trackedNodes.keys
    }

    fun getServicesOnNode(nodeId: String): CompletableFuture<Set<MoleculerServiceData>> {
        val svc = this.nodeIdsToServices[nodeId]
        if (svc != null) {
            return CompletableFuture.completedFuture(svc)
        }

        assert(broker != null) { "broker unavailable" }
        return broker!!.call("\$node.services", Tree().put("onlyLocal", true), CallOptions.nodeID(nodeId))
            .toCompletableFuture()
            .thenApply { tree ->
                return@thenApply tree.map { entry ->
                    // fullName includes the version (v1.accounts) whereas `name` does not (accounts)
                    return@map MoleculerServiceData(fullName = entry["fullName"].asString())
                }.toSet()
            }
    }

    private fun connectToNats(url: String) {
        Bukkit.getScheduler().runTaskAsynchronously(this.plugin, Runnable {
            try {
                natsConnection = Nats.connect(url)
                this.logger.info("Connected to NATS!")

                val dispatcher = natsConnection!!.createDispatcher {}
                dispatcher.subscribe("MOL.HEARTBEAT") { message: Message ->
                    val node = Tree(String(message.data, StandardCharsets.UTF_8))
                    val nodeId = node["sender"].asString()

                    NodeHeartbeatEvent(nodeId, Date()).callEvent()
                }

                dispatcher.subscribe("MOL.DISCOVER") { message: Message ->
                    val packet = Tree(String(message.data, StandardCharsets.UTF_8))
                    val nodeId = packet["sender"].asString()

                    NodeDiscoveryEvent(nodeId).callEvent()
                }

                dispatcher.subscribe("MOL.DISCONNECT") { message: Message ->
                    val packet = Tree(String(message.data, StandardCharsets.UTF_8))
                    val nodeId = packet["sender"].asString()

                    this.nodeIdsToServices.remove(nodeId)
                    this.trackedNodes.remove(nodeId)

                    NodeDisconnectedEvent(nodeId).callEvent()
                }

                dispatcher.subscribe("MOL.INFO.*") { message: Message ->
                    val packet = Tree(String(message.data, StandardCharsets.UTF_8))
                    val discoveredNodeId = message.subject.substring("MOL.INFO.".length)
                    val sponsorNodeId = packet["sender"].asString()

                    NodeDiscoveredEvent(
                        discoveredNodeId = discoveredNodeId,
                        sponsorNodeId = sponsorNodeId
                    ).callEvent()
                }

                dispatcher.subscribe("MOL.INFO") { message: Message ->
                    val packet = Tree(String(message.data, StandardCharsets.UTF_8))
                    val nodeId = packet["sender"].asString()
                    val services = packet["services"]

                    this.nodeIdsToServices[nodeId] = services.map {
                        MoleculerServiceData(fullName = it["fullName"].asString())
                    }.toSet()
                    this.trackedNodes.putIfAbsent(nodeId, true)

                    NodeConnectedEvent(nodeId = nodeId).callEvent()
                }

                dispatcher.subscribe("MOL.EVENTB.>") { message: Message ->
                    val event = Tree(String(message.data, StandardCharsets.UTF_8))

                    val sourceNodeId = event["sender"].asString()
                    val sourceServiceName = event["caller"].asString()
                    val eventName = event["event"].asString()

                    EventEmittedEvent(
                        event = eventName,
                        sourceNodeId = sourceNodeId,
                        sourceServiceName = sourceServiceName,
                        timestamp = Date()
                    ).callEvent()
                }

                dispatcher.subscribe("MOL.REQB.>") { message: Message ->
                    try {
                        val request = Tree(String(message.data, StandardCharsets.UTF_8))

                        val requestId = request["id"].asString()
                        val requestAction = request["action"].asString()
                        val serviceName = requestAction.substring(0, requestAction.lastIndexOf("."))
                        val event = RequestInitiatedEvent(
                            requestId = requestId,
                            sourceNodeId = request["sender"].asString(),
                            sourceServiceName = if (request["caller"].isNull) null else request["caller"].asString(),
                            targetServiceName = serviceName,
                            targetActionName = requestAction
                        )
                        this.moleculerRequestCache.put(requestId, MoleculerRequestData(
                            requestId = event.requestId,
                            sourceNodeId = event.sourceNodeId,
                            // TODO: should this really be optional?
                            sourceServiceName = event.sourceServiceName,
                            targetServiceName = event.targetServiceName,
                            targetActionName = event.targetActionName
                        ))
                        event.callEvent()
                    } catch (ex: Exception) {
                        this.logger.error("Failed to process MOL.REQB", ex)
                    }
                }

                dispatcher.subscribe("MOL.RES.>") { message: Message ->
                    val jsonString = String(message.data, StandardCharsets.UTF_8)
                    val response = Tree(jsonString)

                    val sourceNodeId = message.subject.substring("MOL.RES.".length)
                    val targetNodeId = response["sender"].asString()
                    val requestId = response["id"].asString()
                    val successful = response["success"].asBoolean()
                    val request = this.moleculerRequestCache.getIfPresent(requestId) ?: return@subscribe
                    ResponseEvent(
                        requestId = requestId,
                        sourceNodeId = sourceNodeId,
                        targetNodeId = targetNodeId,
                        sourceServiceName = request.sourceServiceName,
                        targetServiceName = request.targetServiceName,
                        targetActionName = request.targetActionName,
                        successful = successful
                    ).callEvent()
                    this.moleculerRequestCache.invalidate(requestId)
                }
            } catch (ex: Exception) {
                this.logger.error("Failed to connect to NATS", ex)
            }
        })
    }

    private fun connectToBroker(transportUrl: String) {
        val brokerConfig = ServiceBrokerConfig()

        this.logger.info("Connecting to broker via NATS (at {})", transportUrl)
        brokerConfig.transporter = NatsTransporter(transportUrl)
        broker = ServiceBroker(brokerConfig)
        Bukkit.getScheduler().runTaskAsynchronously(this.plugin, Runnable {
            try {
                broker!!.start()
                this.logger.info("Started broker!")

                Bukkit.getScheduler().runTaskTimerAsynchronously(this.plugin, Runnable {
                    try {
                        broker!!.call("\$node.list").then { nodes: Tree ->
                            val seen = mutableMapOf<String, Boolean>()
                            for (node in nodes) {
                                val nodeId = node["id"].asString()
                                if (!trackedNodes.contains(nodeId)) {
                                    trackedNodes[nodeId] = true
                                    NodeConnectedEvent(nodeId = nodeId).callEvent()
                                }
                                seen[nodeId] = true
                            }
                            for (nodeId in trackedNodes.keys) {
                                if (!seen.contains(nodeId)) {
                                    NodeDisconnectedEvent(nodeId = nodeId).callEvent()
                                    trackedNodes.remove(nodeId)
                                }
                            }
                        }
                    } catch (_: services.moleculer.error.RequestRejectedError) {
                    }
                }, 0L, 40L)
            } catch (ex: Exception) {
                this.logger.error("Failed to start broker", ex)
            }
        })
    }
}