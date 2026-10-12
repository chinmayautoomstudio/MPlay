package com.autoomstudio.mp3studio.playback.equalizer

import com.autoomstudio.mp3studio.playback.dsp.Biquad
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.sign
import kotlin.math.tanh

/**
 * Seven-band equalizer with Bass Boost, Loudness, Virtualizer, Preamp and a soft limiter.
 * Parameter changes from any thread are picked up on the next buffer and smoothed (~20 ms).
 */
class EqualizerEffect(
    private val sampleRate: Int,
    private val channelCount: Int,
    startParams: EqualizerParams = EqualizerParams(),
) {
    @Volatile
    private var target = startParams

    private val bandFilters = Array(channelCount) { arrayOfNulls<Biquad>(EqualizerBands.COUNT) }
    private val bassBoostFilters = arrayOfNulls<Biquad>(channelCount)
    private val loudnessLowFilters = arrayOfNulls<Biquad>(channelCount)
    private val loudnessHighFilters = arrayOfNulls<Biquad>(channelCount)
    private val sideHighPass = if (channelCount == 2) {
        Biquad.highPass(sampleRate, VIRTUALIZER_HP_HZ, 0.707)
    } else {
        null
    }

    private val smoothGainsDb = FloatArray(EqualizerBands.COUNT)
    private var smoothPreampDb = 0f
    private var smoothEnabled = 0f
    private var smoothBassBoost = 0f
    private var smoothLoudness = 0f
    private var smoothVirtualizer = 0f

    private val filterGainsDb = FloatArray(EqualizerBands.COUNT) { Float.NaN }
    private var filterBassBoostOn = false
    private var filterLoudnessOn = false

    private val enabledStep = 1f / (sampleRate * SMOOTH_SECONDS)
    private val gainStep = (EqualizerBands.MAX_GAIN_DB - EqualizerBands.MIN_GAIN_DB) /
        (sampleRate * SMOOTH_SECONDS)

    init {
        applyImmediate(startParams)
    }

    /** Safe from any thread. */
    fun setParams(params: EqualizerParams) {
        target = params
    }

    /** True when processing would leave samples bit-identical (fully bypassed). */
    val isBypassed: Boolean
        get() {
            val p = target
            return (!p.enabled && smoothEnabled == 0f) || (p.isNeutral && smoothEnabled == 0f)
        }

    fun process(samples: FloatArray, frameCount: Int) {
        val params = target
        if (!params.enabled && smoothEnabled == 0f) return
        if (params.isNeutral && smoothEnabled == 0f) return

        val isStereo = channelCount == 2
        for (frame in 0 until frameCount) {
            smoothToward(params)
            ensureFilters()
            val wet = smoothEnabled
            if (wet <= 0f && !params.enabled) continue

            val base = frame * channelCount
            if (isStereo) {
                processStereoFrame(samples, base, wet)
            } else {
                for (ch in 0 until channelCount) {
                    val dry = samples[base + ch]
                    samples[base + ch] = dry + wet * (processMono(dry, ch) - dry)
                }
            }
        }

        if (smoothEnabled == 0f && !params.enabled) reset()
    }

    fun reset() {
        bandFilters.forEach { row -> row.forEach { it?.reset() } }
        bassBoostFilters.forEach { it?.reset() }
        loudnessLowFilters.forEach { it?.reset() }
        loudnessHighFilters.forEach { it?.reset() }
        sideHighPass?.reset()
    }

    private fun processStereoFrame(samples: FloatArray, base: Int, wet: Float) {
        val dryL = samples[base]
        val dryR = samples[base + 1]
        var left = processChannelBandsAndShelves(dryL, 0)
        var right = processChannelBandsAndShelves(dryR, 1)

        val hp = sideHighPass
        if (hp != null && smoothVirtualizer > 0.001f) {
            val mid = (left + right) * 0.5f
            val side = (left - right) * 0.5f
            val sideHigh = hp.process(side)
            val sideOut = side + sideHigh * (VIRTUALIZER_SIDE_GAIN - 1f) * smoothVirtualizer
            left = mid + sideOut
            right = mid - sideOut
        }

        val gain = dbToLinear(smoothPreampDb - currentHeadroomDb())
        left = softLimit(left * gain)
        right = softLimit(right * gain)

        samples[base] = dryL + wet * (left - dryL)
        samples[base + 1] = dryR + wet * (right - dryR)
    }

    private fun processMono(sample: Float, channel: Int): Float {
        val x = processChannelBandsAndShelves(sample, channel)
        return softLimit(x * dbToLinear(smoothPreampDb - currentHeadroomDb()))
    }

    private fun processChannelBandsAndShelves(sample: Float, channel: Int): Float {
        var x = sample
        val bands = bandFilters[channel]
        for (i in 0 until EqualizerBands.COUNT) {
            x = bands[i]?.process(x) ?: x
        }
        if (smoothBassBoost > 0.001f) {
            val shelved = bassBoostFilters[channel]?.process(x) ?: x
            x = x + smoothBassBoost * (shelved - x)
        }
        if (smoothLoudness > 0.001f) {
            val low = loudnessLowFilters[channel]?.process(x) ?: x
            val high = loudnessHighFilters[channel]?.process(low) ?: low
            x = x + smoothLoudness * (high - x)
        }
        return x
    }

    private fun smoothToward(params: EqualizerParams) {
        val targetEnabled = if (params.enabled) 1f else 0f
        smoothEnabled = approach(smoothEnabled, targetEnabled, enabledStep)

        for (i in 0 until EqualizerBands.COUNT) {
            val targetGain = if (params.enabled) params.gainsDb[i] else 0f
            smoothGainsDb[i] = approach(smoothGainsDb[i], targetGain, gainStep)
        }
        val targetPreamp = if (params.enabled) params.preampDb else 0f
        smoothPreampDb = approach(smoothPreampDb, targetPreamp, gainStep)

        smoothBassBoost = approach(
            smoothBassBoost,
            if (params.enabled && params.bassBoost) 1f else 0f,
            enabledStep,
        )
        smoothLoudness = approach(
            smoothLoudness,
            if (params.enabled && params.loudness) 1f else 0f,
            enabledStep,
        )
        smoothVirtualizer = approach(
            smoothVirtualizer,
            if (params.enabled && params.virtualizer) 1f else 0f,
            enabledStep,
        )
    }

    private fun currentHeadroomDb(): Float {
        var maxBoost = 0f
        for (g in smoothGainsDb) maxBoost = max(maxBoost, g.coerceAtLeast(0f))
        maxBoost += smoothBassBoost * BASS_BOOST_DB.toFloat()
        maxBoost += smoothLoudness * maxOf(LOUDNESS_LOW_DB, LOUDNESS_HIGH_DB).toFloat()
        return maxBoost
    }

    private fun ensureFilters() {
        var bandsDirty = false
        for (i in 0 until EqualizerBands.COUNT) {
            val stepped = EqualizerBands.clampGain(smoothGainsDb[i])
            if (filterGainsDb[i] != stepped) {
                filterGainsDb[i] = stepped
                bandsDirty = true
            }
        }
        if (bandsDirty) rebuildBandFilters()

        val wantBass = smoothBassBoost > 0.001f
        if (wantBass != filterBassBoostOn) {
            filterBassBoostOn = wantBass
            rebuildBassBoostFilters()
        }
        val wantLoud = smoothLoudness > 0.001f
        if (wantLoud != filterLoudnessOn) {
            filterLoudnessOn = wantLoud
            rebuildLoudnessFilters()
        }
    }

    private fun rebuildBandFilters() {
        for (ch in 0 until channelCount) {
            bandFilters[ch][0] = Biquad.lowShelf(
                sampleRate,
                EqualizerBands.FREQUENCIES_HZ[0],
                filterGainsDb[0].toDouble(),
            )
            for (i in 1 until EqualizerBands.COUNT - 1) {
                bandFilters[ch][i] = Biquad.peaking(
                    sampleRate,
                    EqualizerBands.FREQUENCIES_HZ[i],
                    EqualizerBands.PEAKING_Q,
                    filterGainsDb[i].toDouble(),
                )
            }
            val last = EqualizerBands.COUNT - 1
            bandFilters[ch][last] = Biquad.highShelf(
                sampleRate,
                EqualizerBands.FREQUENCIES_HZ[last],
                filterGainsDb[last].toDouble(),
            )
        }
    }

    private fun rebuildBassBoostFilters() {
        for (ch in 0 until channelCount) {
            bassBoostFilters[ch] = if (filterBassBoostOn) {
                Biquad.lowShelf(sampleRate, BASS_BOOST_HZ, BASS_BOOST_DB)
            } else {
                null
            }
        }
    }

    private fun rebuildLoudnessFilters() {
        for (ch in 0 until channelCount) {
            if (filterLoudnessOn) {
                loudnessLowFilters[ch] = Biquad.lowShelf(sampleRate, LOUDNESS_LOW_HZ, LOUDNESS_LOW_DB)
                loudnessHighFilters[ch] = Biquad.highShelf(sampleRate, LOUDNESS_HIGH_HZ, LOUDNESS_HIGH_DB)
            } else {
                loudnessLowFilters[ch] = null
                loudnessHighFilters[ch] = null
            }
        }
    }

    private fun applyImmediate(params: EqualizerParams) {
        target = params
        smoothEnabled = if (params.enabled) 1f else 0f
        for (i in 0 until EqualizerBands.COUNT) {
            smoothGainsDb[i] = if (params.enabled) params.gainsDb[i] else 0f
            filterGainsDb[i] = Float.NaN
        }
        smoothPreampDb = if (params.enabled) params.preampDb else 0f
        smoothBassBoost = if (params.enabled && params.bassBoost) 1f else 0f
        smoothLoudness = if (params.enabled && params.loudness) 1f else 0f
        smoothVirtualizer = if (params.enabled && params.virtualizer) 1f else 0f
        filterBassBoostOn = false
        filterLoudnessOn = false
        ensureFilters()
    }

    private companion object {
        const val SMOOTH_SECONDS = 0.02f
        const val BASS_BOOST_HZ = 90.0
        const val BASS_BOOST_DB = 5.0
        const val LOUDNESS_LOW_HZ = 100.0
        const val LOUDNESS_LOW_DB = 4.0
        const val LOUDNESS_HIGH_HZ = 8_000.0
        const val LOUDNESS_HIGH_DB = 3.0
        const val VIRTUALIZER_HP_HZ = 200.0
        const val VIRTUALIZER_SIDE_GAIN = 1.5849f // ~+4 dB

        fun dbToLinear(db: Float): Float = 10.0.pow(db / 20.0).toFloat()

        fun approach(current: Float, target: Float, step: Float): Float =
            if (current < target) min(target, current + step) else max(target, current - step)

        fun softLimit(x: Float): Float {
            val magnitude = abs(x)
            if (magnitude <= 0.95f) return x
            return x.sign * (0.95f + 0.05f * tanh((magnitude - 0.95f) / 0.05f))
        }
    }
}

/** Audio-thread parameters derived from [EqualizerState] (no saved-preset list). */
data class EqualizerParams(
    val enabled: Boolean = false,
    val gainsDb: FloatArray = EqualizerBands.flatGains(),
    val preampDb: Float = 0f,
    val bassBoost: Boolean = false,
    val virtualizer: Boolean = false,
    val loudness: Boolean = false,
) {
    val isNeutral: Boolean
        get() = !enabled || (
            gainsDb.all { it == 0f } &&
                preampDb == 0f &&
                !bassBoost &&
                !virtualizer &&
                !loudness
            )

    /** Largest positive combined boost used for automatic headroom (EN6). */
    fun headroomDb(): Float {
        if (!enabled) return 0f
        var maxBoost = gainsDb.maxOrNull()?.coerceAtLeast(0f) ?: 0f
        if (bassBoost) maxBoost += 5f
        if (loudness) maxBoost += 4f
        return maxBoost
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is EqualizerParams) return false
        return enabled == other.enabled &&
            preampDb == other.preampDb &&
            bassBoost == other.bassBoost &&
            virtualizer == other.virtualizer &&
            loudness == other.loudness &&
            gainsDb.contentEquals(other.gainsDb)
    }

    override fun hashCode(): Int {
        var result = enabled.hashCode()
        result = 31 * result + gainsDb.contentHashCode()
        result = 31 * result + preampDb.hashCode()
        result = 31 * result + bassBoost.hashCode()
        result = 31 * result + virtualizer.hashCode()
        result = 31 * result + loudness.hashCode()
        return result
    }

    companion object {
        fun fromState(state: EqualizerState) = EqualizerParams(
            enabled = state.enabled,
            gainsDb = EqualizerBands.copyGains(state.gainsDb),
            preampDb = EqualizerBands.clampGain(state.preampDb),
            bassBoost = state.bassBoost,
            virtualizer = state.virtualizer,
            loudness = state.loudness,
        )
    }
}
