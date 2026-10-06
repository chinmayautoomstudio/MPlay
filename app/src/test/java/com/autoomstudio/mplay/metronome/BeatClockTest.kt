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

    /** Runs [clock] in 100-frame blocks over [from, to) and returns (frame, beat) pairs. */
    private fun run(clock: BeatClock, from: Long, to: Long): List<Pair<Long, Long>> {
        val beats = mutableListOf<Pair<Long, Long>>()
        var start = from
        while (start < to) {
            clock.beatsIn(start, 100) { offset, beat -> beats += (start + offset) to beat }
            start += 100
        }
        return beats
    }

    @Test
    fun alignPutsBeatsOnTheGridWithSongNumbers() {
        val clock = BeatClock(1_000, 60.0)
        clock.align(anchorFrame = 250.0, framesPerBeat = 400.0, firstBeat = 0, fromFrame = 0)
        val beats = run(clock, 0, 2_000)
        assertEquals(listOf(250L to 0L, 650L to 1L, 1_050L to 2L, 1_450L to 3L, 1_850L to 4L), beats)
        assertTrue(clock.aligned)
    }

    @Test
    fun alignNumbersBeatsBeforeTheDownbeatNegatively() {
        val clock = BeatClock(1_000, 60.0)
        clock.align(anchorFrame = 2_000.0, framesPerBeat = 500.0, firstBeat = 0, fromFrame = 0)
        val beats = run(clock, 0, 2_600)
        assertEquals(listOf(0L to -4L, 500L to -3L, 1_000L to -2L, 1_500L to -1L, 2_000L to 0L, 2_500L to 1L), beats)
    }

    @Test
    fun realignSkipsABeatTooCloseToTheLastClick() {
        val clock = BeatClock(1_000, 60.0)
        run(clock, 0, 1_100) // clicks at 0 and 1000
        // The grid has a beat at 1100, only 10% of a beat after the last click; the next one is at 2100.
        clock.align(anchorFrame = 100.0, framesPerBeat = 1_000.0, firstBeat = 0, fromFrame = 1_100)
        assertEquals(listOf(2_100L to 2L, 3_100L to 3L), run(clock, 1_100, 3_200))
    }

    @Test
    fun realignKeepsABeatFarEnoughFromTheLastClick() {
        val clock = BeatClock(1_000, 60.0)
        run(clock, 0, 1_100) // clicks at 0 and 1000
        clock.align(anchorFrame = 1_500.0, framesPerBeat = 1_000.0, firstBeat = 0, fromFrame = 1_100)
        assertEquals(listOf(1_500L to 0L, 2_500L to 1L), run(clock, 1_100, 2_600))
    }

    @Test
    fun unalignCarriesOnAtTheTempoFromTheLastClick() {
        val clock = BeatClock(1_000, 60.0)
        clock.align(anchorFrame = 300.0, framesPerBeat = 400.0, firstBeat = 0, fromFrame = 0)
        run(clock, 0, 800) // grid clicks at 300 and 700
        clock.unalign(120.0)
        assertEquals(listOf(1_200L to 2L, 1_700L to 3L), run(clock, 800, 1_800))
        assertTrue(!clock.aligned)
    }

    @Test
    fun alignedPeriodReplacesTheTempo() {
        val clock = BeatClock(1_000, 60.0)
        clock.align(anchorFrame = 0.0, framesPerBeat = 250.0, firstBeat = 0, fromFrame = 0)
        val frames = run(clock, 0, 1_000).map { it.first }
        assertEquals(listOf(0L, 250L, 500L, 750L), frames)
    }
}
