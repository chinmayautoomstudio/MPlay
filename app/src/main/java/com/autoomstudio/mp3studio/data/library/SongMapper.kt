package com.autoomstudio.mp3studio.data.library

object SongMapper {

    const val UNKNOWN_ARTIST = "Unknown Artist"
    const val UNKNOWN_ALBUM = "Unknown Album"

    /** MediaStore uses this placeholder when a tag is missing. */
    private const val MEDIA_STORE_UNKNOWN = "<unknown>"

    fun displayTitle(title: String?, displayName: String?): String {
        if (isKnown(title)) return title!!.trim()
        val fileName = displayName?.trim().orEmpty()
        val withoutExtension = fileName.substringBeforeLast('.', fileName)
        return withoutExtension.ifBlank { fileName }
    }

    fun displayArtist(artist: String?): String =
        if (isKnown(artist)) artist!!.trim() else UNKNOWN_ARTIST

    fun displayAlbum(album: String?): String =
        if (isKnown(album)) album!!.trim() else UNKNOWN_ALBUM

    /** MediaStore's bitrate when known (Android 11+), otherwise an average estimated from size and length. */
    fun bitrate(reported: Int, sizeBytes: Long, durationMs: Long): Int = when {
        reported > 0 -> reported
        sizeBytes > 0 && durationMs > 0 -> (sizeBytes * 8 * 1000 / durationMs).coerceAtMost(Int.MAX_VALUE.toLong()).toInt()
        else -> 0
    }

    private fun isKnown(value: String?): Boolean =
        !value.isNullOrBlank() && !value.trim().equals(MEDIA_STORE_UNKNOWN, ignoreCase = true)
}
