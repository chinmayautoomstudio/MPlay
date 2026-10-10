package com.autoomstudio.mp3studio.ui.plans

import com.autoomstudio.mp3studio.data.plan.AutopayStatus
import com.autoomstudio.mp3studio.data.plan.BillingInterval
import com.autoomstudio.mp3studio.data.plan.BillingMode
import com.autoomstudio.mp3studio.data.plan.BillingState
import com.autoomstudio.mp3studio.data.plan.PendingPayment
import com.autoomstudio.mp3studio.data.plan.Plan
import com.autoomstudio.mp3studio.data.plan.SubscriptionStatus

/** The billing line on the Plans screen (payments PRD PS1, PS2). Dates are epoch milliseconds. */
sealed interface BillingNotice {
    data object PaymentsUnavailable : BillingNotice
    data class PaymentPending(val txnId: String) : BillingNotice
    data class AutopayOn(val nextBillingAt: Long?, val interval: BillingInterval = BillingInterval.Month) : BillingNotice
    data class AutopayNotSet(val expiresAt: Long?) : BillingNotice
    data class RenewSoon(val expiresAt: Long?) : BillingNotice
    data class PastDue(val graceEnd: Long?) : BillingNotice
    data class CancelPending(val expiresAt: Long?) : BillingNotice
    data class Cancelled(val expiresAt: Long?) : BillingNotice
    data class MandateEnding(val mandateEnd: Long) : BillingNotice
    data object Expired : BillingNotice
}

/** The button under it. */
enum class BillingAction {
    GoPro, GoProAgain, FixPayment, SetUpAutopay, Renew, CheckPayment, SwitchToYearly, SwitchToMonthly, CancelAutopay,
}

data class BillingStatus(val notice: BillingNotice?, val actions: List<BillingAction>)

object BillingStatusRules {
    /** A manual-mode renewal opens this long before the paid month ends, as `begin_checkout` allows. */
    const val RENEW_WINDOW_MS = 7L * 24 * 60 * 60 * 1000

    /** Warn about an autopay mandate ending this long before. */
    const val MANDATE_WARNING_MS = 30L * 24 * 60 * 60 * 1000

    /** Switching between monthly and yearly opens this long before the paid period ends, as `begin_checkout` allows. */
    const val SWITCH_WINDOW_MS = 31L * 24 * 60 * 60 * 1000

    fun of(
        plan: Plan,
        billing: BillingState?,
        pending: PendingPayment?,
        mode: BillingMode,
        paymentsEnabled: Boolean,
        now: Long,
    ): BillingStatus {
        if (pending != null && pending.status == "pending") {
            return BillingStatus(BillingNotice.PaymentPending(pending.txnId), listOf(BillingAction.CheckPayment))
        }
        if (billing == null || billing.status == SubscriptionStatus.Failed || billing.status == SubscriptionStatus.Pending) {
            // Admin-granted Pro has no PayU subscription and nothing to manage.
            if (plan == Plan.Pro) return BillingStatus(null, emptyList())
            return if (paymentsEnabled) BillingStatus(null, listOf(BillingAction.GoPro))
            else BillingStatus(BillingNotice.PaymentsUnavailable, emptyList())
        }
        val pay = { action: BillingAction -> if (paymentsEnabled) listOf(action) else emptyList() }
        return when (billing.status) {
            SubscriptionStatus.PastDue -> BillingStatus(BillingNotice.PastDue(billing.graceEnd), pay(BillingAction.FixPayment))
            SubscriptionStatus.Expired, SubscriptionStatus.Unknown ->
                BillingStatus(BillingNotice.Expired, pay(BillingAction.GoProAgain))
            SubscriptionStatus.Cancelled -> when {
                billing.cancelPending -> BillingStatus(BillingNotice.CancelPending(billing.expiresAt), emptyList())
                billing.expiresAt != null && billing.expiresAt <= now ->
                    BillingStatus(BillingNotice.Expired, pay(BillingAction.GoProAgain))
                else -> BillingStatus(BillingNotice.Cancelled(billing.expiresAt), pay(BillingAction.GoProAgain))
            }
            else -> active(billing, mode, now, pay)
        }
    }

    private fun active(
        billing: BillingState,
        mode: BillingMode,
        now: Long,
        pay: (BillingAction) -> List<BillingAction>,
    ): BillingStatus {
        if (billing.cancelPending) return BillingStatus(BillingNotice.CancelPending(billing.expiresAt), emptyList())
        if (billing.autopayStatus == AutopayStatus.On) {
            val mandateEnd = billing.mandateEnd
            // A new mandate can only be set up once this one has ended (begin_checkout refuses while it is on).
            if (mandateEnd != null && mandateEnd - now < MANDATE_WARNING_MS) {
                return BillingStatus(BillingNotice.MandateEnding(mandateEnd), listOf(BillingAction.CancelAutopay))
            }
            val switch = when {
                billing.interval == BillingInterval.Month -> pay(BillingAction.SwitchToYearly)
                billing.expiresAt == null || billing.expiresAt - now <= SWITCH_WINDOW_MS -> pay(BillingAction.SwitchToMonthly)
                else -> emptyList()
            }
            return BillingStatus(
                BillingNotice.AutopayOn(billing.nextBillingAt, billing.interval),
                switch + BillingAction.CancelAutopay,
            )
        }
        if (mode == BillingMode.Manual) {
            val renewOpen = billing.expiresAt == null || billing.expiresAt - now <= RENEW_WINDOW_MS
            return BillingStatus(
                BillingNotice.RenewSoon(billing.expiresAt),
                if (renewOpen) pay(BillingAction.Renew) else emptyList(),
            )
        }
        return BillingStatus(BillingNotice.AutopayNotSet(billing.expiresAt), pay(BillingAction.SetUpAutopay))
    }
}
