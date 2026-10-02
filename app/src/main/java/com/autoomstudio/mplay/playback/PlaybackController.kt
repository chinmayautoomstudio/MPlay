package com.autoomstudio.mplay.playback

import android.content.ComponentName
import android.content.Context
import android.util.Log
import androidx.core.content.ContextCompat
import androidx.media3.common.Player
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken
import com.autoomstudio.mplay.data.model.Song
import com.google.common.util.concurrent.ListenableFuture
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.guava.await
import kotlinx.coroutines.launch

/**
 * UI-side handle to [PlaybackService]. Must be created and used on the main thread,
 * because the underlying [MediaController] is bound to the thread that built it.
 */
class PlaybackController(
    private val context: Context,
    private val scope: CoroutineScope,
) {
    private val _currentSongId = MutableStateFlow<Long?>(null)
    val currentSongId: StateFlow<Long?> = _currentSongId.asStateFlow()

    private val _isPlaying = MutableStateFlow(false)
    val isPlaying: StateFlow<Boolean> = _isPlaying.asStateFlow()

    private var controllerFuture: ListenableFuture<MediaController>? = null

    private val listener = object : Player.Listener {
        override fun onEvents(player: Player, events: Player.Events) = sync(player)
    }

    /** Connects early so the UI reflects playback that is already running in the service. */
    fun connect() {
        controllerFuture()
    }

    fun playQueue(songs: List<Song>, startIndex: Int) {
        if (songs.isEmpty() || startIndex !in songs.indices) return
        scope.launch {
            val controller = awaitController() ?: return@launch
            controller.setMediaItems(songs.map { it.toMediaItem() }, startIndex, 0L)
            controller.prepare()
            controller.play()
        }
    }

    fun release() {
        controllerFuture?.let(MediaController::releaseFuture)
        controllerFuture = null
    }

    private suspend fun awaitController(): MediaController? =
        try {
            controllerFuture().await()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.w(TAG, "Could not connect to PlaybackService", e)
            null
        }

    private fun controllerFuture(): ListenableFuture<MediaController> =
        controllerFuture ?: MediaController.Builder(
            context,
            SessionToken(context, ComponentName(context, PlaybackService::class.java)),
        ).buildAsync().also { future ->
            controllerFuture = future
            future.addListener(
                {
                    if (future.isCancelled) return@addListener
                    runCatching { future.get() }.onSuccess { controller ->
                        controller.addListener(listener)
                        sync(controller)
                    }
                },
                ContextCompat.getMainExecutor(context),
            )
        }

    private fun sync(player: Player) {
        _currentSongId.value = player.currentMediaItem?.mediaId?.toLongOrNull()
        _isPlaying.value = player.isPlaying
    }

    private companion object {
        const val TAG = "PlaybackController"
    }
}
