package com.autoomstudio.mplay.metronome

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.abs

class BeatClockTest {

    private fun beatFrames(clock: BeatClock, totalFrames: Long, block: Int = 256): List<Long> {
        val frames = mutableListOf<Long>()
        var start = 0L
        while (start < totalFrames) {
            clock.beatsIn(start, block) { offset, _ -> frames += start + offset }
            start += block
        }
        return frames
    }

    @Test
    fun firstBeatIsAtTheStart() {
        assertEquals(0L, beatFrames(BeatClock(48_000, 120.0), 256).first())
    }

    @Test
    fun beatsAreEvenlySpaced() {
        val frames = beatFrames(BeatClock(48_000, 120.0), 48_000L * 10)
        assertEquals(20, frames.size)
        frames.zipWithNext().forEach { (a, b) -> assertEquals(24_000L, b - a) }
    }

    @Test
    fun noDriftOverTenMinutesAtAnAwkwardTempo() {
        // 47.123 BPM at 44.1 kHz gives a fractional frames-per-beat; each beat must stay within a frame of ideal.
        val rate = 44_100
        val bpm = 47.123
        val frames = beatFrames(BeatClock(rate, bpm), rate * 600L, block = 192)
        val ideal = rate * 60.0 / bpm
        frames.forEachIndexed { index, frame -> assertTrue(abs(frame - index * ideal) <= 1.0) }
        val lastErrorMs = abs(frames.last() - (frames.size - 1) * ideal) / rate * 1000
        assertTrue("drift $lastErrorMs ms", lastErrorMs < 10.0)
    }

    @Test
    fun tempoChangeAppliesFromTheNextBeat() {
        val clock = BeatClock(1_000, 60.0)
        val frames = mutableListOf<Long>()
        clock.beatsIn(0, 1_500) { offset, _ -> frames += offset.toLong() }
        clock.setBpm(120.0)
        var start = 1_500L
        while (start < 5_000) {
            clock.beatsIn(start, 100) { offset, _ -> frames += start + offset }
            start += 100
        }
        // Beats at 0 and 1000 at 60 BPM; the beat already due at 2000 keeps its place, then 500 apart.
        assertEquals(listOf(0L, 1_000L, 2_000L, 2_500L, 3_000L, 3_500L, 4_000L, 4_500L), frames)
    }

    @Test
    fun beatIndexesCountUp() {
        val clock = BeatClock(1_000, 600.0)
        val indexes = mutableListOf<Long>()
        clock.beatsIn(0, 1_000) { _, beat -> indexes += beat }
        assertEquals((0L until 10L).toList(), indexes)
    }
}
