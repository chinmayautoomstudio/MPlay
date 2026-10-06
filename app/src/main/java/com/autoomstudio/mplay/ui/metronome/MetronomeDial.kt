package com.autoomstudio.mplay.ui.metronome

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.autoomstudio.mplay.R
import com.autoomstudio.mplay.metronome.BeatTick
import com.autoomstudio.mplay.metronome.MetronomeSettings
import kotlinx.coroutines.flow.StateFlow
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.sin

/**
 * The tempo dial: a glowing ring with the BPM and one dot per beat, minus and plus on either side and decorative
 * waveform bars behind (MT2, MT7). The ring and bars pulse on each beat; tapping the number opens BPM entry.
 */
@Composable
internal fun BpmDial(
    bpm: Int,
    beat: StateFlow<BeatTick?>,
    beatsPerBar: Int,
    accent: Boolean,
    running: Boolean,
    onChange: (Int) -> Unit,
    onEdit: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val tick by beat.collectAsStateWithLifecycle()
    val current = tick?.takeIf { running && it.beatsPerBar == beatsPerBar }?.beatInBar
    val glow = remember { Animatable(0f) }
    LaunchedEffect(tick?.index, running) {
        if (current == null) {
            glow.snapTo(0f)
            return@LaunchedEffect
        }
        glow.snapTo(if (current == 0 && accent) 1f else 0.6f)
        glow.animateTo(0f, tween(GLOW_FADE_MS))
    }
    BoxWithConstraints(
        contentAlignment = Alignment.Center,
        modifier = modifier
            .fillMaxWidth()
            .height(DIAL_HEIGHT),
    ) {
        val ringSize = (maxWidth - STEP_BUTTON * 2 - 16.dp).coerceIn(MIN_RING, MAX_RING)
        WaveformBars(glow = { glow.value }, modifier = Modifier.fillMaxSize())
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally),
            modifier = Modifier.fillMaxWidth(),
        ) {
            FilledTonalIconButton(
                onClick = { onChange(bpm - 1) },
                enabled = bpm > MetronomeSettings.MIN_BPM,
                modifier = Modifier.size(STEP_BUTTON),
            ) {
                Icon(Icons.Filled.Remove, contentDescription = stringResource(R.string.metronome_slower))
            }
            Ring(
                size = ringSize,
                bpm = bpm,
                beatsPerBar = beatsPerBar,
                accent = accent,
                current = current,
                glow = { glow.value },
                onEdit = onEdit,
            )
            FilledIconButton(
                onClick = { onChange(bpm + 1) },
                enabled = bpm < MetronomeSettings.MAX_BPM,
                modifier = Modifier.size(STEP_BUTTON),
            ) {
                Icon(Icons.Filled.Add, contentDescription = stringResource(R.string.metronome_faster))
            }
        }
    }
}

@Composable
private fun Ring(
    size: Dp,
    bpm: Int,
    beatsPerBar: Int,
    accent: Boolean,
    current: Int?,
    glow: () -> Float,
    onEdit: () -> Unit,
) {
    val colors = MaterialTheme.colorScheme
    val sweep = remember(colors) {
        Brush.sweepGradient(
            listOf(colors.primaryContainer, colors.primary, colors.tertiary, colors.primary, colors.primaryContainer),
        )
    }
    val editDescription = stringResource(R.string.metronome_bpm_description, bpm)
    Box(contentAlignment = Alignment.Center, modifier = Modifier.size(size)) {
        Canvas(Modifier.fillMaxSize()) {
            val radius = this.size.minDimension / 2
            val strength = glow()
            drawCircle(
                brush = Brush.radialGradient(
                    0.6f to colors.primary.copy(alpha = 0.22f + 0.3f * strength),
                    1f to Color.Transparent,
                    center = center,
                    radius = radius,
                ),
                radius = radius,
            )
            val ringWidth = RING_STROKE.toPx()
            val ringRadius = radius * 0.86f
            drawCircle(color = colors.surfaceContainer, radius = ringRadius)
            drawCircle(
                color = colors.outlineVariant,
                radius = ringRadius - ringWidth * 2.2f,
                style = Stroke(width = 1.dp.toPx()),
            )
            drawArc(
                brush = sweep,
                startAngle = 0f,
                sweepAngle = 360f,
                useCenter = false,
                topLeft = Offset(center.x - ringRadius, center.y - ringRadius),
                size = Size(ringRadius * 2, ringRadius * 2),
                style = Stroke(width = ringWidth * (1f + 0.35f * strength), cap = StrokeCap.Round),
            )
        }
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier
                    .clip(CircleShape)
                    .clickable(onClickLabel = stringResource(R.string.metronome_enter_bpm), onClick = onEdit)
                    .padding(horizontal = 16.dp, vertical = 4.dp)
                    .semantics(mergeDescendants = true) { contentDescription = editDescription },
            ) {
                Text(
                    text = stringResource(R.string.metronome_bpm),
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Text(
                    text = bpm.toString(),
                    style = if (size < MAX_RING) {
                        MaterialTheme.typography.displayMedium
                    } else {
                        MaterialTheme.typography.displayLarge
                    },
                    color = MaterialTheme.colorScheme.onSurface,
                )
            }
            BeatDots(
                beatsPerBar = beatsPerBar,
                accent = accent,
                current = current,
                modifier = Modifier.padding(top = 6.dp),
            )
        }
    }
}

/** One dot per beat; the sounding beat lights up, beat 1 is ringed when accented (MT7). */
@Composable
private fun BeatDots(beatsPerBar: Int, accent: Boolean, current: Int?, modifier: Modifier = Modifier) {
    val description = if (current != null) {
        stringResource(R.string.metronome_beat_description, current + 1, beatsPerBar)
    } else {
        ""
    }
    val dot = if (beatsPerBar > 8) 6.dp else 8.dp
    Row(
        horizontalArrangement = Arrangement.spacedBy(if (beatsPerBar > 8) 4.dp else 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        modifier = modifier.semantics { contentDescription = description },
    ) {
        repeat(beatsPerBar) { index ->
            val first = index == 0 && accent
            val active = index == current
            val color = when {
                active && first -> MaterialTheme.colorScheme.tertiary
                active -> MaterialTheme.colorScheme.primary
                first -> MaterialTheme.colorScheme.primary.copy(alpha = 0.5f)
                else -> MaterialTheme.colorScheme.surfaceContainerHighest
            }
            Box(
                Modifier
                    .size(if (first) dot + 2.dp else dot)
                    .graphicsLayer {
                        val scale = if (active) 1.3f else 1f
                        scaleX = scale
                        scaleY = scale
                    }
                    .background(color, CircleShape)
                    .then(
                        if (first && !active) {
                            Modifier.border(1.dp, MaterialTheme.colorScheme.tertiary, CircleShape)
                        } else {
                            Modifier
                        },
                    ),
            )
        }
    }
}

/** Decoration only: bars of fixed pseudo-random heights, fading towards the edges and brightening on the beat. */
@Composable
private fun WaveformBars(glow: () -> Float, modifier: Modifier = Modifier) {
    val color = MaterialTheme.colorScheme.primary
    Canvas(modifier.clearAndSetSemantics {}) {
        val barWidth = 3.dp.toPx()
        val gap = 5.dp.toPx()
        val count = (size.width / (barWidth + gap)).toInt()
        if (count <= 0) return@Canvas
        val start = (size.width - count * (barWidth + gap) + gap) / 2
        val strength = glow()
        for (i in 0 until count) {
            val position = (i + 0.5f) / count
            val edgeFade = 1f - abs(position - 0.5f) * 2f
            val shape = 0.25f + 0.75f * abs(sin(i * 1.7f) * cos(i * 0.63f))
            val height = size.height * 0.7f * shape * (0.35f + 0.65f * edgeFade)
            drawRoundRect(
                color = color.copy(alpha = (0.22f + 0.3f * strength) * (0.3f + 0.7f * edgeFade)),
                topLeft = Offset(start + i * (barWidth + gap), (size.height - height) / 2),
                size = Size(barWidth, height),
                cornerRadius = CornerRadius(barWidth / 2),
            )
        }
    }
}

private const val GLOW_FADE_MS = 320
private val DIAL_HEIGHT = 240.dp
private val MIN_RING = 168.dp
private val MAX_RING = 220.dp
private val RING_STROKE = 6.dp
private val STEP_BUTTON = 56.dp
