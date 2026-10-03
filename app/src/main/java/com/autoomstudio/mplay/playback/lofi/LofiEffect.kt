package com.autoomstudio.mplay.playback.lofi

import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.math.sign
import kotlin.math.sin
import kotlin.math.tanh

/**
 * The lofi sound: low-pass, light bit-crush, a small reverb and a quiet vinyl layer, on interleaved
 * float samples in [-1, 1]. [enabled] may change from any thread; the wet amount follows it with a
 * short crossfade so toggling never clicks.
 */
class LofiEffect(
    sampleRate: Int,
    private val channelCount: Int,
    startEnabled: Boolean = false,
    seed: Long = 0x5EED,
) {

    @Volatile
    var enabled: Boolean = startEnabled

    /** 0 = original sound, 1 = full lofi. Starts settled, so a seek does not fade the effect back in. */
    var mix: Float = if (startEnabled) 1f else 0f
        private set

    private val mixStep = 1f / (sampleRate * CROSSFADE_SECONDS)
    private val lowPass = Array(channelCount) { Biquad.lowPass(sampleRate, CUTOFF_HZ, Q) }
    private val reverb = Array(channelCount) { SchroederReverb(sampleRate, stereoOffsetMs = it * 0.37f) }
    private val crackleChance = CRACKLES_PER_SECOND / sampleRate
    private var rng = if (seed == 0L) 1L else seed
    private var crackle = 0f
    private var hiss = 0f

    /** True when [process] would leave samples untouched, so callers can skip conversion work. */
    val isBypassed: Boolean get() = !enabled && mix == 0f

    fun process(samples: FloatArray, frameCount: Int) {
        val target = if (enabled) 1f else 0f
        if (target == 0f && mix == 0f) return
        for (frame in 0 until frameCount) {
            mix = if (mix < target) min(target, mix + mixStep) else max(target, mix - mixStep)
            val noise = vinylNoise()
            val base = frame * channelCount
            for (channel in 0 until channelCount) {
                val dry = samples[base + channel]
                var wet = crush(lowPass[channel].process(dry))
                wet += reverb[channel].process(wet) * REVERB_MIX
                wet = wet * WET_GAIN + noise
                samples[base + channel] = softClip(dry + mix * (wet - dry))
            }
        }
        // Start the next fade-in from silent filter state instead of stale tails.
        if (mix == 0f) reset()
    }

    fun reset() {
        lowPass.forEach(Biquad::reset)
        reverb.forEach(SchroederReverb::reset)
        crackle = 0f
        hiss = 0f
    }

    private fun vinylNoise(): Float {
        hiss += (nextNoise() * HISS_LEVEL - hiss) * HISS_SMOOTHING
        if (nextUnit() < crackleChance) {
            crackle = (CRACKLE_MIN + nextUnit() * (CRACKLE_MAX - CRACKLE_MIN)) * nextNoise().sign
        } else {
            crackle *= CRACKLE_DECAY
        }
        return hiss + crackle
    }

    /** Xorshift, so output is repeatable in tests and allocation-free on the audio thread. */
    private fun nextBits(): Long {
        var x = rng
        x = x xor (x shl 13)
        x = x xor (x ushr 7)
        x = x xor (x shl 17)
        rng = x
        return x
    }

    private fun nextUnit(): Float = ((nextBits() ushr 40).toFloat() / (1L shl 24).toFloat())

    private fun nextNoise(): Float = nextUnit() * 2f - 1f

    private companion object {
        const val CROSSFADE_SECONDS = 0.15f
        const val CUTOFF_HZ = 4_500.0
        const val Q = 0.707
        const val CRUSH_LEVELS = 512f
        const val REVERB_MIX = 0.22f
        const val WET_GAIN = 0.9f
        const val HISS_LEVEL = 0.01f
        const val HISS_SMOOTHING = 0.3f
        const val CRACKLES_PER_SECOND = 7f
        const val CRACKLE_MIN = 0.015f
        const val CRACKLE_MAX = 0.06f
        const val CRACKLE_DECAY = 0.82f

        fun crush(x: Float): Float = (x * CRUSH_LEVELS).roundToInt() / CRUSH_LEVELS

        /** Linear up to 0.9, then a smooth knee that never exceeds 1. */
        fun softClip(x: Float): Float {
            val magnitude = abs(x)
            if (magnitude <= 0.9f) return x
            return x.sign * (0.9f + 0.1f * tanh((magnitude - 0.9f) / 0.1f))
        }
    }
}

/** RBJ cookbook biquad, transposed direct form II. */
internal class Biquad private constructor(
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
            val w0 = 2 * PI * min(cutoffHz, sampleRate * 0.45) / sampleRate
            val alpha = sin(w0) / (2 * q)
            val cosW0 = cos(w0)
            val a0 = 1 + alpha
            return Biquad(
                b0 = ((1 - cosW0) / 2 / a0).toFloat(),
                b1 = ((1 - cosW0) / a0).toFloat(),
                b2 = ((1 - cosW0) / 2 / a0).toFloat(),
                a1 = (-2 * cosW0 / a0).toFloat(),
                a2 = ((1 - alpha) / a0).toFloat(),
            )
        }
    }
}

/** Four damped combs into two allpasses: a small, soft room. */
internal class SchroederReverb(sampleRate: Int, stereoOffsetMs: Float) {
    private val combs = COMB_MS.map { DelayLine(samplesFor(sampleRate, it + stereoOffsetMs)) }
    private val combFilterState = FloatArray(COMB_MS.size)
    private val allpasses = ALLPASS_MS.map { DelayLine(samplesFor(sampleRate, it + stereoOffsetMs)) }

    fun process(x: Float): Float {
        var sum = 0f
        for (i in combs.indices) {
            val line = combs[i]
            val delayed = line.read()
            combFilterState[i] = delayed + (combFilterState[i] - delayed) * DAMPING
            line.write(x + combFilterState[i] * FEEDBACK)
            sum += delayed
        }
        var y = sum / combs.size
        for (line in allpasses) {
            val delayed = line.read()
            val input = y + delayed * ALLPASS_GAIN
            line.write(input)
            y = delayed - input * ALLPASS_GAIN
        }
        return y
    }

    fun reset() {
        combs.forEach(DelayLine::clear)
        allpasses.forEach(DelayLine::clear)
        combFilterState.fill(0f)
    }

    private class DelayLine(size: Int) {
        private val buffer = FloatArray(size.coerceAtLeast(1))
        private var index = 0

        fun read(): Float = buffer[index]

        fun write(value: Float) {
            buffer[index] = value
            index = (index + 1) % buffer.size
        }

        fun clear() = buffer.fill(0f)
    }

    private companion object {
        val COMB_MS = floatArrayOf(29.7f, 37.1f, 41.1f, 43.7f)
        val ALLPASS_MS = floatArrayOf(5.0f, 1.7f)
        const val FEEDBACK = 0.72f
        const val DAMPING = 0.4f
        const val ALLPASS_GAIN = 0.7f

        fun samplesFor(sampleRate: Int, ms: Float): Int = (sampleRate * ms / 1000f).toInt()
    }
}
