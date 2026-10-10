package com.autoomstudio.mp3studio

import android.app.Application
import android.os.Build
import coil3.ImageLoader
import coil3.PlatformContext
import coil3.SingletonImageLoader
import com.autoomstudio.mp3studio.di.AppContainer
import com.autoomstudio.mp3studio.di.AuthEffects
import com.autoomstudio.mp3studio.ui.components.AlbumArtFetcher
import java.io.File

class MPlayApp : Application(), SingletonImageLoader.Factory {
    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        container = AppContainer(this)
        // The separation model runs in a ":separator" process, which needs none of this.
        if (isMainProcess()) {
            container.authRepository.start()
            container.separationController.start()
            AuthEffects(this, container).start()
            container.singAlongSession.cleanUpLeftovers()
        }
    }

    override fun newImageLoader(context: PlatformContext): ImageLoader =
        ImageLoader.Builder(context)
            .components { add(AlbumArtFetcher.Factory(container.albumArtLoader)) }
            .build()

    private fun isMainProcess(): Boolean {
        val name = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            getProcessName()
        } else {
            runCatching { File("/proc/self/cmdline").readText().trimEnd('\u0000') }.getOrNull()
        }
        return name == null || name == packageName
    }
}
