package com.autoomstudio.mplay.spike

import com.autoomstudio.mplay.separation.pipeline.StemSink
import java.io.BufferedInputStream
import java.io.DataInputStream
import java.io.EOFException
import java.io.File
import kotlin.math.log10

/**
 * Passes stems on to [next] while comparing them with reference stems from
 * `tools/make_reference.py song` (interleaved float32 little-endian). Reports signal-to-distortion in dB.
 */
class ReferenceComparison(
    private val next: StemSink,
    vocalsReference: File,
    instrumentalReference: File,
) : StemSink {
    private val vocals = Accumulator(vocalsReference)
    private val instrumental = Accumulator(instrumentalReference)

    override fun write(vocals: FloatArray, instrumental: FloatArray, frames: Int) {
        next.write(vocals, instrumental, frames)
        this.vocals.add(vocals, frames)
        this.instrumental.add(instrumental, frames)
    }

    override fun finish() {
        next.finish()
        vocals.close()
        instrumental.close()
    }

    fun summary(): String = "SDR vs Demucs: vocals ${"%.1f".format(vocals.sdr())} dB, " +
        "instrumental ${"%.1f".format(instrumental.sdr())} dB"

    private class Accumulator(file: File) {
        private val input = DataInputStream(BufferedInputStream(file.inputStream(), 1 shl 16))
        private var signal = 0.0
        private var error = 0.0
        private var ended = false

        fun add(block: FloatArray, frames: Int) {
            if (ended) return
            try {
                for (i in 0 until frames * 2) {
                    val ref = java.lang.Float.intBitsToFloat(Integer.reverseBytes(input.readInt())).toDouble()
                    val diff = ref - block[i]
                    signal += ref * ref
                    error += diff * diff
                }
            } catch (_: EOFException) {
                ended = true
            }
        }

        fun sdr(): Double = 10 * log10((signal + 1e-12) / (error + 1e-12))

        fun close() = input.close()
    }
}