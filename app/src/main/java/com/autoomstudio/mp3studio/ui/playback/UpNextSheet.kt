package com.autoomstudio.mp3studio.ui.playback

import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.AnchoredDraggableDefaults
import androidx.compose.foundation.gestures.AnchoredDraggableState
import androidx.compose.foundation.gestures.DraggableAnchors
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.TargetedFlingBehavior
import androidx.compose.foundation.gestures.anchoredDraggable
import androidx.compose.foundation.gestures.animateTo
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.DragHandle
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import com.autoomstudio.mp3studio.R
import com.autoomstudio.mp3studio.playback.PlaybackQueue
import com.autoomstudio.mp3studio.playback.QueueEntry
import com.autoomstudio.mp3studio.ui.common.formatDuration
import com.autoomstudio.mp3studio.ui.components.ArtworkImage
import com.autoomstudio.mp3studio.ui.components.NowPlayingBars
import kotlin.math.roundToInt
import kotlinx.coroutines.launch

enum class UpNextSheetValue { Peek, Expanded }

typealias UpNextSheetState = AnchoredDraggableState<UpNextSheetValue>

private val UpNextAnimationSpec = spring<Float>(
    dampingRatio = Spring.DampingRatioNoBouncy,
    stiffness = Spring.StiffnessMediumLow,
)

private val PeekHeight = 56.dp
private val ExpandedTopInset = 72.dp
private val SheetCorner = 20.dp

@Composable
fun rememberUpNextSheetState(): UpNextSheetState =
    rememberSaveable(saver = AnchoredDraggableState.Saver()) {
        AnchoredDraggableState(UpNextSheetValue.Peek)
    }

@Composable
fun rememberUpNextSheetFlingBehavior(state: UpNextSheetState): TargetedFlingBehavior =
    AnchoredDraggableDefaults.flingBehavior(
        state = state,
        positionalThreshold = { distance -> distance * 0.3f },
        animationSpec = UpNextAnimationSpec,
    )

/** 0 at Peek, 1 at Expanded; 0 until the sheet has been measured. */
val UpNextSheetState.expandProgress: Float
    get() {
        val peek = anchors.positionOf(UpNextSheetValue.Peek)
        val expanded = anchors.positionOf(UpNextSheetValue.Expanded)
        if (peek.isNaN() || expanded.isNaN() || peek == expanded || offset.isNaN()) return 0f
        return ((peek - offset) / (peek - expanded)).coerceIn(0f, 1f)
    }

/**
 * Nested bottom sheet over Now Playing: a peek bar with "Up Next", expandable to the queue list.
 */
@Composable
fun UpNextSheet(
    queue: PlaybackQueue,
    isPlaying: Boolean,
    onPlayIndex: (Int) -> Unit,
    onMove: (from: Int, to: Int) -> Unit,
    onRemove: (Int) -> Unit,
    modifier: Modifier = Modifier,
    sheetState: UpNextSheetState = rememberUpNextSheetState(),
) {
    val scope = rememberCoroutineScope()
    val fling = rememberUpNextSheetFlingBehavior(sheetState)
    val expanded = sheetState.targetValue == UpNextSheetValue.Expanded
    BackHandler(enabled = expanded) {
        scope.launch { sheetState.animateTo(UpNextSheetValue.Peek, UpNextAnimationSpec) }
    }

    BoxWithConstraints(modifier = modifier.fillMaxSize()) {
        val density = LocalDensity.current
        val peekPx = with(density) { PeekHeight.toPx() }
        val expandedTopPx = with(density) { ExpandedTopInset.toPx() }
        val heightPx = constraints.maxHeight.toFloat()

        LaunchedEffect(heightPx, peekPx, expandedTopPx) {
            if (heightPx <= 0f) return@LaunchedEffect
            sheetState.updateAnchors(
                DraggableAnchors {
                    UpNextSheetValue.Expanded at expandedTopPx
                    UpNextSheetValue.Peek at (heightPx - peekPx).coerceAtLeast(expandedTopPx)
                },
            )
        }

        val offsetY = sheetState.offset
        if (offsetY.isNaN()) return@BoxWithConstraints

        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .fillMaxHeight()
                .offset { IntOffset(0, offsetY.roundToInt()) }
                .anchoredDraggable(
                    state = sheetState,
                    orientation = Orientation.Vertical,
                    flingBehavior = fling,
                ),
            shape = RoundedCornerShape(topStart = SheetCorner, topEnd = SheetCorner),
            color = MaterialTheme.colorScheme.surfaceContainerHigh,
            tonalElevation = 2.dp,
            shadowElevation = 6.dp,
        ) {
            Column(modifier = Modifier.fillMaxSize()) {
                UpNextHeader(
                    expanded = expanded,
                    onToggle = {
                        scope.launch {
                            val target =
                                if (expanded) UpNextSheetValue.Peek else UpNextSheetValue.Expanded
                            sheetState.animateTo(target, UpNextAnimationSpec)
                        }
                    },
                )
                if (queue.entries.isEmpty()) {
                    Text(
                        text = stringResource(R.string.up_next_empty),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(horizontal = 24.dp, vertical = 8.dp),
                    )
                } else {
                    UpNextList(
                        queue = queue,
                        isPlaying = isPlaying,
                        onPlayIndex = onPlayIndex,
                        onMove = onMove,
                        onRemove = onRemove,
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxWidth(),
                    )
                }
            }
        }
    }
}

@Composable
private fun UpNextHeader(expanded: Boolean, onToggle: () -> Unit) {
    val description = stringResource(
        if (expanded) R.string.up_next_collapse else R.string.up_next_expand,
    )
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onToggle)
            .semantics { contentDescription = description }
            .padding(horizontal = 16.dp, vertical = 8.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(
            modifier = Modifier
                .size(width = 36.dp, height = 4.dp)
                .background(MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f), CircleShape),
        )
        Spacer(modifier = Modifier.height(10.dp))
        Text(
            text = stringResource(R.string.up_next_title),
            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold),
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

@Composable
private fun UpNextList(
    queue: PlaybackQueue,
    isPlaying: Boolean,
    onPlayIndex: (Int) -> Unit,
    onMove: (from: Int, to: Int) -> Unit,
    onRemove: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    val listState = rememberLazyListState()
    val reorderState = remember(listState) { QueueReorderState(listState) }
    val items = queue.upNext
    val currentItems by rememberUpdatedState(items)
    val currentOnMove by rememberUpdatedState(onMove)

    LazyColumn(
        state = listState,
        modifier = modifier.padding(bottom = 24.dp),
    ) {
        itemsIndexed(
            items = items,
            key = { _, indexed -> indexed.index },
        ) { relIndex, indexed ->
            val entry = indexed.value
            val absoluteIndex = indexed.index
            val isCurrent = absoluteIndex == queue.currentIndex
            val isDragging = reorderState.draggingAbsIndex == absoluteIndex
            val moveUp = stringResource(R.string.playlist_move_up)
            val moveDown = stringResource(R.string.playlist_move_down)
            val rowModifier = if (isDragging) {
                Modifier
                    .zIndex(1f)
                    .graphicsLayer {
                        translationY = reorderState.dragOffset
                        shadowElevation = 8.dp.toPx()
                    }
            } else {
                Modifier.animateItem()
            }
            UpNextRow(
                entry = entry,
                isCurrent = isCurrent,
                isPlaying = isCurrent && isPlaying,
                onClick = { onPlayIndex(absoluteIndex) },
                onRemove = { onRemove(absoluteIndex) },
                modifier = rowModifier
                    .fillMaxWidth()
                    .semantics {
                        customActions = buildList {
                            if (relIndex > 0) {
                                add(
                                    CustomAccessibilityAction(moveUp) {
                                        val from = currentItems[relIndex].index
                                        val to = currentItems[relIndex - 1].index
                                        currentOnMove(from, to)
                                        true
                                    },
                                )
                            }
                            if (relIndex < currentItems.lastIndex) {
                                add(
                                    CustomAccessibilityAction(moveDown) {
                                        val from = currentItems[relIndex].index
                                        val to = currentItems[relIndex + 1].index
                                        currentOnMove(from, to)
                                        true
                                    },
                                )
                            }
                        }
                    },
                trailingContent = {
                    Box(
                        modifier = Modifier
                            .size(48.dp)
                            .pointerInput(absoluteIndex) {
                                detectDragGestures(
                                    onDragStart = { reorderState.start(absoluteIndex) },
                                    onDragEnd = { reorderState.end() },
                                    onDragCancel = { reorderState.end() },
                                    onDrag = { change, amount ->
                                        change.consume()
                                        reorderState.drag(amount.y, currentItems) { from, to ->
                                            currentOnMove(from, to)
                                        }
                                    },
                                )
                            },
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(
                            imageVector = Icons.Filled.DragHandle,
                            contentDescription = stringResource(
                                R.string.up_next_reorder_handle,
                                entry.title,
                            ),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                },
            )
        }
    }
}

@Composable
private fun UpNextRow(
    entry: QueueEntry,
    isCurrent: Boolean,
    isPlaying: Boolean,
    onClick: () -> Unit,
    onRemove: () -> Unit,
    modifier: Modifier = Modifier,
    trailingContent: @Composable () -> Unit,
) {
    Row(
        modifier = modifier
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        ArtworkImage(
            uri = entry.artworkUri,
            contentDescription = stringResource(R.string.album_art_description, entry.title),
            modifier = Modifier.size(48.dp),
        )
        Spacer(modifier = Modifier.width(12.dp))
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            Text(
                text = entry.title,
                style = MaterialTheme.typography.bodyLarge.copy(
                    fontWeight = if (isCurrent) FontWeight.SemiBold else FontWeight.Normal,
                ),
                color = if (isCurrent) {
                    MaterialTheme.colorScheme.primary
                } else {
                    MaterialTheme.colorScheme.onSurface
                },
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = buildString {
                    append(entry.artist)
                    if (entry.durationMs > 0L) {
                        append(" • ")
                        append(formatDuration(entry.durationMs))
                    }
                },
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        if (isCurrent) {
            NowPlayingBars(
                animate = isPlaying,
                contentDescription = stringResource(
                    if (isPlaying) R.string.now_playing else R.string.paused,
                ),
            )
            Spacer(modifier = Modifier.width(4.dp))
        }
        IconButton(onClick = onRemove) {
            Icon(
                imageVector = Icons.Filled.Close,
                contentDescription = stringResource(R.string.up_next_remove, entry.title),
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        trailingContent()
    }
}

/** Tracks the Up Next row being dragged; [draggingAbsIndex] is the Media3 timeline index. */
@Stable
private class QueueReorderState(private val listState: LazyListState) {
    var draggingAbsIndex by mutableStateOf<Int?>(null)
        private set
    var dragOffset by mutableFloatStateOf(0f)
        private set

    fun start(absoluteIndex: Int) {
        draggingAbsIndex = absoluteIndex
        dragOffset = 0f
    }

    fun drag(
        deltaY: Float,
        items: List<IndexedValue<QueueEntry>>,
        onMove: (from: Int, to: Int) -> Unit,
    ) {
        val absFrom = draggingAbsIndex ?: return
        dragOffset += deltaY
        val visible = listState.layoutInfo.visibleItemsInfo
        val current = visible.firstOrNull { it.key == absFrom } ?: return
        val center = current.offset + dragOffset + current.size / 2f
        val target = visible.firstOrNull {
            it.key != absFrom && it.key is Int && center >= it.offset && center <= it.offset + it.size
        } ?: return
        val absTo = target.key as Int
        if (items.none { it.index == absTo } || absTo == absFrom) return
        dragOffset += current.offset - target.offset
        draggingAbsIndex = absTo
        onMove(absFrom, absTo)
    }

    fun end() {
        draggingAbsIndex = null
        dragOffset = 0f
    }
}
