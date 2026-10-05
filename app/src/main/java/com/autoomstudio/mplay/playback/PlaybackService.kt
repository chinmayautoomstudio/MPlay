package com.autoomstudio.mplay.playback

import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.os.SystemClock
import android.util.Log
import androidx.annotation.OptIn
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.PlaybackParameters
import androidx.media3.common.Player
import androidx.media3.common.Timeline
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.DefaultRenderersFactory
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.audio.AudioSink
import androidx.media3.exoplayer.audio.DefaultAudioSink
import androidx.media3.exoplayer.source.ShuffleOrder.DefaultShuffleOrder
import androidx.media3.session.MediaSession
import androidx.media3.session.MediaSessionService
import androidx.media3.session.SessionCommand
import androidx.media3.session.SessionError
import androidx.media3.session.SessionResult
import com.autoomstudio.mplay.MPlayApp
import com.autoomstudio.mplay.MainActivity
import com.autoomstudio.mplay.data.library.SongRepository
import com.autoomstudio.mplay.data.model.Song
import com.autoomstudio.mplay.data.settings.AppSettings
import com.autoomstudio.mplay.data.stems.StemMode
import com.autoomstudio.mplay.data.stems.StemRepository
import com.autoomstudio.mplay.playback.lofi.LofiAudioProcessor
import com.autoomstudio.mplay.widget.WidgetStatePublisher
import com.google.common.util.concurrent.Futures
import com.google.common.util.concurrent.ListenableFuture
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.guava.future
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.io.File

class PlaybackService : MediaSessionService() {

    private var mediaSession: MediaSession? = null
    private val serviceScope = MainScope()
    private var periodicSave: Job? = null

    private lateinit var sessionStore: PlaybackSessionStore
    private lateinit var saveScope: CoroutineScope
    private lateinit var widgetPublisher: WidgetStatePublisher
    private lateinit var songRepository: SongRepository
    private lateinit var appSettings: AppSettings
    private lateinit var sleepTimer: SleepTimerRunner

    private val lofiProcessor = LofiAudioProcessor()
    private var lofiEnabled = false

    private lateinit var stemRepository: StemRepository
    private var stemMode = StemMode.Original

    @OptIn(UnstableApi::class)
    override fun onCreate() {
        super.onCreate()
        val container = (application as MPlayApp).container
        sessionStore = container.playbackSessionStore
        saveScope = container.applicationScope
        appSettings = container.appSettings

        val audioAttributes = AudioAttributes.Builder()
            .setUsage(C.USAGE_MEDIA)
            .setContentType(C.AUDIO_CONTENT_TYPE_MUSIC)
            .build()
        val player = ExoPlayer.Builder(this, LofiRenderersFactory(this, lofiProcessor))
            .setAudioAttributes(audioAttributes, /* handleAudioFocus = */ true)
            .setHandleAudioBecomingNoisy(true)
            .setWakeMode(C.WAKE_MODE_LOCAL)
            .build()
        stemRepository = container.stemRepository
        player.addListener(SkipUnplayableListener(player))
        player.addListener(SessionSaver())
        widgetPublisher = container.createWidgetStatePublisher().also(player::addListener)
        songRepository = container.songRepository
        sleepTimer = SleepTimerRunner(player, serviceScope, onStatusChanged = { publishExtras() })
            .also(player::addListener)
        serviceScope.launch {
            val modes = sessionStore.loadModes() ?: return@launch
            player.shuffleModeEnabled = modes.shuffleEnabled
            player.repeatMode = modes.repeatMode
        }
        serviceScope.launch { applyLofi(player, appSettings.lofiEnabled.first(), persist = false) }
        serviceScope.launch {
            stemMode = appSettings.stemMode.first()
            publishExtras()
            // Also covers stems finishing or being deleted while their song is queued.
            stemRepository.stemSets.collect { refreshStemUris(player) }
        }

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
            .setCallback(SessionCallback())
            .setSessionExtras(PlaybackCommands.extras(SleepTimerStatus.Off, lofiEnabled))
            .build()
    }

    private fun publishExtras() {
        mediaSession?.setSessionExtras(PlaybackCommands.extras(sleepTimer.status, lofiEnabled, stemMode))
    }

    private fun applyStemMode(player: ExoPlayer, mode: StemMode) {
        stemMode = mode
        publishExtras()
        saveScope.launch { appSettings.setStemMode(mode) }
        refreshStemUris(player)
    }

    /** The file [item] should play in the current mode: its stem when one exists, otherwise the original. */
    private fun resolve(item: MediaItem): MediaItem {
        val original = item.originalUri ?: return item
        val songId = item.mediaId.toLongOrNull() ?: return item
        val stem = stemRepository.stemUri(songId, stemMode)?.takeIf { uri -> uri.path?.let { File(it).isFile } == true }
        return item.withPlaybackUri(stem ?: original) ?: item
    }

    /**
     * Repoints queued items after a mode or cache change. The current item goes first and resumes at the same
     * position; ExoPlayer replaces items by inserting and removing, so the shuffle order is put back afterwards.
     */
    @OptIn(UnstableApi::class)
    private fun refreshStemUris(player: ExoPlayer) {
        val count = player.mediaItemCount
        if (count == 0) return
        val shuffleOrder = shuffleOrderOf(player.currentTimeline)
        val current = player.currentMediaItemIndex
        var changed = false
        player.getMediaItemAt(current).let { item ->
            resolve(item).takeIf { it !== item }?.let { replacement ->
                val started = SystemClock.elapsedRealtime()
                val position = player.currentPosition
                player.replaceMediaItem(current, replacement)
                player.seekTo(current, position)
                logSwitchLatency(player, started)
                changed = true
            }
        }
        for (index in 0 until count) {
            if (index == current) continue
            val item = player.getMediaItemAt(index)
            resolve(item).takeIf { it !== item }?.let {
                player.replaceMediaItem(index, it)
                changed = true
            }
        }
        if (changed && shuffleOrder != null && shuffleOrder.size == player.mediaItemCount) {
            player.setShuffleOrder(DefaultShuffleOrder(shuffleOrder, SystemClock.elapsedRealtime()))
        }
    }

    private fun shuffleOrderOf(timeline: Timeline): IntArray? {
        if (timeline.isEmpty) return null
        val order = ArrayList<Int>(timeline.windowCount)
        var index = timeline.getFirstWindowIndex(true)
        while (index != C.INDEX_UNSET && order.size < timeline.windowCount) {
            order += index
            index = timeline.getNextWindowIndex(index, Player.REPEAT_MODE_OFF, true)
        }
        return order.toIntArray()
    }

    /** Measured for the PRD's switching target; read with `adb logcat -s StemSwitch`. */
    private fun logSwitchLatency(player: Player, startedElapsed: Long) {
        player.addListener(
            object : Player.Listener {
                override fun onPlaybackStateChanged(playbackState: Int) {
                    if (playbackState != Player.STATE_READY) return
                    Log.i(SWITCH_TAG, "Ready after ${SystemClock.elapsedRealtime() - startedElapsed} ms")
                    player.removeListener(this)
                }
            },
        )
    }

    private fun applyLofi(player: Player, enabled: Boolean, persist: Boolean) {
        lofiEnabled = enabled
        lofiProcessor.setEnabled(enabled)
        player.playbackParameters = if (enabled) LOFI_PLAYBACK else PlaybackParameters.DEFAULT
        publishExtras()
        if (persist) saveScope.launch { appSettings.setLofiEnabled(enabled) }
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
            widgetPublisher.publish(player, forcePaused = true)
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
            if (events.containsAny(
                    Player.EVENT_SHUFFLE_MODE_ENABLED_CHANGED,
                    Player.EVENT_REPEAT_MODE_CHANGED,
                )
            ) {
                val modes = PlaybackModes(player.shuffleModeEnabled, player.repeatMode)
                saveScope.launch { sessionStore.saveModes(modes) }
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

    private inner class SessionCallback : MediaSession.Callback {

        /** Only MPlay's own controllers may use the sleep timer and lofi commands. */
        @OptIn(UnstableApi::class)
        override fun onConnectAsync(
            session: MediaSession,
            controller: MediaSession.ControllerInfo,
        ): ListenableFuture<MediaSession.ConnectionResult> {
            val result = MediaSession.ConnectionResult.AcceptedResultBuilder(session, controller)
                .setSessionExtras(session.sessionExtras)
            if (controller.packageName == packageName) {
                val commands = MediaSession.ConnectionResult.DEFAULT_SESSION_COMMANDS.buildUpon()
                PlaybackCommands.all.forEach(commands::add)
                result.setAvailableSessionCommands(commands.build())
            }
            return Futures.immediateFuture(result.build())
        }

        override fun onCustomCommand(
            session: MediaSession,
            controller: MediaSession.ControllerInfo,
            customCommand: SessionCommand,
            args: Bundle,
        ): ListenableFuture<SessionResult> {
            val minutes = args.getInt(PlaybackCommands.ARG_MINUTES, SleepTimer.DEFAULT_MINUTES)
            when (customCommand.customAction) {
                PlaybackCommands.setSleepTimer.customAction -> sleepTimer.start(minutes)
                PlaybackCommands.setSleepTimerEndOfSong.customAction -> sleepTimer.startEndOfSong()
                PlaybackCommands.extendSleepTimer.customAction -> sleepTimer.extend(minutes)
                PlaybackCommands.cancelSleepTimer.customAction -> sleepTimer.cancel()
                PlaybackCommands.setLofi.customAction ->
                    applyLofi(session.player, args.getBoolean(PlaybackCommands.ARG_ENABLED), persist = true)
                PlaybackCommands.setStemMode.customAction -> (session.player as? ExoPlayer)?.let { player ->
                    applyStemMode(player, StemMode.fromName(args.getString(PlaybackCommands.ARG_STEM_MODE)))
                }
                else -> return Futures.immediateFuture(SessionResult(SessionError.ERROR_NOT_SUPPORTED))
            }
            return Futures.immediateFuture(SessionResult(SessionResult.RESULT_SUCCESS))
        }

        /**
         * Rebuilds the last saved queue when play is requested with nothing loaded, for example from the
         * widget, a headset button or the system's media resumption controls after the app was killed.
         */
        @OptIn(UnstableApi::class)
        override fun onPlaybackResumption(
            mediaSession: MediaSession,
            controller: MediaSession.ControllerInfo,
            isForPlayback: Boolean,
        ): ListenableFuture<MediaSession.MediaItemsWithStartPosition> = serviceScope.future {
            val saved = sessionStore.load() ?: throw UnsupportedOperationException("No saved session")
            val restored = restoreQueue(saved, songRepository.loadSongs(), Song::id)
                ?: throw UnsupportedOperationException("Saved songs are no longer in the library")
            MediaSession.MediaItemsWithStartPosition(
                restored.items.map { resolve(it.toMediaItem()) },
                restored.index,
                restored.positionMs,
            )
        }

        /** Every item added by any controller plays the version matching the current mode. */
        override fun onAddMediaItems(
            mediaSession: MediaSession,
            controller: MediaSession.ControllerInfo,
            mediaItems: MutableList<MediaItem>,
        ): ListenableFuture<MutableList<MediaItem>> =
            Futures.immediateFuture(mediaItems.mapTo(ArrayList(mediaItems.size), ::resolve))
    }

    /**
     * Falls back to the original when a stem can't be played, otherwise drops a song that failed to load or decode
     * and moves on, so one bad file never stops the queue.
     */
    private class SkipUnplayableListener(private val player: ExoPlayer) : Player.Listener {
        override fun onPlayerError(error: PlaybackException) {
            val item = player.currentMediaItem
            val original = item?.originalUri
            if (item != null && original != null) {
                item.withPlaybackUri(original)?.let { fallback ->
                    val index = player.currentMediaItemIndex
                    val position = player.currentPosition
                    player.replaceMediaItem(index, fallback)
                    player.seekTo(index, position)
                    player.prepare()
                    return
                }
            }
            if (player.hasNextMediaItem()) {
                player.removeMediaItem(player.currentMediaItemIndex)
                player.prepare()
                player.play()
            } else {
                player.stop()
            }
        }
    }

    /** Builds the default audio sink with [lofi] ahead of Media3's own speed and pitch processing. */
    @OptIn(UnstableApi::class)
    private class LofiRenderersFactory(
        context: Context,
        private val lofi: LofiAudioProcessor,
    ) : DefaultRenderersFactory(context) {
        override fun buildAudioSink(
            context: Context,
            enableFloatOutput: Boolean,
            enableAudioOutputPlaybackParams: Boolean,
        ): AudioSink = DefaultAudioSink.Builder(context)
            .setEnableFloatOutput(enableFloatOutput)
            .setEnableAudioOutputPlaybackParameters(enableAudioOutputPlaybackParams)
            .setAudioProcessors(arrayOf(lofi))
            .build()
    }

    private companion object {
        const val PERIODIC_SAVE_MS = 10_000L
        const val SWITCH_TAG = "StemSwitch"

        /** About 90% speed with the pitch lowered to match, like a slowed-down record. */
        val LOFI_PLAYBACK = PlaybackParameters(0.9f, 0.9f)
    }
}
