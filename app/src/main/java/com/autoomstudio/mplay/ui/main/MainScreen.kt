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
import androidx.compose.foundation.layout.fillMaxSize
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
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import com.autoomstudio.mplay.R
import com.autoomstudio.mplay.data.model.Song
import com.autoomstudio.mplay.playback.NowPlayingState
import com.autoomstudio.mplay.ui.components.ComingSoon
import com.autoomstudio.mplay.ui.components.MPlayTopBar
import com.autoomstudio.mplay.ui.library.LibraryScreen
import com.autoomstudio.mplay.ui.library.LibraryUiState
import com.autoomstudio.mplay.ui.playback.MiniPlayer
import com.autoomstudio.mplay.ui.playback.NowPlayingScreen
import com.autoomstudio.mplay.ui.playback.PlayerActions
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.launch

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
    onSongClick: (Song) -> Unit,
    modifier: Modifier = Modifier,
) {
    var destination by rememberSaveable { mutableStateOf(Destination.Library) }
    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    val comingSoon = stringResource(R.string.coming_soon_snackbar)
    val showComingSoon: () -> Unit = {
        scope.launch {
            snackbarHostState.currentSnackbarData?.dismiss()
            snackbarHostState.showSnackbar(comingSoon)
        }
    }

    // Keeps the last song on screen while the player surfaces animate out after the queue is cleared.
    val lastNowPlaying = remember { mutableStateOf(nowPlaying) }
        .apply { if (nowPlaying != null) value = nowPlaying }
        .value

    Box(modifier = modifier) {
        Scaffold(
            containerColor = MaterialTheme.colorScheme.background,
            topBar = { MPlayTopBar(onSearchClick = showComingSoon, onSortClick = showComingSoon) },
            bottomBar = {
                Column(Modifier.background(MaterialTheme.colorScheme.surfaceContainerLow)) {
                    AnimatedVisibility(
                        visible = nowPlaying != null,
                        enter = slideInVertically { it } + fadeIn(),
                        exit = slideOutVertically { it } + fadeOut(),
                    ) {
                        lastNowPlaying?.let { state ->
                            MiniPlayer(
                                state = state,
                                position = position,
                                actions = playerActions,
                                onExpand = { onShowNowPlayingChange(true) },
                            )
                        }
                    }
                    MPlayNavigationBar(selected = destination, onSelect = { destination = it })
                }
            },
            snackbarHost = { SnackbarHost(snackbarHostState) },
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
                    onSongClick = onSongClick,
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

        AnimatedVisibility(
            visible = showNowPlaying && nowPlaying != null,
            enter = slideInVertically { it },
            exit = slideOutVertically { it },
        ) {
            lastNowPlaying?.let { state ->
                NowPlayingScreen(
                    state = state,
                    position = position,
                    actions = playerActions,
                    onCollapse = { onShowNowPlayingChange(false) },
                )
            }
        }
    }
}

@Composable
private fun MPlayNavigationBar(selected: Destination, onSelect: (Destination) -> Unit) {
    NavigationBar(containerColor = MaterialTheme.colorScheme.surfaceContainerLow) {
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
