package com.autoomstudio.mp3studio.data.stems

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import kotlinx.coroutines.flow.Flow

@Dao
abstract class StemDao {

    @Query("SELECT * FROM stem_sets ORDER BY createdAt DESC")
    abstract fun observeStemSets(): Flow<List<StemSetEntity>>

    @Query("SELECT * FROM stem_sets")
    abstract suspend fun stemSets(): List<StemSetEntity>

    @Query("SELECT * FROM stem_sets WHERE songId = :songId")
    abstract suspend fun stemSet(songId: Long): StemSetEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    abstract suspend fun upsertStemSet(entry: StemSetEntity)

    @Query("DELETE FROM stem_sets WHERE songId IN (:songIds)")
    abstract suspend fun deleteStemSets(songIds: List<Long>)

    @Query("DELETE FROM stem_sets")
    abstract suspend fun deleteAllStemSets()

    @Query("SELECT * FROM separation_jobs ORDER BY enqueuedAt, id")
    abstract fun observeJobs(): Flow<List<SeparationJobEntity>>

    @Query("SELECT state FROM separation_jobs WHERE id = :id")
    abstract fun observeJobState(id: Long): Flow<String?>

    @Query("SELECT * FROM separation_jobs WHERE id = :id")
    abstract suspend fun job(id: Long): SeparationJobEntity?

    @Query("SELECT * FROM separation_jobs WHERE state = 'Queued' ORDER BY enqueuedAt, id LIMIT 1")
    abstract suspend fun nextQueued(): SeparationJobEntity?

    @Query("SELECT COUNT(*) FROM separation_jobs WHERE state = 'Queued'")
    abstract suspend fun queuedCount(): Int

    @Query("SELECT songId FROM separation_jobs WHERE state IN ('Queued', 'Running')")
    abstract suspend fun activeSongIds(): List<Long>

    @Insert
    abstract suspend fun insertJobs(jobs: List<SeparationJobEntity>)

    @Query(
        "UPDATE separation_jobs SET state = 'Running', progress = 0, remainingMs = NULL, pauseReason = NULL, " +
            "error = NULL WHERE id = :id AND state = 'Queued'",
    )
    abstract suspend fun markRunning(id: Long): Int

    /** [pauseReason] on a running job means it is briefly waiting, for example for the phone to cool down. */
    @Query(
        "UPDATE separation_jobs SET progress = :progress, remainingMs = :remainingMs, pauseReason = :pauseReason " +
            "WHERE id = :id AND state = 'Running'",
    )
    abstract suspend fun updateProgress(id: Long, progress: Float, remainingMs: Long?, pauseReason: String?)

    /** Puts a running job back in the queue, for example when the system stopped the worker. */
    @Query(
        "UPDATE separation_jobs SET state = 'Queued', progress = 0, remainingMs = NULL, pauseReason = :reason " +
            "WHERE id = :id AND state = 'Running'",
    )
    abstract suspend fun requeue(id: Long, reason: String?)

    /** Any job still marked running when a worker starts was interrupted by a kill or restart. */
    @Query(
        "UPDATE separation_jobs SET state = 'Queued', progress = 0, remainingMs = NULL, pauseReason = :reason " +
            "WHERE state = 'Running'",
    )
    abstract suspend fun requeueAllRunning(reason: String)

    @Query("UPDATE separation_jobs SET pauseReason = :reason WHERE state = 'Queued'")
    abstract suspend fun setQueuedPauseReason(reason: String?)

    @Query("UPDATE separation_jobs SET state = 'Done', progress = 1, remainingMs = NULL, finishedAt = :now WHERE id = :id")
    abstract suspend fun markDone(id: Long, now: Long)

    @Query(
        "UPDATE separation_jobs SET state = 'Failed', remainingMs = NULL, error = :error, finishedAt = :now " +
            "WHERE id = :id AND state IN ('Queued', 'Running')",
    )
    abstract suspend fun markFailed(id: Long, error: String, now: Long)

    @Query(
        "UPDATE separation_jobs SET state = 'Failed', remainingMs = NULL, error = :error, finishedAt = :now " +
            "WHERE state IN ('Queued', 'Running')",
    )
    abstract suspend fun failAllActive(error: String, now: Long)

    @Query(
        "UPDATE separation_jobs SET state = 'Cancelled', remainingMs = NULL, finishedAt = :now " +
            "WHERE id = :id AND state IN ('Queued', 'Running')",
    )
    abstract suspend fun cancel(id: Long, now: Long)

    @Query(
        "UPDATE separation_jobs SET state = 'Queued', progress = 0, error = NULL, pauseReason = NULL, " +
            "finishedAt = NULL, enqueuedAt = :now WHERE id = :id AND state IN ('Failed', 'Cancelled')",
    )
    abstract suspend fun retry(id: Long, now: Long)

    @Query("DELETE FROM separation_jobs WHERE state IN ('Done', 'Failed', 'Cancelled')")
    abstract suspend fun clearFinished()

    @Query("DELETE FROM separation_jobs WHERE id = :id AND state IN ('Done', 'Failed', 'Cancelled')")
    abstract suspend fun removeFinished(id: Long)

    /** Records a finished separation and its job in one step, so a stem set never exists without its job done. */
    @Transaction
    open suspend fun complete(jobId: Long, stemSet: StemSetEntity, now: Long) {
        upsertStemSet(stemSet)
        markDone(jobId, now)
    }
}
