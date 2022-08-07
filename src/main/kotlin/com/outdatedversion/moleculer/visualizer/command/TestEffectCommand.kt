package com.outdatedversion.moleculer.visualizer.command

import co.aikar.commands.BaseCommand
import co.aikar.commands.annotation.CommandAlias
import co.aikar.commands.annotation.Default
import de.slikey.effectlib.Effect
import de.slikey.effectlib.EffectManager
import net.kyori.adventure.text.Component
import net.kyori.adventure.text.format.NamedTextColor
import org.bukkit.entity.Player

@CommandAlias("testeffect")
class TestEffectCommand(private val effectManager: EffectManager): BaseCommand() {
    @Default
    fun handleCommand(player: Player, effect: String) {
        val clazz = "de.slikey.effectlib.effect.$effect"
        var effectClass: Class<out Any>? = null
        try {
            effectClass = Class.forName(clazz)
        } catch (ex: Exception) {
            player.sendMessage(Component.text("Could not load class: See console for details", NamedTextColor.RED))
            ex.printStackTrace()
        }

        if (effectClass == null) {
            player.sendMessage(Component.text("No matching effect: $clazz", NamedTextColor.RED))
            return
        }

        val effectInstance: Effect =
            effectClass.getDeclaredConstructor(EffectManager::class.java).newInstance(effectManager) as Effect
        if (effectInstance != null) {
            player.sendMessage("Playing ${effectClass.canonicalName}")
            effectInstance.entity = player
            effectInstance.start()
        } else {
            player.sendMessage(Component.text("Could not play ${effectClass.name}", NamedTextColor.RED))
        }
    }
}