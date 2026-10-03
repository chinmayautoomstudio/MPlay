package com.autoomstudio.mplay.ui.library

import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.Saver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue

/** Songs picked by long-press in the visible list. Empty means selection mode is off. */
@Stable
class SongSelection(initial: Set<Long> = emptySet()) {
    var selectedIds by mutableStateOf(initial)
        private set

    /** The playlist on screen, which adds "Remove from playlist" to the selection bar. */
    var playlistId by mutableStateOf<Long?>(null)

    val isActive: Boolean get() = selectedIds.isNotEmpty()

    fun isSelected(id: Long): Boolean = id in selectedIds

    fun toggle(id: Long) {
        selectedIds = SelectionQueries.toggle(selectedIds, id)
    }

    fun clear() {
        if (selectedIds.isNotEmpty()) selectedIds = emptySet()
    }

    /** Drops songs that are no longer in [available], for example after they were deleted. */
    fun retainOnly(available: Set<Long>) {
        val kept = SelectionQueries.retain(selectedIds, available)
        if (kept != selectedIds) selectedIds = kept
    }

    companion object {
        val Saver: Saver<SongSelection, LongArray> = Saver(
            save = { it.selectedIds.toLongArray() },
            restore = { SongSelection(it.toSet()) },
        )
    }
}

@Composable
fun rememberSongSelection(): SongSelection = rememberSaveable(saver = SongSelection.Saver) { SongSelection() }

object SelectionQueries {
    fun toggle(selected: Set<Long>, id: Long): Set<Long> = if (id in selected) selected - id else selected + id

    fun retain(selected: Set<Long>, available: Set<Long>): Set<Long> = selected.filterTo(LinkedHashSet()) { it in available }
}
