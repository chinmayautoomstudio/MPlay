package com.autoomstudio.mp3studio.ui.singalong

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.BluetoothAudio
import androidx.compose.material.icons.outlined.Headphones
import androidx.compose.material.icons.outlined.HeadsetOff
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material.icons.outlined.Mic
import androidx.compose.material.icons.outlined.SdStorage
import androidx.compose.material3.AssistChip
import androidx.compose.material3.AssistChipDefaults
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.autoomstudio.mp3studio.R
import com.autoomstudio.mp3studio.data.model.Song
import com.autoomstudio.mp3studio.data.stems.JobState
import com.autoomstudio.mp3studio.singalong.SingAlongOptions
import com.autoomstudio.mp3studio.ui.common.formatDuration
import com.autoomstudio.mp3studio.ui.separation.SeparatorUi
import kotlinx.coroutines.flow.Flow

/** "Sing along" beside the vocal separator control on Now Playing (SA1). */
@Composable
fun SingAlongChip(onClick: () -> Unit, modifier: Modifier = Modifier, locked: Boolean = false) {
    AssistChip(
        onClick = onClick,
        label = { Text(stringResource(R.string.singalong_chip)) },
        leadingIcon = {
            Icon(Icons.Outlined.Mic, contentDescription = null, modifier = Modifier.size(AssistChipDefaults.IconSize))
        },
        trailingIcon = if (locked) {
            {
                Icon(
                    Icons.Outlined.Lock,
                    contentDescription = stringResource(R.string.upgrade_locked_description),
                    modifier = Modifier.size(AssistChipDefaults.IconSize),
                )
            }
        } else {
            null
        },
        modifier = modifier,
    )
}

/**
 * Sets up a recording: songs without stems are sent to the separator first (SA2); separated songs get
 * the microphone, headphone, storage and start-point checks before recording (SA3-SA8).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SingAlongSheet(
    song: Song?,
    separated: Boolean,
    separator: SeparatorUi?,
    position: Flow<Long>,
    onDismiss: () -> Unit,
    viewModel: SingAlongViewModel = viewModel(factory = SingAlongViewModel.Factory),
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheetState) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 24.dp)
                .padding(bottom = 24.dp)
                .navigationBarsPadding(),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            if (song == null) return@Column
            if (!separated) {
                SeparateFirst(separator = separator, onDismiss = onDismiss)
            } else {
                RecordSetup(song = song, position = position, viewModel = viewModel, onStarted = onDismiss)
            }
        }
    }
}

@Composable
private fun ColumnScope.SeparateFirst(separator: SeparatorUi?, onDismiss: () -> Unit) {
    Text(stringResource(R.string.singalong_separate_first_title), style = MaterialTheme.typography.titleLarge)
    val job = separator?.job
    when {
        separator == null -> Text(stringResource(R.string.singalong_cannot_separate))
        job != null && job.state != JobState.Failed.name -> Text(stringResource(R.string.singalong_separating))
        else -> {
            Text(stringResource(R.string.singalong_separate_first_text))
            Button(
                onClick = {
                    if (job == null) separator.onSeparate() else separator.onRetry(job.id)
                    onDismiss()
                },
                modifier = Modifier.align(Alignment.End),
            ) { Text(stringResource(R.string.singalong_separate_button)) }
        }
    }
}

@Composable
private fun ColumnScope.RecordSetup(
    song: Song,
    position: Flow<Long>,
    viewModel: SingAlongViewModel,
    onStarted: () -> Unit,
) {
    val mic = rememberMicPermissionState()
    val routing = rememberAudioRouting()
    val noteSeen by viewModel.noteSeen.collectAsStateWithLifecycle()
    val positionMs by position.collectAsStateWithLifecycle(initialValue = 0L)
    var fromStart by rememberSaveable { mutableStateOf(true) }
    var countIn by rememberSaveable { mutableStateOf(false) }
    val canResume = positionMs >= MIN_RESUME_MS
    val startMs = if (fromStart || !canResume) 0L else positionMs
    val enoughSpace = viewModel.hasSpaceFor((song.durationMs - startMs).coerceAtLeast(0L))

    Text(stringResource(R.string.singalong_title), style = MaterialTheme.typography.titleLarge)

    if (!mic.isGranted) {
        Notice(Icons.Outlined.Mic, stringResource(R.string.singalong_mic_rationale))
        if (mic.isPermanentlyDenied) {
            Text(stringResource(R.string.singalong_mic_denied), color = MaterialTheme.colorScheme.error)
            OutlinedButton(onClick = mic::openSettings, modifier = Modifier.align(Alignment.End)) {
                Text(stringResource(R.string.singalong_open_settings))
            }
        } else {
            Button(onClick = mic::request, modifier = Modifier.align(Alignment.End)) {
                Text(stringResource(R.string.singalong_mic_allow))
            }
        }
        return
    }

    if (routing.headphones) {
        Notice(Icons.Outlined.Headphones, stringResource(R.string.singalong_headphones_on))
    } else {
        Notice(Icons.Outlined.HeadsetOff, stringResource(R.string.singalong_headphones_off), MaterialTheme.colorScheme.error)
    }
    if (routing.onlyBluetoothMic) {
        Notice(Icons.Outlined.BluetoothAudio, stringResource(R.string.singalong_bluetooth_mic))
    }
    if (!enoughSpace) {
        Notice(Icons.Outlined.SdStorage, stringResource(R.string.singalong_low_storage), MaterialTheme.colorScheme.error)
    }

    Column(Modifier.selectableGroup()) {
        StartOption(
            label = stringResource(R.string.singalong_from_start),
            selected = fromStart || !canResume,
            onSelect = { fromStart = true },
        )
        if (canResume) {
            StartOption(
                label = stringResource(R.string.singalong_from_here, formatDuration(positionMs)),
                selected = !fromStart,
                onSelect = { fromStart = false },
            )
        }
    }
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(stringResource(R.string.singalong_count_in), modifier = Modifier.weight(1f))
        Switch(checked = countIn, onCheckedChange = { countIn = it })
    }

    if (noteSeen == false) {
        Notice(Icons.Outlined.Info, stringResource(R.string.singalong_personal_note))
    }

    Button(
        onClick = {
            viewModel.start(song, SingAlongOptions(fromStart = fromStart || !canResume, countIn = countIn))
            onStarted()
        },
        enabled = enoughSpace,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Icon(Icons.Outlined.Mic, contentDescription = null, modifier = Modifier.padding(end = 8.dp))
        Text(stringResource(R.string.singalong_start))
    }
}

@Composable
private fun StartOption(label: String, selected: Boolean, onSelect: () -> Unit) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .selectable(selected = selected, onClick = onSelect, role = Role.RadioButton)
            .padding(vertical = 4.dp),
    ) {
        RadioButton(selected = selected, onClick = null)
        Text(label, modifier = Modifier.padding(start = 12.dp))
    }
}

@Composable
internal fun Notice(icon: ImageVector, text: String, tint: Color = MaterialTheme.colorScheme.onSurfaceVariant) {
    Row(verticalAlignment = Alignment.Top, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        Icon(icon, contentDescription = null, tint = tint)
        Text(text, style = MaterialTheme.typography.bodyMedium, color = tint)
    }
}

private const val MIN_RESUME_MS = 5_000L
