package com.autoomstudio.mplay.metronome

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow

/** Where the music is: [positionMs] in the song was heard at [nanoTime] ([System.nanoTime]), moving at [speed]. */
data class MusicSnapshot(val positionMs: Long, val nanoTime: Long, val speed: Float)

/** What the metronome needs from the music player to follow a song. */
interface MusicTimeline {
    /** The song loaded in the player, or null. */
    val songId: StateFlow<Long?>

    val playing: StateFlow<Boolean>

    /** Emits when the timing jumps: a seek, the same song starting over, or a speed change. */
    val seeks: Flow<Unit>

    /** Null when nothing is loaded. Call on the main thread. */
    fun snapshot(): MusicSnapshot?

    /** Starts following the player; the flows stay empty until then. Call on the main thread. */
    fun connect()

    /** Stops following the player. Call on the main thread. */
    fun release()
}
