package com.autoomstudio.mplay.data.model

data class Artist(
    val name: String,
    /** Sorted by title. */
    val albums: List<Album>,
    /** Sorted by album, then track. */
    val songs: List<Song>,
)
