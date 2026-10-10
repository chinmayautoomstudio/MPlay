package com.autoomstudio.mp3studio.data.plan

import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.functions.functions
import io.ktor.client.statement.bodyAsText
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import java.time.OffsetDateTime

/** Asks the server for the signed-in user's plan. Throws when offline or rejected. */
fun interface EntitlementsBackend {
    suspend fun fetch(): EntitlementsResponse
}

/** Calls the `entitlements` Edge Function, which also claims the 30-day trial for a new account. */
class SupabaseEntitlementsBackend(
    private val client: SupabaseClient,
    private val deviceId: () -> String?,
) : EntitlementsBackend {

    override suspend fun fetch(): EntitlementsResponse {
        val body = buildJsonObject { deviceId()?.let { put("deviceId", it) } }
        val response = client.functions.invoke("entitlements", body)
        return json.decodeFromString(response.bodyAsText())
    }

    private companion object {
        val json = Json { ignoreUnknownKeys = true }
    }
}

@Serializable
data class EntitlementsResponse(
    val plan: String,
    val role: String? = null,
    val trial: TrialDto? = null,
    val subscription: SubscriptionDto? = null,
    val trialClaim: ClaimDto? = null,
    val usage: UsageDto? = null,
    val billing: BillingDto? = null,
    val pendingPayment: PendingPaymentDto? = null,
    val lastPayment: LastPaymentDto? = null,
    val hasPhone: Boolean = false,
    val billingMode: String? = null,
    val paymentsEnabled: Boolean = false,
    val serverTime: String,
) {
    @Serializable
    data class TrialDto(val startedAt: String, val endsAt: String)

    @Serializable
    data class SubscriptionDto(
        val status: String,
        val provider: String? = null,
        val interval: String? = null,
        val expiresAt: String? = null,
        val nextBillingAt: String? = null,
        val cancelAtPeriodEnd: Boolean = false,
    )

    @Serializable
    data class BillingDto(
        val status: String,
        val autopayStatus: String? = null,
        val interval: String? = null,
        val expiresAt: String? = null,
        val nextBillingAt: String? = null,
        val graceEnd: String? = null,
        val mandateEnd: String? = null,
        val cancelAtPeriodEnd: Boolean = false,
        val cancelPending: Boolean = false,
    ) {
        fun toState() = BillingState(
            status = SubscriptionStatus.of(status) ?: SubscriptionStatus.Unknown,
            autopayStatus = AutopayStatus.of(autopayStatus),
            interval = BillingInterval.of(interval),
            expiresAt = expiresAt?.let(::epochMillis),
            nextBillingAt = nextBillingAt?.let(::epochMillis),
            graceEnd = graceEnd?.let(::epochMillis),
            mandateEnd = mandateEnd?.let(::epochMillis),
            cancelAtPeriodEnd = cancelAtPeriodEnd,
            cancelPending = cancelPending,
        )
    }

    @Serializable
    data class PendingPaymentDto(
        val txnId: String,
        val status: String,
        val linkExpiresAt: String? = null,
        val resolveUntil: String? = null,
    )

    @Serializable
    data class LastPaymentDto(val txnId: String, val status: String, val amountPaise: Int, val at: String)

    @Serializable
    data class ClaimDto(val result: String, val reason: String? = null)

    fun toEntitlements(userId: String, checkedAt: Long) = Entitlements(
        userId = userId,
        plan = when (plan) {
            "pro" -> Plan.Pro
            "trial" -> Plan.Trial
            else -> Plan.Free
        },
        trialStartedAt = trial?.startedAt?.let(::epochMillis),
        trialEndsAt = trial?.endsAt?.let(::epochMillis),
        subscriptionStatus = SubscriptionStatus.of(subscription?.status),
        subscriptionInterval = subscription?.takeIf { it.provider == "payu" }?.let { BillingInterval.of(it.interval) },
        subscriptionExpiresAt = subscription?.expiresAt?.let(::epochMillis),
        subscriptionNextBillingAt = subscription?.nextBillingAt?.let(::epochMillis),
        cancelAtPeriodEnd = subscription?.cancelAtPeriodEnd ?: false,
        trialClaim = when (trialClaim?.result) {
            "granted" -> TrialClaim.Granted
            "existing" -> TrialClaim.Existing
            "denied" -> TrialClaim.Denied
            "unavailable" -> TrialClaim.Unavailable
            else -> null
        },
        usage = usage?.toUsage(),
        isAdmin = role == "admin",
        billing = billing?.toState(),
        pendingPayment = pendingPayment?.let {
            PendingPayment(it.txnId, it.status, it.linkExpiresAt?.let(::epochMillis), it.resolveUntil?.let(::epochMillis))
        },
        lastPayment = lastPayment?.let { LastPayment(it.txnId, it.status, it.amountPaise, epochMillis(it.at)) },
        hasPhone = hasPhone,
        billingMode = BillingMode.of(billingMode),
        paymentsEnabled = paymentsEnabled,
        serverTime = epochMillis(serverTime),
        checkedAt = checkedAt,
    )
}

/** The server's `usage_summary`. */
@Serializable
data class UsageDto(
    val limit: Int,
    val used: Int,
    val reserved: Int,
    val remaining: Int,
    val resetsAt: String,
    val unlimited: Boolean,
) {
    fun toUsage() = SeparatorUsage(limit, used, reserved, remaining, epochMillis(resetsAt), unlimited)
}

/** Postgres sends `2026-10-08T05:42:40.359024+00:00`. */
internal fun epochMillis(value: String): Long = OffsetDateTime.parse(value).toInstant().toEpochMilli()
