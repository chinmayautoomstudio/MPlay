package com.autoomstudio.mp3studio.playback

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class PlaybackQueueTest {

    private fun entry(id: Long) = QueueEntry(
        mediaId = id,
        title = "Song $id",
        artist = "Artist",
        artworkUri = null,
        durationMs = id * 1_000L,
    )

    @Test
    fun emptyCountReturnsEmptyQueue() {
        assertEquals(PlaybackQueue.Empty, playbackQueueOf(0, 3) { entry(it.toLong()) })
    }

    @Test
    fun buildsEntriesAndClampsCurrentIndex() {
        val queue = playbackQueueOf(count = 3, currentIndex = 99) { entry(it.toLong()) }
        assertEquals(listOf(0L, 1L, 2L), queue.entries.map { it.mediaId })
        assertEquals(2, queue.currentIndex)
    }

    @Test
    fun negativeCurrentIndexClampsToZero() {
        val queue = playbackQueueOf(count = 2, currentIndex = -1) { entry(it.toLong()) }
        assertEquals(0, queue.currentIndex)
    }

    @Test
    fun upNextStartsAtCurrentIndex() {
        val queue = PlaybackQueue(
            entries = listOf(entry(10), entry(20), entry(30), entry(40)),
            currentIndex = 1,
        )
        assertEquals(listOf(1, 2, 3), queue.upNext.map { it.index })
        assertEquals(listOf(20L, 30L, 40L), queue.upNext.map { it.value.mediaId })
    }

    @Test
    fun upNextWhenCurrentIsLastIsSingleItem() {
        val queue = PlaybackQueue(entries = listOf(entry(1), entry(2)), currentIndex = 1)
        assertEquals(listOf(2L), queue.upNext.map { it.value.mediaId })
    }

    @Test
    fun upNextOnEmptyIsEmpty() {
        assertTrue(PlaybackQueue.Empty.upNext.isEmpty())
    }

    @Test
    fun clampQueueMoveRejectsNoOps() {
        assertNull(clampQueueMove(0, 0, 3))
        assertNull(clampQueueMove(0, 1, 0))
        assertNull(clampQueueMove(-1, 1, 3))
        assertNull(clampQueueMove(0, 3, 3))
    }

    @Test
    fun clampQueueMoveKeepsValidPair() {
        assertEquals(1 to 2, clampQueueMove(1, 2, 4))
    }
}
