package com.autoomstudio.mp3studio.metronome

import kotlin.math.roundToInt

/** BPM from the average spacing of the last 4 to 8 taps; a pause of [resetMs] starts over (MT8). */
class TapTempo(private val maxTaps: Int = 8, private val minTaps: Int = 4, private val resetMs: Long = 2_000) {
    private val taps = ArrayDeque<Long>()

    /** Number of taps in the current run, so the UI can show progress before a BPM is ready. */
    val count: Int get() = taps.size

    /** Returns the BPM once there are at least [minTaps] taps, or null while still collecting. */
    fun tap(nowMs: Long): Int? {
        if (taps.isNotEmpty() && nowMs - taps.last() > resetMs) taps.clear()
        taps.addLast(nowMs)
        while (taps.size > maxTaps) taps.removeFirst()
        if (taps.size < minTaps) return null
        val averageMs = (taps.last() - taps.first()).toDouble() / (taps.size - 1)
        if (averageMs <= 0) return null
        return MetronomeSettings.clampBpm((60_000 / averageMs).roundToInt())
    }

    fun reset() = taps.clear()
}
