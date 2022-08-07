package com.outdatedversion.moleculer.visualizer.model

data class MoleculerRequestData(
    val requestId: String,
    val sourceNodeId: String,
    val sourceServiceName: String?,
    val targetServiceName: String,
    val targetActionName: String,
)
