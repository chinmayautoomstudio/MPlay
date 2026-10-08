package com.autoomstudio.mp3studio.ui.playback

import android.os.SystemClock
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Bedtime
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.AssistChip
import androidx.compose.material3.AssistChipDefaults
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.autoomstudio.mp3studio.R
import com.autoomstudio.mp3studio.playback.SleepTimer
import com.autoomstudio.mp3studio.playback.SleepTimerStatus
import com.autoomstudio.mp3studio.ui.common.formatDuration
import kotlinx.coroutines.delay
import kotlin.math.roundToInt

/** Wall-clock time left on a timed sleep timer, refreshed every second; null otherwise. */
@Composable
fun rememberSleepRemainingMs(status: SleepTimerStatus): State<Long?> =
    produceState<Long?>(initialValue = null, status) {
        val running = status as? SleepTimerStatus.Running
        if (running == null) {
            value = null
            return@produceState
        }
        while (true) {
            value = SleepTimer.remainingMs(running, SystemClock.elapsedRealtime())
            delay(1_000L)
        }
    }

/** Short label for an active timer, or null when it is off. */
@Composable
fun sleepTimerLabel(status: SleepTimerStatus): String? {
    val remaining by rememberSleepRemainingMs(status)
    return when (status) {
        SleepTimerStatus.Off -> null
        SleepTimerStatus.EndOfSong -> stringResource(R.string.sleep_timer_until_end_of_song)
        is SleepTimerStatus.Running ->
            stringResource(R.string.sleep_timer_remaining, formatDuration(remaining ?: 0L))
    }
}

/** Countdown shown on Now Playing while a timer runs, with quick extend and cancel. */
@Composable
fun SleepTimerChip(
    status: SleepTimerStatus,
    onOpen: () -> Unit,
    onExtend: () -> Unit,
    onCancel: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val label = sleepTimerLabel(status) ?: return
    Row(modifier = modifier, verticalAlignment = Alignment.CenterVertically) {
        AssistChip(
            onClick = onOpen,
            label = { Text(label) },
            leadingIcon = {
                Icon(Icons.Filled.Bedtime, contentDescription = null, modifier = Modifier.size(AssistChipDefaults.IconSize))
            },
        )
        TextButton(onClick = onExtend) {
            Text(stringResource(R.string.sleep_timer_extend, SleepTimer.EXTEND_MINUTES))
        }
        IconButton(onClick = onCancel) {
            Icon(Icons.Filled.Close, contentDescription = stringResource(R.string.sleep_timer_cancel))
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SleepTimerSheet(
    status: SleepTimerStatus,
    suggestedMinutes: Int,
    onStart: (minutes: Int) -> Unit,
    onEndOfSong: () -> Unit,
    onExtend: () -> Unit,
    onCancel: () -> Unit,
    onDismiss: () -> Unit,
) {
    var customMinutes by remember { mutableFloatStateOf(suggestedMinutes.toFloat()) }
    var customTouched by remember { mutableStateOf(false) }
    LaunchedEffect(suggestedMinutes) {
        if (!customTouched) customMinutes = suggestedMinutes.toFloat()
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 24.dp, end = 24.dp, bottom = 32.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(
                text = stringResource(R.string.sleep_timer_title),
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurface,
            )

            val activeLabel = sleepTimerLabel(status)
            if (activeLabel != null) {
                Text(
                    text = activeLabel,
                    style = MaterialTheme.typography.headlineSmall,
                    color = MaterialTheme.colorScheme.primary,
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(onClick = onExtend) {
                        Text(stringResource(R.string.sleep_timer_extend, SleepTimer.EXTEND_MINUTES))
                    }
                    OutlinedButton(onClick = onCancel) {
                        Text(stringResource(R.string.sleep_timer_cancel))
                    }
                }
            }

            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                SleepTimer.PRESET_MINUTES.forEach { minutes ->
                    FilterChip(
                        selected = minutes == suggestedMinutes,
                        onClick = { onStart(minutes) },
                        label = { Text(stringResource(R.string.sleep_timer_minutes, minutes)) },
                    )
                }
                FilterChip(
                    selected = status == SleepTimerStatus.EndOfSong,
                    onClick = onEndOfSong,
                    label = { Text(stringResource(R.string.sleep_timer_end_of_song)) },
                )
            }

            val custom = customMinutes.roundToInt()
            Text(
                text = stringResource(R.string.sleep_timer_custom, custom),
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurface,
            )
            Slider(
                value = customMinutes,
                onValueChange = {
                    customTouched = true
                    customMinutes = it
                },
                valueRange = SleepTimer.MIN_MINUTES.toFloat()..SleepTimer.MAX_MINUTES.toFloat(),
            )
            Button(onClick = { onStart(custom) }, modifier = Modifier.fillMaxWidth()) {
                Text(stringResource(R.string.sleep_timer_start))
            }
            Text(
                text = stringResource(R.string.sleep_timer_fade_note),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}
