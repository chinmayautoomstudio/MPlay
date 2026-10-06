package com.autoomstudio.mplay.metronome

import kotlin.math.max
import kotlin.math.roundToInt
import kotlin.math.sqrt

/**
 * A time signature estimate (MT18). [clickBpm] is the BPM the metronome should use with it: for 6/8 that is the
 * eighth-note rate, so the metronome's six clicks per bar line up with the song's bar.
 */
data class MeterEstimate(
    val beatsPerBar: Int,
    val beatUnit: Int,
    val confidence: TempoConfidence,
    val score: Float,
    val clickBpm: Int,
) {
    val timeSignature: TimeSignature get() = TimeSignature(beatsPerBar, beatUnit)
}

/**
 * Guesses the time signature from the onset envelopes and the beat period found by [TempoDetector] (MT18). It lines
 * a comb up with the beats, takes each beat's full-band and low-band onset strength, and scores bar lengths of 2,
 * 3, 4 and 6 beats by how much one position in the bar stands out (a t-statistic of the accented position against
 * the rest), backed by the autocorrelation of the beat strengths at the bar length and its multiples. Nested bars
 * are told apart by whether the two halves of the longer bar differ: 4/4 needs beat 1 stronger than beat 3; 6/8
 * needs pulse 4 weaker than pulse 1 but stronger than its neighbours, checked on the beat grid and on the half-beat
 * grid (when the tempo locked onto quarter notes), and two-beat bars with triplet subdivisions are 6/8 felt in two.
 * 4/4 is the prior, then 3/4; anything weak falls back to 4/4 with Low confidence.
 */
internal class MeterDetector {

    /**
     * [accents] (full-band) and [low] are linear-magnitude onset envelopes, so they show how hard each beat is hit.
     * [beatPeriod] is in envelope frames and matches [tempo].
     */
    fun detect(accents: DoubleArray, low: DoubleArray, beatPeriod: Double, tempo: TempoEstimate): MeterEstimate? {
        if (beatPeriod < MIN_PERIOD_FRAMES * 2 || accents.size <= beatPeriod || low.size != accents.size) return null
        // The loud hits are the beats; on the log envelope a quiet off-beat hat can look as strong as the snare.
        val phase = beatPhase(accents, beatPeriod)
        val strength = strengths(accents, low, phase, beatPeriod) ?: return null
        val beats = strength.size
        if (beats < MIN_BARS * 4) return null
        val acf = autocorrelation(strength, MAX_BAR * 3)

        val fits = CANDIDATE_BARS.filter { beats >= MIN_BARS * it }.associateWith { fit(strength, it) }
        fun score(bar: Int): Double = fits[bar]?.let { it.t * (0.5 + 0.5 * support(acf, bar)) } ?: 0.0

        val duple = max(score(2), score(4)) * PRIOR_DUPLE
        val triple = max(score(3), score(6)) * PRIOR_TRIPLE
        val best = max(duple, triple)
        val bpm = tempo.bpm.roundToInt()
        if (best < MIN_SCORE) return MeterEstimate(4, 4, TempoConfidence.Low, best.toFloat(), bpm)

        val margin = best / max(minOf(duple, triple), MIN_SCORE)
        var confidence = when {
            best >= HIGH_SCORE && margin >= HIGH_MARGIN -> TempoConfidence.High
            best >= MEDIUM_SCORE && margin >= MEDIUM_MARGIN -> TempoConfidence.Medium
            else -> TempoConfidence.Low
        }
        if (tempo.confidence == TempoConfidence.Low) confidence = TempoConfidence.Low

        if (triple > duple) {
            // The beat is the eighth: six beats, pulse 4 accented less than pulse 1 but more than its neighbours.
            val six = fits[6]
            if (six != null && six.split(3) >= SPLIT_T && six.secondaryAccent() >= SPLIT_T) {
                return MeterEstimate(6, 8, confidence, best.toFloat(), bpm)
            }
            // The beat is the quarter of a 6/8 bar (two eighths), so the second accent falls between beats 2 and 3.
            val eighths = (tempo.bpm * 2).roundToInt()
            val halves = strengths(accents, low, phase, beatPeriod / 2)
                ?.takeIf { it.size >= MIN_BARS * 6 }
                ?.let { fit(it, 6) }
            if (halves != null && halves.secondaryAccent() >= SPLIT_T && eighths <= MetronomeSettings.MAX_BPM) {
                return MeterEstimate(6, 8, confidence, best.toFloat(), eighths)
            }
            return MeterEstimate(3, 4, confidence, best.toFloat(), bpm)
        }
        val four = fits.getValue(4)
        if (four.split(2) >= SPLIT_T) return MeterEstimate(4, 4, confidence, best.toFloat(), bpm)
        // Two-beat bars whose beats split in three are 6/8 felt in two; click the eighths when the engine can.
        val eighths = (tempo.bpm * 3).roundToInt()
        if (isTriplet(accents, phase, beatPeriod) && eighths <= MetronomeSettings.MAX_BPM) {
            return MeterEstimate(6, 8, confidence, best.toFloat(), eighths)
        }
        // A 4/4 bar whose halves sound alike is the same pattern as 2/4, so this is never more than a suggestion.
        return MeterEstimate(2, 4, TempoConfidence.Low, best.toFloat(), bpm)
    }

    /** Where the beat comb sums the most onset energy, in frames from the start. */
    private fun beatPhase(envelope: DoubleArray, period: Double): Double {
        var bestOffset = 0
        var bestSum = Double.NEGATIVE_INFINITY
        for (offset in 0 until period.toInt().coerceAtLeast(1)) {
            var sum = 0.0
            var position = offset.toDouble()
            while (position < envelope.size) {
                sum += envelope[position.roundToInt().coerceAtMost(envelope.size - 1)]
                position += period
            }
            if (sum > bestSum) {
                bestSum = sum
                bestOffset = offset
            }
        }
        return bestOffset.toDouble()
    }

    /**
     * Each beat's onset strength on a grid of [period] frames from [phase]: full-band and low-band peaks, each
     * divided by its mean and blended, so the result averages about 1. Null when there is no onset energy.
     */
    private fun strengths(accents: DoubleArray, low: DoubleArray, phase: Double, period: Double): DoubleArray? {
        val beats = ((accents.size - 1 - phase) / period).toInt() + 1
        if (beats < 1) return null
        val reach = max(MIN_REACH, (period * REACH_FRACTION).roundToInt())
        val fullBeats = DoubleArray(beats) { peak(accents, phase + it * period, reach) }
        val lowBeats = DoubleArray(beats) { peak(low, phase + it * period, reach) }
        val fullMean = fullBeats.average()
        if (fullMean <= 0) return null
        val lowMean = lowBeats.average()
        // Without real bass content the low band is leakage, and dividing by its mean would only amplify noise.
        val useLow = lowMean > fullMean * MIN_LOW_SHARE
        return DoubleArray(beats) { i ->
            if (useLow) {
                FULL_WEIGHT * fullBeats[i] / fullMean + LOW_WEIGHT * lowBeats[i] / lowMean
            } else {
                fullBeats[i] / fullMean
            }
        }
    }

    private fun peak(envelope: DoubleArray, center: Double, reach: Int): Double {
        val middle = center.roundToInt()
        var best = 0.0
        for (i in (middle - reach).coerceAtLeast(0)..(middle + reach).coerceAtMost(envelope.size - 1)) {
            if (envelope[i] > best) best = envelope[i]
        }
        return best
    }

    private class Fit(val means: DoubleArray, val counts: IntArray, val variance: Double, val accent: Int, val t: Double) {
        /** How far the accented position stands above the one [offset] positions later, as a t-statistic. */
        fun split(offset: Int): Double {
            val other = (accent + offset) % means.size
            return (means[accent] - means[other]) / sqrt(variance * (1.0 / counts[accent] + 1.0 / counts[other]))
        }

        /** In a six-beat bar, how far the middle position stands above the positions either side of it. */
        fun secondaryAccent(): Double {
            val middle = (accent + 3) % 6
            val before = (accent + 2) % 6
            val after = (accent + 4) % 6
            val neighbour = if (means[before] > means[after]) before else after
            return (means[middle] - means[neighbour]) / sqrt(variance * (1.0 / counts[middle] + 1.0 / counts[neighbour]))
        }
    }

    /** Mean strength per position in a [bar]-beat bar, and how much the strongest position stands out. */
    private fun fit(strength: DoubleArray, bar: Int): Fit {
        val sums = DoubleArray(bar)
        val counts = IntArray(bar)
        for (i in strength.indices) {
            sums[i % bar] += strength[i]
            counts[i % bar]++
        }
        val means = DoubleArray(bar) { sums[it] / counts[it] }
        var squares = 0.0
        for (i in strength.indices) {
            val d = strength[i] - means[i % bar]
            squares += d * d
        }
        // Clean audio repeats almost exactly; the floor keeps tiny differences from looking significant.
        val variance = max(squares / (strength.size - bar), VARIANCE_FLOOR)
        val accent = means.indices.maxBy { means[it] }
        val otherCount = strength.size - counts[accent]
        val otherMean = (sums.sum() - sums[accent]) / otherCount
        val t = (means[accent] - otherMean) / sqrt(variance * (1.0 / counts[accent] + 1.0 / otherCount))
        return Fit(means, counts, variance, accent, t)
    }

    /** Mean autocorrelation of the beat strengths at the bar length and its multiples, clipped to 0..1. */
    private fun support(acf: DoubleArray, bar: Int): Double {
        val lags = (1..3).map { it * bar }.filter { it < acf.size }
        if (lags.isEmpty()) return 0.0
        return lags.map { acf[it] }.average().coerceIn(0.0, 1.0)
    }

    private fun autocorrelation(values: DoubleArray, maxLag: Int): DoubleArray {
        val mean = values.average()
        val centered = DoubleArray(values.size) { values[it] - mean }
        val lags = minOf(maxLag, values.size / 2)
        val result = DoubleArray(lags + 1)
        for (lag in 0..lags) {
            var sum = 0.0
            for (i in 0 until centered.size - lag) sum += centered[i] * centered[i + lag]
            result[lag] = sum / (centered.size - lag)
        }
        val zero = result[0]
        if (zero <= 0) return DoubleArray(lags + 1)
        for (lag in result.indices) result[lag] /= zero
        return result
    }

    /** Whether onsets inside the beats fall on the thirds (compound time) rather than on the half. */
    private fun isTriplet(accents: DoubleArray, phase: Double, period: Double): Boolean {
        val reach = max(1, (period * SUBDIVISION_REACH).roundToInt())
        fun strengthAt(fraction: Double): Double {
            var sum = 0.0
            var count = 0
            var position = phase + fraction * period
            while (position < accents.size) {
                sum += peak(accents, position, reach)
                count++
                position += period
            }
            return if (count > 0) sum / count else 0.0
        }
        val onBeat = strengthAt(0.0)
        if (onBeat <= 0) return false
        val thirds = (strengthAt(1.0 / 3) + strengthAt(2.0 / 3)) / 2
        val half = strengthAt(0.5)
        return thirds >= TRIPLET_MIN_SHARE * onBeat && thirds >= TRIPLET_RATIO * half
    }

    private companion object {
        val CANDIDATE_BARS = listOf(2, 3, 4, 6)
        const val MAX_BAR = 6
        const val MIN_BARS = 8
        const val MIN_PERIOD_FRAMES = 4.0
        const val MIN_REACH = 2
        const val REACH_FRACTION = 0.08
        const val SUBDIVISION_REACH = 0.05
        const val MIN_LOW_SHARE = 0.01
        const val FULL_WEIGHT = 0.4
        const val LOW_WEIGHT = 0.6
        const val VARIANCE_FLOOR = 0.0025
        const val PRIOR_DUPLE = 1.0
        const val PRIOR_TRIPLE = 0.85
        const val MIN_SCORE = 4.0
        const val MEDIUM_SCORE = 8.0
        const val HIGH_SCORE = 16.0
        const val MEDIUM_MARGIN = 1.3
        const val HIGH_MARGIN = 1.8
        const val SPLIT_T = 4.0
        const val TRIPLET_MIN_SHARE = 0.25
        const val TRIPLET_RATIO = 2.0
    }
}
