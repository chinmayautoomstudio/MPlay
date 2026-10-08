package com.autoomstudio.mp3studio.data.library

import android.content.ContentResolver
import android.database.ContentObserver
import android.os.Handler
import android.os.Looper
import com.autoomstudio.mp3studio.data.model.Song
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.conflate
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.merge
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.onStart

class SongRepository(
    private val contentResolver: ContentResolver,
    private val source: MediaStoreSongSource,
    private val scanner: AudioFolderScanner,
    private val watcher: AudioFolderWatcher,
) {

    private val refreshRequests = MutableSharedFlow<Unit>(extraBufferCapacity = 1)

    /**
     * Emits the song list immediately, on [refresh] or [rescan], whenever MediaStore audio changes,
     * and after automatic scans while collected (folder changes and a periodic fallback).
     */
    @OptIn(FlowPreview::class)
    fun songs(): Flow<List<Song>> = merge(
        mediaStoreChanges().debounce(CHANGE_DEBOUNCE_MS),
        refreshRequests,
        automaticScans(),
    )
        .onStart { emit(Unit) }
        .conflate()
        .map { source.querySongs() }

    /** One-off snapshot of the library. */
    suspend fun loadSongs(): List<Song> = source.querySongs()

    fun refresh() {
        refreshRequests.tryEmit(Unit)
    }

    /** Indexes the audio folders, then reloads the list. */
    suspend fun rescan() {
        scanner.scan()
        refresh()
    }

    /** Like [rescan], but skipped if a scan finished less than [maxAgeMs] ago. */
    suspend fun rescanIfStale(maxAgeMs: Long) {
        if (scanner.millisSinceLastScan >= maxAgeMs) rescan()
    }

    @OptIn(FlowPreview::class)
    private fun automaticScans(): Flow<Unit> = merge(
        watcher.changes().debounce(WATCH_DEBOUNCE_MS),
        flow {
            while (true) {
                delay(PERIODIC_SCAN_MS)
                emit(Unit)
            }
        },
    ).onEach { scanner.scan() }

    private fun mediaStoreChanges(): Flow<Unit> = callbackFlow {
        val observer = object : ContentObserver(Handler(Looper.getMainLooper())) {
            override fun onChange(selfChange: Boolean) {
                trySend(Unit)
            }
        }
        contentResolver.registerContentObserver(source.collectionUri, true, observer)
        awaitClose { contentResolver.unregisterContentObserver(observer) }
    }

    private companion object {
        const val CHANGE_DEBOUNCE_MS = 300L
        const val WATCH_DEBOUNCE_MS = 2_000L
        const val PERIODIC_SCAN_MS = 60_000L
    }
}
