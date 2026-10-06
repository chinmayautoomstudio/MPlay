package com.autoomstudio.mplay.ui.separation

import android.net.Uri
import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider.AndroidViewModelFactory.Companion.APPLICATION_KEY
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.autoomstudio.mplay.MPlayApp
import com.autoomstudio.mplay.data.model.Song
import com.autoomstudio.mplay.data.settings.AppSettings
import com.autoomstudio.mplay.data.settings.SeparationSettings
import com.autoomstudio.mplay.data.stems.JobState
import com.autoomstudio.mplay.data.stems.SeparationJobEntity
import com.autoomstudio.mplay.data.stems.StemExporter
import com.autoomstudio.mplay.data.stems.StemMode
import com.autoomstudio.mplay.data.stems.StemRepository
import com.autoomstudio.mplay.data.stems.StemSetEntity
import com.autoomstudio.mplay.separation.EnqueueResult
import com.autoomstudio.mplay.separation.ModelImporter
import com.autoomstudio.mplay.separation.ModelState
import com.autoomstudio.mplay.separation.SeparationController
import com.autoomstudio.mplay.separation.UnsupportedReason
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

sealed interface SeparationMessage {
    data class Queued(val count: Int) : SeparationMessage
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

    private val _noticeRequest = MutableStateFlow<List<Song>?>(null)

    /** Songs waiting for the one-time notice to be confirmed. */
    val noticeRequest: StateFlow<List<Song>?> = _noticeRequest.asStateFlow()

    private val _modelRequest = MutableStateFlow<List<Song>?>(null)

    /** Songs waiting for the model to be installed; the dialog explains what that takes. */
    val modelRequest: StateFlow<List<Song>?> = _modelRequest.asStateFlow()

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
            if (settings.separationNoticeShown()) enqueue(songs) else _noticeRequest.value = songs
        }
    }

    fun confirmNotice() {
        val songs = _noticeRequest.value ?: return
        _noticeRequest.value = null
        viewModelScope.launch {
            settings.setSeparationNoticeShown()
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
        val message = when (val result = controller.enqueue(songs)) {
            is EnqueueResult.Queued -> SeparationMessage.Queued(result.count)
            EnqueueResult.NothingNew -> SeparationMessage.NothingNew
            is EnqueueResult.NotEnoughStorage -> SeparationMessage.NotEnoughStorage(result.requiredBytes)
            is EnqueueResult.Unsupported -> SeparationMessage.Unavailable
            EnqueueResult.ModelNotInstalled -> {
                _modelRequest.value = songs
                return
            }
        }
        _messages.send(message)
    }

    fun cancel(jobId: Long) {
        viewModelScope.launch { controller.cancel(jobId) }
    }

    fun retry(jobId: Long) {
        viewModelScope.launch { controller.retry(jobId) }
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
