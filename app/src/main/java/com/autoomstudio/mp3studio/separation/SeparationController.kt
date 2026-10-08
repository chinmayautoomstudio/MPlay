package com.autoomstudio.mp3studio.separation

import com.autoomstudio.mp3studio.data.model.Song
import com.autoomstudio.mp3studio.data.settings.AppSettings
import com.autoomstudio.mp3studio.data.stems.JobError
import com.autoomstudio.mp3studio.data.stems.JobState
import com.autoomstudio.mp3studio.data.stems.StemRepository
import com.autoomstudio.mp3studio.data.usage.SeparationUsageGate
import com.autoomstudio.mp3studio.data.usage.UsageDecision
import com.autoomstudio.mp3studio.data.usage.UsageJob
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.launch
import java.util.UUID

sealed interface EnqueueResult {
    data class Queued(val count: Int) : EnqueueResult

    /** Only [count] songs fit in this week's Free limit; [skipped] weren't queued (PRD US7). */
    data class PartiallyQueued(val count: Int, val skipped: Int, val resetsAt: Long) : EnqueueResult

    /** The Free weekly limit is used up; nothing was queued (PRD US7). */
    data class LimitReached(val resetsAt: Long) : EnqueueResult

    /** Free users need the server to start a separation (PRD PL5). */
    data object NeedsInternet : EnqueueResult

    /** Everything picked was already separated or waiting. */
    data object NothingNew : EnqueueResult

    data class NotEnoughStorage(val requiredBytes: Long, val freeBytes: Long) : EnqueueResult

    data class Unsupported(val reasons: List<UnsupportedReason>) : EnqueueResult

    data object ModelNotInstalled : EnqueueResult

    data object SignedOut : EnqueueResult
}

/** What the UI talks to; schedules the backend whenever the queue or its settings change. */
class SeparationController(
    private val repository: StemRepository,
    private val backend: SeparationBackend,
    private val modelProvider: ModelProvider,
    private val settings: AppSettings,
    private val scope: CoroutineScope,
    /** Every new or retried job first reserves an AI Vocal Separator use (PRD US2). */
    private val usageGate: SeparationUsageGate,
    /** Separation only runs for a signed-in user (PRD AU1); the queue waits while signed out. */
    private val signedInUserId: () -> String?,
    private val newJobRef: () -> String = { UUID.randomUUID().toString() },
) {
    val deviceReasons: List<UnsupportedReason> get() = backend.deviceReasons

    val deviceEligible: Boolean get() = deviceReasons.isEmpty()

    val modelState: StateFlow<ModelState> = modelProvider.state

    val isAvailable: Boolean get() = deviceEligible && modelState.value == ModelState.Installed

    private val isSignedIn: Boolean get() = signedInUserId() != null

    /** Re-checks after the model file changed. */
    fun refreshModel() = modelProvider.refresh()

    /**
     * Applies settings changes to waiting work. Queued work resumes in [onSignedIn], once the saved session is known.
     */
    fun start() {
        if (!isAvailable) scope.launch { repository.failQueued(JobError.ModelUnavailable) }
        scope.launch {
            settings.separationSettings.drop(1).collect {
                if (isAvailable && isSignedIn && repository.hasQueued()) backend.schedule()
            }
        }
    }

    /** Resumes the queue after sign-in or an app restart with a saved session. */
    suspend fun onSignedIn() {
        if (isAvailable && repository.hasQueued()) backend.schedule()
    }

    /** Stops separating; the interrupted song goes back to the queue and waits for the next sign-in. */
    suspend fun onSignedOut() = backend.cancel()

    /** Stops separating when the app is removed from recents; the interrupted song goes back to the queue. */
    suspend fun pause() = backend.cancel()

    /** Picks the queue up again after [pause]. */
    suspend fun resume() {
        if (isAvailable && isSignedIn && repository.hasQueued()) backend.schedule()
    }

    suspend fun enqueue(songs: List<Song>): EnqueueResult {
        if (!deviceEligible) return EnqueueResult.Unsupported(deviceReasons)
        if (modelState.value != ModelState.Installed) return EnqueueResult.ModelNotInstalled
        val userId = signedInUserId() ?: return EnqueueResult.SignedOut
        val fresh = repository.newSongs(songs)
        if (fresh.isEmpty()) return EnqueueResult.NothingNew
        val required = StorageEstimate.requiredFreeBytes(fresh.map { it.durationMs })
        val free = repository.freeBytes()
        if (free < required) return EnqueueResult.NotEnoughStorage(required, free)

        val jobs = fresh.map { UsageJob(newJobRef(), it.title) }
        val decision = usageGate.reserve(jobs)
        val granted = when (decision) {
            is UsageDecision.Granted -> decision.refs.toSet()
            is UsageDecision.Partial -> decision.granted.toSet()
            is UsageDecision.LimitReached -> return EnqueueResult.LimitReached(decision.resetsAt)
            UsageDecision.NeedsInternet -> return EnqueueResult.NeedsInternet
        }
        val refs = fresh.zip(jobs)
            .filter { (_, job) -> job.jobRef in granted }
            .associate { (song, job) -> song.id to job.jobRef }
        val count = repository.enqueue(fresh.filter { it.id in refs }, refs, userId)
        if (count == 0) return EnqueueResult.NothingNew
        backend.schedule()
        return when (decision) {
            is UsageDecision.Partial -> EnqueueResult.PartiallyQueued(count, decision.skipped, decision.resetsAt)
            else -> EnqueueResult.Queued(count)
        }
    }

    suspend fun cancel(jobId: Long) = repository.cancel(jobId)

    /** A retried job needs a new reservation, since failing or cancelling gave the old one back. */
    suspend fun retry(jobId: Long): EnqueueResult {
        if (!isAvailable) return EnqueueResult.ModelNotInstalled
        val userId = signedInUserId() ?: return EnqueueResult.SignedOut
        val job = repository.job(jobId)
            ?.takeIf { it.state == JobState.Failed.name || it.state == JobState.Cancelled.name }
            ?: return EnqueueResult.NothingNew
        val ref = newJobRef()
        when (val decision = usageGate.reserve(listOf(UsageJob(ref, job.title)))) {
            is UsageDecision.LimitReached -> return EnqueueResult.LimitReached(decision.resetsAt)
            UsageDecision.NeedsInternet -> return EnqueueResult.NeedsInternet
            is UsageDecision.Partial, is UsageDecision.Granted -> Unit
        }
        repository.retry(jobId, ref, userId)
        backend.schedule()
        return EnqueueResult.Queued(1)
    }
}
