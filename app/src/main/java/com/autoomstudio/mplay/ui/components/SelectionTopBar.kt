package com.autoomstudio.mplay.ui.components

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.PlaylistAdd
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.autoomstudio.mplay.R

/** Replaces [MPlayTopBar] while songs are selected. [onRemoveFromPlaylist] is null outside a playlist. */
@Composable
fun SelectionTopBar(
    count: Int,
    onClose: () -> Unit,
    onAddToPlaylist: () -> Unit,
    onPlayNext: () -> Unit,
    onAddToQueue: () -> Unit,
    onDelete: () -> Unit,
    onRemoveFromPlaylist: (() -> Unit)?,
    modifier: Modifier = Modifier,
    onSeparate: (() -> Unit)? = null,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .windowInsetsPadding(WindowInsets.statusBars)
            .heightIn(min = 64.dp)
            .padding(start = 4.dp, end = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        IconButton(onClick = onClose) {
            Icon(
                imageVector = Icons.Filled.Close,
                contentDescription = stringResource(R.string.action_clear_selection),
                tint = MaterialTheme.colorScheme.onSurface,
            )
        }
        Text(
            text = pluralStringResource(R.plurals.selected_count, count, count),
            style = MaterialTheme.typography.titleLarge,
            color = MaterialTheme.colorScheme.onSurface,
            maxLines = 1,
            modifier = Modifier
                .weight(1f)
                .padding(start = 8.dp)
                .semantics { liveRegion = LiveRegionMode.Polite },
        )
        IconButton(onClick = onAddToPlaylist) {
            Icon(
                imageVector = Icons.AutoMirrored.Outlined.PlaylistAdd,
                contentDescription = stringResource(R.string.menu_add_to_playlist),
                tint = MaterialTheme.colorScheme.onSurface,
            )
        }
        IconButton(onClick = onDelete) {
            Icon(
                imageVector = Icons.Outlined.Delete,
                contentDescription = stringResource(R.string.action_delete),
                tint = MaterialTheme.colorScheme.onSurface,
            )
        }
        SelectionOverflowMenu(
            onPlayNext = onPlayNext,
            onAddToQueue = onAddToQueue,
            onRemoveFromPlaylist = onRemoveFromPlaylist,
            onSeparate = onSeparate,
        )
    }
}

@Composable
private fun SelectionOverflowMenu(
    onPlayNext: () -> Unit,
    onAddToQueue: () -> Unit,
    onRemoveFromPlaylist: (() -> Unit)?,
    onSeparate: (() -> Unit)?,
) {
    var expanded by remember { mutableStateOf(false) }
    Box {
        IconButton(onClick = { expanded = true }) {
            Icon(
                imageVector = Icons.Filled.MoreVert,
                contentDescription = stringResource(R.string.action_more_selection_options),
                tint = MaterialTheme.colorScheme.onSurface,
            )
        }
        DropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false },
            containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
        ) {
            val item: @Composable (Int, () -> Unit) -> Unit = { label, action ->
                DropdownMenuItem(
                    text = { Text(stringResource(label)) },
                    onClick = {
                        expanded = false
                        action()
                    },
                )
            }
            item(R.string.menu_play_next, onPlayNext)
            item(R.string.menu_add_to_queue, onAddToQueue)
            if (onRemoveFromPlaylist != null) item(R.string.menu_remove_from_playlist, onRemoveFromPlaylist)
            if (onSeparate != null) item(R.string.separate_vocals, onSeparate)
        }
    }
}
