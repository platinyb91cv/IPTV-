package com.example.data.repository

import androidx.room.withTransaction
import com.example.data.local.AppDatabase
import com.example.data.local.dao.HistoryChannelItem
import com.example.data.local.entity.ChannelEntity
import com.example.data.local.entity.PlaylistEntity
import com.example.data.local.entity.PlaylistType
import com.example.data.local.entity.WatchHistoryEntity
import com.example.data.parser.M3uParser
import com.example.data.remote.XtreamService
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.withContext
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.IOException

class IptvRepository(
    private val database: AppDatabase,
    private val okHttpClient: OkHttpClient,
    private val m3uParser: M3uParser,
    private val xtreamService: XtreamService
) {
    private val playlistDao = database.playlistDao()
    private val channelDao = database.channelDao()
    private val historyDao = database.historyDao()

    val playlists: Flow<List<PlaylistEntity>> = playlistDao.getAllPlaylists()
    val allChannels: Flow<List<ChannelEntity>> = channelDao.getAllChannels()
    val favoriteChannels: Flow<List<ChannelEntity>> = channelDao.getFavoriteChannels()
    val historyChannels: Flow<List<HistoryChannelItem>> = historyDao.getHistoryChannels()

    fun channelsForPlaylist(playlistId: Long): Flow<List<ChannelEntity>> =
        channelDao.getChannelsByPlaylist(playlistId)

    suspend fun getChannelById(channelId: Long): ChannelEntity? =
        channelDao.getChannelById(channelId)

    fun observeChannelById(channelId: Long): Flow<ChannelEntity?> =
        channelDao.observeChannelById(channelId)

    suspend fun addM3uPlaylist(name: String, rawUrl: String): Result<PlaylistEntity> =
        withContext(Dispatchers.IO) {
            runCatching {
                val normalizedUrl = rawUrl.trim().replace("\n", "").replace("\r", "")
                val httpUrl = normalizedUrl.toHttpUrlOrNull()
                    ?: throw IllegalArgumentException("URL M3U/M3U8 inválida.")
                val finalName = name.trim().ifBlank { httpUrl.host }

                val playlistId = playlistDao.insertPlaylist(
                    PlaylistEntity(
                        name = finalName,
                        type = PlaylistType.M3U,
                        m3uUrl = normalizedUrl
                    )
                )

                try {
                    val parsedChannels = fetchAndParseM3u(normalizedUrl, playlistId)
                    if (parsedChannels.isEmpty()) {
                        throw IllegalStateException("Nenhum canal encontrado na lista M3U.")
                    }
                    val updatedPlaylist = PlaylistEntity(
                        id = playlistId,
                        name = finalName,
                        type = PlaylistType.M3U,
                        m3uUrl = normalizedUrl,
                        channelCount = parsedChannels.size,
                        lastUpdatedAt = System.currentTimeMillis()
                    )
                    database.withTransaction {
                        parsedChannels.chunked(1000).forEach { batch ->
                            channelDao.insertChannels(batch)
                        }
                        playlistDao.updatePlaylist(updatedPlaylist)
                    }
                    updatedPlaylist
                } catch (e: Exception) {
                    playlistDao.deletePlaylistById(playlistId)
                    throw e
                }
            }
        }

    suspend fun addXtreamPlaylist(
        name: String,
        host: String,
        username: String,
        password: String
    ): Result<PlaylistEntity> = withContext(Dispatchers.IO) {
        runCatching {
            val cleanHost = xtreamService.normalizeHost(host)
            val cleanUser = username.trim()
            val cleanPass = password.trim()
            if (cleanUser.isBlank() || cleanPass.isBlank()) {
                throw IllegalArgumentException("Utilizador e senha são obrigatórios.")
            }
            val parsedHost = cleanHost.toHttpUrlOrNull()
                ?: throw IllegalArgumentException("Servidor Xtream inválido.")
            val finalName = name.trim().ifBlank { parsedHost.host }

            val playlistId = playlistDao.insertPlaylist(
                PlaylistEntity(
                    name = finalName,
                    type = PlaylistType.XTREAM,
                    xtreamHost = cleanHost,
                    xtreamUsername = cleanUser,
                    xtreamPassword = cleanPass
                )
            )

            try {
                val channels = xtreamService.fetchLiveChannels(
                    rawHost = cleanHost,
                    username = cleanUser,
                    password = cleanPass,
                    playlistId = playlistId
                )
                if (channels.isEmpty()) {
                    throw IllegalStateException("Nenhum canal ao vivo encontrado nesta conta Xtream.")
                }
                val updatedPlaylist = PlaylistEntity(
                    id = playlistId,
                    name = finalName,
                    type = PlaylistType.XTREAM,
                    xtreamHost = cleanHost,
                    xtreamUsername = cleanUser,
                    xtreamPassword = cleanPass,
                    channelCount = channels.size,
                    lastUpdatedAt = System.currentTimeMillis()
                )
                database.withTransaction {
                    channelDao.insertChannels(channels)
                    playlistDao.updatePlaylist(updatedPlaylist)
                }
                updatedPlaylist
            } catch (e: Exception) {
                playlistDao.deletePlaylistById(playlistId)
                throw e
            }
        }
    }

    suspend fun refreshPlaylist(playlistId: Long): Result<Int> = withContext(Dispatchers.IO) {
        runCatching {
            val playlist = playlistDao.getPlaylistById(playlistId)
                ?: throw IllegalStateException("Lista não encontrada.")
            val favoriteUrls = channelDao.getFavoriteStreamUrls(playlistId).toSet()

            val freshChannels = when (playlist.type) {
                PlaylistType.M3U -> {
                    val url = playlist.m3uUrl
                        ?: throw IllegalStateException("URL da lista ausente.")
                    fetchAndParseM3u(url, playlistId)
                }
                PlaylistType.XTREAM -> {
                    xtreamService.fetchLiveChannels(
                        rawHost = playlist.xtreamHost.orEmpty(),
                        username = playlist.xtreamUsername.orEmpty(),
                        password = playlist.xtreamPassword.orEmpty(),
                        playlistId = playlistId
                    )
                }
            }.map { channel ->
                if (channel.streamUrl in favoriteUrls) {
                    channel.copy(isFavorite = true)
                } else {
                    channel
                }
            }

            if (freshChannels.isEmpty()) {
                throw IllegalStateException("A atualização não retornou canais.")
            }

            database.withTransaction {
                channelDao.deleteChannelsByPlaylist(playlistId)
                freshChannels.chunked(1000).forEach { batch ->
                    channelDao.insertChannels(batch)
                }
                playlistDao.updatePlaylist(
                    playlist.copy(
                        channelCount = freshChannels.size,
                        lastUpdatedAt = System.currentTimeMillis()
                    )
                )
            }
            freshChannels.size
        }
    }

    suspend fun deletePlaylist(playlistId: Long) {
        withContext(Dispatchers.IO) {
            playlistDao.deletePlaylistById(playlistId)
        }
    }

    suspend fun toggleFavorite(channelId: Long, isFavorite: Boolean) {
        withContext(Dispatchers.IO) {
            channelDao.setFavorite(channelId, isFavorite)
        }
    }

    suspend fun recordChannelWatch(channelId: Long) {
        withContext(Dispatchers.IO) {
            historyDao.upsertHistory(
                WatchHistoryEntity(
                    channelId = channelId,
                    watchedAt = System.currentTimeMillis()
                )
            )
        }
    }

    suspend fun clearHistory() {
        withContext(Dispatchers.IO) {
            historyDao.clearHistory()
        }
    }

    private fun fetchAndParseM3u(url: String, playlistId: Long): List<ChannelEntity> {
        val request = Request.Builder()
            .url(url)
            .get()
            .build()
        okHttpClient.newCall(request).execute().use { response ->
            if (!response.isSuccessful) {
                throw IOException("Erro HTTP ${response.code} ao baixar a lista M3U.")
            }
            val body = response.body ?: throw IOException("Corpo da lista M3U vazio.")
            return m3uParser.parse(body.byteStream(), playlistId)
        }
    }
}
