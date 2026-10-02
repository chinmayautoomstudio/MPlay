package com.autoomstudio.mplay.data.playlist

import com.autoomstudio.mplay.data.model.Playlist
import com.autoomstudio.mplay.data.model.Song
import com.autoomstudio.mplay.data.model.StoredPlaylist

object PlaylistQueries {

    /** Keeps the stored order and skips IDs missing from [songsById]. */
    fun resolvePlaylist(stored: StoredPlaylist, songsById: Map<Long, Song>): Playlist {
        val songs = stored.songIds.mapNotNull { songsById[it] }
        return Playlist(
            id = stored.id,
            name = stored.name,
            songs = songs,
            unavailableCount = stored.songIds.size - songs.size,
        )
    }

    /**
     * Applies [visibleOrder], a reordering of the available songs, to [allIds].
     * Unavailable IDs keep their slots, so reordering never drops a song whose file may come back.
     */
    fun applyVisibleOrder(allIds: List<Long>, visibleOrder: List<Long>): List<Long> {
        val visible = visibleOrder.toSet()
        val next = visibleOrder.iterator()
        val result = allIds.map { id -> if (id in visible && next.hasNext()) next.next() else id }
        val placed = result.toSet()
        return result + visibleOrder.filterNot { it in placed }
    }

    /** [list] with the item at [from] moved to [to]. */
    fun <T> moveItem(list: List<T>, from: Int, to: Int): List<T> {
        if (from == to || from !in list.indices || to !in list.indices) return list
        return list.toMutableList().apply { add(to, removeAt(from)) }
    }
}
