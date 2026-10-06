package com.autoomstudio.mplay.metronome

import kotlin.math.ceil

/**
 * Places beats on an audio frame timeline (MT14). Beat n since the last tempo change sits at
 * `anchor + n * framesPerBeat`, computed in double precision instead of accumulated, so there is no drift
 * however long it runs. A tempo change takes effect after the beat already due, so the current beat is never cut short.
 */
class BeatClock(private val sampleRate: Int, bpm: Double) {
    private var framesPerBeat = framesPerBeat(bpm)
    private var pendingFramesPerBeat: Double? = null
    private var anchorFrame = 0.0
    private var beatsSinceAnchor = 0L

    /** Beats placed so far, counting from 0. */
    var beatIndex = 0L
        private set

    fun setBpm(bpm: Double) {
        pendingFramesPerBeat = framesPerBeat(bpm)
    }

    /** Calls [onBeat] with the offset inside the block and the beat's index, for every beat in the block. */
    fun beatsIn(blockStart: Long, frames: Int, onBeat: (offset: Int, beat: Long) -> Unit) {
        val blockEnd = blockStart + frames
        while (true) {
            val beatFrame = ceil(anchorFrame + beatsSinceAnchor * framesPerBeat).toLong()
            if (beatFrame >= blockEnd) return
            onBeat((beatFrame - blockStart).coerceAtLeast(0).toInt(), beatIndex)
            beatIndex++
            beatsSinceAnchor++
            pendingFramesPerBeat?.let { next ->
                anchorFrame += (beatsSinceAnchor - 1) * framesPerBeat
                beatsSinceAnchor = 1
                framesPerBeat = next
                pendingFramesPerBeat = null
            }
        }
    }

    private fun framesPerBeat(bpm: Double): Double = sampleRate * 60.0 / bpm
}
