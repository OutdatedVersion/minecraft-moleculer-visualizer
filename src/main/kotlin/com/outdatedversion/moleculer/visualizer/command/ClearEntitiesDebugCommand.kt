package com.outdatedversion.moleculer.visualizer.command

import co.aikar.commands.BaseCommand
import co.aikar.commands.annotation.CommandAlias
import co.aikar.commands.annotation.Default
import com.outdatedversion.moleculer.visualizer.EntityManager
import net.kyori.adventure.text.Component
import net.kyori.adventure.text.format.NamedTextColor
import org.bukkit.entity.Player

@CommandAlias("visualizerclearentities")
class ClearEntitiesDebugCommand(private val entityManager: EntityManager): BaseCommand() {
    @Default
    fun handleCommand(player: Player) {
        val ours = player.world.entities
            .filter { entity -> this.entityManager.isOurEntity(entity) }
        ours.forEach { entity -> entity.remove() }
        player.sendMessage(Component.text("Removed ${ours.count()} entities", NamedTextColor.GREEN))
    }
}