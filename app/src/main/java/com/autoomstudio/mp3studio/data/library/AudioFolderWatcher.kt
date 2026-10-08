package com.autoomstudio.mp3studio.data.library

import android.os.Build
import android.os.FileObserver
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import java.io.File

/** Emits whenever a file is added, finished, moved or deleted directly inside one of [folders]. */
class AudioFolderWatcher(private val folders: List<File>) {

    fun changes(): Flow<Unit> = callbackFlow {
        val onEvent: (String?) -> Unit = { path ->
            if (path != null && !path.startsWith(".")) trySend(Unit)
        }
        val observers = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            listOf(
                object : FileObserver(folders, EVENTS) {
                    override fun onEvent(event: Int, path: String?) = onEvent(path)
                },
            )
        } else {
            folders.map { folder ->
                @Suppress("DEPRECATION")
                object : FileObserver(folder.path, EVENTS) {
                    override fun onEvent(event: Int, path: String?) = onEvent(path)
                }
            }
        }
        observers.forEach(FileObserver::startWatching)
        awaitClose { observers.forEach(FileObserver::stopWatching) }
    }

    private companion object {
        const val EVENTS = FileObserver.CREATE or FileObserver.CLOSE_WRITE or
            FileObserver.MOVED_TO or FileObserver.DELETE or FileObserver.MOVED_FROM
    }
}
