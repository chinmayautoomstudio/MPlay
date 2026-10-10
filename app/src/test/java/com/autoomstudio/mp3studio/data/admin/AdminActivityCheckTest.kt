package com.autoomstudio.mp3studio.data.admin

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant

class AdminActivityCheckTest {

    private fun millis(at: String) = Instant.parse(at).toEpochMilli()

    private val signup = AdminActivity("signup", "2026-10-08T10:00:00+00:00", userId = "u1", email = "a@b.c")
    private val subscribed = AdminActivity("subscribed", "2026-10-08T09:00:00+00:00", userId = "u1", plan = "pro")
    private val deleted = AdminActivity("deleted", "2026-10-07T09:00:00+00:00", plan = "free")
    private val events = listOf(signup, subscribed, deleted)
    private val now = millis("2026-10-08T12:00:00Z")

    @Test
    fun firstCheckOnlySetsTheBaseline() {
        val check = checkNewActivity(events, notifiedAt = 0L, seenAt = 0L, now = now)
        assertTrue(check.toNotify.isEmpty())
        assertEquals(millis("2026-10-08T10:00:00Z"), check.notifiedAt)
    }

    @Test
    fun firstCheckWithNoEventsUsesNow() {
        val check = checkNewActivity(emptyList(), notifiedAt = 0L, seenAt = 0L, now = now)
        assertTrue(check.toNotify.isEmpty())
        assertEquals(now, check.notifiedAt)
    }

    @Test
    fun newerEventsAreNotifiedOldestFirst() {
        val check = checkNewActivity(events, notifiedAt = millis("2026-10-07T12:00:00Z"), seenAt = 0L, now = now)
        assertEquals(listOf(subscribed, signup), check.toNotify)
        assertEquals(millis("2026-10-08T10:00:00Z"), check.notifiedAt)
    }

    @Test
    fun eventsAlreadySeenInTheAppAreSkipped() {
        val check = checkNewActivity(
            events,
            notifiedAt = millis("2026-10-07T12:00:00Z"),
            seenAt = millis("2026-10-08T09:30:00Z"),
            now = now,
        )
        assertEquals(listOf(signup), check.toNotify)
    }

    @Test
    fun nothingNewKeepsTheBaseline() {
        val notifiedAt = millis("2026-10-08T10:00:00Z")
        val check = checkNewActivity(events, notifiedAt = notifiedAt, seenAt = 0L, now = now)
        assertTrue(check.toNotify.isEmpty())
        assertEquals(notifiedAt, check.notifiedAt)
    }

    @Test
    fun unparsableTimesAreIgnored() {
        val broken = AdminActivity("signup", "not a time")
        val check = checkNewActivity(listOf(broken, signup), notifiedAt = 1L, seenAt = 0L, now = now)
        assertEquals(listOf(signup), check.toNotify)
    }
}
