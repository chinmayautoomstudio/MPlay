package com.autoomstudio.mplay.data.stems

import android.content.Context
import android.net.Uri
import android.util.Log
import com.autoomstudio.mplay.data.duplicates.FileFingerprinter
import com.autoomstudio.mplay.data.model.Song
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.File

/**
 * Separated stems in `filesDir/stems/<songId>/` and the processing queue. Stems stay playable and deletable on
 * phones that can't make new ones.
 */
class StemRepository(
    context: Context,
    private val dao: StemDao,
    private val fingerprinter: FileFingerprinter,
    private val scope: CoroutineScope,
    private val clock: () -> Long = System::currentTimeMillis,
) {
    private val stemsDir = File(context.filesDir, STEMS_DIR)
    private val workRoot = File(stemsDir, WORK_DIR)
    private val reconcileMutex = Mutex()
    private var reconcileJob: Job? = null

    /** Stem sets by song id; read synchronously by the playback service when it resolves URIs. */
    val stemSets: StateFlow<Map<Long, StemSetEntity>> = dao.observeStemSets()
        .map { sets -> sets.associateBy { it.songId } }
        .stateIn(scope, SharingStarted.Eagerly, emptyMap())

    val readySongIds: Flow<Set<Long>> = stemSets.map { it.keys }.distinctUntilChanged()

    val cacheBytes: Flow<Long> = stemSets.map { sets -> sets.values.sumOf { it.bytes } }.distinctUntilChanged()

    val jobs: Flow<List<SeparationJobEntity>> = dao.observeJobs()

    /** Null for [StemMode.Original] or when the song has no usable stems, meaning: play the original file. */
    fun stemUri(songId: Long, mode: StemMode): Uri? {
        val set = stemSets.value[songId] ?: return null
        val path = when (mode) {
            StemMode.Original -> return null
            StemMode.Instrumental -> set.instrumentalPath
            StemMode.Vocals -> set.vocalsPath
        }
        return Uri.fromFile(File(path))
    }

    /** Queues [songs] that aren't already separated or waiting. Returns how many were added. */
    suspend fun enqueue(songs: List<Song>): Int {
        val ready = stemSets.value.keys
        val active = dao.activeSongIds().toSet()
        val now = clock()
        val jobs = songs.distinctBy { it.id }
            .filter { it.id !in ready && it.id !in active }
            .map { song ->
                SeparationJobEntity(
                    songId = song.id,
                    title = song.title,
                    artist = song.artist,
                    sourceUri = song.uri.toString(),
                    durationMs = song.durationMs,
                    sourceSizeBytes = song.sizeBytes,
                    sourceDateModified = song.dateModified,
                    state = JobState.Queued.name,
                    enqueuedAt = now,
                )
            }
        if (jobs.isNotEmpty()) dao.insertJobs(jobs)
        return jobs.size
    }

    suspend fun cancel(jobId: Long) = dao.cancel(jobId, clock())

    suspend fun retry(jobId: Long) = dao.retry(jobId, clock())

    suspend fun removeFinished(jobId: Long) = dao.removeFinished(jobId)

    suspend fun clearFinished() = dao.clearFinished()

    suspend fun hasQueued(): Boolean = dao.queuedCount() > 0

    suspend fun queuedCount(): Int = dao.queuedCount()

    /** For when separation became unavailable with jobs waiting, for example a model that is no longer installed. */
    suspend fun failQueued(error: JobError) = dao.failAllActive(error.name, clock())

    /** Removes the database row first, so playback stops picking the files before they disappear. */
    suspend fun delete(songIds: List<Long>) {
        if (songIds.isEmpty()) return
        val sets = songIds.mapNotNull { stemSets.value[it] }
        dao.deleteStemSets(songIds)
        withContext(Dispatchers.IO) { sets.forEach(::deleteFiles) }
    }

    suspend fun deleteAll() {
        val sets = dao.stemSets()
        dao.deleteAllStemSets()
        withContext(Dispatchers.IO) {
            sets.forEach(::deleteFiles)
            stemsDir.listFiles()?.filter { it.name != WORK_DIR }?.forEach { it.deleteRecursively() }
        }
    }

    /**
     * Drops stems whose source was deleted or changed, and re-attaches stems whose source moved (new song id, same
     * content). Call with the full, unfiltered library after each refresh; an empty list is ignored so a failed
     * scan can't wipe the cache.
     */
    suspend fun reconcile(songs: List<Song>) = reconcileMutex.withLock {
        if (songs.isEmpty()) return@withLock
        withContext(Dispatchers.IO) {
            val byId = songs.associateBy { it.id }
            val sets = dao.stemSets()
            val claimed = sets.map { it.songId }.filter { it in byId }.toMutableSet()
            val stale = mutableListOf<StemSetEntity>()
            for (set in sets) {
                val song = byId[set.songId]
                when {
                    song == null -> {
                        val moved = findMoved(set, songs, claimed)
                        if (moved != null) {
                            claimed += moved.id
                            dao.deleteStemSets(listOf(set.songId))
                            dao.upsertStemSet(set.copy(songId = moved.id, title = moved.title, artist = moved.artist))
                        } else {
                            stale += set
                        }
                    }
                    song.sizeBytes == set.sourceSizeBytes && song.dateModified == set.sourceDateModified -> Unit
                    set.fingerprint != null && fingerprinter.fingerprint(song.uri) == set.fingerprint ->
                        dao.upsertStemSet(
                            set.copy(sourceSizeBytes = song.sizeBytes, sourceDateModified = song.dateModified),
                        )
                    else -> stale += set
                }
            }
            if (stale.isNotEmpty()) {
                Log.i(TAG, "Removing ${stale.size} stem sets whose source changed or disappeared")
                dao.deleteStemSets(stale.map { it.songId })
                stale.forEach(::deleteFiles)
            }
        }
    }

    /** Runs [reconcile] in the background once the library has settled; later calls replace earlier ones. */
    fun onLibraryChanged(songs: List<Song>) {
        reconcileJob?.cancel()
        reconcileJob = scope.launch {
            delay(RECONCILE_DELAY_MS)
            reconcile(songs)
        }
    }

    fun fingerprint(uri: Uri): String? = fingerprinter.fingerprint(uri)

    private fun findMoved(set: StemSetEntity, songs: List<Song>, claimed: Set<Long>): Song? {
        val fingerprint = set.fingerprint ?: return null
        return songs.asSequence()
            .filter { it.id !in claimed && it.sizeBytes == set.sourceSizeBytes }
            .firstOrNull { fingerprinter.fingerprint(it.uri) == fingerprint }
    }

    // The functions below are for the worker, which runs one job at a time.

    suspend fun nextQueued(): SeparationJobEntity? = dao.nextQueued()

    suspend fun job(jobId: Long): SeparationJobEntity? = dao.job(jobId)

    fun observeJobState(jobId: Long): Flow<JobState?> =
        dao.observeJobState(jobId).map { name -> JobState.entries.firstOrNull { it.name == name } }

    suspend fun markRunning(jobId: Long): Boolean = dao.markRunning(jobId) > 0

    suspend fun updateProgress(jobId: Long, progress: Float, remainingMs: Long?, pauseReason: PauseReason? = null) =
        dao.updateProgress(jobId, progress, remainingMs, pauseReason?.name)

    suspend fun requeue(jobId: Long, reason: PauseReason?) = dao.requeue(jobId, reason?.name)

    suspend fun setQueuedPauseReason(reason: PauseReason?) = dao.setQueuedPauseReason(reason?.name)

    suspend fun markFailed(jobId: Long, error: JobError) = dao.markFailed(jobId, error.name, clock())

    /**
     * Run when a worker starts: jobs left running were interrupted and start over, and anything on disk without
     * a database row (unfinished work folders, folders renamed just before a crash) is removed.
     */
    suspend fun recoverInterrupted() {
        dao.requeueAllRunning(PauseReason.Interrupted.name)
        val known = dao.stemSets().map { File(it.vocalsPath).parentFile?.canonicalPath }.toSet()
        withContext(Dispatchers.IO) {
            workRoot.deleteRecursively()
            stemsDir.listFiles()
                ?.filter { it.name != WORK_DIR && it.canonicalPath !in known }
                ?.forEach { it.deleteRecursively() }
        }
    }

    fun workDir(jobId: Long): File = File(workRoot, jobId.toString()).apply {
        deleteRecursively()
        mkdirs()
    }

    fun freeBytes(): Long = stemsDir.apply { mkdirs() }.usableSpace

    /**
     * Moves finished stems from [workDir] into place, then records them. The rename is atomic, so playback only
     * ever sees complete files.
     */
    suspend fun commit(job: SeparationJobEntity, workDir: File, vocals: File, instrumental: File, fingerprint: String?) {
        val target = File(stemsDir, job.songId.toString())
        val set = withContext(Dispatchers.IO) {
            stemSets.value[job.songId]?.let { dao.deleteStemSets(listOf(job.songId)) }
            target.deleteRecursively()
            if (!workDir.renameTo(target)) throw java.io.IOException("Could not move stems into place")
            val vocalsFile = File(target, vocals.name)
            val instrumentalFile = File(target, instrumental.name)
            StemSetEntity(
                songId = job.songId,
                title = job.title,
                artist = job.artist,
                sourceSizeBytes = job.sourceSizeBytes,
                sourceDateModified = job.sourceDateModified,
                fingerprint = fingerprint,
                vocalsPath = vocalsFile.absolutePath,
                instrumentalPath = instrumentalFile.absolutePath,
                bytes = vocalsFile.length() + instrumentalFile.length(),
                createdAt = clock(),
            )
        }
        dao.complete(job.id, set, clock())
    }

    private fun deleteFiles(set: StemSetEntity) {
        val folder = File(set.vocalsPath).parentFile
        if (folder != null && folder.parentFile?.canonicalPath == stemsDir.canonicalPath) {
            folder.deleteRecursively()
        } else {
            File(set.vocalsPath).delete()
            File(set.instrumentalPath).delete()
        }
    }

    private companion object {
        const val TAG = "StemRepository"
        const val STEMS_DIR = "stems"
        const val WORK_DIR = ".work"
        const val RECONCILE_DELAY_MS = 3_000L
    }
}
