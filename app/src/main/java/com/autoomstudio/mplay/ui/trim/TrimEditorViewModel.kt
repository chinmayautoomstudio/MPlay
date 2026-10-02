package com.autoomstudio.mplay.ui.trim

import android.app.Application
import android.net.Uri
import androidx.annotation.OptIn
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider.AndroidViewModelFactory.Companion.APPLICATION_KEY
import androidx.lifecycle.createSavedStateHandle
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.transformer.ExportException
import com.autoomstudio.mplay.MPlayApp
import com.autoomstudio.mplay.data.clip.ClipExporter
import com.autoomstudio.mplay.data.clip.ClipNames
import com.autoomstudio.mplay.data.clip.ClipStore
import com.autoomstudio.mplay.data.clip.NotEnoughStorageException
import com.autoomstudio.mplay.data.clip.RingtoneSetter
import com.autoomstudio.mplay.data.clip.SoundType
import com.autoomstudio.mplay.data.clip.TrimRange
import com.autoomstudio.mplay.data.clip.WaveformExtractor
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.io.IOException

enum class TrimMode { Cut, Ringtone }

sealed interface WaveformState {
    /** [partial] holds the peaks decoded so far, with the rest still zero. */
    class Loading(val partial: FloatArray? = null) : WaveformState
    class Ready(val peaks: FloatArray) : WaveformState
    data object Failed : WaveformState
}

sealed interface ExportStatus {
    /** [progress] is null until Transformer can estimate it. */
    data class Exporting(val progress: Float?) : ExportStatus
    data object Saving : ExportStatus
}

enum class TrimError { UnsupportedFile, NotEnoughStorage, PermissionDenied, ExportFailed, SaveFailed }

/** One-shot results shown in a snackbar. */
sealed interface TrimMessage {
    data class Saved(val uri: Uri) : TrimMessage
    data class SoundSet(val type: SoundType, val previous: Uri?) : TrimMessage

    /** The clip was saved and marked, but the app may not change the default sound. */
    data class SoundNotSet(val type: SoundType) : TrimMessage
    data object SaveUndone : TrimMessage
    data object SoundUndone : TrimMessage
    data class Failed(val error: TrimError) : TrimMessage
}

data class TrimEditorUiState(
    val title: String,
    val mode: TrimMode,
    val durationMs: Long,
    val range: TrimRange,
    val waveform: WaveformState = WaveformState.Loading(),
    val isPreviewing: Boolean = false,
    val export: ExportStatus? = null,
) {
    val canEdit: Boolean get() = export == null && waveform !is WaveformState.Failed
}

class TrimEditorViewModel(
    application: Application,
    savedState: SavedStateHandle,
    private val waveformExtractor: WaveformExtractor,
    private val exporter: ClipExporter,
    private val clipStore: ClipStore,
    private val ringtoneSetter: RingtoneSetter,
) : ViewModel() {

    private val sourceUri: Uri = checkNotNull(savedState[TrimEditorActivity.EXTRA_URI])
    private val artist: String = savedState[TrimEditorActivity.EXTRA_ARTIST] ?: ""
    private val title: String = savedState[TrimEditorActivity.EXTRA_TITLE] ?: ""
    private val durationMs: Long = savedState[TrimEditorActivity.EXTRA_DURATION] ?: 0L
    private val mode: TrimMode =
        TrimMode.valueOf(savedState[TrimEditorActivity.EXTRA_MODE] ?: TrimMode.Cut.name)

    private val _state = MutableStateFlow(
        TrimEditorUiState(
            title = title,
            mode = mode,
            durationMs = durationMs,
            range = if (mode == TrimMode.Ringtone) TrimRange.ringtoneDefault(durationMs) else TrimRange.full(durationMs),
        ),
    )
    val state: StateFlow<TrimEditorUiState> = _state.asStateFlow()

    private val _playheadMs = MutableStateFlow<Long?>(null)
    val playheadMs: StateFlow<Long?> = _playheadMs.asStateFlow()

    private val _messages = Channel<TrimMessage>(Channel.BUFFERED)
    val messages: Flow<TrimMessage> = _messages.receiveAsFlow()

    val defaultClipName: String = ClipNames.defaultName(title)

    private val player: ExoPlayer = ExoPlayer.Builder(application)
        .setAudioAttributes(
            AudioAttributes.Builder().setUsage(C.USAGE_MEDIA).setContentType(C.AUDIO_CONTENT_TYPE_MUSIC).build(),
            /* handleAudioFocus = */ true,
        )
        .build()
        .apply {
            setMediaItem(MediaItem.fromUri(sourceUri))
            addListener(object : Player.Listener {
                override fun onIsPlayingChanged(isPlaying: Boolean) {
                    // Another app taking audio focus pauses the preview; reflect that in the button.
                    if (!isPlaying && !playWhenReady && _state.value.isPreviewing) stopPreview()
                }

                override fun onPlaybackStateChanged(playbackState: Int) {
                    if (playbackState == Player.STATE_ENDED) seekTo(_state.value.range.startMs)
                }
            })
        }

    private var previewJob: Job? = null
    private var exportJob: Job? = null

    init {
        viewModelScope.launch {
            val waveform = try {
                WaveformState.Ready(
                    waveformExtractor.extract(sourceUri, durationMs) { partial ->
                        _state.update {
                            if (it.waveform is WaveformState.Loading) it.copy(waveform = WaveformState.Loading(partial)) else it
                        }
                    },
                )
            } catch (e: CancellationException) {
                throw e
            } catch (_: Exception) {
                WaveformState.Failed
            }
            _state.update { it.copy(waveform = waveform) }
        }
    }

    fun moveStart(toMs: Long) = updateRange { it.moveStart(toMs) }

    fun moveEnd(toMs: Long) = updateRange { it.moveEnd(toMs) }

    fun nudgeStart(forward: Boolean) {
        updateRange { it.nudgeStart(forward) }
        restartPreview()
    }

    fun nudgeEnd(forward: Boolean) = updateRange { it.nudgeEnd(forward) }

    /** Called when a handle drag ends; a running preview starts over from the new start. */
    fun onRangeChangeFinished() = restartPreview()

    fun toggleFullTrack() {
        updateRange { range ->
            when {
                !range.isFullTrack -> range.fullTrack()
                mode == TrimMode.Ringtone -> TrimRange.ringtoneDefault(durationMs)
                else -> range
            }
        }
        restartPreview()
    }

    fun togglePreview() {
        if (_state.value.isPreviewing) stopPreview() else startPreview()
    }

    fun stopPreview() {
        previewJob?.cancel()
        previewJob = null
        player.pause()
        _playheadMs.value = null
        _state.update { it.copy(isPreviewing = false) }
    }

    fun saveClip(name: String) = runExport {
        val uri = exportAndStore(name)
        _messages.send(TrimMessage.Saved(uri))
    }

    /** Saves the clip and sets it as the default [type]; without the permission the clip is only marked. */
    fun setAsSound(type: SoundType) = runExport {
        val uri = exportAndStore(defaultClipName)
        clipStore.markAsSound(uri, type)
        val previous = ringtoneSetter.current(type)
        val set = ringtoneSetter.canWrite() && ringtoneSetter.setDefault(type, uri)
        _messages.send(if (set) TrimMessage.SoundSet(type, previous) else TrimMessage.SoundNotSet(type))
    }

    fun canWriteSettings(): Boolean = ringtoneSetter.canWrite()

    fun cancelExport() {
        exportJob?.cancel()
    }

    fun undoSave(uri: Uri) {
        viewModelScope.launch {
            runCatching { clipStore.delete(uri) }
            _messages.send(TrimMessage.SaveUndone)
        }
    }

    fun undoSound(type: SoundType, previous: Uri?) {
        viewModelScope.launch {
            ringtoneSetter.setDefault(type, previous)
            _messages.send(TrimMessage.SoundUndone)
        }
    }

    private fun updateRange(transform: (TrimRange) -> TrimRange) {
        _state.update { it.copy(range = transform(it.range)) }
    }

    private fun startPreview() {
        if (player.playbackState == Player.STATE_IDLE) player.prepare()
        player.seekTo(_state.value.range.startMs)
        player.play()
        _state.update { it.copy(isPreviewing = true) }
        previewJob?.cancel()
        previewJob = viewModelScope.launch {
            while (isActive) {
                val range = _state.value.range
                val position = player.currentPosition
                if (position >= range.endMs || position < range.startMs - LOOP_TOLERANCE_MS) {
                    player.seekTo(range.startMs)
                    _playheadMs.value = range.startMs
                } else {
                    _playheadMs.value = position
                }
                delay(LOOP_CHECK_MS)
            }
        }
    }

    private fun restartPreview() {
        if (_state.value.isPreviewing) player.seekTo(_state.value.range.startMs)
    }

    private fun runExport(block: suspend () -> Unit) {
        if (exportJob?.isActive == true) return
        stopPreview()
        exportJob = viewModelScope.launch {
            try {
                block()
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _messages.send(TrimMessage.Failed(e.toTrimError()))
            } finally {
                _state.update { it.copy(export = null) }
            }
        }
    }

    private suspend fun exportAndStore(name: String): Uri {
        val range = _state.value.range
        clipStore.ensureSpaceFor(range)
        _state.update { it.copy(export = ExportStatus.Exporting(null)) }
        val temp = exporter.export(sourceUri, range) { progress ->
            _state.update { it.copy(export = ExportStatus.Exporting(progress)) }
        }
        _state.update { it.copy(export = ExportStatus.Saving) }
        return clipStore.save(temp, ClipNames.clean(name, defaultClipName), artist)
    }

    @OptIn(UnstableApi::class)
    private fun Exception.toTrimError(): TrimError = when (this) {
        is NotEnoughStorageException -> TrimError.NotEnoughStorage
        is SecurityException -> TrimError.PermissionDenied
        is ExportException -> when (errorCode) {
            ExportException.ERROR_CODE_DECODING_FAILED,
            ExportException.ERROR_CODE_DECODING_FORMAT_UNSUPPORTED,
            ExportException.ERROR_CODE_DECODER_INIT_FAILED,
            ExportException.ERROR_CODE_IO_FILE_NOT_FOUND,
            -> TrimError.UnsupportedFile
            else -> TrimError.ExportFailed
        }
        is IOException -> TrimError.SaveFailed
        else -> TrimError.ExportFailed
    }

    override fun onCleared() {
        player.release()
    }

    companion object {
        private const val LOOP_CHECK_MS = 50L
        private const val LOOP_TOLERANCE_MS = 250L

        val Factory = viewModelFactory {
            initializer {
                val app = this[APPLICATION_KEY] as MPlayApp
                val container = app.container
                TrimEditorViewModel(
                    application = app,
                    savedState = createSavedStateHandle(),
                    waveformExtractor = container.waveformExtractor,
                    exporter = container.clipExporter,
                    clipStore = container.clipStore,
                    ringtoneSetter = container.ringtoneSetter,
                )
            }
        }
    }
}
