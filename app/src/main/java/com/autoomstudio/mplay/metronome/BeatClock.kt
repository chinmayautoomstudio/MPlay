package com.autoomstudio.mplay.metronome

import kotlin.math.ceil

/**
 * Places beats on an audio frame timeline (MT14). Beat n since the last tempo change sits at
 * `anchor + n * framesPerBeat`, computed in double precision instead of accumulated, so there is no drift
 * however long it runs. A tempo change takes effect after the beat already due, so the current beat is never cut short.
 *
 * [align] puts the beats on a song's grid instead: the given beat numbering and period replace the tempo until
 * [unalign], which carries on at the tempo from the last beat played.
 */
class BeatClock(private val sampleRate: Int, bpm: Double) {
    private var framesPerBeat = framesPerBeat(bpm)
    private var pendingFramesPerBeat: Double? = null
    private var anchorFrame = 0.0
    private var beatsSinceAnchor = 0L
    private var lastBeatFrame: Long? = null

    /** The index the next beat gets. Counts from 0, or follows the song's beat numbers while aligned. */
    var beatIndex = 0L
        private set

    var aligned = false
        private set

    fun setBpm(bpm: Double) {
        pendingFramesPerBeat = framesPerBeat(bpm)
    }

    /**
     * From [fromFrame] on, beat `firstBeat + n` sits at `anchorFrame + n * framesPerBeat`. A grid beat that would
     * follow the last click by less than [MIN_GAP] of a beat is skipped, so re-aligning never double-clicks.
     */
    fun align(anchorFrame: Double, framesPerBeat: Double, firstBeat: Long, fromFrame: Long) {
        var n = ceil((fromFrame - anchorFrame) / framesPerBeat).toLong()
        val last = lastBeatFrame
        if (last != null) {
            while (ceil(anchorFrame + n * framesPerBeat) - last < framesPerBeat * MIN_GAP) n++
        }
        this.anchorFrame = anchorFrame + n * framesPerBeat
        this.framesPerBeat = framesPerBeat
        beatsSinceAnchor = 0
        pendingFramesPerBeat = null
        beatIndex = firstBeat + n
        aligned = true
    }

    /** Goes back to free running at [bpm], one beat after the last click. */
    fun unalign(bpm: Double) {
        val period = framesPerBeat(bpm)
        val last = lastBeatFrame
        if (last != null) {
            anchorFrame = last.toDouble()
            beatsSinceAnchor = 1
        }
        framesPerBeat = period
        pendingFramesPerBeat = null
        aligned = false
    }

    /** Calls [onBeat] with the offset inside the block and the beat's index, for every beat in the block. */
    fun beatsIn(blockStart: Long, frames: Int, onBeat: (offset: Int, beat: Long) -> Unit) {
        val blockEnd = blockStart + frames
        while (true) {
            val beatFrame = ceil(anchorFrame + beatsSinceAnchor * framesPerBeat).toLong()
            if (beatFrame >= blockEnd) return
            onBeat((beatFrame - blockStart).coerceAtLeast(0).toInt(), beatIndex)
            lastBeatFrame = maxOf(beatFrame, blockStart)
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

    private companion object {
        const val MIN_GAP = 0.4
    }
}
