package com.autoomstudio.mplay.metronome

import android.content.Context
import android.content.Intent
import android.media.AudioAttributes
import android.media.AudioFocusRequest
import android.media.AudioManager
import android.os.Handler
import android.os.Looper
import android.util.Log
import androidx.core.content.ContextCompat
import com.autoomstudio.mplay.data.settings.AppSettings
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.math.abs
import kotlin.math.roundToInt

/** [muted] is the user's mute toggle; the beat keeps going while muted. */
data class MetronomeState(
    val running: Boolean = false,
    val settings: MetronomeSettings = MetronomeSettings(),
    val muted: Boolean = false,
)

/** Whether the clicks follow a song's beats. */
sealed interface SyncState {
    data object Off : SyncState

    /** [startedByUs]: sync started the metronome, so it stops with the song. [musicPaused]: silent until it plays. */
    data class On(val songId: Long, val startedByUs: Boolean, val musicPaused: Boolean) : SyncState
}

/**
 * The one metronome shared by the Metronome tab, the Now Playing sheet and the service notification.
 *
 * Audio focus (MT13): while MPlay's music plays, the metronome never requests focus, so the music is never paused
 * or ducked. Otherwise it requests focus like any player; losing it to MPlay's own player keeps the metronome
 * running, while losing it to another app stops it. Calls silence it until they end (MT16).
 *
 * Sync with the song ([syncTo]): the clicks sit on the song's detected [BeatGrid], go silent while the music is
 * paused, re-align after a seek or speed change and whenever they drift more than [MAX_ERROR_MS], and sync ends
 * when the song changes or the BPM is changed by hand. Sync calls must be made on the main thread.
 */
class MetronomeController(
    private val context: Context,
    private val appSettings: AppSettings,
    private val musicPlaying: StateFlow<Boolean>,
    private val timeline: MusicTimeline,
    private val scope: CoroutineScope,
) {
    private val audioManager = context.getSystemService(AudioManager::class.java)
    private val mainHandler = Handler(Looper.getMainLooper())

    private val _beat = MutableStateFlow<BeatTick?>(null)

    /** The beat currently sounding; changes on every beat, so collect it only where the flash is drawn. */
    val beat: StateFlow<BeatTick?> = _beat.asStateFlow()

    private val engine = MetronomeEngine { tick -> _beat.value = tick }
    private val running = MutableStateFlow(false)
    private val settings = MutableStateFlow(MetronomeSettings())

    private val userMuted = MutableStateFlow(false)

    val state: StateFlow<MetronomeState> = combine(running, settings, userMuted, ::MetronomeState)
        .stateIn(scope, SharingStarted.Eagerly, MetronomeState())

    private var saveJob: Job? = null
    private var callWatch: Job? = null
    private var focusLossJob: Job? = null
    private var holdsFocus = false

    @Volatile private var inCall = false

    @Volatile private var focusPaused = false

    @Volatile private var musicPaused = false

    private val _sync = MutableStateFlow<SyncState>(SyncState.Off)
    val sync: StateFlow<SyncState> = _sync.asStateFlow()

    private var syncJob: Job? = null
    private var grid: BeatGrid? = null
    private var alignment: Alignment? = null

    private val focusRequest = AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN)
        .setAudioAttributes(
            AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_MEDIA)
                .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                .build(),
        )
        .setWillPauseWhenDucked(false)
        .setOnAudioFocusChangeListener(::onFocusChange, mainHandler)
        .build()

    init {
        scope.launch {
            val saved = appSettings.metronomeSettings.first()
            settings.value = saved
            engine.update(saved)
        }
    }

    /** A BPM change ends sync unless [keepSync], for callers that pass a matching grid to [syncTo] next. */
    fun update(keepSync: Boolean = false, transform: (MetronomeSettings) -> MetronomeSettings) {
        val next = transform(settings.value).let { it.copy(bpm = MetronomeSettings.clampBpm(it.bpm)) }
        if (next == settings.value) return
        if (!keepSync && next.bpm != settings.value.bpm) stopSync()
        settings.value = next
        engine.update(next)
        saveJob?.cancel()
        saveJob = scope.launch {
            delay(SAVE_DELAY_MS)
            appSettings.setMetronomeSettings(next)
        }
    }

    fun toggle() = if (running.value) stop() else start()

    fun start() {
        if (running.value) return
        if (!musicPlaying.value) requestFocus()
        engine.start()
        if (!engine.isRunning) {
            abandonFocus()
            return
        }
        running.value = true
        watchCalls()
        try {
            ContextCompat.startForegroundService(context, Intent(context, MetronomeService::class.java))
        } catch (e: Exception) {
            // The tab and sheet only start it from visible UI, but keep playing in the app if the system refuses.
            Log.w(TAG, "Could not start the metronome service", e)
        }
    }

    fun stop() {
        if (!running.value) return
        stopSync()
        running.value = false
        callWatch?.cancel()
        focusLossJob?.cancel()
        engine.stop()
        _beat.value = null
        abandonFocus()
        inCall = false
        focusPaused = false
        updateMute()
    }

    /** Doubles or halves the BPM; while synced the grid is scaled exactly, so the clicks stay on the song. */
    fun scaleTempo(factor: Double) {
        val target = (settings.value.bpm * factor).roundToInt()
        update(keepSync = true) { it.copy(bpm = target) }
        val current = grid ?: return
        if (settings.value.bpm != target) {
            stopSync()
            return
        }
        grid = current.scaled(factor)
        realign()
    }

    /**
     * Puts the clicks on [grid], which must match the current BPM, and follows song [songId] in MPlay's player.
     * Starts the metronome if it isn't running.
     */
    fun syncTo(songId: Long, grid: BeatGrid) {
        val current = _sync.value as? SyncState.On
        if (current?.songId == songId && syncJob?.isActive == true) {
            this.grid = grid
            realign()
            return
        }
        val startedByUs = current?.startedByUs == true || !running.value
        stopSync()
        start()
        if (!running.value) return
        this.grid = grid
        _sync.value = SyncState.On(songId, startedByUs, musicPaused = false)
        syncJob = scope.launch(Dispatchers.Main.immediate) { follow(songId, startedByUs) }
    }

    fun stopSync() {
        if (syncJob == null && _sync.value == SyncState.Off) return
        syncJob?.cancel()
        syncJob = null
        grid = null
        alignment = null
        musicPaused = false
        updateMute()
        engine.unalign()
        timeline.release()
        _sync.value = SyncState.Off
    }

    private suspend fun follow(songId: Long, startedByUs: Boolean) {
        timeline.connect()
        val loaded = withTimeoutOrNull(CONNECT_TIMEOUT_MS) { timeline.songId.first { it != null } }
        if (loaded != songId) {
            stopSync()
            return
        }
        realign()
        coroutineScope {
            launch {
                timeline.songId.first { it != songId }
                if (startedByUs) stop() else stopSync()
            }
            launch {
                timeline.playing.collect { playing ->
                    musicPaused = !playing
                    updateMute()
                    (_sync.value as? SyncState.On)?.let { _sync.value = it.copy(musicPaused = !playing) }
                    if (playing) realign()
                }
            }
            launch { timeline.seeks.collect { realign() } }
            launch {
                // The first timestamps after starting are rough; check again soon, then every few seconds.
                delay(SETTLE_MS)
                while (true) {
                    if (timeline.playing.value) correct()
                    delay(CHECK_INTERVAL_MS)
                }
            }
        }
    }

    /** Where the song's beats fall on the metronome output now, or null when either side can't tell. */
    private fun freshAlignment(): Pair<Alignment, HeardFrame>? {
        val current = grid ?: return null
        val music = timeline.snapshot() ?: return null
        val heard = engine.heardFrame() ?: return null
        return SongSync.align(current, music, heard, engine.outputSampleRate) to heard
    }

    private fun realign() {
        val (fresh, _) = freshAlignment() ?: return
        alignment = fresh
        engine.align(fresh)
    }

    private fun correct() {
        val (fresh, heard) = freshAlignment() ?: return
        val current = alignment
        if (current != null && abs(SongSync.errorMs(current, fresh, heard.frame, engine.outputSampleRate)) <= MAX_ERROR_MS) {
            return
        }
        alignment = fresh
        engine.align(fresh)
    }

    /** Not saved, but kept across stop and start. */
    fun setUserMuted(muted: Boolean) {
        userMuted.value = muted
        updateMute()
    }

    private fun updateMute() {
        engine.setMuted(inCall || focusPaused || musicPaused || userMuted.value)
    }

    private fun requestFocus() {
        holdsFocus = audioManager?.requestAudioFocus(focusRequest) == AudioManager.AUDIOFOCUS_REQUEST_GRANTED
    }

    private fun abandonFocus() {
        if (!holdsFocus) return
        audioManager?.abandonAudioFocusRequest(focusRequest)
        holdsFocus = false
    }

    private fun onFocusChange(change: Int) {
        when (change) {
            AudioManager.AUDIOFOCUS_LOSS -> {
                focusLossJob?.cancel()
                focusLossJob = scope.launch(Dispatchers.Main) {
                    // MPlay's own player takes focus when it starts; give it a moment to report that it is playing.
                    delay(FOCUS_LOSS_GRACE_MS)
                    if (musicPlaying.value) {
                        abandonFocus()
                        setFocusPaused(false)
                    } else {
                        stop()
                    }
                }
            }
            AudioManager.AUDIOFOCUS_LOSS_TRANSIENT -> setFocusPaused(true)
            AudioManager.AUDIOFOCUS_GAIN -> setFocusPaused(false)
        }
    }

    private fun setFocusPaused(paused: Boolean) {
        focusPaused = paused
        updateMute()
    }

    /** Polling the audio mode covers every Android version and needs no phone-state permission. */
    private fun watchCalls() {
        callWatch?.cancel()
        callWatch = scope.launch {
            while (isActive) {
                val mode = audioManager?.mode ?: AudioManager.MODE_NORMAL
                val calling = mode == AudioManager.MODE_IN_CALL ||
                    mode == AudioManager.MODE_IN_COMMUNICATION ||
                    mode == AudioManager.MODE_RINGTONE
                if (calling != inCall) {
                    inCall = calling
                    updateMute()
                }
                delay(CALL_POLL_MS)
            }
        }
    }

    private companion object {
        const val TAG = "MetronomeController"
        const val SAVE_DELAY_MS = 400L
        const val CALL_POLL_MS = 500L
        const val FOCUS_LOSS_GRACE_MS = 600L
        const val CONNECT_TIMEOUT_MS = 3_000L
        const val SETTLE_MS = 500L
        const val CHECK_INTERVAL_MS = 2_000L
        const val MAX_ERROR_MS = 20.0
    }
}
