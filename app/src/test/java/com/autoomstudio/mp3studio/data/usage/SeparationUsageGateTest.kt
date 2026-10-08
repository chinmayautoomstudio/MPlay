package com.autoomstudio.mp3studio.data.usage

import com.autoomstudio.mp3studio.data.plan.SeparatorUsage
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Test
import java.io.IOException

class SeparationUsageGateTest {

    private val resetsAt = 1_800_000_000_000L
    private val jobs = listOf(UsageJob("a", "Song A"), UsageJob("b", "Song B"), UsageJob("c", "Song C"))

    private fun usage(remaining: Int) = SeparatorUsage(10, 10 - remaining, 0, remaining, resetsAt, unlimited = false)

    private class FakeBackend(private val answer: (List<UsageJob>) -> ReserveAnswer) : UsageBackend {
        var calls = 0
        override suspend fun reserve(jobs: List<UsageJob>): ReserveAnswer {
            calls++
            return answer(jobs)
        }

        override suspend fun finish(jobRef: String, outcome: String) = error("not used")
    }

    private fun gate(backend: UsageBackend, unlimitedOffline: Boolean = false, seen: MutableList<SeparatorUsage>? = null) =
        SeparationUsageGate(backend, unlimitedOffline = { unlimitedOffline }, onUsage = { seen?.add(it) })

    @Test
    fun everyJobGrantedQueuesThemAll() = runBlocking {
        val seen = mutableListOf<SeparatorUsage>()
        val backend = FakeBackend { ReserveAnswer(setOf("a", "b", "c"), emptySet(), usage(5)) }
        assertEquals(UsageDecision.Granted(listOf("a", "b", "c")), gate(backend, seen = seen).reserve(jobs))
        assertEquals(listOf(usage(5)), seen)
    }

    @Test
    fun overTheLimitQueuesThePickedOrderUpToWhatRemains() = runBlocking {
        val backend = FakeBackend { ReserveAnswer(setOf("a"), setOf("b", "c"), usage(0)) }
        assertEquals(UsageDecision.Partial(listOf("a"), 2, resetsAt), gate(backend).reserve(jobs))
    }

    @Test
    fun nothingGrantedIsLimitReached() = runBlocking {
        val backend = FakeBackend { ReserveAnswer(emptySet(), setOf("a", "b", "c"), usage(0)) }
        assertEquals(UsageDecision.LimitReached(resetsAt), gate(backend).reserve(jobs))
    }

    @Test
    fun freeUsersNeedTheServer() = runBlocking {
        val backend = FakeBackend { throw IOException("offline") }
        assertEquals(UsageDecision.NeedsInternet, gate(backend, unlimitedOffline = false).reserve(jobs))
    }

    @Test
    fun aCachedTrialOrProQueuesOffline() = runBlocking {
        val backend = FakeBackend { throw IOException("offline") }
        assertEquals(UsageDecision.Granted(listOf("a", "b", "c")), gate(backend, unlimitedOffline = true).reserve(jobs))
    }

    @Test
    fun noJobsNeverCallTheServer() = runBlocking {
        val backend = FakeBackend { error("unexpected") }
        assertEquals(UsageDecision.Granted(emptyList()), gate(backend).reserve(emptyList()))
        assertEquals(0, backend.calls)
    }
}
