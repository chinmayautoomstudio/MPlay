package com.autoomstudio.mp3studio.data.model

import androidx.compose.runtime.Immutable

@Immutable
data class Artist(
    val name: String,
    /** Sorted by title. */
    val albums: List<Album>,
    /** Song ids sorted by album, then track. Resolve against the library when full [Song]s are needed. */
    val songIds: List<Long>,
) {
    val songCount: Int get() = songIds.size
}
