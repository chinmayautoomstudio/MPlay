package com.autoomstudio.mplay.separation

import com.autoomstudio.mplay.data.model.Song
import com.autoomstudio.mplay.data.settings.AppSettings
import com.autoomstudio.mplay.data.stems.JobError
import com.autoomstudio.mplay.data.stems.StemRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.launch

sealed interface EnqueueResult {
    data class Queued(val count: Int) : EnqueueResult

    /** Everything picked was already separated or waiting. */
    data object NothingNew : EnqueueResult

    data class NotEnoughStorage(val requiredBytes: Long, val freeBytes: Long) : EnqueueResult

    data class Unsupported(val reasons: List<UnsupportedReason>) : EnqueueResult

    data object ModelNotInstalled : EnqueueResult
}

/** What the UI talks to; schedules the backend whenever the queue or its settings change. */
class SeparationController(
    private val repository: StemRepository,
    private val backend: SeparationBackend,
    private val modelProvider: ModelProvider,
    private val settings: AppSettings,
    private val scope: CoroutineScope,
) {
    val deviceReasons: List<UnsupportedReason> get() = backend.deviceReasons

    val deviceEligible: Boolean get() = deviceReasons.isEmpty()

    val modelState: StateFlow<ModelState> = modelProvider.state

    val isAvailable: Boolean get() = deviceEligible && modelState.value == ModelState.Installed

    /** Re-checks after the model file changed. */
    fun refreshModel() = modelProvider.refresh()

    /** Resumes queued work after a restart and applies settings changes to waiting work. */
    fun start() {
        if (isAvailable) {
            scope.launch { if (repository.hasQueued()) backend.schedule() }
        } else {
            scope.launch { repository.failQueued(JobError.ModelUnavailable) }
        }
        scope.launch {
            settings.separationSettings.drop(1).collect {
                if (isAvailable && repository.hasQueued()) backend.schedule()
            }
        }
    }

    suspend fun enqueue(songs: List<Song>): EnqueueResult {
        if (!deviceEligible) return EnqueueResult.Unsupported(deviceReasons)
        if (modelState.value != ModelState.Installed) return EnqueueResult.ModelNotInstalled
        val required = StorageEstimate.requiredFreeBytes(songs.map { it.durationMs })
        val free = repository.freeBytes()
        if (free < required) return EnqueueResult.NotEnoughStorage(required, free)
        val count = repository.enqueue(songs)
        if (count == 0) return EnqueueResult.NothingNew
        backend.schedule()
        return EnqueueResult.Queued(count)
    }

    suspend fun cancel(jobId: Long) = repository.cancel(jobId)

    suspend fun retry(jobId: Long) {
        if (!isAvailable) return
        repository.retry(jobId)
        backend.schedule()
    }
}
