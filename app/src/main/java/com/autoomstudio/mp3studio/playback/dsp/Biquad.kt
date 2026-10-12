package com.autoomstudio.mp3studio.playback.dsp

import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.sin
import kotlin.math.sqrt

/** RBJ cookbook biquad, transposed direct form II. */
class Biquad private constructor(
    private val b0: Float,
    private val b1: Float,
    private val b2: Float,
    private val a1: Float,
    private val a2: Float,
) {
    private var z1 = 0f
    private var z2 = 0f

    fun process(x: Float): Float {
        val y = b0 * x + z1
        z1 = b1 * x - a1 * y + z2
        z2 = b2 * x - a2 * y
        return y
    }

    fun reset() {
        z1 = 0f
        z2 = 0f
    }

    companion object {
        fun lowPass(sampleRate: Int, cutoffHz: Double, q: Double): Biquad {
            val w0 = omega(sampleRate, cutoffHz)
            val alpha = sin(w0) / (2 * q)
            val cosW0 = cos(w0)
            return normalized(
                b0 = (1 - cosW0) / 2,
                b1 = 1 - cosW0,
                b2 = (1 - cosW0) / 2,
                a0 = 1 + alpha,
                a1 = -2 * cosW0,
                a2 = 1 - alpha,
            )
        }

        fun highPass(sampleRate: Int, cutoffHz: Double, q: Double): Biquad {
            val w0 = omega(sampleRate, cutoffHz)
            val alpha = sin(w0) / (2 * q)
            val cosW0 = cos(w0)
            return normalized(
                b0 = (1 + cosW0) / 2,
                b1 = -(1 + cosW0),
                b2 = (1 + cosW0) / 2,
                a0 = 1 + alpha,
                a1 = -2 * cosW0,
                a2 = 1 - alpha,
            )
        }

        fun peaking(sampleRate: Int, centerHz: Double, q: Double, gainDb: Double): Biquad {
            val w0 = omega(sampleRate, centerHz)
            val alpha = sin(w0) / (2 * q)
            val cosW0 = cos(w0)
            val a = 10.0.pow(gainDb / 40)
            return normalized(
                b0 = 1 + alpha * a,
                b1 = -2 * cosW0,
                b2 = 1 - alpha * a,
                a0 = 1 + alpha / a,
                a1 = -2 * cosW0,
                a2 = 1 - alpha / a,
            )
        }

        fun lowShelf(sampleRate: Int, cutoffHz: Double, gainDb: Double, slope: Double = 1.0): Biquad {
            val w0 = omega(sampleRate, cutoffHz)
            val cosW0 = cos(w0)
            val a = 10.0.pow(gainDb / 40)
            val alpha = sin(w0) / 2 * sqrt((a + 1 / a) * (1 / slope - 1) + 2)
            val twoSqrtAAlpha = 2 * sqrt(a) * alpha
            return normalized(
                b0 = a * ((a + 1) - (a - 1) * cosW0 + twoSqrtAAlpha),
                b1 = 2 * a * ((a - 1) - (a + 1) * cosW0),
                b2 = a * ((a + 1) - (a - 1) * cosW0 - twoSqrtAAlpha),
                a0 = (a + 1) + (a - 1) * cosW0 + twoSqrtAAlpha,
                a1 = -2 * ((a - 1) + (a + 1) * cosW0),
                a2 = (a + 1) + (a - 1) * cosW0 - twoSqrtAAlpha,
            )
        }

        fun highShelf(sampleRate: Int, cutoffHz: Double, gainDb: Double, slope: Double = 1.0): Biquad {
            val w0 = omega(sampleRate, cutoffHz)
            val cosW0 = cos(w0)
            val a = 10.0.pow(gainDb / 40)
            val alpha = sin(w0) / 2 * sqrt((a + 1 / a) * (1 / slope - 1) + 2)
            val twoSqrtAAlpha = 2 * sqrt(a) * alpha
            return normalized(
                b0 = a * ((a + 1) + (a - 1) * cosW0 + twoSqrtAAlpha),
                b1 = -2 * a * ((a - 1) + (a + 1) * cosW0),
                b2 = a * ((a + 1) + (a - 1) * cosW0 - twoSqrtAAlpha),
                a0 = (a + 1) - (a - 1) * cosW0 + twoSqrtAAlpha,
                a1 = 2 * ((a - 1) - (a + 1) * cosW0),
                a2 = (a + 1) - (a - 1) * cosW0 - twoSqrtAAlpha,
            )
        }

        private fun omega(sampleRate: Int, hz: Double): Double =
            2 * PI * min(hz, sampleRate * 0.45) / sampleRate

        private fun normalized(
            b0: Double,
            b1: Double,
            b2: Double,
            a0: Double,
            a1: Double,
            a2: Double,
        ) = Biquad(
            b0 = (b0 / a0).toFloat(),
            b1 = (b1 / a0).toFloat(),
            b2 = (b2 / a0).toFloat(),
            a1 = (a1 / a0).toFloat(),
            a2 = (a2 / a0).toFloat(),
        )
    }
}
