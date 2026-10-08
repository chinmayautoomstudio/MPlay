package com.autoomstudio.mp3studio.data.plan

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.IOException

class EntitlementsRepositoryTest {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val now = 1_800_000_000_000L

    @After
    fun tearDown() = scope.cancel()

    private class MemoryCache(var stored: Entitlements? = null) : EntitlementsCache {
        override suspend fun read() = stored
        override suspend fun write(entitlements: Entitlements?) {
            stored = entitlements
        }
    }

    private fun trialResponse() = EntitlementsResponse(
        plan = "trial",
        trial = EntitlementsResponse.TrialDto(
            startedAt = "2027-01-15T08:00:00.000000+00:00",
            endsAt = "2027-02-14T08:00:00.000000+00:00",
        ),
        trialClaim = EntitlementsResponse.ClaimDto("granted"),
        serverTime = "2027-01-15T08:00:01.5+00:00",
    )

    @Test
    fun refreshStoresTheServerAnswer() = runBlocking {
        val cache = MemoryCache()
        val repository = EntitlementsRepository({ trialResponse() }, cache, scope, clock = { now })

        assertTrue(repository.refresh("u1"))

        val stored = cache.stored!!
        assertEquals(Plan.Trial, stored.plan)
        assertEquals(TrialClaim.Granted, stored.trialClaim)
        assertEquals(now, stored.checkedAt)
        assertEquals(1_800_000_001_500L, stored.serverTime)
        assertEquals(stored, repository.entitlements.value)
        assertTrue(repository.canUse(Feature.SingAlong, "u1"))
    }

    @Test
    fun offlineRefreshKeepsTheCache() = runBlocking {
        val cached = Entitlements(userId = "u1", plan = Plan.Pro, serverTime = now, checkedAt = now)
        val repository = EntitlementsRepository(
            backend = { throw IOException("offline") },
            cache = MemoryCache(cached),
            scope = scope,
            clock = { now + 1000 },
        )

        assertFalse(repository.refresh("u1"))

        assertEquals(cached, repository.entitlements.value)
        assertTrue(repository.canUse(Feature.BpmDetector, "u1"))
    }

    @Test
    fun anotherAccountsCacheUnlocksNothing() = runBlocking {
        val cached = Entitlements(userId = "u1", plan = Plan.Pro, serverTime = now, checkedAt = now)
        val repository = EntitlementsRepository({ trialResponse() }, MemoryCache(cached), scope, clock = { now })

        assertFalse(repository.canUse(Feature.BpmDetector, "u2"))
        assertFalse(repository.canUse(Feature.BpmDetector, null))
    }

    @Test
    fun clearEmptiesMemoryAndDisk() = runBlocking {
        val cache = MemoryCache(Entitlements(userId = "u1", plan = Plan.Pro, serverTime = now, checkedAt = now))
        val repository = EntitlementsRepository({ trialResponse() }, cache, scope, clock = { now })

        repository.clear()

        assertNull(cache.stored)
        assertNull(repository.entitlements.value)
        assertFalse(repository.canUse(Feature.SingAlong, "u1"))
    }

    @Test
    fun unknownPlansAndClaimsMapToFree() {
        val response = EntitlementsResponse(
            plan = "platinum",
            trialClaim = EntitlementsResponse.ClaimDto("something"),
            serverTime = "2027-01-15T08:00:00Z",
        )
        val mapped = response.toEntitlements("u1", now)
        assertEquals(Plan.Free, mapped.plan)
        assertNull(mapped.trialClaim)
    }
}
