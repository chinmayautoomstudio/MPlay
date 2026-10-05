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
import com.autoomstudio.mplay.data.stems.SeparationJobEntity
import com.autoomstudio.mplay.data.stems.StemExporter
import com.autoomstudio.mplay.data.stems.StemMode
import com.autoomstudio.mplay.data.stems.StemRepository
import com.autoomstudio.mplay.data.stems.StemSetEntity
import com.autoomstudio.mplay.separation.EnqueueResult
import com.autoomstudio.mplay.separation.ModelImporter
import com.autoomstudio.mplay.separation.SeparationAvailability
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
    val available: Boolean = false,
    val unavailableReasons: List<UnsupportedReason> = emptyList(),
    val readySongIds: Set<Long> = emptySet(),
    val stemSets: List<StemSetEntity> = emptyList(),
    val jobs: List<SeparationJobEntity> = emptyList(),
    val cacheBytes: Long = 0L,
    val settings: SeparationSettings = SeparationSettings(),
)

sealed interface SeparationMessage {
    data class Queued(val count: Int) : SeparationMessage
    data object NothingNew : SeparationMessage
    data class NotEnoughStorage(val requiredBytes: Long) : SeparationMessage
    data object Unavailable : SeparationMessage
    data object StemsDeleted : SeparationMessage
    data object Exported : SeparationMessage
    data object ExportFailed : SeparationMessage
    data object ModelImported : SeparationMessage
    data object ModelImportFailed : SeparationMessage
}

class SeparationViewModel(
    private val controller: SeparationController,
    private val repository: StemRepository,
    private val settings: AppSettings,
    private val exporter: StemExporter,
    private val modelImporter: ModelImporter,
) : ViewModel() {

    val state: StateFlow<SeparationUiState> = combine(
        controller.availabilityFlow,
        repository.stemSets,
        repository.jobs,
        settings.separationSettings,
    ) { availability, sets, jobs, separationSettings ->
        SeparationUiState(
            available = availability is SeparationAvailability.Available,
            unavailableReasons = (availability as? SeparationAvailability.Unavailable)?.reasons.orEmpty(),
            readySongIds = sets.keys,
            stemSets = sets.values.sortedByDescending { it.createdAt },
            jobs = jobs,
            cacheBytes = sets.values.sumOf { it.bytes },
            settings = separationSettings,
        )
    }.stateIn(
        viewModelScope,
        SharingStarted.Eagerly,
        SeparationUiState(available = controller.isAvailable),
    )

    private val _noticeRequest = MutableStateFlow<List<Song>?>(null)

    /** Songs waiting for the one-time notice to be confirmed. */
    val noticeRequest: StateFlow<List<Song>?> = _noticeRequest.asStateFlow()

    private val _messages = Channel<SeparationMessage>(Channel.BUFFERED)
    val messages: Flow<SeparationMessage> = _messages.receiveAsFlow()

    fun requestSeparation(songs: List<Song>) {
        if (songs.isEmpty()) return
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

    private suspend fun enqueue(songs: List<Song>) {
        val message = when (val result = controller.enqueue(songs)) {
            is EnqueueResult.Queued -> SeparationMessage.Queued(result.count)
            EnqueueResult.NothingNew -> SeparationMessage.NothingNew
            is EnqueueResult.NotEnoughStorage -> SeparationMessage.NotEnoughStorage(result.requiredBytes)
            is EnqueueResult.Unavailable -> SeparationMessage.Unavailable
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

    fun importModel(uri: Uri) {
        viewModelScope.launch {
            val message = try {
                modelImporter.import(uri)
                controller.refreshAvailability()
                SeparationMessage.ModelImported
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.w(TAG, "Model import failed", e)
                SeparationMessage.ModelImportFailed
            }
            _messages.send(message)
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
