package com.autoomstudio.mp3studio.data.usage

import com.autoomstudio.mp3studio.data.plan.SeparatorUsage
import com.autoomstudio.mp3studio.data.stems.UsageReportEntity
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/** The job outcomes waiting to be sent, kept in Room by [com.autoomstudio.mp3studio.data.stems.StemRepository]. */
interface UsageReportStore {
    suspend fun pending(): List<UsageReportEntity>

    suspend fun remove(jobRef: String)
}

/**
 * Tells the server how each reserved job ended (PRD US3): completed jobs count as a use, cancelled or failed ones give
 * the reservation back. Reports wait while signed out; reports made under another account are dropped, since only
 * that account's token could send them.
 */
class UsageReporter(
    private val store: UsageReportStore,
    private val backend: UsageBackend,
    private val currentUserId: () -> String?,
    private val onUsage: suspend (userId: String, SeparatorUsage) -> Unit = { _, _ -> },
) {
    private val mutex = Mutex()

    /**
     * Sends what it can. Returns false when a send failed and should be retried; signed out there is nothing to
     * retry until the next sign-in.
     */
    suspend fun sendPending(): Boolean = mutex.withLock {
        val userId = currentUserId() ?: return@withLock true
        var allSent = true
        for (report in store.pending()) {
            if (report.userId != userId) {
                store.remove(report.jobRef)
                continue
            }
            try {
                val usage = backend.finish(report.jobRef, report.outcome)
                store.remove(report.jobRef)
                onUsage(userId, usage)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                allSent = false
            }
        }
        allSent
    }

    /** Drops the reports of a deleted account; its jobs no longer exist on the server. */
    suspend fun forget(userId: String) = mutex.withLock {
        store.pending().filter { it.userId == userId }.forEach { store.remove(it.jobRef) }
    }
}
