package com.autoomstudio.mp3studio.ui.library

import androidx.activity.compose.BackHandler
import androidx.annotation.StringRes
import androidx.compose.animation.AnimatedContent
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.PrimaryTabRow
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.listSaver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.snapshots.SnapshotStateList
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import com.autoomstudio.mp3studio.R
import com.autoomstudio.mp3studio.data.library.LibraryQueries
import com.autoomstudio.mp3studio.data.model.Album
import com.autoomstudio.mp3studio.data.model.Artist
import com.autoomstudio.mp3studio.data.model.Song
import com.autoomstudio.mp3studio.ui.components.LoadingIndicator
import com.autoomstudio.mp3studio.ui.theme.fadeThrough
import com.autoomstudio.mp3studio.ui.theme.sharedAxisX
import kotlinx.coroutines.flow.drop

private enum class LibraryTab(@StringRes val label: Int) {
    Songs(R.string.tab_songs),
    Albums(R.string.tab_albums),
    Artists(R.string.tab_artists),
}

/** Screens stacked on top of the tabs. */
private sealed interface LibraryRoute {
    data class AlbumRoute(val albumId: Long) : LibraryRoute
    data class ArtistRoute(val name: String) : LibraryRoute
}

private const val ALBUM_PREFIX = "album:"
private const val ARTIST_PREFIX = "artist:"

private val RouteStackSaver = listSaver<SnapshotStateList<LibraryRoute>, String>(
    save = { stack ->
        stack.map { route ->
            when (route) {
                is LibraryRoute.AlbumRoute -> ALBUM_PREFIX + route.albumId
                is LibraryRoute.ArtistRoute -> ARTIST_PREFIX + route.name
            }
        }
    },
    restore = { saved ->
        saved.mapNotNull { entry ->
            when {
                entry.startsWith(ALBUM_PREFIX) ->
                    entry.removePrefix(ALBUM_PREFIX).toLongOrNull()?.let { LibraryRoute.AlbumRoute(it) }
                entry.startsWith(ARTIST_PREFIX) -> LibraryRoute.ArtistRoute(entry.removePrefix(ARTIST_PREFIX))
                else -> null
            }
        }.toMutableStateList()
    },
)

private fun <T> List<T>.toMutableStateList(): SnapshotStateList<T> = mutableStateListOf<T>().also { it.addAll(this) }

@Composable
fun LibraryScreen(
    state: LibraryUiState,
    searchQuery: String,
    currentSongId: Long?,
    isPlaying: Boolean,
    isRefreshing: Boolean,
    onRefresh: () -> Unit,
    onPlay: (songs: List<Song>, start: Song) -> Unit,
    onShuffle: (songs: List<Song>) -> Unit,
    actions: SongActions,
    selection: SongSelection,
    modifier: Modifier = Modifier,
    /** False while the full player covers this screen, so Back closes the player first. */
    backEnabled: Boolean = true,
) {
    var selectedIndex by rememberSaveable { mutableIntStateOf(0) }
    val backStack = rememberSaveable(saver = RouteStackSaver) { mutableStateListOf() }
    val route = backStack.lastOrNull()
    BackHandler(enabled = backEnabled && route != null) { backStack.removeAt(backStack.lastIndex) }
    LaunchedEffect(selection) {
        snapshotFlow { selectedIndex to backStack.toList() }.drop(1).collect { selection.clear() }
    }

    val isLoading = state is LibraryUiState.Loading || state is LibraryUiState.NoPermission
    AnimatedContent(
        targetState = isLoading,
        transitionSpec = { fadeThrough() },
        label = "libraryLoading",
        modifier = modifier,
    ) { loading ->
        if (loading) {
            LoadingIndicator(modifier = Modifier.fillMaxSize())
        } else {
            AnimatedContent(
                targetState = RouteEntry(route, backStack.size),
                transitionSpec = { sharedAxisX(forward = targetState.depth > initialState.depth) },
                label = "libraryRoute",
            ) { entry ->
                LibraryRouteContent(
                    route = entry.route,
                    state = state,
                    selectedIndex = selectedIndex,
                    onSelectTab = { selectedIndex = it },
                    backStack = backStack,
                    searchQuery = searchQuery,
                    currentSongId = currentSongId,
                    isPlaying = isPlaying,
                    isRefreshing = isRefreshing,
                    onRefresh = onRefresh,
                    onPlay = onPlay,
                    onShuffle = onShuffle,
                    actions = actions,
                    selection = selection,
                )
            }
        }
    }
}

private data class RouteEntry(val route: LibraryRoute?, val depth: Int)

@Composable
private fun LibraryRouteContent(
    route: LibraryRoute?,
    state: LibraryUiState,
    selectedIndex: Int,
    onSelectTab: (Int) -> Unit,
    backStack: SnapshotStateList<LibraryRoute>,
    searchQuery: String,
    currentSongId: Long?,
    isPlaying: Boolean,
    isRefreshing: Boolean,
    onRefresh: () -> Unit,
    onPlay: (songs: List<Song>, start: Song) -> Unit,
    onShuffle: (songs: List<Song>) -> Unit,
    actions: SongActions,
    selection: SongSelection,
) {
    val modifier = Modifier.fillMaxSize()
    val content = state as? LibraryUiState.Content
    val allSongs = content?.allSongs.orEmpty()
    val pop: () -> Unit = {
        if (backStack.lastOrNull() == route && backStack.isNotEmpty()) backStack.removeAt(backStack.lastIndex)
    }

    when (route) {
        null -> LibraryTabs(
            selectedIndex = selectedIndex,
            onSelect = onSelectTab,
            content = content,
            searchQuery = searchQuery,
            currentSongId = currentSongId,
            isPlaying = isPlaying,
            isRefreshing = isRefreshing,
            onRefresh = onRefresh,
            onPlay = onPlay,
            actions = actions,
            selection = selection,
            onAlbumClick = { backStack.add(LibraryRoute.AlbumRoute(it.id)) },
            onArtistClick = { backStack.add(LibraryRoute.ArtistRoute(it.name)) },
            modifier = modifier,
        )

        is LibraryRoute.AlbumRoute -> {
            val album = remember(allSongs, route.albumId) {
                LibraryQueries.groupAlbums(allSongs.filter { it.albumId == route.albumId }).firstOrNull()
            }
            if (album == null) {
                LaunchedEffect(route) { pop() }
            } else {
                AlbumDetailScreen(
                    album = album,
                    currentSongId = currentSongId,
                    isPlaying = isPlaying,
                    onBack = pop,
                    onPlay = onPlay,
                    onShuffle = onShuffle,
                    actions = actions,
                    selection = selection,
                    modifier = modifier,
                )
            }
        }

        is LibraryRoute.ArtistRoute -> {
            val artist = remember(allSongs, route.name) {
                LibraryQueries.groupArtists(allSongs.filter { it.artist == route.name }).firstOrNull()
            }
            if (artist == null) {
                LaunchedEffect(route) { pop() }
            } else {
                ArtistDetailScreen(
                    artist = artist,
                    currentSongId = currentSongId,
                    isPlaying = isPlaying,
                    onBack = pop,
                    onAlbumClick = { backStack.add(LibraryRoute.AlbumRoute(it.id)) },
                    onPlay = onPlay,
                    onShuffle = onShuffle,
                    actions = actions,
                    selection = selection,
                    modifier = modifier,
                )
            }
        }
    }
}

@Composable
private fun LibraryTabs(
    selectedIndex: Int,
    onSelect: (Int) -> Unit,
    content: LibraryUiState.Content?,
    searchQuery: String,
    currentSongId: Long?,
    isPlaying: Boolean,
    isRefreshing: Boolean,
    onRefresh: () -> Unit,
    onPlay: (songs: List<Song>, start: Song) -> Unit,
    actions: SongActions,
    selection: SongSelection,
    onAlbumClick: (Album) -> Unit,
    onArtistClick: (Artist) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier) {
        PrimaryTabRow(
            selectedTabIndex = selectedIndex,
            containerColor = MaterialTheme.colorScheme.background,
            contentColor = MaterialTheme.colorScheme.primary,
            divider = { HorizontalDivider(color = MaterialTheme.colorScheme.outline) },
        ) {
            LibraryTab.entries.forEachIndexed { index, tab ->
                Tab(
                    selected = index == selectedIndex,
                    onClick = { onSelect(index) },
                    text = {
                        Text(
                            text = stringResource(tab.label),
                            style = MaterialTheme.typography.titleSmall,
                        )
                    },
                    selectedContentColor = MaterialTheme.colorScheme.primary,
                    unselectedContentColor = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }

        val contentModifier = Modifier.fillMaxSize()
        AnimatedContent(
            targetState = selectedIndex,
            transitionSpec = { sharedAxisX(forward = targetState > initialState) },
            label = "libraryTab",
        ) { index ->
            when (LibraryTab.entries[index]) {
                LibraryTab.Songs -> SongsTab(
                    content = content,
                    searchQuery = searchQuery,
                    currentSongId = currentSongId,
                    isPlaying = isPlaying,
                    isRefreshing = isRefreshing,
                    onRefresh = onRefresh,
                    onPlay = onPlay,
                    actions = actions,
                    selection = selection,
                    modifier = contentModifier,
                )

                LibraryTab.Albums -> AlbumsTab(
                    content = content,
                    searchQuery = searchQuery,
                    onAlbumClick = onAlbumClick,
                    modifier = contentModifier,
                )

                LibraryTab.Artists -> ArtistsTab(
                    content = content,
                    searchQuery = searchQuery,
                    onArtistClick = onArtistClick,
                    modifier = contentModifier,
                )
            }
        }
    }
}
