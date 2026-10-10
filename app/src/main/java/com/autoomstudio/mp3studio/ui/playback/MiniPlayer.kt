package com.autoomstudio.mp3studio.ui.playback

import androidx.compose.animation.core.animateDpAsState
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.ProgressBarRangeInfo
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.progressBarRangeInfo
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.setProgress
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.autoomstudio.mp3studio.R
import com.autoomstudio.mp3studio.playback.NowPlayingState
import com.autoomstudio.mp3studio.ui.common.formatDuration
import com.autoomstudio.mp3studio.ui.components.ArtworkImage
import com.autoomstudio.mp3studio.ui.components.pressBounce
import com.autoomstudio.mp3studio.ui.metronome.MetronomeMiniIndicator
import kotlin.math.abs
import kotlinx.coroutines.flow.Flow

private val ProgressLineHeight = 4.dp
private val ScrubLineHeight = 6.dp
private val ScrubThumbRadius = 7.dp
private val ScrubStripHeight = 16.dp
private val RowHeight = 64.dp
private val RowVerticalPadding = 6.dp

val MiniPlayerHeight = ProgressLineHeight + RowHeight
val MiniArtworkSize = 44.dp
val MiniArtworkStart = 10.dp
val MiniArtworkTop = ProgressLineHeight + (RowHeight - MiniArtworkSize) / 2

/** Mini player row without its own card; the surrounding card draws the background. */
@Composable
fun MiniPlayerContent(
    state: NowPlayingState,
    position: Flow<Long>,
    actions: PlayerActions,
    onExpand: () -> Unit,
    modifier: Modifier = Modifier,
    showArtwork: Boolean = true,
    swipe: SwipeSkipState = rememberSwipeSkipState(),
) {
    val positionMs by position.collectAsStateWithLifecycle(initialValue = 0L)
    val openLabel = stringResource(R.string.action_open_player, state.title)
    // Follows the finger while scrubbing, so position polls don't pull the line back.
    var scrubFraction by remember { mutableStateOf<Float?>(null) }
    val durationMs = state.durationMs
    val playedFraction = if (durationMs > 0) (positionMs.toFloat() / durationMs).coerceIn(0f, 1f) else 0f
    val shownFraction = scrubFraction ?: playedFraction

    Box(modifier = modifier.fillMaxWidth()) {
        Column(modifier = Modifier.fillMaxWidth()) {
            Spacer(Modifier.height(ProgressLineHeight))
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(RowHeight)
                    .swipeToSkip(
                        state = swipe,
                        canNext = state.hasNext,
                        canPrevious = state.hasPrevious,
                        onNext = actions::next,
                        onPrevious = actions::previousTrack,
                    )
                    .clickable(onClickLabel = openLabel, onClick = onExpand)
                    .padding(
                        start = MiniArtworkStart,
                        end = 4.dp,
                        top = RowVerticalPadding,
                        bottom = RowVerticalPadding,
                    ),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                if (showArtwork) {
                    ArtworkImage(
                        uri = state.artworkUri,
                        contentDescription = null,
                        modifier = Modifier
                            .size(MiniArtworkSize)
                            .graphicsLayer { translationX = swipe.offset.value },
                        cornerRadius = 8.dp,
                    )
                } else {
                    Spacer(Modifier.size(MiniArtworkSize))
                }
                Spacer(Modifier.width(12.dp))
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .graphicsLayer {
                            translationX = swipe.offset.value
                            if (size.width > 0f) {
                                alpha = 1f - (abs(swipe.offset.value) / size.width).coerceIn(0f, 0.7f)
                            }
                        },
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        MetronomeMiniIndicator(Modifier.padding(end = 6.dp))
                        Text(
                            text = state.title,
                            style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.SemiBold),
                            color = MaterialTheme.colorScheme.onSurface,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                    val scrubbing = scrubFraction
                    Text(
                        text = if (scrubbing != null) {
                            stringResource(
                                R.string.scrub_position,
                                formatDuration((scrubbing * durationMs).toLong()),
                                formatDuration(durationMs),
                            )
                        } else {
                            state.artist
                        },
                        style = MaterialTheme.typography.bodyMedium,
                        color = if (scrubbing != null) {
                            MaterialTheme.colorScheme.primary
                        } else {
                            MaterialTheme.colorScheme.onSurfaceVariant
                        },
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                val playSource = remember { MutableInteractionSource() }
                val nextSource = remember { MutableInteractionSource() }
                IconButton(
                    onClick = actions::playPause,
                    interactionSource = playSource,
                    modifier = Modifier.pressBounce(playSource),
                ) {
                    PlayPauseIcon(
                        isPlaying = state.isPlaying,
                        tint = MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.size(32.dp),
                    )
                }
                IconButton(
                    onClick = actions::next,
                    enabled = state.hasNext,
                    interactionSource = nextSource,
                    modifier = Modifier.pressBounce(nextSource),
                ) {
                    Icon(
                        imageVector = Icons.Filled.SkipNext,
                        contentDescription = stringResource(R.string.action_next),
                        tint = MaterialTheme.colorScheme.onSurface.copy(
                            alpha = if (state.hasNext) 1f else 0.38f,
                        ),
                        modifier = Modifier.size(30.dp),
                    )
                }
            }
        }

        ScrubStrip(
            fraction = shownFraction,
            scrubbing = scrubFraction != null,
            durationMs = durationMs,
            onScrub = { scrubFraction = it },
            onScrubEnd = { commit ->
                val fraction = scrubFraction
                scrubFraction = null
                if (commit && fraction != null) actions.seekTo((fraction * durationMs).toLong())
            },
            onSeek = { actions.seekTo((it * durationMs).toLong()) },
        )
    }
}

/**
 * Progress line with a taller transparent touch area over the top of the mini player.
 * Horizontal drags scrub and taps seek; vertical drags are left for the card's swipe-up.
 */
@Composable
private fun ScrubStrip(
    fraction: Float,
    scrubbing: Boolean,
    durationMs: Long,
    onScrub: (Float) -> Unit,
    onScrubEnd: (commit: Boolean) -> Unit,
    onSeek: (Float) -> Unit,
) {
    val lineHeight by animateDpAsState(if (scrubbing) ScrubLineHeight else ProgressLineHeight, label = "lineHeight")
    // The head must fit inside the strip, so the line drops to the head's centre while scrubbing.
    val lineCenter by animateDpAsState(
        if (scrubbing) ScrubThumbRadius else ProgressLineHeight / 2,
        label = "lineCenter",
    )
    val lineColor = MaterialTheme.colorScheme.primary
    val trackColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.12f)
    val seekDescription = stringResource(R.string.seek_bar_description)
    val enabled = durationMs > 0
    val currentOnScrub by rememberUpdatedState(onScrub)
    val currentOnScrubEnd by rememberUpdatedState(onScrubEnd)
    val currentOnSeek by rememberUpdatedState(onSeek)

    var gestureModifier: Modifier = Modifier
    if (enabled) {
        gestureModifier = Modifier
            .pointerInput(Unit) {
                detectTapGestures { offset -> currentOnSeek((offset.x / size.width).coerceIn(0f, 1f)) }
            }
            .pointerInput(Unit) {
                var dragFraction = 0f
                detectHorizontalDragGestures(
                    onDragStart = { offset ->
                        dragFraction = (offset.x / size.width).coerceIn(0f, 1f)
                        currentOnScrub(dragFraction)
                    },
                    onDragEnd = { currentOnScrubEnd(true) },
                    onDragCancel = { currentOnScrubEnd(false) },
                    onHorizontalDrag = { change, dragAmount ->
                        change.consume()
                        dragFraction = (dragFraction + dragAmount / size.width).coerceIn(0f, 1f)
                        currentOnScrub(dragFraction)
                    },
                )
            }
    }

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(ScrubStripHeight)
            .semantics {
                contentDescription = seekDescription
                progressBarRangeInfo = ProgressBarRangeInfo(fraction, 0f..1f)
                if (enabled) {
                    setProgress { target ->
                        currentOnSeek(target.coerceIn(0f, 1f))
                        true
                    }
                }
            }
            .then(gestureModifier)
            .drawBehind {
                val linePx = lineHeight.toPx()
                val centerY = lineCenter.toPx()
                val lineTop = Offset(0f, centerY - linePx / 2)
                if (scrubbing) drawRect(trackColor, topLeft = lineTop, size = Size(size.width, linePx))
                drawRect(lineColor, topLeft = lineTop, size = Size(size.width * fraction, linePx))
                if (scrubbing) {
                    val radius = ScrubThumbRadius.toPx()
                    drawCircle(
                        color = lineColor,
                        radius = radius,
                        center = Offset((size.width * fraction).coerceIn(radius, size.width - radius), centerY),
                    )
                }
            },
    )
}
