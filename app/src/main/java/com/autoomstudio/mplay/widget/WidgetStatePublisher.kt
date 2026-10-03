package com.autoomstudio.mplay.widget

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.util.Log
import androidx.core.graphics.scale
import androidx.glance.appwidget.updateAll
import androidx.media3.common.Player
import com.autoomstudio.mplay.R
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

/** Mirrors the player into [WidgetStateStore] and refreshes the widget on track and play-state changes. */
class WidgetStatePublisher(
    private val context: Context,
    private val store: WidgetStateStore,
    private val scope: CoroutineScope,
) : Player.Listener {

    private val unknownTitle = context.getString(R.string.unknown_title)
    private val unknownArtist = context.getString(R.string.unknown_artist)
    private val writeLock = Mutex()
    private var lastPublished: WidgetState? = null
    private var artworkFor: Pair<Long?, Uri?>? = null
    private var pending: Job? = null

    override fun onEvents(player: Player, events: Player.Events) {
        if (events.containsAny(
                Player.EVENT_MEDIA_ITEM_TRANSITION,
                Player.EVENT_IS_PLAYING_CHANGED,
                Player.EVENT_TIMELINE_CHANGED,
                Player.EVENT_MEDIA_METADATA_CHANGED,
            )
        ) {
            publish(player)
        }
    }

    /** Snapshots on the player thread; the write runs on [scope]. */
    fun publish(player: Player, forcePaused: Boolean = false) {
        val hasItem = player.mediaItemCount > 0 && player.currentMediaItem != null
        val metadata = player.mediaMetadata
        val state = widgetStateOf(
            hasItem = hasItem,
            mediaId = player.currentMediaItem?.mediaId,
            title = metadata.title,
            artist = metadata.artist,
            isPlaying = player.isPlaying && !forcePaused,
            unknownTitle = unknownTitle,
            unknownArtist = unknownArtist,
        )
        val artworkKey = state.songId to metadata.artworkUri.takeIf { hasItem }
        val artworkChanged = artworkKey != artworkFor
        if (state == lastPublished && !artworkChanged) return
        lastPublished = state
        artworkFor = artworkKey

        val previous = pending
        pending = scope.launch {
            if (artworkChanged) previous?.cancel()
            writeLock.withLock {
                try {
                    store.save(state)
                    if (artworkChanged) store.saveArtwork(artworkKey.second?.let { loadArtwork(it) })
                    MPlayWidget().updateAll(context)
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    Log.w(TAG, "Could not update the widget", e)
                }
            }
        }
    }

    /** Decodes album art at roughly [ARTWORK_PX] so the widget's RemoteViews stay well under binder limits. */
    private suspend fun loadArtwork(uri: Uri): Bitmap? = withContext(Dispatchers.IO) {
        try {
            val resolver = context.contentResolver
            val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            resolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, bounds) } ?: return@withContext null
            if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return@withContext null
            var sample = 1
            while (bounds.outWidth / (sample * 2) >= ARTWORK_PX && bounds.outHeight / (sample * 2) >= ARTWORK_PX) {
                sample *= 2
            }
            val decoded = resolver.openInputStream(uri)?.use {
                BitmapFactory.decodeStream(it, null, BitmapFactory.Options().apply { inSampleSize = sample })
            } ?: return@withContext null
            val side = minOf(decoded.width, decoded.height)
            val cropped = Bitmap.createBitmap(decoded, (decoded.width - side) / 2, (decoded.height - side) / 2, side, side)
            if (side <= ARTWORK_PX) cropped else cropped.scale(ARTWORK_PX, ARTWORK_PX)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            // No embedded art or the file is gone: the widget shows its placeholder.
            null
        }
    }

    private companion object {
        const val TAG = "WidgetStatePublisher"
        const val ARTWORK_PX = 256
    }
}
