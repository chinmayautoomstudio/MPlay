package com.autoomstudio.mplay.separation.dsp

import org.junit.Assert.assertEquals
import org.junit.Test
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin
import kotlin.random.Random

class FftTest {

    @Test
    fun forwardMatchesDirectDft() {
        val n = 64
        val random = Random(7)
        val re = DoubleArray(n) { random.nextDouble(-1.0, 1.0) }
        val im = DoubleArray(n) { random.nextDouble(-1.0, 1.0) }
        val expectedRe = DoubleArray(n)
        val expectedIm = DoubleArray(n)
        for (k in 0 until n) {
            for (t in 0 until n) {
                val a = -2 * PI * k * t / n
                expectedRe[k] += re[t] * cos(a) - im[t] * sin(a)
                expectedIm[k] += re[t] * sin(a) + im[t] * cos(a)
            }
        }
        Fft(n).forward(re, im)
        for (k in 0 until n) {
            assertEquals(expectedRe[k], re[k], 1e-9)
            assertEquals(expectedIm[k], im[k], 1e-9)
        }
    }

    @Test
    fun inverseUndoesForwardUpToScale() {
        val n = 4096
        val random = Random(3)
        val original = DoubleArray(n) { random.nextDouble(-1.0, 1.0) }
        val re = original.copyOf()
        val im = DoubleArray(n)
        val fft = Fft(n)
        fft.forward(re, im)
        fft.inverse(re, im)
        for (i in 0 until n) {
            assertEquals(original[i], re[i] / n, 1e-12)
            assertEquals(0.0, im[i] / n, 1e-12)
        }
    }

    @Test(expected = IllegalArgumentException::class)
    fun rejectsNonPowerOfTwo() {
        Fft(100)
    }
}
