package com.autoomstudio.mplay.data.library

import android.content.ContentResolver
import android.database.ContentObserver
import android.os.Handler
import android.os.Looper
import com.autoomstudio.mplay.data.model.Song
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.conflate
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.merge
import kotlinx.coroutines.flow.onStart

class SongRepository(
    private val contentResolver: ContentResolver,
    private val source: MediaStoreSongSource,
) {

    private val refreshRequests = MutableSharedFlow<Unit>(extraBufferCapacity = 1)

    /** Emits the song list immediately, on [refresh], and whenever MediaStore audio changes. */
    @OptIn(FlowPreview::class)
    fun songs(): Flow<List<Song>> = merge(
        mediaStoreChanges().debounce(CHANGE_DEBOUNCE_MS),
        refreshRequests,
    )
        .onStart { emit(Unit) }
        .conflate()
        .map { source.querySongs() }

    fun refresh() {
        refreshRequests.tryEmit(Unit)
    }

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
    }
}
