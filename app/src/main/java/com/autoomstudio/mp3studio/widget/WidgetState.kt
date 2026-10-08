package com.autoomstudio.mp3studio.widget

/** What the home-screen widget shows. [songId] is null when nothing is queued. */
data class WidgetState(
    val songId: Long?,
    val title: String,
    val artist: String,
    val isPlaying: Boolean,
) {
    val isIdle: Boolean get() = songId == null

    companion object {
        val Idle = WidgetState(songId = null, title = "", artist = "", isPlaying = false)
    }
}

/** Maps the player's current item to widget state; missing tags fall back like the library does. */
fun widgetStateOf(
    hasItem: Boolean,
    mediaId: String?,
    title: CharSequence?,
    artist: CharSequence?,
    isPlaying: Boolean,
    unknownTitle: String,
    unknownArtist: String,
): WidgetState {
    if (!hasItem) return WidgetState.Idle
    return WidgetState(
        songId = mediaId?.toLongOrNull() ?: UNKNOWN_SONG_ID,
        title = title?.toString()?.takeIf { it.isNotBlank() } ?: unknownTitle,
        artist = artist?.toString()?.takeIf { it.isNotBlank() } ?: unknownArtist,
        isPlaying = isPlaying,
    )
}

/** Used for queued items whose media ID isn't a MediaStore ID, so they still count as "something queued". */
const val UNKNOWN_SONG_ID = -1L
