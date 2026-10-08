package com.autoomstudio.mp3studio.ui.components

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.interaction.InteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.graphics.graphicsLayer
import com.autoomstudio.mp3studio.ui.theme.EmphasizedDecelerateEasing
import com.autoomstudio.mp3studio.ui.theme.MotionShort

private val ReleaseSpring = spring<Float>(dampingRatio = 0.45f, stiffness = Spring.StiffnessMedium)

/** Shrinks the element while [interactionSource] is pressed and springs it back on release. */
fun Modifier.pressBounce(interactionSource: InteractionSource, pressedScale: Float = 0.88f): Modifier = composed {
    val pressed by interactionSource.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (pressed) pressedScale else 1f,
        animationSpec = if (pressed) tween(MotionShort / 2) else ReleaseSpring,
        label = "pressBounce",
    )
    graphicsLayer {
        scaleX = scale
        scaleY = scale
    }
}

/** Briefly pops the element larger whenever [key] changes while [enabled]; skips the first composition. */
fun Modifier.popOnChange(key: Any, enabled: Boolean = true): Modifier = composed {
    val scale = remember { Animatable(1f) }
    val isFirst = remember { booleanArrayOf(true) }
    LaunchedEffect(key) {
        if (isFirst[0]) {
            isFirst[0] = false
            return@LaunchedEffect
        }
        if (enabled) {
            scale.animateTo(1.25f, tween(MotionShort / 2, easing = EmphasizedDecelerateEasing))
            scale.animateTo(1f, ReleaseSpring)
        }
    }
    graphicsLayer {
        scaleX = scale.value
        scaleY = scale.value
    }
}
