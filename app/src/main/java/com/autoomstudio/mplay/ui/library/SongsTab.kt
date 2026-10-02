package com.autoomstudio.mplay.ui.library

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.material3.pulltorefresh.PullToRefreshDefaults
import androidx.compose.material3.pulltorefresh.rememberPullToRefreshState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.autoomstudio.mplay.data.model.Song

/** [content] is null when the device has no music. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SongsTab(
    content: LibraryUiState.Content?,
    searchQuery: String,
    currentSongId: Long?,
    isPlaying: Boolean,
    isRefreshing: Boolean,
    onRefresh: () -> Unit,
    onPlay: (songs: List<Song>, start: Song) -> Unit,
    actions: SongActions,
    modifier: Modifier = Modifier,
) {
    val songs = content?.songs.orEmpty()

    val pullState = rememberPullToRefreshState()
    PullToRefreshBox(
        isRefreshing = isRefreshing,
        onRefresh = onRefresh,
        modifier = modifier,
        state = pullState,
        indicator = {
            PullToRefreshDefaults.Indicator(
                state = pullState,
                isRefreshing = isRefreshing,
                modifier = Modifier.align(Alignment.TopCenter),
                containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                color = MaterialTheme.colorScheme.primary,
            )
        },
    ) {
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(vertical = 8.dp),
        ) {
            if (songs.isNotEmpty()) {
                songItems(
                    songs = songs,
                    currentSongId = currentSongId,
                    isPlaying = isPlaying,
                    onSongClick = { onPlay(songs, it) },
                    actions = actions,
                )
            } else {
                item {
                    LibraryEmptyMessage(
                        isFiltered = content?.isFiltered == true,
                        searchQuery = searchQuery,
                        modifier = Modifier.fillParentMaxSize(),
                    )
                }
            }
        }
    }
}
