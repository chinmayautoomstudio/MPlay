package com.autoomstudio.mplay.data.playlist

import com.autoomstudio.mplay.data.model.Song
import com.autoomstudio.mplay.data.model.StoredPlaylist
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine

class PlaylistRepository(
    private val dao: PlaylistDao,
    private val clock: () -> Long = System::currentTimeMillis,
) {

    /** All playlists sorted by name, each with its song IDs in order. */
    val playlists: Flow<List<StoredPlaylist>> =
        combine(dao.observePlaylists(), dao.observeEntries()) { playlists, entries ->
            val idsByPlaylist = entries.groupBy({ it.playlistId }, { it.songId })
            playlists.map { StoredPlaylist(it.id, it.name, idsByPlaylist[it.id].orEmpty()) }
        }

    suspend fun create(name: String): Long {
        val trimmed = name.trim()
        require(trimmed.isNotEmpty()) { "Playlist name must not be blank" }
        val now = clock()
        return dao.insert(PlaylistEntity(name = trimmed, createdAt = now, updatedAt = now))
    }

    suspend fun rename(id: Long, name: String) {
        val trimmed = name.trim()
        require(trimmed.isNotEmpty()) { "Playlist name must not be blank" }
        dao.rename(id, trimmed, clock())
    }

    suspend fun delete(id: Long) = dao.delete(id)

    /** Returns how many of [songs] were added; songs already in the playlist are skipped. */
    suspend fun addSongs(id: Long, songs: List<Song>): Int = dao.addSongs(id, songs.map { it.id }, clock())

    suspend fun remove(id: Long, song: Song) = dao.removeSong(id, song.id, clock())

    suspend fun reorder(id: Long, visibleOrder: List<Song>) = dao.reorder(id, visibleOrder.map { it.id }, clock())
}
