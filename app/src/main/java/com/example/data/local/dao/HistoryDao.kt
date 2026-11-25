package com.example.data.local.dao

import androidx.room.Dao
import androidx.room.Embedded
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.example.data.local.entity.ChannelEntity
import com.example.data.local.entity.WatchHistoryEntity
import kotlinx.coroutines.flow.Flow

data class HistoryChannelItem(
    @Embedded val channel: ChannelEntity,
    val watchedAt: Long
)

@Dao
interface HistoryDao {
    @Query(
        """
        SELECT c.*, h.watchedAt AS watchedAt
        FROM watch_history h
        INNER JOIN channels c ON c.id = h.channelId
        ORDER BY h.watchedAt DESC
        LIMIT 100
        """
    )
    fun getHistoryChannels(): Flow<List<HistoryChannelItem>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertHistory(entry: WatchHistoryEntity)

    @Query("DELETE FROM watch_history")
    suspend fun clearHistory()
}
