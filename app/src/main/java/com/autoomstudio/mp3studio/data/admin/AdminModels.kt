package com.autoomstudio.mp3studio.data.admin

import com.autoomstudio.mp3studio.data.plan.EntitlementsResponse
import com.autoomstudio.mp3studio.data.plan.UsageDto
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonObject

/** Answers of the `admin` Edge Function. Timestamps are ISO strings and week starts are `yyyy-MM-dd` dates. */

enum class UserFilter(val wire: String) { All("all"), Pro("pro"), Trial("trial"), Free("free"), Disabled("disabled"), Admins("admin") }

enum class AddAdminResult { Promoted, AlreadyAdmin, Invited }

/** Why an admin call failed, as the screens explain it. */
enum class AdminError { Forbidden, LastAdmin, Self, InvalidEmail, InvalidDate, NotFound, Offline, Other }

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
)

@Serializable
data class AdminPaymentEvent(
    val provider: String,
    val type: String,
    val amountPaise: Int? = null,
    val currency: String? = null,
    val createdAt: String,
)

@Serializable
data class AdminUserDetail(
    val profile: AdminProfile,
    val entitlements: AdminEntitlements? = null,
    val usage: UsageDto,
    val weeks: List<AdminWeek> = emptyList(),
    val jobs: List<AdminJob> = emptyList(),
    val subscriptions: List<AdminSubscription> = emptyList(),
    val events: List<AdminPaymentEvent> = emptyList(),
) {
    /** The Pro an Admin granted, while it's active. */
    val adminGrant: AdminSubscription?
        get() = subscriptions.firstOrNull { it.provider == "admin" && it.status == "active" }
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
