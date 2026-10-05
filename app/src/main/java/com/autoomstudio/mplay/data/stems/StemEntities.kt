package com.autoomstudio.mplay.data.stems

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * Separated vocals and instrumental of one song, in app-private storage. Valid while the source still matches
 * [sourceSizeBytes], [sourceDateModified] and, when those changed, [fingerprint].
 */
@Entity(tableName = "stem_sets")
data class StemSetEntity(
    @PrimaryKey val songId: Long,
    val title: String,
    val artist: String,
    val sourceSizeBytes: Long,
    val sourceDateModified: Long,
    /** [com.autoomstudio.mplay.data.duplicates.FileFingerprinter] hash of the source; null if it couldn't be read. */
    val fingerprint: String?,
    val vocalsPath: String,
    val instrumentalPath: String,
    val bytes: Long,
    val createdAt: Long,
)

/** One song waiting for, undergoing or finished with separation. */
@Entity(tableName = "separation_jobs", indices = [Index("songId"), Index("state")])
data class SeparationJobEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val songId: Long,
    val title: String,
    val artist: String,
    val sourceUri: String,
    val durationMs: Long,
    val sourceSizeBytes: Long,
    val sourceDateModified: Long,
    /** A [JobState] name. */
    val state: String,
    val progress: Float = 0f,
    val remainingMs: Long? = null,
    /** A [PauseReason] name while queued after an automatic pause. */
    val pauseReason: String? = null,
    /** A [JobError] name when [state] is Failed. */
    val error: String? = null,
    val enqueuedAt: Long,
    val finishedAt: Long? = null,
)

enum class JobState { Queued, Running, Done, Failed, Cancelled }

enum class PauseReason { Charging, Battery, Heat, TimeLimit, Interrupted, NeedsApp }

enum class JobError {
    SourceMissing,
    UnsupportedFormat,
    CorruptFile,
    OutOfMemory,
    LowStorage,
    ModelUnavailable,
    ModelFailed,
    Unknown,
}
