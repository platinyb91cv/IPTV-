package com.example.ui.screens.home

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.focusGroup
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.PlaylistPlay
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.StarBorder
import androidx.compose.material.icons.filled.Tv
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.R
import com.example.data.local.entity.ChannelEntity
import com.example.ui.components.AddPlaylistDialog
import com.example.ui.components.ChannelLogoBox
import com.example.ui.components.LiveBadge
import com.example.ui.components.tvFocusClickable
import com.example.ui.theme.Accent
import com.example.ui.theme.AccentBlueGlow
import com.example.ui.theme.Background
import com.example.ui.theme.CanvasBackground
import com.example.ui.theme.FavoriteGold
import com.example.ui.theme.LiveRed
import com.example.ui.theme.Surface
import com.example.ui.theme.SurfaceFocus
import com.example.ui.theme.TextPrimary
import com.example.ui.theme.TextSecondary
import org.koin.androidx.compose.koinViewModel

@Composable
fun HomeScreen(
    onChannelSelected: (channelId: Long, playlistId: Long, categoryKey: String) -> Unit,
    viewModel: HomeViewModel = koinViewModel()
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }

    LaunchedEffect(uiState.feedbackMessage) {
        val msg = uiState.feedbackMessage
        if (!msg.isNullOrBlank()) {
            snackbarHostState.showSnackbar(msg)
            viewModel.consumeFeedback()
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Background)
    ) {
        Row(
            modifier = Modifier
                .fillMaxSize()
                .windowInsetsPadding(WindowInsets.statusBars)
                .windowInsetsPadding(WindowInsets.navigationBars)
        ) {
            SidebarPanel(
                uiState = uiState,
                onSelectPlaylist = viewModel::selectPlaylist,
                onSelectCategory = viewModel::selectCategory,
                onOpenAddPlaylist = viewModel::openAddPlaylistDialog,
                onRefreshPlaylist = viewModel::refreshPlaylist,
                onDeletePlaylist = viewModel::deletePlaylist,
                modifier = Modifier
                    .width(200.dp)
                    .fillMaxHeight()
            )

            ChannelsPanel(
                uiState = uiState,
                onSearchQueryChange = viewModel::updateSearchQuery,
                onChannelClick = { channel ->
                    onChannelSelected(
                        channel.id,
                        uiState.selectedPlaylist?.id ?: -1L,
                        uiState.selectedCategory.key
                    )
                },
                onToggleFavorite = viewModel::toggleFavorite,
                onOpenAddPlaylist = viewModel::openAddPlaylistDialog,
                onClearSearch = { viewModel.updateSearchQuery("") },
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight()
            )
        }

        SnackbarHost(
            hostState = snackbarHostState,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .windowInsetsPadding(WindowInsets.navigationBars)
                .padding(16.dp)
        )
    }

    if (uiState.showAddPlaylistDialog) {
        AddPlaylistDialog(
            isLoading = uiState.isAddingPlaylist,
            errorMessage = uiState.addPlaylistError,
            onDismiss = viewModel::closeAddPlaylistDialog,
            onAddM3u = viewModel::addM3uPlaylist,
            onAddXtream = viewModel::addXtreamPlaylist
        )
    }
}

@Composable
private fun SidebarPanel(
    uiState: HomeUiState,
    onSelectPlaylist: (Long?) -> Unit,
    onSelectCategory: (SidebarCategory) -> Unit,
    onOpenAddPlaylist: () -> Unit,
    onRefreshPlaylist: (Long) -> Unit,
    onDeletePlaylist: (Long) -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .background(Background)
            .focusGroup()
    ) {
        // Section: LISTAS
        Text(
            text = "LISTAS",
            fontFamily = FontFamily.Default,
            fontSize = 10.sp,
            fontWeight = FontWeight.SemiBold,
            color = TextSecondary,
            modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 4.dp)
        )

        LazyColumn(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(max = 160.dp)
                .testTag("playlist_list")
        ) {
            item(key = "playlist_all") {
                val isSelected = uiState.selectedPlaylist == null
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(48.dp)
                        .tvFocusClickable(
                            shape = RectangleShape,
                            defaultBackground = if (isSelected) Surface else Color.Transparent,
                            focusedBackground = Surface,
                            defaultBorder = Color.Transparent,
                            focusedBorder = Color.Transparent,
                            focusScale = 1.0f,
                            onClick = { onSelectPlaylist(null) }
                        )
                        .drawBehind {
                            if (isSelected) {
                                drawRect(
                                    color = Accent,
                                    size = Size(3.dp.toPx(), size.height)
                                )
                            }
                        }
                        .padding(horizontal = 16.dp)
                        .testTag("playlist_all_item"),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.PlaylistPlay,
                        contentDescription = null,
                        tint = if (isSelected) Accent else TextSecondary,
                        modifier = Modifier.size(24.dp)
                    )
                    Text(
                        text = "Todas as Listas",
                        fontFamily = FontFamily.Default,
                        fontSize = 14.sp,
                        fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Normal,
                        color = if (isSelected) Accent else TextSecondary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f)
                    )
                }
            }

            items(
                items = uiState.playlists,
                key = { "pl_${it.id}" }
            ) { playlist ->
                val isSelected = uiState.selectedPlaylist?.id == playlist.id
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(48.dp)
                        .tvFocusClickable(
                            shape = RectangleShape,
                            defaultBackground = if (isSelected) Surface else Color.Transparent,
                            focusedBackground = Surface,
                            defaultBorder = Color.Transparent,
                            focusedBorder = Color.Transparent,
                            focusScale = 1.0f,
                            onClick = { onSelectPlaylist(playlist.id) }
                        )
                        .drawBehind {
                            if (isSelected) {
                                drawRect(
                                    color = Accent,
                                    size = Size(3.dp.toPx(), size.height)
                                )
                            }
                        }
                        .padding(start = 16.dp, end = 8.dp)
                        .testTag("playlist_item_${playlist.id}"),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.PlaylistPlay,
                        contentDescription = null,
                        tint = if (isSelected) Accent else TextSecondary,
                        modifier = Modifier.size(24.dp)
                    )
                    Text(
                        text = playlist.name,
                        fontFamily = FontFamily.Default,
                        fontSize = 14.sp,
                        fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Normal,
                        color = if (isSelected) Accent else TextSecondary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f)
                    )
                    IconButton(
                        onClick = { onRefreshPlaylist(playlist.id) },
                        modifier = Modifier.size(28.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Refresh,
                            contentDescription = "Atualizar lista",
                            tint = TextSecondary,
                            modifier = Modifier.size(16.dp)
                        )
                    }
                    IconButton(
                        onClick = { onDeletePlaylist(playlist.id) },
                        modifier = Modifier.size(28.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.DeleteOutline,
                            contentDescription = "Remover lista",
                            tint = TextSecondary,
                            modifier = Modifier.size(16.dp)
                        )
                    }
                }
            }
        }

        // Section: CATEGORIAS (no dividers between sections)
        Text(
            text = "CATEGORIAS",
            fontFamily = FontFamily.Default,
            fontSize = 10.sp,
            fontWeight = FontWeight.SemiBold,
            color = TextSecondary,
            modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 4.dp)
        )

        LazyColumn(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
        ) {
            item(key = "cat_all") {
                SidebarCategoryRow(
                    title = stringResource(R.string.all_channels),
                    count = uiState.totalChannelCount,
                    icon = Icons.Default.Tv,
                    selected = uiState.selectedCategory == SidebarCategory.All,
                    onClick = { onSelectCategory(SidebarCategory.All) },
                    modifier = Modifier.testTag("category_all")
                )
            }
            item(key = "cat_favorites") {
                SidebarCategoryRow(
                    title = stringResource(R.string.favorites),
                    count = uiState.favoriteCount,
                    icon = Icons.Default.Star,
                    selected = uiState.selectedCategory == SidebarCategory.Favorites,
                    onClick = { onSelectCategory(SidebarCategory.Favorites) },
                    modifier = Modifier.testTag("category_favorites")
                )
            }
            item(key = "cat_history") {
                SidebarCategoryRow(
                    title = stringResource(R.string.history),
                    count = uiState.historyCount,
                    icon = Icons.Default.History,
                    selected = uiState.selectedCategory == SidebarCategory.History,
                    onClick = { onSelectCategory(SidebarCategory.History) },
                    modifier = Modifier.testTag("category_history")
                )
            }

            items(
                items = uiState.groupCategories,
                key = { "group_${it.title}" }
            ) { item ->
                SidebarCategoryRow(
                    title = item.title,
                    count = item.count,
                    icon = Icons.Default.Folder,
                    selected = uiState.selectedCategory == item.category,
                    onClick = { onSelectCategory(item.category) }
                )
            }
        }

        Spacer(modifier = Modifier.height(8.dp))

        // Sidebar Footer: "+ Adicionar Lista" outlined button
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 16.dp, end = 16.dp, bottom = 16.dp)
                .height(48.dp)
                .tvFocusClickable(
                    shape = RoundedCornerShape(8.dp),
                    defaultBackground = Color.Transparent,
                    focusedBackground = AccentBlueGlow,
                    defaultBorder = Accent,
                    focusedBorder = Accent,
                    focusScale = 1.0f,
                    onClick = onOpenAddPlaylist
                )
                .testTag("add_playlist_button"),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = "+ ${stringResource(R.string.add_playlist)}",
                fontFamily = FontFamily.Default,
                fontSize = 14.sp,
                fontWeight = FontWeight.SemiBold,
                color = Accent,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

@Composable
private fun SidebarCategoryRow(
    title: String,
    count: Int,
    icon: ImageVector,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .height(48.dp)
            .tvFocusClickable(
                shape = RectangleShape,
                defaultBackground = if (selected) Surface else Color.Transparent,
                focusedBackground = Surface,
                defaultBorder = Color.Transparent,
                focusedBorder = Color.Transparent,
                focusScale = 1.0f,
                onClick = onClick
            )
            .drawBehind {
                if (selected) {
                    drawRect(
                        color = Accent,
                        size = Size(3.dp.toPx(), size.height)
                    )
                }
            }
            .padding(horizontal = 16.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = if (selected) TextPrimary else TextSecondary,
            modifier = Modifier.size(20.dp)
        )
        Spacer(modifier = Modifier.width(10.dp))
        Text(
            text = title,
            fontFamily = FontFamily.Default,
            fontSize = 14.sp,
            fontWeight = FontWeight.Medium,
            color = if (selected) TextPrimary else TextSecondary,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f)
        )
        Spacer(modifier = Modifier.width(6.dp))
        Text(
            text = count.toString(),
            fontFamily = FontFamily.Default,
            fontSize = 12.sp,
            fontWeight = FontWeight.Normal,
            color = TextSecondary
        )
    }
}

@Composable
private fun ChannelsPanel(
    uiState: HomeUiState,
    onSearchQueryChange: (String) -> Unit,
    onChannelClick: (ChannelEntity) -> Unit,
    onToggleFavorite: (ChannelEntity) -> Unit,
    onOpenAddPlaylist: () -> Unit,
    onClearSearch: () -> Unit,
    modifier: Modifier = Modifier
) {
    val focusManager = LocalFocusManager.current

    Column(
        modifier = modifier
            .background(CanvasBackground)
            .focusGroup()
    ) {
        // Header Search Bar: #1A1D27, 24dp pill shape, gray search icon, 44dp height, 12dp margin all sides
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp)
                .height(44.dp)
                .clip(RoundedCornerShape(24.dp))
                .background(Surface)
                .padding(horizontal = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Icon(
                imageVector = Icons.Default.Search,
                contentDescription = "Pesquisar",
                tint = TextSecondary,
                modifier = Modifier.size(20.dp)
            )
            Box(
                modifier = Modifier.weight(1f),
                contentAlignment = Alignment.CenterStart
            ) {
                if (uiState.searchQuery.isEmpty()) {
                    Text(
                        text = stringResource(R.string.search_hint),
                        fontFamily = FontFamily.Default,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Normal,
                        color = TextSecondary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                BasicTextField(
                    value = uiState.searchQuery,
                    onValueChange = onSearchQueryChange,
                    singleLine = true,
                    textStyle = TextStyle(
                        fontFamily = FontFamily.Default,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Normal,
                        color = TextPrimary
                    ),
                    cursorBrush = SolidColor(Accent),
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                    keyboardActions = KeyboardActions(
                        onSearch = { focusManager.clearFocus() }
                    ),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("channel_search_input")
                )
            }
            if (uiState.searchQuery.isNotEmpty()) {
                IconButton(
                    onClick = onClearSearch,
                    modifier = Modifier
                        .size(28.dp)
                        .testTag("clear_search_button")
                ) {
                    Icon(
                        imageVector = Icons.Default.Close,
                        contentDescription = "Limpar pesquisa",
                        tint = TextSecondary,
                        modifier = Modifier.size(16.dp)
                    )
                }
            }
        }

        if (uiState.channels.isEmpty()) {
            EmptyChannelsState(
                hasPlaylists = uiState.playlists.isNotEmpty(),
                hasSearchQuery = uiState.searchQuery.isNotBlank(),
                selectedCategory = uiState.selectedCategory,
                onOpenAddPlaylist = onOpenAddPlaylist,
                onClearSearch = onClearSearch,
                modifier = Modifier
                    .fillMaxSize()
                    .weight(1f)
            )
        } else {
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .weight(1f)
                    .testTag("channel_list"),
                contentPadding = PaddingValues(bottom = 12.dp)
            ) {
                items(
                    items = uiState.channels,
                    key = { it.id }
                ) { channel ->
                    ChannelRowCard(
                        channel = channel,
                        onClick = { onChannelClick(channel) },
                        onToggleFavorite = { onToggleFavorite(channel) }
                    )
                }
            }
        }
    }
}

@Composable
private fun ChannelRowCard(
    channel: ChannelEntity,
    onClick: () -> Unit,
    onToggleFavorite: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 4.dp)
            .height(72.dp)
            .tvFocusClickable(
                shape = RoundedCornerShape(8.dp),
                defaultBackground = Surface,
                focusedBackground = SurfaceFocus,
                defaultBorder = Color.Transparent,
                focusedBorder = Accent,
                focusScale = 1.0f,
                onClick = onClick
            )
            .padding(horizontal = 12.dp)
            .testTag("channel_card_${channel.id}"),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        ChannelLogoBox(
            logoUrl = channel.logoUrl,
            channelName = channel.name,
            size = 44.dp
        )

        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(2.dp)
        ) {
            Text(
                text = channel.name,
                fontFamily = FontFamily.Default,
                fontSize = 15.sp,
                fontWeight = FontWeight.SemiBold,
                color = TextPrimary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                text = channel.groupName,
                fontFamily = FontFamily.Default,
                fontSize = 12.sp,
                fontWeight = FontWeight.Normal,
                color = TextSecondary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }

        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(36.dp)
                    .tvFocusClickable(
                        shape = CircleShape,
                        defaultBackground = Color.Transparent,
                        focusedBackground = SurfaceFocus,
                        defaultBorder = Color.Transparent,
                        focusedBorder = Accent,
                        focusScale = 1.0f,
                        onClick = onToggleFavorite
                    )
                    .testTag("favorite_button_${channel.id}"),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = if (channel.isFavorite) Icons.Default.Star else Icons.Default.StarBorder,
                    contentDescription = if (channel.isFavorite) {
                        "Remover dos favoritos"
                    } else {
                        "Adicionar aos favoritos"
                    },
                    tint = if (channel.isFavorite) FavoriteGold else TextSecondary,
                    modifier = Modifier.size(18.dp)
                )
            }

            LiveBadge()
        }
    }
}

@Composable
private fun EmptyChannelsState(
    hasPlaylists: Boolean,
    hasSearchQuery: Boolean,
    selectedCategory: SidebarCategory,
    onOpenAddPlaylist: () -> Unit,
    onClearSearch: () -> Unit,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier,
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Icon(
                imageVector = Icons.Default.Tv,
                contentDescription = null,
                tint = Accent,
                modifier = Modifier.size(64.dp)
            )

            val title = when {
                !hasPlaylists -> "Nenhuma lista configurada"
                hasSearchQuery -> stringResource(R.string.no_channels_title)
                selectedCategory == SidebarCategory.Favorites -> "Sem canais favoritos"
                selectedCategory == SidebarCategory.History -> "Histórico vazio"
                else -> stringResource(R.string.no_channels_title)
            }

            val subtitle = when {
                !hasPlaylists -> stringResource(R.string.no_channels_subtitle)
                hasSearchQuery -> "Tente pesquisar por outro nome ou categoria."
                selectedCategory == SidebarCategory.Favorites -> "Toque na estrela de um canal para adicioná-lo aos favoritos."
                selectedCategory == SidebarCategory.History -> "Os canais assistidos aparecerão aqui."
                else -> "Esta categoria não possui canais disponíveis."
            }

            Text(
                text = title,
                fontFamily = FontFamily.Default,
                fontSize = 18.sp,
                fontWeight = FontWeight.SemiBold,
                color = TextPrimary,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(horizontal = 32.dp)
            )

            Text(
                text = subtitle,
                fontFamily = FontFamily.Default,
                fontSize = 14.sp,
                fontWeight = FontWeight.Normal,
                color = TextSecondary,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(horizontal = 32.dp)
            )

            if (!hasPlaylists) {
                Spacer(modifier = Modifier.height(4.dp))
                OutlinedButton(
                    onClick = onOpenAddPlaylist,
                    shape = RoundedCornerShape(8.dp),
                    border = BorderStroke(1.dp, Accent),
                    modifier = Modifier
                        .width(160.dp)
                        .height(48.dp)
                        .testTag("empty_add_playlist_button")
                ) {
                    Text(
                        text = "+ Adicionar",
                        fontFamily = FontFamily.Default,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = Accent
                    )
                }
            } else if (hasSearchQuery) {
                Spacer(modifier = Modifier.height(4.dp))
                OutlinedButton(
                    onClick = onClearSearch,
                    shape = RoundedCornerShape(8.dp),
                    border = BorderStroke(1.dp, Accent),
                    modifier = Modifier
                        .width(160.dp)
                        .height(48.dp)
                ) {
                    Text(
                        text = "Limpar",
                        fontFamily = FontFamily.Default,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = Accent
                    )
                }
            }
        }
    }
}
