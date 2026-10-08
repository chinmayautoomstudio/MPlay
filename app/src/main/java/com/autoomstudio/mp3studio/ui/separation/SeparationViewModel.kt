package com.autoomstudio.mp3studio.ui.separation

import android.net.Uri
import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider.AndroidViewModelFactory.Companion.APPLICATION_KEY
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.autoomstudio.mp3studio.MPlayApp
import com.autoomstudio.mp3studio.data.model.Song
import com.autoomstudio.mp3studio.data.settings.AppSettings
import com.autoomstudio.mp3studio.data.settings.SeparationSettings
import com.autoomstudio.mp3studio.data.stems.JobState
import com.autoomstudio.mp3studio.data.stems.SeparationJobEntity
import com.autoomstudio.mp3studio.data.stems.StemExporter
import com.autoomstudio.mp3studio.data.stems.StemMode
import com.autoomstudio.mp3studio.data.stems.StemRepository
import com.autoomstudio.mp3studio.data.stems.StemSetEntity
import com.autoomstudio.mp3studio.separation.EnqueueResult
import com.autoomstudio.mp3studio.separation.ModelImporter
import com.autoomstudio.mp3studio.separation.ModelState
import com.autoomstudio.mp3studio.separation.SeparationController
import com.autoomstudio.mp3studio.separation.SeparationEstimate
import com.autoomstudio.mp3studio.separation.UnsupportedReason
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class SeparationUiState(
    val deviceEligible: Boolean = false,
    val unsupportedReasons: List<UnsupportedReason> = emptyList(),
    val modelState: ModelState = ModelState.Installed,
    val readySongIds: Set<Long> = emptySet(),
    val stemSets: List<StemSetEntity> = emptyList(),
    val jobs: List<SeparationJobEntity> = emptyList(),
    val cacheBytes: Long = 0L,
    val settings: SeparationSettings = SeparationSettings(),
) {
    /** The phone qualifies and the model is ready, so songs can be queued. */
    val available: Boolean get() = deviceEligible && modelState == ModelState.Installed

    /** The newest job for each song that is still waiting, running or failed. */
    val activeJobs: Map<Long, SeparationJobEntity> by lazy {
        jobs.filter { it.state == JobState.Queued.name || it.state == JobState.Running.name || it.state == JobState.Failed.name }
            .groupBy { it.songId }
            .mapValues { (_, songJobs) -> songJobs.maxBy { it.id } }
    }

    val runningJob: SeparationJobEntity? by lazy { jobs.firstOrNull { it.state == JobState.Running.name } }
}

/** [estimateMinutes] is null until a separation has finished on this phone. */
data class NoticeRequest(val songs: List<Song>, val estimateMinutes: Int?)

sealed interface SeparationMessage {
    data class Queued(val count: Int) : SeparationMessage

    /** Some songs went over the Free weekly limit; the snackbar offers the plans. */
    data class PartiallyQueued(val count: Int, val skipped: Int) : SeparationMessage
    data object NeedsInternet : SeparationMessage
    data object NothingNew : SeparationMessage
    data class NotEnoughStorage(val requiredBytes: Long) : SeparationMessage
    data object Unavailable : SeparationMessage
    data object StemsDeleted : SeparationMessage
    data object Exported : SeparationMessage
    data object ExportFailed : SeparationMessage
    data object ExportPermissionDenied : SeparationMessage
    data object ModelImported : SeparationMessage
    data object ModelImportFailed : SeparationMessage
    data object ModelImportWrongFile : SeparationMessage

    /** A song finished while the app was open; the snackbar offers to play it as instrumental. */
    data class Ready(val songId: Long, val title: String) : SeparationMessage

    data class Failed(val title: String, val error: String?) : SeparationMessage
}

class SeparationViewModel(
    private val controller: SeparationController,
    private val repository: StemRepository,
    private val settings: AppSettings,
    private val exporter: StemExporter,
    private val modelImporter: ModelImporter,
) : ViewModel() {

    val state: StateFlow<SeparationUiState> = combine(
        controller.modelState,
        repository.stemSets,
        repository.jobs,
        settings.separationSettings,
    ) { modelState, sets, jobs, separationSettings ->
        SeparationUiState(
            deviceEligible = controller.deviceEligible,
            unsupportedReasons = controller.deviceReasons,
            modelState = modelState,
            readySongIds = sets.keys,
            stemSets = sets.values.sortedByDescending { it.createdAt },
            jobs = jobs,
            cacheBytes = sets.values.sumOf { it.bytes },
            settings = separationSettings,
        )
    }.stateIn(
        viewModelScope,
        SharingStarted.Eagerly,
        SeparationUiState(
            deviceEligible = controller.deviceEligible,
            unsupportedReasons = controller.deviceReasons,
            modelState = controller.modelState.value,
        ),
    )

    private val _noticeRequest = MutableStateFlow<NoticeRequest?>(null)

    /** Songs waiting for the time notice to be confirmed. */
    val noticeRequest: StateFlow<NoticeRequest?> = _noticeRequest.asStateFlow()

    private val _modelRequest = MutableStateFlow<List<Song>?>(null)

    /** Songs waiting for the model to be installed; the dialog explains what that takes. */
    val modelRequest: StateFlow<List<Song>?> = _modelRequest.asStateFlow()

    private val _limitReachedAt = MutableStateFlow<Long?>(null)

    /** When the Free weekly limit stopped a separation: the reset time to show in the upgrade sheet (PRD US7). */
    val limitReachedAt: StateFlow<Long?> = _limitReachedAt.asStateFlow()

    private val _messages = Channel<SeparationMessage>(Channel.BUFFERED)
    val messages: Flow<SeparationMessage> = _messages.receiveAsFlow()

    init {
        viewModelScope.launch {
            var watched = emptyMap<Long, String>()
            state.collect { current ->
                watched.forEach { (songId, title) ->
                    val failed = current.activeJobs[songId]?.takeIf { it.state == JobState.Failed.name }
                    when {
                        songId in current.readySongIds -> _messages.send(SeparationMessage.Ready(songId, title))
                        failed != null -> _messages.send(SeparationMessage.Failed(title, failed.error))
                    }
                }
                watched = current.jobs
                    .filter { it.state == JobState.Queued.name || it.state == JobState.Running.name }
                    .associate { it.songId to it.title }
            }
        }
    }

    fun requestSeparation(songs: List<Song>) {
        if (songs.isEmpty()) return
        if (controller.modelState.value != ModelState.Installed) {
            _modelRequest.value = songs
            return
        }
        viewModelScope.launch {
            if (settings.separationNoticeHidden()) {
                enqueue(songs)
            } else {
                val minutes = SeparationEstimate.minutes(settings.separationSpeedFactor(), songs.map { it.durationMs })
                _noticeRequest.value = NoticeRequest(songs, minutes)
            }
        }
    }

    fun confirmNotice(dontShowAgain: Boolean) {
        val songs = _noticeRequest.value?.songs ?: return
        _noticeRequest.value = null
        viewModelScope.launch {
            if (dontShowAgain) settings.setSeparationNoticeHidden()
            enqueue(songs)
        }
    }

    fun dismissNotice() {
        _noticeRequest.value = null
    }

    fun dismissModelRequest() {
        _modelRequest.value = null
    }

    private suspend fun enqueue(songs: List<Song>) {
        report(controller.enqueue(songs), songs)
    }

    private suspend fun report(result: EnqueueResult, songs: List<Song>?) {
        val message = when (result) {
            is EnqueueResult.Queued -> SeparationMessage.Queued(result.count)
            is EnqueueResult.PartiallyQueued -> SeparationMessage.PartiallyQueued(result.count, result.skipped)
            is EnqueueResult.LimitReached -> {
                _limitReachedAt.value = result.resetsAt
                return
            }
            EnqueueResult.NeedsInternet -> SeparationMessage.NeedsInternet
            EnqueueResult.NothingNew -> SeparationMessage.NothingNew
            is EnqueueResult.NotEnoughStorage -> SeparationMessage.NotEnoughStorage(result.requiredBytes)
            is EnqueueResult.Unsupported, EnqueueResult.SignedOut -> SeparationMessage.Unavailable
            EnqueueResult.ModelNotInstalled -> {
                if (songs != null) _modelRequest.value = songs else _messages.send(SeparationMessage.Unavailable)
                return
            }
        }
        _messages.send(message)
    }

    fun dismissLimitReached() {
        _limitReachedAt.value = null
    }

    fun cancel(jobId: Long) {
        viewModelScope.launch { controller.cancel(jobId) }
    }

    /** Retrying reserves a new use; a refusal is shown like a new request's. A queued retry shows no message. */
    fun retry(jobId: Long) {
        viewModelScope.launch {
            val result = controller.retry(jobId)
            if (result !is EnqueueResult.Queued && result != EnqueueResult.NothingNew) report(result, songs = null)
        }
    }

    fun removeJob(jobId: Long) {
        viewModelScope.launch { repository.removeFinished(jobId) }
    }

    fun clearFinished() {
        viewModelScope.launch { repository.clearFinished() }
    }

    fun deleteStems(songId: Long) {
        viewModelScope.launch {
            repository.delete(listOf(songId))
            _messages.send(SeparationMessage.StemsDeleted)
        }
    }

    fun deleteAllStems() {
        viewModelScope.launch {
            repository.deleteAll()
            _messages.send(SeparationMessage.StemsDeleted)
        }
    }

    /** Saves the song's vocals or instrumental to Music/MPlay Stems. */
    fun export(songId: Long, mode: StemMode) {
        val set = state.value.stemSets.firstOrNull { it.songId == songId }
        if (set == null) {
            viewModelScope.launch { _messages.send(SeparationMessage.ExportFailed) }
            return
        }
        export(set, mode)
    }

    fun exportPermissionDenied() {
        viewModelScope.launch { _messages.send(SeparationMessage.ExportPermissionDenied) }
    }

    fun export(set: StemSetEntity, mode: StemMode) {
        viewModelScope.launch {
            val message = try {
                exporter.export(set, mode)
                SeparationMessage.Exported
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.w(TAG, "Export failed", e)
                SeparationMessage.ExportFailed
            }
            _messages.send(message)
        }
    }

    fun setChargingOnly(enabled: Boolean) {
        viewModelScope.launch { settings.setSeparationChargingOnly(enabled) }
    }

    fun setPauseOnLowBattery(enabled: Boolean) {
        viewModelScope.launch { settings.setSeparationPauseOnLowBattery(enabled) }
    }

    /** Copies a picked model file into app storage, then continues with any songs that were waiting for it. */
    fun importModel(uri: Uri) {
        viewModelScope.launch {
            val message = try {
                modelImporter.import(uri)
                controller.refreshModel()
                SeparationMessage.ModelImported
            } catch (e: CancellationException) {
                throw e
            } catch (e: ModelImporter.WrongModelException) {
                Log.w(TAG, "Picked file is not the model", e)
                SeparationMessage.ModelImportWrongFile
            } catch (e: Exception) {
                Log.w(TAG, "Model import failed", e)
                SeparationMessage.ModelImportFailed
            }
            _messages.send(message)
            val waiting = _modelRequest.value
            _modelRequest.value = null
            if (message == SeparationMessage.ModelImported && waiting != null) requestSeparation(waiting)
        }
    }

    companion object {
        private const val TAG = "SeparationViewModel"

        val Factory = viewModelFactory {
            initializer {
                val app = this[APPLICATION_KEY] as MPlayApp
                val container = app.container
                SeparationViewModel(
                    container.separationController,
                    container.stemRepository,
                    container.appSettings,
                    StemExporter(app),
                    ModelImporter(app),
                )
            }
        }
    }
}
