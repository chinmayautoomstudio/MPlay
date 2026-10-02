package com.autoomstudio.mplay.playback

import android.app.PendingIntent
import android.content.Intent
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.session.MediaSession
import androidx.media3.session.MediaSessionService
import com.autoomstudio.mplay.MPlayApp
import com.autoomstudio.mplay.MainActivity
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

class PlaybackService : MediaSessionService() {

    private var mediaSession: MediaSession? = null
    private val serviceScope = MainScope()
    private var periodicSave: Job? = null

    private lateinit var sessionStore: PlaybackSessionStore
    private lateinit var saveScope: CoroutineScope

    override fun onCreate() {
        super.onCreate()
        val container = (application as MPlayApp).container
        sessionStore = container.playbackSessionStore
        saveScope = container.applicationScope

        val audioAttributes = AudioAttributes.Builder()
            .setUsage(C.USAGE_MEDIA)
            .setContentType(C.AUDIO_CONTENT_TYPE_MUSIC)
            .build()
        val player = ExoPlayer.Builder(this)
            .setAudioAttributes(audioAttributes, /* handleAudioFocus = */ true)
            .setHandleAudioBecomingNoisy(true)
            .setWakeMode(C.WAKE_MODE_LOCAL)
            .build()
        player.addListener(SkipUnplayableListener(player))
        player.addListener(SessionSaver())

        val sessionActivity = PendingIntent.getActivity(
            this,
            0,
            Intent(this, MainActivity::class.java)
                .addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP)
                .putExtra(MainActivity.EXTRA_OPEN_NOW_PLAYING, true),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        mediaSession = MediaSession.Builder(this, player)
            .setSessionActivity(sessionActivity)
            .build()
    }

    override fun onGetSession(controllerInfo: MediaSession.ControllerInfo): MediaSession? =
        mediaSession

    override fun onTaskRemoved(rootIntent: Intent?) {
        val player = mediaSession?.player
        player?.let(::saveSession)
        if (player == null ||
            !player.playWhenReady ||
            player.mediaItemCount == 0 ||
            player.playbackState == Player.STATE_ENDED
        ) {
            stopSelf()
        }
    }

    override fun onDestroy() {
        serviceScope.cancel()
        mediaSession?.run {
            saveSession(player)
            player.release()
            release()
        }
        mediaSession = null
        super.onDestroy()
    }

    /** Snapshots on the main thread, writes on the application scope so it survives service teardown. */
    private fun saveSession(player: Player) {
        if (player.mediaItemCount == 0) return
        val session = SavedSession(
            songIds = (0 until player.mediaItemCount).mapNotNull {
                player.getMediaItemAt(it).mediaId.toLongOrNull()
            },
            index = player.currentMediaItemIndex,
            positionMs = player.currentPosition.coerceAtLeast(0L),
        )
        saveScope.launch { sessionStore.save(session) }
    }

    private inner class SessionSaver : Player.Listener {
        override fun onEvents(player: Player, events: Player.Events) {
            if (events.containsAny(
                    Player.EVENT_MEDIA_ITEM_TRANSITION,
                    Player.EVENT_TIMELINE_CHANGED,
                    Player.EVENT_IS_PLAYING_CHANGED,
                    Player.EVENT_POSITION_DISCONTINUITY,
                )
            ) {
                saveSession(player)
            }
            if (events.contains(Player.EVENT_IS_PLAYING_CHANGED)) {
                if (player.isPlaying) startPeriodicSave(player) else periodicSave?.cancel()
            }
        }

        private fun startPeriodicSave(player: Player) {
            periodicSave?.cancel()
            periodicSave = serviceScope.launch {
                while (isActive) {
                    delay(PERIODIC_SAVE_MS)
                    saveSession(player)
                }
            }
        }
    }

    /** Drops a song that failed to load or decode and moves on, so one bad file never stops the queue. */
    private class SkipUnplayableListener(private val player: ExoPlayer) : Player.Listener {
        override fun onPlayerError(error: PlaybackException) {
            if (player.hasNextMediaItem()) {
                player.removeMediaItem(player.currentMediaItemIndex)
                player.prepare()
                player.play()
            } else {
                player.stop()
            }
        }
    }

    private companion object {
        const val PERIODIC_SAVE_MS = 10_000L
    }
}
