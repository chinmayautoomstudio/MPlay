package com.autoomstudio.mplay.playback

import android.content.ComponentName
import android.content.Context
import android.util.Log
import androidx.core.content.ContextCompat
import androidx.media3.common.C
import androidx.media3.common.Player
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken
import com.autoomstudio.mplay.data.library.SongRepository
import com.autoomstudio.mplay.data.model.Song
import com.google.common.util.concurrent.ListenableFuture
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.guava.await
import kotlinx.coroutines.launch

/**
 * UI-side handle to [PlaybackService]. Must be created and used on the main thread,
 * because the underlying [MediaController] is bound to the thread that built it.
 */
class PlaybackController(
    private val context: Context,
    private val scope: CoroutineScope,
    private val songRepository: SongRepository,
    private val sessionStore: PlaybackSessionStore,
) {
    private val _state = MutableStateFlow<NowPlayingState?>(null)

    /** Null while nothing is queued. */
    val state: StateFlow<NowPlayingState?> = _state.asStateFlow()

    private var controllerFuture: ListenableFuture<MediaController>? = null
    private var controller: MediaController? = null

    private val listener = object : Player.Listener {
        override fun onEvents(player: Player, events: Player.Events) = sync(player)
    }

    /** Connects early so the UI reflects playback that is already running in the service. */
    fun connect() {
        controllerFuture()
    }

    /** Current playback position, polled while collected. */
    fun positionMs(): Flow<Long> = flow {
        while (true) {
            emit(controller?.currentPosition?.coerceAtLeast(0L) ?: 0L)
            delay(POSITION_POLL_MS)
        }
    }.distinctUntilChanged()

    fun playQueue(songs: List<Song>, startIndex: Int) {
        if (songs.isEmpty() || startIndex !in songs.indices) return
        withController { controller ->
            controller.setMediaItems(songs.map { it.toMediaItem() }, startIndex, 0L)
            controller.prepare()
            controller.play()
        }
    }

    /** Turns shuffle on and plays [songs] starting from a random one. */
    fun shuffleQueue(songs: List<Song>) {
        if (songs.isEmpty()) return
        withController { controller ->
            controller.shuffleModeEnabled = true
            controller.setMediaItems(songs.map { it.toMediaItem() }, songs.indices.random(), 0L)
            controller.prepare()
            controller.play()
        }
    }

    /**
     * Inserts [songs] in order after the current one, or plays them when nothing is queued.
     * With shuffle on, Media3 picks their slots.
     */
    fun playNext(songs: List<Song>) {
        if (songs.isEmpty()) return
        withController { controller ->
            val items = songs.map { it.toMediaItem() }
            if (controller.mediaItemCount == 0) {
                controller.setMediaItems(items, 0, 0L)
                controller.prepare()
                controller.play()
            } else {
                controller.addMediaItems(controller.currentMediaItemIndex + 1, items)
            }
        }
    }

    /** Appends [songs] to the queue, or queues them paused when nothing is queued. */
    fun addToQueue(songs: List<Song>) {
        if (songs.isEmpty()) return
        withController { controller ->
            val items = songs.map { it.toMediaItem() }
            if (controller.mediaItemCount == 0) {
                controller.setMediaItems(items, 0, 0L)
                controller.prepare()
            } else {
                controller.addMediaItems(items)
            }
        }
    }

    /** Drops every queue entry for [songIds], for example after the files were deleted. */
    fun removeSongs(songIds: Set<Long>) {
        if (songIds.isEmpty()) return
        withController { controller ->
            for (index in controller.mediaItemCount - 1 downTo 0) {
                val id = controller.getMediaItemAt(index).mediaId.toLongOrNull()
                if (id in songIds) controller.removeMediaItem(index)
            }
        }
    }

    fun playPause() = withController { controller ->
        if (controller.isPlaying) {
            controller.pause()
        } else {
            when (controller.playbackState) {
                Player.STATE_IDLE -> controller.prepare()
                Player.STATE_ENDED -> controller.seekToDefaultPosition()
                Player.STATE_BUFFERING, Player.STATE_READY -> Unit
            }
            controller.play()
        }
    }

    fun next() = withController { it.seekToNext() }

    fun previous() = withController { it.seekToPrevious() }

    fun seekTo(positionMs: Long) = withController { it.seekTo(positionMs.coerceAtLeast(0L)) }

    fun toggleShuffle() = withController { it.shuffleModeEnabled = !it.shuffleModeEnabled }

    fun cycleRepeat() = withController { controller ->
        controller.repeatMode = nextRepeatMode(controller.repeatMode.toRepeatMode()).toPlayerRepeatMode()
    }

    fun release() {
        controllerFuture?.let(MediaController::releaseFuture)
        controllerFuture = null
        controller = null
    }

    private fun withController(block: (MediaController) -> Unit) {
        val connected = controller
        if (connected != null) {
            block(connected)
        } else {
            scope.launch { awaitController()?.let(block) }
        }
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
                    if (future.isCancelled || controllerFuture !== future) return@addListener
                    runCatching { future.get() }.onSuccess { connected ->
                        controller = connected
                        connected.addListener(listener)
                        sync(connected)
                        if (connected.mediaItemCount == 0) restoreSession(connected)
                    }
                },
                ContextCompat.getMainExecutor(context),
            )
        }

    /** Loads the last saved queue paused, unless the user started something in the meantime. */
    private fun restoreSession(controller: MediaController) {
        scope.launch {
            val restored = try {
                val saved = sessionStore.load() ?: return@launch
                restoreQueue(saved, songRepository.loadSongs(), Song::id)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.w(TAG, "Could not restore the last session", e)
                null
            } ?: return@launch
            if (this@PlaybackController.controller !== controller || controller.mediaItemCount > 0) return@launch
            controller.setMediaItems(restored.items.map { it.toMediaItem() }, restored.index, restored.positionMs)
            controller.prepare()
        }
    }

    private fun sync(player: Player) {
        if (player.mediaItemCount == 0) {
            _state.value = null
            return
        }
        val metadata = player.mediaMetadata
        _state.value = NowPlayingState(
            songId = player.currentMediaItem?.mediaId?.toLongOrNull(),
            title = metadata.title?.toString().orEmpty(),
            artist = metadata.artist?.toString().orEmpty(),
            artworkUri = metadata.artworkUri,
            durationMs = player.duration.takeIf { it != C.TIME_UNSET }?.coerceAtLeast(0L) ?: 0L,
            isPlaying = player.isPlaying,
            shuffleEnabled = player.shuffleModeEnabled,
            repeatMode = player.repeatMode.toRepeatMode(),
            hasPrevious = player.hasPreviousMediaItem(),
            hasNext = player.hasNextMediaItem(),
        )
    }

    private companion object {
        const val TAG = "PlaybackController"
        const val POSITION_POLL_MS = 500L
    }
}

private fun Int.toRepeatMode(): RepeatMode = when (this) {
    Player.REPEAT_MODE_ALL -> RepeatMode.All
    Player.REPEAT_MODE_ONE -> RepeatMode.One
    else -> RepeatMode.Off
}

private fun RepeatMode.toPlayerRepeatMode(): Int = when (this) {
    RepeatMode.Off -> Player.REPEAT_MODE_OFF
    RepeatMode.All -> Player.REPEAT_MODE_ALL
    RepeatMode.One -> Player.REPEAT_MODE_ONE
}
