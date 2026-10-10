package com.autoomstudio.mp3studio.data.plan

import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class EntitlementsResponseTest {

    private fun response(role: String?) = EntitlementsResponse(
        plan = "free",
        role = role,
        serverTime = "2027-01-15T08:00:01.5+00:00",
    )

    @Test
    fun theAdminRoleShowsTheAdminScreens() {
        assertTrue(response("admin").toEntitlements("u", 0).isAdmin)
    }

    @Test
    fun otherRolesAndOlderAnswersDoNot() {
        assertFalse(response("user").toEntitlements("u", 0).isAdmin)
        assertFalse(response(null).toEntitlements("u", 0).isAdmin)
    }

    @Test
    fun subscriptionStatusesMapToTheEnum() {
        assertEquals(SubscriptionStatus.Active, SubscriptionStatus.of("active"))
        assertEquals(SubscriptionStatus.PastDue, SubscriptionStatus.of("past_due"))
        assertEquals(SubscriptionStatus.Cancelled, SubscriptionStatus.of("cancelled"))
        assertEquals(SubscriptionStatus.Failed, SubscriptionStatus.of("failed"))
        assertEquals(SubscriptionStatus.Unknown, SubscriptionStatus.of("halted"))
        assertNull(SubscriptionStatus.of(null))
        assertEquals(AutopayStatus.On, AutopayStatus.of("on"))
        assertEquals(AutopayStatus.Revoked, AutopayStatus.of("revoked"))
        assertEquals(AutopayStatus.NotSet, AutopayStatus.of(null))
    }

    @Test
    fun billingFieldsAreRead() {
        val json = Json { ignoreUnknownKeys = true }
        val body = """
            {"plan":"pro","role":"user","hasPhone":true,"billingMode":"manual","paymentsEnabled":true,
             "subscription":{"status":"past_due","provider":"payu","expiresAt":"2027-02-01T00:00:00+00:00",
               "autopayStatus":"on","graceEnd":"2027-02-04T00:00:00+00:00"},
             "billing":{"status":"past_due","autopayStatus":"on","expiresAt":"2027-02-01T00:00:00+00:00",
               "graceEnd":"2027-02-04T00:00:00+00:00","mandateEnd":null,"cancelAtPeriodEnd":false,"cancelPending":false},
             "pendingPayment":{"txnId":"MPABC","status":"pending","kind":"renewal","linkExpiresAt":null,
               "resolveUntil":"2027-02-02T00:00:00+00:00"},
             "lastPayment":{"txnId":"MPOLD","status":"success","amountPaise":9900,"at":"2027-01-01T00:00:00+00:00"},
             "serverTime":"2027-02-01T10:00:00+00:00"}
        """.trimIndent()
        val e = json.decodeFromString<EntitlementsResponse>(body).toEntitlements("u", 0)
        assertEquals(SubscriptionStatus.PastDue, e.subscriptionStatus)
        assertEquals(SubscriptionStatus.PastDue, e.billing?.status)
        assertEquals(AutopayStatus.On, e.billing?.autopayStatus)
        assertEquals(epochMillis("2027-02-04T00:00:00+00:00"), e.billing?.graceEnd)
        assertEquals("MPABC", e.pendingPayment?.txnId)
        assertEquals(9900, e.lastPayment?.amountPaise)
        assertTrue(e.hasPhone)
        assertTrue(e.paymentsEnabled)
        assertEquals(BillingMode.Manual, e.billingMode)
        assertEquals("No interval from an older server means monthly", BillingInterval.Month, e.subscriptionInterval)
        assertEquals(BillingInterval.Month, e.billing?.interval)
    }

    @Test
    fun theIntervalIsReadAndAdminGrantsHaveNone() {
        val json = Json { ignoreUnknownKeys = true }
        val yearly = """
            {"plan":"pro","subscription":{"status":"active","provider":"payu","interval":"year"},
             "billing":{"status":"active","autopayStatus":"on","interval":"year"},
             "serverTime":"2027-02-01T10:00:00+00:00"}
        """.trimIndent()
        val e = json.decodeFromString<EntitlementsResponse>(yearly).toEntitlements("u", 0)
        assertEquals(BillingInterval.Year, e.subscriptionInterval)
        assertEquals(BillingInterval.Year, e.billing?.interval)

        val granted = """
            {"plan":"pro","subscription":{"status":"active","provider":"admin","interval":"month"},
             "serverTime":"2027-02-01T10:00:00+00:00"}
        """.trimIndent()
        assertNull(json.decodeFromString<EntitlementsResponse>(granted).toEntitlements("u", 0).subscriptionInterval)
    }

    @Test
    fun anOlderServerMeansNoPaymentsAndAutopayMode() {
        val e = response(null).toEntitlements("u", 0)
        assertFalse(e.paymentsEnabled)
        assertFalse(e.hasPhone)
        assertEquals(BillingMode.Autopay, e.billingMode)
        assertNull(e.billing)
    }
}
