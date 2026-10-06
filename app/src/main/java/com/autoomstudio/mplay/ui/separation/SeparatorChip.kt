package com.autoomstudio.mplay.ui.separation

import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.AutoAwesome
import androidx.compose.material.icons.outlined.HourglassEmpty
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.AssistChipDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.autoomstudio.mplay.R
import com.autoomstudio.mplay.data.stems.JobState
import com.autoomstudio.mplay.data.stems.SeparationJobEntity

/** What the Now Playing separator chip needs for the current song; null hides the chip. */
data class SeparatorUi(
    /** The song's waiting, running or failed job; null when it was never separated. */
    val job: SeparationJobEntity?,
    val onSeparate: () -> Unit,
    val onCancel: (jobId: Long) -> Unit,
    val onRetry: (jobId: Long) -> Unit,
)

/** "AI Vocal Separator" beside Lofi: starts a separation, then shows its progress until the song is ready. */
@Composable
fun SeparatorChip(ui: SeparatorUi, title: String, modifier: Modifier = Modifier) {
    var confirmingCancel by rememberSaveable { mutableStateOf(false) }
    val job = ui.job
    val state = job?.state
    val iconSize = AssistChipDefaults.IconSize
    AssistChip(
        onClick = {
            when {
                job == null -> ui.onSeparate()
                state == JobState.Failed.name -> ui.onRetry(job.id)
                else -> confirmingCancel = true
            }
        },
        label = {
            Text(
                text = when {
                    job == null -> stringResource(R.string.separator_chip)
                    job.isCoolingDown -> stringResource(R.string.separator_chip_cooling, (job.progress * 100).toInt())
                    state == JobState.Running.name && job.progress <= 0f -> stringResource(R.string.separator_chip_preparing)
                    state == JobState.Running.name ->
                        stringResource(R.string.separator_chip_running, (job.progress * 100).toInt())
                    state == JobState.Failed.name -> stringResource(R.string.separator_chip_retry)
                    job.pauseReason != null -> pauseText(job.pauseReason)
                    else -> stringResource(R.string.separator_chip_waiting)
                },
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        },
        leadingIcon = {
            when (state) {
                null -> Icon(Icons.Outlined.AutoAwesome, contentDescription = null, modifier = Modifier.size(iconSize))
                JobState.Running.name -> if (job.progress > 0f) {
                    CircularProgressIndicator(
                        progress = { job.progress },
                        modifier = Modifier.size(iconSize),
                        strokeWidth = 2.dp,
                    )
                } else {
                    CircularProgressIndicator(modifier = Modifier.size(iconSize), strokeWidth = 2.dp)
                }
                JobState.Failed.name -> Icon(
                    Icons.Outlined.Refresh,
                    contentDescription = null,
                    modifier = Modifier.size(iconSize),
                    tint = MaterialTheme.colorScheme.error,
                )
                else -> Icon(Icons.Outlined.HourglassEmpty, contentDescription = null, modifier = Modifier.size(iconSize))
            }
        },
        modifier = modifier,
    )

    if (confirmingCancel && job != null) {
        AlertDialog(
            onDismissRequest = { confirmingCancel = false },
            title = { Text(stringResource(R.string.separator_cancel_title)) },
            text = { Text(stringResource(R.string.separator_cancel_message, title)) },
            confirmButton = {
                TextButton(
                    onClick = {
                        confirmingCancel = false
                        ui.onCancel(job.id)
                    },
                ) { Text(stringResource(R.string.separator_cancel_confirm)) }
            },
            dismissButton = {
                TextButton(onClick = { confirmingCancel = false }) {
                    Text(stringResource(R.string.separator_cancel_keep))
                }
            },
        )
    }
}
