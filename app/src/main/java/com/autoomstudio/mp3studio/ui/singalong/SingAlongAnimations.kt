package com.autoomstudio.mp3studio.ui.singalong

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.SizeTransform
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.MutableTransitionState
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.keyframes
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathMeasure
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.autoomstudio.mp3studio.R
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.cos
import kotlin.math.sin

/** The "Recording" dot; blinks unless system animations are off. */
@Composable
internal fun PulsingDot(color: Color, animate: Boolean, modifier: Modifier = Modifier) {
    val alpha = if (animate) {
        rememberInfiniteTransition(label = "dot").animateFloat(
            initialValue = 1f,
            targetValue = 0.3f,
            animationSpec = infiniteRepeatable(tween(800), RepeatMode.Reverse),
            label = "dotAlpha",
        )
    } else {
        null
    }
    Box(
        modifier
            .size(12.dp)
            .graphicsLayer { this.alpha = alpha?.value ?: 1f }
            .background(color, CircleShape),
    )
}

/**
 * Rings that expand out from behind a round button of [buttonSize] and a halo that grows with the
 * voice [level] (0 to 1). Draws nothing when [animate] is false.
 */
@Composable
internal fun VoiceRings(level: Float, color: Color, buttonSize: Dp, animate: Boolean, modifier: Modifier = Modifier) {
    if (!animate) return
    val phase by rememberInfiniteTransition(label = "rings").animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(RING_PERIOD_MS, easing = LinearEasing)),
        label = "ringPhase",
    )
    val halo by animateFloatAsState(level.coerceIn(0f, 1f), tween(120), label = "halo")
    Canvas(modifier.clearAndSetSemantics {}) {
        val base = buttonSize.toPx() / 2
        val outer = size.minDimension / 2
        val haloRadius = base + (outer - base) * (0.25f + 0.75f * halo)
        drawCircle(
            Brush.radialGradient(
                0f to color.copy(alpha = 0.1f + 0.35f * halo),
                1f to Color.Transparent,
                center = center,
                radius = haloRadius,
            ),
            radius = haloRadius,
        )
        val stroke = Stroke(2.dp.toPx())
        repeat(RINGS) { i ->
            val p = (phase + i.toFloat() / RINGS) % 1f
            drawCircle(
                color.copy(alpha = (1f - p) * (0.25f + 0.45f * halo)),
                radius = base + (outer - base) * p,
                style = stroke,
            )
        }
    }
}

/**
 * Recent mic levels as bars, newest on the right. [sampleCount] goes up by one per added level; with
 * [animate] the bars glide left between samples instead of jumping.
 */
@Composable
internal fun LiveWaveform(
    levels: List<Float>,
    sampleCount: Long,
    clipLevel: Float,
    animate: Boolean,
    modifier: Modifier = Modifier,
) {
    val description = stringResource(R.string.singalong_level_description)
    val shift = remember { Animatable(1f) }
    LaunchedEffect(sampleCount, animate) {
        if (!animate) return@LaunchedEffect
        shift.snapTo(0f)
        shift.animateTo(1f, tween(WAVE_STEP_MS, easing = LinearEasing))
    }
    val normal = MaterialTheme.colorScheme.primary
    val clipped = MaterialTheme.colorScheme.error
    val track = MaterialTheme.colorScheme.surfaceVariant
    Canvas(modifier.semantics { contentDescription = description }) {
        val step = size.width / WAVE_BARS
        val barWidth = step * 0.55f
        val minHeight = barWidth
        val offset = if (animate) (1f - shift.value) * step else 0f
        drawLine(track, Offset(0f, center.y), Offset(size.width, center.y), strokeWidth = 1.dp.toPx())
        clipRect {
            val n = levels.size
            levels.forEachIndexed { i, value ->
                val x = size.width - (n - i) * step + step / 2 + offset
                val height = (value.coerceIn(0f, 1f) * size.height).coerceAtLeast(minHeight)
                val fade = (x / (size.width * 0.3f)).coerceIn(0.15f, 1f)
                drawLine(
                    color = (if (value > clipLevel) clipped else normal).copy(alpha = fade),
                    start = Offset(x, center.y - height / 2 + barWidth / 2),
                    end = Offset(x, center.y + height / 2 - barWidth / 2),
                    strokeWidth = barWidth,
                    cap = StrokeCap.Round,
                )
            }
        }
    }
}

/** Elapsed time whose digits roll as they change. */
@Composable
internal fun RollingTimer(text: String, animate: Boolean, modifier: Modifier = Modifier) {
    if (!animate) {
        Text(text, fontSize = 64.sp, fontWeight = FontWeight.Light, modifier = modifier)
        return
    }
    Row(modifier.clearAndSetSemantics { contentDescription = text }) {
        text.forEachIndexed { index, char ->
            key(index) {
                AnimatedContent(
                    targetState = char,
                    transitionSpec = {
                        (slideInVertically { it / 2 } + fadeIn()) togetherWith
                            (slideOutVertically { -it / 2 } + fadeOut()) using SizeTransform(clip = false)
                    },
                    label = "digit",
                ) { shown ->
                    Text(shown.toString(), fontSize = 64.sp, fontWeight = FontWeight.Light)
                }
            }
        }
    }
}

/** Circular save progress with the percent inside and a bobbing mic above. */
@Composable
internal fun SavingRing(progress: Float, animate: Boolean, modifier: Modifier = Modifier) {
    val shown by animateFloatAsState(
        progress.coerceIn(0f, 1f),
        if (animate) tween(300) else snap(),
        label = "saveProgress",
    )
    val bob = if (animate) {
        rememberInfiniteTransition(label = "bob").animateFloat(
            initialValue = -4f,
            targetValue = 4f,
            animationSpec = infiniteRepeatable(tween(900, easing = FastOutSlowInEasing), RepeatMode.Reverse),
            label = "bobOffset",
        )
    } else {
        null
    }
    val colors = MaterialTheme.colorScheme
    val percent = (shown * 100).toInt()
    val description = stringResource(R.string.singalong_saving_progress, (progress * 100).toInt())
    Column(modifier, horizontalAlignment = Alignment.CenterHorizontally) {
        Icon(
            Icons.Filled.Mic,
            contentDescription = null,
            tint = colors.primary,
            modifier = Modifier
                .size(32.dp)
                .graphicsLayer { translationY = (bob?.value ?: 0f) * density },
        )
        Spacer(Modifier.height(16.dp))
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier
                .size(160.dp)
                .clearAndSetSemantics { contentDescription = description },
        ) {
            Canvas(Modifier.fillMaxSize()) {
                val width = 10.dp.toPx()
                val inset = width / 2
                val arcSize = Size(size.width - width, size.height - width)
                val radius = size.minDimension / 2 - inset
                drawCircle(colors.surfaceVariant, radius = radius, style = Stroke(width))
                // The round cap reaches behind the start; keep it clear of where the gradient wraps.
                val capDegrees = Math.toDegrees((inset / radius).toDouble()).toFloat() + 1f
                rotate(-90f - capDegrees) {
                    drawArc(
                        brush = Brush.sweepGradient(listOf(colors.primary.copy(alpha = 0.5f), colors.primary, colors.tertiary)),
                        startAngle = capDegrees,
                        sweepAngle = 360f * shown,
                        useCenter = false,
                        topLeft = Offset(inset, inset),
                        size = arcSize,
                        style = Stroke(width, cap = StrokeCap.Round),
                    )
                }
            }
            Text("$percent%", style = MaterialTheme.typography.headlineMedium)
        }
    }
}

/** A circle that springs in, a check that draws itself, and a one-shot burst of dots. */
@Composable
internal fun SavedCheck(animate: Boolean, modifier: Modifier = Modifier) {
    val scale = remember { Animatable(if (animate) 0f else 1f) }
    val draw = remember { Animatable(if (animate) 0f else 1f) }
    val burst = remember { Animatable(if (animate) 0f else 1f) }
    LaunchedEffect(Unit) {
        if (!animate) return@LaunchedEffect
        launch { scale.animateTo(1f, spring(dampingRatio = 0.5f, stiffness = Spring.StiffnessLow)) }
        delay(200)
        launch { burst.animateTo(1f, tween(700, easing = FastOutSlowInEasing)) }
        draw.animateTo(1f, tween(400, easing = FastOutSlowInEasing))
    }
    val primary = MaterialTheme.colorScheme.primary
    val secondary = MaterialTheme.colorScheme.secondary
    val onPrimary = MaterialTheme.colorScheme.onPrimary
    val segment = remember { Path() }
    val measure = remember { PathMeasure() }
    Canvas(modifier.size(160.dp).clearAndSetSemantics {}) {
        val radius = 48.dp.toPx()
        val outer = size.minDimension / 2
        if (burst.value < 1f) {
            repeat(BURST_DOTS) { i ->
                val angle = Math.toRadians(i * 360.0 / BURST_DOTS + 10.0 * (i % 2))
                val distance = radius + (outer - radius) * burst.value * (if (i % 2 == 0) 1f else 0.8f)
                drawCircle(
                    color = (if (i % 2 == 0) primary else secondary).copy(alpha = 1f - burst.value),
                    radius = 4.dp.toPx() * (1f - burst.value * 0.5f),
                    center = Offset(center.x + distance * cos(angle).toFloat(), center.y + distance * sin(angle).toFloat()),
                )
            }
        }
        scale(scale.value, pivot = center) {
            drawCircle(primary, radius)
            if (draw.value > 0f) {
                val check = Path().apply {
                    moveTo(center.x - radius * 0.42f, center.y + radius * 0.02f)
                    lineTo(center.x - radius * 0.12f, center.y + radius * 0.32f)
                    lineTo(center.x + radius * 0.45f, center.y - radius * 0.3f)
                }
                measure.setPath(check, false)
                segment.reset()
                measure.getSegment(0f, measure.length * draw.value, segment, true)
                drawPath(
                    segment,
                    onPrimary,
                    style = Stroke(6.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round),
                )
            }
        }
    }
}

/** Fades and slides [content] up after [delayMs]; shown at once when [animate] is false. */
@Composable
internal fun StaggeredIn(delayMs: Int, animate: Boolean, content: @Composable () -> Unit) {
    val visible = remember { MutableTransitionState(!animate).apply { targetState = true } }
    AnimatedVisibility(
        visibleState = visible,
        enter = fadeIn(tween(300, delayMillis = delayMs)) +
            slideInVertically(tween(300, delayMillis = delayMs)) { it / 3 },
    ) { content() }
}

/** Shakes [content] side to side once when it first appears. */
@Composable
internal fun ShakeOnEnter(animate: Boolean, content: @Composable () -> Unit) {
    val offset = remember { Animatable(0f) }
    LaunchedEffect(Unit) {
        if (!animate) return@LaunchedEffect
        offset.animateTo(
            0f,
            keyframes {
                durationMillis = 420
                -12f at 60
                12f at 130
                -8f at 200
                8f at 270
                -3f at 340
            },
        )
    }
    Box(Modifier.graphicsLayer { translationX = offset.value * density }) { content() }
}

internal const val WAVE_BARS = 48
internal const val WAVE_STEP_MS = 50
private const val RINGS = 3
private const val RING_PERIOD_MS = 1800
private const val BURST_DOTS = 12
