package com.autoomstudio.mplay.data.library

import android.content.Context
import android.media.MediaScannerConnection
import android.os.Build
import android.os.Environment
import android.os.SystemClock
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withTimeoutOrNull
import java.io.File
import java.util.concurrent.atomic.AtomicInteger
import kotlin.coroutines.resume

/**
 * Asks Android's media scanner to index the public audio folders. Files copied in by some tools
 * (adb, certain file managers) stay unindexed, and therefore invisible in MediaStore, until scanned.
 */
class AudioFolderScanner(context: Context) {

    private val appContext = context.applicationContext
    private val mutex = Mutex()

    @Volatile
    private var lastScanAt = 0L

    /** Milliseconds since the last completed scan, or [Long.MAX_VALUE] if none has run. */
    val millisSinceLastScan: Long
        get() = if (lastScanAt == 0L) Long.MAX_VALUE else SystemClock.elapsedRealtime() - lastScanAt

    fun folders(): List<File> {
        val names = buildList {
            add(Environment.DIRECTORY_MUSIC)
            add(Environment.DIRECTORY_DOWNLOADS)
            add(Environment.DIRECTORY_PODCASTS)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) add(Environment.DIRECTORY_AUDIOBOOKS)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) add(Environment.DIRECTORY_RECORDINGS)
        }
        @Suppress("DEPRECATION")
        return names.map { Environment.getExternalStoragePublicDirectory(it) }.filter { it.isDirectory }
    }

    /** Scans every audio folder and waits until the scanner is done. A scan already in progress is shared. */
    suspend fun scan() {
        if (!mutex.tryLock()) {
            mutex.withLock { }
            return
        }
        try {
            val paths = folders().map { it.absolutePath }
            if (paths.isEmpty()) return
            withTimeoutOrNull(SCAN_TIMEOUT_MS) {
                suspendCancellableCoroutine { continuation ->
                    val remaining = AtomicInteger(paths.size)
                    MediaScannerConnection.scanFile(appContext, paths.toTypedArray(), null) { _, _ ->
                        if (remaining.decrementAndGet() == 0 && continuation.isActive) {
                            continuation.resume(Unit)
                        }
                    }
                }
            }
            lastScanAt = SystemClock.elapsedRealtime()
        } finally {
            mutex.unlock()
        }
    }

    private companion object {
        const val SCAN_TIMEOUT_MS = 15_000L
    }
}
