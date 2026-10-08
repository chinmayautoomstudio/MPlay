package com.autoomstudio.mp3studio.playback.lofi

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.sin
import kotlin.math.sqrt

class LofiEffectTest {

    private val sampleRate = 44_100
    private val channels = 2

    private fun sine(frames: Int, frequency: Double, amplitude: Float = 0.5f): FloatArray =
        FloatArray(frames * channels) { i ->
            (amplitude * sin(2 * PI * frequency * (i / channels) / sampleRate)).toFloat()
        }

    @Test
    fun disabledEffectLeavesSamplesUntouched() {
        val effect = LofiEffect(sampleRate, channels)
        val input = sine(1_000, 440.0)
        val samples = input.copyOf()
        effect.process(samples, 1_000)
        assertArrayEquals(input, samples, 0f)
        assertTrue(effect.isBypassed)
    }

    @Test
    fun enablingFadesInInsteadOfJumping() {
        val effect = LofiEffect(sampleRate, channels)
        effect.enabled = true
        val input = sine(100, 440.0)
        val samples = input.copyOf()
        effect.process(samples, 100)
        // The first frame is almost entirely the original sound.
        assertEquals(input[0], samples[0], 0.01f)
        assertTrue(effect.mix > 0f && effect.mix < 0.05f)
    }

    @Test
    fun crossfadeFinishesWithinThreeHundredMilliseconds() {
        val effect = LofiEffect(sampleRate, channels)
        effect.enabled = true
        val frames = (sampleRate * 0.3).toInt()
        effect.process(sine(frames, 440.0), frames)
        assertEquals(1f, effect.mix, 0f)

        effect.enabled = false
        effect.process(sine(frames, 440.0), frames)
        assertEquals(0f, effect.mix, 0f)
        assertTrue(effect.isBypassed)
    }

    @Test
    fun fullScaleInputNeverClips() {
        val effect = LofiEffect(sampleRate, channels, startEnabled = true)
        val frames = sampleRate
        val samples = sine(frames, 220.0, amplitude = 1f)
        effect.process(samples, frames)
        assertTrue(samples.all { abs(it) <= 1f })
    }

    @Test
    fun lowPassCutsHighFrequencies() {
        val effect = LofiEffect(sampleRate, channels, startEnabled = true)
        val frames = sampleRate / 2
        val samples = sine(frames, 12_000.0)
        effect.process(samples, frames)
        // Skip the start while the filter and reverb settle, then compare loudness.
        val tail = samples.copyOfRange(samples.size / 2, samples.size)
        val peak = tail.maxOf { abs(it) }
        assertTrue("peak was $peak", peak < 0.25f)
    }

    @Test
    fun silenceHasOnlySoftHissAndNoCrackle() {
        val effect = LofiEffect(sampleRate, channels, startEnabled = true)
        val frames = sampleRate * 3
        val samples = FloatArray(frames * channels)
        effect.process(samples, frames)
        val peak = samples.maxOf { abs(it) }
        assertTrue("peak was $peak", peak <= 0.011f)
    }

    @Test
    fun vocalBandIsQuieterThanBass() {
        val vocal = settledRms(sine(sampleRate / 2, 2_500.0, amplitude = 0.3f))
        val bass = settledRms(sine(sampleRate / 2, 150.0, amplitude = 0.3f))
        assertTrue("vocal $vocal, bass $bass", vocal < bass * 0.7f)
    }

    @Test
    fun stereoSidesAreLiftedOverTheCentre() {
        val centre = sine(sampleRate / 2, 300.0, amplitude = 0.3f)
        val sides = centre.copyOf().also { for (i in 1 until it.size step channels) it[i] = -it[i] }
        val centreRms = settledRms(centre)
        val sidesRms = settledRms(sides)
        assertTrue("centre $centreRms, sides $sidesRms", sidesRms > centreRms * 1.15f)
    }

    private fun settledRms(samples: FloatArray): Float {
        LofiEffect(sampleRate, channels, startEnabled = true).process(samples, samples.size / channels)
        val tail = samples.copyOfRange(samples.size / 2, samples.size)
        return sqrt(tail.sumOf { (it * it).toDouble() } / tail.size).toFloat()
    }

    @Test
    fun startEnabledSkipsTheFadeAfterASeek() {
        val effect = LofiEffect(sampleRate, channels, startEnabled = true)
        assertEquals(1f, effect.mix, 0f)
        assertTrue(!effect.isBypassed)
    }

    @Test
    fun outputIsRepeatableForTheSameSeed() {
        val first = sine(2_000, 440.0).also { LofiEffect(sampleRate, channels, startEnabled = true).process(it, 2_000) }
        val second = sine(2_000, 440.0).also { LofiEffect(sampleRate, channels, startEnabled = true).process(it, 2_000) }
        assertArrayEquals(first, second, 0f)
    }
}
