package com.autoomstudio.mp3studio.data.playlist

import com.autoomstudio.mp3studio.data.model.Playlist
import com.autoomstudio.mp3studio.data.model.Song
import com.autoomstudio.mp3studio.data.model.StoredPlaylist

object PlaylistQueries {

    /**
     * Keeps the stored order and skips IDs missing from [songsById]. Each ID is first mapped through
     * [canonicalId], so an entry for a hidden duplicate shows its kept copy, once.
     */
    fun resolvePlaylist(
        stored: StoredPlaylist,
        songsById: Map<Long, Song>,
        canonicalId: (Long) -> Long = { it },
    ): Playlist {
        val songs = LinkedHashMap<Long, Song>()
        val entryIds = mutableMapOf<Long, MutableList<Long>>()
        var unavailable = 0
        for (storedId in stored.songIds) {
            val song = songsById[canonicalId(storedId)]
            if (song == null) {
                unavailable++
                continue
            }
            songs.putIfAbsent(song.id, song)
            entryIds.getOrPut(song.id) { mutableListOf() } += storedId
        }
        return Playlist(
            id = stored.id,
            name = stored.name,
            songs = songs.values.toList(),
            unavailableCount = unavailable,
            entryIds = entryIds.filter { (songId, ids) -> ids != listOf(songId) },
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
