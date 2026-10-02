package com.autoomstudio.mplay.ui.playback

/** Transport controls shared by the mini-player and Now Playing. */
interface PlayerActions {
    fun playPause()
    fun next()
    fun previous()
    fun seekTo(positionMs: Long)
    fun toggleShuffle()
    fun cycleRepeat()
}
