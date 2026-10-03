package com.autoomstudio.mplay.data.duplicates

import com.autoomstudio.mplay.data.model.Song
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

class DuplicateRepository(
    private val dao: DuplicateDao,
    private val fingerprinter: FileFingerprinter,
    /** Fingerprinting runs here, so it finishes even if the screen that asked goes away. */
    private val scope: CoroutineScope,
) {
    private val fingerprintLock = Mutex()

    /**
     * Duplicate groups for [songs]. Emits right away from cached fingerprints, then again as missing
     * fingerprints are computed in the background and whenever the user changes a choice.
     */
    fun index(songs: List<Song>): Flow<DuplicateIndex> =
        combine(dao.observeFingerprints(), dao.observeOverrides()) { fingerprints, overrides ->
            val songsById = songs.associateBy { it.id }
            val validFingerprints = fingerprints
                .filter { songsById[it.songId]?.let(it::matches) == true }
                .associate { it.songId to it.hash }
            DuplicateDetector.buildIndex(
                songs = songs,
                fingerprints = validFingerprints,
                keepOverrides = overrides.idsOf(DuplicateOverrideKind.Keep),
                restoredIds = overrides.idsOf(DuplicateOverrideKind.Restore),
            )
        }
            .onStart { scope.launch { updateFingerprints(songs) } }
            .flowOn(Dispatchers.Default)

    suspend fun keep(songId: Long, group: DuplicateGroup) = dao.keep(songId, group.songs.map { it.id })

    suspend fun restore(songId: Long) = dao.restore(songId)

    suspend fun hideAgain(songId: Long) = dao.hideAgain(songId)

    /** Exact copies always share a byte size, so only files with a same-size twin are read. */
    private suspend fun updateFingerprints(songs: List<Song>) = fingerprintLock.withLock {
        withContext(Dispatchers.IO) {
            if (songs.isEmpty()) return@withContext
            val existing = dao.fingerprints().associateBy { it.songId }
            val libraryIds = songs.mapTo(HashSet()) { it.id }
            existing.keys.filterNot { it in libraryIds }
                .chunked(SQL_BATCH)
                .forEach { dao.deleteFingerprints(it) }

            songs.filter { it.sizeBytes > 0 }
                .groupBy { it.sizeBytes }
                .values
                .filter { it.size > 1 }
                .flatten()
                .filter { song -> existing[song.id]?.matches(song) != true }
                .chunked(WRITE_BATCH)
                .forEach { batch ->
                    val computed = batch.mapNotNull { song ->
                        fingerprinter.fingerprint(song)?.let { hash ->
                            SongFingerprintEntity(song.id, song.sizeBytes, song.dateModified, hash)
                        }
                    }
                    if (computed.isNotEmpty()) dao.upsertFingerprints(computed)
                }
        }
    }

    private companion object {
        const val SQL_BATCH = 500
        const val WRITE_BATCH = 50
    }
}

private fun SongFingerprintEntity.matches(song: Song): Boolean =
    sizeBytes == song.sizeBytes && dateModified == song.dateModified

private fun List<DuplicateOverrideEntity>.idsOf(kind: DuplicateOverrideKind): Set<Long> =
    filter { it.kind == kind.name }.mapTo(HashSet()) { it.songId }
