package com.autoomstudio.mp3studio.separation.dsp

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.nio.FloatBuffer
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

class DemucsSpectrogramTest {

    private val length = DemucsSpectrogram.SEGMENT
    private val spectrogram = DemucsSpectrogram(length)

    @Test
    fun segmentHas336Frames() {
        assertEquals(336, spectrogram.frames)
        assertEquals(4 * 2048 * 336, spectrogram.magSize)
    }

    /** Recomputes chosen bins straight from torch.stft's definition on Demucs' reflect-padded signal. */
    @Test
    fun forwardMatchesDirectDefinition() {
        val left = signal(0)
        val right = signal(1)
        val mag = FloatBuffer.allocate(spectrogram.magSize)
        spectrogram.forward(left, right, mag)

        val frames = spectrogram.frames
        val plane = DemucsSpectrogram.FREQS * frames
        for (frame in intArrayOf(0, 1, 2, 150, frames - 2, frames - 1)) {
            for (bin in intArrayOf(0, 1, 20, 300, 1500, 2047)) {
                for ((channel, x) in listOf(left, right).withIndex()) {
                    val (re, im) = directBin(x, frame, bin)
                    val base = bin * frames + frame
                    assertEquals("re ch$channel f$bin t$frame", re, mag.get((2 * channel) * plane + base).toDouble(), 2e-4)
                    assertEquals("im ch$channel f$bin t$frame", im, mag.get((2 * channel + 1) * plane + base).toDouble(), 2e-4)
                }
            }
        }
    }

    @Test
    fun inverseOfForwardReconstructsBandLimitedSignal() {
        val left = tones(0)
        val right = tones(1)
        val mag = FloatBuffer.allocate(spectrogram.magSize)
        spectrogram.forward(left, right, mag)
        val outLeft = FloatArray(length)
        val outRight = FloatArray(length)
        spectrogram.inverse(mag, intArrayOf(0), outLeft, outRight)
        val edge = DemucsSpectrogram.PAD
        for (i in edge until length - edge) {
            assertEquals(left[i], outLeft[i], 1e-4f)
            assertEquals(right[i], outRight[i], 1e-4f)
        }
    }

    /**
     * Demucs' `_ispec` pads two empty frames on each side and torch.istft still counts them in the window
     * normalization, so the first and last [DemucsSpectrogram.PAD] samples come out attenuated: by exactly half
     * at the very first sample. The model was trained with this, so it is reproduced, not fixed.
     */
    @Test
    fun edgesAreAttenuatedLikeTorch() {
        val left = tones(0)
        val right = tones(1)
        val mag = FloatBuffer.allocate(spectrogram.magSize)
        spectrogram.forward(left, right, mag)
        val outLeft = FloatArray(length)
        val outRight = FloatArray(length)
        spectrogram.inverse(mag, intArrayOf(0), outLeft, outRight)
        assertEquals(left[0] / 2, outLeft[0], 1e-4f)
        // The right padding is asymmetric (the segment is not a multiple of the hop), so only the direction is fixed.
        assertTrue(abs(outRight[length - 1]) < abs(right[length - 1]))
    }

    @Test
    fun inverseSumsSources() {
        val left = tones(0)
        val right = tones(1)
        val mag = FloatBuffer.allocate(spectrogram.magSize)
        spectrogram.forward(left, right, mag)
        // Two sources each holding half of the spectrogram.
        val spec = FloatBuffer.allocate(2 * spectrogram.magSize)
        for (i in 0 until spectrogram.magSize) {
            val half = mag.get(i) / 2
            spec.put(i, half)
            spec.put(spectrogram.magSize + i, half)
        }
        val outLeft = FloatArray(length)
        val outRight = FloatArray(length)
        spectrogram.inverse(spec, intArrayOf(0, 1), outLeft, outRight)
        for (i in DemucsSpectrogram.PAD until length - DemucsSpectrogram.PAD step 101) {
            assertEquals(left[i], outLeft[i], 1e-4f)
            assertEquals(right[i], outRight[i], 1e-4f)
        }
    }

    private fun directBin(x: FloatArray, frame: Int, bin: Int): Pair<Double, Double> {
        val n = DemucsSpectrogram.N_FFT
        val pad = DemucsSpectrogram.PAD
        var re = 0.0
        var im = 0.0
        for (k in 0 until n) {
            // Position in the reflect-padded signal, then mapped back into x.
            var p = frame * DemucsSpectrogram.HOP + k - pad
            if (p < 0) p = -p
            if (p >= length) p = 2 * (length - 1) - p
            val w = 0.5 - 0.5 * cos(2 * PI * k / n)
            val a = -2 * PI * bin * k / n
            re += x[p] * w * cos(a)
            im += x[p] * w * sin(a)
        }
        val scale = 1 / sqrt(n.toDouble())
        return re * scale to im * scale
    }

    private fun signal(channel: Int): FloatArray {
        var state = 99L + channel
        return FloatArray(length) { i ->
            state = (state * 1103515245 + 12345) % (1L shl 31)
            val noise = state.toDouble() / (1L shl 31) * 2 - 1
            (0.4 * sin(2 * PI * 330 * i / 44100.0 + channel) + 0.1 * noise).toFloat()
        }
    }

    private fun tones(channel: Int) = FloatArray(length) { i ->
        val t = i / 44100.0
        (0.5 * sin(2 * PI * 440 * t + channel) + 0.25 * sin(2 * PI * 1234.5 * t) + 0.1 * cos(2 * PI * 6000 * t)).toFloat()
    }
}
