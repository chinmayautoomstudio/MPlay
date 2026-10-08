package com.autoomstudio.mp3studio.data.playlist

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import kotlinx.coroutines.flow.Flow

@Dao
abstract class PlaylistDao {

    @Query("SELECT * FROM playlists ORDER BY name COLLATE NOCASE, id")
    abstract fun observePlaylists(): Flow<List<PlaylistEntity>>

    @Query("SELECT * FROM playlist_songs ORDER BY playlistId, position")
    abstract fun observeEntries(): Flow<List<PlaylistSongEntity>>

    @Insert
    abstract suspend fun insert(playlist: PlaylistEntity): Long

    @Query("UPDATE playlists SET name = :name, updatedAt = :now WHERE id = :id")
    abstract suspend fun rename(id: Long, name: String, now: Long)

    @Query("DELETE FROM playlists WHERE id = :id")
    abstract suspend fun delete(id: Long)

    @Query("DELETE FROM playlist_songs WHERE playlistId = :playlistId AND songId IN (:songIds)")
    protected abstract suspend fun deleteEntries(playlistId: Long, songIds: List<Long>)

    /** Drops songs that no longer exist on the device from every playlist. */
    @Query("DELETE FROM playlist_songs WHERE songId IN (:songIds)")
    abstract suspend fun removeSongsEverywhere(songIds: List<Long>)

    @Query("SELECT songId FROM playlist_songs WHERE playlistId = :playlistId ORDER BY position")
    abstract suspend fun songIds(playlistId: Long): List<Long>

    @Query("SELECT COALESCE(MAX(position), -1) FROM playlist_songs WHERE playlistId = :playlistId")
    protected abstract suspend fun lastPosition(playlistId: Long): Int

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    protected abstract suspend fun insertEntries(entries: List<PlaylistSongEntity>)

    @Query("DELETE FROM playlist_songs WHERE playlistId = :playlistId")
    protected abstract suspend fun clearEntries(playlistId: Long)

    @Query("UPDATE playlists SET updatedAt = :now WHERE id = :id")
    protected abstract suspend fun touch(id: Long, now: Long)

    /** Appends the songs not already in the playlist and returns how many were added. */
    @Transaction
    open suspend fun addSongs(playlistId: Long, songIds: List<Long>, now: Long): Int {
        val existing = songIds(playlistId).toSet()
        val added = songIds.distinct().filterNot { it in existing }
        if (added.isEmpty()) return 0
        val start = lastPosition(playlistId) + 1
        insertEntries(added.mapIndexed { i, id -> PlaylistSongEntity(playlistId, id, start + i) })
        touch(playlistId, now)
        return added.size
    }

    @Transaction
    open suspend fun removeSongs(playlistId: Long, songIds: List<Long>, now: Long) {
        deleteEntries(playlistId, songIds)
        touch(playlistId, now)
    }

    /** Reorders the available songs to [visibleOrder]; see [PlaylistQueries.applyVisibleOrder]. */
    @Transaction
    open suspend fun reorder(playlistId: Long, visibleOrder: List<Long>, now: Long) {
        val ordered = PlaylistQueries.applyVisibleOrder(songIds(playlistId), visibleOrder)
        clearEntries(playlistId)
        insertEntries(ordered.mapIndexed { i, id -> PlaylistSongEntity(playlistId, id, i) })
        touch(playlistId, now)
    }
}
