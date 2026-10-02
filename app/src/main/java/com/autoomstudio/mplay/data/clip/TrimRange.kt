package com.autoomstudio.mplay.data.clip

/** A selection within a track of [durationMs], always at least [MIN_CLIP_MS] long. */
data class TrimRange(val startMs: Long, val endMs: Long, val durationMs: Long) {

    val lengthMs: Long get() = endMs - startMs

    /** Longer than most phones will play as a ringtone before the call goes to voicemail. */
    val isLongForRingtone: Boolean get() = lengthMs > LONG_RINGTONE_MS

    val isFullTrack: Boolean get() = startMs == 0L && endMs == durationMs

    fun moveStart(toMs: Long): TrimRange =
        copy(startMs = toMs.coerceIn(0L, (endMs - minLength).coerceAtLeast(0L)))

    fun moveEnd(toMs: Long): TrimRange =
        copy(endMs = toMs.coerceIn((startMs + minLength).coerceAtMost(durationMs), durationMs))

    fun nudgeStart(forward: Boolean): TrimRange = moveStart(startMs + step(forward))

    fun nudgeEnd(forward: Boolean): TrimRange = moveEnd(endMs + step(forward))

    fun fullTrack(): TrimRange = copy(startMs = 0L, endMs = durationMs)

    /** Tracks shorter than the minimum can only be used whole. */
    private val minLength: Long get() = MIN_CLIP_MS.coerceAtMost(durationMs)

    private fun step(forward: Boolean) = if (forward) NUDGE_MS else -NUDGE_MS

    companion object {
        const val MIN_CLIP_MS = 1_000L
        const val NUDGE_MS = 100L
        const val RINGTONE_DEFAULT_MS = 30_000L
        const val LONG_RINGTONE_MS = 40_000L

        fun full(durationMs: Long): TrimRange {
            val duration = durationMs.coerceAtLeast(0L)
            return TrimRange(0L, duration, duration)
        }

        fun ringtoneDefault(durationMs: Long): TrimRange {
            val duration = durationMs.coerceAtLeast(0L)
            return TrimRange(0L, duration.coerceAtMost(RINGTONE_DEFAULT_MS), duration)
        }
    }
}
