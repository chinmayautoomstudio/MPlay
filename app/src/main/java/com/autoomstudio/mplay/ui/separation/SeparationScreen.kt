package com.autoomstudio.mplay.ui.separation

import android.text.format.Formatter
import androidx.activity.compose.BackHandler
import androidx.annotation.StringRes
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.outlined.GraphicEq
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.autoomstudio.mplay.R
import com.autoomstudio.mplay.data.stems.JobError
import com.autoomstudio.mplay.data.stems.JobState
import com.autoomstudio.mplay.data.stems.PauseReason
import com.autoomstudio.mplay.data.stems.SeparationJobEntity
import com.autoomstudio.mplay.data.stems.StemMode
import com.autoomstudio.mplay.data.stems.StemSetEntity
import com.autoomstudio.mplay.ui.components.ComingSoon
import com.autoomstudio.mplay.ui.library.DetailBackButton
import kotlin.math.roundToInt

/** The processing queue (AI25) and the separated songs with delete and export (AI16, AI23). */
@Composable
fun SeparationScreen(
    state: SeparationUiState,
    viewModel: SeparationViewModel,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    backEnabled: Boolean = true,
) {
    BackHandler(enabled = backEnabled, onBack = onBack)
    val running = state.jobs.filter { it.state == JobState.Running.name }
    val waiting = state.jobs.filter { it.state == JobState.Queued.name }
    val finished = state.jobs
        .filter { it.state == JobState.Failed.name || it.state == JobState.Cancelled.name }
        .sortedByDescending { it.finishedAt ?: 0L }

    if (state.jobs.none { it.state != JobState.Done.name } && state.stemSets.isEmpty()) {
        Column(modifier = modifier) {
            DetailBackButton(onBack = onBack)
            ComingSoon(
                icon = Icons.Outlined.GraphicEq,
                title = stringResource(R.string.separation_empty_title),
                message = stringResource(R.string.separation_empty_message),
            )
        }
        return
    }

    LazyColumn(modifier = modifier, contentPadding = PaddingValues(bottom = 16.dp)) {
        item(key = "back") { DetailBackButton(onBack = onBack) }
        item(key = "title") {
            Text(
                text = stringResource(R.string.separation_queue_title),
                style = MaterialTheme.typography.headlineSmall,
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
            )
        }
        jobSection("processing", R.string.separation_section_processing, running) { RunningJobRow(it, viewModel) }
        jobSection("waiting", R.string.separation_section_waiting, waiting) { WaitingJobRow(it, viewModel) }
        if (finished.isNotEmpty()) {
            item(key = "finished_header") {
                Box(Modifier.fillMaxWidth()) {
                    SectionTitle(stringResource(R.string.separation_section_finished))
                    TextButton(
                        onClick = viewModel::clearFinished,
                        modifier = Modifier
                            .align(Alignment.CenterEnd)
                            .padding(end = 8.dp),
                    ) { Text(stringResource(R.string.separation_clear_finished)) }
                }
            }
            items(finished, key = { "job_${it.id}" }) { FinishedJobRow(it, canRetry = state.available, viewModel) }
        }
        if (state.stemSets.isNotEmpty()) {
            item(key = "ready_header") { SectionTitle(stringResource(R.string.separation_section_ready)) }
            items(state.stemSets, key = { "set_${it.songId}" }) { StemSetRow(it, viewModel) }
        }
    }
}

private fun LazyListScope.jobSection(
    key: String,
    title: Int,
    jobs: List<SeparationJobEntity>,
    row: @Composable (SeparationJobEntity) -> Unit,
) {
    if (jobs.isEmpty()) return
    item(key = "${key}_header") { SectionTitle(stringResource(title)) }
    items(jobs, key = { "job_${it.id}" }) { row(it) }
}

@Composable
private fun SectionTitle(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.titleSmall,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 20.dp, bottom = 8.dp),
    )
}

@Composable
private fun RunningJobRow(job: SeparationJobEntity, viewModel: SeparationViewModel) {
    val percent = (job.progress * 100).roundToInt().coerceIn(0, 100)
    ListItem(
        headlineContent = { SongTitle(job.title) },
        supportingContent = {
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(
                    text = when {
                        job.isCoolingDown -> stringResource(R.string.separation_pill_cooling, percent)
                        job.remainingMs != null ->
                            stringResource(R.string.separation_notification_percent_eta, percent, remainingText(job.remainingMs))
                        else -> stringResource(R.string.separation_notification_percent, percent)
                    },
                )
                LinearProgressIndicator(progress = { job.progress }, modifier = Modifier.fillMaxWidth())
            }
        },
        trailingContent = {
            TextButton(onClick = { viewModel.cancel(job.id) }) { Text(stringResource(R.string.separation_cancel)) }
        },
        colors = ListItemDefaults.colors(containerColor = MaterialTheme.colorScheme.background),
    )
}

@Composable
private fun WaitingJobRow(job: SeparationJobEntity, viewModel: SeparationViewModel) {
    ListItem(
        headlineContent = { SongTitle(job.title) },
        supportingContent = { Text(pauseText(job.pauseReason)) },
        trailingContent = {
            TextButton(onClick = { viewModel.cancel(job.id) }) { Text(stringResource(R.string.separation_cancel)) }
        },
        colors = ListItemDefaults.colors(containerColor = MaterialTheme.colorScheme.background),
    )
}

@Composable
private fun FinishedJobRow(job: SeparationJobEntity, canRetry: Boolean, viewModel: SeparationViewModel) {
    val failed = job.state == JobState.Failed.name
    ListItem(
        headlineContent = { SongTitle(job.title) },
        supportingContent = {
            Text(
                text = if (failed) errorText(job.error) else stringResource(R.string.separation_state_cancelled),
                color = if (failed) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant,
            )
        },
        trailingContent = {
            Row {
                TextButton(onClick = { viewModel.removeJob(job.id) }) {
                    Text(stringResource(R.string.separation_remove))
                }
                if (canRetry) {
                    TextButton(onClick = { viewModel.retry(job.id) }) {
                        Text(stringResource(R.string.separation_retry))
                    }
                }
            }
        },
        colors = ListItemDefaults.colors(containerColor = MaterialTheme.colorScheme.background),
    )
}

@Composable
private fun StemSetRow(set: StemSetEntity, viewModel: SeparationViewModel) {
    val context = LocalContext.current
    var menuOpen by remember { mutableStateOf(false) }
    ListItem(
        headlineContent = { SongTitle(set.title) },
        supportingContent = {
            Text(
                text = stringResource(R.string.separation_size, set.artist, Formatter.formatShortFileSize(context, set.bytes)),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        },
        trailingContent = {
            Box {
                IconButton(onClick = { menuOpen = true }) {
                    Icon(
                        imageVector = Icons.Filled.MoreVert,
                        contentDescription = stringResource(R.string.action_more_options, set.title),
                    )
                }
                DropdownMenu(
                    expanded = menuOpen,
                    onDismissRequest = { menuOpen = false },
                    containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                ) {
                    DropdownMenuItem(
                        text = { Text(stringResource(R.string.separation_export_instrumental)) },
                        onClick = {
                            menuOpen = false
                            viewModel.export(set, StemMode.Instrumental)
                        },
                    )
                    DropdownMenuItem(
                        text = { Text(stringResource(R.string.separation_export_vocals)) },
                        onClick = {
                            menuOpen = false
                            viewModel.export(set, StemMode.Vocals)
                        },
                    )
                    DropdownMenuItem(
                        text = { Text(stringResource(R.string.separation_delete)) },
                        onClick = {
                            menuOpen = false
                            viewModel.deleteStems(set.songId)
                        },
                    )
                }
            }
        },
        colors = ListItemDefaults.colors(containerColor = MaterialTheme.colorScheme.background),
    )
}

@Composable
private fun SongTitle(title: String) {
    Text(text = title, maxLines = 1, overflow = TextOverflow.Ellipsis)
}

/** A running job waits between segments while the phone is hot, keeping its progress. */
internal val SeparationJobEntity.isCoolingDown: Boolean
    get() = state == JobState.Running.name && pauseReason == PauseReason.Heat.name

@Composable
internal fun pauseText(reason: String?): String = stringResource(
    when (PauseReason.entries.firstOrNull { it.name == reason }) {
        null -> R.string.separation_state_waiting
        PauseReason.Charging -> R.string.separation_paused_charging
        PauseReason.Battery -> R.string.separation_paused_battery
        PauseReason.Heat -> R.string.separation_paused_heat
        PauseReason.TimeLimit -> R.string.separation_paused_time_limit
        PauseReason.Interrupted -> R.string.separation_paused_interrupted
        PauseReason.NeedsApp -> R.string.separation_paused_needs_app
    },
)

@Composable
fun errorText(error: String?): String = stringResource(errorTextRes(error))

@StringRes
fun errorTextRes(error: String?): Int = when (JobError.entries.firstOrNull { it.name == error }) {
    JobError.SourceMissing -> R.string.separation_error_source_missing
    JobError.UnsupportedFormat -> R.string.separation_error_unsupported
    JobError.CorruptFile -> R.string.separation_error_corrupt
    JobError.OutOfMemory -> R.string.separation_error_memory
    JobError.LowStorage -> R.string.separation_error_storage
    JobError.ModelUnavailable -> R.string.separation_error_model
    JobError.ModelFailed -> R.string.separation_error_model_failed
    JobError.Unknown, null -> R.string.separation_error_unknown
}

@Composable
internal fun remainingText(ms: Long): String {
    val minutes = ((ms + 30_000) / 60_000).toInt()
    return if (minutes < 1) {
        stringResource(R.string.duration_under_minute)
    } else {
        stringResource(R.string.duration_minutes_short, minutes)
    }
}
