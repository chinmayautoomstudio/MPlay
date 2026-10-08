package com.autoomstudio.mp3studio.separation.dsp

import org.junit.Assert.assertEquals
import org.junit.Assume.assumeTrue
import org.junit.Test
import java.io.File
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.FloatBuffer
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

/**
 * Compares against torch/Demucs output written by `python tools/make_reference.py dsp`.
 * Skipped until that reference has been generated.
 */
class SpectrogramReferenceTest {

    private val length = DemucsSpectrogram.SEGMENT
    private val spectrogram = DemucsSpectrogram(length)

    @Test
    fun stftMatchesDemucs() {
        val expected = reference("stft_frames.f32") ?: return
        val mag = forwardOfReferenceSignal()
        val frames = spectrogram.frames
        val freqs = DemucsSpectrogram.FREQS
        var i = 0
        for (frame in STFT_FRAMES) {
            for (channel in 0 until 4) {
                for (f in 0 until freqs) {
                    val actual = mag.get((channel * freqs + f) * frames + frame)
                    assertEquals("frame $frame ch $channel f $f", expected[i++], actual, 1e-4f)
                }
            }
        }
    }

    @Test
    fun istftMatchesDemucs() {
        val expected = reference("istft_samples.f32") ?: return
        val mag = forwardOfReferenceSignal()
        val frames = spectrogram.frames
        val freqs = DemucsSpectrogram.FREQS
        val plane = freqs * frames
        for (f in 0 until freqs) {
            for (t in 0 until frames) {
                val gain = (0.5 + 0.5 * cos(0.013 * f + 0.17 * t)).toFloat()
                for (channel in 0 until 4) {
                    val index = channel * plane + f * frames + t
                    mag.put(index, mag.get(index) * gain)
                }
            }
        }
        val left = FloatArray(length)
        val right = FloatArray(length)
        spectrogram.inverse(mag, intArrayOf(0), left, right)
        var i = 0
        for (channel in listOf(left, right)) {
            for (n in 0 until length step ISTFT_DECIMATION) {
                assertEquals("sample $n", expected[i++], channel[n], 1e-4f)
            }
        }
    }

    private fun forwardOfReferenceSignal(): FloatBuffer {
        val (left, right) = ReferenceSignal.generate(length)
        val mag = FloatBuffer.allocate(spectrogram.magSize)
        spectrogram.forward(left, right, mag)
        return mag
    }

    private fun reference(name: String): FloatArray? {
        val url = javaClass.classLoader?.getResource("reference/$name")
        assumeTrue("Run tools/make_reference.py dsp to generate $name", url != null)
        val bytes = File(url!!.toURI()).readBytes()
        val floats = ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN).asFloatBuffer()
        return FloatArray(floats.remaining()).also(floats::get)
    }

    private companion object {
        val STFT_FRAMES = intArrayOf(0, 1, 2, 167, 334, 335)
        const val ISTFT_DECIMATION = 97
    }
}

/** The synthetic signal from tools/make_reference.py, generated identically. */
object ReferenceSignal {
    fun generate(length: Int): Pair<FloatArray, FloatArray> {
        val channels = Array(2) { c ->
            var state = 12345L + c
            FloatArray(length) { n ->
                state = (state * 1103515245 + 12345) % (1L shl 31)
                val noise = state.toDouble() / (1L shl 31) * 2.0 - 1.0
                val t = n.toDouble() / 44100
                (0.3 * sin(2 * PI * 220.0 * t + c) + 0.2 * sin(2 * PI * 3150.5 * t) + 0.1 * noise).toFloat()
            }
        }
        return channels[0] to channels[1]
    }
}
