package com.autoomstudio.mp3studio.ui.playback

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import com.airbnb.lottie.compose.LottieAnimation
import com.airbnb.lottie.compose.LottieCompositionSpec
import com.airbnb.lottie.compose.rememberLottieComposition
import com.autoomstudio.mp3studio.R
import com.autoomstudio.mp3studio.ui.components.rememberThemedLottieProperties
import com.autoomstudio.mp3studio.ui.theme.EmphasizedEasing
import com.autoomstudio.mp3studio.ui.theme.MotionMedium

/**
 * Play/pause glyph that morphs between the two shapes whenever [isPlaying] flips, including
 * changes that come from the notification or widget.
 */
@Composable
fun PlayPauseIcon(isPlaying: Boolean, tint: Color, modifier: Modifier = Modifier) {
    val description = stringResource(if (isPlaying) R.string.action_pause else R.string.action_play)
    val composition by rememberLottieComposition(LottieCompositionSpec.RawRes(R.raw.anim_play_pause))
    val progress = animateFloatAsState(
        targetValue = if (isPlaying) 1f else 0f,
        animationSpec = tween(MotionMedium, easing = EmphasizedEasing),
        label = "playPauseMorph",
    )
    val properties = rememberThemedLottieProperties(tint, tint)
    val loaded = composition
    if (loaded == null) {
        Icon(
            imageVector = if (isPlaying) Icons.Filled.Pause else Icons.Filled.PlayArrow,
            contentDescription = description,
            tint = tint,
            modifier = modifier,
        )
    } else {
        LottieAnimation(
            composition = loaded,
            progress = { progress.value },
            dynamicProperties = properties,
            modifier = modifier.semantics { contentDescription = description },
        )
    }
}
