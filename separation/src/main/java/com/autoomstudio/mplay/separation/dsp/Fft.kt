package com.autoomstudio.mplay.separation.dsp

import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

/** In-place iterative radix-2 complex FFT of a fixed power-of-two size. Not thread-safe. */
class Fft(val size: Int) {

    init {
        require(size >= 2 && size and (size - 1) == 0) { "size must be a power of two: $size" }
    }

    private val bitReversed = IntArray(size).also { table ->
        val bits = Integer.numberOfTrailingZeros(size)
        for (i in 0 until size) table[i] = Integer.reverse(i) ushr (32 - bits)
    }
    private val cosTable = DoubleArray(size / 2) { cos(2 * PI * it / size) }
    private val sinTable = DoubleArray(size / 2) { sin(2 * PI * it / size) }

    /** X[k] = sum x[n] e^(-2 pi i k n / N), unscaled. */
    fun forward(re: DoubleArray, im: DoubleArray) = transform(re, im, inverse = false)

    /** x[n] = sum X[k] e^(+2 pi i k n / N), unscaled (no 1/N). */
    fun inverse(re: DoubleArray, im: DoubleArray) = transform(re, im, inverse = true)

    private fun transform(re: DoubleArray, im: DoubleArray, inverse: Boolean) {
        require(re.size == size && im.size == size)
        for (i in 0 until size) {
            val j = bitReversed[i]
            if (j > i) {
                var t = re[i]; re[i] = re[j]; re[j] = t
                t = im[i]; im[i] = im[j]; im[j] = t
            }
        }
        val sign = if (inverse) 1.0 else -1.0
        var half = 1
        while (half < size) {
            val step = size / (half * 2)
            var start = 0
            while (start < size) {
                var k = 0
                for (j in start until start + half) {
                    val wr = cosTable[k]
                    val wi = sign * sinTable[k]
                    val l = j + half
                    val tr = wr * re[l] - wi * im[l]
                    val ti = wr * im[l] + wi * re[l]
                    re[l] = re[j] - tr
                    im[l] = im[j] - ti
                    re[j] += tr
                    im[j] += ti
                    k += step
                }
                start += half * 2
            }
            half *= 2
        }
    }
}
