package com.autoomstudio.mplay.separation.pipeline

/**
 * Where Demucs' `apply_model(split=True)` cuts a track of [totalFrames] into model-sized windows.
 * Each segment covers `[offset, offset + length)` of the output; the model sees `[windowStart, windowStart + segment)`,
 * centred on the segment and zero-filled outside the track.
 */
class SegmentPlan(val totalFrames: Long, val segment: Int, overlap: Double) {

    init {
        require(totalFrames >= 0 && segment > 1)
        require(overlap in 0.0..0.9) { "overlap out of range: $overlap" }
    }

    val stride: Int = ((1 - overlap) * segment).toInt()

    val count: Int = if (totalFrames == 0L) 0 else ((totalFrames + stride - 1) / stride).toInt()

    fun offset(index: Int): Long = index.toLong() * stride

    fun length(index: Int): Int = minOf(segment.toLong(), totalFrames - offset(index)).toInt()

    fun windowStart(index: Int): Long = offset(index) - (segment - length(index)) / 2

    /** First output frame a later segment still adds to; everything before it is final after [index]. */
    fun finalBefore(index: Int): Long = if (index + 1 < count) offset(index + 1) else totalFrames

    /** First input frame any segment after [index] reads. */
    fun inputNeededFrom(index: Int): Long =
        if (index + 1 < count) minOf(offset(index + 1), windowStart(index + 1)) else totalFrames

    /** The triangular transition weight for position [i] of a segment, as in `apply_model` with power 1. */
    fun weight(i: Int): Float {
        val half = segment / 2
        val w = if (i < half) i + 1 else segment - i
        return w.toFloat() / maxOf(half, segment - half)
    }
}
