package com.autoomstudio.mp3studio.data.billing

import com.autoomstudio.mp3studio.data.plan.BillingInterval
import com.autoomstudio.mp3studio.data.plan.BillingMode
import com.autoomstudio.mp3studio.data.plan.Plan
import com.autoomstudio.mp3studio.data.plan.epochMillis
import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.exceptions.HttpRequestException
import io.github.jan.supabase.exceptions.RestException
import io.github.jan.supabase.functions.functions
import io.github.jan.supabase.postgrest.from
import io.github.jan.supabase.postgrest.query.Columns
import io.github.jan.supabase.postgrest.query.Order
import io.ktor.client.statement.bodyAsText
import kotlinx.coroutines.CancellationException
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import java.io.IOException

/** Pro payments through the server (payments PRD). The app never talks to PayU; every call throws [BillingException]. */
interface BillingBackend {
    /** Creates a PayU payment link for [interval]; [phone] is saved to the profile when given. */
    suspend fun startCheckout(phone: String?, interval: BillingInterval): CheckoutLink

    /** Checks [txnId] (or the latest attempt) with PayU on the server and returns where it stands. */
    suspend fun paymentStatus(txnId: String?): PaymentSummary

    suspend fun cancelSubscription(): CancelResult

    /** The signed-in user's payments, newest first. */
    suspend fun payments(): List<PaymentRecord>
}

class SupabaseBillingBackend(private val client: SupabaseClient) : BillingBackend {

    override suspend fun startCheckout(phone: String?, interval: BillingInterval): CheckoutLink {
        val dto = call<CheckoutDto>(
            "start-checkout",
            buildJsonObject {
                phone?.let { put("phone", it) }
                put("interval", interval.wire)
            },
        )
        return CheckoutLink(dto.url, dto.txnId, BillingMode.of(dto.mode))
    }

    override suspend fun paymentStatus(txnId: String?): PaymentSummary =
        call<SummaryDto>("payment-status", buildJsonObject { txnId?.let { put("txnId", it) } }).toSummary()

    override suspend fun cancelSubscription(): CancelResult {
        val dto = call<CancelDto>("subscription", buildJsonObject { put("action", "cancel") })
        return if (dto.result == "cancelled") CancelResult.Cancelled(dto.expiresAt?.let(::epochMillis)) else CancelResult.Pending
    }

    override suspend fun payments(): List<PaymentRecord> = guard {
        client.from("payments")
            .select(Columns.list(PaymentRow.COLUMNS)) {
                order("created_at", Order.DESCENDING)
                limit(HISTORY_LIMIT)
            }
            .decodeList<PaymentRow>()
            .map(PaymentRow::toRecord)
    }

    private suspend inline fun <reified T> call(function: String, body: JsonObject): T {
        val text = guard { client.functions.invoke(function, body).bodyAsText() }
        return try {
            json.decodeFromString<T>(text)
        } catch (e: Exception) {
            throw BillingException(BillingError.Other, e)
        }
    }

    private inline fun <T> guard(block: () -> T): T = try {
        block()
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        throw BillingException(billingErrorOf(e), e)
    }

    @Serializable
    private data class CheckoutDto(val url: String, val txnId: String, val mode: String? = null)

    @Serializable
    private data class CancelDto(val result: String, val expiresAt: String? = null)

    private companion object {
        const val HISTORY_LIMIT = 50L
        val json = Json { ignoreUnknownKeys = true }
    }
}

@Serializable
internal data class SummaryDto(
    val txnId: String,
    val status: String,
    val failureReason: String? = null,
    val resolveUntil: String? = null,
    val amountPaise: Int = 0,
    val completedAt: String? = null,
    val plan: String? = null,
) {
    fun toSummary() = PaymentSummary(
        txnId = txnId,
        state = PaymentState.of(status),
        failureReason = failureReason,
        resolveUntil = resolveUntil?.let(::epochMillis),
        amountPaise = amountPaise,
        completedAt = completedAt?.let(::epochMillis),
        plan = when (plan) {
            "pro" -> Plan.Pro
            "trial" -> Plan.Trial
            else -> Plan.Free
        },
    )
}

@Serializable
internal data class PaymentRow(
    @SerialName("txn_id") val txnId: String,
    val kind: String,
    @SerialName("billing_interval") val interval: String? = null,
    val status: String,
    @SerialName("amount_paise") val amountPaise: Int,
    @SerialName("refunded_paise") val refundedPaise: Int = 0,
    val method: String? = null,
    @SerialName("created_at") val createdAt: String,
    @SerialName("completed_at") val completedAt: String? = null,
    @SerialName("period_start") val periodStart: String? = null,
    @SerialName("period_end") val periodEnd: String? = null,
    @SerialName("failure_reason") val failureReason: String? = null,
) {
    fun toRecord() = PaymentRecord(
        txnId = txnId,
        kind = kind,
        interval = BillingInterval.of(interval),
        state = PaymentState.of(status),
        amountPaise = amountPaise,
        refundedPaise = refundedPaise,
        method = method,
        createdAt = epochMillis(createdAt),
        completedAt = completedAt?.let(::epochMillis),
        periodStart = periodStart?.let(::epochMillis),
        periodEnd = periodEnd?.let(::epochMillis),
        failureReason = failureReason,
    )

    companion object {
        val COLUMNS = listOf(
            "txn_id", "kind", "billing_interval", "status", "amount_paise", "refunded_paise", "method", "created_at", "completed_at",
            "period_start", "period_end", "failure_reason",
        )
    }
}

/** Edge Functions answer refusals with 4xx/5xx `{"error": code}`. */
internal fun billingErrorOf(e: Throwable): BillingError = when (e) {
    is RestException -> billingErrorOf(e.statusCode, "${e.error} ${e.description.orEmpty()}")
    is HttpRequestException, is IOException -> BillingError.Offline
    else -> BillingError.Other
}

internal fun billingErrorOf(status: Int, body: String): BillingError {
    val code = CODES.firstOrNull { it in body }
    return when {
        code != null -> BillingError.of(code)
        status == 503 -> BillingError.Unavailable
        status == 429 -> BillingError.RateLimited
        else -> BillingError.Other
    }
}

private val CODES = listOf(
    "phone_required", "invalid_phone", "invalid_interval", "payment_in_progress", "already_subscribed",
    "switch_not_yet", "mandate_active",
    "mandate_update_pending", "rate_limited", "account_disabled", "payu_unavailable", "not_subscribed", "not_found",
)
