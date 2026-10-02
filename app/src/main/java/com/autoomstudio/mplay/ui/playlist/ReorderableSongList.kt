package com.autoomstudio.mplay.ui.playlist

import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DragHandle
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import com.autoomstudio.mplay.R
import com.autoomstudio.mplay.data.model.Song
import com.autoomstudio.mplay.ui.library.SongActions
import com.autoomstudio.mplay.ui.library.SongRow

/** Tracks the row being dragged; rows are keyed by song ID in the surrounding lazy list. */
@Stable
class SongReorderState(private val listState: LazyListState) {
    var draggingId by mutableStateOf<Long?>(null)
        private set
    var dragOffset by mutableFloatStateOf(0f)
        private set

    fun start(songId: Long) {
        draggingId = songId
        dragOffset = 0f
    }

    /** Moves the dragged row past a neighbour once its center crosses into that neighbour. */
    fun drag(deltaY: Float, songs: List<Song>, onMove: (from: Int, to: Int) -> Unit) {
        val id = draggingId ?: return
        dragOffset += deltaY
        val visible = listState.layoutInfo.visibleItemsInfo
        val current = visible.firstOrNull { it.key == id } ?: return
        val center = current.offset + dragOffset + current.size / 2f
        val target = visible.firstOrNull {
            it.key != id && it.key is Long && center >= it.offset && center <= it.offset + it.size
        } ?: return
        val from = songs.indexOfFirst { it.id == id }
        val to = songs.indexOfFirst { it.id == target.key }
        if (from < 0 || to < 0) return
        dragOffset += current.offset - target.offset
        onMove(from, to)
    }

    fun end() {
        draggingId = null
        dragOffset = 0f
    }
}

@Composable
fun rememberSongReorderState(listState: LazyListState): SongReorderState =
    remember(listState) { SongReorderState(listState) }

/**
 * Song rows with a drag handle. [onMove] updates the on-screen order while dragging;
 * [onDragEnd] fires once on drop, when the order should be saved.
 */
fun LazyListScope.reorderableSongItems(
    songs: List<Song>,
    state: SongReorderState,
    currentSongId: Long?,
    isPlaying: Boolean,
    onSongClick: (Song) -> Unit,
    actions: SongActions,
    onRemove: (Song) -> Unit,
    onMove: (from: Int, to: Int) -> Unit,
    onDragEnd: () -> Unit,
) {
    itemsIndexed(items = songs, key = { _, song -> song.id }) { index, song ->
        val isCurrent = song.id == currentSongId
        val isDragging = state.draggingId == song.id
        val moveUp = stringResource(R.string.playlist_move_up)
        val moveDown = stringResource(R.string.playlist_move_down)
        val currentSongs by rememberUpdatedState(songs)
        val currentOnMove by rememberUpdatedState(onMove)
        val currentOnDragEnd by rememberUpdatedState(onDragEnd)

        val rowModifier = if (isDragging) {
            Modifier
                .zIndex(1f)
                .graphicsLayer {
                    translationY = state.dragOffset
                    shadowElevation = 8.dp.toPx()
                }
        } else {
            Modifier.animateItem()
        }
        SongRow(
            song = song,
            isCurrent = isCurrent,
            isPlaying = isCurrent && isPlaying,
            onClick = { onSongClick(song) },
            actions = actions,
            onRemove = { onRemove(song) },
            modifier = rowModifier.semantics {
                customActions = buildList {
                    if (index > 0) {
                        add(CustomAccessibilityAction(moveUp) {
                            currentOnMove(index, index - 1)
                            currentOnDragEnd()
                            true
                        })
                    }
                    if (index < songs.lastIndex) {
                        add(CustomAccessibilityAction(moveDown) {
                            currentOnMove(index, index + 1)
                            currentOnDragEnd()
                            true
                        })
                    }
                }
            },
            trailingContent = {
                Box(
                    modifier = Modifier
                        .size(48.dp)
                        .pointerInput(song.id) {
                            detectDragGestures(
                                onDragStart = { state.start(song.id) },
                                onDragEnd = {
                                    state.end()
                                    currentOnDragEnd()
                                },
                                onDragCancel = {
                                    state.end()
                                    currentOnDragEnd()
                                },
                                onDrag = { change, amount ->
                                    change.consume()
                                    state.drag(amount.y, currentSongs, currentOnMove)
                                },
                            )
                        },
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        imageVector = Icons.Filled.DragHandle,
                        contentDescription = stringResource(R.string.playlist_reorder_handle, song.title),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            },
        )
    }
}
