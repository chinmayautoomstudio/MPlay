package com.autoomstudio.mp3studio.data.plan

import kotlinx.serialization.Serializable

enum class Plan { Free, Trial, Pro }

/** Features that depend on the plan (PRD section 6.3). Everything else is free for every signed-in user. */
enum class Feature { BpmDetector, SingAlong, UnlimitedSeparator }

/** Outcome of the trial claim the server made on this check, if it made one. */
enum class TrialClaim { Granted, Existing, Denied, Unavailable }

/** `subscriptions.status` (payments PRD 2.1). [PastDue] keeps Pro until the grace period ends. */
enum class SubscriptionStatus {
    Pending, Active, PastDue, Cancelled, Expired, Failed, Unknown;

    companion object {
        fun of(value: String?): SubscriptionStatus? = when (value) {
            null -> null
            "pending" -> Pending
            "active" -> Active
            "past_due" -> PastDue
            "cancelled" -> Cancelled
            "expired" -> Expired
            "failed" -> Failed
            else -> Unknown
        }
    }
}

/** Whether PayU will debit the next month by itself (`subscriptions.autopay_status`). */
enum class AutopayStatus {
    On, Off, NotSet, Revoked;

    companion object {
        fun of(value: String?): AutopayStatus = when (value) {
            "on" -> On
            "off" -> Off
            "revoked" -> Revoked
            else -> NotSet
        }
    }
}

/** Server flag `PAYU_BILLING_MODE`: recurring autopay links, or one-time links renewed by hand each month. */
enum class BillingMode {
    Autopay, Manual;

    companion object {
        fun of(value: String?): BillingMode = if (value == "manual") Manual else Autopay
    }
}

/**
 * What the server said the user may use, as last fetched. Times are epoch milliseconds. [checkedAt] is the phone's
 * clock when the answer arrived and [serverTime] the server's, so the policy can measure how old it is.
 */
@Serializable
data class Entitlements(
    val userId: String,
    val plan: Plan,
    val trialStartedAt: Long? = null,
    val trialEndsAt: Long? = null,
    /** The subscription that gives Pro now, if any. */
    val subscriptionStatus: SubscriptionStatus? = null,
    val subscriptionExpiresAt: Long? = null,
    val subscriptionNextBillingAt: Long? = null,
    val cancelAtPeriodEnd: Boolean = false,
    val trialClaim: TrialClaim? = null,
    val usage: SeparatorUsage? = null,
    /** Only decides whether the Admin screens are offered; the server checks every admin call. */
    val isAdmin: Boolean = false,
    /** The latest PayU subscription, even when it no longer gives Pro (for Fix payment and Resubscribe). */
    val billing: BillingState? = null,
    val pendingPayment: PendingPayment? = null,
    val lastPayment: LastPayment? = null,
    /** Whether the profile has the mobile number PayU needs (payments PRD CK13). */
    val hasPhone: Boolean = false,
    val billingMode: BillingMode = BillingMode.Autopay,
    /** False until the server has PayU credentials; Go Pro stays disabled then. */
    val paymentsEnabled: Boolean = false,
    val serverTime: Long,
    val checkedAt: Long,
)

/** The user's PayU subscription as `compute_entitlements` reports it under `billing`. */
@Serializable
data class BillingState(
    val status: SubscriptionStatus,
    val autopayStatus: AutopayStatus,
    val expiresAt: Long? = null,
    val nextBillingAt: Long? = null,
    val graceEnd: Long? = null,
    val mandateEnd: Long? = null,
    val cancelAtPeriodEnd: Boolean = false,
    /** Cancellation asked for but PayU hasn't confirmed the mandate is revoked yet (CN4). */
    val cancelPending: Boolean = false,
)

/** An unfinished payment attempt; [resolveUntil] is set while the bank hasn't answered. */
@Serializable
data class PendingPayment(
    val txnId: String,
    val status: String,
    val linkExpiresAt: Long? = null,
    val resolveUntil: Long? = null,
)

@Serializable
data class LastPayment(val txnId: String, val status: String, val amountPaise: Int, val at: Long)

/**
 * AI Vocal Separator uses this week (PRD US6), as last reported by the server. [reserved] are queued jobs holding a
 * use; [resetsAt] is the next Monday 00:00 India Standard Time in epoch milliseconds.
 */
@Serializable
data class SeparatorUsage(
    val limit: Int,
    val used: Int,
    val reserved: Int,
    val remaining: Int,
    val resetsAt: Long,
    val unlimited: Boolean,
)
