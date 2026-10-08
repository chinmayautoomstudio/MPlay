package com.autoomstudio.mp3studio.ui.metronome

import android.os.SystemClock
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider.AndroidViewModelFactory.Companion.APPLICATION_KEY
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.autoomstudio.mp3studio.MPlayApp
import com.autoomstudio.mp3studio.data.model.Song
import com.autoomstudio.mp3studio.data.plan.Feature
import com.autoomstudio.mp3studio.data.tempo.GatedDetection
import com.autoomstudio.mp3studio.data.tempo.TempoDetectionGate
import com.autoomstudio.mp3studio.metronome.BeatGrid
import com.autoomstudio.mp3studio.metronome.BeatTick
import com.autoomstudio.mp3studio.metronome.MeterEstimate
import com.autoomstudio.mp3studio.metronome.MetronomeController
import com.autoomstudio.mp3studio.metronome.MetronomeSettings
import com.autoomstudio.mp3studio.metronome.MetronomeState
import com.autoomstudio.mp3studio.metronome.SyncState
import com.autoomstudio.mp3studio.metronome.TapTempo
import com.autoomstudio.mp3studio.metronome.TempoConfidence
import com.autoomstudio.mp3studio.metronome.TempoEstimate
import com.autoomstudio.mp3studio.metronome.TimeSignature
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
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

    /**
     * [meter] is null when the time signature couldn't be told (MT19). [applied] says whether the metronome took
     * it; when false the sheet offers it as a suggestion. [grid] places the song's clicks, for syncing.
     */
    data class Done(
        override val songId: Long,
        val estimate: TempoEstimate,
        val meter: MeterEstimate? = null,
        val applied: Boolean = false,
        val grid: BeatGrid? = null,
    ) : TempoDetection

    data class Failed(override val songId: Long) : TempoDetection
}

/** A detected time signature was applied (MT19); [previous] and [tempoBpm] are what Undo puts back. */
data class MeterApplied(
    val songId: Long,
    val timeSignature: TimeSignature,
    val previous: TimeSignature,
    val tempoBpm: Int,
)

class MetronomeViewModel(
    private val controller: MetronomeController,
    private val tempoGate: TempoDetectionGate,
) : ViewModel() {

    val state: StateFlow<MetronomeState> = controller.state
    val beat: StateFlow<BeatTick?> = controller.beat
    val sync: StateFlow<SyncState> = controller.sync

    private val tapTempo = TapTempo()
    private val _tapCount = MutableStateFlow(0)

    /** Taps in the current tap-tempo run; 0 when idle. */
    val tapCount: StateFlow<Int> = _tapCount.asStateFlow()

    private val _detection = MutableStateFlow<TempoDetection>(TempoDetection.Idle)
    val detection: StateFlow<TempoDetection> = _detection.asStateFlow()

    private val _meterApplied = MutableSharedFlow<MeterApplied>(extraBufferCapacity = 1)

    /** One-shot events for the "Time signature set to …" snackbar. */
    val meterApplied: SharedFlow<MeterApplied> = _meterApplied.asSharedFlow()

    private val _upgradeRequests = MutableSharedFlow<Feature>(extraBufferCapacity = 1)

    /** Detect BPM was tapped for a song never analyzed while BPM Detector isn't in the plan (PRD flow 6). */
    val upgradeRequests: SharedFlow<Feature> = _upgradeRequests.asSharedFlow()

    private var tapReset: Job? = null
    private var detectJob: Job? = null

    fun update(transform: (MetronomeSettings) -> MetronomeSettings) = controller.update(transform = transform)

    fun toggle() = controller.toggle()

    fun toggleMute() = controller.setUserMuted(!controller.state.value.muted)

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

    /**
     * Fills in the BPM from [song], instantly when it was detected before, and the time signature when it is at
     * least fairly sure of it (MT18, MT19), then puts the clicks on the song's beats. A new detection needs BPM
     * Detector in the plan; otherwise [upgradeRequests] fires.
     */
    fun detect(song: Song) {
        if ((_detection.value as? TempoDetection.Running)?.songId == song.id) return
        detectJob?.cancel()
        _detection.value = TempoDetection.Running(song.id, 0f)
        detectJob = viewModelScope.launch {
            var shown = 0f
            val result = tempoGate.detect(song) { progress ->
                if (progress - shown >= 0.01f) {
                    shown = progress
                    _detection.value = TempoDetection.Running(song.id, progress)
                }
            }
            if (result is GatedDetection.Locked) {
                _detection.value = TempoDetection.Idle
                _upgradeRequests.tryEmit(Feature.BpmDetector)
                return@launch
            }
            val rhythm = (result as GatedDetection.Finished).rhythm
            if (rhythm == null) {
                _detection.value = TempoDetection.Failed(song.id)
                return@launch
            }
            val meter = rhythm.meter
            val done = TempoDetection.Done(song.id, rhythm.tempo, meter, grid = rhythm.grid)
            if (meter != null && meter.confidence != TempoConfidence.Low) {
                applyMeter(done)
            } else {
                controller.update(keepSync = true) { it.copy(bpm = rhythm.tempo.bpm.roundToInt()) }
                _detection.value = done
            }
            follow(_detection.value as TempoDetection.Done)
        }
    }

    /** Turns following the song on or off; on needs a detection with a beat grid for that song. */
    fun toggleSync() {
        if (controller.sync.value is SyncState.On) {
            controller.stopSync()
        } else {
            (_detection.value as? TempoDetection.Done)?.let(::follow)
        }
    }

    private fun follow(done: TempoDetection.Done) {
        gridFor(done)?.let { controller.syncTo(done.songId, it) }
    }

    /** Re-places the clicks after the BPM was changed for [done], if they were following its song. */
    private fun refollow(done: TempoDetection.Done) {
        if ((controller.sync.value as? SyncState.On)?.songId == done.songId) follow(done)
    }

    /**
     * The grid for the BPM [done] set. The detected grid clicks at the meter's rate; when the meter wasn't taken,
     * the BPM is the detected tempo, which for 6/8 is a whole number of those clicks.
     */
    private fun gridFor(done: TempoDetection.Done): BeatGrid? {
        val grid = done.grid ?: return null
        val meter = done.meter
        if (meter == null || done.applied) return grid
        val clicksPerBeat = (meter.clickBpm / done.estimate.bpm).roundToInt().coerceAtLeast(1)
        return grid.copy(periodMs = grid.periodMs * clicksPerBeat)
    }

    /** Takes the suggested time signature (and its BPM) from the last detection, for the Low-confidence chip. */
    fun applyMeter() {
        val done = _detection.value as? TempoDetection.Done ?: return
        if (done.meter == null || done.applied) return
        applyMeter(done)
        refollow(_detection.value as TempoDetection.Done)
    }

    private fun applyMeter(done: TempoDetection.Done) {
        val meter = done.meter ?: return
        val previous = controller.state.value.settings.timeSignature
        controller.update(keepSync = true) {
            it.copy(bpm = meter.clickBpm, beatsPerBar = meter.beatsPerBar, beatUnit = meter.beatUnit)
        }
        _detection.value = done.copy(applied = true)
        _meterApplied.tryEmit(MeterApplied(done.songId, meter.timeSignature, previous, done.estimate.bpm.roundToInt()))
    }

    /** Puts back the time signature from before [event] and the plain detected BPM. */
    fun undoMeter(event: MeterApplied) {
        val done = (_detection.value as? TempoDetection.Done)?.takeIf { it.songId == event.songId }
        controller.update(keepSync = done != null) {
            it.copy(bpm = event.tempoBpm, beatsPerBar = event.previous.beats, beatUnit = event.previous.unit)
        }
        if (done == null) return
        val undone = done.copy(applied = false)
        _detection.value = undone
        refollow(undone)
    }

    /** Doubles or halves the BPM, for detections that landed on half or double time; sync stays on. */
    fun scaleBpm(factor: Double) = controller.scaleTempo(factor)

    companion object {
        private const val TAP_RESET_MS = 2_000L

        val Factory = viewModelFactory {
            initializer {
                val container = (this[APPLICATION_KEY] as MPlayApp).container
                MetronomeViewModel(
                    container.metronomeController,
                    TempoDetectionGate(container.tempoAnalyzer) {
                        container.entitlementsRepository.canUse(Feature.BpmDetector, container.signedInUserId())
                    },
                )
            }
        }
    }
}
