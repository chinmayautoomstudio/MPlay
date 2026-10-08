package com.autoomstudio.mp3studio.data.usage

import com.autoomstudio.mp3studio.data.plan.SeparatorUsage
import com.autoomstudio.mp3studio.data.stems.UsageReportEntity
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.IOException

class UsageReporterTest {

    private val usage = SeparatorUsage(10, 1, 0, 9, 1_800_000_000_000L, unlimited = false)

    private class MemoryStore(vararg reports: UsageReportEntity) : UsageReportStore {
        val reports = reports.toMutableList()
        override suspend fun pending() = reports.toList()
        override suspend fun remove(jobRef: String) {
            reports.removeAll { it.jobRef == jobRef }
        }
    }

    private inner class FakeBackend(private val failing: Set<String> = emptySet()) : UsageBackend {
        val sent = mutableListOf<Pair<String, String>>()
        override suspend fun reserve(jobs: List<UsageJob>) = error("not used")
        override suspend fun finish(jobRef: String, outcome: String): SeparatorUsage {
            if (jobRef in failing) throw IOException("offline")
            sent += jobRef to outcome
            return usage
        }
    }

    private fun report(ref: String, user: String = "u1", outcome: String = "completed") =
        UsageReportEntity(ref, user, outcome, createdAt = 0)

    @Test
    fun sentReportsAreRemovedAndUpdateTheUsage() = runBlocking {
        val store = MemoryStore(report("a"), report("b", outcome = "released"))
        val backend = FakeBackend()
        val updates = mutableListOf<Pair<String, SeparatorUsage>>()
        val reporter = UsageReporter(store, backend, { "u1" }, { user, u -> updates += user to u })

        assertTrue(reporter.sendPending())
        assertEquals(listOf("a" to "completed", "b" to "released"), backend.sent)
        assertTrue(store.reports.isEmpty())
        assertEquals(listOf("u1" to usage, "u1" to usage), updates)
    }

    @Test
    fun aFailedSendStaysPending() = runBlocking {
        val store = MemoryStore(report("a"), report("b"))
        val reporter = UsageReporter(store, FakeBackend(failing = setOf("a")), { "u1" })

        assertFalse(reporter.sendPending())
        assertEquals(listOf("a"), store.reports.map { it.jobRef })
    }

    @Test
    fun reportsFromAnotherAccountAreDropped() = runBlocking {
        val store = MemoryStore(report("a", user = "someone-else"))
        val backend = FakeBackend()

        assertTrue(UsageReporter(store, backend, { "u1" }).sendPending())
        assertTrue(backend.sent.isEmpty())
        assertTrue(store.reports.isEmpty())
    }

    @Test
    fun signedOutReportsWaitForTheNextSignIn() = runBlocking {
        val store = MemoryStore(report("a"))
        val backend = FakeBackend()

        assertTrue(UsageReporter(store, backend, { null }).sendPending())
        assertEquals(1, store.reports.size)
        assertTrue(backend.sent.isEmpty())
    }
}
