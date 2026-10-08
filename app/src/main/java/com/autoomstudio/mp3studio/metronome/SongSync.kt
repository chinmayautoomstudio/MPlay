package com.autoomstudio.mp3studio.metronome

import kotlin.math.abs

/** The metronome output frame [frame] was heard at [nanoTime]. */
data class HeardFrame(val frame: Long, val nanoTime: Long)

/** Song beat 0 (the grid's downbeat) falls on metronome frame [anchorFrame]; beats are [framesPerBeat] apart. */
data class Alignment(val anchorFrame: Double, val framesPerBeat: Double)

/** Maps a song's [BeatGrid] onto the metronome's output frames, using when each side was last heard. */
object SongSync {

    fun align(grid: BeatGrid, music: MusicSnapshot, heard: HeardFrame, sampleRate: Int): Alignment {
        val speed = music.speed.toDouble().takeIf { it > 0 } ?: 1.0
        val downbeatNanos = music.nanoTime + (grid.downbeatMs - music.positionMs) / speed * NANOS_PER_MS
        val anchor = heard.frame + (downbeatNanos - heard.nanoTime) * sampleRate / NANOS_PER_SECOND
        return Alignment(anchor, grid.periodMs / speed * sampleRate / 1000.0)
    }

    /**
     * How far [current] is from [fresh] at [atFrame], in ms; positive when the clicks are late. Infinite when the
     * periods differ, as after a speed change, since the gap then grows.
     */
    fun errorMs(current: Alignment, fresh: Alignment, atFrame: Long, sampleRate: Int): Double {
        if (abs(current.framesPerBeat - fresh.framesPerBeat) > fresh.framesPerBeat * PERIOD_TOLERANCE) {
            return Double.POSITIVE_INFINITY
        }
        val currentBeats = (atFrame - current.anchorFrame) / current.framesPerBeat
        val freshBeats = (atFrame - fresh.anchorFrame) / fresh.framesPerBeat
        return (freshBeats - currentBeats) * fresh.framesPerBeat * 1000.0 / sampleRate
    }

    private const val NANOS_PER_MS = 1_000_000.0
    private const val NANOS_PER_SECOND = 1_000_000_000.0
    private const val PERIOD_TOLERANCE = 0.001
}
