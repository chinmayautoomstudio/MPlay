package com.autoomstudio.mplay.ui.metronome

import android.os.SystemClock
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider.AndroidViewModelFactory.Companion.APPLICATION_KEY
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.autoomstudio.mplay.MPlayApp
import com.autoomstudio.mplay.data.model.Song
import com.autoomstudio.mplay.data.tempo.SongTempoAnalyzer
import com.autoomstudio.mplay.metronome.BeatTick
import com.autoomstudio.mplay.metronome.MetronomeController
import com.autoomstudio.mplay.metronome.MetronomeSettings
import com.autoomstudio.mplay.metronome.MetronomeState
import com.autoomstudio.mplay.metronome.TapTempo
import com.autoomstudio.mplay.metronome.TempoEstimate
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

/** Detect BPM for one song; [songId] tells the sheet whether the result belongs to the song now playing. */
sealed interface TempoDetection {
    val songId: Long?

    data object Idle : TempoDetection {
        override val songId: Long? = null
    }

    data class Running(override val songId: Long, val progress: Float) : TempoDetection

    data class Done(override val songId: Long, val estimate: TempoEstimate) : TempoDetection

    data class Failed(override val songId: Long) : TempoDetection
}

class MetronomeViewModel(
    private val controller: MetronomeController,
    private val tempoAnalyzer: SongTempoAnalyzer,
) : ViewModel() {

    val state: StateFlow<MetronomeState> = controller.state
    val beat: StateFlow<BeatTick?> = controller.beat

    private val tapTempo = TapTempo()
    private val _tapCount = MutableStateFlow(0)

    /** Taps in the current tap-tempo run; 0 when idle. */
    val tapCount: StateFlow<Int> = _tapCount.asStateFlow()

    private val _detection = MutableStateFlow<TempoDetection>(TempoDetection.Idle)
    val detection: StateFlow<TempoDetection> = _detection.asStateFlow()

    private var tapReset: Job? = null
    private var detectJob: Job? = null

    fun update(transform: (MetronomeSettings) -> MetronomeSettings) = controller.update(transform)

    fun toggle() = controller.toggle()

    fun tap() {
        val bpm = tapTempo.tap(SystemClock.elapsedRealtime())
        _tapCount.value = tapTempo.count
        if (bpm != null) controller.update { it.copy(bpm = bpm) }
        tapReset?.cancel()
        tapReset = viewModelScope.launch {
            delay(TAP_RESET_MS)
            tapTempo.reset()
            _tapCount.value = 0
        }
    }

    /** Fills in the BPM from [song], instantly when it was detected before. */
    fun detect(song: Song) {
        if ((_detection.value as? TempoDetection.Running)?.songId == song.id) return
        detectJob?.cancel()
        _detection.value = TempoDetection.Running(song.id, 0f)
        detectJob = viewModelScope.launch {
            var shown = 0f
            val estimate = tempoAnalyzer.detect(song) { progress ->
                if (progress - shown >= 0.01f) {
                    shown = progress
                    _detection.value = TempoDetection.Running(song.id, progress)
                }
            }
            _detection.value = if (estimate != null) {
                controller.update { it.copy(bpm = estimate.bpm.roundToInt()) }
                TempoDetection.Done(song.id, estimate)
            } else {
                TempoDetection.Failed(song.id)
            }
        }
    }

    /** Doubles or halves the BPM, for detections that landed on half or double time. */
    fun scaleBpm(factor: Double) = controller.update { it.copy(bpm = (it.bpm * factor).roundToInt()) }

    companion object {
        private const val TAP_RESET_MS = 2_000L

        val Factory = viewModelFactory {
            initializer {
                val app = this[APPLICATION_KEY] as MPlayApp
                MetronomeViewModel(app.container.metronomeController, app.container.tempoAnalyzer)
            }
        }
    }
}
