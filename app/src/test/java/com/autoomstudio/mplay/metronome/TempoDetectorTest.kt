package com.autoomstudio.mplay.metronome

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.random.Random

class TempoDetectorTest {

    private val rate = TempoDetector.ANALYSIS_RATE
    private val detector = TempoDetector(rate)

    private fun clicks(
        bpm: Double,
        seconds: Int = 30,
        offbeatGain: Float = 0f,
        noise: Float = 0f,
        accentEvery: Int = 0,
    ): FloatArray = SyntheticAudio.clicks(bpm, seconds, offbeatGain, noise, accentEvery)

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
