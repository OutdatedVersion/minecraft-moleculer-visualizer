package com.outdatedversion.moleculer.visualizer.moleculer.event

import org.bukkit.Bukkit
import org.bukkit.event.Event
import org.bukkit.event.HandlerList

class NodeDiscoveredEvent(
    val discoveredNodeId: String,
    val sponsorNodeId: String,
): Event(!Bukkit.isPrimaryThread()) {
    companion object {
        private val HANDLERS = HandlerList()

        @JvmStatic
        fun getHandlerList(): HandlerList {
            return HANDLERS
        }
    }

    override fun getHandlers(): HandlerList {
        return HANDLERS
    }
}
