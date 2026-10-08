package com.autoomstudio.mp3studio.playback

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class SessionRestoreTest {

    private val library = listOf(1L, 2L, 3L, 4L)

    private fun restore(ids: List<Long>, index: Int, positionMs: Long) =
        restoreQueue(SavedSession(ids, index, positionMs), library) { it }

    @Test
    fun allSongsPresentKeepsIndexAndPosition() {
        val restored = restore(listOf(1, 2, 3), index = 1, positionMs = 42_000)!!
        assertEquals(listOf(1L, 2L, 3L), restored.items)
        assertEquals(1, restored.index)
        assertEquals(42_000L, restored.positionMs)
    }

    @Test
    fun missingEarlierSongShiftsIndex() {
        val restored = restore(listOf(9, 2, 3), index = 2, positionMs = 5_000)!!
        assertEquals(listOf(2L, 3L), restored.items)
        assertEquals(1, restored.index)
        assertEquals(5_000L, restored.positionMs)
    }

    @Test
    fun missingCurrentSongMovesToNextFromStart() {
        val restored = restore(listOf(1, 9, 3), index = 1, positionMs = 5_000)!!
        assertEquals(listOf(1L, 3L), restored.items)
        assertEquals(1, restored.index)
        assertEquals(0L, restored.positionMs)
    }

    @Test
    fun missingLastCurrentSongFallsBackToLastSurvivor() {
        val restored = restore(listOf(1, 2, 9), index = 2, positionMs = 5_000)!!
        assertEquals(1, restored.index)
        assertEquals(0L, restored.positionMs)
    }

    @Test
    fun nothingLeftReturnsNull() {
        assertNull(restore(listOf(8, 9), index = 0, positionMs = 0))
    }

    @Test
    fun outOfRangeIndexIsClamped() {
        val restored = restore(listOf(1, 2), index = 7, positionMs = 1_000)!!
        assertEquals(1, restored.index)
        assertEquals(1_000L, restored.positionMs)
    }
}
