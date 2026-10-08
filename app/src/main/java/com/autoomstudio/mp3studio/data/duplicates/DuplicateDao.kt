package com.autoomstudio.mp3studio.data.duplicates

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import kotlinx.coroutines.flow.Flow

@Dao
abstract class DuplicateDao {

    @Query("SELECT * FROM song_fingerprints")
    abstract fun observeFingerprints(): Flow<List<SongFingerprintEntity>>

    @Query("SELECT * FROM song_fingerprints")
    abstract suspend fun fingerprints(): List<SongFingerprintEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    abstract suspend fun upsertFingerprints(entries: List<SongFingerprintEntity>)

    @Query("DELETE FROM song_fingerprints WHERE songId IN (:songIds)")
    abstract suspend fun deleteFingerprints(songIds: List<Long>)

    @Query("SELECT * FROM duplicate_overrides")
    abstract fun observeOverrides(): Flow<List<DuplicateOverrideEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    protected abstract suspend fun upsertOverride(entry: DuplicateOverrideEntity)

    @Query("DELETE FROM duplicate_overrides WHERE songId IN (:songIds)")
    protected abstract suspend fun deleteOverrides(songIds: List<Long>)

    @Query("DELETE FROM duplicate_overrides WHERE songId IN (:songIds) AND kind = :kind")
    protected abstract suspend fun deleteOverridesOfKind(songIds: List<Long>, kind: String)

    /** Makes [songId] the shown copy of its group, replacing any earlier pick among [groupIds]. */
    @Transaction
    open suspend fun keep(songId: Long, groupIds: List<Long>) {
        deleteOverridesOfKind(groupIds, DuplicateOverrideKind.Keep.name)
        deleteOverrides(listOf(songId))
        upsertOverride(DuplicateOverrideEntity(songId, DuplicateOverrideKind.Keep.name))
    }

    open suspend fun restore(songId: Long) {
        upsertOverride(DuplicateOverrideEntity(songId, DuplicateOverrideKind.Restore.name))
    }

    open suspend fun hideAgain(songId: Long) {
        deleteOverridesOfKind(listOf(songId), DuplicateOverrideKind.Restore.name)
    }
}
