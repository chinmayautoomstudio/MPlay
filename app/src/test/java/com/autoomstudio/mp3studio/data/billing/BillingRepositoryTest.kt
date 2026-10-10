package com.autoomstudio.mp3studio.data.billing

import com.autoomstudio.mp3studio.data.plan.BillingMode
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test

class BillingRepositoryTest {

    class FakeStore : PendingPaymentStore {
        var value: Pair<String, String>? = null
        override suspend fun read() = value
        override suspend fun write(userId: String, txnId: String) {
            value = userId to txnId
        }
        override suspend fun clear() {
            value = null
        }
    }

    class FakeBackend : BillingBackend {
        val answers = ArrayDeque<PaymentState>()
        val asked = mutableListOf<String?>()
        var fail: BillingError? = null
        var cancels = 0

        override suspend fun startCheckout(phone: String?): CheckoutLink {
            fail?.let { throw BillingException(it) }
            return CheckoutLink("https://u.payu.in/x", "MPNEW", BillingMode.Autopay)
        }

        override suspend fun paymentStatus(txnId: String?): PaymentSummary {
            asked += txnId
            fail?.let { throw BillingException(it) }
            return PaymentSummary(txnId ?: "?", answers.removeFirstOrNull() ?: PaymentState.Pending)
        }

        override suspend fun cancelSubscription(): CancelResult {
            cancels++
            return CancelResult.Pending
        }

        override suspend fun payments(): List<PaymentRecord> = emptyList()
    }

    private val store = FakeStore()
    private val backend = FakeBackend()
    private val refreshed = mutableListOf<String>()
    private val sleeps = mutableListOf<Long>()
    private val repository = BillingRepository(backend, store, { refreshed += it }, { sleeps += it })

    @Test
    fun checkoutRemembersTheTransactionForTheUser() = runTest {
        val link = repository.startCheckout("u1", "9876543210")
        assertEquals("MPNEW", link.txnId)
        assertEquals("u1" to "MPNEW", store.value)
        assertEquals("MPNEW", repository.pendingTxnId("u1"))
        assertNull("Another account doesn't see it", repository.pendingTxnId("u2"))
    }

    @Test
    fun aFailedCheckoutRemembersNothing() = runTest {
        backend.fail = BillingError.PaymentInProgress
        try {
            repository.startCheckout("u1", null)
            fail()
        } catch (e: BillingException) {
            assertEquals(BillingError.PaymentInProgress, e.error)
        }
        assertNull(store.value)
    }

    @Test
    fun confirmPollsUntilPayUHasAnAnswerThenRefreshesThePlan() = runTest {
        store.value = "u1" to "MP1"
        backend.answers += listOf(PaymentState.Created, PaymentState.Pending, PaymentState.Success)
        val summary = repository.confirm("u1", attempts = 5, intervalMs = 100)
        assertEquals(PaymentState.Success, summary?.state)
        assertEquals(listOf<String?>("MP1", "MP1", "MP1"), backend.asked)
        assertEquals(listOf(100L, 100L), sleeps)
        assertNull("A settled payment is forgotten", store.value)
        assertEquals(listOf("u1"), refreshed)
    }

    @Test
    fun anUnsettledPaymentStaysRemembered() = runTest {
        store.value = "u1" to "MP1"
        val summary = repository.confirm("u1", attempts = 2, intervalMs = 50)
        assertEquals(PaymentState.Pending, summary?.state)
        assertEquals(listOf(50L), sleeps)
        assertEquals("u1" to "MP1", store.value)
        assertTrue(refreshed.isEmpty())
    }

    @Test
    fun nothingToConfirmWithoutATransaction() = runTest {
        assertNull(repository.confirm("u1"))
        assertTrue(backend.asked.isEmpty())
    }

    @Test
    fun aReturnLinkTransactionIsCheckedWithoutTouchingTheRememberedOne() = runTest {
        store.value = "u1" to "MP1"
        backend.answers += PaymentState.Failed
        repository.confirm("u1", txnId = "MP2")
        assertEquals(listOf<String?>("MP2"), backend.asked)
        assertEquals("u1" to "MP1", store.value)
        assertEquals(listOf("u1"), refreshed)
    }

    @Test
    fun anUnknownTransactionIsForgotten() = runTest {
        store.value = "u1" to "MP1"
        backend.fail = BillingError.NotFound
        try {
            repository.confirm("u1")
            fail()
        } catch (e: BillingException) {
            assertEquals(BillingError.NotFound, e.error)
        }
        assertNull(store.value)
    }

    @Test
    fun offlineKeepsThePendingPayment() = runTest {
        store.value = "u1" to "MP1"
        backend.fail = BillingError.Offline
        runCatching { repository.confirm("u1") }
        assertEquals("u1" to "MP1", store.value)
    }

    @Test
    fun cancellingRefreshesThePlan() = runTest {
        assertEquals(CancelResult.Pending, repository.cancel("u1"))
        assertEquals(1, backend.cancels)
        assertEquals(listOf("u1"), refreshed)
    }

    @Test
    fun serverRefusalsAreReadFromTheBody() {
        assertEquals(BillingError.PhoneRequired, billingErrorOf(409, """{"error":"phone_required"}"""))
        assertEquals(BillingError.MandateUpdatePending, billingErrorOf(409, """{"error":"mandate_update_pending"}"""))
        assertEquals(BillingError.AlreadySubscribed, billingErrorOf(409, """{"error":"already_subscribed"}"""))
        assertEquals(BillingError.RateLimited, billingErrorOf(429, """{"error":"rate_limited"}"""))
        assertEquals(BillingError.Unavailable, billingErrorOf(503, """{"error":"payu_unavailable"}"""))
        assertEquals(BillingError.Unavailable, billingErrorOf(503, ""))
        assertEquals(BillingError.Other, billingErrorOf(500, """{"error":"server_error"}"""))
    }

    @Test
    fun phoneNumbersFollowTheServerRules() {
        assertEquals("9876543210", PhoneNumber.normalize("98765 43210"))
        assertEquals("9876543210", PhoneNumber.normalize("+91 98765-43210"))
        assertEquals("9876543210", PhoneNumber.normalize("919876543210"))
        assertEquals("9876543210", PhoneNumber.normalize("09876543210"))
        assertNull(PhoneNumber.normalize("5876543210"))
        assertNull(PhoneNumber.normalize("98765"))
        assertEquals("MPABC1", TxnId.parse("MPABC1"))
        assertNull(TxnId.parse("MP-1"))
        assertNull(TxnId.parse("x".repeat(26)))
    }

    @Test
    fun onlySettledStatesStopTheWait() {
        assertTrue(PaymentState.Success.settled)
        assertTrue(PaymentState.Failed.settled)
        assertTrue(PaymentState.Cancelled.settled)
        assertTrue(!PaymentState.Pending.settled)
        assertTrue(!PaymentState.Created.settled)
        assertEquals(PaymentState.PartiallyRefunded, PaymentState.of("partially_refunded"))
    }
}
