package com.autoomstudio.mplay.separation.dsp

import kotlin.math.PI
import kotlin.math.ceil
import kotlin.math.cos
import kotlin.math.floor
import kotlin.math.sin

/**
 * Streaming sample-rate converter for interleaved stereo: a Blackman-windowed sinc, looked up from a table,
 * with the cutoff lowered when downsampling so nothing aliases. The output has no delay: output frame m is
 * the input interpolated at m * inRate / outRate, and [flush] emits ceil(inputFrames * outRate / inRate) frames in total.
 */
class StereoResampler(private val inRate: Int, private val outRate: Int) {

    init {
        require(inRate > 0 && outRate > 0)
    }

    val isPassthrough: Boolean = inRate == outRate

    private val scale = minOf(1.0, outRate.toDouble() / inRate)
    private val halfWidth = ceil(HALF_TAPS / scale).toInt()
    private val cutoff = 0.5 * scale * PASSBAND

    /** Kernel value at distance d (input frames) is table[d * PHASES], linearly interpolated. */
    private val table = FloatArray(halfWidth * PHASES + 2) { i ->
        val x = i.toDouble() / PHASES
        if (x >= halfWidth) {
            0f
        } else {
            val arg = 2 * cutoff * x
            val sinc = if (arg == 0.0) 1.0 else sin(PI * arg) / (PI * arg)
            val r = x / halfWidth
            val window = 0.42 + 0.5 * cos(PI * r) + 0.08 * cos(2 * PI * r)
            (2 * cutoff * sinc * window).toFloat()
        }
    }

    private var buffer = FloatArray(0)
    private var bufferStart = 0L
    private var bufferFrames = 0
    private var inputFrames = 0L
    private var outputIndex = 0L
    private var flushed = false
    private val out = FloatArray(BLOCK * 2)
    private var outFrames = 0

    /** Feeds [frames] interleaved stereo frames; [emit] receives converted blocks (array reused afterwards). */
    fun process(input: FloatArray, frames: Int, emit: (FloatArray, Int) -> Unit) {
        check(!flushed)
        if (isPassthrough) {
            emit(input, frames)
            return
        }
        append(input, frames)
        produce(emit)
    }

    /** Emits the remaining output once the input has ended. */
    fun flush(emit: (FloatArray, Int) -> Unit) {
        if (flushed) return
        flushed = true
        if (isPassthrough) return
        produce(emit)
    }

    private fun append(input: FloatArray, frames: Int) {
        val needed = (bufferFrames + frames) * 2
        if (buffer.size < needed) buffer = buffer.copyOf(maxOf(needed, buffer.size * 3 / 2))
        System.arraycopy(input, 0, buffer, bufferFrames * 2, frames * 2)
        bufferFrames += frames
        inputFrames += frames
    }

    private fun produce(emit: (FloatArray, Int) -> Unit) {
        val totalOut = if (flushed) ceilDiv(inputFrames * outRate, inRate.toLong()) else Long.MAX_VALUE
        while (outputIndex < totalOut) {
            val t = outputIndex.toDouble() * inRate / outRate
            val centre = floor(t).toLong()
            if (!flushed && centre + halfWidth >= inputFrames) break
            var left = 0.0
            var right = 0.0
            for (k in centre - halfWidth + 1..centre + halfWidth) {
                if (k < 0 || k >= inputFrames) continue
                val d = kotlin.math.abs(t - k) * PHASES
                val i = d.toInt()
                val frac = (d - i).toFloat()
                val h = table[i] + (table[i + 1] - table[i]) * frac
                val p = ((k - bufferStart) * 2).toInt()
                left += buffer[p] * h
                right += buffer[p + 1] * h
            }
            out[outFrames * 2] = left.toFloat()
            out[outFrames * 2 + 1] = right.toFloat()
            outFrames++
            outputIndex++
            if (outFrames == BLOCK) {
                emit(out, outFrames)
                outFrames = 0
            }
        }
        if (outFrames > 0) {
            emit(out, outFrames)
            outFrames = 0
        }
        discardConsumed()
    }

    private fun discardConsumed() {
        val t = outputIndex.toDouble() * inRate / outRate
        val keepFrom = (floor(t).toLong() - halfWidth + 1).coerceAtLeast(bufferStart)
        val drop = (keepFrom - bufferStart).coerceAtMost(bufferFrames.toLong()).toInt()
        if (drop <= 0) return
        System.arraycopy(buffer, drop * 2, buffer, 0, (bufferFrames - drop) * 2)
        bufferFrames -= drop
        bufferStart += drop
    }

    private fun ceilDiv(a: Long, b: Long): Long = (a + b - 1) / b

    private companion object {
        const val HALF_TAPS = 24
        const val PHASES = 512
        const val PASSBAND = 0.97
        const val BLOCK = 4096
    }
}
