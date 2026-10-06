package com.autoomstudio.mplay.metronome

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.PI
import kotlin.math.exp
import kotlin.math.sin
import kotlin.random.Random

class TempoDetectorTest {

    private val rate = TempoDetector.ANALYSIS_RATE
    private val detector = TempoDetector(rate)

    /** Decaying tone bursts on every beat; [offbeatGain] adds quieter eighth notes in between. */
    private fun clicks(
        bpm: Double,
        seconds: Int = 30,
        offbeatGain: Float = 0f,
        noise: Float = 0f,
        accentEvery: Int = 0,
        seed: Int = 1,
    ): FloatArray {
        val out = FloatArray(rate * seconds)
        val random = Random(seed)
        if (noise > 0) for (i in out.indices) out[i] = (random.nextFloat() * 2 - 1) * noise
        val beat = rate * 60.0 / bpm
        fun burst(at: Double, gain: Float, freq: Double) {
            val start = at.toInt()
            for (i in 0 until (rate * 0.05).toInt()) {
                val index = start + i
                if (index >= out.size) return
                out[index] += (gain * exp(-i / (rate * 0.01)) * sin(2 * PI * freq * i / rate)).toFloat()
            }
        }
        var n = 0
        while (n * beat < out.size) {
            val accent = accentEvery > 0 && n % accentEvery == 0
            burst(n * beat, if (accent) 1f else 0.7f, if (accent) 1500.0 else 1000.0)
            if (offbeatGain > 0) burst(n * beat + beat / 2, offbeatGain, 3000.0)
            n++
        }
        return out
    }

    private fun assertBpm(expected: Double, samples: FloatArray, tolerance: Double = 1.0) {
        val estimate = detector.detect(samples)
        assertNotNull(estimate)
        assertEquals(expected, estimate!!.bpm, tolerance)
    }

    @Test
    fun findsSteadyClickTempos() {
        listOf(72.0, 97.0, 120.0, 128.0, 150.0).forEach { assertBpm(it, clicks(it)) }
    }

    @Test
    fun octaveAmbiguousTempoLandsOnAnOctaveOfTheBeat() {
        // A bare pulse at 174 is as much 87 as 174; the sheet's x2 and /2 buttons settle it.
        val bpm = detector.detect(clicks(174.0))!!.bpm
        assertTrue("got $bpm", kotlin.math.abs(bpm - 174.0) <= 1.0 || kotlin.math.abs(bpm - 87.0) <= 0.5)
    }

    @Test
    fun handlesFractionalTempos() {
        assertBpm(93.5, clicks(93.5), tolerance = 0.5)
    }

    @Test
    fun quieterOffbeatsDoNotDoubleTheTempo() {
        assertBpm(100.0, clicks(100.0, offbeatGain = 0.35f))
    }

    @Test
    fun accentedBarsStillGiveTheBeat() {
        assertBpm(110.0, clicks(110.0, accentEvery = 4))
    }

    @Test
    fun foldsVeryFastAndVerySlowIntoRange() {
        assertBpm(120.0, clicks(240.0))
        assertBpm(100.0, clicks(50.0))
    }

    @Test
    fun survivesBackgroundNoise() {
        assertBpm(132.0, clicks(132.0, noise = 0.15f))
    }

    @Test
    fun clearClicksAreConfident() {
        assertEquals(TempoConfidence.High, detector.detect(clicks(120.0))!!.confidence)
    }

    @Test
    fun noiseIsNotConfident() {
        val random = Random(7)
        val noise = FloatArray(rate * 30) { (random.nextFloat() * 2 - 1) * 0.5f }
        val estimate = detector.detect(noise)
        assertTrue(estimate == null || estimate.confidence == TempoConfidence.Low)
    }

    @Test
    fun silenceAndShortClipsGiveNothing() {
        assertNull(detector.detect(FloatArray(rate * 30)))
        assertNull(detector.detect(clicks(120.0).copyOf(rate * 2)))
    }
}
