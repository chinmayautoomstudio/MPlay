package com.autoomstudio.mplay.di

import android.content.Context
import com.autoomstudio.mplay.data.library.MediaStoreSongSource
import com.autoomstudio.mplay.data.library.SongRepository

class AppContainer(context: Context) {
    private val contentResolver = context.applicationContext.contentResolver

    val songRepository: SongRepository by lazy {
        SongRepository(contentResolver, MediaStoreSongSource(contentResolver))
    }
}
