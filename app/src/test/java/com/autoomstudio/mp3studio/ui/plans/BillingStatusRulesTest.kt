package com.autoomstudio.mp3studio.ui.plans

import com.autoomstudio.mp3studio.data.plan.AutopayStatus
import com.autoomstudio.mp3studio.data.plan.BillingMode
import com.autoomstudio.mp3studio.data.plan.BillingState
import com.autoomstudio.mp3studio.data.plan.PendingPayment
import com.autoomstudio.mp3studio.data.plan.Plan
import com.autoomstudio.mp3studio.data.plan.SubscriptionStatus
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class BillingStatusRulesTest {

    private val now = 1_800_000_000_000L
    private val day = 24L * 60 * 60 * 1000

    private fun status(
        billing: BillingState?,
        plan: Plan = Plan.Pro,
        pending: PendingPayment? = null,
        mode: BillingMode = BillingMode.Autopay,
        enabled: Boolean = true,
    ) = BillingStatusRules.of(plan, billing, pending, mode, enabled, now)

    private fun sub(
        status: SubscriptionStatus = SubscriptionStatus.Active,
        autopay: AutopayStatus = AutopayStatus.On,
        expiresAt: Long = now + 20 * day,
        mandateEnd: Long? = null,
        cancelPending: Boolean = false,
    ) = BillingState(status, autopay, expiresAt, expiresAt, now + 3 * day, mandateEnd, false, cancelPending)

    @Test
    fun freeUsersCanGoProOnlyWhenPaymentsAreSetUp() {
        assertEquals(listOf(BillingAction.GoPro), status(null, Plan.Free).actions)
        val off = status(null, Plan.Free, enabled = false)
        assertEquals(BillingNotice.PaymentsUnavailable, off.notice)
        assertEquals(emptyList<BillingAction>(), off.actions)
    }

    @Test
    fun adminGrantedProHasNothingToManage() {
        val s = status(null, Plan.Pro)
        assertNull(s.notice)
        assertEquals(emptyList<BillingAction>(), s.actions)
    }

    @Test
    fun aPaymentWaitingForTheBankComesFirst() {
        val s = status(sub(), pending = PendingPayment("MP1", "pending"))
        assertEquals(BillingNotice.PaymentPending("MP1"), s.notice)
        assertEquals(listOf(BillingAction.CheckPayment), s.actions)
        assertEquals("An unopened link doesn't block anything", listOf(BillingAction.GoPro),
            status(null, Plan.Free, pending = PendingPayment("MP2", "created")).actions)
    }

    @Test
    fun activeAutopayCanBeCancelled() {
        val s = status(sub())
        assertEquals(BillingNotice.AutopayOn(now + 20 * day), s.notice)
        assertEquals(listOf(BillingAction.CancelAutopay), s.actions)
    }

    @Test
    fun anEndingMandateIsAnnouncedButNotReplacedYet() {
        val s = status(sub(mandateEnd = now + 10 * day))
        assertEquals(BillingNotice.MandateEnding(now + 10 * day), s.notice)
        assertEquals(listOf(BillingAction.CancelAutopay), s.actions)
    }

    @Test
    fun pastDueOffersFixPayment() {
        val s = status(sub(status = SubscriptionStatus.PastDue))
        assertEquals(BillingNotice.PastDue(now + 3 * day), s.notice)
        assertEquals(listOf(BillingAction.FixPayment), s.actions)
    }

    @Test
    fun cancellationStates() {
        assertEquals(listOf<BillingAction>(), status(sub(cancelPending = true)).actions)
        val cancelled = status(sub(status = SubscriptionStatus.Cancelled, autopay = AutopayStatus.Off))
        assertEquals(BillingNotice.Cancelled(now + 20 * day), cancelled.notice)
        assertEquals(listOf(BillingAction.GoProAgain), cancelled.actions)
        val over = status(sub(status = SubscriptionStatus.Cancelled, expiresAt = now - day), Plan.Free)
        assertEquals(BillingNotice.Expired, over.notice)
    }

    @Test
    fun autopayNotSetOffersSetUpAndManualModeOffersRenewalInTheLastWeek() {
        assertEquals(listOf(BillingAction.SetUpAutopay), status(sub(autopay = AutopayStatus.NotSet)).actions)
        val early = status(sub(autopay = AutopayStatus.NotSet), mode = BillingMode.Manual)
        assertEquals(BillingNotice.RenewSoon(now + 20 * day), early.notice)
        assertEquals(emptyList<BillingAction>(), early.actions)
        val late = status(sub(autopay = AutopayStatus.NotSet, expiresAt = now + 5 * day), mode = BillingMode.Manual)
        assertEquals(listOf(BillingAction.Renew), late.actions)
    }

    @Test
    fun expiredOffersGoProAgain() {
        val s = status(sub(status = SubscriptionStatus.Expired, autopay = AutopayStatus.Off), Plan.Free)
        assertEquals(BillingNotice.Expired, s.notice)
        assertEquals(listOf(BillingAction.GoProAgain), s.actions)
    }

    @Test
    fun returnLinksMustBeOurs() {
        assertEquals("MP1", PaymentReturnLink.parse("https", "pay.example.com", "/pay/return", "MP1", "pay.example.com"))
        assertEquals("MP1", PaymentReturnLink.parse("https", "pay.example.com", "/pay/return/", "MP1", "pay.example.com"))
        assertEquals("", PaymentReturnLink.parse("https", "pay.example.com", "/pay/return", "MP-1", "pay.example.com"))
        assertEquals("", PaymentReturnLink.parse("https", "pay.example.com", "/pay/return", null, "pay.example.com"))
        assertNull(PaymentReturnLink.parse("http", "pay.example.com", "/pay/return", "MP1", "pay.example.com"))
        assertNull(PaymentReturnLink.parse("https", "evil.example.com", "/pay/return", "MP1", "pay.example.com"))
        assertNull(PaymentReturnLink.parse("https", "pay.example.com", "/other", "MP1", "pay.example.com"))
        assertNull(PaymentReturnLink.parse(null, null, null, null, "pay.example.com"))
    }
}
