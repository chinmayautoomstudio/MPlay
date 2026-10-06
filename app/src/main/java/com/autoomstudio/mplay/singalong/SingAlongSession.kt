package com.autoomstudio.mplay.singalong

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.SystemClock
import android.util.Log
import androidx.core.content.ContextCompat
import com.autoomstudio.mplay.data.model.Song
import com.autoomstudio.mplay.data.stems.StemMode
import com.autoomstudio.mplay.data.stems.StemRepository
import com.autoomstudio.mplay.metronome.MetronomeController
import com.autoomstudio.mplay.playback.PlaybackController
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import java.io.File
import kotlin.math.abs

sealed interface SingAlongState {
    data object Idle : SingAlongState

    data class Preparing(val title: String) : SingAlongState

    data class CountIn(val title: String, val count: Int) : SingAlongState

    /** [startedAtMs] is in [SystemClock.elapsedRealtime] time, for the timer. */
    data class Recording(val title: String, val startedAtMs: Long) : SingAlongState

    data class Review(val take: Take, val saveFailed: Boolean = false) : SingAlongState

    data class Saving(val take: Take, val progress: Float) : SingAlongState

    data class Saved(val name: String) : SingAlongState

    data class Failed(val reason: FailReason) : SingAlongState
}

enum class FailReason { Microphone, NoStems, Playback, TooShort }

data class SingAlongOptions(val fromStart: Boolean = true, val countIn: Boolean = false)

/**
 * One sing-along at a time: switches playback to the instrumental, records the voice in step with it, stops
 * cleanly when anything interrupts playback (SA9, SA11), then offers review, mixing and saving.
 *
 * Runs on the main thread, because [PlaybackController] must.
 */
class SingAlongSession(
    private val context: Context,
    private val stemRepository: StemRepository,
    private val recordingStore: RecordingStore,
    private val metronome: MetronomeController,
    private val createPlayback: (CoroutineScope) -> PlaybackController,
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val tempDir = File(context.cacheDir, TEMP_DIR)

    private val _state = MutableStateFlow<SingAlongState>(SingAlongState.Idle)
    val state: StateFlow<SingAlongState> = _state.asStateFlow()

    private val _mix = MutableStateFlow(MixSettings())
    val mix: StateFlow<MixSettings> = _mix.asStateFlow()

    @Volatile private var recorderFailed = false
    private val recorder = VoiceRecorder(context) { recorderFailed = true }

    private var playback: PlaybackController? = null
    private var sessionJob: Job? = null
    private var watchJob: Job? = null

    private var song: Song? = null
    private var instrumentalUri: Uri? = null
    private var options = SingAlongOptions()
    private var startMs = 0L
    private var leadFrames = 0L
    private var voiceFile: File? = null
    private var restore: Restore? = null

    /** The review screen's player; exists only while reviewing. */
    var preview: TakePreview? = null
        private set

    /** Preparing, counting in or recording. */
    val inProgress: Boolean
        get() = _state.value.let {
            it is SingAlongState.Preparing || it is SingAlongState.CountIn || it is SingAlongState.Recording
        }

    /** Live input level while recording, 0 to 1. */
    fun level(): Float = recorder.level

    /** Removes takes and half-saved files left by a process that was killed mid-session (SA16). */
    fun cleanUpLeftovers() {
        scope.launch {
            withContext(Dispatchers.IO) { if (_state.value == SingAlongState.Idle) tempDir.deleteRecursively() }
            recordingStore.deleteAbandoned()
        }
    }

    /** Bytes a take of [durationMs] needs: the raw voice, the mixed file and the saved copy, plus a margin. */
    fun hasSpaceFor(durationMs: Long): Boolean {
        val seconds = durationMs / 1000 + 1
        val needed = seconds * (VOICE_BYTES_PER_SECOND + 2 * AAC_BYTES_PER_SECOND) + SPACE_MARGIN_BYTES
        return runCatching { recordingStore.availableBytes() >= needed }.getOrDefault(true)
    }

    fun start(song: Song, options: SingAlongOptions) {
        if (inProgress || _state.value is SingAlongState.Saving) return
        releaseTake()
        val instrumental = stemRepository.stemUri(song.id, StemMode.Instrumental)
        if (instrumental == null) {
            _state.value = SingAlongState.Failed(FailReason.NoStems)
            return
        }
        this.song = song
        this.instrumentalUri = instrumental
        this.options = options
        _mix.value = MixSettings()
        startService()
        sessionJob = scope.launch { record(fixedStartMs = null) }
    }

    /** Stops recording and goes to review; during the count-in it cancels instead. */
    fun stop() {
        when (_state.value) {
            is SingAlongState.Recording -> finish(EndReason.Stopped)
            is SingAlongState.Preparing, is SingAlongState.CountIn -> cancel()
            else -> Unit
        }
    }

    /** Throws the current take away and records again from the same place. */
    fun retake() {
        val current = _state.value
        if (current !is SingAlongState.Recording && current !is SingAlongState.Review) return
        watchJob?.cancel()
        recorder.stop()
        releaseTake()
        startService()
        sessionJob = scope.launch { record(fixedStartMs = startMs) }
    }

    fun cancel() {
        sessionJob?.cancel()
        watchJob?.cancel()
        recorder.stop()
        playback?.pause()
        restorePlayback()
        releaseTake()
        _state.value = SingAlongState.Idle
    }

    fun updateMix(transform: (MixSettings) -> MixSettings) {
        _mix.value = transform(_mix.value).let {
            it.copy(
                voiceGain = it.voiceGain.coerceIn(0f, MixSettings.MAX_GAIN),
                instrumentalGain = it.instrumentalGain.coerceIn(0f, MixSettings.MAX_GAIN),
                offsetMs = it.offsetMs.coerceIn(-MixSettings.MAX_OFFSET_MS, MixSettings.MAX_OFFSET_MS),
            )
        }
    }

    fun save(name: String) {
        val review = _state.value as? SingAlongState.Review ?: return
        val take = review.take
        preview?.pause()
        val settings = _mix.value
        _state.value = SingAlongState.Saving(take, 0f)
        sessionJob = scope.launch {
            val temp = File(tempDir, "mix-${System.currentTimeMillis()}.m4a")
            val job = currentCoroutineContext()[Job]
            try {
                withContext(Dispatchers.Default) {
                    TakeRenderer(context, take).export(
                        output = temp,
                        settings = settings,
                        onProgress = { progress -> _state.value = SingAlongState.Saving(take, progress) },
                        shouldStop = { job?.isActive == false },
                    )
                }
                recordingStore.save(temp, name, take.artist)
                releaseTake()
                _state.value = SingAlongState.Saved(name)
            } catch (e: CancellationException) {
                temp.delete()
                throw e
            } catch (e: Exception) {
                Log.e(TAG, "Could not save the sing-along", e)
                temp.delete()
                _state.value = SingAlongState.Review(take, saveFailed = true)
            }
        }
    }

    /** Leaves review, saved or failed states, deleting the take if it wasn't saved. */
    fun close() {
        if (inProgress || _state.value is SingAlongState.Saving) return
        releaseTake()
        _state.value = SingAlongState.Idle
    }

    private suspend fun record(fixedStartMs: Long?) {
        val song = song ?: return
        _state.value = SingAlongState.Preparing(song.title)
        recorderFailed = false
        val playback = playback ?: createPlayback(scope).also {
            it.connect()
            playback = it
        }
        val current = withTimeoutOrNull(CONNECT_TIMEOUT_MS) { playback.state.first { it?.songId == song.id } }
        if (current == null) {
            fail(FailReason.Playback)
            return
        }
        if (restore == null) restore = Restore(current.stemMode, current.lofiEnabled)
        startMs = fixedStartMs ?: if (options.fromStart) 0L else playback.currentPositionMs() ?: 0L
        playback.pause()
        playback.setLofi(false)
        playback.setStemMode(StemMode.Instrumental)
        // Switching stems reloads the song, and seeks are ignored until the new file is ready.
        val seeked = withTimeoutOrNull(CONNECT_TIMEOUT_MS) {
            playback.state.first { it?.stemMode == StemMode.Instrumental && !it.isPlaying && !it.lofiEnabled }
            while (!playback.isSeekable()) delay(SEEK_POLL_MS)
            playback.seekTo(startMs)
            while (abs((playback.currentPositionMs() ?: startMs) - startMs) > SEEK_TOLERANCE_MS) delay(SEEK_POLL_MS)
        }
        if (seeked == null) {
            fail(FailReason.Playback)
            return
        }

        if (options.countIn) {
            val metronomeState = metronome.state.value
            val beatMs = if (metronomeState.running) 60_000L / metronomeState.settings.bpm else 1_000L
            for (count in COUNT_IN downTo 1) {
                _state.value = SingAlongState.CountIn(song.title, count)
                delay(beatMs)
            }
        }

        tempDir.mkdirs()
        val file = File(tempDir, "voice-${System.currentTimeMillis()}.pcm")
        voiceFile = file
        if (!recorder.start(file)) {
            file.delete()
            voiceFile = null
            fail(FailReason.Microphone)
            return
        }
        playback.play()
        val playing = withTimeoutOrNull(CONNECT_TIMEOUT_MS) {
            playback.state.first { it?.isPlaying == true && it.songId == song.id }
        }
        if (playing == null) {
            recorder.stop()
            file.delete()
            voiceFile = null
            fail(FailReason.Playback)
            return
        }
        // Work back from where the player is now to when the instrumental became audible.
        val now = SystemClock.elapsedRealtime()
        val position = playback.currentPositionMs() ?: startMs
        val instrumentalStartedAt = now - (position - startMs).coerceAtLeast(0)
        leadFrames = ((instrumentalStartedAt - recorder.startedAtMs) * SingAlongMixer.SAMPLE_RATE / 1000)
            .coerceAtLeast(0)
        _state.value = SingAlongState.Recording(song.title, recorder.startedAtMs)
        watchJob = scope.launch { watch(song, playback) }
    }

    /** Ends the take when playback stops for any reason other than the Stop button (SA9, SA11). */
    private suspend fun watch(song: Song, playback: PlaybackController) {
        var lastPosition = startMs
        var pausedPolls = 0
        while (currentCoroutineContext().isActive) {
            delay(WATCH_INTERVAL_MS)
            val state = playback.state.value
            val position = playback.currentPositionMs() ?: lastPosition
            val reason = when {
                recorderFailed -> EndReason.Failed
                state == null || state.songId != song.id -> EndReason.SongEnded
                // Repeat-one jumped back to the start.
                position + LOOP_JUMP_MS < lastPosition -> EndReason.SongEnded
                !state.isPlaying -> {
                    pausedPolls++
                    when {
                        state.durationMs > 0 && position >= state.durationMs - END_MARGIN_MS -> EndReason.SongEnded
                        pausedPolls >= PAUSED_POLLS_TO_STOP -> EndReason.Interrupted
                        else -> null
                    }
                }
                else -> {
                    pausedPolls = 0
                    null
                }
            }
            lastPosition = position
            if (reason != null) {
                finish(reason)
                return
            }
        }
    }

    private fun finish(reason: EndReason) {
        watchJob?.cancel()
        recorder.stop()
        playback?.pause()
        restorePlayback()
        val song = song
        val file = voiceFile
        val instrumental = instrumentalUri
        if (song == null || file == null || instrumental == null) {
            _state.value = SingAlongState.Idle
            return
        }
        val take = Take(
            songId = song.id,
            title = song.title,
            artist = song.artist,
            instrumentalUri = instrumental,
            instrumentalStartMs = startMs,
            voiceFile = file,
            leadFrames = leadFrames,
            voiceFrames = recorder.frames,
            endReason = reason,
        )
        if (take.durationMs < MIN_TAKE_MS) {
            releaseTake()
            _state.value = SingAlongState.Failed(FailReason.TooShort)
            return
        }
        preview = TakePreview(context, take) { _mix.value }
        _state.value = SingAlongState.Review(take)
    }

    private fun fail(reason: FailReason) {
        playback?.pause()
        restorePlayback()
        _state.value = SingAlongState.Failed(reason)
    }

    private fun restorePlayback() {
        val previous = restore ?: return
        restore = null
        playback?.setStemMode(previous.stemMode)
        playback?.setLofi(previous.lofi)
    }

    private fun releaseTake() {
        preview?.release()
        preview = null
        voiceFile?.delete()
        voiceFile = null
    }

    private fun startService() {
        try {
            ContextCompat.startForegroundService(context, Intent(context, RecordingService::class.java))
        } catch (e: Exception) {
            // Recording still works while MPlay stays on screen.
            Log.w(TAG, "Could not start the recording service", e)
        }
    }

    private data class Restore(val stemMode: StemMode, val lofi: Boolean)

    private companion object {
        const val TAG = "SingAlongSession"
        const val TEMP_DIR = "singalong"
        const val COUNT_IN = 3
        const val CONNECT_TIMEOUT_MS = 5_000L
        const val SEEK_TOLERANCE_MS = 300L
        const val SEEK_POLL_MS = 50L
        const val WATCH_INTERVAL_MS = 200L
        const val PAUSED_POLLS_TO_STOP = 2
        const val END_MARGIN_MS = 1_500L
        const val LOOP_JUMP_MS = 1_500L
        const val MIN_TAKE_MS = 1_000L
        const val VOICE_BYTES_PER_SECOND = 44_100L * 2
        const val AAC_BYTES_PER_SECOND = 192_000L / 8
        const val SPACE_MARGIN_BYTES = 20L * 1024 * 1024
    }
}
