package com.autoomstudio.mp3studio.ui.library

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.autoomstudio.mp3studio.R
import com.autoomstudio.mp3studio.data.model.Song
import com.autoomstudio.mp3studio.ui.common.formatDuration
import java.text.DateFormat
import java.util.Date

@Composable
fun SongInfoDialog(song: Song, onDismiss: () -> Unit) {
    val dateAdded = remember(song.dateAdded) {
        DateFormat.getDateInstance(DateFormat.MEDIUM).format(Date(song.dateAdded * 1000))
    }
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
        title = { Text(song.title, style = MaterialTheme.typography.titleLarge) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                InfoLine(stringResource(R.string.song_info_artist), song.artist)
                InfoLine(stringResource(R.string.song_info_album), song.album)
                InfoLine(stringResource(R.string.song_info_duration), formatDuration(song.durationMs))
                InfoLine(stringResource(R.string.song_info_date_added), dateAdded)
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.dialog_close)) }
        },
    )
}

@Composable
private fun InfoLine(label: String, value: String) {
    Column {
        Text(
            text = label,
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(
            text = value,
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurface,
        )
    }
}
