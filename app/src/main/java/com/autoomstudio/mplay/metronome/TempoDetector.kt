package com.autoomstudio.mplay.metronome

import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.exp
import kotlin.math.ln
import kotlin.math.max
import kotlin.math.roundToInt
import kotlin.math.sin
import kotlin.math.sqrt

enum class TempoConfidence { Low, Medium, High }

data class TempoEstimate(val bpm: Double, val confidence: TempoConfidence, val score: Float)

/** The tempo and, when there is enough of a pattern to judge, the time signature from one analysis (MT18). */
data class RhythmEstimate(val tempo: TempoEstimate, val meter: MeterEstimate?)

/**
 * Estimates a song's tempo from mono audio (MT9). It builds an onset envelope from spectral flux, finds the beat
 * period by autocorrelation, and picks between half and double tempo with a comb score and a mild preference
 * for tempos near 120 BPM. Results are folded into [MIN_BPM]..[MAX_BPM]; the user fixes the rest with x2 and /2.
 */
class TempoDetector(private val sampleRate: Int) {

    private val window = FloatArray(FRAME) { (0.5 - 0.5 * cos(2 * PI * it / FRAME)).toFloat() }
    private val framesPerSecond = sampleRate.toDouble() / HOP

    /** Returns null when the audio is too short or too quiet to say anything. */
    fun detect(samples: FloatArray, length: Int = samples.size): TempoEstimate? = analyze(samples, length)?.tempo

    /** Tempo plus a time signature estimate from the same envelopes (MT18); null when [detect] would be. */
    fun detectRhythm(samples: FloatArray, length: Int = samples.size): RhythmEstimate? {
        val analysis = analyze(samples, length) ?: return null
        val meter = MeterDetector().detect(
            accents = analysis.envelopes.accents,
            low = analysis.envelopes.low,
            beatPeriod = analysis.beatPeriod,
            tempo = analysis.tempo,
        )
        return RhythmEstimate(analysis.tempo, meter)
    }

    /** [beatPeriod] is in envelope frames and matches the reported (folded) BPM. */
    private class Analysis(val envelopes: Envelopes, val beatPeriod: Double, val tempo: TempoEstimate)

    /**
     * [full] is the log-magnitude onset envelope the tempo comes from. [accents] and [low] use linear magnitude,
     * over the whole spectrum and below [LOW_BAND_HZ], so a louder hit gives a larger onset; the log envelope
     * barely tells loud from soft.
     */
    private class Envelopes(val full: DoubleArray, val accents: DoubleArray, val low: DoubleArray)

    private fun analyze(samples: FloatArray, length: Int): Analysis? {
        val envelopes = onsetEnvelopes(samples, length) ?: return null
        val envelope = envelopes.full
        val acf = autocorrelation(envelope, lagFor(SEARCH_MIN_BPM) * 2 + 2) ?: return null

        val shortest = lagFor(SEARCH_MAX_BPM)
        val longest = lagFor(SEARCH_MIN_BPM)
        var bestLag = -1
        var bestScore = Double.NEGATIVE_INFINITY
        for (lag in shortest..longest) {
            val comb = acf[lag] + 0.5 * acf.at(lag * 2)
            val bpm = 60 * framesPerSecond / lag
            val octaves = ln(bpm / PREFERRED_BPM) / ln(2.0)
            val score = comb * exp(-0.5 * (octaves / PRIOR_WIDTH_OCTAVES) * (octaves / PRIOR_WIDTH_OCTAVES))
            if (score > bestScore) {
                bestScore = score
                bestLag = lag
            }
        }
        if (bestLag < 0) return null

        var bpm = 60 * framesPerSecond / refineLag(acf, bestLag)
        while (bpm > MAX_BPM) bpm /= 2
        while (bpm < MIN_BPM) bpm *= 2
        val strength = acf[bestLag].toFloat().coerceIn(0f, 1f)
        val confidence = when {
            strength >= HIGH_CONFIDENCE -> TempoConfidence.High
            strength >= MEDIUM_CONFIDENCE -> TempoConfidence.Medium
            else -> TempoConfidence.Low
        }
        val estimate = TempoEstimate((bpm * 10).roundToInt() / 10.0, confidence, strength)
        return Analysis(envelopes, 60 * framesPerSecond / bpm, estimate)
    }

    /** Positive spectral flux with the slow trend removed; see [Envelopes]. */
    private fun onsetEnvelopes(samples: FloatArray, length: Int): Envelopes? {
        val count = (length - FRAME) / HOP + 1
        if (count < MIN_ENVELOPE_FRAMES) return null
        val re = DoubleArray(FRAME)
        val im = DoubleArray(FRAME)
        val bins = FRAME / 2
        val lowBins = (LOW_BAND_HZ * FRAME / sampleRate).roundToInt().coerceIn(2, bins)
        var previous = DoubleArray(bins)
        var current = DoubleArray(bins)
        var previousMagnitude = DoubleArray(bins)
        var currentMagnitude = DoubleArray(bins)
        val flux = DoubleArray(count)
        val accentFlux = DoubleArray(count)
        val lowFlux = DoubleArray(count)
        var energy = 0.0
        for (frame in 0 until count) {
            val start = frame * HOP
            for (i in 0 until FRAME) {
                re[i] = (samples[start + i] * window[i]).toDouble()
                im[i] = 0.0
            }
            fft(re, im)
            var sum = 0.0
            var accentSum = 0.0
            var lowSum = 0.0
            for (bin in 1 until bins) {
                val magnitude = sqrt(re[bin] * re[bin] + im[bin] * im[bin])
                energy += magnitude
                current[bin] = ln(1 + COMPRESSION * magnitude)
                currentMagnitude[bin] = magnitude
                if (frame > 0) {
                    sum += max(0.0, current[bin] - previous[bin])
                    val rise = max(0.0, magnitude - previousMagnitude[bin])
                    accentSum += rise
                    if (bin < lowBins) lowSum += rise
                }
            }
            flux[frame] = sum
            accentFlux[frame] = accentSum
            lowFlux[frame] = lowSum
            val swap = previous
            previous = current
            current = swap
            val swapMagnitude = previousMagnitude
            previousMagnitude = currentMagnitude
            currentMagnitude = swapMagnitude
        }
        if (energy / count < SILENCE_ENERGY) return null
        return Envelopes(shape(flux), shape(accentFlux), shape(lowFlux))
    }

    private fun shape(flux: DoubleArray): DoubleArray {
        val count = flux.size
        // Subtracting a half-second moving average leaves the onsets and drops loudness swells.
        val half = (framesPerSecond * 0.25).roundToInt().coerceAtLeast(1)
        val prefix = DoubleArray(count + 1)
        for (i in 0 until count) prefix[i + 1] = prefix[i] + flux[i]
        val onsets = DoubleArray(count) { i ->
            val from = max(0, i - half)
            val to = minOf(count, i + half + 1)
            max(0.0, flux[i] - (prefix[to] - prefix[from]) / (to - from))
        }
        // Onsets are a frame or two wide; widening them lets beat periods that fall between whole-frame lags
        // still line up, so a fractional period isn't scored lower than its double.
        return DoubleArray(count) { i ->
            var sum = 0.0
            for (k in SMOOTHING.indices) {
                val index = i + k - SMOOTHING.size / 2
                if (index in 0 until count) sum += onsets[index] * SMOOTHING[k]
            }
            sum
        }
    }

    /** Autocorrelation of the mean-removed envelope for lags 0..[maxLag], normalized so lag 0 is 1. */
    private fun autocorrelation(envelope: DoubleArray, maxLag: Int): DoubleArray? {
        if (envelope.size <= maxLag * 2) return null
        val mean = envelope.average()
        val centered = DoubleArray(envelope.size) { envelope[it] - mean }
        val result = DoubleArray(maxLag + 1)
        for (lag in 0..maxLag) {
            var sum = 0.0
            for (i in 0 until centered.size - lag) sum += centered[i] * centered[i + lag]
            // Unbiased, so long lags aren't penalized for overlapping less.
            result[lag] = sum / (centered.size - lag)
        }
        val zero = result[0]
        if (zero <= 0) return null
        for (lag in result.indices) result[lag] /= zero
        return result
    }

    /** Averages the interpolated peaks near 1 to 4 times the beat lag, for a period finer than one frame. */
    private fun refineLag(acf: DoubleArray, lag: Int): Double {
        var weighted = 0.0
        var weights = 0.0
        for (multiple in 1..4) {
            val center = lag * multiple
            if (center + 2 >= acf.size) break
            val reach = multiple + 1
            var peak = center
            for (candidate in (center - reach).coerceAtLeast(1)..(center + reach).coerceAtMost(acf.size - 2)) {
                if (acf[candidate] > acf[peak]) peak = candidate
            }
            val a = acf[peak - 1]
            val b = acf[peak]
            val c = acf[peak + 1]
            val denominator = a - 2 * b + c
            val offset = if (denominator < 0) (0.5 * (a - c) / denominator).coerceIn(-0.5, 0.5) else 0.0
            // Each peak gives the period as (peak / multiple); weighting that by multiple favors the finer later peaks.
            weighted += peak + offset
            weights += multiple
        }
        return if (weights > 0) weighted / weights else lag.toDouble()
    }

    private fun DoubleArray.at(lag: Int): Double = if (lag in indices) this[lag] else 0.0

    private fun lagFor(bpm: Double): Int = (60 * framesPerSecond / bpm).roundToInt()

    companion object {
        /** The detector works at this rate; feed it audio resampled to it. */
        const val ANALYSIS_RATE = 11_025
        const val MIN_BPM = 60.0
        const val MAX_BPM = 200.0

        private const val FRAME = 1024
        private const val HOP = 128
        private const val SEARCH_MIN_BPM = 40.0
        private const val SEARCH_MAX_BPM = 240.0
        private const val PREFERRED_BPM = 120.0
        private const val PRIOR_WIDTH_OCTAVES = 1.0
        private const val COMPRESSION = 100.0
        private const val SILENCE_ENERGY = 1e-3
        private const val MIN_ENVELOPE_FRAMES = 400
        private const val HIGH_CONFIDENCE = 0.3f
        private const val MEDIUM_CONFIDENCE = 0.12f
        /** Kicks and bass notes, which usually mark the downbeat, sit below this. */
        private const val LOW_BAND_HZ = 200.0

        /** A Gaussian with a sigma of 1.5 frames. */
        private val SMOOTHING = DoubleArray(9) { exp(-0.5 * ((it - 4) / 1.5) * ((it - 4) / 1.5)) }
            .let { kernel -> kernel.map { it / kernel.sum() }.toDoubleArray() }

        /** In-place radix-2 FFT; [re] and [im] must have the same power-of-two length. */
        private fun fft(re: DoubleArray, im: DoubleArray) {
            val n = re.size
            var j = 0
            for (i in 1 until n) {
                var bit = n shr 1
                while (j and bit != 0) {
                    j = j xor bit
                    bit = bit shr 1
                }
                j = j xor bit
                if (i < j) {
                    var t = re[i]; re[i] = re[j]; re[j] = t
                    t = im[i]; im[i] = im[j]; im[j] = t
                }
            }
            var size = 2
            while (size <= n) {
                val angle = -2 * PI / size
                val stepRe = cos(angle)
                val stepIm = sin(angle)
                for (start in 0 until n step size) {
                    var wRe = 1.0
                    var wIm = 0.0
                    for (k in 0 until size / 2) {
                        val a = start + k
                        val b = a + size / 2
                        val tRe = re[b] * wRe - im[b] * wIm
                        val tIm = re[b] * wIm + im[b] * wRe
                        re[b] = re[a] - tRe
                        im[b] = im[a] - tIm
                        re[a] += tRe
                        im[a] += tIm
                        val next = wRe * stepRe - wIm * stepIm
                        wIm = wRe * stepIm + wIm * stepRe
                        wRe = next
                    }
                }
                size *= 2
            }
        }
    }
}
