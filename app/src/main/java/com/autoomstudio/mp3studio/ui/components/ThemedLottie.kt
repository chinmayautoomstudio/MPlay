package com.autoomstudio.mp3studio.ui.components

import android.provider.Settings
import androidx.annotation.RawRes
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import com.airbnb.lottie.LottieProperty
import com.airbnb.lottie.compose.LottieAnimation
import com.airbnb.lottie.compose.LottieCompositionSpec
import com.airbnb.lottie.compose.LottieConstants
import com.airbnb.lottie.compose.LottieDynamicProperties
import com.airbnb.lottie.compose.animateLottieCompositionAsState
import com.airbnb.lottie.compose.rememberLottieComposition
import com.airbnb.lottie.compose.rememberLottieDynamicProperties
import com.airbnb.lottie.compose.rememberLottieDynamicProperty

/** False when the user turned animations off in developer or accessibility settings. */
@Composable
fun rememberAnimationsEnabled(): Boolean {
    val context = LocalContext.current
    return remember(context) {
        Settings.Global.getFloat(context.contentResolver, Settings.Global.ANIMATOR_DURATION_SCALE, 1f) != 0f
    }
}

/**
 * Overrides every fill and stroke with [primary], then layers named `accent` with [accent], so the
 * JSON's baked-in colors never show and the animation follows light, dark and wallpaper colors.
 */
@Composable
fun rememberThemedLottieProperties(primary: Color, accent: Color): LottieDynamicProperties {
    val primaryArgb = primary.toArgb()
    val accentArgb = accent.toArgb()
    return rememberLottieDynamicProperties(
        rememberLottieDynamicProperty(LottieProperty.COLOR, primaryArgb, "**"),
        rememberLottieDynamicProperty(LottieProperty.STROKE_COLOR, primaryArgb, "**"),
        rememberLottieDynamicProperty(LottieProperty.COLOR, accentArgb, "accent", "**"),
        rememberLottieDynamicProperty(LottieProperty.STROKE_COLOR, accentArgb, "accent", "**"),
    )
}

/**
 * Looping Lottie animation recolored to the theme. Shows [placeholder] until the composition is
 * parsed, and holds [staticProgress] instead of looping when system animations are off.
 */
@Composable
fun ThemedLottie(
    @RawRes animation: Int,
    modifier: Modifier = Modifier,
    primary: Color = MaterialTheme.colorScheme.primary,
    accent: Color = MaterialTheme.colorScheme.secondary,
    isPlaying: Boolean = true,
    staticProgress: Float = 0.5f,
    placeholder: @Composable () -> Unit = {},
) {
    val composition by rememberLottieComposition(LottieCompositionSpec.RawRes(animation))
    val animationsEnabled = rememberAnimationsEnabled()
    val progress = animateLottieCompositionAsState(
        composition = composition,
        isPlaying = isPlaying && animationsEnabled,
        iterations = LottieConstants.IterateForever,
    )
    val properties = rememberThemedLottieProperties(primary, accent)

    Box(modifier = modifier, contentAlignment = Alignment.Center) {
        val loaded = composition
        if (loaded == null) {
            placeholder()
        } else {
            LottieAnimation(
                composition = loaded,
                progress = { if (animationsEnabled) progress.value else staticProgress },
                dynamicProperties = properties,
                modifier = Modifier.fillMaxSize(),
            )
        }
    }
}
