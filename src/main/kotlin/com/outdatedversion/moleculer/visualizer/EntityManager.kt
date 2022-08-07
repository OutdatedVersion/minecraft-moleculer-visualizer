package com.outdatedversion.moleculer.visualizer

import org.bukkit.World
import org.bukkit.entity.Entity
import org.bukkit.event.EventHandler
import org.bukkit.event.Listener
import org.bukkit.event.entity.EntityDamageEvent
import org.bukkit.event.entity.EntityTargetEvent
import org.bukkit.event.entity.EntityUnleashEvent
import org.bukkit.persistence.PersistentDataType

class EntityManager(private val plugin: Plugin): Listener {
    val isVisualizerEntityKey = plugin.createKey("is_visualizer_entity")

    init {
        this.plugin.server.pluginManager.registerEvents(this, plugin)
    }

    fun isOurEntity(entity: Entity): Boolean {
        return entity.persistentDataContainer.getOrDefault(
            isVisualizerEntityKey,
            PersistentDataType.BYTE,
            0
        ).toInt() == 1
    }

    fun removeAll(world: World) {
        world.entities.forEach { entity ->
            if (isOurEntity(entity)) {
                entity.remove()
            }
        }
    }

    @EventHandler(ignoreCancelled = true)
    fun preventOwnedEntityDamage(event: EntityDamageEvent) {
        if (isOurEntity(event.entity)) {
            event.isCancelled = true
        }
    }

    @EventHandler(ignoreCancelled = true)
    fun preventOwnedEntityTarget(event: EntityTargetEvent) {
        val entityType = event.entity.persistentDataContainer.get(
            plugin.createKey("visualizer_entity_type"),
            PersistentDataType.STRING
        )
        if (!entityType.isNullOrBlank()) {
            if (VisualizerEntityType.valueOf(entityType) == VisualizerEntityType.Service) {
                event.isCancelled = true
            }
        }
    }

    @EventHandler(ignoreCancelled = true)
    fun preventLeadBreak(event: EntityUnleashEvent) {
        if (isOurEntity(event.entity)) {
            event.isCancelled = true
        }
    }
}