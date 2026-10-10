package com.autoomstudio.mp3studio.data.billing

import com.autoomstudio.mp3studio.data.plan.BillingInterval
import com.autoomstudio.mp3studio.data.plan.BillingMode
import com.autoomstudio.mp3studio.data.plan.Plan

/** Why a billing call didn't go through. The server sends these as `{"error": code}`. */
enum class BillingError {
    /** PayU needs a mobile number and the profile has none (CK13). */
    PhoneRequired,
    InvalidPhone,

    /** Another payment attempt is still being confirmed. */
    PaymentInProgress,
    AlreadySubscribed,

    /** A switch between monthly and yearly opens 31 days before the paid period ends. */
    SwitchNotYet,
    InvalidInterval,

    /** The old autopay couldn't be cancelled with PayU yet, so no new one can be set up (5.5). */
    MandateUpdatePending,
    RateLimited,
    AccountDisabled,

    /** Payments aren't set up on the server, or PayU didn't answer. */
    Unavailable,
    NotSubscribed,
    NotFound,
    Offline,
    Other,
    ;

    companion object {
        fun of(code: String?): BillingError = when (code) {
            "phone_required" -> PhoneRequired
            "invalid_phone" -> InvalidPhone
            "payment_in_progress" -> PaymentInProgress
            "already_subscribed", "mandate_active" -> AlreadySubscribed
            "switch_not_yet" -> SwitchNotYet
            "invalid_interval" -> InvalidInterval
            "mandate_update_pending" -> MandateUpdatePending
            "rate_limited" -> RateLimited
            "account_disabled" -> AccountDisabled
            "payu_unavailable" -> Unavailable
            "not_subscribed" -> NotSubscribed
            "not_found" -> NotFound
            else -> Other
        }
    }
}

class BillingException(val error: BillingError, cause: Throwable? = null) :
    Exception("Billing call failed: $error", cause)

/** A PayU payment link for one attempt; [txnId] is our reference for checking it afterwards. */
data class CheckoutLink(val url: String, val txnId: String, val mode: BillingMode)

/** `payments.status`. */
enum class PaymentState {
    Created, Pending, Success, Failed, Cancelled, Refunded, PartiallyRefunded, Disputed, Unknown;

    /** PayU has a final answer: nothing more to wait for. */
    val settled: Boolean get() = this != Created && this != Pending && this != Unknown

    companion object {
        fun of(value: String?): PaymentState = when (value) {
            "created" -> Created
            "pending" -> Pending
            "success" -> Success
            "failed" -> Failed
            "cancelled" -> Cancelled
            "refunded" -> Refunded
            "partially_refunded" -> PartiallyRefunded
            "disputed" -> Disputed
            else -> Unknown
        }
    }
}

/** The `payment-status` answer after the server checked the payment with PayU. Times are epoch milliseconds. */
data class PaymentSummary(
    val txnId: String,
    val state: PaymentState,
    val failureReason: String? = null,
    /** While the bank hasn't answered, the server keeps checking until this time (CK9). */
    val resolveUntil: Long? = null,
    val amountPaise: Int = 0,
    val completedAt: Long? = null,
    val plan: Plan = Plan.Free,
)

sealed interface CancelResult {
    /** Autopay is off; Pro stays until [expiresAt]. */
    data class Cancelled(val expiresAt: Long?) : CancelResult

    /** Renewals are stopped but PayU hasn't confirmed yet; the server keeps retrying (CN4). */
    data object Pending : CancelResult
}

/** One row of the user's payment history (PS4, PS5). */
data class PaymentRecord(
    val txnId: String,
    val kind: String,
    val state: PaymentState,
    val amountPaise: Int,
    val refundedPaise: Int,
    val interval: BillingInterval = BillingInterval.Month,
    val method: String?,
    val createdAt: Long,
    val completedAt: Long?,
    val periodStart: Long?,
    val periodEnd: Long?,
    val failureReason: String?,
)
