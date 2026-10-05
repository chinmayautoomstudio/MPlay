package com.autoomstudio.mplay.separation.dsp

import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Test
import kotlin.math.PI
import kotlin.math.ceil
import kotlin.math.sin

class StereoResamplerTest {

    @Test
    fun downsamples48kTo44k() = checkSine(inRate = 48_000, outRate = 44_100, frequency = 1000.0)

    @Test
    fun upsamples22kTo44k() = checkSine(inRate = 22_050, outRate = 44_100, frequency = 3000.0)

    @Test
    fun upsamples32kTo44k() = checkSine(inRate = 32_000, outRate = 44_100, frequency = 440.0)

    @Test
    fun passthroughForwardsBlocksUnchanged() {
        val resampler = StereoResampler(44_100, 44_100)
        val block = FloatArray(8) { it.toFloat() }
        var received: FloatArray? = null
        resampler.process(block, 4) { out, frames ->
            received = out
            assertEquals(4, frames)
        }
        assertSame(block, received)
    }

    private fun checkSine(inRate: Int, outRate: Int, frequency: Double) {
        val inputFrames = inRate * 2
        val input = FloatArray(inputFrames * 2) { i ->
            val n = i / 2
            val phase = if (i % 2 == 0) 0.0 else 0.5
            (0.5 * sin(2 * PI * frequency * n / inRate + phase)).toFloat()
        }
        val output = ArrayList<Float>()
        val resampler = StereoResampler(inRate, outRate)
        val collect: (FloatArray, Int) -> Unit = { block, frames -> for (i in 0 until frames * 2) output += block[i] }
        // Uneven blocks exercise the streaming state.
        var offset = 0
        var block = 777
        while (offset < inputFrames) {
            val frames = minOf(block, inputFrames - offset)
            resampler.process(input.copyOfRange(offset * 2, (offset + frames) * 2), frames, collect)
            offset += frames
            block = block * 7 % 5000 + 100
        }
        resampler.flush(collect)

        val expectedFrames = ceil(inputFrames.toDouble() * outRate / inRate).toInt()
        assertEquals(expectedFrames, output.size / 2)
        // Away from the zero-padded edges the output is the same sine sampled at the new rate.
        val margin = 200
        for (m in margin until expectedFrames - margin step 7) {
            val left = 0.5 * sin(2 * PI * frequency * m / outRate)
            val right = 0.5 * sin(2 * PI * frequency * m / outRate + 0.5)
            assertEquals("left $m", left, output[2 * m].toDouble(), 2e-3)
            assertEquals("right $m", right, output[2 * m + 1].toDouble(), 2e-3)
        }
    }
}
