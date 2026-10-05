package com.autoomstudio.mplay.ui.playback

import com.autoomstudio.mplay.data.stems.StemMode

/**
 * Transport controls shared by the mini-player and Now Playing, plus Now Playing's sleep timer, lofi mode and
 * separated-version picker.
 */
interface PlayerActions {
    fun playPause()
    fun next()
    fun previous()
    fun seekTo(positionMs: Long)
    fun toggleShuffle()
    fun cycleRepeat()
    fun setSleepTimer(minutes: Int)
    fun setSleepTimerEndOfSong()
    fun extendSleepTimer(minutes: Int)
    fun cancelSleepTimer()
    fun setLofi(enabled: Boolean)
    fun setStemMode(mode: StemMode)
}
