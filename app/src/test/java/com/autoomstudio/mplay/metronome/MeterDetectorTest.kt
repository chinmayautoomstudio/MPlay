package com.autoomstudio.mplay.metronome

import com.autoomstudio.mplay.metronome.SyntheticAudio.Drum.Hat
import com.autoomstudio.mplay.metronome.SyntheticAudio.Drum.Kick
import com.autoomstudio.mplay.metronome.SyntheticAudio.Drum.Snare
import com.autoomstudio.mplay.metronome.SyntheticAudio.Hit
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.random.Random

class MeterDetectorTest {

    private val rate = SyntheticAudio.RATE
    private val detector = TempoDetector(rate)

    /** Kick on 1 and (quieter) 3, snare on 2 and 4, eighth-note hats. */
    private val rock = listOf(
        Hit(0.0, Kick, 1f), Hit(1.0, Snare, 0.8f), Hit(2.0, Kick, 0.6f), Hit(3.0, Snare, 0.8f),
    ) + (0..3).map { Hit(it + 0.5, Hat, 0.25f) }

    /** Oom-pah-pah. */
    private val waltz = listOf(Hit(0.0, Kick, 1f), Hit(1.0, Snare, 0.6f), Hit(2.0, Snare, 0.6f))

    /** Kick, snare, with eighth-note hats. */
    private val march = listOf(Hit(0.0, Kick, 1f), Hit(1.0, Snare, 0.8f), Hit(0.5, Hat, 0.25f), Hit(1.5, Hat, 0.25f))

    /** Six eighths: kick on pulse 1, snare on pulse 4, hats in between. */
    private val sixEight = listOf(Hit(0.0, Kick, 1f), Hit(3.0, Snare, 0.8f)) +
        listOf(1, 2, 4, 5).map { Hit(it.toDouble(), Hat, 0.35f) }

    private data class Case(val name: String, val bpm: Double, val beats: Int, val hits: List<Hit>, val expected: TimeSignature)

    private val cleanCases = listOf(
        Case("4/4 @90", 90.0, 4, rock, TimeSignature(4, 4)),
        Case("4/4 @120", 120.0, 4, rock, TimeSignature(4, 4)),
        Case("4/4 @140", 140.0, 4, rock, TimeSignature(4, 4)),
        Case("3/4 @90", 90.0, 3, waltz, TimeSignature(3, 4)),
        Case("3/4 @120", 120.0, 3, waltz, TimeSignature(3, 4)),
        Case("2/4 @100", 100.0, 2, march, TimeSignature(2, 4)),
        // Eighths at 180, so the felt dotted-quarter pulse is 60.
        Case("6/8 @180", 180.0, 6, sixEight, TimeSignature(6, 8)),
    )

    /** Each case clean, and with two levels of background noise. */
    private val variants = listOf(0f to 1, 0.05f to 1, 0.05f to 2, 0.1f to 3)

    @Test
    fun detectsCleanSyntheticMetersAtLeast90PercentOfTheTime() {
        val rows = mutableListOf<String>()
        var correct = 0
        var total = 0
        for (case in cleanCases) {
            for ((noise, seed) in variants) {
                val samples = SyntheticAudio.pattern(case.bpm, case.beats, case.hits, noise = noise, seed = seed)
                val rhythm = detector.detectRhythm(samples)
                val meter = rhythm?.meter
                val ok = meter?.timeSignature == case.expected
                if (ok) correct++
                total++
                rows += "%-9s noise=%.2f -> %s BPM, %s %s score=%.1f click=%s %s".format(
                    case.name, noise, rhythm?.tempo?.bpm, meter?.timeSignature, meter?.confidence,
                    meter?.score ?: 0f, meter?.clickBpm, if (ok) "" else "WRONG",
                )
            }
        }
        println(rows.joinToString("\n"))
        val accuracy = correct.toDouble() / total
        assertTrue("accuracy $correct/$total\n${rows.joinToString("\n")}", accuracy >= 0.9)
    }

    @Test
    fun sixEightClicksTheEighths() {
        val meter = detector.detectRhythm(SyntheticAudio.pattern(180.0, 6, sixEight))!!.meter!!
        assertEquals(TimeSignature(6, 8), meter.timeSignature)
        assertEquals(180.0, meter.clickBpm.toDouble(), 2.0)
    }

    @Test
    fun clearPatternsAreConfidentEnoughToApply() {
        val meter = detector.detectRhythm(SyntheticAudio.pattern(120.0, 3, waltz))!!.meter!!
        assertTrue(meter.confidence != TempoConfidence.Low)
    }

    @Test
    fun unaccentedClicksFallBackToFourFourLow() {
        listOf(100.0, 120.0).forEach { bpm ->
            val meter = detector.detectRhythm(SyntheticAudio.clicks(bpm, seconds = 45))!!.meter
            assertNotNull(meter)
            assertEquals(TimeSignature(4, 4), meter!!.timeSignature)
            assertEquals(TempoConfidence.Low, meter.confidence)
        }
    }

    @Test
    fun noiseIsNullOrLow() {
        listOf(7, 11).forEach { seed ->
            val random = Random(seed)
            val noise = FloatArray(rate * 45) { (random.nextFloat() * 2 - 1) * 0.5f }
            val meter = detector.detectRhythm(noise)?.meter
            assertTrue("got $meter", meter == null || meter.confidence == TempoConfidence.Low)
        }
    }

    @Test
    fun silenceGivesNothing() {
        assertNull(detector.detectRhythm(FloatArray(rate * 45)))
    }

    @Test
    fun tooFewBarsGiveNoMeter() {
        val rhythm = detector.detectRhythm(SyntheticAudio.pattern(120.0, 4, rock, seconds = 10))
        assertNotNull(rhythm)
        assertNull(rhythm!!.meter)
    }

    @Test
    fun gridLandsOnTheDownbeat() {
        val rows = mutableListOf<String>()
        var worst = 0.0
        var periodsOk = true
        for (case in cleanCases) {
            val barMs = 60_000.0 / case.bpm * case.beats
            // Drop the start so the analyzed audio begins mid-bar, like a window cut from the middle of a song.
            for (shiftMs in listOf(0.0, 1_234.5)) {
                val full = SyntheticAudio.pattern(case.bpm, case.beats, case.hits, seconds = 47)
                val shift = (shiftMs * rate / 1000).toInt()
                val samples = full.copyOfRange(shift, shift + rate * 45)
                val grid = detector.detectRhythm(samples)!!.grid
                assertNotNull(case.name, grid)
                val expected = Math.floorMod(-shift.toLong(), (barMs * rate / 1000).toLong()) * 1000.0 / rate
                val actual = ((grid!!.downbeatMs % barMs) + barMs) % barMs
                var error = kotlin.math.abs(actual - expected)
                error = minOf(error, barMs - error)
                val signed = ((actual - expected + barMs / 2) % barMs + barMs) % barMs - barMs / 2
                rows += "%-9s shift=%.0f downbeat=%.1f expected=%.1f error=%+.1f ms period=%.2f".format(
                    case.name, shiftMs, actual, expected, signed, grid.periodMs,
                )
                worst = maxOf(worst, error)
                val clickMs = 60_000.0 / case.bpm
                if (kotlin.math.abs(grid.periodMs - clickMs) > clickMs * 0.001) periodsOk = false
            }
        }
        assertTrue(rows.joinToString("\n"), worst <= 6.0)
        assertTrue(rows.joinToString("\n"), periodsOk)
    }

    @Test
    fun detectRhythmMatchesDetectForTheTempo() {
        val samples = SyntheticAudio.pattern(120.0, 4, rock)
        assertEquals(detector.detect(samples), detector.detectRhythm(samples)!!.tempo)
    }
}
