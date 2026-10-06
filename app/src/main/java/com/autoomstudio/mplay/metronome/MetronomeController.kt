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

data class MetronomeState(val running: Boolean = false, val settings: MetronomeSettings = MetronomeSettings())

/**
 * The one metronome shared by the Metronome tab, the Now Playing sheet and the service notification.
 *
 * Audio focus (MT13): while MPlay's music plays, the metronome never requests focus, so the music is never paused
 * or ducked. Otherwise it requests focus like any player; losing it to MPlay's own player keeps the metronome
 * running, while losing it to another app stops it. Calls silence it until they end (MT16).
 */
class MetronomeController(
    private val context: Context,
    private val appSettings: AppSettings,
    private val musicPlaying: StateFlow<Boolean>,
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

    val state: StateFlow<MetronomeState> = combine(running, settings, ::MetronomeState)
        .stateIn(scope, SharingStarted.Eagerly, MetronomeState())

    private var saveJob: Job? = null
    private var callWatch: Job? = null
    private var focusLossJob: Job? = null
    private var holdsFocus = false

    @Volatile private var inCall = false

    @Volatile private var focusPaused = false

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

    fun update(transform: (MetronomeSettings) -> MetronomeSettings) {
        val next = transform(settings.value).let { it.copy(bpm = MetronomeSettings.clampBpm(it.bpm)) }
        if (next == settings.value) return
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
        running.value = false
        callWatch?.cancel()
        focusLossJob?.cancel()
        engine.stop()
        _beat.value = null
        abandonFocus()
        inCall = false
        focusPaused = false
        engine.setMuted(false)
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
        engine.setMuted(inCall || focusPaused)
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
                    engine.setMuted(inCall || focusPaused)
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
    }
}
