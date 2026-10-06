package com.autoomstudio.mplay.data.tempo

import androidx.room.Dao
import androidx.room.Entity
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.PrimaryKey
import androidx.room.Query

/** A song's detected tempo (MT10); the size and date of the analyzed file tell when it has changed. */
@Entity(tableName = "song_tempos")
data class SongTempoEntity(
    @PrimaryKey val songId: Long,
    val sizeBytes: Long,
    val dateModified: Long,
    val bpm: Double,
    /** A [com.autoomstudio.mplay.metronome.TempoConfidence] name. */
    val confidence: String,
)

@Dao
interface TempoDao {
    @Query("SELECT * FROM song_tempos WHERE songId = :songId")
    suspend fun tempo(songId: Long): SongTempoEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(entry: SongTempoEntity)

    @Query("DELETE FROM song_tempos WHERE songId = :songId")
    suspend fun delete(songId: Long)
}
