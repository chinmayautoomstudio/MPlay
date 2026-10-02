package com.autoomstudio.mplay.ui.playlist

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.QueueMusic
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.autoomstudio.mplay.R
import com.autoomstudio.mplay.data.model.Playlist
import com.autoomstudio.mplay.data.model.Song
import com.autoomstudio.mplay.ui.components.ComingSoon
import com.autoomstudio.mplay.ui.library.SongActions
import com.autoomstudio.mplay.ui.library.songCountText

/** [playlists] is null while loading. */
@Composable
fun PlaylistsScreen(
    playlists: List<Playlist>?,
    currentSongId: Long?,
    isPlaying: Boolean,
    playlistActions: PlaylistActions,
    songActions: SongActions,
    onPlay: (songs: List<Song>, start: Song) -> Unit,
    onShuffle: (songs: List<Song>) -> Unit,
    modifier: Modifier = Modifier,
    /** False while the full player covers this screen, so Back closes the player first. */
    backEnabled: Boolean = true,
) {
    var selectedId by rememberSaveable { mutableStateOf<Long?>(null) }
    var showCreate by rememberSaveable { mutableStateOf(false) }
    var renameId by rememberSaveable { mutableStateOf<Long?>(null) }
    var deleteId by rememberSaveable { mutableStateOf<Long?>(null) }
    BackHandler(enabled = backEnabled && selectedId != null) { selectedId = null }

    if (playlists == null) {
        Box(modifier = modifier, contentAlignment = Alignment.Center) { CircularProgressIndicator() }
        return
    }
    val byId = remember(playlists) { playlists.associateBy { it.id } }

    val selected = selectedId?.let { byId[it] }
    if (selectedId != null && selected == null) {
        LaunchedEffect(selectedId) { selectedId = null }
    }
    if (selected != null) {
        PlaylistDetailScreen(
            playlist = selected,
            currentSongId = currentSongId,
            isPlaying = isPlaying,
            onBack = { selectedId = null },
            onPlay = onPlay,
            onShuffle = onShuffle,
            actions = songActions,
            onRemove = { playlistActions.removeFromPlaylist(selected, it) },
            onReorder = { playlistActions.reorderPlaylist(selected, it) },
            modifier = modifier,
        )
    } else {
        Box(modifier = modifier) {
            if (playlists.isEmpty()) {
                ComingSoon(
                    icon = Icons.AutoMirrored.Outlined.QueueMusic,
                    title = stringResource(R.string.playlists_empty_title),
                    message = stringResource(R.string.playlists_empty_message),
                )
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(top = 8.dp, bottom = 96.dp),
                ) {
                    items(items = playlists, key = { it.id }) { playlist ->
                        PlaylistListRow(
                            title = playlist.name,
                            subtitle = songCountText(playlist.songs.size),
                            onClick = { selectedId = playlist.id },
                            leading = { PlaylistArtwork(playlist, size = 52.dp) },
                            trailing = {
                                PlaylistMenu(
                                    playlist = playlist,
                                    onRename = { renameId = playlist.id },
                                    onDelete = { deleteId = playlist.id },
                                )
                            },
                            modifier = Modifier.animateItem(),
                        )
                    }
                }
            }
            ExtendedFloatingActionButton(
                onClick = { showCreate = true },
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(16.dp),
            ) {
                Icon(Icons.Filled.Add, contentDescription = null)
                Spacer(Modifier.width(12.dp))
                Text(stringResource(R.string.playlist_new))
            }
        }
    }

    if (showCreate) {
        PlaylistNameDialog(
            title = stringResource(R.string.playlist_new),
            confirmLabel = stringResource(R.string.playlist_create),
            onConfirm = {
                playlistActions.createPlaylist(it)
                showCreate = false
            },
            onDismiss = { showCreate = false },
        )
    }
    renameId?.let { byId[it] }?.let { playlist ->
        PlaylistNameDialog(
            title = stringResource(R.string.playlist_rename_title),
            confirmLabel = stringResource(R.string.playlist_rename),
            initialName = playlist.name,
            onConfirm = {
                playlistActions.renamePlaylist(playlist, it)
                renameId = null
            },
            onDismiss = { renameId = null },
        )
    }
    deleteId?.let { byId[it] }?.let { playlist ->
        AlertDialog(
            onDismissRequest = { deleteId = null },
            title = { Text(stringResource(R.string.playlist_delete_title)) },
            text = { Text(stringResource(R.string.playlist_delete_message, playlist.name)) },
            confirmButton = {
                TextButton(onClick = {
                    playlistActions.deletePlaylist(playlist)
                    deleteId = null
                }) { Text(stringResource(R.string.playlist_delete)) }
            },
            dismissButton = {
                TextButton(onClick = { deleteId = null }) { Text(stringResource(R.string.action_cancel)) }
            },
        )
    }
}

@Composable
private fun PlaylistMenu(playlist: Playlist, onRename: () -> Unit, onDelete: () -> Unit) {
    var expanded by remember { mutableStateOf(false) }
    Box {
        IconButton(onClick = { expanded = true }) {
            Icon(
                imageVector = Icons.Filled.MoreVert,
                contentDescription = stringResource(R.string.action_more_options, playlist.name),
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        DropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false },
            containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
        ) {
            DropdownMenuItem(
                text = { Text(stringResource(R.string.playlist_rename)) },
                onClick = {
                    expanded = false
                    onRename()
                },
            )
            DropdownMenuItem(
                text = { Text(stringResource(R.string.playlist_delete)) },
                onClick = {
                    expanded = false
                    onDelete()
                },
            )
        }
    }
}
