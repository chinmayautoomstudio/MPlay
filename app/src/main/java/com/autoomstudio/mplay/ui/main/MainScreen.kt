package com.autoomstudio.mplay.ui.main

import androidx.annotation.StringRes
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.QueueMusic
import androidx.compose.material.icons.automirrored.outlined.QueueMusic
import androidx.compose.material.icons.filled.LibraryMusic
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.outlined.LibraryMusic
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.res.stringResource
import com.autoomstudio.mplay.R
import com.autoomstudio.mplay.data.library.SongSortOrder
import com.autoomstudio.mplay.data.model.Song
import com.autoomstudio.mplay.playback.NowPlayingState
import com.autoomstudio.mplay.ui.components.ComingSoon
import com.autoomstudio.mplay.ui.components.MPlayTopBar
import com.autoomstudio.mplay.ui.library.LibraryScreen
import com.autoomstudio.mplay.ui.library.LibraryUiState
import com.autoomstudio.mplay.ui.playback.ExpandablePlayer
import com.autoomstudio.mplay.ui.playback.MiniPlayerSlotHeight
import com.autoomstudio.mplay.ui.playback.PlayerActions
import com.autoomstudio.mplay.ui.playback.PlayerSheetValue
import com.autoomstudio.mplay.ui.playback.animateSheetTo
import com.autoomstudio.mplay.ui.playback.expandProgress
import com.autoomstudio.mplay.ui.playback.rememberPlayerSheetFlingBehavior
import com.autoomstudio.mplay.ui.playback.rememberPlayerSheetState
import com.autoomstudio.mplay.ui.playback.updateSheetAnchors
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.drop

private enum class Destination(
    @StringRes val label: Int,
    val selectedIcon: ImageVector,
    val icon: ImageVector,
) {
    Library(R.string.nav_library, Icons.Filled.LibraryMusic, Icons.Outlined.LibraryMusic),
    Playlists(
        R.string.nav_playlists,
        Icons.AutoMirrored.Filled.QueueMusic,
        Icons.AutoMirrored.Outlined.QueueMusic,
    ),
    Settings(R.string.nav_settings, Icons.Filled.Settings, Icons.Outlined.Settings),
}

@Composable
fun MainScreen(
    libraryState: LibraryUiState,
    nowPlaying: NowPlayingState?,
    position: Flow<Long>,
    playerActions: PlayerActions,
    showNowPlaying: Boolean,
    onShowNowPlayingChange: (Boolean) -> Unit,
    isRefreshing: Boolean,
    onRefresh: () -> Unit,
    searchQuery: String,
    onSearchQueryChange: (String) -> Unit,
    onClearSearch: () -> Unit,
    sortOrder: SongSortOrder,
    onSortOrderChange: (SongSortOrder) -> Unit,
    onPlay: (songs: List<Song>, start: Song) -> Unit,
    onShuffle: (songs: List<Song>) -> Unit,
    modifier: Modifier = Modifier,
) {
    var destination by rememberSaveable { mutableStateOf(Destination.Library) }
    var searchActive by rememberSaveable { mutableStateOf(false) }
    val closeSearch = {
        searchActive = false
        onClearSearch()
    }

    // Keeps the last song on screen while the player surfaces animate out after the queue is cleared.
    val lastNowPlaying = remember { mutableStateOf(nowPlaying) }
        .apply { if (nowPlaying != null) value = nowPlaying }
        .value

    val sheetExpanded = showNowPlaying && nowPlaying != null
    val sheetState = rememberPlayerSheetState(initiallyExpanded = sheetExpanded)
    val sheetFling = rememberPlayerSheetFlingBehavior(sheetState)
    LaunchedEffect(sheetExpanded) {
        val target = if (sheetExpanded) PlayerSheetValue.Expanded else PlayerSheetValue.Collapsed
        if (sheetState.settledValue != target || sheetState.targetValue != target) {
            sheetState.animateSheetTo(target)
        }
    }
    val hasNowPlaying by rememberUpdatedState(nowPlaying != null)
    val currentOnShowNowPlayingChange by rememberUpdatedState(onShowNowPlayingChange)
    LaunchedEffect(sheetState) {
        snapshotFlow { sheetState.settledValue }
            .drop(1)
            .collect { value ->
                if (hasNowPlaying) currentOnShowNowPlayingChange(value == PlayerSheetValue.Expanded)
            }
    }

    var collapsedTop by remember { mutableFloatStateOf(Float.NaN) }
    var miniSlotAttached by remember { mutableStateOf(false) }

    Box(modifier = modifier) {
        Scaffold(
            containerColor = MaterialTheme.colorScheme.background,
            topBar = {
                MPlayTopBar(
                    showActions = destination == Destination.Library,
                    searchActive = searchActive && destination == Destination.Library,
                    searchQuery = searchQuery,
                    onSearchQueryChange = onSearchQueryChange,
                    onOpenSearch = { searchActive = true },
                    onCloseSearch = closeSearch,
                    sortOrder = sortOrder,
                    onSortOrderChange = onSortOrderChange,
                )
            },
            bottomBar = {
                Column(Modifier.background(MaterialTheme.colorScheme.surfaceContainerLow)) {
                    AnimatedVisibility(
                        visible = nowPlaying != null,
                        enter = slideInVertically { it } + fadeIn(),
                        exit = slideOutVertically { it } + fadeOut(),
                    ) {
                        DisposableEffect(Unit) {
                            miniSlotAttached = true
                            onDispose { miniSlotAttached = false }
                        }
                        Spacer(
                            Modifier
                                .fillMaxWidth()
                                .height(MiniPlayerSlotHeight)
                                .onGloballyPositioned { coordinates ->
                                    collapsedTop = coordinates.positionInRoot().y
                                    sheetState.updateSheetAnchors(collapsedTop)
                                },
                        )
                    }
                    MPlayNavigationBar(
                        selected = destination,
                        onSelect = {
                            if (it != Destination.Library && searchActive) closeSearch()
                            destination = it
                        },
                        modifier = Modifier.graphicsLayer {
                            translationY = sheetState.expandProgress * size.height
                        },
                    )
                }
            },
        ) { innerPadding ->
            val contentModifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
            when (destination) {
                Destination.Library -> LibraryScreen(
                    state = libraryState,
                    currentSongId = nowPlaying?.songId,
                    isPlaying = nowPlaying?.isPlaying == true,
                    isRefreshing = isRefreshing,
                    onRefresh = onRefresh,
                    searchQuery = searchQuery,
                    onPlay = onPlay,
                    onShuffle = onShuffle,
                    modifier = contentModifier,
                )

                Destination.Playlists -> ComingSoon(
                    icon = Icons.AutoMirrored.Outlined.QueueMusic,
                    title = stringResource(R.string.nav_playlists),
                    message = stringResource(R.string.coming_soon_playlists),
                    modifier = contentModifier,
                )

                Destination.Settings -> ComingSoon(
                    icon = Icons.Outlined.Settings,
                    title = stringResource(R.string.nav_settings),
                    message = stringResource(R.string.coming_soon_settings),
                    modifier = contentModifier,
                )
            }
        }

        val state = lastNowPlaying
        if (state != null && miniSlotAttached && !collapsedTop.isNaN()) {
            ExpandablePlayer(
                state = state,
                position = position,
                actions = playerActions,
                sheetState = sheetState,
                sheetFling = sheetFling,
                collapsedTop = { collapsedTop },
                onExpand = { onShowNowPlayingChange(true) },
                onCollapse = { onShowNowPlayingChange(false) },
            )
        }
    }
}

@Composable
private fun MPlayNavigationBar(
    selected: Destination,
    onSelect: (Destination) -> Unit,
    modifier: Modifier = Modifier,
) {
    NavigationBar(modifier = modifier, containerColor = MaterialTheme.colorScheme.surfaceContainerLow) {
        Destination.entries.forEach { item ->
            val isSelected = item == selected
            NavigationBarItem(
                selected = isSelected,
                onClick = { onSelect(item) },
                icon = {
                    Icon(
                        imageVector = if (isSelected) item.selectedIcon else item.icon,
                        contentDescription = null,
                    )
                },
                label = { Text(stringResource(item.label)) },
                colors = NavigationBarItemDefaults.colors(
                    selectedIconColor = MaterialTheme.colorScheme.primary,
                    selectedTextColor = MaterialTheme.colorScheme.primary,
                    indicatorColor = Color.Transparent,
                    unselectedIconColor = MaterialTheme.colorScheme.onSurfaceVariant,
                    unselectedTextColor = MaterialTheme.colorScheme.onSurfaceVariant,
                ),
            )
        }
    }
}
