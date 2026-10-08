package com.autoomstudio.mp3studio.ui.auth

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Spacer
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.rememberVectorPainter
import androidx.compose.ui.res.vectorResource
import androidx.compose.ui.unit.dp
import com.autoomstudio.mp3studio.R
import kotlin.math.PI
import kotlin.math.exp
import kotlin.math.pow
import kotlin.math.sin
import kotlin.random.Random

internal object SignInColors {
    val Navy = Color(0xFF0B0A2A)
    val Ink = Color(0xFF05040F)
    val Blue = Color(0xFF4F8BFF)
    val Indigo = Color(0xFF3B2DB8)
    val Violet = Color(0xFF8B5CF6)
    val Magenta = Color(0xFFE05CFF)
    val Pink = Color(0xFFFF6EC7)
    val Lavender = Color(0xFFB9B4D6)
    val Link = Color(0xFF8B7CFF)
    val ButtonFill = Color(0xFFF5F3FF)
    val ButtonText = Color(0xFF14122B)
}

private const val TwoPi = (2 * PI).toFloat()

/** One full loop of the hero; every motion in it repeats a whole number of times per loop, so it never jumps. */
private const val LoopMillis = 12_000

/** Frame shown when animations are off. */
private const val StaticTime = 0.18f

/** Midnight gradient with soft glows behind the logo and the hero. */
@Composable
internal fun SignInBackdrop(modifier: Modifier = Modifier, content: @Composable () -> Unit) {
    Box(
        modifier
            .background(Brush.verticalGradient(listOf(SignInColors.Navy, SignInColors.Ink)))
            .drawBehind {
                drawCircle(
                    brush = Brush.radialGradient(
                        listOf(SignInColors.Violet.copy(alpha = 0.22f), Color.Transparent),
                        center = Offset(size.width / 2, size.height * 0.2f),
                        radius = size.width * 0.75f,
                    ),
                    radius = size.width * 0.75f,
                    center = Offset(size.width / 2, size.height * 0.2f),
                )
                drawCircle(
                    brush = Brush.radialGradient(
                        listOf(SignInColors.Magenta.copy(alpha = 0.16f), Color.Transparent),
                        center = Offset(size.width * 0.75f, size.height * 0.58f),
                        radius = size.width * 0.8f,
                    ),
                    radius = size.width * 0.8f,
                    center = Offset(size.width * 0.75f, size.height * 0.58f),
                )
                drawCircle(
                    brush = Brush.radialGradient(
                        listOf(SignInColors.Blue.copy(alpha = 0.14f), Color.Transparent),
                        center = Offset(size.width * 0.15f, size.height * 0.62f),
                        radius = size.width * 0.7f,
                    ),
                    radius = size.width * 0.7f,
                    center = Offset(size.width * 0.15f, size.height * 0.62f),
                )
            },
    ) { content() }
}

@Composable
private fun rememberLoopTime(animated: Boolean): State<Float> {
    if (!animated) return remember { mutableFloatStateOf(StaticTime) }
    return rememberInfiniteTransition(label = "signInHero").animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(LoopMillis, easing = LinearEasing)),
        label = "heroTime",
    )
}

private class Bar(val x: Float, val speed1: Int, val speed2: Int, val offset1: Float, val offset2: Float)

private class WaveLayer(
    val base: Float,
    val amplitude: Float,
    val frequency: Float,
    val speed: Int,
    val offset: Float,
    val colors: List<Color>,
    val crest: Color,
)

private class Note(
    val x: Float,
    val y: Float,
    val sizeFraction: Float,
    val beamed: Boolean,
    val color: Color,
    val speed: Int,
    val offset: Float,
)

private class Sparkle(val x: Float, val y: Float, val radius: Float, val speed: Int, val offset: Float)

private const val BarCount = 34

private val Bars = Random(11).let { random ->
    List(BarCount) { i ->
        Bar(
            x = (i + 0.5f) / BarCount,
            speed1 = 1 + random.nextInt(3),
            speed2 = 2 + random.nextInt(4),
            offset1 = random.nextFloat() * TwoPi,
            offset2 = random.nextFloat() * TwoPi,
        )
    }
}

private val Waves = listOf(
    WaveLayer(
        base = 0.50f, amplitude = 0.075f, frequency = 1.2f, speed = 1, offset = 0f,
        colors = listOf(SignInColors.Blue.copy(alpha = 0.55f), SignInColors.Violet.copy(alpha = 0.6f), SignInColors.Magenta.copy(alpha = 0.55f)),
        crest = SignInColors.Blue,
    ),
    WaveLayer(
        base = 0.60f, amplitude = 0.085f, frequency = 0.9f, speed = -1, offset = 1.7f,
        colors = listOf(SignInColors.Violet.copy(alpha = 0.75f), SignInColors.Indigo.copy(alpha = 0.7f), SignInColors.Pink.copy(alpha = 0.75f)),
        crest = SignInColors.Magenta,
    ),
    WaveLayer(
        base = 0.72f, amplitude = 0.06f, frequency = 1.5f, speed = 2, offset = 3.1f,
        colors = listOf(SignInColors.Indigo.copy(alpha = 0.9f), SignInColors.Violet.copy(alpha = 0.8f), SignInColors.Indigo.copy(alpha = 0.9f)),
        crest = SignInColors.Violet,
    ),
    WaveLayer(
        base = 0.84f, amplitude = 0.05f, frequency = 1.1f, speed = -1, offset = 4.4f,
        colors = listOf(Color(0xFF1A1460), Color(0xFF2A1A7A), Color(0xFF1A1460)),
        crest = SignInColors.Lavender,
    ),
)

private val Notes = listOf(
    Note(x = 0.07f, y = 0.10f, sizeFraction = 0.075f, beamed = false, color = SignInColors.Pink, speed = 2, offset = 0f),
    Note(x = 0.24f, y = 0.18f, sizeFraction = 0.085f, beamed = true, color = SignInColors.Blue, speed = 1, offset = 0.35f),
    Note(x = 0.60f, y = 0.24f, sizeFraction = 0.055f, beamed = false, color = SignInColors.Violet, speed = 2, offset = 0.6f),
    Note(x = 0.86f, y = 0.06f, sizeFraction = 0.11f, beamed = true, color = SignInColors.Link, speed = 1, offset = 0.8f),
)

private val Sparkles = Random(5).let { random ->
    List(22) {
        Sparkle(
            x = random.nextFloat(),
            y = random.nextFloat() * 0.6f,
            radius = 0.6f + random.nextFloat() * 1.2f,
            speed = 1 + random.nextInt(3),
            offset = random.nextFloat() * TwoPi,
        )
    }
}

/** Animated waves, equalizer bars, floating notes and sparkles under the title. */
@Composable
internal fun SignInHero(animated: Boolean, modifier: Modifier = Modifier) {
    val time = rememberLoopTime(animated)
    val single = Icons.Filled.MusicNote
    val beamed = ImageVector.vectorResource(R.drawable.ic_music_note_beamed)
    // A VectorPainter caches one rendering; sharing it between notes of different sizes in a frame clips them.
    val notePainters = Notes.map { rememberVectorPainter(if (it.beamed) beamed else single) }
    Spacer(
        modifier.drawWithCache {
            val fill = Path()
            val crest = Path()
            val fillBrushes = Waves.map { Brush.horizontalGradient(it.colors, startX = 0f, endX = size.width) }
            val shade = Brush.verticalGradient(
                0f to Color.Transparent,
                1f to SignInColors.Ink.copy(alpha = 0.55f),
                startY = size.height * 0.45f,
                endY = size.height,
            )
            val fade = Brush.verticalGradient(
                0f to Color.Transparent,
                1f to lerp(SignInColors.Navy, SignInColors.Ink, 0.8f),
                startY = size.height * 0.82f,
                endY = size.height,
            )
            onDrawBehind {
                val t = time.value
                drawEqualizer(t)
                Waves.forEachIndexed { i, layer -> drawWave(t, layer, fillBrushes[i], shade, fill, crest) }
                drawSparkles(t)
                Notes.forEachIndexed { i, note -> drawNote(t, note, notePainters[i]) }
                drawRect(fade, topLeft = Offset(0f, size.height * 0.82f))
            }
        },
    )
}

private fun DrawScope.drawEqualizer(t: Float) {
    val slot = size.width / BarCount
    val barWidth = slot * 0.42f
    val bottom = size.height * 0.8f
    Bars.forEach { bar ->
        val envelope = 0.18f + 0.82f * exp(-((bar.x - 0.64f) / 0.27f).pow(2))
        val motion = 0.55f +
            0.3f * sin(TwoPi * bar.speed1 * t + bar.offset1) +
            0.15f * sin(TwoPi * bar.speed2 * t + bar.offset2)
        val height = size.height * 0.66f * envelope * motion.coerceIn(0.15f, 1f)
        val color = lerp(SignInColors.Blue, SignInColors.Magenta, bar.x)
        val x = bar.x * size.width
        drawLine(
            brush = Brush.verticalGradient(
                listOf(color.copy(alpha = 0.95f * envelope), color.copy(alpha = 0.15f * envelope)),
                startY = bottom - height,
                endY = bottom,
            ),
            start = Offset(x, bottom),
            end = Offset(x, bottom - height),
            strokeWidth = barWidth,
            cap = StrokeCap.Round,
        )
    }
}

private fun DrawScope.drawWave(t: Float, layer: WaveLayer, fillBrush: Brush, shade: Brush, fill: Path, crest: Path) {
    val w = size.width
    val h = size.height
    val phase = TwoPi * layer.speed * t + layer.offset
    val step = (w / 72f).coerceAtLeast(4f)
    fill.reset()
    crest.reset()
    fill.moveTo(0f, h)
    var x = 0f
    while (true) {
        val u = x / w
        val y = h * (
            layer.base +
                layer.amplitude * sin(TwoPi * layer.frequency * u + phase) +
                layer.amplitude * 0.35f * sin(TwoPi * layer.frequency * 2.3f * u - phase * 2f)
            )
        if (x == 0f) crest.moveTo(x, y) else crest.lineTo(x, y)
        fill.lineTo(x, y)
        if (x >= w) break
        x = (x + step).coerceAtMost(w)
    }
    fill.lineTo(w, h)
    fill.close()
    drawPath(fill, fillBrush)
    drawPath(fill, shade)
    drawPath(crest, layer.crest.copy(alpha = 0.18f), style = Stroke(width = 6.dp.toPx(), cap = StrokeCap.Round))
    drawPath(crest, layer.crest.copy(alpha = 0.85f), style = Stroke(width = 1.2.dp.toPx(), cap = StrokeCap.Round))
}

private fun DrawScope.drawSparkles(t: Float) {
    Sparkles.forEach { sparkle ->
        val twinkle = ((sin(TwoPi * sparkle.speed * t + sparkle.offset) + 1f) / 2f).pow(3)
        drawCircle(
            color = Color.White.copy(alpha = 0.15f + 0.75f * twinkle),
            radius = sparkle.radius.dp.toPx(),
            center = Offset(sparkle.x * size.width, sparkle.y * size.height),
        )
    }
}

private fun DrawScope.drawNote(t: Float, note: Note, painter: Painter) {
    val progress = (note.speed * t + note.offset) % 1f
    val alpha = sin(PI.toFloat() * progress).coerceIn(0f, 1f)
    val side = size.width * note.sizeFraction
    val x = note.x * size.width + sin(TwoPi * progress) * 6.dp.toPx() - side / 2
    val y = note.y * size.height + (0.5f - progress) * 36.dp.toPx()
    val center = Offset(x + side / 2, y + side / 2)
    drawCircle(
        brush = Brush.radialGradient(listOf(note.color.copy(alpha = 0.45f * alpha), Color.Transparent), center, side),
        radius = side,
        center = center,
    )
    translate(x, y) {
        with(painter) { draw(Size(side, side), alpha = alpha, colorFilter = ColorFilter.tint(note.color)) }
    }
}