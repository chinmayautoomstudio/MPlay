package com.autoomstudio.mplay.separation.dsp

import java.nio.FloatBuffer
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sqrt

/**
 * HT-Demucs' `_spec`/`_magnitude` and `_mask`/`_ispec` for one stereo segment of [length] samples:
 * a normalized STFT (n_fft 4096, hop 1024, periodic Hann) of the reflect-padded signal, keeping
 * [frames] frames and the lowest [FREQS] bins, stored as channels L.re, L.im, R.re, R.im.
 *
 * Spectrogram layout everywhere is `[channel][freq][frame]`; for model outputs it is prefixed by the source.
 * Not thread-safe: scratch buffers are reused between calls.
 */
class DemucsSpectrogram(val length: Int = SEGMENT) {

    val frames: Int = framesFor(length)
    private val rightPad = PAD + frames * HOP - length
    private val paddedLength = PAD + length + rightPad

    private val fft = Fft(N_FFT)
    private val window = DoubleArray(N_FFT) { 0.5 - 0.5 * cos(2 * PI * it / N_FFT) }
    private val re = DoubleArray(N_FFT)
    private val im = DoubleArray(N_FFT)
    private val paddedLeft = FloatArray(paddedLength)
    private val paddedRight = FloatArray(paddedLength)
    private val accLeft = DoubleArray(length)
    private val accRight = DoubleArray(length)

    /** Window-squared overlap sum at each output sample, as torch.istft divides by it. */
    private val envelope = DoubleArray(length).also { env ->
        // iSTFT input gets 2 empty frames on each side; output sample i sits at i + n_fft/2 + PAD in the full signal.
        val totalFrames = frames + 4
        val offset = N_FFT / 2 + PAD
        for (i in 0 until length) {
            val p = i + offset
            var sum = 0.0
            val tMin = maxOf(0, (p - N_FFT) / HOP)
            val tMax = minOf(totalFrames - 1, p / HOP)
            for (t in tMin..tMax) {
                val n = p - t * HOP
                if (n in 0 until N_FFT) sum += window[n] * window[n]
            }
            env[i] = sum
        }
    }

    val magSize: Int get() = CHANNELS * FREQS * frames

    /** Writes the spectrogram of [left] and [right] (each [length] samples) into [mag] at absolute positions. */
    fun forward(left: FloatArray, right: FloatArray, mag: FloatBuffer) {
        require(left.size >= length && right.size >= length)
        require(mag.capacity() >= magSize)
        reflectPad(left, paddedLeft)
        reflectPad(right, paddedRight)
        val scale = 0.5 / sqrt(N_FFT.toDouble())
        val planeSize = FREQS * frames
        for (j in 0 until frames) {
            val start = j * HOP
            for (n in 0 until N_FFT) {
                val w = window[n]
                re[n] = paddedLeft[start + n] * w
                im[n] = paddedRight[start + n] * w
            }
            fft.forward(re, im)
            // Two real signals packed as L + iR; separate them with the conjugate-symmetric halves.
            for (k in 0 until FREQS) {
                val nk = (N_FFT - k) and (N_FFT - 1)
                val zr = re[k]
                val zi = im[k]
                val cr = re[nk]
                val ci = im[nk]
                val base = k * frames + j
                mag.put(base, ((zr + cr) * scale).toFloat())
                mag.put(planeSize + base, ((zi - ci) * scale).toFloat())
                mag.put(2 * planeSize + base, ((zi + ci) * scale).toFloat())
                mag.put(3 * planeSize + base, ((cr - zr) * scale).toFloat())
            }
        }
    }

    /**
     * Inverse STFT of the sum of [sources] in a model output [spec] laid out `[source][channel][freq][frame]`.
     * Overwrites [outLeft] and [outRight] (each at least [length] samples).
     */
    fun inverse(spec: FloatBuffer, sources: IntArray, outLeft: FloatArray, outRight: FloatArray) {
        require(outLeft.size >= length && outRight.size >= length)
        val planeSize = FREQS * frames
        val sourceSize = CHANNELS * planeSize
        accLeft.fill(0.0)
        accRight.fill(0.0)
        val scale = 1.0 / sqrt(N_FFT.toDouble())
        for (j in 0 until frames) {
            re.fill(0.0)
            im.fill(0.0)
            for (k in 0 until FREQS) {
                val base = k * frames + j
                var lr = 0.0
                var li = 0.0
                var rr = 0.0
                var ri = 0.0
                for (s in sources) {
                    val o = s * sourceSize + base
                    lr += spec.get(o)
                    li += spec.get(o + planeSize)
                    rr += spec.get(o + 2 * planeSize)
                    ri += spec.get(o + 3 * planeSize)
                }
                // A real inverse FFT ignores the imaginary part of the DC bin.
                if (k == 0) {
                    li = 0.0
                    ri = 0.0
                }
                // Pack Y = L + iR, using the Hermitian mirror for the upper half; the Nyquist bin stays zero.
                re[k] += lr - ri
                im[k] += li + rr
                if (k != 0) {
                    val m = N_FFT - k
                    re[m] += lr + ri
                    im[m] += rr - li
                }
            }
            fft.inverse(re, im)
            val start = j * HOP - PAD
            val nFrom = maxOf(0, -start)
            val nTo = minOf(N_FFT, length - start)
            for (n in nFrom until nTo) {
                val w = window[n] * scale
                accLeft[start + n] += re[n] * w
                accRight[start + n] += im[n] * w
            }
        }
        for (i in 0 until length) {
            outLeft[i] = (accLeft[i] / envelope[i]).toFloat()
            outRight[i] = (accRight[i] / envelope[i]).toFloat()
        }
    }

    /** torch-style reflect padding (edge sample not repeated) by [PAD] on the left and [rightPad] on the right. */
    private fun reflectPad(source: FloatArray, target: FloatArray) {
        System.arraycopy(source, 0, target, PAD, length)
        for (i in 0 until PAD) target[i] = source[PAD - i]
        for (k in 0 until rightPad) target[PAD + length + k] = source[length - 2 - k]
    }

    companion object {
        const val N_FFT = 4096
        const val HOP = 1024
        const val FREQS = N_FFT / 2
        const val PAD = HOP / 2 * 3
        const val CHANNELS = 4

        /** HT-Demucs training segment: 7.8 s at 44.1 kHz. */
        const val SEGMENT = 343_980

        fun framesFor(length: Int): Int = (length + HOP - 1) / HOP
    }
}
