package com.autoomstudio.mp3studio.playback

import android.net.Uri

/** One row in the Media3 timeline, for the Up Next UI. */
data class QueueEntry(
    val mediaId: Long,
    val title: String,
    val artist: String,
    val artworkUri: Uri?,
    val durationMs: Long,
)

/** Snapshot of the full playback timeline and which index is current. */
data class PlaybackQueue(
    val entries: List<QueueEntry>,
    val currentIndex: Int,
) {
    /** Current song plus everything after it (what Up Next shows). */
    val upNext: List<IndexedValue<QueueEntry>>
        get() {
            if (entries.isEmpty()) return emptyList()
            val start = currentIndex.coerceIn(0, entries.lastIndex)
            return entries.withIndex().drop(start)
        }

    companion object {
        val Empty = PlaybackQueue(emptyList(), 0)
    }
}

/**
 * Builds a [PlaybackQueue] from a player timeline. [entryAt] is called for each index in `0 until count`.
 * Returns [PlaybackQueue.Empty] when [count] is 0.
 */
fun playbackQueueOf(
    count: Int,
    currentIndex: Int,
    entryAt: (Int) -> QueueEntry,
): PlaybackQueue {
    if (count <= 0) return PlaybackQueue.Empty
    val index = currentIndex.coerceIn(0, count - 1)
    return PlaybackQueue(
        entries = List(count, entryAt),
        currentIndex = index,
    )
}

/** Clamps a move so both ends stay inside the timeline. */
fun clampQueueMove(from: Int, to: Int, size: Int): Pair<Int, Int>? {
    if (size <= 0 || from !in 0 until size || to !in 0 until size || from == to) return null
    return from to to
}
