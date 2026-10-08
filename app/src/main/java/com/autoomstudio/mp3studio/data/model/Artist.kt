package com.autoomstudio.mp3studio.data.model

data class Artist(
    val name: String,
    /** Sorted by title. */
    val albums: List<Album>,
    /** Sorted by album, then track. */
    val songs: List<Song>,
)
