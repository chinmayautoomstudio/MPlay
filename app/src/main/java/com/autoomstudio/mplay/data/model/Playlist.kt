package com.autoomstudio.mplay.data.model

/** A playlist as stored: song IDs in order, some of which may no longer exist on the device. */
data class StoredPlaylist(
    val id: Long,
    val name: String,
    val songIds: List<Long>,
)

/** A playlist resolved against the library; [unavailableCount] songs were skipped because their files are gone. */
data class Playlist(
    val id: Long,
    val name: String,
    val songs: List<Song>,
    val unavailableCount: Int,
)
