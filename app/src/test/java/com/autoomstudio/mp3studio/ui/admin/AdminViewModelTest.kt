package com.autoomstudio.mp3studio.ui.admin

import com.autoomstudio.mp3studio.data.admin.AddAdminResult
import com.autoomstudio.mp3studio.data.admin.AdminActivity
import com.autoomstudio.mp3studio.data.admin.AdminBackend
import com.autoomstudio.mp3studio.data.admin.AdminBillingHealth
import com.autoomstudio.mp3studio.data.admin.AdminBillingResult
import com.autoomstudio.mp3studio.data.admin.AdminError
import com.autoomstudio.mp3studio.data.admin.AdminException
import com.autoomstudio.mp3studio.data.admin.AdminList
import com.autoomstudio.mp3studio.data.admin.AdminOverview
import com.autoomstudio.mp3studio.data.admin.AdminPaymentPage
import com.autoomstudio.mp3studio.data.admin.AdminPaymentRow
import com.autoomstudio.mp3studio.data.admin.PaymentFilter
import com.autoomstudio.mp3studio.data.admin.AdminProfile
import com.autoomstudio.mp3studio.data.admin.AdminSubscription
import com.autoomstudio.mp3studio.data.admin.AdminUsage
import com.autoomstudio.mp3studio.data.admin.AdminUserDetail
import com.autoomstudio.mp3studio.data.admin.AdminUserPage
import com.autoomstudio.mp3studio.data.admin.AdminUserRow
import com.autoomstudio.mp3studio.data.admin.AuditEntry
import com.autoomstudio.mp3studio.data.admin.UserFilter
import com.autoomstudio.mp3studio.data.plan.UsageDto
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.time.Instant

@OptIn(ExperimentalCoroutinesApi::class)
class AdminViewModelTest {

    private val dispatcher = UnconfinedTestDispatcher()

    @Before
    fun setUp() = Dispatchers.setMain(dispatcher)

    @After
    fun tearDown() = Dispatchers.resetMain()

    private class FakeBackend : AdminBackend {
        var failWith: AdminError? = null
        val queries = mutableListOf<Pair<String, UserFilter>>()
        val roleChanges = mutableListOf<Pair<String, Boolean>>()

        private fun check() {
            failWith?.let { throw AdminException(it) }
        }

        var overviewGate: CompletableDeferred<Unit>? = null

        override suspend fun overview(): AdminOverview {
            overviewGate?.await()
            check()
            return AdminOverview(3, 0, 1, 2, 0, 1, 4, 0, 1, "2026-10-05")
        }

        override suspend fun users(query: String, filter: UserFilter, offset: Int): AdminUserPage {
            check()
            queries += query to filter
            return AdminUserPage(1, listOf(AdminUserRow("u1", "a@b.c", "A", "user", "free", false, "2026-10-08T00:00:00+00:00")))
        }

        override suspend fun user(id: String): AdminUserDetail {
            check()
            return AdminUserDetail(
                profile = AdminProfile(id, "a@b.c", "A", null, "user", false, "2026-10-08T00:00:00+00:00"),
                usage = UsageDto(10, 0, 0, 10, "2026-10-11T18:30:00+00:00", false),
            )
        }

        override suspend fun usage() = AdminUsage(emptyList(), emptyList()).also { check() }
        override suspend fun setRole(userId: String, admin: Boolean) {
            check()
            roleChanges += userId to admin
        }
        override suspend fun setDisabled(userId: String, disabled: Boolean) = check()
        override suspend fun addAdmin(email: String) = AddAdminResult.Invited.also { check() }
        override suspend fun admins() = AdminList(emptyList(), emptyList()).also { check() }
        override suspend fun revokeInvite(email: String) = check()
        override suspend fun grantPro(userId: String, until: Long) = check()
        override suspend fun revokePro(userId: String) = check()
        override suspend fun audit(before: Long?): List<AuditEntry> = emptyList<AuditEntry>().also { check() }
        override suspend fun activity(): List<AdminActivity> = listOf(
            AdminActivity("signup", "2026-10-08T10:00:00+00:00", userId = "u1", email = "a@b.c"),
            AdminActivity("subscribed", "2026-10-08T09:00:00+00:00", userId = "u1", provider = "admin", plan = "pro"),
            AdminActivity("deleted", "2026-10-07T09:00:00+00:00", plan = "free"),
        ).also { check() }

        val paymentQueries = mutableListOf<Pair<String, PaymentFilter>>()
        val refunds = mutableListOf<String>()
        var cancelResult = AdminBillingResult.Cancelled

        override suspend fun payments(query: String, filter: PaymentFilter, offset: Int): AdminPaymentPage {
            check()
            paymentQueries += query to filter
            return AdminPaymentPage(
                1,
                listOf(AdminPaymentRow("MP1", "u1", "a@b.c", "first", "success", 9900, payuRef = "403", createdAt = "2026-10-08T00:00:00+00:00")),
            )
        }

        override suspend fun billingHealth() = AdminBillingHealth(flaggedWebhooks = 1).also { check() }
        override suspend fun reverifyPayment(txnId: String) = AdminBillingResult.Checked.also { check() }
        override suspend fun refundPayment(txnId: String): AdminBillingResult {
            check()
            refunds += txnId
            return AdminBillingResult.RefundRequested
        }
        override suspend fun cancelSubscription(userId: String) = cancelResult.also { check() }

        val revoked = mutableListOf<String>()
        var revokeResult = AdminBillingResult.Revoked
        override suspend fun revokeSubscription(userId: String): AdminBillingResult {
            check()
            revoked += userId
            return revokeResult
        }
    }

    private val backend = FakeBackend()
    private val refreshed = mutableListOf<String>()
    private val seenAt = MutableStateFlow(0L)
    private val notificationsOn = MutableStateFlow(true)

    private fun viewModel() = AdminViewModel(
        backend = backend,
        currentUserId = { "me" },
        refreshOwnPlan = { refreshed += it },
        activitySeenAt = seenAt,
        markActivitySeen = { seenAt.value = it },
        notificationsEnabled = notificationsOn,
        saveNotificationsEnabled = { notificationsOn.value = it },
        searchDelayMillis = 300,
    ).also { it.enter() }

    @Test
    fun phoneNotificationsSwitchFollowsTheSetting() = runTest(dispatcher) {
        val vm = viewModel()
        assertTrue(vm.notificationsEnabled.value)
        vm.setNotificationsEnabled(false)
        assertFalse(notificationsOn.value)
        assertFalse(vm.notificationsEnabled.value)
        vm.setNotificationsEnabled(true)
        assertTrue(vm.notificationsEnabled.value)
    }

    @Test
    fun newActivityIsUnreadUntilTheFeedIsOpened() = runTest(dispatcher) {
        seenAt.value = Instant.parse("2026-10-08T08:00:00Z").toEpochMilli()
        val vm = viewModel()
        assertEquals(2, vm.unread.value)
        vm.open(AdminPage.Activity)
        assertEquals(3, vm.activity.value.data?.size)
        assertEquals(Instant.parse("2026-10-08T10:00:00Z").toEpochMilli(), seenAt.value)
        assertEquals(0, vm.unread.value)
    }

    @Test
    fun pullToRefreshShowsItsIndicatorUntilEveryPartHasAnswered() = runTest(dispatcher) {
        val vm = viewModel()
        backend.overviewGate = CompletableDeferred()
        backend.queries.clear()
        vm.refresh()
        assertTrue(vm.refreshing.value)
        assertFalse("The page's own spinner stays hidden", vm.overview.value.loading)
        assertEquals(listOf("" to UserFilter.All), backend.queries)
        backend.overviewGate!!.complete(Unit)
        assertFalse(vm.refreshing.value)
        assertEquals(3, vm.overview.value.data?.users)
    }

    @Test
    fun aFailedPullShowsTheErrorButAFailedAutoRefreshKeepsTheData() = runTest(dispatcher) {
        val vm = viewModel()
        backend.failWith = AdminError.Offline
        vm.autoRefresh()
        assertEquals(3, vm.overview.value.data?.users)
        assertEquals(null, vm.overview.value.error)
        assertEquals(null, vm.users.value.error)
        assertEquals(listOf("u1"), vm.users.value.users.map { it.id })

        vm.refresh()
        assertFalse(vm.refreshing.value)
        assertEquals(AdminError.Offline, vm.users.value.error)
        assertEquals(listOf("u1"), vm.users.value.users.map { it.id })
    }

    @Test
    fun autoRefreshFetchesTheVisiblePageQuietly() = runTest(dispatcher) {
        val vm = viewModel()
        vm.open(AdminPage.Payments)
        backend.paymentQueries.clear()
        vm.autoRefresh()
        assertEquals(listOf("" to PaymentFilter.All), backend.paymentQueries)
        assertFalse(vm.payments.value.loading)
    }

    @Test
    fun enteringLoadsTheOverviewAndTheFirstUsers() = runTest(dispatcher) {
        val vm = viewModel()
        assertEquals(3, vm.overview.value.data?.users)
        assertEquals(listOf("u1"), vm.users.value.users.map { it.id })
    }

    @Test
    fun pagesStackAndBackReturnsToHome() = runTest(dispatcher) {
        val vm = viewModel()
        vm.open(AdminPage.User("u1"))
        assertEquals(AdminPage.User("u1"), vm.page.value)
        assertEquals("u1", vm.detail.value.data?.profile?.id)
        vm.open(AdminPage.Audit)
        assertTrue(vm.back())
        assertEquals(AdminPage.User("u1"), vm.page.value)
        assertTrue(vm.back())
        assertEquals(AdminPage.Home, vm.page.value)
        assertFalse(vm.back())
    }

    @Test
    fun typingWaitsBeforeSearching() = runTest(dispatcher) {
        val vm = viewModel()
        backend.queries.clear()
        vm.setQuery("a")
        vm.setQuery("an")
        advanceTimeBy(301)
        assertEquals(listOf("an" to UserFilter.All), backend.queries)
    }

    @Test
    fun aRefusalIsReportedWithItsReason() = runTest(dispatcher) {
        val vm = viewModel()
        backend.failWith = AdminError.LastAdmin
        vm.setRole("me", "me@x.y", admin = false)
        assertEquals(AdminMessage.Failed(AdminError.LastAdmin), vm.messages.first())
        assertFalse(vm.closed.value)
    }

    @Test
    fun changingYourOwnRoleRefreshesYourPlan() = runTest(dispatcher) {
        val vm = viewModel()
        vm.setRole("me", "me@x.y", admin = false)
        assertEquals(listOf("me" to false), backend.roleChanges)
        assertEquals(listOf("me"), refreshed)
        assertEquals(AdminMessage.RoleChanged("me@x.y", admin = false), vm.messages.first())
    }

    @Test
    fun thePaymentsPageLoadsHealthAndFilteredPayments() = runTest(dispatcher) {
        val vm = viewModel()
        vm.open(AdminPage.Payments)
        assertEquals(1, vm.health.value.data?.flaggedWebhooks)
        assertFalse(vm.health.value.data!!.healthy)
        assertEquals(listOf("MP1"), vm.payments.value.payments.map { it.txnId })
        assertTrue(vm.payments.value.payments.single().refundable)
        vm.setPaymentFilter(PaymentFilter.Refunded)
        assertEquals("" to PaymentFilter.Refunded, backend.paymentQueries.last())
    }

    @Test
    fun billingActionsReportWhatPayUDid() = runTest(dispatcher) {
        val vm = viewModel()
        vm.refundPayment("MP1")
        assertEquals(listOf("MP1"), backend.refunds)
        assertEquals(AdminMessage.Billing(AdminBillingResult.RefundRequested), vm.messages.first())
        backend.cancelResult = AdminBillingResult.CancelPending
        vm.cancelSubscription("me")
        assertEquals(AdminMessage.Billing(AdminBillingResult.CancelPending), vm.messages.first())
        assertEquals("Cancelling your own subscription refreshes your plan", listOf("me"), refreshed)
        backend.failWith = AdminError.NotRefundable
        vm.refundPayment("MP1")
        assertEquals(AdminMessage.Failed(AdminError.NotRefundable), vm.messages.first())
    }

    @Test
    fun revokingASubscriptionReportsWhetherPayUConfirmed() = runTest(dispatcher) {
        val vm = viewModel()
        vm.revokeSubscription("u1")
        assertEquals(listOf("u1"), backend.revoked)
        assertEquals(AdminMessage.Billing(AdminBillingResult.Revoked), vm.messages.first())
        assertTrue("Someone else's plan isn't refreshed", refreshed.isEmpty())

        backend.revokeResult = AdminBillingResult.RevokePending
        vm.revokeSubscription("me")
        assertEquals(AdminMessage.Billing(AdminBillingResult.RevokePending), vm.messages.first())
        assertEquals("Revoking your own subscription refreshes your plan", listOf("me"), refreshed)

        backend.failWith = AdminError.NotSubscribed
        vm.revokeSubscription("u1")
        assertEquals(AdminMessage.Failed(AdminError.NotSubscribed), vm.messages.first())
    }

    @Test
    fun onlyAPayUSubscriptionThatStillGivesProCanBeRevoked() {
        val now = Instant.parse("2026-10-10T00:00:00Z").toEpochMilli()
        fun detail(vararg subs: AdminSubscription) = AdminUserDetail(
            profile = AdminProfile("u1", role = "user", disabled = false, createdAt = "2026-10-08T00:00:00+00:00"),
            usage = UsageDto(10, 0, 0, 10, "2026-10-11T18:30:00+00:00", false),
            subscriptions = subs.toList(),
        )
        val later = "2026-11-10T00:00:00+00:00"
        assertEquals("cancelled", detail(AdminSubscription("payu", "cancelled", expiresAt = later)).revocableSubscription(now)?.status)
        assertEquals("active", detail(AdminSubscription("payu", "active", expiresAt = later)).revocableSubscription(now)?.status)
        assertEquals(null, detail(AdminSubscription("payu", "cancelled", expiresAt = "2026-10-09T00:00:00+00:00")).revocableSubscription(now))
        assertEquals(null, detail(AdminSubscription("payu", "expired", expiresAt = later)).revocableSubscription(now))
        assertEquals(null, detail(AdminSubscription("admin", "active", expiresAt = later)).revocableSubscription(now))
    }

    @Test
    fun refundsNeedAPaidPaymentWithAPayUReference() {
        val paid = AdminPaymentRow("MP1", kind = "first", status = "success", amountPaise = 9900, payuRef = "1", createdAt = "x")
        assertTrue(paid.refundable)
        assertFalse(paid.copy(payuRef = null).refundable)
        assertFalse(paid.copy(refundPending = true).refundable)
        assertFalse(paid.copy(status = "failed").refundable)
        assertTrue(paid.copy(status = "partially_refunded").refundable)
    }

    @Test
    fun losingAdminAccessClosesTheScreens() = runTest(dispatcher) {
        val vm = viewModel()
        backend.failWith = AdminError.Forbidden
        vm.reload()
        assertTrue(vm.closed.value)
        assertEquals(listOf("me"), refreshed)
        assertEquals(AdminMessage.Failed(AdminError.Forbidden), vm.messages.first())
    }
}
