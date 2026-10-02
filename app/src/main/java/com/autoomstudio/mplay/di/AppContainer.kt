package com.autoomstudio.mplay.di

import android.content.Context
import com.autoomstudio.mplay.data.library.MediaStoreSongSource
import com.autoomstudio.mplay.data.library.SongRepository
import com.autoomstudio.mplay.playback.PlaybackController
import kotlinx.coroutines.CoroutineScope

class AppContainer(context: Context) {
    private val appContext = context.applicationContext
    private val contentResolver = appContext.contentResolver

    val songRepository: SongRepository by lazy {
        SongRepository(contentResolver, MediaStoreSongSource(contentResolver))
    }

    fun createPlaybackController(scope: CoroutineScope): PlaybackController =
        PlaybackController(appContext, scope)
}
