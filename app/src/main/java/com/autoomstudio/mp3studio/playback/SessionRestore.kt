package com.autoomstudio.mp3studio.playback

data class RestoredQueue<T>(
    val items: List<T>,
    val index: Int,
    val positionMs: Long,
)

/**
 * Rebuilds a saved queue from the current library. Songs that no longer exist are dropped.
 * If the current song itself is gone, playback resumes from the start of the next surviving song.
 */
fun <T> restoreQueue(saved: SavedSession, library: List<T>, idOf: (T) -> Long): RestoredQueue<T>? {
    val byId = library.associateBy(idOf)
    val savedIndex = saved.index.coerceIn(0, (saved.songIds.size - 1).coerceAtLeast(0))
    val items = mutableListOf<T>()
    var index = -1
    var currentSurvived = false
    saved.songIds.forEachIndexed { i, id ->
        val item = byId[id] ?: return@forEachIndexed
        if (i == savedIndex) {
            index = items.size
            currentSurvived = true
        } else if (i > savedIndex && index == -1) {
            index = items.size
        }
        items += item
    }
    if (items.isEmpty()) return null
    if (index == -1) index = items.lastIndex
    return RestoredQueue(
        items = items,
        index = index,
        positionMs = if (currentSurvived) saved.positionMs.coerceAtLeast(0L) else 0L,
    )
}
