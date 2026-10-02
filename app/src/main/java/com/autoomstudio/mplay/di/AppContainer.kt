package com.autoomstudio.mplay.di

import android.content.Context
import com.autoomstudio.mplay.data.library.AudioFolderScanner
import com.autoomstudio.mplay.data.library.AudioFolderWatcher
import com.autoomstudio.mplay.data.library.MediaStoreSongSource
import com.autoomstudio.mplay.data.library.SongRepository
import com.autoomstudio.mplay.playback.PlaybackController
import com.autoomstudio.mplay.playback.PlaybackSessionStore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob

class AppContainer(context: Context) {
    private val appContext = context.applicationContext
    private val contentResolver = appContext.contentResolver

    /** Outlives screens and services, for writes that must finish after their caller is gone. */
    val applicationScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    val songRepository: SongRepository by lazy {
        val scanner = AudioFolderScanner(appContext)
        SongRepository(
            contentResolver = contentResolver,
            source = MediaStoreSongSource(contentResolver),
            scanner = scanner,
            watcher = AudioFolderWatcher(scanner.folders()),
        )
    }

    val playbackSessionStore: PlaybackSessionStore by lazy { PlaybackSessionStore(appContext) }

    fun createPlaybackController(scope: CoroutineScope): PlaybackController =
        PlaybackController(appContext, scope, songRepository, playbackSessionStore)
}
