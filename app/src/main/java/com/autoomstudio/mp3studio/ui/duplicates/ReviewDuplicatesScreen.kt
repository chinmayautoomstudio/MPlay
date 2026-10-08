package com.autoomstudio.mp3studio.ui.duplicates

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.LibraryMusic
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.autoomstudio.mp3studio.R
import com.autoomstudio.mp3studio.data.duplicates.DuplicateDetector
import com.autoomstudio.mp3studio.data.duplicates.DuplicateGroup
import com.autoomstudio.mp3studio.data.duplicates.DuplicateIndex
import com.autoomstudio.mp3studio.data.model.Song
import com.autoomstudio.mp3studio.ui.common.formatDuration
import com.autoomstudio.mp3studio.ui.components.ArtworkImage
import com.autoomstudio.mp3studio.ui.components.ComingSoon
import com.autoomstudio.mp3studio.ui.library.DetailBackButton

/** The duplicate setting and the review screen's choices. */
@Immutable
data class DuplicateActions(
    val hideDuplicates: Boolean,
    val onHideDuplicatesChange: (Boolean) -> Unit,
    val onKeep: (Song, DuplicateGroup) -> Unit,
    val onRestore: (Song) -> Unit,
    val onHideAgain: (Song) -> Unit,
)

@Composable
fun ReviewDuplicatesScreen(
    duplicates: DuplicateIndex,
    actions: DuplicateActions,
    onDeleteHidden: (List<Song>) -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    backEnabled: Boolean = true,
) {
    BackHandler(enabled = backEnabled, onBack = onBack)

    if (duplicates.groups.isEmpty()) {
        Column(modifier = modifier) {
            DetailBackButton(onBack = onBack)
            ComingSoon(
                icon = Icons.Outlined.LibraryMusic,
                title = stringResource(R.string.duplicates_empty_title),
                message = stringResource(R.string.duplicates_empty_message),
            )
        }
        return
    }

    LazyColumn(
        modifier = modifier,
        contentPadding = PaddingValues(bottom = 16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item(key = "back") { DetailBackButton(onBack = onBack) }
        item(key = "header") {
            Column(Modifier.padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    text = stringResource(R.string.duplicates_title),
                    style = MaterialTheme.typography.headlineSmall,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                Text(
                    text = stringResource(R.string.duplicates_intro),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        items(items = duplicates.groups, key = { group -> group.songs.joinToString(",") { it.id.toString() } }) { group ->
            DuplicateGroupCard(group = group, actions = actions, onDeleteHidden = onDeleteHidden)
        }
    }
}

@Composable
private fun DuplicateGroupCard(
    group: DuplicateGroup,
    actions: DuplicateActions,
    onDeleteHidden: (List<Song>) -> Unit,
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
        shape = RoundedCornerShape(16.dp),
    ) {
        Text(
            text = group.kept.title,
            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold),
            color = MaterialTheme.colorScheme.onSurface,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 12.dp),
        )
        Text(
            text = group.kept.artist,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(horizontal = 16.dp),
        )
        group.songs.forEach { song ->
            DuplicateCopyRow(song = song, group = group, actions = actions)
        }
        val hidden = group.songs.filter { it.id in group.hiddenIds }
        if (hidden.isNotEmpty()) {
            TextButton(
                onClick = { onDeleteHidden(hidden) },
                modifier = Modifier.padding(start = 8.dp, bottom = 4.dp),
            ) {
                Text(stringResource(R.string.duplicates_delete_extras))
            }
        }
    }
}

@Composable
private fun DuplicateCopyRow(song: Song, group: DuplicateGroup, actions: DuplicateActions) {
    val isKept = song.id == group.keptId
    val isRestored = song.id in group.restoredIds
    val status = stringResource(
        when {
            isKept -> R.string.duplicates_kept
            isRestored -> R.string.duplicates_restored
            else -> R.string.duplicates_hidden
        },
    )
    val quality = if (DuplicateDetector.isLossless(song.mimeType)) {
        stringResource(R.string.duplicates_lossless)
    } else {
        stringResource(R.string.duplicates_bitrate, song.bitrate / 1000)
    }
    ListItem(
        headlineContent = {
            Text(
                text = song.album,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        },
        supportingContent = {
            Column {
                Text(stringResource(R.string.duplicates_copy_details, status, quality, formatDuration(song.durationMs)))
                if (!isKept) {
                    TextButton(
                        onClick = { if (isRestored) actions.onHideAgain(song) else actions.onRestore(song) },
                        contentPadding = PaddingValues(horizontal = 0.dp),
                    ) {
                        Text(
                            stringResource(if (isRestored) R.string.duplicates_hide_again else R.string.duplicates_restore),
                        )
                    }
                }
            }
        },
        leadingContent = {
            ArtworkImage(uri = song.albumArtUri, contentDescription = null, modifier = Modifier.size(44.dp))
        },
        trailingContent = {
            RadioButton(selected = isKept, onClick = null)
        },
        colors = ListItemDefaults.colors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
        modifier = Modifier.selectable(
            selected = isKept,
            role = Role.RadioButton,
            onClick = { if (!isKept) actions.onKeep(song, group) },
        ),
    )
}
