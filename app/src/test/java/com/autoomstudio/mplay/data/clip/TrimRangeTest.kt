package com.autoomstudio.mplay.data.clip

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class TrimRangeTest {

    private val full = TrimRange.full(60_000)

    @Test
    fun moveStart_clampsToZeroAndKeepsMinimumLength() {
        assertEquals(0L, full.moveStart(-500).startMs)
        assertEquals(59_000L, full.moveStart(59_800).startMs)
    }

    @Test
    fun moveEnd_clampsToDurationAndKeepsMinimumLength() {
        val range = full.moveStart(10_000)
        assertEquals(60_000L, range.moveEnd(90_000).endMs)
        assertEquals(11_000L, range.moveEnd(10_200).endMs)
    }

    @Test
    fun nudges_moveByOneTenthOfASecond() {
        val range = full.moveStart(5_000).moveEnd(20_000)
        assertEquals(5_100L, range.nudgeStart(forward = true).startMs)
        assertEquals(4_900L, range.nudgeStart(forward = false).startMs)
        assertEquals(20_100L, range.nudgeEnd(forward = true).endMs)
        assertEquals(19_900L, range.nudgeEnd(forward = false).endMs)
    }

    @Test
    fun nudges_stopAtTheMinimumLength() {
        val range = full.moveStart(10_000).moveEnd(11_000)
        assertEquals(10_000L, range.nudgeStart(forward = true).startMs)
        assertEquals(11_000L, range.nudgeEnd(forward = false).endMs)
    }

    @Test
    fun ringtoneDefault_isFirstThirtySecondsOrWholeTrack() {
        assertEquals(TrimRange(0, 30_000, 60_000), TrimRange.ringtoneDefault(60_000))
        assertEquals(TrimRange(0, 12_000, 12_000), TrimRange.ringtoneDefault(12_000))
    }

    @Test
    fun longRingtoneWarning_startsAboveFortySeconds() {
        assertFalse(full.moveEnd(40_000).isLongForRingtone)
        assertTrue(full.moveEnd(40_100).isLongForRingtone)
    }

    @Test
    fun trackShorterThanMinimum_canOnlyBeUsedWhole() {
        val tiny = TrimRange.full(600)
        assertEquals(0L, tiny.moveStart(300).startMs)
        assertEquals(600L, tiny.moveEnd(100).endMs)
    }

    @Test
    fun fullTrack_coversEverything() {
        val range = full.moveStart(3_000).moveEnd(8_000).fullTrack()
        assertTrue(range.isFullTrack)
        assertEquals(60_000L, range.lengthMs)
    }
}
