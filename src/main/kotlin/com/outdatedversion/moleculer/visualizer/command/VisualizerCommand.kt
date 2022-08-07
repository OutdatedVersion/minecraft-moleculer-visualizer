package com.outdatedversion.moleculer.visualizer.command

import co.aikar.commands.BaseCommand
import co.aikar.commands.annotation.CommandAlias
import co.aikar.commands.annotation.Default
import co.aikar.commands.annotation.Subcommand
import com.outdatedversion.moleculer.visualizer.EntityManager
import com.outdatedversion.moleculer.visualizer.Plugin
import com.outdatedversion.moleculer.visualizer.VisualizerStage
import com.outdatedversion.moleculer.visualizer.moleculer.MoleculerBridge
import de.slikey.effectlib.EffectManager
import net.kyori.adventure.text.Component
import net.kyori.adventure.text.format.NamedTextColor
import org.bukkit.entity.Player

@CommandAlias("visualizer")
class VisualizerCommand(
    private val plugin: Plugin,
    private val moleculerBridge: MoleculerBridge,
    private val effectManager: EffectManager,
    private val entityManager: EntityManager,
): BaseCommand() {
    private var stage: VisualizerStage? = null

    @Default
    fun handleCommand(player: Player) {
        if (stage == null) {
            player.sendMessage(Component.text("There is no active stage", NamedTextColor.YELLOW))
        } else {
            player.sendMessage(Component.text("There is an active stage", NamedTextColor.YELLOW))
        }
    }

    @Subcommand("start")
    fun handleStartCommand(player: Player) {
        val loc = player.location.toBlockLocation()
        player.sendMessage(
            Component.text(
                "Starting visualizer at ${loc.x}, ${loc.y}, ${loc.z}",
                NamedTextColor.YELLOW
            )
        )

        stage = VisualizerStage(
            plugin = this.plugin,
            entityManager = this.entityManager,
            moleculerBridge = this.moleculerBridge,
            effectManager = this.effectManager,
            origin = loc
        )
        stage!!.begin()
    }

    @Subcommand("stop")
    fun handleStopCommand(player: Player) {
        if (stage == null) {
            player.sendMessage(Component.text("There is not an active stage ", NamedTextColor.RED)
                .append(Component.text("(Start one with /visualizer start)", NamedTextColor.YELLOW)))
            return
        }

        stage!!.end()
        stage = null
        player.sendMessage("Cleaned up")
    }
}