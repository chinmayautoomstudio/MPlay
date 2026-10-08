package com.autoomstudio.mp3studio.data.usage

import com.autoomstudio.mp3studio.data.plan.SeparatorUsage
import kotlinx.coroutines.CancellationException

sealed interface UsageDecision {
    /** Every job may be queued; [refs] are their job refs in order. */
    data class Granted(val refs: List<String>) : UsageDecision

    /** Only [granted] may be queued; [skipped] went over this week's limit. */
    data class Partial(val granted: List<String>, val skipped: Int, val resetsAt: Long) : UsageDecision

    data class LimitReached(val resetsAt: Long) : UsageDecision

    /** The server couldn't be reached and the cached plan doesn't allow queueing without it (PRD PL5). */
    data object NeedsInternet : UsageDecision
}

/**
 * Asks the server for one AI Vocal Separator use per job before anything is queued (PRD US2). Free users need the
 * server; a cached Trial or Pro ([unlimitedOffline]) may queue offline and the server learns about it on completion.
 */
class SeparationUsageGate(
    private val backend: UsageBackend,
    private val unlimitedOffline: suspend () -> Boolean,
    private val onUsage: suspend (SeparatorUsage) -> Unit = {},
) {
    suspend fun reserve(jobs: List<UsageJob>): UsageDecision {
        if (jobs.isEmpty()) return UsageDecision.Granted(emptyList())
        val refs = jobs.map { it.jobRef }
        val answer = try {
            backend.reserve(jobs)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            return if (unlimitedOffline()) UsageDecision.Granted(refs) else UsageDecision.NeedsInternet
        }
        onUsage(answer.usage)
        val granted = refs.filter { it in answer.granted }
        return when {
            granted.size == refs.size -> UsageDecision.Granted(granted)
            granted.isEmpty() -> UsageDecision.LimitReached(answer.usage.resetsAt)
            else -> UsageDecision.Partial(granted, refs.size - granted.size, answer.usage.resetsAt)
        }
    }
}
