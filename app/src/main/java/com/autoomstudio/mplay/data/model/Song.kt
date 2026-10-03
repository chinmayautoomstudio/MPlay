package com.autoomstudio.mplay.data.model

import android.net.Uri

data class Song(
    val id: Long,
    val uri: Uri,
    val title: String,
    val artist: String,
    val album: String,
    val albumId: Long,
    val durationMs: Long,
    val dateAdded: Long,
    val albumArtUri: Uri,
    /** Position within its album disc; 0 when the file has no track tag. */
    val trackNumber: Int = 0,
    val sizeBytes: Long = 0L,
    /** Seconds since the epoch, as MediaStore reports it. */
    val dateModified: Long = 0L,
    val mimeType: String = "",
    /** Bits per second; 0 when unknown. */
    val bitrate: Int = 0,
)
