package com.autoomstudio.mplay.playback

sealed interface SleepTimerStatus {
    data object Off : SleepTimerStatus

    /** Fires at [endElapsedMs] on the `SystemClock.elapsedRealtime()` clock. */
    data class Running(val endElapsedMs: Long) : SleepTimerStatus

    /** Fires when the current song finishes. */
    data object EndOfSong : SleepTimerStatus
}

object SleepTimer {
    const val FADE_MS = 30_000L
    const val MIN_MINUTES = 1
    const val MAX_MINUTES = 180
    const val DEFAULT_MINUTES = 30
    const val EXTEND_MINUTES = 10
    val PRESET_MINUTES = listOf(15, 30, 45, 60)

    fun start(minutes: Int, nowElapsedMs: Long): SleepTimerStatus.Running =
        SleepTimerStatus.Running(nowElapsedMs + minutes.coerceIn(MIN_MINUTES, MAX_MINUTES) * MINUTE_MS)

    /**
     * Adds [minutes] to a timed timer. An end-of-song timer becomes a timed one counting from now,
     * and nothing starts when the timer is off.
     */
    fun extend(status: SleepTimerStatus, minutes: Int, nowElapsedMs: Long): SleepTimerStatus = when (status) {
        SleepTimerStatus.Off -> status
        SleepTimerStatus.EndOfSong -> start(minutes, nowElapsedMs)
        is SleepTimerStatus.Running ->
            SleepTimerStatus.Running(maxOf(status.endElapsedMs, nowElapsedMs) + minutes * MINUTE_MS)
    }

    /** Wall-clock time until a timed timer fires, never negative. */
    fun remainingMs(status: SleepTimerStatus.Running, nowElapsedMs: Long): Long =
        (status.endElapsedMs - nowElapsedMs).coerceAtLeast(0L)

    /** Wall-clock time left in the current song, given that media time advances at [speed]. */
    fun songRemainingMs(durationMs: Long, positionMs: Long, speed: Float): Long {
        val mediaRemaining = (durationMs - positionMs).coerceAtLeast(0L)
        return if (speed > 0f) (mediaRemaining / speed).toLong() else mediaRemaining
    }

    /** Full volume until the last [FADE_MS], then a linear fade to silence. */
    fun volumeAt(remainingMs: Long): Float =
        (remainingMs.toFloat() / FADE_MS).coerceIn(0f, 1f)

    private const val MINUTE_MS = 60_000L
}
