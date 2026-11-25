package com.example.ui.navigation

import kotlinx.serialization.Serializable

@Serializable
data object HomeRoute

@Serializable
data class PlayerRoute(
    val channelId: Long,
    val playlistId: Long = -1L,
    val categoryKey: String = "ALL"
)
