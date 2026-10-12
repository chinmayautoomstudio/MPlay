package com.autoomstudio.mp3studio.ui.equalizer

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.semantics.ProgressBarRangeInfo
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.progressBarRangeInfo
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.setProgress
import androidx.compose.ui.unit.dp
import com.autoomstudio.mp3studio.playback.equalizer.EqualizerBands
import kotlin.math.roundToInt

@Composable
fun VerticalEqSlider(
    valueDb: Float,
    onValueChange: (Float) -> Unit,
    enabled: Boolean,
    label: String,
    modifier: Modifier = Modifier,
) {
    val min = EqualizerBands.MIN_GAIN_DB
    val max = EqualizerBands.MAX_GAIN_DB
    val primary = MaterialTheme.colorScheme.primary
    val trackColor = MaterialTheme.colorScheme.surfaceContainerHighest
    val muted = primary.copy(alpha = if (enabled) 1f else 0.35f)
    val a11yLabel = "$label, ${formatDb(valueDb)}"
    var heightPx by remember { mutableFloatStateOf(1f) }
    val latestValue by rememberUpdatedState(valueDb)
    val latestOnChange by rememberUpdatedState(onValueChange)

    fun valueFromY(y: Float): Float {
        val fraction = (1f - (y / heightPx).coerceIn(0f, 1f))
        val raw = min + fraction * (max - min)
        val steps = ((raw - min) / EqualizerBands.GAIN_STEP_DB).roundToInt()
        return (min + steps * EqualizerBands.GAIN_STEP_DB).coerceIn(min, max)
    }

    val drawModifier = Modifier.fillMaxHeight().width(28.dp)
    Box(
        modifier = modifier
            .width(36.dp)
            .fillMaxHeight()
            .semantics {
                contentDescription = a11yLabel
                progressBarRangeInfo = ProgressBarRangeInfo(valueDb, min..max, steps = 48)
                if (enabled) {
                    setProgress { target ->
                        latestOnChange(EqualizerBands.clampGain(target))
                        true
                    }
                }
            }
            .pointerInput(enabled) {
                if (!enabled) return@pointerInput
                detectTapGestures { offset -> latestOnChange(valueFromY(offset.y)) }
            }
            .pointerInput(enabled) {
                if (!enabled) return@pointerInput
                detectDragGestures { change, _ ->
                    change.consume()
                    latestOnChange(valueFromY(change.position.y))
                }
            },
        contentAlignment = Alignment.Center,
    ) {
        Canvas(drawModifier) {
            heightPx = size.height
            val fraction = ((latestValue - min) / (max - min)).coerceIn(0f, 1f)
            val thumbY = size.height * (1f - fraction)
            val trackWidth = 6.dp.toPx()
            val trackLeft = (size.width - trackWidth) / 2f

            drawRoundRect(
                color = trackColor,
                topLeft = Offset(trackLeft, 0f),
                size = Size(trackWidth, size.height),
                cornerRadius = CornerRadius(trackWidth / 2f),
            )
            drawRoundRect(
                brush = Brush.verticalGradient(
                    colors = listOf(muted, muted.copy(alpha = 0.35f)),
                    startY = thumbY,
                    endY = size.height,
                ),
                topLeft = Offset(trackLeft, thumbY),
                size = Size(trackWidth, (size.height - thumbY).coerceAtLeast(0f)),
                cornerRadius = CornerRadius(trackWidth / 2f),
            )

            val thumbRadius = 10.dp.toPx()
            drawCircle(
                color = muted.copy(alpha = 0.35f),
                radius = thumbRadius + 4.dp.toPx(),
                center = Offset(size.width / 2f, thumbY),
            )
            drawCircle(
                color = Color.White,
                radius = thumbRadius,
                center = Offset(size.width / 2f, thumbY),
            )
            drawCircle(
                color = muted,
                radius = thumbRadius,
                center = Offset(size.width / 2f, thumbY),
                style = Stroke(width = 3.dp.toPx()),
            )
        }
    }
}

internal fun formatDb(value: Float): String {
    val rounded = EqualizerBands.clampGain(value)
    return if (rounded == 0f) "0 dB" else "%+.1f dB".format(rounded).replace(".0 ", " ")
}
