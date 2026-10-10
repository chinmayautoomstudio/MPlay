package com.autoomstudio.mp3studio.ui.playback

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.util.VelocityTracker
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.autoomstudio.mp3studio.R
import kotlinx.coroutines.launch

/** Share of the swiped element's width a drag must pass to change the song. */
private const val COMMIT_FRACTION = 0.3f

/** How far a drag moves, relative to the finger, toward a song that doesn't exist. */
private const val RESISTANCE = 0.3f

private val FlingVelocity = 1000.dp

internal enum class SwipeResult { Next, Previous, None }

/** Left (negative [offset]) is the next song, right is the previous one; a fast fling counts even when short. */
internal fun swipeDecision(
    offset: Float,
    velocity: Float,
    width: Float,
    flingVelocity: Float,
    canNext: Boolean,
    canPrevious: Boolean,
): SwipeResult {
    if (width <= 0f) return SwipeResult.None
    val threshold = width * COMMIT_FRACTION
    return when {
        canNext && offset < 0f && (offset < -threshold || velocity < -flingVelocity) -> SwipeResult.Next
        canPrevious && offset > 0f && (offset > threshold || velocity > flingVelocity) -> SwipeResult.Previous
        else -> SwipeResult.None
    }
}

/** Horizontal offset, in px, of whatever follows a swipe-to-skip drag. */
@Stable
class SwipeSkipState {
    val offset = Animatable(0f)
}

@Composable
fun rememberSwipeSkipState(): SwipeSkipState = remember { SwipeSkipState() }

/**
 * Swipe left for the next song and right for the previous one. The content follows the finger through [state],
 * slides out when the swipe counts and comes back in from the other side; otherwise it springs back.
 * Vertical drags and taps are left to the parent.
 */
@Composable
fun Modifier.swipeToSkip(
    state: SwipeSkipState,
    canNext: Boolean,
    canPrevious: Boolean,
    onNext: () -> Unit,
    onPrevious: () -> Unit,
): Modifier {
    val scope = rememberCoroutineScope()
    val haptics = LocalHapticFeedback.current
    val currentCanNext by rememberUpdatedState(canNext)
    val currentCanPrevious by rememberUpdatedState(canPrevious)
    val currentOnNext by rememberUpdatedState(onNext)
    val currentOnPrevious by rememberUpdatedState(onPrevious)
    val nextLabel = stringResource(R.string.action_next)
    val previousLabel = stringResource(R.string.action_previous)

    return this
        .semantics {
            customActions = buildList {
                if (canNext) add(CustomAccessibilityAction(nextLabel) { onNext(); true })
                if (canPrevious) add(CustomAccessibilityAction(previousLabel) { onPrevious(); true })
            }
        }
        .pointerInput(state) {
            val tracker = VelocityTracker()
            var raw = 0f
            fun shown(value: Float): Float {
                val allowed = (value < 0f && currentCanNext) || (value > 0f && currentCanPrevious)
                return if (allowed) value else value * RESISTANCE
            }
            detectHorizontalDragGestures(
                onDragStart = {
                    tracker.resetTracking()
                    raw = state.offset.value
                },
                onDragEnd = {
                    val width = size.width.toFloat()
                    val result = swipeDecision(
                        offset = state.offset.value,
                        velocity = tracker.calculateVelocity().x,
                        width = width,
                        flingVelocity = FlingVelocity.toPx(),
                        canNext = currentCanNext,
                        canPrevious = currentCanPrevious,
                    )
                    scope.launch {
                        if (result == SwipeResult.None) {
                            state.offset.animateTo(0f, spring(stiffness = Spring.StiffnessMediumLow))
                            return@launch
                        }
                        haptics.performHapticFeedback(HapticFeedbackType.SegmentTick)
                        val direction = if (result == SwipeResult.Next) -1f else 1f
                        state.offset.animateTo(direction * width, tween(150))
                        if (result == SwipeResult.Next) currentOnNext() else currentOnPrevious()
                        state.offset.snapTo(-direction * width * COMMIT_FRACTION)
                        state.offset.animateTo(0f, spring(dampingRatio = 0.8f, stiffness = Spring.StiffnessMediumLow))
                    }
                },
                onDragCancel = {
                    scope.launch { state.offset.animateTo(0f, spring(stiffness = Spring.StiffnessMediumLow)) }
                },
                onHorizontalDrag = { change, dragAmount ->
                    change.consume()
                    tracker.addPosition(change.uptimeMillis, change.position)
                    raw += dragAmount
                    val target = shown(raw)
                    scope.launch { state.offset.snapTo(target) }
                },
            )
        }
}
