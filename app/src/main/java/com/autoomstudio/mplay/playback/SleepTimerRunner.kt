package com.autoomstudio.mplay.playback

import android.os.SystemClock
import androidx.annotation.OptIn
import androidx.media3.common.C
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

/**
 * Drives the sleep timer inside [PlaybackService], so it fires even when the app UI is gone.
 * Fades the volume over the last [SleepTimer.FADE_MS], then pauses and keeps the queue.
 * Must be used on the player's thread.
 */
internal class SleepTimerRunner(
    private val player: ExoPlayer,
    private val scope: CoroutineScope,
    private val onStatusChanged: (SleepTimerStatus) -> Unit,
    private val clock: () -> Long = SystemClock::elapsedRealtime,
) : Player.Listener {

    var status: SleepTimerStatus = SleepTimerStatus.Off
        private set

    private var ticker: Job? = null

    fun start(minutes: Int) = update(SleepTimer.start(minutes, clock()))

    fun startEndOfSong() = update(SleepTimerStatus.EndOfSong)

    fun extend(minutes: Int) = update(SleepTimer.extend(status, minutes, clock()))

    fun cancel() = update(SleepTimerStatus.Off)

    override fun onEvents(player: Player, events: Player.Events) {
        if (status == SleepTimerStatus.Off) return
        val stopped = player.mediaItemCount == 0 ||
            (player.playbackState == Player.STATE_IDLE && player.playerError == null)
        if (stopped) cancel()
    }

    override fun onPlayWhenReadyChanged(playWhenReady: Boolean, reason: Int) {
        if (status == SleepTimerStatus.EndOfSong &&
            !playWhenReady &&
            reason == Player.PLAY_WHEN_READY_CHANGE_REASON_END_OF_MEDIA_ITEM
        ) {
            cancel()
        }
    }

    override fun onPlaybackStateChanged(playbackState: Int) {
        if (status == SleepTimerStatus.EndOfSong && playbackState == Player.STATE_ENDED) cancel()
    }

    @OptIn(UnstableApi::class)
    private fun update(next: SleepTimerStatus) {
        status = next
        player.pauseAtEndOfMediaItems = next == SleepTimerStatus.EndOfSong
        if (next == SleepTimerStatus.Off) {
            ticker?.cancel()
            ticker = null
            player.volume = 1f
        } else if (ticker == null) {
            ticker = scope.launch {
                while (isActive) {
                    tick()
                    delay(TICK_MS)
                }
            }
        }
        onStatusChanged(next)
    }

    private fun tick() {
        when (val current = status) {
            SleepTimerStatus.Off -> Unit
            is SleepTimerStatus.Running -> {
                val remaining = SleepTimer.remainingMs(current, clock())
                if (remaining == 0L) {
                    player.pause()
                    cancel()
                } else {
                    player.volume = SleepTimer.volumeAt(remaining)
                }
            }
            SleepTimerStatus.EndOfSong -> {
                val duration = player.duration
                player.volume = if (duration == C.TIME_UNSET) {
                    1f
                } else {
                    SleepTimer.volumeAt(
                        SleepTimer.songRemainingMs(duration, player.currentPosition, player.playbackParameters.speed),
                    )
                }
            }
        }
    }

    private companion object {
        const val TICK_MS = 250L
    }
}
