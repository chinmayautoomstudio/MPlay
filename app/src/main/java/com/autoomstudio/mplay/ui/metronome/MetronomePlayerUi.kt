package com.autoomstudio.mplay.ui.metronome

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.GraphicEq
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.autoomstudio.mplay.R
import com.autoomstudio.mplay.data.model.Song
import com.autoomstudio.mplay.metronome.MetronomeSettings
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
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(start = 24.dp, end = 24.dp, bottom = 32.dp),
        ) {
            Text(
                text = stringResource(R.string.metronome_title),
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurface,
            )
            MetronomeControls(
                state = state,
                beat = viewModel.beat,
                tapCount = tapCount,
                onUpdate = viewModel::update,
                onToggle = viewModel::toggle,
                onTap = viewModel::tap,
                tempoExtras = {
                    DetectTempo(
                        detection = detection.takeIf { song != null && it.songId == song.id }
                            ?: TempoDetection.Idle,
                        bpm = state.settings.bpm,
                        canDetect = song != null,
                        onDetect = { song?.let(viewModel::detect) },
                        onScale = viewModel::scaleBpm,
                    )
                },
            )
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
) {
    val running = detection is TempoDetection.Running
    Column(verticalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
            OutlinedButton(onClick = onDetect, enabled = canDetect && !running, modifier = Modifier.weight(1f)) {
                Icon(Icons.Outlined.GraphicEq, contentDescription = null)
                Text(stringResource(R.string.metronome_detect), Modifier.padding(start = 8.dp))
            }
            val halveLabel = stringResource(R.string.metronome_halve_description)
            OutlinedButton(
                onClick = { onScale(0.5) },
                enabled = bpm / 2 >= MetronomeSettings.MIN_BPM,
                modifier = Modifier.semantics { contentDescription = halveLabel },
            ) {
                Text(stringResource(R.string.metronome_halve))
            }
            val doubleLabel = stringResource(R.string.metronome_double_description)
            OutlinedButton(
                onClick = { onScale(2.0) },
                enabled = bpm * 2 <= MetronomeSettings.MAX_BPM,
                modifier = Modifier.semantics { contentDescription = doubleLabel },
            ) {
                Text(stringResource(R.string.metronome_double))
            }
        }
        when (detection) {
            TempoDetection.Idle -> Unit
            is TempoDetection.Running -> {
                Text(
                    stringResource(R.string.metronome_detecting),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                LinearProgressIndicator(progress = { detection.progress }, modifier = Modifier.fillMaxWidth())
            }
            is TempoDetection.Done -> {
                Text(
                    stringResource(R.string.metronome_detected, detection.estimate.bpm.roundToInt()),
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.onSurface,
                )
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
            }
            is TempoDetection.Failed -> Text(
                stringResource(R.string.metronome_detect_failed),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.error,
            )
        }
    }
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
