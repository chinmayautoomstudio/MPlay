package com.autoomstudio.mplay.separation.pipeline

import com.autoomstudio.mplay.separation.dsp.DemucsSpectrogram
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.FloatBuffer

/**
 * Runs one normalized stereo segment through [model]: spectrogram in, then vocals and instrumental
 * (drums + bass + other) out as `istft(spec) + wave`. Buffers are direct so ONNX Runtime can use them in place.
 */
internal class DemucsSegmentProcessor(private val model: SeparationModel) {
    private val segment = DemucsSpectrogram.SEGMENT
    private val spectrogram = DemucsSpectrogram(segment)

    private val mix = directFloats(2 * segment)
    private val mag = directFloats(spectrogram.magSize)
    private val spec = directFloats(SOURCES * spectrogram.magSize)
    private val wave = directFloats(SOURCES * 2 * segment)

    val vocalsLeft = FloatArray(segment)
    val vocalsRight = FloatArray(segment)
    val instrumentalLeft = FloatArray(segment)
    val instrumentalRight = FloatArray(segment)

    fun process(left: FloatArray, right: FloatArray) {
        mix.clear()
        mix.put(left, 0, segment)
        mix.put(right, 0, segment)
        mix.rewind()
        spectrogram.forward(left, right, mag)
        model.run(mix, mag, spec, wave)

        spectrogram.inverse(spec, VOCALS, vocalsLeft, vocalsRight)
        spectrogram.inverse(spec, INSTRUMENTAL, instrumentalLeft, instrumentalRight)
        addWave(VOCALS, vocalsLeft, vocalsRight)
        addWave(INSTRUMENTAL, instrumentalLeft, instrumentalRight)
    }

    private fun addWave(sources: IntArray, outLeft: FloatArray, outRight: FloatArray) {
        for (s in sources) {
            val leftBase = (s * 2) * segment
            val rightBase = leftBase + segment
            for (n in 0 until segment) {
                outLeft[n] += wave.get(leftBase + n)
                outRight[n] += wave.get(rightBase + n)
            }
        }
    }

    private companion object {
        const val SOURCES = 4
        val VOCALS = intArrayOf(3)
        val INSTRUMENTAL = intArrayOf(0, 1, 2)

        fun directFloats(count: Int): FloatBuffer =
            ByteBuffer.allocateDirect(count * 4).order(ByteOrder.nativeOrder()).asFloatBuffer()
    }
}
