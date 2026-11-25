package com.example.ui.screens.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.local.entity.ChannelEntity
import com.example.data.local.entity.PlaylistEntity
import com.example.data.repository.IptvRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

sealed class SidebarCategory(val key: String) {
    data object All : SidebarCategory("ALL")
    data object Favorites : SidebarCategory("FAVORITES")
    data object History : SidebarCategory("HISTORY")
    data class Group(val name: String) : SidebarCategory("GROUP:$name")
}

data class CategoryItem(
    val category: SidebarCategory,
    val title: String,
    val count: Int
)

data class HomeUiState(
    val playlists: List<PlaylistEntity> = emptyList(),
    val selectedPlaylist: PlaylistEntity? = null,
    val selectedCategory: SidebarCategory = SidebarCategory.All,
    val groupCategories: List<CategoryItem> = emptyList(),
    val searchQuery: String = "",
    val channels: List<ChannelEntity> = emptyList(),
    val totalChannelCount: Int = 0,
    val favoriteCount: Int = 0,
    val historyCount: Int = 0,
    val showAddPlaylistDialog: Boolean = false,
    val isAddingPlaylist: Boolean = false,
    val addPlaylistError: String? = null,
    val isRefreshing: Boolean = false,
    val feedbackMessage: String? = null
)

private data class HomeFilterState(
    val selectedPlaylistId: Long? = null,
    val selectedCategory: SidebarCategory = SidebarCategory.All,
    val searchQuery: String = "",
    val showAddDialog: Boolean = false,
    val isAdding: Boolean = false,
    val addError: String? = null,
    val isRefreshing: Boolean = false,
    val feedbackMessage: String? = null
)

class HomeViewModel(
    private val repository: IptvRepository
) : ViewModel() {

    private val filterState = MutableStateFlow(HomeFilterState())

    val uiState: StateFlow<HomeUiState> = combine(
        repository.playlists,
        repository.allChannels,
        repository.favoriteChannels,
        repository.historyChannels,
        filterState
    ) { playlists, allChannels, favoriteChannels, historyItems, filter ->

        val activePlaylist = filter.selectedPlaylistId?.let { id ->
            playlists.firstOrNull { it.id == id }
        }

        val scopedChannels = if (activePlaylist != null) {
            allChannels.filter { it.playlistId == activePlaylist.id }
        } else {
            allChannels
        }

        val scopedFavorites = if (activePlaylist != null) {
            favoriteChannels.filter { it.playlistId == activePlaylist.id }
        } else {
            favoriteChannels
        }

        val scopedHistory = if (activePlaylist != null) {
            historyItems.map { it.channel }.filter { it.playlistId == activePlaylist.id }
        } else {
            historyItems.map { it.channel }
        }

        val groupCounts = scopedChannels
            .groupingBy { it.groupName }
            .eachCount()
            .entries
            .sortedBy { it.key.lowercase() }
            .map { (groupName, count) ->
                CategoryItem(
                    category = SidebarCategory.Group(groupName),
                    title = groupName,
                    count = count
                )
            }

        val effectiveCategory = when (val cat = filter.selectedCategory) {
            is SidebarCategory.Group -> {
                if (groupCounts.any { it.title == cat.name }) cat else SidebarCategory.All
            }
            else -> cat
        }

        val baseList = when (effectiveCategory) {
            SidebarCategory.All -> scopedChannels
            SidebarCategory.Favorites -> scopedFavorites
            SidebarCategory.History -> scopedHistory
            is SidebarCategory.Group -> scopedChannels.filter { it.groupName == effectiveCategory.name }
        }

        val query = filter.searchQuery.trim()
        val filteredList = if (query.isBlank()) {
            baseList
        } else {
            baseList.filter { channel ->
                channel.name.contains(query, ignoreCase = true) ||
                    channel.groupName.contains(query, ignoreCase = true)
            }
        }

        HomeUiState(
            playlists = playlists,
            selectedPlaylist = activePlaylist,
            selectedCategory = effectiveCategory,
            groupCategories = groupCounts,
            searchQuery = filter.searchQuery,
            channels = filteredList,
            totalChannelCount = scopedChannels.size,
            favoriteCount = scopedFavorites.size,
            historyCount = scopedHistory.size,
            showAddPlaylistDialog = filter.showAddDialog,
            isAddingPlaylist = filter.isAdding,
            addPlaylistError = filter.addError,
            isRefreshing = filter.isRefreshing,
            feedbackMessage = filter.feedbackMessage
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = HomeUiState()
    )

    fun selectPlaylist(playlistId: Long?) {
        filterState.update {
            it.copy(
                selectedPlaylistId = playlistId,
                selectedCategory = SidebarCategory.All
            )
        }
    }

    fun selectCategory(category: SidebarCategory) {
        filterState.update { it.copy(selectedCategory = category) }
    }

    fun updateSearchQuery(query: String) {
        filterState.update { it.copy(searchQuery = query) }
    }

    fun openAddPlaylistDialog() {
        filterState.update { it.copy(showAddDialog = true, addError = null) }
    }

    fun closeAddPlaylistDialog() {
        if (!filterState.value.isAdding) {
            filterState.update { it.copy(showAddDialog = false, addError = null) }
        }
    }

    fun addM3uPlaylist(name: String, url: String) {
        viewModelScope.launch {
            filterState.update { it.copy(isAdding = true, addError = null) }
            val result = repository.addM3uPlaylist(name, url)
            result.onSuccess { playlist ->
                filterState.update {
                    it.copy(
                        isAdding = false,
                        showAddDialog = false,
                        selectedPlaylistId = playlist.id,
                        selectedCategory = SidebarCategory.All,
                        feedbackMessage = "${playlist.channelCount} canais importados de ${playlist.name}"
                    )
                }
            }.onFailure { err ->
                filterState.update {
                    it.copy(
                        isAdding = false,
                        addError = err.message ?: "Não foi possível carregar a lista M3U."
                    )
                }
            }
        }
    }

    fun addXtreamPlaylist(name: String, host: String, username: String, password: String) {
        viewModelScope.launch {
            filterState.update { it.copy(isAdding = true, addError = null) }
            val result = repository.addXtreamPlaylist(name, host, username, password)
            result.onSuccess { playlist ->
                filterState.update {
                    it.copy(
                        isAdding = false,
                        showAddDialog = false,
                        selectedPlaylistId = playlist.id,
                        selectedCategory = SidebarCategory.All,
                        feedbackMessage = "${playlist.channelCount} canais importados de ${playlist.name}"
                    )
                }
            }.onFailure { err ->
                filterState.update {
                    it.copy(
                        isAdding = false,
                        addError = err.message ?: "Falha na autenticação Xtream Codes."
                    )
                }
            }
        }
    }

    fun refreshPlaylist(playlistId: Long) {
        viewModelScope.launch {
            filterState.update { it.copy(isRefreshing = true) }
            val result = repository.refreshPlaylist(playlistId)
            result.onSuccess { count ->
                filterState.update {
                    it.copy(
                        isRefreshing = false,
                        feedbackMessage = "Lista atualizada ($count canais)"
                    )
                }
            }.onFailure { err ->
                filterState.update {
                    it.copy(
                        isRefreshing = false,
                        feedbackMessage = err.message ?: "Erro ao atualizar a lista"
                    )
                }
            }
        }
    }

    fun deletePlaylist(playlistId: Long) {
        viewModelScope.launch {
            repository.deletePlaylist(playlistId)
            filterState.update { current ->
                current.copy(
                    selectedPlaylistId = if (current.selectedPlaylistId == playlistId) null else current.selectedPlaylistId,
                    selectedCategory = SidebarCategory.All,
                    feedbackMessage = "Lista removida"
                )
            }
        }
    }

    fun toggleFavorite(channel: ChannelEntity) {
        viewModelScope.launch {
            repository.toggleFavorite(channel.id, !channel.isFavorite)
        }
    }

    fun clearHistory() {
        viewModelScope.launch {
            repository.clearHistory()
            filterState.update { it.copy(feedbackMessage = "Histórico limpo") }
        }
    }

    fun consumeFeedback() {
        filterState.update { it.copy(feedbackMessage = null) }
    }
}
