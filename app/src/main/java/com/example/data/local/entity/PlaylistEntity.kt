package com.example.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

enum class PlaylistType {
    M3U,
    XTREAM
}

@Entity(tableName = "playlists")
data class PlaylistEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val type: PlaylistType,
    val m3uUrl: String? = null,
    val xtreamHost: String? = null,
    val xtreamUsername: String? = null,
    val xtreamPassword: String? = null,
    val channelCount: Int = 0,
    val lastUpdatedAt: Long = System.currentTimeMillis()
)
