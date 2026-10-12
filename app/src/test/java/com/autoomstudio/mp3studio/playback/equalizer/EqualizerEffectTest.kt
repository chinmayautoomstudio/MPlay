package com.autoomstudio.mp3studio.playback.equalizer

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.log10
import kotlin.math.sin
import kotlin.math.sqrt

class EqualizerEffectTest {

    private val sampleRate = 44_100
    private val channels = 2

    private fun sine(frames: Int, frequency: Double, amplitude: Float = 0.5f): FloatArray =
        FloatArray(frames * channels) { i ->
            (amplitude * sin(2 * PI * frequency * (i / channels) / sampleRate)).toFloat()
        }

    @Test
    fun disabledEffectLeavesSamplesUntouched() {
        val effect = EqualizerEffect(sampleRate, channels)
        val input = sine(1_000, 440.0)
        val samples = input.copyOf()
        effect.process(samples, 1_000)
        assertArrayEquals(input, samples, 0f)
        assertTrue(effect.isBypassed)
    }

    @Test
    fun neutralEnabledStillBypassesWhenSettledOffPath() {
        val effect = EqualizerEffect(
            sampleRate,
            channels,
            startParams = EqualizerParams(enabled = false),
        )
        assertTrue(effect.isBypassed)
    }

    @Test
    fun peakingBoostRaisesBandCenterRelativeToOtherBands() {
        // Headroom cancels the absolute boost; the boosted centre stays louder than a far band.
        val params = EqualizerParams(
            enabled = true,
            gainsDb = floatArrayOf(0f, 0f, 0f, 12f, 0f, 0f, 0f),
        )
        val at1k = settledRms(sine(sampleRate / 2, 1_000.0, amplitude = 0.2f), params)
        val at100 = settledRms(sine(sampleRate / 2, 100.0, amplitude = 0.2f), params)
        val relativeDb = 20 * log10(at1k / at100)
        assertTrue("relative $relativeDb dB", relativeDb in 8.0..14.0)
    }

    @Test
    fun flatEnabledIsNearUnityAtMidBand() {
        val frames = sampleRate / 2
        val input = sine(frames, 1_000.0, amplitude = 0.3f)
        val samples = input.copyOf()
        EqualizerEffect(sampleRate, channels, EqualizerParams(enabled = true)).process(samples, frames)
        val mid = samples.size / 2
        val inRms = rms(input.copyOfRange(mid, input.size))
        val outRms = rms(samples.copyOfRange(mid, samples.size))
        val errDb = 20 * log10(outRms / inRms)
        assertTrue("error $errDb dB", abs(errDb) < 0.5)
    }

    @Test
    fun headroomKeepsFullScaleBelowUnity() {
        val gains = floatArrayOf(12f, 12f, 12f, 12f, 12f, 12f, 12f)
        val effect = EqualizerEffect(
            sampleRate,
            channels,
            EqualizerParams(enabled = true, gainsDb = gains, bassBoost = true, loudness = true),
        )
        val frames = sampleRate / 2
        val samples = sine(frames, 100.0, amplitude = 1f)
        effect.process(samples, frames)
        assertTrue(samples.all { abs(it) <= 1.001f })
    }

    @Test
    fun virtualizerLeavesMonoUnchanged() {
        val frames = sampleRate / 4
        val mono = FloatArray(frames * channels) { i ->
            (0.3f * sin(2 * PI * 1_000.0 * (i / channels) / sampleRate)).toFloat()
        }
        val input = mono.copyOf()
        val effect = EqualizerEffect(
            sampleRate,
            channels,
            EqualizerParams(enabled = true, virtualizer = true),
        )
        effect.process(mono, frames)
        val mid = mono.size / 2
        for (i in mid until mono.size) {
            assertEquals(input[i], mono[i], 0.02f)
        }
    }

    @Test
    fun virtualizerDoesNotWidenBass() {
        val frames = sampleRate / 2
        // Anti-phase bass (sides only)
        val sides = FloatArray(frames * channels) { i ->
            val frame = i / channels
            val s = (0.3f * sin(2 * PI * 80.0 * frame / sampleRate)).toFloat()
            if (i % 2 == 0) s else -s
        }
        val before = rms(sides.copyOf().also {
            EqualizerEffect(sampleRate, channels, EqualizerParams(enabled = true)).process(it, frames)
        })
        val after = rms(sides.copyOf().also {
            EqualizerEffect(
                sampleRate,
                channels,
                EqualizerParams(enabled = true, virtualizer = true),
            ).process(it, frames)
        })
        val ratio = after / before
        assertTrue("ratio $ratio", ratio in 0.9f..1.15f)
    }

    @Test
    fun worksAtNinetySixKiloHertz() {
        val rate = 96_000
        val effect = EqualizerEffect(
            rate,
            1,
            EqualizerParams(enabled = true, gainsDb = floatArrayOf(0f, 0f, 0f, 6f, 0f, 0f, 0f)),
        )
        val frames = rate / 10
        val samples = FloatArray(frames) { i ->
            (0.2f * sin(2 * PI * 1_000.0 * i / rate)).toFloat()
        }
        effect.process(samples, frames)
        assertTrue(samples.all { abs(it) <= 1f })
    }

    @Test
    fun gainChangeIsContinuous() {
        val effect = EqualizerEffect(sampleRate, channels, EqualizerParams(enabled = true))
        val frames = 512
        val samples = sine(frames, 440.0, amplitude = 0.4f)
        effect.process(samples, frames)
        effect.setParams(
            EqualizerParams(enabled = true, gainsDb = floatArrayOf(0f, 0f, 0f, 12f, 0f, 0f, 0f)),
        )
        val next = sine(frames, 440.0, amplitude = 0.4f)
        effect.process(next, frames)
        var maxJump = 0f
        for (i in 1 until next.size) {
            maxJump = maxOf(maxJump, abs(next[i] - next[i - 1]))
        }
        assertTrue("max jump $maxJump", maxJump < 0.25f)
    }

    @Test
    fun presetGainsAreWithinRange() {
        BuiltInPreset.entries.forEach { preset ->
            assertEquals(EqualizerBands.COUNT, preset.gainsDb.size)
            preset.gainsDb.forEach { gain ->
                assertTrue(gain in EqualizerBands.MIN_GAIN_DB..EqualizerBands.MAX_GAIN_DB)
            }
        }
    }

    private fun settledRms(samples: FloatArray, params: EqualizerParams): Float {
        EqualizerEffect(sampleRate, channels, params).process(samples, samples.size / channels)
        val mid = samples.size / 2
        return rms(samples.copyOfRange(mid, samples.size))
    }

    private fun rms(samples: FloatArray): Float =
        sqrt(samples.sumOf { (it * it).toDouble() } / samples.size).toFloat()
}
