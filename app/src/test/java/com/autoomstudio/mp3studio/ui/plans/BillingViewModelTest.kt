package com.autoomstudio.mp3studio.ui.plans

import com.autoomstudio.mp3studio.data.billing.BillingError
import com.autoomstudio.mp3studio.data.billing.BillingRepository
import com.autoomstudio.mp3studio.data.billing.BillingRepositoryTest.FakeBackend
import com.autoomstudio.mp3studio.data.billing.BillingRepositoryTest.FakeRegionStore
import com.autoomstudio.mp3studio.data.billing.BillingRepositoryTest.FakeStore
import com.autoomstudio.mp3studio.data.billing.CancelResult
import com.autoomstudio.mp3studio.data.billing.PaymentState
import com.autoomstudio.mp3studio.data.plan.AutopayStatus
import com.autoomstudio.mp3studio.data.plan.BillingInterval
import com.autoomstudio.mp3studio.data.plan.BillingMode
import com.autoomstudio.mp3studio.data.plan.BillingRegion
import com.autoomstudio.mp3studio.data.plan.BillingState
import com.autoomstudio.mp3studio.data.plan.BillingCurrency
import com.autoomstudio.mp3studio.data.plan.Entitlements
import com.autoomstudio.mp3studio.data.plan.Plan
import com.autoomstudio.mp3studio.data.plan.PriceList
import com.autoomstudio.mp3studio.data.plan.SubscriptionStatus
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class BillingViewModelTest {

    private val dispatcher = UnconfinedTestDispatcher()

    @Before
    fun setUp() = Dispatchers.setMain(dispatcher)

    @After
    fun tearDown() = Dispatchers.resetMain()

    private val backend = FakeBackend()
    private val store = FakeStore()
    private val regionStore = FakeRegionStore()
    private val repository = BillingRepository(backend, store, regionStore, {}, {})
    private var entitlements = Entitlements(
        userId = "u1",
        plan = Plan.Free,
        hasPhone = false,
        hasIndianPhone = false,
        billingMode = BillingMode.Autopay,
        paymentsEnabled = true,
        serverTime = 0,
        checkedAt = 0,
    )
    private val viewModel = BillingViewModel(
        repository = repository,
        signedInUserId = { "u1" },
        entitlements = { entitlements },
    )

    @Test
    fun anInternationalCheckoutIsManualAndSendsTheRegion() {
        entitlements = entitlements.copy(detectedRegion = BillingRegion.International)
        viewModel.openCheckout(region = BillingRegion.International)
        val sheet = viewModel.state.value.checkout!!
        assertEquals(BillingRegion.International, sheet.region)
        assertEquals(BillingMode.Manual, sheet.mode)
        assertTrue(sheet.needsPhone)
        viewModel.onPhoneChange("+14155551212")
        viewModel.pay()
        assertEquals(listOf(BillingRegion.International), backend.regions)
    }

    @Test
    fun checkoutDefaultsToTheDetectedRegionWhenThereIsNoOverride() {
        entitlements = entitlements.copy(detectedRegion = BillingRegion.International, hasPhone = true)
        viewModel.openCheckout()
        val sheet = viewModel.state.value.checkout!!
        assertEquals(BillingRegion.International, sheet.region)
        assertEquals(BillingMode.Manual, sheet.mode)
        assertEquals(PriceList.International, sheet.prices)
    }

    @Test
    fun aStoredOverrideBeatsTheDetectedRegion() = runTest {
        repository.setRegionOverride(BillingRegion.International)
        entitlements = entitlements.copy(detectedRegion = BillingRegion.India, hasPhone = true)
        viewModel.openCheckout()
        assertEquals(BillingRegion.International, viewModel.state.value.checkout!!.region)
        assertEquals(BillingMode.Manual, viewModel.state.value.checkout!!.mode)
    }

    @Test
    fun renewAndFixStayOnTheSubscriptionCurrency() {
        val usd = BillingState(
            SubscriptionStatus.Active,
            AutopayStatus.NotSet,
            expiresAt = 5_000L,
            currency = BillingCurrency.Usd,
        )
        entitlements = entitlements.copy(
            hasPhone = true,
            detectedRegion = BillingRegion.India,
            billing = usd,
        )
        viewModel.openCheckout(CheckoutPurpose.Renew, region = BillingRegion.India)
        assertEquals(BillingRegion.International, viewModel.state.value.checkout!!.region)
        viewModel.openCheckout(CheckoutPurpose.FixPayment, region = BillingRegion.India)
        assertEquals(BillingRegion.International, viewModel.state.value.checkout!!.region)
    }

    @Test
    fun checkoutAsksForAPhoneWhenTheProfileHasNone() {
        viewModel.openCheckout()
        val sheet = viewModel.state.value.checkout!!
        assertTrue(sheet.needsPhone)
        assertEquals(BillingMode.Autopay, sheet.mode)
    }

    @Test
    fun newUsersStartOnMonthlyAndThePickedIntervalIsSent() {
        entitlements = entitlements.copy(hasPhone = true, hasIndianPhone = true)
        viewModel.openCheckout()
        val sheet = viewModel.state.value.checkout!!
        assertEquals(BillingInterval.Month, sheet.interval)
        assertFalse(sheet.intervalLocked)
        viewModel.onIntervalChange(BillingInterval.Year)
        assertEquals(BillingInterval.Year, viewModel.state.value.checkout!!.interval)
        viewModel.pay()
        assertEquals(listOf(BillingInterval.Year), backend.intervals)
    }

    @Test
    fun checkoutDefaultsToTheCurrentIntervalAndASwitchLocksTheOther() {
        val yearly = BillingState(
            SubscriptionStatus.Active,
            AutopayStatus.On,
            expiresAt = 5_000L,
            interval = BillingInterval.Year,
        )
        entitlements = entitlements.copy(hasPhone = true, hasIndianPhone = true, billing = yearly)
        viewModel.openCheckout(CheckoutPurpose.FixPayment)
        assertEquals(BillingInterval.Year, viewModel.state.value.checkout!!.interval)
        assertNull(viewModel.state.value.checkout!!.switchStartsAt)

        viewModel.openCheckout(CheckoutPurpose.SwitchInterval)
        val sheet = viewModel.state.value.checkout!!
        assertEquals(BillingInterval.Month, sheet.interval)
        assertTrue(sheet.intervalLocked)
        assertEquals(5_000L, sheet.switchStartsAt)
        viewModel.onIntervalChange(BillingInterval.Year)
        assertEquals("A switch can't be changed back", BillingInterval.Month, viewModel.state.value.checkout!!.interval)
        viewModel.pay()
        assertEquals(listOf(BillingInterval.Month), backend.intervals)
    }

    @Test
    fun thePlansToggleSetsTheStartingIntervalExceptForASwitch() {
        val monthly = BillingState(SubscriptionStatus.Active, AutopayStatus.On, expiresAt = 5_000L)
        entitlements = entitlements.copy(hasPhone = true, hasIndianPhone = true, billing = monthly)
        viewModel.openCheckout(CheckoutPurpose.Subscribe, preselect = BillingInterval.Year)
        assertEquals(BillingInterval.Year, viewModel.state.value.checkout!!.interval)
        assertFalse(viewModel.state.value.checkout!!.intervalLocked)

        viewModel.openCheckout(CheckoutPurpose.SwitchInterval, preselect = BillingInterval.Month)
        assertEquals(BillingInterval.Year, viewModel.state.value.checkout!!.interval)
    }

    @Test
    fun aBadPhoneIsCaughtBeforeCallingTheServer() {
        viewModel.openCheckout()
        viewModel.onPhoneChange("12345")
        viewModel.pay()
        assertTrue(viewModel.state.value.checkout!!.phoneInvalid)
        assertNull(store.value)
    }

    @Test
    fun payingOpensTheLinkAndComingBackConfirms() = runTest {
        entitlements = entitlements.copy(hasPhone = true, hasIndianPhone = true)
        viewModel.openCheckout()
        viewModel.pay()
        assertNull(viewModel.state.value.checkout)
        assertEquals("https://u.payu.in/x", viewModel.openUrl.first())

        backend.answers += PaymentState.Success
        viewModel.onForeground()
        assertEquals(PaymentResultUi.Success(null), viewModel.state.value.result)
        assertEquals(listOf<String?>("MPNEW"), backend.asked)
    }

    @Test
    fun serverRefusalsStayOnTheSheet() {
        entitlements = entitlements.copy(hasPhone = true, hasIndianPhone = true)
        backend.fail = BillingError.MandateUpdatePending
        viewModel.openCheckout()
        viewModel.pay()
        val sheet = viewModel.state.value.checkout!!
        assertEquals(BillingError.MandateUpdatePending, sheet.error)
        assertTrue(!sheet.busy)
    }

    @Test
    fun aMissingPhoneOnTheServerShowsThePhoneField() {
        entitlements = entitlements.copy(hasPhone = true, hasIndianPhone = true)
        backend.fail = BillingError.PhoneRequired
        viewModel.openCheckout()
        viewModel.pay()
        val sheet = viewModel.state.value.checkout!!
        assertTrue(sheet.needsPhone)
        assertNull(sheet.error)
    }

    @Test
    fun resultsFollowWhatTheServerFound() {
        store.value = "u1" to "MP1"
        backend.answers += List(BillingViewModel.VISIBLE_ATTEMPTS) { PaymentState.Pending }
        viewModel.onReturned(null)
        assertEquals(PaymentResultUi.Waiting(null), viewModel.state.value.result)

        viewModel.dismissResult()
        backend.answers += PaymentState.Failed
        viewModel.onReturned("MP1")
        assertEquals(PaymentResultUi.Failed(null), viewModel.state.value.result)
    }

    @Test
    fun aQuietCheckOnlyShowsSuccess() {
        store.value = "u1" to "MP1"
        backend.answers += PaymentState.Failed
        viewModel.onForeground()
        assertNull(viewModel.state.value.result)

        store.value = "u1" to "MP2"
        backend.answers += PaymentState.Success
        viewModel.onForeground()
        assertEquals(PaymentResultUi.Success(null), viewModel.state.value.result)
    }

    @Test
    fun offlineAfterReturningSaysTheServerWillFinish() {
        store.value = "u1" to "MP1"
        backend.fail = BillingError.Offline
        viewModel.onReturned(null)
        assertEquals(PaymentResultUi.Unchecked(BillingError.Offline), viewModel.state.value.result)
    }

    @Test
    fun cancellingShowsWhetherPayUConfirmed() {
        viewModel.requestCancel()
        assertTrue(viewModel.state.value.cancel is CancelUi.Confirm)
        viewModel.confirmCancel()
        assertEquals(CancelUi.Done(CancelResult.Pending), viewModel.state.value.cancel)
        viewModel.dismissCancel()
        assertNull(viewModel.state.value.cancel)
    }
}
