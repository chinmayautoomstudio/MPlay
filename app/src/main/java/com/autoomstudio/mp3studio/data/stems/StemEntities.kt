package com.autoomstudio.mp3studio.data.stems

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
    /** [com.autoomstudio.mp3studio.data.duplicates.FileFingerprinter] hash of the source; null if it couldn't be read. */
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
    /** The `jobRef` of this job's AI Vocal Separator use on the server; null for jobs queued before usage limits. */
    val usageRef: String? = null,
    /** The account that reserved [usageRef]. */
    val usageUserId: String? = null,
)

/**
 * How a job with a [SeparationJobEntity.usageRef] ended, waiting to reach the server. Kept apart from the job so
 * clearing the queue doesn't lose it.
 */
@Entity(tableName = "usage_reports")
data class UsageReportEntity(
    @PrimaryKey val jobRef: String,
    val userId: String,
    /** `completed` or `released`, as the server names them. */
    val outcome: String,
    val createdAt: Long,
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
