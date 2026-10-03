package com.autoomstudio.mplay.playback

import android.os.Bundle
import androidx.media3.session.SessionCommand

/**
 * Custom commands the app's own [androidx.media3.session.MediaController] sends to [PlaybackService],
 * and the session extras the service publishes back.
 */
internal object PlaybackCommands {
    private const val PREFIX = "com.autoomstudio.mplay.command."

    val setSleepTimer = SessionCommand("${PREFIX}SET_SLEEP_TIMER", Bundle.EMPTY)
    val setSleepTimerEndOfSong = SessionCommand("${PREFIX}SET_SLEEP_TIMER_END_OF_SONG", Bundle.EMPTY)
    val extendSleepTimer = SessionCommand("${PREFIX}EXTEND_SLEEP_TIMER", Bundle.EMPTY)
    val cancelSleepTimer = SessionCommand("${PREFIX}CANCEL_SLEEP_TIMER", Bundle.EMPTY)
    val setLofi = SessionCommand("${PREFIX}SET_LOFI", Bundle.EMPTY)

    val all = listOf(setSleepTimer, setSleepTimerEndOfSong, extendSleepTimer, cancelSleepTimer, setLofi)

    const val ARG_MINUTES = "minutes"
    const val ARG_ENABLED = "enabled"

    /** `SystemClock.elapsedRealtime()` at which the timer fires; absent when no timed timer runs. */
    private const val EXTRA_SLEEP_END_ELAPSED = "sleep_end_elapsed"
    private const val EXTRA_SLEEP_END_OF_SONG = "sleep_end_of_song"
    private const val EXTRA_LOFI = "lofi"

    fun extras(sleepTimer: SleepTimerStatus, lofiEnabled: Boolean): Bundle = Bundle().apply {
        when (sleepTimer) {
            SleepTimerStatus.Off -> Unit
            SleepTimerStatus.EndOfSong -> putBoolean(EXTRA_SLEEP_END_OF_SONG, true)
            is SleepTimerStatus.Running -> putLong(EXTRA_SLEEP_END_ELAPSED, sleepTimer.endElapsedMs)
        }
        putBoolean(EXTRA_LOFI, lofiEnabled)
    }

    fun sleepTimerOf(extras: Bundle): SleepTimerStatus = when {
        extras.getBoolean(EXTRA_SLEEP_END_OF_SONG) -> SleepTimerStatus.EndOfSong
        extras.containsKey(EXTRA_SLEEP_END_ELAPSED) ->
            SleepTimerStatus.Running(extras.getLong(EXTRA_SLEEP_END_ELAPSED))
        else -> SleepTimerStatus.Off
    }

    fun lofiOf(extras: Bundle): Boolean = extras.getBoolean(EXTRA_LOFI)
}
