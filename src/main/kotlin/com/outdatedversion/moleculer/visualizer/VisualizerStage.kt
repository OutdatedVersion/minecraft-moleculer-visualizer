package com.outdatedversion.moleculer.visualizer

import com.outdatedversion.moleculer.visualizer.effect.DrawLineEffect
import com.outdatedversion.moleculer.visualizer.moleculer.MoleculerBridge
import com.outdatedversion.moleculer.visualizer.moleculer.event.EventEmittedEvent
import com.outdatedversion.moleculer.visualizer.moleculer.event.NodeConnectedEvent
import com.outdatedversion.moleculer.visualizer.moleculer.event.NodeDisconnectedEvent
import com.outdatedversion.moleculer.visualizer.moleculer.event.NodeDiscoveredEvent
import com.outdatedversion.moleculer.visualizer.moleculer.event.NodeDiscoveryEvent
import com.outdatedversion.moleculer.visualizer.moleculer.event.NodeHeartbeatEvent
import com.outdatedversion.moleculer.visualizer.moleculer.event.RequestInitiatedEvent
import com.outdatedversion.moleculer.visualizer.moleculer.event.RequestTimeoutEvent
import com.outdatedversion.moleculer.visualizer.moleculer.event.ResponseEvent
import de.slikey.effectlib.EffectManager
import de.slikey.effectlib.EffectType
import de.slikey.effectlib.effect.CircleEffect
import de.slikey.effectlib.effect.HelixEffect
import net.kyori.adventure.text.Component
import net.kyori.adventure.text.format.NamedTextColor
import net.kyori.adventure.text.format.TextDecoration
import org.bukkit.Bukkit
import org.bukkit.Color
import org.bukkit.EntityEffect
import org.bukkit.Location
import org.bukkit.Particle
import org.bukkit.entity.Ageable
import org.bukkit.entity.ArmorStand
import org.bukkit.entity.Cow
import org.bukkit.entity.Firework
import org.bukkit.entity.LivingEntity
import org.bukkit.entity.Mob
import org.bukkit.entity.Panda
import org.bukkit.entity.Pig
import org.bukkit.event.EventHandler
import org.bukkit.event.HandlerList
import org.bukkit.event.Listener
import org.bukkit.persistence.PersistentDataType
import org.jetbrains.annotations.NotNull
import org.slf4j.LoggerFactory
import java.util.*

class VisualizerStage(
    private val plugin: Plugin,
    private val entityManager: EntityManager,
    private val effectManager: EffectManager,
    private val moleculerBridge: MoleculerBridge,
    private val origin: Location,
): Listener {
    private val logger = LoggerFactory.getLogger(VisualizerStage::class.java)

    private val nodeIdToEntityUuid = mutableMapOf<String, UUID>()
    private val nodeIdToServices = mutableMapOf<String, Set<String>>()
    private val serviceKeyToEntityUuid = mutableMapOf<String, UUID>()

    fun begin() {
        plugin.server.pluginManager.registerEvents(this, plugin)
        for (nodeId in moleculerBridge.getActiveNodes()) {
            onNodeConnected(NodeConnectedEvent(nodeId = nodeId))
        }
    }

    fun end() {
        HandlerList.unregisterAll(this)
        entityManager.removeAll(this.origin.world)
        effectManager.cancel(false)
    }

    @EventHandler
    fun onEventEmitted(event: EventEmittedEvent) {
        Bukkit.broadcast(Component.text("Event '${event.event}' (${event.sourceNodeId})", NamedTextColor.YELLOW))
        Bukkit.getScheduler().runTask(this.plugin, Runnable {
            val entity =
                if (event.sourceServiceName == null) getEntityForNode(event.sourceNodeId) else getEntityForService(
                    event.sourceNodeId,
                    event.sourceServiceName)

            if (entity != null) {
                val stand =
                    entity.world.spawn(entity.location.clone().subtract(0.0, 1.0, 0.0), ArmorStand::class.java)
                stand.persistentDataContainer.set(entityManager.isVisualizerEntityKey, PersistentDataType.BYTE, 1)
                Bukkit.getScheduler().runTaskLater(this.plugin, Runnable {
                    stand.remove()
                }, 30)
                stand.isVisible = false
                stand.setGravity(false)
                stand.customName(Component.text(event.event, NamedTextColor.YELLOW))
                stand.isCustomNameVisible = true

                entity.world.spawn(entity.location.clone().subtract(0.0, 1.0, 0.0), Firework::class.java)
            }

            val effect = HelixEffect(effectManager)
            effect.type = EffectType.INSTANT
            effect.entity = entity
            effect.duration = 1500
            effect.particles = 20
            effect.radius = 1f
            effect.curve = 15f
            effect.particleSize = .5f
            effect.particle = Particle.REDSTONE
            effect.color = Color.YELLOW
            effect.start()
        })
    }

    @EventHandler
    fun onRequestTimeout(event: RequestTimeoutEvent) {
        Bukkit.broadcast(Component.text("Request timed out '${event.requestId}' (${event.sourceNodeId})",
            NamedTextColor.GOLD))
        Bukkit.getScheduler().runTask(this.plugin, Runnable {
            val sourceEntity = if (event.sourceServiceName != null) getEntityForService(event.sourceNodeId,
                event.sourceServiceName) else getEntityForNode(event.sourceNodeId)
            if (sourceEntity != null) {
                val effect = CircleEffect(this.effectManager)
                effect.duration = 750
                effect.particles = 20
                effect.entity = sourceEntity
                effect.color = Color.RED
                effect.particle = Particle.REDSTONE
                effect.start()
            }
        })
    }

    @EventHandler
    fun onRequestInitiated(event: RequestInitiatedEvent) {
        Bukkit.broadcast(Component.text("Request initiated '${event.requestId}' (${event.sourceNodeId})",
            NamedTextColor.GRAY))
    }

    @EventHandler
    fun onResponse(event: ResponseEvent) {
        Bukkit.broadcast(Component.text("Response '${event.requestId}' (${event.sourceNodeId} -> ${event.targetNodeId})",
            NamedTextColor.GOLD))
        Bukkit.getScheduler().runTask(this.plugin) { _ ->
            val sourceEntity = if (event.sourceServiceName != null) getEntityForService(event.sourceNodeId,
                event.sourceServiceName) else getEntityForNode(event.sourceNodeId)
            val targetEntity = getEntityForService(event.targetNodeId, event.targetServiceName)

            if (sourceEntity == null || targetEntity == null) {
                this.logger.debug("skipping animation for request(id=${event.requestId}): cannot get entities")
                return@runTask
            }

            val line = DrawLineEffect(effectManager)
            line.particleSize = .70f
            line.color = Color.YELLOW
            line.entity = sourceEntity
            line.targetEntity = targetEntity
            line.callback = Runnable {
                Bukkit.getScheduler().runTask(this.plugin, Runnable {
                    val other = DrawLineEffect(effectManager)
                    other.particleSize = .70f
                    other.color = if (event.successful) Color.GREEN else Color.RED
                    other.entity = targetEntity
                    other.targetEntity = sourceEntity
                    other.start()
                })
            }
            line.start()
        }
    }

    @EventHandler
    fun onHeartbeat(event: NodeHeartbeatEvent) {
        Bukkit.broadcast(Component.text("Heartbeat ${event.nodeId}", NamedTextColor.RED))
        Bukkit.getScheduler().runTask(plugin, Runnable {
            val entity = getEntityForNode(nodeId = event.nodeId) ?: return@Runnable
            entity.playEffect(EntityEffect.LOVE_HEARTS)
        })
    }

    @EventHandler
    fun onNodeDiscovery(event: NodeDiscoveryEvent) {
        Bukkit.broadcast(Component.text("Node discovery '${event.nodeId}'", NamedTextColor.AQUA))
        Bukkit.getScheduler().runTask(plugin, Runnable { spawnNodeEntity(nodeId = event.nodeId, isConfirmed = false) })
    }

    @EventHandler
    fun onNodeDiscovery(event: NodeDiscoveredEvent) {
        Bukkit.broadcast(Component.text("Node discovered '${event.discoveredNodeId}' by '${event.sponsorNodeId}'",
            NamedTextColor.AQUA))
        Bukkit.getScheduler().runTask(plugin, Runnable {
            val discoveredNodeEntity = getEntityForNode(event.discoveredNodeId)
            val sponsorNodeEntity = getEntityForNode(event.sponsorNodeId)

            if (discoveredNodeEntity is Mob) {
                discoveredNodeEntity.target = sponsorNodeEntity
            }

            if (discoveredNodeEntity != null && sponsorNodeEntity != null) {
                val line = DrawLineEffect(effectManager)
                line.particleSize = .60f
                line.color = Color.AQUA
                line.entity = sponsorNodeEntity
                line.targetEntity = discoveredNodeEntity
                line.start()
            }
        })
    }

    @EventHandler
    fun onNodeConnected(event: NodeConnectedEvent) {
        Bukkit.broadcast(Component.text("Node connected '${event.nodeId}'", NamedTextColor.AQUA))
        Bukkit.getScheduler().runTask(plugin, Runnable {
            val entity = getEntityForNode(event.nodeId)
            if (entity == null) {
                spawnNodeEntity(nodeId = event.nodeId, isConfirmed = true)
            } else {
                if (entity is Ageable) {
                    entity.ageLock = false
                    entity.setAdult()
                    if (entity.customName() != null) {
                        entity.customName(entity.customName()!!.color(NamedTextColor.GREEN))
                    }
                }
                if (entity is Mob) {
                    entity.target = null
                }
            }
        })
        Bukkit.getScheduler().runTaskAsynchronously(plugin, Runnable {
            moleculerBridge.getServicesOnNode(nodeId = event.nodeId).handle { services, ex ->

                Bukkit.getScheduler().runTask(plugin, Runnable {
                    if (ex != null) {
                        val entity = getEntityForNode(nodeId = event.nodeId)
                        if (entity?.customName() != null) {
                            entity.customName(entity.customName()!!.color(NamedTextColor.GREEN)
                                .decorate(TextDecoration.ITALIC))
                        }
                        return@Runnable
                    }

                    for (service in services) {
                        val entity = getEntityForService(nodeId = event.nodeId, serviceName = service.fullName)
                        if (entity == null) {
                            spawnServiceEntity(nodeId = event.nodeId, serviceName = service.fullName)
                        }
                    }
                    nodeIdToServices[event.nodeId] = services.map { it.fullName }.toSet()
                })
            }
        })
    }

    @EventHandler
    fun onNodeDisconnected(event: NodeDisconnectedEvent) {
        Bukkit.broadcast(Component.text("Node disconnected '${event.nodeId}'", NamedTextColor.AQUA))
        Bukkit.getScheduler().runTask(plugin, Runnable {
            getEntityForNode(nodeId = event.nodeId)?.remove()
            nodeIdToServices[event.nodeId]?.forEach {
                getEntityForService(
                    nodeId = event.nodeId,
                    serviceName = it
                )?.remove()
            }
        })
    }

    private fun getEntityForNode(nodeId: String): LivingEntity? {
        val entityId = nodeIdToEntityUuid[nodeId] ?: return null
        return this.origin.world.getEntity(entityId) as? LivingEntity
    }

    private fun getEntityForService(@NotNull nodeId: String, @NotNull serviceName: String): LivingEntity? {
        val entityId = serviceKeyToEntityUuid["$nodeId:$serviceName"] ?: return null
        return this.origin.world.getEntity(entityId) as? LivingEntity
    }

    private fun spawnNodeEntity(nodeId: String, isConfirmed: Boolean = true): LivingEntity {
        this.logger.debug("[visualizer] [{}] creating component for moleculer node", nodeId)
        val cow = this.origin.world.spawn(this.origin, Cow::class.java)
        cow.customName(Component.text(nodeId, if (isConfirmed) NamedTextColor.GREEN else NamedTextColor.GRAY))
        cow.isCustomNameVisible = true
        cow.persistentDataContainer.set(entityManager.isVisualizerEntityKey, PersistentDataType.BYTE, 1)
        cow.persistentDataContainer.set(plugin.createKey("visualizer_entity_type"),
            PersistentDataType.STRING,
            VisualizerEntityType.Node.name)
        if (!isConfirmed) {
            cow.setBaby()
            cow.ageLock = true
        }
        nodeIdToEntityUuid[nodeId] = cow.uniqueId
        return cow
    }

    private fun spawnServiceEntity(nodeId: String, serviceName: String): LivingEntity? {
        val isInternalService = serviceName.startsWith("$")
        this.logger.debug("[visualizer] [{}] creating component for moleculer service '{}'", nodeId, serviceName)
        val nodeEntity = this.origin.world.getEntity(nodeIdToEntityUuid[nodeId]!!)
        if (nodeEntity == null) {
            this.logger.debug(
                "[visualizer] [{}] failed to create component for '{}': could not find node component entity",
                nodeId,
                serviceName
            )
            return null
        }

        val serviceEntityType = if (isInternalService) Pig::class.java else Panda::class.java
        val serviceEntity = nodeEntity.world.spawn(nodeEntity.location, serviceEntityType)
        serviceEntity.customName(Component.text(serviceName,
            if (isInternalService) NamedTextColor.YELLOW else NamedTextColor.AQUA))
        serviceEntity.isCustomNameVisible = true
        serviceEntity.setBaby()
        serviceEntity.ageLock = true
        serviceEntity.isSilent = true
        serviceEntity.target = nodeEntity as LivingEntity? // cast b/c we know it is a cow from above
        serviceEntity.setLeashHolder(nodeEntity)
        serviceEntity.persistentDataContainer.set(entityManager.isVisualizerEntityKey, PersistentDataType.BYTE, 1)
        serviceEntity.persistentDataContainer.set(plugin.createKey("visualizer_entity_type"),
            PersistentDataType.STRING,
            VisualizerEntityType.Service.name)

        this.logger.debug(
            "[visualizer] [{}] attached service component '{}' to node component '{}'",
            nodeId,
            serviceName,
            nodeId
        )
        serviceKeyToEntityUuid["$nodeId:$serviceName"] = serviceEntity.uniqueId
        return serviceEntity
    }
}
