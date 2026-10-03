package com.autoomstudio.mplay.ui.playback

import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.foundation.gestures.AnchoredDraggableDefaults
import androidx.compose.foundation.gestures.AnchoredDraggableState
import androidx.compose.foundation.gestures.DraggableAnchors
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.TargetedFlingBehavior
import androidx.compose.foundation.gestures.anchoredDraggable
import androidx.compose.foundation.gestures.animateTo
import androidx.compose.runtime.Composable
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier

enum class PlayerSheetValue { Collapsed, Expanded }

typealias PlayerSheetState = AnchoredDraggableState<PlayerSheetValue>

private val SheetAnimationSpec = spring<Float>(
    dampingRatio = Spring.DampingRatioNoBouncy,
    stiffness = Spring.StiffnessMediumLow,
)

@Composable
fun rememberPlayerSheetState(initiallyExpanded: Boolean): PlayerSheetState =
    rememberSaveable(saver = AnchoredDraggableState.Saver()) {
        AnchoredDraggableState(
            if (initiallyExpanded) PlayerSheetValue.Expanded else PlayerSheetValue.Collapsed,
        )
    }

@Composable
fun rememberPlayerSheetFlingBehavior(state: PlayerSheetState): TargetedFlingBehavior =
    AnchoredDraggableDefaults.flingBehavior(
        state = state,
        positionalThreshold = { distance -> distance * 0.3f },
        animationSpec = SheetAnimationSpec,
    )

/** Expanded sits at the top of the container; Collapsed sits at the mini player's top edge. */
fun PlayerSheetState.updateSheetAnchors(collapsedTopPx: Float) {
    updateAnchors(
        DraggableAnchors {
            PlayerSheetValue.Expanded at 0f
            PlayerSheetValue.Collapsed at collapsedTopPx
        },
    )
}

/** 0 when collapsed, 1 when expanded; 0 until the sheet has been measured. */
val PlayerSheetState.expandProgress: Float
    get() {
        // AnchoredDraggableState.progress() reports 1 while anchors are missing, so compute it directly.
        val collapsed = anchors.positionOf(PlayerSheetValue.Collapsed)
        val expanded = anchors.positionOf(PlayerSheetValue.Expanded)
        if (collapsed.isNaN() || expanded.isNaN() || collapsed == expanded || offset.isNaN()) return 0f
        return ((collapsed - offset) / (collapsed - expanded)).coerceIn(0f, 1f)
    }

suspend fun PlayerSheetState.animateSheetTo(target: PlayerSheetValue) {
    animateTo(target, SheetAnimationSpec)
}

fun Modifier.playerSheetDrag(
    state: PlayerSheetState,
    flingBehavior: TargetedFlingBehavior,
): Modifier = anchoredDraggable(
    state = state,
    orientation = Orientation.Vertical,
    flingBehavior = flingBehavior,
)
