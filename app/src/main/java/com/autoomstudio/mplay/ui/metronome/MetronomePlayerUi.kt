package com.autoomstudio.mplay.ui.metronome

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.outlined.GraphicEq
import androidx.compose.material.icons.outlined.Lightbulb
import androidx.compose.material.icons.outlined.MusicNote
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Snackbar
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.autoomstudio.mplay.R
import com.autoomstudio.mplay.data.model.Song
import com.autoomstudio.mplay.metronome.MetronomeSettings
import com.autoomstudio.mplay.metronome.SyncState
import com.autoomstudio.mplay.metronome.TempoConfidence
import com.autoomstudio.mplay.ui.components.MetronomeIcons
import kotlin.math.roundToInt

/** The compact metronome control on Now Playing (MT12); shows the BPM while the metronome runs. */
@Composable
fun MetronomeChip(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: MetronomeViewModel = viewModel(factory = MetronomeViewModel.Factory),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    FilterChip(
        selected = state.running,
        onClick = onClick,
        label = {
            Text(
                if (state.running) {
                    stringResource(R.string.metronome_chip_bpm, state.settings.bpm)
                } else {
                    stringResource(R.string.metronome_title)
                },
                maxLines = 1,
            )
        },
        leadingIcon = {
            Icon(MetronomeIcons.Default, contentDescription = null, modifier = Modifier.size(FilterChipDefaults.IconSize))
        },
        modifier = modifier,
    )
}

/** Same controls as the Metronome tab, plus Detect BPM for [song] (MT9, MT12). */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MetronomeSheet(
    song: Song?,
    onDismiss: () -> Unit,
    viewModel: MetronomeViewModel = viewModel(factory = MetronomeViewModel.Factory),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val tapCount by viewModel.tapCount.collectAsStateWithLifecycle()
    val detection by viewModel.detection.collectAsStateWithLifecycle()
    val sync by viewModel.sync.collectAsStateWithLifecycle()
    // The sheet covers the main screen's snackbar, so it shows its own (MT19).
    val snackbarHostState = remember { SnackbarHostState() }
    val resources = LocalContext.current.resources
    LaunchedEffect(viewModel) {
        viewModel.meterApplied.collect { event ->
            snackbarHostState.currentSnackbarData?.dismiss()
            val result = snackbarHostState.showSnackbar(
                message = resources.getString(R.string.metronome_meter_set, event.timeSignature.toString()),
                actionLabel = resources.getString(R.string.metronome_meter_undo),
                duration = SnackbarDuration.Short,
            )
            if (result == SnackbarResult.ActionPerformed) viewModel.undoMeter(event)
        }
    }
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
    ) {
        Box(modifier = Modifier.fillMaxWidth()) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
                    .padding(start = 24.dp, end = 24.dp, bottom = 32.dp),
            ) {
                val songDetection = detection.takeIf { song != null && it.songId == song.id } ?: TempoDetection.Idle
                val songSync = (sync as? SyncState.On)?.takeIf { song != null && it.songId == song.id }
                MetronomeControls(
                    state = state,
                    beat = viewModel.beat,
                    tapCount = tapCount,
                    onUpdate = viewModel::update,
                    onToggle = viewModel::toggle,
                    onTap = viewModel::tap,
                    onToggleMute = viewModel::toggleMute,
                    onBack = onDismiss,
                    tempoExtras = {
                        DetectTempo(
                            detection = songDetection,
                            bpm = state.settings.bpm,
                            canDetect = song != null,
                            onDetect = { song?.let(viewModel::detect) },
                            onScale = viewModel::scaleBpm,
                            onApplyMeter = viewModel::applyMeter,
                        )
                    },
                    syncTile = (songDetection as? TempoDetection.Done)?.let { done ->
                        {
                            SyncTile(
                                sync = songSync,
                                available = done.grid != null,
                                onToggle = viewModel::toggleSync,
                                modifier = Modifier.weight(1f),
                            )
                        }
                    },
                )
            }
            val undoDescription = stringResource(R.string.metronome_meter_undo_description)
            SnackbarHost(
                hostState = snackbarHostState,
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(16.dp),
            ) { data ->
                Snackbar(
                    action = data.visuals.actionLabel?.let { label ->
                        {
                            TextButton(
                                onClick = { data.performAction() },
                                modifier = Modifier.semantics { contentDescription = undoDescription },
                            ) {
                                Text(label)
                            }
                        }
                    },
                ) {
                    Text(data.visuals.message)
                }
            }
        }
    }
}

@Composable
private fun DetectTempo(
    detection: TempoDetection,
    bpm: Int,
    canDetect: Boolean,
    onDetect: () -> Unit,
    onScale: (Double) -> Unit,
    onApplyMeter: () -> Unit,
) {
    val running = detection is TempoDetection.Running
    Column(verticalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.fillMaxWidth()) {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
            BoxButton(onClick = onDetect, enabled = canDetect && !running, modifier = Modifier.weight(1f)) {
                Icon(Icons.Outlined.GraphicEq, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                Text(stringResource(R.string.metronome_detect), Modifier.padding(start = 10.dp))
            }
            BoxButton(
                onClick = { onScale(0.5) },
                enabled = bpm / 2 >= MetronomeSettings.MIN_BPM,
                description = stringResource(R.string.metronome_halve_description),
                modifier = Modifier.width(64.dp),
            ) {
                Text(stringResource(R.string.metronome_halve))
            }
            BoxButton(
                onClick = { onScale(2.0) },
                enabled = bpm * 2 <= MetronomeSettings.MAX_BPM,
                description = stringResource(R.string.metronome_double_description),
                modifier = Modifier.width(64.dp),
            ) {
                Text(stringResource(R.string.metronome_double))
            }
        }
        when (detection) {
            TempoDetection.Idle -> Unit
            is TempoDetection.Running -> MetronomeCard {
                Text(
                    stringResource(R.string.metronome_detecting),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                LinearProgressIndicator(
                    progress = { detection.progress },
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 10.dp),
                )
            }
            is TempoDetection.Done -> DetectResult(detection, onApplyMeter)
            is TempoDetection.Failed -> MetronomeCard {
                Text(
                    stringResource(R.string.metronome_detect_failed),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.error,
                )
            }
        }
    }
}

/** An outlined rounded box, for the Detect BPM row. */
@Composable
private fun BoxButton(
    onClick: () -> Unit,
    enabled: Boolean,
    modifier: Modifier = Modifier,
    description: String? = null,
    content: @Composable RowScope.() -> Unit,
) {
    Surface(
        onClick = onClick,
        enabled = enabled,
        shape = TILE_SHAPE,
        color = MaterialTheme.colorScheme.surfaceContainer,
        contentColor = MaterialTheme.colorScheme.onSurface.copy(alpha = if (enabled) 1f else 0.4f),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
        modifier = modifier
            .heightIn(min = 52.dp)
            .then(if (description != null) Modifier.semantics { contentDescription = description } else Modifier),
    ) {
        Row(
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(horizontal = 12.dp),
            content = content,
        )
    }
}

/** "120 BPM, 4/4" with a hint for each part, and the time signature as a pill when it wasn't applied (MT19, MT21). */
@Composable
private fun DetectResult(detection: TempoDetection.Done, onApplyMeter: () -> Unit) {
    val meter = detection.meter
    val tempoBpm = detection.estimate.bpm.roundToInt()
    val headline = when {
        meter == null -> stringResource(R.string.metronome_detected, tempoBpm)
        !detection.applied -> stringResource(R.string.metronome_detected_with_meter, tempoBpm, meter.timeSignature.toString())
        // 6/8 clicks the eighths; the felt pulse is the dotted quarter, three clicks.
        meter.beatUnit == 8 -> stringResource(
            R.string.metronome_detected_with_meter_felt,
            meter.clickBpm,
            meter.timeSignature.toString(),
            (meter.clickBpm / 3.0).roundToInt(),
        )
        else -> stringResource(R.string.metronome_detected_with_meter, meter.clickBpm, meter.timeSignature.toString())
    }
    MetronomeCard {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(headline, style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurface)
                Text(
                    stringResource(
                        when (detection.estimate.confidence) {
                            TempoConfidence.High -> R.string.metronome_confidence_high
                            TempoConfidence.Medium -> R.string.metronome_confidence_medium
                            TempoConfidence.Low -> R.string.metronome_confidence_low
                        },
                    ),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Text(
                    stringResource(
                        when (meter?.confidence) {
                            null -> R.string.metronome_meter_unknown
                            TempoConfidence.High -> R.string.metronome_meter_confidence_high
                            TempoConfidence.Medium -> R.string.metronome_meter_confidence_medium
                            TempoConfidence.Low -> R.string.metronome_meter_confidence_low
                        },
                    ),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            if (meter != null && !detection.applied) {
                MeterSuggestion(signature = meter.timeSignature.toString(), onClick = onApplyMeter)
            }
        }
    }
}

/** "Looks like 3/4. Use it?" as a tappable pill (MT19). */
@Composable
private fun MeterSuggestion(signature: String, onClick: () -> Unit) {
    val description = stringResource(R.string.metronome_meter_suggestion_description, signature)
    Surface(
        onClick = onClick,
        shape = TILE_SHAPE,
        color = MaterialTheme.colorScheme.primary.copy(alpha = 0.16f),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.6f)),
        modifier = Modifier
            .widthIn(max = 168.dp)
            .semantics { contentDescription = description },
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 10.dp),
        ) {
            Icon(
                Icons.Outlined.Lightbulb,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(20.dp),
            )
            Text(
                stringResource(R.string.metronome_meter_suggestion, signature),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier
                    .weight(1f, fill = false)
                    .padding(horizontal = 8.dp),
            )
            Icon(
                Icons.AutoMirrored.Filled.KeyboardArrowRight,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(18.dp),
            )
        }
    }
}

/** Turns following the song on or off, and says what the clicks are doing while on. */
@Composable
private fun SyncTile(sync: SyncState.On?, available: Boolean, onToggle: () -> Unit, modifier: Modifier = Modifier) {
    ActionTile(
        icon = Icons.Outlined.MusicNote,
        title = stringResource(R.string.metronome_sync),
        subtitle = stringResource(
            when {
                sync == null -> R.string.metronome_sync_hint
                sync.musicPaused -> R.string.metronome_sync_paused
                else -> R.string.metronome_sync_following
            },
        ),
        onClick = onToggle,
        selected = sync != null,
        enabled = available,
        description = stringResource(
            if (sync != null) R.string.metronome_sync_on_description else R.string.metronome_sync_off_description,
        ),
        modifier = modifier,
    )
}

/** A small metronome mark for the mini player that pulses on each beat while the metronome runs (MT12). */
@Composable
fun MetronomeMiniIndicator(
    modifier: Modifier = Modifier,
    viewModel: MetronomeViewModel = viewModel(factory = MetronomeViewModel.Factory),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    if (!state.running) return
    val tick by viewModel.beat.collectAsStateWithLifecycle()
    val pulse = remember { Animatable(1f) }
    LaunchedEffect(tick?.index) {
        if (tick == null) return@LaunchedEffect
        pulse.snapTo(1f)
        pulse.animateTo(0.45f, tween(PULSE_FADE_MS))
    }
    val description = stringResource(R.string.metronome_mini_indicator, state.settings.bpm)
    Icon(
        imageVector = MetronomeIcons.Default,
        contentDescription = description,
        tint = MaterialTheme.colorScheme.primary,
        modifier = modifier
            .size(16.dp)
            .graphicsLayer { alpha = pulse.value },
    )
}

private const val PULSE_FADE_MS = 250
