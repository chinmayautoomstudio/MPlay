package com.autoomstudio.mp3studio.separation.pipeline

import kotlin.math.sqrt

/**
 * Mean and sample standard deviation of the mono mix, accumulated in one streaming pass. Demucs normalizes
 * the whole track with these before separating and restores them afterwards.
 */
class TrackStats {
    var frames: Long = 0
        private set
    private var mean = 0.0
    private var m2 = 0.0

    /** Adds [frameCount] interleaved stereo frames. */
    fun add(interleaved: FloatArray, frameCount: Int) {
        for (i in 0 until frameCount) {
            val x = (interleaved[2 * i] + interleaved[2 * i + 1]) * 0.5
            frames++
            val delta = x - mean
            mean += delta / frames
            m2 += delta * (x - mean)
        }
    }

    fun result(): Stats {
        val std = if (frames > 1) sqrt(m2 / (frames - 1)) else 0.0
        // Silence would divide by zero; Demucs would produce NaN, a flat track is safer.
        return Stats(mean.toFloat(), if (std > MIN_STD) std.toFloat() else 1f)
    }

    data class Stats(val mean: Float, val std: Float)

    private companion object {
        const val MIN_STD = 1e-8
    }
}
