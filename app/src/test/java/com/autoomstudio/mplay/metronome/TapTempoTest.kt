package com.autoomstudio.mplay.metronome

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class TapTempoTest {

    @Test
    fun needsFourTapsBeforeGivingATempo() {
        val tap = TapTempo()
        assertNull(tap.tap(0))
        assertNull(tap.tap(500))
        assertNull(tap.tap(1_000))
        assertEquals(120, tap.tap(1_500))
    }

    @Test
    fun averagesUnevenTaps() {
        val tap = TapTempo()
        listOf(0L, 480L, 1_010L, 1_500L).forEach { tap.tap(it) }
        // 1500 ms over 3 gaps = 500 ms per beat.
        assertEquals(120, tap.tap(2_000))
    }

    @Test
    fun keepsOnlyTheLastEightTaps() {
        val tap = TapTempo()
        // Four slow taps, then eight fast ones: the slow ones fall out of the window.
        var t = 0L
        repeat(4) { tap.tap(t); t += 1_000 }
        var bpm: Int? = null
        repeat(8) { bpm = tap.tap(t); t += 400 }
        assertEquals(150, bpm)
    }

    @Test
    fun aLongPauseStartsOver() {
        val tap = TapTempo()
        listOf(0L, 500L, 1_000L, 1_500L).forEach { tap.tap(it) }
        assertNull(tap.tap(5_000))
        assertEquals(1, tap.count)
    }

    @Test
    fun resultIsClampedToTheMetronomeRange() {
        val tap = TapTempo()
        listOf(0L, 100L, 200L).forEach { tap.tap(it) }
        assertEquals(300, tap.tap(300))
    }
}
