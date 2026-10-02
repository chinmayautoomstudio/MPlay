package com.autoomstudio.mplay.ui.library

import androidx.activity.compose.BackHandler
import androidx.annotation.StringRes
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.CircularProgressIndicator
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
import androidx.compose.runtime.snapshots.SnapshotStateList
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import com.autoomstudio.mplay.R
import com.autoomstudio.mplay.data.library.LibraryQueries
import com.autoomstudio.mplay.data.model.Album
import com.autoomstudio.mplay.data.model.Artist
import com.autoomstudio.mplay.data.model.Song

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
    modifier: Modifier = Modifier,
) {
    var selectedIndex by rememberSaveable { mutableIntStateOf(0) }
    val backStack = rememberSaveable(saver = RouteStackSaver) { mutableStateListOf() }
    val route = backStack.lastOrNull()
    BackHandler(enabled = route != null) { backStack.removeAt(backStack.lastIndex) }

    if (state is LibraryUiState.Loading || state is LibraryUiState.NoPermission) {
        Box(modifier = modifier, contentAlignment = Alignment.Center) { CircularProgressIndicator() }
        return
    }
    val content = state as? LibraryUiState.Content
    val allSongs = content?.allSongs.orEmpty()
    val pop: () -> Unit = { if (backStack.isNotEmpty()) backStack.removeAt(backStack.lastIndex) }

    when (route) {
        null -> LibraryTabs(
            selectedIndex = selectedIndex,
            onSelect = { selectedIndex = it },
            content = content,
            searchQuery = searchQuery,
            currentSongId = currentSongId,
            isPlaying = isPlaying,
            isRefreshing = isRefreshing,
            onRefresh = onRefresh,
            onPlay = onPlay,
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
        when (LibraryTab.entries[selectedIndex]) {
            LibraryTab.Songs -> SongsTab(
                content = content,
                searchQuery = searchQuery,
                currentSongId = currentSongId,
                isPlaying = isPlaying,
                isRefreshing = isRefreshing,
                onRefresh = onRefresh,
                onPlay = onPlay,
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
