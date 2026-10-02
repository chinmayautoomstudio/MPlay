package com.autoomstudio.mplay

import android.app.Application
import com.autoomstudio.mplay.di.AppContainer

class MPlayApp : Application() {
    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        container = AppContainer(this)
    }
}
