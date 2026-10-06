package com.autoomstudio.mplay.ui.metronome

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material.icons.outlined.TouchApp
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
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
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.autoomstudio.mplay.R
import com.autoomstudio.mplay.metronome.BeatTick
import com.autoomstudio.mplay.metronome.ClickSound
import com.autoomstudio.mplay.metronome.MetronomeSettings
import com.autoomstudio.mplay.metronome.MetronomeState
import com.autoomstudio.mplay.metronome.TimeSignature
import kotlinx.coroutines.flow.StateFlow
import kotlin.math.roundToInt

/**
 * The full set of metronome controls, shared by the Metronome tab and the Now Playing sheet (MT2 to MT8).
 * [tempoExtras] sits under the tempo slider, for Detect BPM in the sheet.
 */
@Composable
fun MetronomeControls(
    state: MetronomeState,
    beat: StateFlow<BeatTick?>,
    tapCount: Int,
    onUpdate: ((MetronomeSettings) -> MetronomeSettings) -> Unit,
    onToggle: () -> Unit,
    onTap: () -> Unit,
    modifier: Modifier = Modifier,
    tempoExtras: @Composable ColumnScope.() -> Unit = {},
) {
    val settings = state.settings
    var editingBpm by rememberSaveable { mutableStateOf(false) }

    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        BeatIndicator(
            beat = beat,
            beatsPerBar = settings.beatsPerBar,
            accent = settings.accent,
            running = state.running,
            modifier = Modifier.padding(vertical = 8.dp),
        )
        val tempoLabel = stringResource(R.string.metronome_tempo_slider)
        val bpmState = "${settings.bpm} ${stringResource(R.string.metronome_bpm)}"
        BpmRow(
            bpm = settings.bpm,
            onChange = { bpm -> onUpdate { it.copy(bpm = bpm) } },
            onEdit = { editingBpm = true },
        )
        Slider(
            value = settings.bpm.toFloat(),
            onValueChange = { value -> onUpdate { it.copy(bpm = value.roundToInt()) } },
            valueRange = MetronomeSettings.MIN_BPM.toFloat()..MetronomeSettings.MAX_BPM.toFloat(),
            modifier = Modifier
                .fillMaxWidth()
                .semantics {
                    contentDescription = tempoLabel
                    stateDescription = bpmState
                },
        )
        tempoExtras()
        Row(
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            modifier = Modifier.fillMaxWidth(),
        ) {
            OutlinedButton(
                onClick = onTap,
                modifier = Modifier
                    .weight(1f)
                    .height(56.dp),
            ) {
                Icon(Icons.Outlined.TouchApp, contentDescription = null)
                Text(stringResource(R.string.metronome_tap), Modifier.padding(start = 8.dp))
            }
            Button(
                onClick = onToggle,
                modifier = Modifier
                    .weight(1f)
                    .height(56.dp),
            ) {
                Icon(if (state.running) Icons.Filled.Stop else Icons.Filled.PlayArrow, contentDescription = null)
                Text(
                    stringResource(if (state.running) R.string.metronome_stop else R.string.metronome_start),
                    Modifier.padding(start = 8.dp),
                )
            }
        }
        val tapsLeft = TAPS_NEEDED - tapCount
        Text(
            text = if (tapCount in 1 until TAPS_NEEDED) {
                LocalResources.current.getQuantityString(R.plurals.metronome_tap_more, tapsLeft, tapsLeft)
            } else {
                ""
            },
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )

        SectionLabel(stringResource(R.string.metronome_time_signature))
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.fillMaxWidth(),
        ) {
            TimeSignature.Presets.forEach { preset ->
                FilterChip(
                    selected = settings.timeSignature == preset,
                    onClick = { onUpdate { it.copy(beatsPerBar = preset.beats, beatUnit = preset.unit) } },
                    label = { Text(preset.toString()) },
                )
            }
        }
        Stepper(
            label = stringResource(R.string.metronome_beats_per_bar),
            value = settings.beatsPerBar,
            range = 1..MetronomeSettings.MAX_BEATS,
            decreaseLabel = stringResource(R.string.metronome_fewer_beats),
            increaseLabel = stringResource(R.string.metronome_more_beats),
            onChange = { beats -> onUpdate { it.copy(beatsPerBar = beats) } },
        )
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 48.dp)
                .clickable(role = Role.Switch) { onUpdate { it.copy(accent = !it.accent) } },
        ) {
            Text(
                stringResource(R.string.metronome_accent),
                style = MaterialTheme.typography.bodyLarge,
                modifier = Modifier.weight(1f),
            )
            Switch(checked = settings.accent, onCheckedChange = null)
        }

        SectionLabel(stringResource(R.string.metronome_sound))
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.fillMaxWidth(),
        ) {
            ClickSound.entries.forEach { sound ->
                FilterChip(
                    selected = settings.sound == sound,
                    onClick = { onUpdate { it.copy(sound = sound) } },
                    label = { Text(stringResource(sound.label)) },
                )
            }
        }
        val volumeLabel = stringResource(R.string.metronome_volume)
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
            Icon(
                Icons.AutoMirrored.Filled.VolumeUp,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Slider(
                value = settings.volume,
                onValueChange = { value -> onUpdate { it.copy(volume = value) } },
                modifier = Modifier
                    .weight(1f)
                    .padding(start = 12.dp)
                    .semantics { contentDescription = volumeLabel },
            )
        }
    }

    if (editingBpm) {
        BpmEntryDialog(
            initial = settings.bpm,
            onConfirm = { bpm ->
                onUpdate { it.copy(bpm = bpm) }
                editingBpm = false
            },
            onDismiss = { editingBpm = false },
        )
    }
}

private const val TAPS_NEEDED = 4

private val ClickSound.label: Int
    get() = when (this) {
        ClickSound.Classic -> R.string.metronome_sound_classic
        ClickSound.WoodBlock -> R.string.metronome_sound_wood
        ClickSound.SoftBeep -> R.string.metronome_sound_beep
    }

/** One dot per beat; the sounding beat lights up and pulses, beat 1 is larger when accented (MT7). */
@Composable
private fun BeatIndicator(
    beat: StateFlow<BeatTick?>,
    beatsPerBar: Int,
    accent: Boolean,
    running: Boolean,
    modifier: Modifier = Modifier,
) {
    val tick by beat.collectAsStateWithLifecycle()
    val current = tick?.takeIf { running && it.beatsPerBar == beatsPerBar }?.beatInBar
    val pulse = remember { Animatable(1f) }
    LaunchedEffect(tick?.index) {
        if (tick == null) return@LaunchedEffect
        pulse.snapTo(1.3f)
        pulse.animateTo(1f, tween(PULSE_MS))
    }
    val description = if (current != null) {
        stringResource(R.string.metronome_beat_description, current + 1, beatsPerBar)
    } else {
        ""
    }
    FlowRow(
        horizontalArrangement = Arrangement.spacedBy(10.dp, Alignment.CenterHorizontally),
        verticalArrangement = Arrangement.spacedBy(10.dp),
        modifier = modifier
            .fillMaxWidth()
            .heightIn(min = 40.dp)
            .semantics { contentDescription = description },
    ) {
        repeat(beatsPerBar) { index ->
            val first = index == 0 && accent
            val active = index == current
            val color = when {
                active && first -> MaterialTheme.colorScheme.tertiary
                active -> MaterialTheme.colorScheme.primary
                else -> MaterialTheme.colorScheme.surfaceContainerHighest
            }
            Box(
                Modifier
                    .align(Alignment.CenterVertically)
                    .size(if (first) 30.dp else 22.dp)
                    .graphicsLayer {
                        val scale = if (active) pulse.value else 1f
                        scaleX = scale
                        scaleY = scale
                    }
                    .background(color, CircleShape)
                    .then(
                        if (first && !active) {
                            Modifier.border(2.dp, MaterialTheme.colorScheme.tertiary, CircleShape)
                        } else {
                            Modifier
                        },
                    ),
            )
        }
    }
}

private const val PULSE_MS = 120

@Composable
private fun BpmRow(bpm: Int, onChange: (Int) -> Unit, onEdit: () -> Unit) {
    val editDescription = stringResource(R.string.metronome_bpm_description, bpm)
    Row(verticalAlignment = Alignment.CenterVertically) {
        FilledTonalIconButton(
            onClick = { onChange(bpm - 1) },
            enabled = bpm > MetronomeSettings.MIN_BPM,
            modifier = Modifier.size(56.dp),
        ) {
            Icon(Icons.Filled.Remove, contentDescription = stringResource(R.string.metronome_slower))
        }
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier
                .width(168.dp)
                .clickable(onClickLabel = stringResource(R.string.metronome_enter_bpm), onClick = onEdit)
                .semantics(mergeDescendants = true) { contentDescription = editDescription },
        ) {
            Text(
                text = bpm.toString(),
                style = MaterialTheme.typography.displayLarge,
                color = MaterialTheme.colorScheme.onSurface,
            )
            Text(
                text = stringResource(R.string.metronome_bpm),
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        FilledTonalIconButton(
            onClick = { onChange(bpm + 1) },
            enabled = bpm < MetronomeSettings.MAX_BPM,
            modifier = Modifier.size(56.dp),
        ) {
            Icon(Icons.Filled.Add, contentDescription = stringResource(R.string.metronome_faster))
        }
    }
}

@Composable
private fun Stepper(
    label: String,
    value: Int,
    range: IntRange,
    decreaseLabel: String,
    increaseLabel: String,
    onChange: (Int) -> Unit,
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Text(label, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
        IconButton(onClick = { onChange(value - 1) }, enabled = value > range.first) {
            Icon(Icons.Filled.Remove, contentDescription = decreaseLabel)
        }
        Text(
            text = value.toString(),
            style = MaterialTheme.typography.titleMedium,
            modifier = Modifier.width(32.dp),
            textAlign = TextAlign.Center,
        )
        IconButton(onClick = { onChange(value + 1) }, enabled = value < range.last) {
            Icon(Icons.Filled.Add, contentDescription = increaseLabel)
        }
    }
}

@Composable
private fun SectionLabel(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.titleSmall,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 8.dp),
    )
}

@Composable
private fun BpmEntryDialog(initial: Int, onConfirm: (Int) -> Unit, onDismiss: () -> Unit) {
    // Starts fully selected so typing replaces the current tempo.
    var field by rememberSaveable(stateSaver = TextFieldValue.Saver) {
        val text = initial.toString()
        mutableStateOf(TextFieldValue(text, selection = TextRange(0, text.length)))
    }
    val value = field.text.toIntOrNull()?.takeIf { it in MetronomeSettings.MIN_BPM..MetronomeSettings.MAX_BPM }
    val focusRequester = remember { FocusRequester() }
    LaunchedEffect(Unit) { focusRequester.requestFocus() }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.metronome_enter_bpm)) },
        text = {
            OutlinedTextField(
                value = field,
                onValueChange = { input ->
                    val digits = input.text.filter(Char::isDigit).take(3)
                    field = if (digits == input.text) input else TextFieldValue(digits, TextRange(digits.length))
                },
                singleLine = true,
                modifier = Modifier.focusRequester(focusRequester),
                label = { Text(stringResource(R.string.metronome_bpm)) },
                supportingText = { Text(stringResource(R.string.metronome_enter_bpm_hint)) },
                isError = value == null,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number, imeAction = ImeAction.Done),
                keyboardActions = KeyboardActions(onDone = { value?.let(onConfirm) }),
            )
        },
        confirmButton = {
            TextButton(onClick = { value?.let(onConfirm) }, enabled = value != null) {
                Text(stringResource(R.string.metronome_set))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.metronome_cancel)) }
        },
    )
}
