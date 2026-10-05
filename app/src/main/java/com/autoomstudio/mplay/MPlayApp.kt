package com.autoomstudio.mplay

import android.app.Application
import android.os.Build
import com.autoomstudio.mplay.di.AppContainer
import java.io.File

class MPlayApp : Application() {
    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        container = AppContainer(this)
        // The separation model runs in a ":separator" process, which needs none of this.
        if (isMainProcess()) container.separationController.start()
    }

    private fun isMainProcess(): Boolean {
        val name = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            getProcessName()
        } else {
            runCatching { File("/proc/self/cmdline").readText().trimEnd('\u0000') }.getOrNull()
        }
        return name == null || name == packageName
    }
}
