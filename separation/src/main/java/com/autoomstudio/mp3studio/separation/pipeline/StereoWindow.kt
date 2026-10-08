package com.autoomstudio.mp3studio.separation.pipeline

/**
 * A sliding stereo window over a stream of known [totalFrames]: frames are appended in order, read back by
 * absolute position (zero outside the track) and dropped once no later read needs them.
 */
internal class StereoWindow(private val totalFrames: Long, initialCapacity: Int) {
    private var left = FloatArray(initialCapacity)
    private var right = FloatArray(initialCapacity)

    /** Absolute position of `left[0]`. */
    private var start = 0L
    private var count = 0

    /** Absolute position just past the last stored frame. */
    val end: Long get() = start + count

    /** Appends up to the track end, applying `(x - mean) / std`. */
    fun append(interleaved: FloatArray, frames: Int, mean: Float, std: Float) {
        val usable = minOf(frames.toLong(), totalFrames - end).toInt()
        if (usable <= 0) return
        ensureCapacity(count + usable)
        val inv = 1f / std
        for (i in 0 until usable) {
            left[count + i] = (interleaved[2 * i] - mean) * inv
            right[count + i] = (interleaved[2 * i + 1] - mean) * inv
        }
        count += usable
    }

    /** Copies `[from, from + length)` into the targets; frames before 0, past the track or not yet appended are zero. */
    fun read(from: Long, length: Int, outLeft: FloatArray, outRight: FloatArray) {
        require(maxOf(from, 0L) >= start || from + length <= 0) { "frames before $start were discarded" }
        for (i in 0 until length) {
            val p = from + i - start
            if (p in 0 until count) {
                outLeft[i] = left[p.toInt()]
                outRight[i] = right[p.toInt()]
            } else {
                outLeft[i] = 0f
                outRight[i] = 0f
            }
        }
    }

    fun discardBefore(position: Long) {
        val drop = (position - start).coerceIn(0, count.toLong()).toInt()
        if (drop == 0) return
        System.arraycopy(left, drop, left, 0, count - drop)
        System.arraycopy(right, drop, right, 0, count - drop)
        count -= drop
        start += drop
    }

    private fun ensureCapacity(needed: Int) {
        if (needed <= left.size) return
        val size = maxOf(needed, left.size * 3 / 2)
        left = left.copyOf(size)
        right = right.copyOf(size)
    }
}
