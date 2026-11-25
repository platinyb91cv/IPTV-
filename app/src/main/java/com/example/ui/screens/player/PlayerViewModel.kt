package com.example.ui.screens.player

import android.content.Context
import androidx.annotation.OptIn
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.DefaultHttpDataSource
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.ui.AspectRatioFrameLayout
import com.example.data.local.entity.ChannelEntity
import com.example.data.repository.IptvRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class PlayerUiState(
    val currentChannel: ChannelEntity? = null,
    val queue: List<ChannelEntity> = emptyList(),
    val currentIndex: Int = -1,
    val isPlaying: Boolean = true,
    val isBuffering: Boolean = true,
    val errorMessage: String? = null,
    val resizeMode: Int = AspectRatioFrameLayout.RESIZE_MODE_FIT
) {
    val hasPrevious: Boolean get() = queue.size > 1 && currentIndex >= 0
    val hasNext: Boolean get() = queue.size > 1 && currentIndex >= 0
}

@OptIn(UnstableApi::class)
class PlayerViewModel(
    context: Context,
    private val repository: IptvRepository
) : ViewModel() {

    private val httpDataSourceFactory = DefaultHttpDataSource.Factory()
        .setAllowCrossProtocolRedirects(true)
        .setConnectTimeoutMs(15_000)
        .setReadTimeoutMs(30_000)
        .setUserAgent("VLC/3.0.18 LibVLC/3.0.18")

    val exoPlayer: ExoPlayer = ExoPlayer.Builder(context.applicationContext)
        .setMediaSourceFactory(DefaultMediaSourceFactory(httpDataSourceFactory))
        .setAudioAttributes(
            AudioAttributes.Builder()
                .setUsage(C.USAGE_MEDIA)
                .setContentType(C.AUDIO_CONTENT_TYPE_MOVIE)
                .build(),
            true
        )
        .setHandleAudioBecomingNoisy(true)
        .build()

    private val _uiState = MutableStateFlow(PlayerUiState())
    val uiState: StateFlow<PlayerUiState> = _uiState.asStateFlow()

    private var initializedKey: String? = null

    private val playerListener = object : Player.Listener {
        override fun onIsPlayingChanged(isPlaying: Boolean) {
            _uiState.update { it.copy(isPlaying = isPlaying) }
        }

        override fun onPlaybackStateChanged(playbackState: Int) {
            val buffering = playbackState == Player.STATE_BUFFERING
            _uiState.update { it.copy(isBuffering = buffering) }
        }

        override fun onPlayerError(error: PlaybackException) {
            _uiState.update {
                it.copy(
                    isBuffering = false,
                    isPlaying = false,
                    errorMessage = "Falha ao reproduzir canal (${error.errorCodeName}). Verifique a ligação ou tente o próximo canal."
                )
            }
        }
    }

    init {
        exoPlayer.addListener(playerListener)
    }

    fun initialize(channelId: Long, playlistId: Long, categoryKey: String) {
        val key = "$channelId:$playlistId:$categoryKey"
        if (initializedKey == key) return
        initializedKey = key

        viewModelScope.launch {
            val allChannels = if (playlistId > 0L) {
                repository.channelsForPlaylist(playlistId).first()
            } else {
                repository.allChannels.first()
            }

            val queue = when {
                categoryKey == "FAVORITES" -> allChannels.filter { it.isFavorite }
                categoryKey == "HISTORY" -> {
                    val history = repository.historyChannels.first().map { it.channel }
                    if (playlistId > 0L) history.filter { it.playlistId == playlistId } else history
                }
                categoryKey.startsWith("GROUP:") -> {
                    val groupName = categoryKey.removePrefix("GROUP:")
                    allChannels.filter { it.groupName == groupName }
                }
                else -> allChannels
            }.ifEmpty {
                listOfNotNull(repository.getChannelById(channelId))
            }

            val targetIndex = queue.indexOfFirst { it.id == channelId }.let { idx ->
                if (idx >= 0) idx else 0
            }
            val targetChannel = queue.getOrNull(targetIndex)
                ?: repository.getChannelById(channelId)

            if (targetChannel != null) {
                _uiState.update {
                    it.copy(
                        queue = queue,
                        currentIndex = targetIndex,
                        currentChannel = targetChannel
                    )
                }
                playChannel(targetChannel)
            } else {
                _uiState.update {
                    it.copy(
                        isBuffering = false,
                        errorMessage = "Canal não encontrado."
                    )
                }
            }
        }
    }

    fun togglePlayPause() {
        if (exoPlayer.isPlaying) {
            exoPlayer.pause()
        } else {
            if (exoPlayer.playbackState == Player.STATE_IDLE) {
                exoPlayer.prepare()
            }
            exoPlayer.play()
        }
    }

    fun previousChannel() {
        val state = _uiState.value
        if (state.queue.isEmpty()) return
        val prevIndex = if (state.currentIndex > 0) {
            state.currentIndex - 1
        } else {
            state.queue.lastIndex
        }
        val channel = state.queue[prevIndex]
        _uiState.update { it.copy(currentIndex = prevIndex, currentChannel = channel) }
        playChannel(channel)
    }

    fun nextChannel() {
        val state = _uiState.value
        if (state.queue.isEmpty()) return
        val nextIndex = if (state.currentIndex < state.queue.lastIndex) {
            state.currentIndex + 1
        } else {
            0
        }
        val channel = state.queue[nextIndex]
        _uiState.update { it.copy(currentIndex = nextIndex, currentChannel = channel) }
        playChannel(channel)
    }

    fun retry() {
        val channel = _uiState.value.currentChannel ?: return
        playChannel(channel)
    }

    fun cycleResizeMode() {
        val nextMode = when (_uiState.value.resizeMode) {
            AspectRatioFrameLayout.RESIZE_MODE_FIT -> AspectRatioFrameLayout.RESIZE_MODE_FILL
            AspectRatioFrameLayout.RESIZE_MODE_FILL -> AspectRatioFrameLayout.RESIZE_MODE_ZOOM
            else -> AspectRatioFrameLayout.RESIZE_MODE_FIT
        }
        _uiState.update { it.copy(resizeMode = nextMode) }
    }

    fun toggleFavorite() {
        val channel = _uiState.value.currentChannel ?: return
        val newFav = !channel.isFavorite
        viewModelScope.launch {
            repository.toggleFavorite(channel.id, newFav)
            _uiState.update { state ->
                val updatedChannel = channel.copy(isFavorite = newFav)
                val updatedQueue = state.queue.map { item ->
                    if (item.id == channel.id) updatedChannel else item
                }
                state.copy(
                    currentChannel = updatedChannel,
                    queue = updatedQueue
                )
            }
        }
    }

    private fun playChannel(channel: ChannelEntity) {
        _uiState.update {
            it.copy(
                currentChannel = channel,
                isBuffering = true,
                errorMessage = null
            )
        }
        exoPlayer.stop()
        exoPlayer.setMediaItem(MediaItem.fromUri(channel.streamUrl))
        exoPlayer.prepare()
        exoPlayer.playWhenReady = true

        viewModelScope.launch {
            repository.recordChannelWatch(channel.id)
        }
    }

    override fun onCleared() {
        exoPlayer.removeListener(playerListener)
        exoPlayer.release()
        super.onCleared()
    }
}
