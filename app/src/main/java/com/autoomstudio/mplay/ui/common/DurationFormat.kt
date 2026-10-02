package com.autoomstudio.mplay.ui.common

import java.util.Locale

/** Formats milliseconds as `m:ss`, or `h:mm:ss` for an hour or longer. */
fun formatDuration(durationMs: Long): String {
    val totalSeconds = (durationMs.coerceAtLeast(0L)) / 1000
    val hours = totalSeconds / 3600
    val minutes = (totalSeconds % 3600) / 60
    val seconds = totalSeconds % 60
    return if (hours > 0) {
        String.format(Locale.ROOT, "%d:%02d:%02d", hours, minutes, seconds)
    } else {
        String.format(Locale.ROOT, "%d:%02d", minutes, seconds)
    }
}

/** Formats milliseconds as `m:ss.s`, for trim positions that move in tenths of a second. */
fun formatPreciseDuration(durationMs: Long): String {
    val tenths = durationMs.coerceAtLeast(0L) / 100
    val minutes = tenths / 600
    val seconds = (tenths % 600) / 10
    return String.format(Locale.ROOT, "%d:%02d.%d", minutes, seconds, tenths % 10)
}
