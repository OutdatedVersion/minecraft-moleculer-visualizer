package com.outdatedversion.moleculer.visualizer

import co.aikar.commands.PaperCommandManager
import com.outdatedversion.moleculer.visualizer.command.ClearEntitiesDebugCommand
import com.outdatedversion.moleculer.visualizer.command.TestEffectCommand
import com.outdatedversion.moleculer.visualizer.command.VisualizerCommand
import com.outdatedversion.moleculer.visualizer.moleculer.MoleculerBridge
import de.slikey.effectlib.EffectManager
import org.bukkit.NamespacedKey
import org.bukkit.configuration.file.YamlConfiguration
import org.bukkit.event.Listener
import org.bukkit.plugin.java.JavaPlugin

class Plugin: JavaPlugin(), Listener {
    private lateinit var effectManager: EffectManager
    private lateinit var moleculerBridge: MoleculerBridge
    lateinit var entityManager: EntityManager
    private lateinit var commandManager: PaperCommandManager

    override fun onEnable() {
        this.saveDefaultConfig()
        val config = this.config as YamlConfiguration

        effectManager = EffectManager(this)
        entityManager = EntityManager(this)

        moleculerBridge = MoleculerBridge(this)
        moleculerBridge.connect(config.getConfigurationSection("moleculer")!!)

        commandManager = PaperCommandManager(this)
        commandManager.registerCommand(ClearEntitiesDebugCommand(entityManager))
        commandManager.registerCommand(TestEffectCommand(effectManager))
        commandManager.registerCommand(
            VisualizerCommand(
                plugin = this,
                entityManager = this.entityManager,
                effectManager = this.effectManager,
                moleculerBridge = this.moleculerBridge
            )
        )
    }

    override fun onDisable() {
        // Stop receiving and processing network events right away
        this.moleculerBridge.disconnect()
        // Prevent interacting with the stage
        this.commandManager.unregisterCommands()
        // Visuals cleanup
        this.effectManager.dispose()
    }

    fun createKey(key: String): NamespacedKey {
        return NamespacedKey(this, key)
    }
}