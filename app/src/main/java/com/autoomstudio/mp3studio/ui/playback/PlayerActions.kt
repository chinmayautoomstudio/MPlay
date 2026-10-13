package com.autoomstudio.mp3studio.ui.playback

import com.autoomstudio.mp3studio.data.stems.StemMode

/**
 * Transport controls shared by the mini-player and Now Playing, plus Now Playing's sleep timer, lofi mode and
 * separated-version picker.
 */
interface PlayerActions {
    fun playPause()
    fun next()
    fun previous()

    /** The previous song, even when the current one has played for a while (unlike [previous], which restarts it). */
    fun previousTrack()
    fun seekTo(positionMs: Long)
    fun toggleShuffle()
    fun cycleRepeat()
    fun setSleepTimer(minutes: Int)
    fun setSleepTimerEndOfSong()
    fun extendSleepTimer(minutes: Int)
    fun cancelSleepTimer()
    fun setLofi(enabled: Boolean)
    fun setStemMode(mode: StemMode)

    /** Jump to a timeline index and play from the start. */
    fun playQueueIndex(index: Int)

    /** Reorder two timeline entries. */
    fun moveQueueItem(from: Int, to: Int)

    /** Drop one timeline entry by index. */
    fun removeQueueIndex(index: Int)
}
