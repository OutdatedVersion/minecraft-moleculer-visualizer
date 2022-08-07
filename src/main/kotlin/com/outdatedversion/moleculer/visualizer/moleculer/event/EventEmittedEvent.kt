package com.outdatedversion.moleculer.visualizer.moleculer.event

import org.bukkit.Bukkit
import org.bukkit.event.Event
import org.bukkit.event.HandlerList
import java.util.*

class EventEmittedEvent(
    val event: String,
    val sourceNodeId: String,
    val sourceServiceName: String,
    val timestamp: Date,
): Event(!Bukkit.isPrimaryThread()) {
    companion object {
        private val handlers = HandlerList()

        @JvmStatic
        fun getHandlerList(): HandlerList {
            return handlers
        }
    }

    override fun getHandlers(): HandlerList {
        return Companion.handlers
    }
}