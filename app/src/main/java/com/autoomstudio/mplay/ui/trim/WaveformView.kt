package com.autoomstudio.mplay.ui.trim

import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import com.autoomstudio.mplay.R
import com.autoomstudio.mplay.data.clip.TrimRange
import com.autoomstudio.mplay.ui.common.formatPreciseDuration
import kotlin.math.abs
import kotlin.math.roundToInt
import kotlin.math.sin

private val HandleWidth = 48.dp
private const val ACCESSIBILITY_STEP_MS = 1_000L

private enum class Handle { Start, End }

/**
 * Peaks with the selected [range] highlighted and a handle at each end. A drag moves whichever
 * handle is nearer to where it started. [peaks] is null while the waveform loads.
 */
@Composable
fun WaveformView(
    peaks: FloatArray?,
    range: TrimRange,
    playheadMs: Long?,
    enabled: Boolean,
    onMoveStart: (Long) -> Unit,
    onMoveEnd: (Long) -> Unit,
    onMoveFinished: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val primary = MaterialTheme.colorScheme.primary
    val dimmed = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.35f)
    val playheadColor = MaterialTheme.colorScheme.onSurface
    val currentRange by rememberUpdatedState(range)
    val loadingAlpha by rememberInfiniteTransition(label = "waveformLoading").animateFloat(
        initialValue = 0.25f,
        targetValue = 0.6f,
        animationSpec = infiniteRepeatable(tween(700), RepeatMode.Reverse),
        label = "waveformLoadingAlpha",
    )

    BoxWithConstraints(modifier = modifier) {
        val density = LocalDensity.current
        val padPx = with(density) { (HandleWidth / 2).toPx() }
        val trackPx = (constraints.maxWidth - 2 * padPx).coerceAtLeast(1f)
        val duration = range.durationMs.coerceAtLeast(1L)
        val xOf = { ms: Long -> padPx + ms.toFloat() / duration * trackPx }
        val msOf = { x: Float -> (((x - padPx) / trackPx) * duration).toLong().coerceIn(0L, duration) }
        var activeHandle by remember { mutableStateOf<Handle?>(null) }

        Canvas(
            modifier = Modifier
                .fillMaxSize()
                .pointerInput(enabled, duration, trackPx) {
                    if (!enabled) return@pointerInput
                    detectDragGestures(
                        onDragStart = { offset ->
                            val r = currentRange
                            activeHandle = if (abs(offset.x - xOf(r.startMs)) <= abs(offset.x - xOf(r.endMs))) {
                                Handle.Start
                            } else {
                                Handle.End
                            }
                        },
                        onDragEnd = {
                            activeHandle = null
                            onMoveFinished()
                        },
                        onDragCancel = {
                            activeHandle = null
                            onMoveFinished()
                        },
                        onDrag = { change, _ ->
                            change.consume()
                            when (activeHandle) {
                                Handle.Start -> onMoveStart(msOf(change.position.x))
                                Handle.End -> onMoveEnd(msOf(change.position.x))
                                null -> Unit
                            }
                        },
                    )
                },
        ) {
            val startX = xOf(range.startMs)
            val endX = xOf(range.endMs)
            drawRect(
                color = primary.copy(alpha = 0.12f),
                topLeft = Offset(startX, 0f),
                size = Size(endX - startX, size.height),
            )
            if (peaks != null) {
                drawBars(peaks.size, padPx, trackPx) { index, x ->
                    val color = if (x in startX..endX) primary else dimmed
                    peaks[index] to color
                }
            } else {
                val placeholder = primary.copy(alpha = loadingAlpha)
                drawBars(PLACEHOLDER_BARS, padPx, trackPx) { index, _ ->
                    (0.25f + 0.5f * abs(sin(index * 0.37f)) * abs(sin(index * 0.11f))) to placeholder
                }
            }
            drawHandle(startX, primary)
            drawHandle(endX, primary)
            playheadMs?.let { ms ->
                val x = xOf(ms)
                drawLine(playheadColor, Offset(x, 0f), Offset(x, size.height), strokeWidth = 2.dp.toPx())
            }
        }

        HandleSemantics(
            handle = Handle.Start,
            positionMs = range.startMs,
            centerX = xOf(range.startMs),
            enabled = enabled,
            onMove = { onMoveStart(it); onMoveFinished() },
        )
        HandleSemantics(
            handle = Handle.End,
            positionMs = range.endMs,
            centerX = xOf(range.endMs),
            enabled = enabled,
            onMove = { onMoveEnd(it); onMoveFinished() },
        )
    }
}

/** An invisible 48dp target over a handle that gives screen readers its position and 1 s moves. */
@Composable
private fun HandleSemantics(
    handle: Handle,
    positionMs: Long,
    centerX: Float,
    enabled: Boolean,
    onMove: (Long) -> Unit,
) {
    val density = LocalDensity.current
    val half = with(density) { (HandleWidth / 2).toPx() }
    val label = stringResource(
        if (handle == Handle.Start) R.string.trim_start_handle else R.string.trim_end_handle,
        formatPreciseDuration(positionMs),
    )
    val earlier = stringResource(R.string.trim_move_earlier)
    val later = stringResource(R.string.trim_move_later)
    Box(
        modifier = Modifier
            .offset { IntOffset((centerX - half).roundToInt(), 0) }
            .width(HandleWidth)
            .fillMaxHeight()
            .semantics {
                contentDescription = label
                if (enabled) {
                    customActions = listOf(
                        CustomAccessibilityAction(earlier) { onMove(positionMs - ACCESSIBILITY_STEP_MS); true },
                        CustomAccessibilityAction(later) { onMove(positionMs + ACCESSIBILITY_STEP_MS); true },
                    )
                }
            },
    )
}

private const val PLACEHOLDER_BARS = 120

private inline fun DrawScope.drawBars(
    count: Int,
    padPx: Float,
    trackPx: Float,
    bar: (index: Int, x: Float) -> Pair<Float, Color>,
) {
    if (count == 0) return
    val step = trackPx / count
    val stroke = (step * 0.6f).coerceAtLeast(1f)
    val centerY = size.height / 2
    val minHeight = 2.dp.toPx()
    val maxHeight = size.height * 0.85f
    for (i in 0 until count) {
        val x = padPx + step * (i + 0.5f)
        val (peak, color) = bar(i, x)
        val half = (peak * maxHeight).coerceAtLeast(minHeight) / 2
        drawLine(color, Offset(x, centerY - half), Offset(x, centerY + half), strokeWidth = stroke)
    }
}

private fun DrawScope.drawHandle(x: Float, color: Color) {
    drawLine(color, Offset(x, 0f), Offset(x, size.height), strokeWidth = 3.dp.toPx())
    val knob = 8.dp.toPx()
    drawCircle(color, radius = knob, center = Offset(x, knob))
    drawCircle(color, radius = knob, center = Offset(x, size.height - knob))
}
