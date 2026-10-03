package com.autoomstudio.mplay.playback

import android.net.Uri

enum class RepeatMode { Off, All, One }

/** Off, then repeat the whole queue, then repeat the current song. */
fun nextRepeatMode(current: RepeatMode): RepeatMode = when (current) {
    RepeatMode.Off -> RepeatMode.All
    RepeatMode.All -> RepeatMode.One
    RepeatMode.One -> RepeatMode.Off
}

/** Snapshot of what the player is doing, taken from the media session rather than the library list. */
data class NowPlayingState(
    val songId: Long?,
    val title: String,
    val artist: String,
    val artworkUri: Uri?,
    val durationMs: Long,
    val isPlaying: Boolean,
    val shuffleEnabled: Boolean,
    val repeatMode: RepeatMode,
    val hasPrevious: Boolean,
    val hasNext: Boolean,
    val sleepTimer: SleepTimerStatus = SleepTimerStatus.Off,
    val lofiEnabled: Boolean = false,
)
