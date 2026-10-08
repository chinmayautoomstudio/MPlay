package com.autoomstudio.mp3studio.ui.components

import androidx.annotation.RawRes
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.autoomstudio.mp3studio.ui.theme.EmphasizedDecelerateEasing
import com.autoomstudio.mp3studio.ui.theme.MotionLong
import kotlinx.coroutines.delay

private const val StaggerMs = 70L

@Composable
fun ComingSoon(
    icon: ImageVector,
    title: String,
    message: String,
    modifier: Modifier = Modifier,
    /** Raw Lottie animation shown in place of [icon]; the icon remains the loading placeholder. */
    @RawRes animation: Int? = null,
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(32.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp, Alignment.CenterVertically),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        val iconBadge: @Composable () -> Unit = {
            Box(
                modifier = Modifier
                    .size(88.dp)
                    .background(MaterialTheme.colorScheme.surfaceContainerHigh, CircleShape),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    modifier = Modifier.size(40.dp),
                    tint = MaterialTheme.colorScheme.primary,
                )
            }
        }
        EnterUp(order = 0) {
            if (animation == null) {
                iconBadge()
            } else {
                val glow = MaterialTheme.colorScheme.primary.copy(alpha = 0.12f)
                ThemedLottie(
                    animation = animation,
                    modifier = Modifier
                        .size(160.dp)
                        .background(Brush.radialGradient(listOf(glow, Color.Transparent)), CircleShape),
                    placeholder = iconBadge,
                )
            }
        }
        EnterUp(order = 1) {
            Text(
                text = title,
                style = MaterialTheme.typography.headlineSmall,
                color = MaterialTheme.colorScheme.onSurface,
                textAlign = TextAlign.Center,
            )
        }
        EnterUp(order = 2) {
            Text(
                text = message,
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
            )
        }
    }
}

/** Fades and slides [content] up on first composition, delayed by its [order] in the stagger. */
@Composable
private fun EnterUp(order: Int, content: @Composable () -> Unit) {
    val progress = remember { Animatable(0f) }
    val offsetPx = with(LocalDensity.current) { 16.dp.toPx() }
    LaunchedEffect(Unit) {
        delay(order * StaggerMs)
        progress.animateTo(1f, tween(MotionLong, easing = EmphasizedDecelerateEasing))
    }
    Box(
        modifier = Modifier.graphicsLayer {
            alpha = progress.value
            translationY = (1f - progress.value) * offsetPx
        },
    ) {
        content()
    }
}
