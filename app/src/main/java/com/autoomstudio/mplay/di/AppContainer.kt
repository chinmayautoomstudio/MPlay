package com.autoomstudio.mplay.di

import android.content.Context
import com.autoomstudio.mplay.data.clip.ClipExporter
import com.autoomstudio.mplay.data.clip.ClipStore
import com.autoomstudio.mplay.data.clip.RingtoneSetter
import com.autoomstudio.mplay.data.clip.WaveformExtractor
import com.autoomstudio.mplay.data.library.AudioFolderScanner
import com.autoomstudio.mplay.data.library.AudioFolderWatcher
import com.autoomstudio.mplay.data.library.LibraryPreferences
import com.autoomstudio.mplay.data.library.MediaStoreSongSource
import com.autoomstudio.mplay.data.library.SongRepository
import com.autoomstudio.mplay.data.playlist.MPlayDatabase
import com.autoomstudio.mplay.data.playlist.PlaylistRepository
import com.autoomstudio.mplay.playback.PlaybackController
import com.autoomstudio.mplay.data.settings.AppSettings
import com.autoomstudio.mplay.playback.PlaybackSessionStore
import com.autoomstudio.mplay.widget.WidgetStatePublisher
import com.autoomstudio.mplay.widget.WidgetStateStore
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

    val libraryPreferences: LibraryPreferences by lazy { LibraryPreferences(appContext) }

    private val database: MPlayDatabase by lazy { MPlayDatabase.create(appContext) }

    val playlistRepository: PlaylistRepository by lazy { PlaylistRepository(database.playlistDao()) }

    val clipStore: ClipStore by lazy { ClipStore(appContext) }

    val clipExporter: ClipExporter by lazy { ClipExporter(appContext) }

    val waveformExtractor: WaveformExtractor by lazy { WaveformExtractor(appContext) }

    val ringtoneSetter: RingtoneSetter by lazy { RingtoneSetter(appContext) }

    val widgetStateStore: WidgetStateStore by lazy { WidgetStateStore(appContext) }

    val appSettings: AppSettings by lazy { AppSettings(appContext) }

    fun createWidgetStatePublisher(): WidgetStatePublisher =
        WidgetStatePublisher(appContext, widgetStateStore, applicationScope)

    fun createPlaybackController(scope: CoroutineScope): PlaybackController =
        PlaybackController(appContext, scope, songRepository, playbackSessionStore)
}
