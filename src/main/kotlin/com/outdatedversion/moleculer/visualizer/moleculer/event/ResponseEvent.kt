package com.outdatedversion.moleculer.visualizer.moleculer.event

import org.bukkit.Bukkit
import org.bukkit.event.Event
import org.bukkit.event.HandlerList

class ResponseEvent(
    val requestId: String,
    val sourceNodeId: String,
    val targetNodeId: String,
    val sourceServiceName: String?,
    val targetServiceName: String,
    val targetActionName: String,
    val successful: Boolean,
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