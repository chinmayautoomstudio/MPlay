package com.autoomstudio.mp3studio.ui.components

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.material3.LocalContentColor
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.drawscope.inset
import com.autoomstudio.mp3studio.ui.theme.MotionMedium
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive

/** Bar heights (fraction of the icon) matching the silhouette of Icons.Filled.GraphicEq. */
private val RestingHeights = listOf(0.34f, 0.67f, 1f, 0.67f, 0.34f)
private val SwayRanges = listOf(0.25f..0.6f, 0.4f..0.9f, 0.55f..1f, 0.35f..0.85f, 0.2f..0.55f)
private val SwayDurationsMs = listOf(900, 760, 1100, 820, 980)
private val SwayDelaysMs = listOf(0L, 180L, 90L, 260L, 140L)
private const val IconInset = 2f / 24f

/**
 * Lofi mode's waveform glyph. While [active] the bars sway slowly out of phase; otherwise they
 * settle into the GraphicEq resting shape. Drawn in [LocalContentColor] so chips can tint it.
 */
@Composable
fun LofiWaveIcon(active: Boolean, modifier: Modifier = Modifier) {
    val color = LocalContentColor.current
    val animationsEnabled = rememberAnimationsEnabled()
    val heights = remember { RestingHeights.map { Animatable(it) } }

    heights.forEachIndexed { index, height ->
        LaunchedEffect(active, animationsEnabled) {
            val resting = RestingHeights[index]
            if (!active || !animationsEnabled) {
                height.animateTo(resting, tween(MotionMedium, easing = FastOutSlowInEasing))
                return@LaunchedEffect
            }
            val range = SwayRanges[index]
            val sway = tween<Float>(SwayDurationsMs[index], easing = FastOutSlowInEasing)
            delay(SwayDelaysMs[index])
            while (isActive) {
                height.animateTo(range.endInclusive, sway)
                height.animateTo(range.start, sway)
            }
        }
    }

    Canvas(modifier = modifier) {
        // Same optical padding as a 24-unit Material icon glyph.
        inset(size.width * IconInset, size.height * IconInset) {
            val count = RestingHeights.size
            // Bars take 2 of every 3 units across, with a unit-wide gap between them.
            val unit = size.width / (count * 3 - 1)
            val barWidth = unit * 2
            val corner = CornerRadius(barWidth / 2, barWidth / 2)
            heights.forEachIndexed { index, height ->
                val barHeight = (size.height * height.value).coerceAtLeast(barWidth)
                drawRoundRect(
                    color = color,
                    topLeft = Offset(index * unit * 3, (size.height - barHeight) / 2),
                    size = Size(barWidth, barHeight),
                    cornerRadius = corner,
                )
            }
        }
    }
}
