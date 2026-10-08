package com.autoomstudio.mp3studio.ui.metronome

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.automirrored.filled.VolumeOff
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.autoomstudio.mp3studio.R
import com.autoomstudio.mp3studio.metronome.BeatTick
import com.autoomstudio.mp3studio.metronome.ClickSound
import com.autoomstudio.mp3studio.metronome.MetronomeSettings
import com.autoomstudio.mp3studio.metronome.MetronomeState
import com.autoomstudio.mp3studio.metronome.TimeSignature
import kotlinx.coroutines.flow.StateFlow
import kotlin.math.roundToInt

/**
 * The full set of metronome controls, shared by the Metronome tab and the Now Playing sheet (MT2 to MT7).
 * [onBack] adds a close button to the header (the sheet). [tempoExtras] sits under the tempo slider, for Detect BPM,
 * and [syncTile] below it once a song's beats are known.
 */
@Composable
fun MetronomeControls(
    state: MetronomeState,
    beat: StateFlow<BeatTick?>,
    onUpdate: ((MetronomeSettings) -> MetronomeSettings) -> Unit,
    onToggle: () -> Unit,
    onToggleMute: () -> Unit,
    modifier: Modifier = Modifier,
    onBack: (() -> Unit)? = null,
    tempoExtras: @Composable ColumnScope.() -> Unit = {},
    syncTile: (@Composable RowScope.() -> Unit)? = null,
) {
    val settings = state.settings
    var editingBpm by rememberSaveable { mutableStateOf(false) }
    var showSoundSettings by rememberSaveable { mutableStateOf(false) }

    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Header(onBack)
        BpmDial(
            bpm = settings.bpm,
            beat = beat,
            beatsPerBar = settings.beatsPerBar,
            accent = settings.accent,
            running = state.running,
            onChange = { bpm -> onUpdate { it.copy(bpm = bpm) } },
            onEdit = { editingBpm = true },
        )
        TempoSlider(bpm = settings.bpm, onChange = { bpm -> onUpdate { it.copy(bpm = bpm) } })
        tempoExtras()
        if (syncTile != null) {
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.fillMaxWidth()) {
                syncTile()
            }
        }
        TransportRow(
            running = state.running,
            muted = state.muted,
            onToggle = onToggle,
            onToggleMute = onToggleMute,
            onSettings = { showSoundSettings = true },
        )
        TimeSignatureCard(settings = settings, onUpdate = onUpdate)
        AccentCard(accent = settings.accent, onToggle = { onUpdate { it.copy(accent = !it.accent) } })
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
    if (showSoundSettings) {
        SoundSettingsSheet(settings = settings, onUpdate = onUpdate, onDismiss = { showSoundSettings = false })
    }
}

private val ClickSound.label: Int
    get() = when (this) {
        ClickSound.Classic -> R.string.metronome_sound_classic
        ClickSound.WoodBlock -> R.string.metronome_sound_wood
        ClickSound.SoftBeep -> R.string.metronome_sound_beep
    }

@Composable
private fun Header(onBack: (() -> Unit)?) {
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
        Box(Modifier.size(HEADER_BUTTON)) {
            if (onBack != null) {
                IconButton(
                    onClick = onBack,
                    modifier = Modifier
                        .size(HEADER_BUTTON)
                        .background(MaterialTheme.colorScheme.surfaceContainerHigh, CircleShape),
                ) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.metronome_back))
                }
            }
        }
        Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.weight(1f)) {
            Text(
                stringResource(R.string.metronome_title),
                style = MaterialTheme.typography.titleLarge,
                color = MaterialTheme.colorScheme.onSurface,
            )
            Text(
                stringResource(R.string.metronome_subtitle),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.primary.copy(alpha = 0.8f),
            )
        }
        Spacer(Modifier.size(HEADER_BUTTON))
    }
}

private val HEADER_BUTTON = 44.dp

@Composable
private fun TempoSlider(bpm: Int, onChange: (Int) -> Unit) {
    val tempoLabel = stringResource(R.string.metronome_tempo_slider)
    val bpmState = "$bpm ${stringResource(R.string.metronome_bpm)}"
    Column(modifier = Modifier.fillMaxWidth()) {
        MetronomeSlider(
            value = bpm.toFloat(),
            onValueChange = { value -> onChange(value.roundToInt()) },
            valueRange = MetronomeSettings.MIN_BPM.toFloat()..MetronomeSettings.MAX_BPM.toFloat(),
            modifier = Modifier
                .fillMaxWidth()
                .semantics {
                    contentDescription = tempoLabel
                    stateDescription = bpmState
                },
        )
        Row(modifier = Modifier.fillMaxWidth()) {
            Text(
                MetronomeSettings.MIN_BPM.toString(),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.weight(1f))
            Text(
                MetronomeSettings.MAX_BPM.toString(),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

/** Thin track with a round glowing thumb. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun MetronomeSlider(
    value: Float,
    onValueChange: (Float) -> Unit,
    modifier: Modifier = Modifier,
    valueRange: ClosedFloatingPointRange<Float> = 0f..1f,
) {
    val colors = MaterialTheme.colorScheme
    Slider(
        value = value,
        onValueChange = onValueChange,
        valueRange = valueRange,
        thumb = {
            Box(
                Modifier
                    .size(22.dp)
                    .shadow(6.dp, CircleShape, ambientColor = colors.primary, spotColor = colors.primary)
                    .background(colors.onPrimary, CircleShape)
                    .border(3.dp, colors.primary, CircleShape),
            )
        },
        track = { sliderState ->
            SliderDefaults.Track(
                sliderState = sliderState,
                modifier = Modifier.height(6.dp),
                thumbTrackGapSize = 0.dp,
                drawStopIndicator = null,
                colors = SliderDefaults.colors(
                    activeTrackColor = colors.primary,
                    inactiveTrackColor = colors.surfaceContainerHighest,
                ),
            )
        },
        modifier = modifier,
    )
}

/** A rounded card with an icon, a title and a one-line hint, for Sync with song and Tap tempo. */
@Composable
internal fun ActionTile(
    icon: ImageVector,
    title: String,
    subtitle: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    selected: Boolean = false,
    enabled: Boolean = true,
    description: String? = null,
) {
    val colors = MaterialTheme.colorScheme
    Surface(
        onClick = onClick,
        enabled = enabled,
        shape = TILE_SHAPE,
        color = if (selected) colors.primary.copy(alpha = 0.14f) else colors.surfaceContainer,
        border = BorderStroke(1.dp, if (selected) colors.primary else colors.outlineVariant),
        modifier = modifier
            .heightIn(min = 64.dp)
            .then(if (description != null) Modifier.semantics { contentDescription = description } else Modifier),
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 12.dp),
        ) {
            Icon(icon, contentDescription = null, tint = colors.primary.copy(alpha = if (enabled) 1f else 0.4f))
            Column(
                modifier = Modifier
                    .weight(1f)
                    .padding(horizontal = 8.dp),
            ) {
                Text(
                    title,
                    style = MaterialTheme.typography.titleSmall,
                    color = colors.onSurface.copy(alpha = if (enabled) 1f else 0.5f),
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = colors.onSurfaceVariant,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            Icon(
                Icons.AutoMirrored.Filled.KeyboardArrowRight,
                contentDescription = null,
                tint = colors.onSurfaceVariant,
                modifier = Modifier.size(18.dp),
            )
        }
    }
}

internal val TILE_SHAPE = RoundedCornerShape(16.dp)

/** Mute on the left, the big start/stop circle in the middle, sound settings on the right. */
@Composable
private fun TransportRow(
    running: Boolean,
    muted: Boolean,
    onToggle: () -> Unit,
    onToggleMute: () -> Unit,
    onSettings: () -> Unit,
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceEvenly,
        modifier = Modifier.fillMaxWidth(),
    ) {
        RoundAction(
            icon = if (muted) Icons.AutoMirrored.Filled.VolumeOff else Icons.AutoMirrored.Filled.VolumeUp,
            label = stringResource(R.string.metronome_sound_toggle),
            description = stringResource(if (muted) R.string.metronome_unmute else R.string.metronome_mute),
            onClick = onToggleMute,
        )
        val toggleLabel = stringResource(if (running) R.string.metronome_stop else R.string.metronome_start)
        val colors = MaterialTheme.colorScheme
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier
                .size(PLAY_SIZE)
                .shadow(elevation = 18.dp, shape = CircleShape, ambientColor = colors.primary, spotColor = colors.primary)
                .clip(CircleShape)
                .background(Brush.linearGradient(listOf(colors.primary, colors.primaryContainer)))
                .clickable(onClick = onToggle)
                .semantics {
                    role = Role.Button
                    contentDescription = toggleLabel
                },
        ) {
            Icon(
                if (running) Icons.Filled.Stop else Icons.Filled.PlayArrow,
                contentDescription = null,
                tint = colors.onPrimary,
                modifier = Modifier.size(40.dp),
            )
        }
        RoundAction(
            icon = Icons.Outlined.Settings,
            label = stringResource(R.string.metronome_settings),
            description = stringResource(R.string.metronome_settings_description),
            onClick = onSettings,
        )
    }
}

private val PLAY_SIZE = 88.dp

@Composable
private fun RoundAction(icon: ImageVector, label: String, description: String, onClick: () -> Unit) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier
            .clip(RoundedCornerShape(12.dp))
            .clickable(onClick = onClick)
            .padding(6.dp)
            .semantics(mergeDescendants = true) {
                role = Role.Button
                contentDescription = description
            },
    ) {
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier
                .size(52.dp)
                .background(MaterialTheme.colorScheme.surfaceContainerHigh, CircleShape),
        ) {
            Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.onSurface)
        }
        Text(
            label,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = 6.dp),
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SoundSettingsSheet(
    settings: MetronomeSettings,
    onUpdate: ((MetronomeSettings) -> MetronomeSettings) -> Unit,
    onDismiss: () -> Unit,
) {
    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(
            verticalArrangement = Arrangement.spacedBy(12.dp),
            modifier = Modifier.padding(start = 24.dp, end = 24.dp, bottom = 32.dp),
        ) {
            Text(stringResource(R.string.metronome_sound_settings), style = MaterialTheme.typography.titleMedium)
            Text(
                stringResource(R.string.metronome_sound),
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.primary,
            )
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
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
                MetronomeSlider(
                    value = settings.volume,
                    onValueChange = { value -> onUpdate { it.copy(volume = value) } },
                    modifier = Modifier
                        .weight(1f)
                        .padding(start = 12.dp)
                        .semantics { contentDescription = volumeLabel },
                )
            }
        }
    }
}

/** A rounded section card, as used for the time signature and accent settings. */
@Composable
internal fun MetronomeCard(modifier: Modifier = Modifier, content: @Composable ColumnScope.() -> Unit) {
    Surface(
        shape = RoundedCornerShape(20.dp),
        color = MaterialTheme.colorScheme.surfaceContainer,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
        modifier = modifier.fillMaxWidth(),
    ) {
        Column(modifier = Modifier.padding(16.dp), content = content)
    }
}

@Composable
private fun TimeSignatureCard(
    settings: MetronomeSettings,
    onUpdate: ((MetronomeSettings) -> MetronomeSettings) -> Unit,
) {
    var editingCustom by rememberSaveable { mutableStateOf(false) }
    val isPreset = settings.timeSignature in TimeSignature.Presets
    MetronomeCard {
        Text(
            stringResource(R.string.metronome_time_signature),
            style = MaterialTheme.typography.titleSmall,
            color = MaterialTheme.colorScheme.onSurface,
        )
        Row(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 12.dp),
        ) {
            TimeSignature.Presets.forEach { preset ->
                Segment(
                    label = preset.toString(),
                    selected = settings.timeSignature == preset,
                    onClick = { onUpdate { it.copy(beatsPerBar = preset.beats, beatUnit = preset.unit) } },
                    modifier = Modifier.weight(1f),
                )
            }
            val customDescription = stringResource(R.string.metronome_custom_description)
            Segment(
                label = if (isPreset) stringResource(R.string.metronome_custom) else settings.timeSignature.toString(),
                selected = !isPreset,
                onClick = { editingCustom = true },
                modifier = Modifier
                    .weight(1.4f)
                    .semantics { contentDescription = customDescription },
            )
        }
        HorizontalDivider(
            color = MaterialTheme.colorScheme.outlineVariant,
            modifier = Modifier.padding(vertical = 14.dp),
        )
        Stepper(
            label = stringResource(R.string.metronome_beats_per_bar),
            value = settings.beatsPerBar,
            range = 1..MetronomeSettings.MAX_BEATS,
            decreaseLabel = stringResource(R.string.metronome_fewer_beats),
            increaseLabel = stringResource(R.string.metronome_more_beats),
            onChange = { beats -> onUpdate { it.copy(beatsPerBar = beats) } },
        )
    }
    if (editingCustom) {
        CustomSignatureDialog(
            initial = settings.timeSignature,
            onConfirm = { signature ->
                onUpdate { it.copy(beatsPerBar = signature.beats, beatUnit = signature.unit) }
                editingCustom = false
            },
            onDismiss = { editingCustom = false },
        )
    }
}

@Composable
private fun Segment(label: String, selected: Boolean, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val colors = MaterialTheme.colorScheme
    Box(
        contentAlignment = Alignment.Center,
        modifier = modifier
            .heightIn(min = 44.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(if (selected) colors.primary.copy(alpha = 0.28f) else colors.surfaceContainerHigh)
            .border(1.dp, if (selected) colors.primary else colors.outlineVariant, RoundedCornerShape(12.dp))
            .selectable(selected = selected, role = Role.RadioButton, onClick = onClick)
            .padding(horizontal = 4.dp),
    ) {
        Text(
            label,
            style = MaterialTheme.typography.labelLarge,
            color = if (selected) colors.onSurface else colors.onSurfaceVariant,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
private fun AccentCard(accent: Boolean, onToggle: () -> Unit) {
    MetronomeCard(
        modifier = Modifier
            .clip(RoundedCornerShape(20.dp))
            .clickable(role = Role.Switch, onClick = onToggle),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    stringResource(R.string.metronome_accent),
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                Text(
                    stringResource(R.string.metronome_accent_hint),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Switch(checked = accent, onCheckedChange = null)
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
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .clip(RoundedCornerShape(12.dp))
                .background(MaterialTheme.colorScheme.surfaceContainerHigh),
        ) {
            IconButton(onClick = { onChange(value - 1) }, enabled = value > range.first) {
                Icon(Icons.Filled.Remove, contentDescription = decreaseLabel)
            }
            Text(
                text = value.toString(),
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.width(36.dp),
                textAlign = TextAlign.Center,
            )
            IconButton(onClick = { onChange(value + 1) }, enabled = value < range.last) {
                Icon(Icons.Filled.Add, contentDescription = increaseLabel)
            }
        }
    }
}

@Composable
private fun CustomSignatureDialog(
    initial: TimeSignature,
    onConfirm: (TimeSignature) -> Unit,
    onDismiss: () -> Unit,
) {
    var beats by rememberSaveable { mutableIntStateOf(initial.beats) }
    var unit by rememberSaveable { mutableIntStateOf(initial.unit) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.metronome_custom_title)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                Stepper(
                    label = stringResource(R.string.metronome_beats_per_bar),
                    value = beats,
                    range = 1..MetronomeSettings.MAX_BEATS,
                    decreaseLabel = stringResource(R.string.metronome_fewer_beats),
                    increaseLabel = stringResource(R.string.metronome_more_beats),
                    onChange = { beats = it },
                )
                Text(stringResource(R.string.metronome_beat_unit), style = MaterialTheme.typography.bodyLarge)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                    BEAT_UNITS.forEach { option ->
                        Segment(
                            label = stringResource(R.string.metronome_beat_unit_option, option),
                            selected = unit == option,
                            onClick = { unit = option },
                            modifier = Modifier.weight(1f),
                        )
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = { onConfirm(TimeSignature(beats, unit)) }) {
                Text(stringResource(R.string.metronome_set))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.metronome_cancel)) }
        },
    )
}

/** The units [MetronomeSettings.of] accepts. */
private val BEAT_UNITS = listOf(4, 8)

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
