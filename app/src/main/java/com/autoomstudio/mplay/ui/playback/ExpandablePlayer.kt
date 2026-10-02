package com.autoomstudio.mplay.ui.playback

import androidx.compose.foundation.gestures.TargetedFlingBehavior
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.geometry.lerp
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.layout
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.lerp
import androidx.compose.ui.util.lerp
import com.autoomstudio.mplay.R
import com.autoomstudio.mplay.data.model.Song
import com.autoomstudio.mplay.playback.NowPlayingState
import com.autoomstudio.mplay.ui.components.ArtworkImage
import com.autoomstudio.mplay.ui.library.SongActions
import kotlin.math.roundToInt
import kotlinx.coroutines.flow.Flow

private val CardHorizontalMargin = 8.dp
private val CardVerticalMargin = 6.dp
private val CardCorner = 14.dp
private val MiniArtCorner = 8.dp
private val FullArtCorner = 28.dp
private val FullArtShadow = 32.dp
private val FullContentSlide = 24.dp

/** Height the collapsed card occupies in the bottom bar, including its margins. */
val MiniPlayerSlotHeight = MiniPlayerHeight + CardVerticalMargin * 2

/**
 * A single card that is the mini player when collapsed and the full player when expanded.
 * Its bounds, colors and the shared artwork are interpolated by [PlayerSheetState.expandProgress].
 */
@Composable
fun ExpandablePlayer(
    state: NowPlayingState,
    position: Flow<Long>,
    actions: PlayerActions,
    sheetState: PlayerSheetState,
    sheetFling: TargetedFlingBehavior,
    collapsedTop: () -> Float,
    onExpand: () -> Unit,
    onCollapse: () -> Unit,
    song: Song?,
    songActions: SongActions,
    modifier: Modifier = Modifier,
) {
    BoxWithConstraints(modifier = modifier.fillMaxSize()) {
        val fullWidth = constraints.maxWidth
        val fullHeight = constraints.maxHeight
        val density = LocalDensity.current
        val marginH = with(density) { CardHorizontalMargin.toPx() }
        val marginV = with(density) { CardVerticalMargin.toPx() }
        val miniHeight = with(density) { MiniPlayerHeight.toPx() }
        val slidePx = with(density) { FullContentSlide.toPx() }
        val miniArtRect = with(density) {
            Rect(
                offset = Offset(MiniArtworkStart.toPx(), MiniArtworkTop.toPx()),
                size = Size(MiniArtworkSize.toPx(), MiniArtworkSize.toPx()),
            )
        }

        val progress = { sheetState.expandProgress }
        val cardLeft = { p: Float -> lerp(marginH, 0f, p) }
        val cardTop = { p: Float -> lerp(collapsedTop() + marginV, 0f, p) }
        val cardBottom = { p: Float -> lerp(collapsedTop() + marginV + miniHeight, fullHeight.toFloat(), p) }

        val collapsedColor = MaterialTheme.colorScheme.surfaceContainerHigh
        val expandedColor = MaterialTheme.colorScheme.background
        val primary = MaterialTheme.colorScheme.primary

        val showMini by remember { derivedStateOf { progress() < 0.3f } }
        val prominentArt by remember { derivedStateOf { progress() > 0.5f } }
        val backEnabled = sheetState.targetValue == PlayerSheetValue.Expanded

        var fullArtRect by remember { mutableStateOf(Rect.Zero) }
        val positions = remember { ArtworkPositions() }
        val updateFullArtRect = {
            val root = positions.fullRoot
            val art = positions.fullArt
            if (root != null && art != null && root.isAttached && art.isAttached) {
                fullArtRect = root.localBoundingBoxOf(art, clipBounds = false)
            }
        }

        Box(
            modifier = Modifier
                .layout { measurable, _ ->
                    val p = progress()
                    val left = cardLeft(p)
                    val top = cardTop(p)
                    val width = (fullWidth - 2 * left).roundToInt().coerceAtLeast(0)
                    val height = (cardBottom(p) - top).roundToInt().coerceAtLeast(0)
                    val placeable = measurable.measure(Constraints.fixed(width, height))
                    layout(fullWidth, fullHeight) {
                        placeable.place(left.roundToInt(), top.roundToInt())
                    }
                }
                .graphicsLayer {
                    shape = RoundedCornerShape(lerp(CardCorner, 0.dp, progress()))
                    clip = true
                }
                .drawBehind { drawRect(lerp(collapsedColor, expandedColor, progress())) }
                .playerSheetDrag(sheetState, sheetFling),
        ) {
            NowPlayingContent(
                state = state,
                position = position,
                actions = actions,
                onCollapse = onCollapse,
                showArtwork = false,
                backEnabled = backEnabled,
                song = song,
                songActions = songActions,
                onArtworkPositioned = {
                    positions.fullArt = it
                    updateFullArtRect()
                },
                modifier = Modifier
                    .layout { measurable, constraints ->
                        val placeable = measurable.measure(Constraints.fixed(fullWidth, fullHeight))
                        layout(constraints.maxWidth, constraints.maxHeight) {
                            placeable.place(-cardLeft(progress()).roundToInt(), 0)
                        }
                    }
                    .graphicsLayer {
                        val visible = ((progress() - 0.5f) / 0.5f).coerceIn(0f, 1f)
                        alpha = visible
                        translationY = (1f - visible) * slidePx
                    }
                    .onGloballyPositioned {
                        positions.fullRoot = it
                        updateFullArtRect()
                    },
            )

            if (showMini) {
                MiniPlayerContent(
                    state = state,
                    position = position,
                    actions = actions,
                    onExpand = onExpand,
                    showArtwork = false,
                    modifier = Modifier
                        .layout { measurable, constraints ->
                            val width = (fullWidth - 2 * marginH).roundToInt()
                            val placeable = measurable.measure(
                                Constraints.fixed(width, miniHeight.roundToInt()),
                            )
                            layout(constraints.maxWidth, constraints.maxHeight) { placeable.place(0, 0) }
                        }
                        .graphicsLayer { alpha = (1f - progress() / 0.25f).coerceIn(0f, 1f) },
                )
            }

            ArtworkImage(
                uri = state.artworkUri,
                contentDescription = stringResource(R.string.album_art_description, state.title),
                cornerRadius = 0.dp,
                prominent = prominentArt,
                requestSizePx = fullWidth,
                modifier = Modifier
                    .layout { measurable, constraints ->
                        val p = progress()
                        val rect = if (fullArtRect == Rect.Zero) {
                            miniArtRect
                        } else {
                            lerp(miniArtRect, fullArtRect.translate(-cardLeft(p), 0f), p)
                        }
                        val placeable = measurable.measure(
                            Constraints.fixed(
                                rect.width.roundToInt().coerceAtLeast(0),
                                rect.height.roundToInt().coerceAtLeast(0),
                            ),
                        )
                        layout(constraints.maxWidth, constraints.maxHeight) {
                            placeable.place(rect.left.roundToInt(), rect.top.roundToInt())
                        }
                    }
                    .graphicsLayer {
                        val p = progress()
                        shape = RoundedCornerShape(lerp(MiniArtCorner, FullArtCorner, p))
                        clip = true
                        shadowElevation = lerp(0.dp, FullArtShadow, p).toPx()
                        ambientShadowColor = primary
                        spotShadowColor = primary
                    },
            )
        }
    }
}

private class ArtworkPositions {
    var fullRoot: LayoutCoordinates? = null
    var fullArt: LayoutCoordinates? = null
}
