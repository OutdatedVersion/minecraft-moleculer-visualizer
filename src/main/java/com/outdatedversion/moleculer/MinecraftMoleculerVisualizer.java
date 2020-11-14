package com.outdatedversion.moleculer;

import com.outdatedversion.moleculer.service.WatchdogService;
import io.datatree.Tree;
import io.nats.client.Connection;
import io.nats.client.Dispatcher;
import io.nats.client.Nats;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.EntityEffect;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.Cow;
import org.bukkit.entity.Entity;
import org.bukkit.plugin.java.JavaPlugin;
import services.moleculer.ServiceBroker;
import services.moleculer.config.ServiceBrokerConfig;
import services.moleculer.transporter.NatsTransporter;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * @author Ben Watkins
 * @since Jul/06/2020
 */
public class MinecraftMoleculerVisualizer extends JavaPlugin {

    private ServiceBroker broker;
    private Connection natsConnection;

    private Map<String, UUID> nodeIdToEntityUuid = new HashMap<>();

    @Override
    public void onEnable() {
        this.saveDefaultConfig();

        this.connectToNats(this::subscribeToEvents);
        this.createAndStartBroker(() -> {
            try {
                Thread.sleep(2000);
            }
            catch (InterruptedException e) {
                e.printStackTrace();
            } this.broker.call("$node.list").then(nodes -> {
                this.getSLF4JLogger().info("Nodes: {}", nodes.toString(true));

                for (final Tree node : nodes) {
                    final String nodeId = node.get("id").asString();

                    this.getSLF4JLogger().info("ID: {}", nodeId);
                    Bukkit.getScheduler().runTask(this, () -> {
                        this.spawnNodeEntity(nodeId);
                    });
                }
            });
        });
    }

    @Override
    public void onDisable() {
        for (final UUID entityUuid : nodeIdToEntityUuid.values()) {
            final Entity entity = Bukkit.getWorld("world").getEntity(entityUuid);

            if (entity != null) {
                entity.remove();
            }
        }

        if (this.natsConnection != null) {
            try {
                this.natsConnection.close();
                this.getSLF4JLogger().info("Disconnected from NATS");
            } catch (Exception ex) {
                this.getSLF4JLogger().error("Failed to disconnect from NATS", ex);
            }
        }
    }

    private void spawnNodeEntity(final String nodeId) {
        this.getSLF4JLogger().info("Spawning entity for {}", nodeId);
        final World world = Bukkit.getWorld("world");
        final Cow cow = world.spawn(new Location(world, -138, 80, 121), Cow.class);

        cow.setCustomName(ChatColor.GREEN + nodeId);
        cow.setCustomNameVisible(true);

        nodeIdToEntityUuid.put(nodeId, cow.getUniqueId());
    }

    /**
     *
     * @param callback executes off main thread
     */
    private void connectToNats(Runnable callback) {
        Bukkit.getScheduler().runTaskAsynchronously(this, () -> {
            try {
                this.natsConnection = Nats.connect("nats://localhost:4222");
                this.getSLF4JLogger().info("Connected to NATS!");
                callback.run();
            }
            catch (Exception ex) {
                this.getSLF4JLogger().error("Failed to connect to NATS", ex);
            }
        });
    }

    private void subscribeToEvents() {
        this.getSLF4JLogger().info("Starting MOL listener");
        final Dispatcher dispatcher = this.natsConnection.createDispatcher(message -> {});

        dispatcher.subscribe("MOL.HEARTBEAT", (message) -> {
            try {
                final String jsonString = new String(message.getData(), StandardCharsets.UTF_8);
                final Tree node = new Tree(jsonString);

                this.getSLF4JLogger().info("Moleculer heartbeat, data: '{}'", node.toString(true));

                final String nodeId = node.get("sender").asString();
                // TODO(ben): should handle null somewhere
                final Entity entity = this.getEntityForNode(nodeId);

                entity.playEffect(EntityEffect.LOVE_HEARTS);
            }
            catch (Exception e) {
                e.printStackTrace();
            }
        });

        // TODO(ben): support MOL.REQ too
        dispatcher.subscribe("MOL.REQB.*.*", (message) -> {
            try {
                final String jsonString = new String(message.getData(), StandardCharsets.UTF_8);
                final Tree request = new Tree(jsonString);

                this.getSLF4JLogger().info("reqb, sub: '{}', data: '{}'", message.getSubject(), request.toString(true));

                final String sourceNodeId = request.get("sender").asString();
                final Entity entity = this.getEntityForNode(sourceNodeId);
                entity.playEffect(EntityEffect.ARMOR_STAND_HIT);
            } catch (Exception ex) {
                this.getSLF4JLogger().error("Failed to process MOL.REQB", ex);
            }
        });
        dispatcher.subscribe("MOL.RES.*", (message) -> {
            this.getSLF4JLogger().info("res, sub: '{}', data: '{}'", message.getSubject(), new String(message.getData(), StandardCharsets.UTF_8));
        });

        try {
            natsConnection.flush(Duration.ZERO);
        } catch (Exception ex) {
            this.getSLF4JLogger().error("Failed to flush connection", ex);
        }
    }

    private Entity getEntityForNode(String nodeId) {
        final UUID entityId = this.nodeIdToEntityUuid.get(nodeId);

        if (entityId == null) {
            // fuck! (v1)
        }

        final Entity entity = Bukkit.getWorld("world").getEntity(entityId);

        if (entity == null) {
            // fuck!
        }

        return entity;
    }




    private void createAndStartBroker(Runnable callback) {
        final ServiceBrokerConfig brokerConfig = new ServiceBrokerConfig();

        // TODO(ben): support different transports
        brokerConfig.setTransporter(new NatsTransporter(this.getConfig().getString("moleculer.transport.url")));

        this.broker = new ServiceBroker(brokerConfig);

        Bukkit.getScheduler().runTaskAsynchronously(this, () -> {
            try {
                this.broker.createService(new WatchdogService(this.getSLF4JLogger()));
                this.broker.start();
                this.getSLF4JLogger().info("Started broker!");

                callback.run();
            }
            catch (Exception ex) {
                this.getSLF4JLogger().error("Failed to start broker", ex);
            }
        });
    }

}
