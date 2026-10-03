package com.autoomstudio.mplay.ui.library

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.outlined.Circle
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.autoomstudio.mplay.R
import com.autoomstudio.mplay.data.model.Song
import com.autoomstudio.mplay.ui.common.formatDuration
import com.autoomstudio.mplay.ui.components.ArtworkImage
import com.autoomstudio.mplay.ui.components.NowPlayingBars

/** Actions offered by every song row's three-dot menu. */
@Immutable
data class SongActions(
    val onAddToPlaylist: (Song) -> Unit,
    val onPlayNext: (Song) -> Unit,
    val onAddToQueue: (Song) -> Unit,
    val onCutAndSave: (Song) -> Unit,
    val onSetAsRingtone: (Song) -> Unit,
    val onShowInfo: (Song) -> Unit,
    val onDelete: (Song) -> Unit,
)

/** Song rows shared by the Songs tab and the album and artist screens. */
fun LazyListScope.songItems(
    songs: List<Song>,
    currentSongId: Long?,
    isPlaying: Boolean,
    onSongClick: (Song) -> Unit,
    actions: SongActions,
    showTrackNumbers: Boolean = false,
    selection: SongSelection? = null,
) {
    items(items = songs, key = { it.id }) { song ->
        val isCurrent = song.id == currentSongId
        SongRow(
            song = song,
            isCurrent = isCurrent,
            isPlaying = isCurrent && isPlaying,
            showTrackNumber = showTrackNumbers,
            onClick = { onSongClick(song) },
            actions = actions,
            selection = selection,
        )
    }
}

/** Shows [SongInfoDialog] for the song with [songId] while it is still in [songs]. */
@Composable
fun SongInfoHost(songs: List<Song>, songId: Long?, onDismiss: () -> Unit) {
    val song = songId?.let { id -> songs.firstOrNull { it.id == id } } ?: return
    SongInfoDialog(song = song, onDismiss = onDismiss)
}

/**
 * One song with its menu. [onRemove] adds "Remove from playlist" to the menu,
 * and [trailingContent] is drawn after the menu button, for example a drag handle.
 * With a [selection], long-press selects the row; while selecting, taps toggle rows
 * and the menu and [trailingContent] are hidden.
 */
@Composable
fun SongRow(
    song: Song,
    isCurrent: Boolean,
    isPlaying: Boolean,
    onClick: () -> Unit,
    actions: SongActions,
    modifier: Modifier = Modifier,
    showTrackNumber: Boolean = false,
    onRemove: (() -> Unit)? = null,
    selection: SongSelection? = null,
    trailingContent: (@Composable () -> Unit)? = null,
) {
    val selecting = selection?.isActive == true
    val selected = selection?.isSelected(song.id) == true
    val haptics = LocalHapticFeedback.current
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 8.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(
                when {
                    selected -> MaterialTheme.colorScheme.secondaryContainer
                    isCurrent -> MaterialTheme.colorScheme.surfaceVariant
                    else -> Color.Transparent
                },
            )
            .heightIn(min = 72.dp)
            .then(
                if (selection == null) {
                    Modifier.clickable(onClick = onClick)
                } else {
                    Modifier
                        .semantics { this.selected = selected }
                        .combinedClickable(
                            onLongClickLabel = stringResource(R.string.action_select),
                            onLongClick = {
                                if (!selecting) haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                                selection.toggle(song.id)
                            },
                            onClick = { if (selecting) selection.toggle(song.id) else onClick() },
                        )
                },
            )
            .padding(start = 8.dp, top = 8.dp, bottom = 8.dp, end = if (selecting) 16.dp else 0.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (selecting) {
            Box(
                modifier = Modifier.width(if (showTrackNumber) 36.dp else 52.dp),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = if (selected) Icons.Filled.CheckCircle else Icons.Outlined.Circle,
                    contentDescription = null,
                    tint = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(if (showTrackNumber) 24.dp else 28.dp),
                )
            }
        } else if (showTrackNumber) {
            Box(modifier = Modifier.width(36.dp), contentAlignment = Alignment.Center) {
                Text(
                    text = if (song.trackNumber > 0) song.trackNumber.toString() else "–",
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        } else {
            ArtworkImage(
                uri = song.albumArtUri,
                contentDescription = stringResource(R.string.album_art_description, song.album),
                modifier = Modifier.size(52.dp),
            )
        }
        Spacer(Modifier.width(16.dp))
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            Text(
                text = song.title,
                style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.SemiBold),
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = song.artist,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        Spacer(Modifier.width(12.dp))
        if (isCurrent) {
            NowPlayingBars(
                animate = isPlaying,
                contentDescription = stringResource(
                    if (isPlaying) R.string.now_playing else R.string.paused,
                ),
            )
        } else {
            Text(
                text = formatDuration(song.durationMs),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        if (!selecting) {
            SongMenuButton(song = song, actions = actions, onRemove = onRemove)
            trailingContent?.invoke()
        }
    }
}

/** The three-dot button and its menu, shared by song rows and Now Playing. */
@Composable
fun SongMenuButton(
    song: Song,
    actions: SongActions,
    modifier: Modifier = Modifier,
    onRemove: (() -> Unit)? = null,
    tint: Color = MaterialTheme.colorScheme.onSurfaceVariant,
    extraItems: (@Composable (onDismiss: () -> Unit) -> Unit)? = null,
) {
    var expanded by remember { mutableStateOf(false) }
    Box(modifier = modifier) {
        IconButton(onClick = { expanded = true }) {
            Icon(
                imageVector = Icons.Filled.MoreVert,
                contentDescription = stringResource(R.string.action_more_options, song.title),
                tint = tint,
            )
        }
        DropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false },
            containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
        ) {
            extraItems?.invoke { expanded = false }
            SongMenuItems(song = song, actions = actions, onRemove = onRemove, onDismiss = { expanded = false })
        }
    }
}

@Composable
fun SongMenuItems(song: Song, actions: SongActions, onRemove: (() -> Unit)?, onDismiss: () -> Unit) {
    val item: @Composable (Int, () -> Unit) -> Unit = { label, action ->
        DropdownMenuItem(
            text = { Text(stringResource(label)) },
            onClick = {
                onDismiss()
                action()
            },
        )
    }
    item(R.string.menu_add_to_playlist) { actions.onAddToPlaylist(song) }
    item(R.string.menu_play_next) { actions.onPlayNext(song) }
    item(R.string.menu_add_to_queue) { actions.onAddToQueue(song) }
    if (onRemove != null) item(R.string.menu_remove_from_playlist, onRemove)
    item(R.string.menu_cut_and_save) { actions.onCutAndSave(song) }
    item(R.string.menu_set_as_ringtone) { actions.onSetAsRingtone(song) }
    item(R.string.menu_song_info) { actions.onShowInfo(song) }
    item(R.string.menu_delete_from_device) { actions.onDelete(song) }
}
