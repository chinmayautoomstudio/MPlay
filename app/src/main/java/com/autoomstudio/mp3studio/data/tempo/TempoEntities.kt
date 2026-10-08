package com.autoomstudio.mp3studio.data.tempo

import androidx.room.ColumnInfo
import androidx.room.Dao
import androidx.room.Entity
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.PrimaryKey
import androidx.room.Query

/**
 * A song's detected tempo (MT10), time signature (MT20) and beat grid; the size and date of the analyzed file tell
 * when it has changed. Rows written before the time signature existed have a null [meterConfidence], and rows
 * written before the beat grid existed have a null [beatPeriodMs].
 */
@Entity(tableName = "song_tempos")
data class SongTempoEntity(
    @PrimaryKey val songId: Long,
    val sizeBytes: Long,
    val dateModified: Long,
    val bpm: Double,
    /** A [com.autoomstudio.mp3studio.metronome.TempoConfidence] name. */
    val confidence: String,
    @ColumnInfo(defaultValue = "NULL") val beatsPerBar: Int? = null,
    @ColumnInfo(defaultValue = "NULL") val beatUnit: Int? = null,
    /** A TempoConfidence name, [TempoCache.NO_METER] when the meter couldn't be told, null when never analyzed. */
    @ColumnInfo(defaultValue = "NULL") val meterConfidence: String? = null,
    /** The BPM the metronome clicks with this meter; for 6/8 that is the eighth-note rate. */
    @ColumnInfo(defaultValue = "NULL") val meterBpm: Int? = null,
    /** Song time of a downbeat, for following the song (schema 6). */
    @ColumnInfo(defaultValue = "NULL") val downbeatMs: Double? = null,
    /** Time between clicks; [TempoCache.NO_GRID] when the beats couldn't be placed, null when never analyzed. */
    @ColumnInfo(defaultValue = "NULL") val beatPeriodMs: Double? = null,
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
