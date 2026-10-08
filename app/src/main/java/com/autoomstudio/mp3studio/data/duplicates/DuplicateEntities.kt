package com.autoomstudio.mp3studio.data.duplicates

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * Cached content fingerprint of one file. It is valid while [sizeBytes] and [dateModified] still match
 * MediaStore, so only new or changed files are read again.
 */
@Entity(tableName = "song_fingerprints")
data class SongFingerprintEntity(
    @PrimaryKey val songId: Long,
    val sizeBytes: Long,
    val dateModified: Long,
    val hash: String,
)

/** The user's choice for one duplicate copy; see [DuplicateOverrideKind]. */
@Entity(tableName = "duplicate_overrides")
data class DuplicateOverrideEntity(
    @PrimaryKey val songId: Long,
    val kind: String,
)

enum class DuplicateOverrideKind {
    /** Show this copy instead of the automatic pick. */
    Keep,

    /** Show this extra copy as well. */
    Restore,
}
