package com.autoomstudio.mp3studio.data.admin

import com.autoomstudio.mp3studio.data.plan.EntitlementsResponse
import com.autoomstudio.mp3studio.data.plan.UsageDto
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonObject

/** Answers of the `admin` Edge Function. Timestamps are ISO strings and week starts are `yyyy-MM-dd` dates. */

enum class UserFilter(val wire: String) { All("all"), Pro("pro"), Trial("trial"), Free("free"), Disabled("disabled"), Admins("admin") }

enum class AddAdminResult { Promoted, AlreadyAdmin, Invited }

/** `admin_list_payments` filters (payments PRD AD2). */
enum class PaymentFilter(val wire: String) {
    All("all"),
    Success("success"),
    Failed("failed"),
    Pending("pending"),
    Refunded("refunded"),
    Disputed("disputed"),
    PastDue("past_due"),
    Cancelled("cancelled"),
}

/** Why an admin call failed, as the screens explain it. */
enum class AdminError {
    Forbidden, LastAdmin, Self, InvalidEmail, InvalidDate, NotFound,

    /** Refund refused: not paid, no PayU reference, or a refund already requested. */
    NotRefundable,

    /** No active or past-due PayU subscription to cancel. */
    NotSubscribed,

    /** PayU answered but refused the request. */
    PayuRefused,

    /** PayU isn't configured on the server. */
    PaymentsUnavailable,
    Offline, Other,
}

/** What a billing action did once PayU answered. */
enum class AdminBillingResult { Checked, RefundRequested, Cancelled, CancelPending }

class AdminException(val error: AdminError, cause: Throwable? = null) : Exception(error.name, cause)

@Serializable
data class AdminOverview(
    val users: Int,
    val pro: Int,
    val trial: Int,
    val free: Int,
    val disabled: Int,
    val admins: Int,
    val completed: Int,
    val reserved: Int,
    val denied: Int,
    val weekStart: String,
)

@Serializable
data class AdminUserRow(
    val id: String,
    val email: String? = null,
    val name: String? = null,
    val role: String,
    val plan: String,
    val disabled: Boolean,
    val createdAt: String,
    val usedThisWeek: Int = 0,
)

@Serializable
data class AdminUserPage(val total: Int, val users: List<AdminUserRow>)

@Serializable
data class AdminProfile(
    val id: String,
    val email: String? = null,
    val name: String? = null,
    val avatarUrl: String? = null,
    val role: String,
    val disabled: Boolean,
    val createdAt: String,
)

@Serializable
data class AdminEntitlements(
    val plan: String,
    val trial: EntitlementsResponse.TrialDto? = null,
    val subscription: EntitlementsResponse.SubscriptionDto? = null,
)

@Serializable
data class AdminWeek(
    val weekStart: String,
    val completed: Int,
    val released: Int = 0,
    val denied: Int = 0,
    val users: Int = 0,
)

@Serializable
data class AdminJob(
    val jobRef: String,
    val songRef: String? = null,
    val status: String,
    val reservedAt: String,
    val completedAt: String? = null,
)

@Serializable
data class AdminSubscription(
    val provider: String,
    val status: String,
    val startedAt: String? = null,
    val expiresAt: String? = null,
    val nextBillingAt: String? = null,
    val lastPaymentAt: String? = null,
    val paymentStatus: String? = null,
    val cancelAtPeriodEnd: Boolean = false,
    val autopayStatus: String? = null,
    val graceEnd: String? = null,
    val mandateEnd: String? = null,
    /** Cancellation asked for but PayU hasn't confirmed it yet. */
    val cancelPending: Boolean = false,
)

@Serializable
data class AdminPaymentEvent(
    val provider: String,
    val type: String,
    val txnId: String? = null,
    val reference: String? = null,
    val amountPaise: Int? = null,
    val currency: String? = null,
    val createdAt: String,
)

/** One `payments` row as the Admin screens list it. [userId] and [email] are null once the account was deleted. */
@Serializable
data class AdminPaymentRow(
    val txnId: String,
    val userId: String? = null,
    val email: String? = null,
    val kind: String,
    val status: String,
    val amountPaise: Int,
    val refundedPaise: Int = 0,
    val payuRef: String? = null,
    val method: String? = null,
    val failureReason: String? = null,
    val subscriptionStatus: String? = null,
    val refundPending: Boolean = false,
    val createdAt: String,
    val completedAt: String? = null,
) {
    /** Mirrors `admin_billing_action`: only paid payments with a PayU reference and no refund in flight. */
    val refundable: Boolean
        get() = (status == "success" || status == "partially_refunded") && payuRef != null && !refundPending
}

@Serializable
data class AdminPaymentPage(val total: Int, val payments: List<AdminPaymentRow>)

@Serializable
data class AdminJobHealth(
    val job: String,
    val lastRunAt: String? = null,
    val processed: Int = 0,
    val failed: Int = 0,
    val skipped: Int = 0,
    val error: String? = null,
    val overdue: Boolean,
)

/** `admin_billing_health`: scheduled jobs and the queues an Admin may need to look at. */
@Serializable
data class AdminBillingHealth(
    val jobs: List<AdminJobHealth> = emptyList(),
    val flaggedWebhooks: Int = 0,
    val failedWebhooks: Int = 0,
    val pendingMandateCancels: Int = 0,
    val needsReview: Int = 0,
) {
    val healthy: Boolean
        get() = jobs.none { it.overdue } && flaggedWebhooks == 0 && failedWebhooks == 0 &&
            pendingMandateCancels == 0 && needsReview == 0
}

@Serializable
data class AdminUserDetail(
    val profile: AdminProfile,
    val entitlements: AdminEntitlements? = null,
    val usage: UsageDto,
    val weeks: List<AdminWeek> = emptyList(),
    val jobs: List<AdminJob> = emptyList(),
    val subscriptions: List<AdminSubscription> = emptyList(),
    val payments: List<AdminPaymentRow> = emptyList(),
    val events: List<AdminPaymentEvent> = emptyList(),
) {
    /** The Pro an Admin granted, while it's active. */
    val adminGrant: AdminSubscription?
        get() = subscriptions.firstOrNull { it.provider == "admin" && it.status == "active" }

    /** The PayU subscription an Admin can cancel. */
    val payuSubscription: AdminSubscription?
        get() = subscriptions.firstOrNull { it.provider == "payu" && (it.status == "active" || it.status == "past_due") }
}

@Serializable
data class AdminTopUser(val id: String, val email: String? = null, val name: String? = null, val completed: Int)

@Serializable
data class AdminUsage(val weeks: List<AdminWeek>, val top: List<AdminTopUser>)

@Serializable
data class AdminMember(val id: String, val email: String? = null, val name: String? = null, val disabled: Boolean)

@Serializable
data class AdminInvite(val email: String, val invitedBy: String? = null, val createdAt: String)

@Serializable
data class AdminList(val admins: List<AdminMember>, val invites: List<AdminInvite>)

/** One event in the Admin activity feed. [type] is `signup`, `subscribed` or `deleted`; deleted accounts have no user. */
@Serializable
data class AdminActivity(
    val type: String,
    val at: String,
    val userId: String? = null,
    val email: String? = null,
    val name: String? = null,
    val provider: String? = null,
    val plan: String? = null,
)

@Serializable
data class AuditEntry(
    val id: Long,
    val action: String,
    val actorEmail: String? = null,
    val targetId: String? = null,
    val targetEmail: String? = null,
    val details: JsonObject? = null,
    val createdAt: String,
)
