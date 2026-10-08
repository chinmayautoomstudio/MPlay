package com.autoomstudio.mp3studio.data.plan

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.concurrent.TimeUnit

class EntitlementPolicyTest {

    private val day = TimeUnit.DAYS.toMillis(1)
    private val checkedAt = 1_800_000_000_000L

    private fun entitlements(
        plan: Plan,
        trialEndsAt: Long? = null,
        subscriptionExpiresAt: Long? = null,
    ) = Entitlements(
        userId = "u1",
        plan = plan,
        trialStartedAt = trialEndsAt?.minus(30 * day),
        trialEndsAt = trialEndsAt,
        subscriptionExpiresAt = subscriptionExpiresAt,
        serverTime = checkedAt,
        checkedAt = checkedAt,
    )

    private fun unlocked(entitlements: Entitlements?, now: Long = checkedAt) =
        Feature.entries.associateWith { EntitlementPolicy.canUse(it, entitlements, now) }

    @Test
    fun freeLocksBpmSingAlongAndUnlimitedSeparation() {
        assertTrue(unlocked(entitlements(Plan.Free)).values.none { it })
    }

    @Test
    fun noCacheIsFree() {
        assertEquals(Plan.Free, EntitlementPolicy.effectivePlan(null, checkedAt))
        assertTrue(unlocked(null).values.none { it })
    }

    @Test
    fun trialAndProUnlockEverything() {
        assertTrue(unlocked(entitlements(Plan.Trial, trialEndsAt = checkedAt + 20 * day)).values.all { it })
        assertTrue(unlocked(entitlements(Plan.Pro, subscriptionExpiresAt = checkedAt + 20 * day)).values.all { it })
        assertTrue(unlocked(entitlements(Plan.Pro)).values.all { it })
    }

    @Test
    fun anEndedTrialIsFreeEvenFromTheCache() {
        val trial = entitlements(Plan.Trial, trialEndsAt = checkedAt + day)
        assertEquals(Plan.Trial, EntitlementPolicy.effectivePlan(trial, checkedAt + day - 1))
        assertEquals(Plan.Free, EntitlementPolicy.effectivePlan(trial, checkedAt + day))
    }

    @Test
    fun anExpiredSubscriptionFallsBackToARunningTrialOrFree() {
        val pro = entitlements(Plan.Pro, subscriptionExpiresAt = checkedAt + day)
        assertEquals(Plan.Free, EntitlementPolicy.effectivePlan(pro, checkedAt + 2 * day))
        val proInTrial = entitlements(Plan.Pro, trialEndsAt = checkedAt + 5 * day, subscriptionExpiresAt = checkedAt + day)
        assertEquals(Plan.Trial, EntitlementPolicy.effectivePlan(proInTrial, checkedAt + 2 * day))
    }

    @Test
    fun proWorksOfflineForSevenDaysThenLocks() {
        val pro = entitlements(Plan.Pro)
        assertEquals(Plan.Pro, EntitlementPolicy.effectivePlan(pro, checkedAt + 7 * day))
        assertFalse(EntitlementPolicy.isStale(pro, checkedAt + 7 * day))
        assertEquals(Plan.Free, EntitlementPolicy.effectivePlan(pro, checkedAt + 7 * day + 1))
        assertTrue(EntitlementPolicy.isStale(pro, checkedAt + 7 * day + 1))
    }

    @Test
    fun turningTheClockBackDoesNotExtendTheCache() {
        val pro = entitlements(Plan.Pro)
        assertEquals(Plan.Pro, EntitlementPolicy.effectivePlan(pro, checkedAt - TimeUnit.MINUTES.toMillis(30)))
        assertEquals(Plan.Free, EntitlementPolicy.effectivePlan(pro, checkedAt - 2 * day))
    }

    @Test
    fun serverTimeDecidesEndDatesWhenThePhoneClockIsOff() {
        // The phone is a day behind the server; the trial ended by server time.
        val trial = entitlements(Plan.Trial, trialEndsAt = checkedAt + day)
            .copy(serverTime = checkedAt + day + 1)
        assertEquals(Plan.Free, EntitlementPolicy.effectivePlan(trial, checkedAt))
    }

    @Test
    fun trialDaysLeftRoundsUp() {
        val trial = entitlements(Plan.Trial, trialEndsAt = checkedAt + 2 * day + 1)
        assertEquals(3, EntitlementPolicy.trialDaysLeft(trial, checkedAt))
        assertEquals(1, EntitlementPolicy.trialDaysLeft(trial, checkedAt + 2 * day))
        assertNull(EntitlementPolicy.trialDaysLeft(entitlements(Plan.Pro), checkedAt))
        assertNull(EntitlementPolicy.trialDaysLeft(trial, checkedAt + 3 * day))
    }

    private val usage = SeparatorUsage(
        limit = 10,
        used = 7,
        reserved = 1,
        remaining = 2,
        resetsAt = checkedAt + 2 * day,
        unlimited = false,
    )

    @Test
    fun usageIsShownOnlyOnFree() {
        assertEquals(usage, EntitlementPolicy.separatorUsage(entitlements(Plan.Free).copy(usage = usage), checkedAt))
        assertNull(EntitlementPolicy.separatorUsage(entitlements(Plan.Pro).copy(usage = usage), checkedAt))
        assertNull(EntitlementPolicy.separatorUsage(entitlements(Plan.Free), checkedAt))
    }

    @Test
    fun usageStartsOverOnceTheCachedWeekEnds() {
        val cached = entitlements(Plan.Free).copy(usage = usage)
        val nextWeek = EntitlementPolicy.separatorUsage(cached, checkedAt + 3 * day)
        assertEquals(usage.copy(used = 0, remaining = 9, resetsAt = usage.resetsAt + 7 * day), nextWeek)
    }
}
