package com.autoomstudio.mp3studio.playback.lofi

import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.roundToInt
import kotlin.math.sign
import kotlin.math.sin
import kotlin.math.tanh

/**
 * The lofi sound on interleaved float samples in [-1, 1]: a vocal-presence dip on the centre (mid)
 * channel with the stereo sides lifted, so instruments sit level with the voice, then a low-pass,
 * light bit-crush, a small reverb and a soft hiss. [enabled] may change from any thread; the wet
 * amount follows it with a short crossfade so toggling never clicks.
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
    private val isStereo = channelCount == 2
    // Stereo applies the dip to the shared mid signal only; other layouts filter each channel.
    private val presence = Array(if (isStereo) 1 else channelCount) {
        Biquad.peaking(sampleRate, PRESENCE_HZ, PRESENCE_Q, PRESENCE_DB)
    }
    private val lowPass = Array(channelCount) { Biquad.lowPass(sampleRate, CUTOFF_HZ, Q) }
    private val reverb = Array(channelCount) { SchroederReverb(sampleRate, stereoOffsetMs = it * 0.37f) }
    private val balanced = FloatArray(channelCount)
    private var rng = if (seed == 0L) 1L else seed
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
            balanceVocals(samples, base)
            for (channel in 0 until channelCount) {
                val dry = samples[base + channel]
                var wet = crush(lowPass[channel].process(balanced[channel]))
                wet += reverb[channel].process(wet) * REVERB_MIX
                wet = wet * WET_GAIN + noise
                samples[base + channel] = softClip(dry + mix * (wet - dry))
            }
        }
        // Start the next fade-in from silent filter state instead of stale tails.
        if (mix == 0f) reset()
    }

    fun reset() {
        presence.forEach(Biquad::reset)
        lowPass.forEach(Biquad::reset)
        reverb.forEach(SchroederReverb::reset)
        hiss = 0f
    }

    /** Fills [balanced] with the frame at [base], vocal presence dipped and (for stereo) sides lifted. */
    private fun balanceVocals(samples: FloatArray, base: Int) {
        if (isStereo) {
            val left = samples[base]
            val right = samples[base + 1]
            val mid = presence[0].process((left + right) * 0.5f)
            val side = (left - right) * 0.5f * SIDE_GAIN
            balanced[0] = mid + side
            balanced[1] = mid - side
        } else {
            for (channel in 0 until channelCount) {
                balanced[channel] = presence[channel].process(samples[base + channel])
            }
        }
    }

    private fun vinylNoise(): Float {
        hiss += (nextNoise() * HISS_LEVEL - hiss) * HISS_SMOOTHING
        return hiss
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
        const val CUTOFF_HZ = 5_000.0
        const val Q = 0.707
        const val PRESENCE_HZ = 2_500.0
        const val PRESENCE_Q = 1.0
        const val PRESENCE_DB = -4.0
        const val SIDE_GAIN = 1.2f
        const val CRUSH_LEVELS = 512f
        const val REVERB_MIX = 0.5f
        const val WET_GAIN = 0.85f
        const val HISS_LEVEL = 0.01f
        const val HISS_SMOOTHING = 0.3f

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

        private fun omega(sampleRate: Int, hz: Double): Double = 2 * PI * min(hz, sampleRate * 0.45) / sampleRate

        private fun normalized(b0: Double, b1: Double, b2: Double, a0: Double, a1: Double, a2: Double) = Biquad(
            b0 = (b0 / a0).toFloat(),
            b1 = (b1 / a0).toFloat(),
            b2 = (b2 / a0).toFloat(),
            a1 = (a1 / a0).toFloat(),
            a2 = (a2 / a0).toFloat(),
        )
    }
}

/** Four damped combs into two allpasses: a soft room with a tail of about 1.2 s. */
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
        const val FEEDBACK = 0.80f
        const val DAMPING = 0.4f
        const val ALLPASS_GAIN = 0.7f

        fun samplesFor(sampleRate: Int, ms: Float): Int = (sampleRate * ms / 1000f).toInt()
    }
}
