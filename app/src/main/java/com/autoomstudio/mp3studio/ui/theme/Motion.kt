package com.autoomstudio.mp3studio.ui.theme

import androidx.compose.animation.AnimatedContentTransitionScope
import androidx.compose.animation.ContentTransform
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith

const val MotionShort = 180
const val MotionMedium = 300
const val MotionLong = 450

val EmphasizedEasing = CubicBezierEasing(0.2f, 0f, 0f, 1f)
val EmphasizedDecelerateEasing = CubicBezierEasing(0.05f, 0.7f, 0.1f, 1f)
val EmphasizedAccelerateEasing = CubicBezierEasing(0.3f, 0f, 0.8f, 0.15f)

/** Material fade-through: old content fades out quickly, new content fades and scales in. */
fun fadeThrough(): ContentTransform =
    fadeIn(tween(MotionMedium - 90, delayMillis = 90, easing = EmphasizedDecelerateEasing)) +
        scaleIn(tween(MotionMedium - 90, delayMillis = 90, easing = EmphasizedDecelerateEasing), initialScale = 0.96f) togetherWith
        fadeOut(tween(90, easing = EmphasizedAccelerateEasing))

/** Horizontal shared-axis slide; [forward] moves new content in from the end. */
fun <S> AnimatedContentTransitionScope<S>.sharedAxisX(forward: Boolean): ContentTransform {
    val direction = if (forward) 1 else -1
    return (slideInHorizontally(tween(MotionMedium, easing = EmphasizedEasing)) { direction * it / 5 } +
        fadeIn(tween(MotionMedium - 90, delayMillis = 90, easing = EmphasizedDecelerateEasing))) togetherWith
        (slideOutHorizontally(tween(MotionMedium, easing = EmphasizedEasing)) { -direction * it / 5 } +
            fadeOut(tween(90, easing = EmphasizedAccelerateEasing)))
}
