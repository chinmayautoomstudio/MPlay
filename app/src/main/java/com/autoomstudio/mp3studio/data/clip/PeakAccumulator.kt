package com.autoomstudio.mp3studio.data.clip

import kotlin.math.abs

/**
 * Folds interleaved 16-bit PCM into [bucketCount] peaks spread over [totalFrames] frames.
 * Frames beyond the estimate land in the last bucket, so a slightly wrong duration is harmless.
 */
class PeakAccumulator(private val bucketCount: Int, totalFrames: Long) {

    private val totalFrames = totalFrames.coerceAtLeast(1L)
    private val peaks = IntArray(bucketCount)
    private var frame = 0L

    fun add(samples: ShortArray, length: Int, channels: Int) {
        val channelCount = channels.coerceAtLeast(1)
        var i = 0
        while (i + channelCount <= length) {
            var loudest = 0
            for (c in 0 until channelCount) {
                val value = abs(samples[i + c].toInt())
                if (value > loudest) loudest = value
            }
            val bucket = ((frame * bucketCount) / totalFrames).toInt().coerceAtMost(bucketCount - 1)
            if (loudest > peaks[bucket]) peaks[bucket] = loudest
            frame++
            i += channelCount
        }
    }

    /** Peaks scaled to 0..1 against the loudest one; all zeros for silence. */
    fun result(): FloatArray {
        val max = peaks.maxOrNull() ?: 0
        if (max == 0) return FloatArray(bucketCount)
        return FloatArray(bucketCount) { peaks[it].toFloat() / max }
    }
}
