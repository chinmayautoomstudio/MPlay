package com.autoomstudio.mp3studio.separation

import kotlin.math.ceil

/**
 * Before-you-start estimates from how fast separation ran on this phone (SN3). The speed factor is processing
 * seconds per second of audio, kept as a rolling average so one slow, hot run does not dominate.
 */
object SeparationEstimate {
    private const val WEIGHT_NEW = 0.3

    /** Ignores measurements that cannot be real, such as a song with no duration. */
    fun updatedFactor(previous: Double?, measured: Double): Double? {
        if (!measured.isFinite() || measured <= 0.0) return previous
        if (previous == null || !previous.isFinite() || previous <= 0.0) return measured
        return previous * (1 - WEIGHT_NEW) + measured * WEIGHT_NEW
    }

    /** Whole minutes, at least 1; null without history or without any known song duration. */
    fun minutes(factor: Double?, durationsMs: List<Long>): Int? {
        if (factor == null || factor <= 0.0) return null
        val audioMs = durationsMs.filter { it > 0 }.sum()
        if (audioMs <= 0) return null
        return ceil(audioMs * factor / 60_000.0).toInt().coerceAtLeast(1)
    }
}
