package com.example.data.remote

import com.example.data.local.entity.ChannelEntity
import com.squareup.moshi.Moshi
import com.squareup.moshi.Types
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.IOException
import java.net.URLEncoder

class XtreamService(
    private val okHttpClient: OkHttpClient,
    private val moshi: Moshi
) {
    fun normalizeHost(rawHost: String): String {
        var host = rawHost.trim()
        if (!host.startsWith("http://", ignoreCase = true) &&
            !host.startsWith("https://", ignoreCase = true)
        ) {
            host = "http://$host"
        }
        host = host.trimEnd('/')
        if (host.endsWith("/player_api.php", ignoreCase = true)) {
            host = host.removeSuffix("/player_api.php")
        }
        return host
    }

    suspend fun fetchLiveChannels(
        rawHost: String,
        username: String,
        password: String,
        playlistId: Long
    ): List<ChannelEntity> = withContext(Dispatchers.IO) {
        val baseUrl = normalizeHost(rawHost)
        val parsedBase = baseUrl.toHttpUrlOrNull()
            ?: throw IllegalArgumentException("URL do servidor Xtream inválida.")

        // 1. Validate authentication
        val authUrl = parsedBase.newBuilder()
            .addPathSegment("player_api.php")
            .addQueryParameter("username", username)
            .addQueryParameter("password", password)
            .build()

        val authJson = executeGet(authUrl.toString())
        val authAdapter = moshi.adapter(XtreamAuthResponse::class.java)
        val authResponse = runCatching { authAdapter.fromJson(authJson) }.getOrNull()
        val userInfo = authResponse?.userInfo
        if (userInfo != null && userInfo.auth == 0) {
            throw IllegalStateException("Utilizador ou senha Xtream inválidos.")
        }

        // 2. Fetch categories map
        val categoriesUrl = parsedBase.newBuilder()
            .addPathSegment("player_api.php")
            .addQueryParameter("username", username)
            .addQueryParameter("password", password)
            .addQueryParameter("action", "get_live_categories")
            .build()

        val categoryListType = Types.newParameterizedType(List::class.java, XtreamCategory::class.java)
        val categoryAdapter = moshi.adapter<List<XtreamCategory>>(categoryListType)
        val categoryMap = runCatching {
            val catJson = executeGet(categoriesUrl.toString())
            categoryAdapter.fromJson(catJson)
                ?.mapNotNull { cat ->
                    val id = cat.categoryId ?: return@mapNotNull null
                    val name = cat.categoryName?.takeIf { it.isNotBlank() } ?: return@mapNotNull null
                    id to name
                }?.toMap().orEmpty()
        }.getOrDefault(emptyMap())

        // 3. Fetch live streams
        val streamsUrl = parsedBase.newBuilder()
            .addPathSegment("player_api.php")
            .addQueryParameter("username", username)
            .addQueryParameter("password", password)
            .addQueryParameter("action", "get_live_streams")
            .build()

        val streamsJson = executeGet(streamsUrl.toString())
        val streamListType = Types.newParameterizedType(List::class.java, XtreamLiveStream::class.java)
        val streamAdapter = moshi.adapter<List<XtreamLiveStream>>(streamListType)
        val streams = streamAdapter.fromJson(streamsJson).orEmpty()

        val encodedUser = URLEncoder.encode(username, "UTF-8")
        val encodedPass = URLEncoder.encode(password, "UTF-8")

        streams.mapIndexedNotNull { index, stream ->
            val streamId = stream.streamId ?: return@mapIndexedNotNull null
            val channelName = stream.name?.takeIf { it.isNotBlank() } ?: "Canal ${index + 1}"
            val streamUrl = stream.directSource?.takeIf { it.isNotBlank() }
                ?: "$baseUrl/live/$encodedUser/$encodedPass/$streamId.m3u8"
            val groupName = stream.categoryId?.let { categoryMap[it] } ?: "Ao Vivo"

            ChannelEntity(
                playlistId = playlistId,
                name = channelName,
                streamUrl = streamUrl,
                logoUrl = stream.streamIcon?.takeIf { it.isNotBlank() },
                groupName = groupName,
                tvgId = stream.epgChannelId?.takeIf { it.isNotBlank() },
                isFavorite = false,
                streamOrder = stream.num ?: index
            )
        }
    }

    private fun executeGet(url: String): String {
        val request = Request.Builder()
            .url(url)
            .get()
            .build()
        okHttpClient.newCall(request).execute().use { response ->
            if (!response.isSuccessful) {
                throw IOException("Erro HTTP ${response.code} ao ligar ao servidor.")
            }
            return response.body?.string()
                ?: throw IOException("Resposta vazia do servidor.")
        }
    }
}
