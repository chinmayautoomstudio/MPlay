package com.autoomstudio.mplay.ui.playlist

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.PlaylistAdd
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.autoomstudio.mplay.R
import com.autoomstudio.mplay.data.model.Playlist
import com.autoomstudio.mplay.data.model.Song
import com.autoomstudio.mplay.data.playlist.PlaylistQueries
import com.autoomstudio.mplay.ui.components.ComingSoon
import com.autoomstudio.mplay.ui.library.DetailBackButton
import com.autoomstudio.mplay.ui.library.DetailHeader
import com.autoomstudio.mplay.ui.library.SongActions
import com.autoomstudio.mplay.ui.library.songCountText

@Composable
fun PlaylistDetailScreen(
    playlist: Playlist,
    currentSongId: Long?,
    isPlaying: Boolean,
    onBack: () -> Unit,
    onPlay: (songs: List<Song>, start: Song) -> Unit,
    onShuffle: (songs: List<Song>) -> Unit,
    actions: SongActions,
    onRemove: (Song) -> Unit,
    onReorder: (List<Song>) -> Unit,
    modifier: Modifier = Modifier,
) {
    // Local copy so rows move instantly while dragging; saved once on drop.
    var order by remember(playlist.songs) { mutableStateOf(playlist.songs) }
    val listState = rememberLazyListState()
    val reorderState = rememberSongReorderState(listState)

    LazyColumn(modifier = modifier, state = listState, contentPadding = PaddingValues(bottom = 8.dp)) {
        item(key = "back") { DetailBackButton(onBack = onBack) }
        item(key = "header") {
            DetailHeader(
                title = playlist.name,
                subtitle = songCountText(order.size),
                onPlay = { onPlay(order, order.first()) },
                onShuffle = { onShuffle(order) },
                actionsEnabled = order.isNotEmpty(),
            ) {
                PlaylistArtwork(playlist, size = 200.dp)
            }
        }
        if (playlist.unavailableCount > 0) {
            item(key = "unavailable") {
                Text(
                    text = pluralStringResource(
                        R.plurals.unavailable_count,
                        playlist.unavailableCount,
                        playlist.unavailableCount,
                    ),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 8.dp),
                )
            }
        }
        if (order.isEmpty()) {
            item(key = "empty") {
                ComingSoon(
                    icon = Icons.AutoMirrored.Outlined.PlaylistAdd,
                    title = stringResource(R.string.playlist_empty_title),
                    message = stringResource(R.string.playlist_empty_message),
                    animation = R.raw.anim_empty_playlist,
                )
            }
        }
        reorderableSongItems(
            songs = order,
            state = reorderState,
            currentSongId = currentSongId,
            isPlaying = isPlaying,
            onSongClick = { onPlay(order, it) },
            actions = actions,
            onRemove = onRemove,
            onMove = { from, to -> order = PlaylistQueries.moveItem(order, from, to) },
            onDragEnd = { if (order != playlist.songs) onReorder(order) },
        )
    }
}
