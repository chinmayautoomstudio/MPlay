package com.autoomstudio.mp3studio.data.billing

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.autoomstudio.mp3studio.data.plan.BillingInterval
import com.autoomstudio.mp3studio.data.plan.BillingRegion
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first

/** The payment attempt the user last opened, so it is still checked after the app was closed (payments PRD CK8). */
interface PendingPaymentStore {
    /** `(userId, txnId)`, or null when nothing is waiting. */
    suspend fun read(): Pair<String, String>?
    suspend fun write(userId: String, txnId: String)
    suspend fun clear()
}

/** Manual India / International override; null means use the IP-detected region. */
interface BillingRegionStore {
    suspend fun readOverride(): BillingRegion?
    suspend fun writeOverride(region: BillingRegion)
    suspend fun clearOverride()
}

private val Context.billingDataStore: DataStore<Preferences> by preferencesDataStore(name = "billing")

class DataStorePendingPaymentStore(context: Context) : PendingPaymentStore, BillingRegionStore {

    private val dataStore = context.applicationContext.billingDataStore

    override suspend fun read(): Pair<String, String>? {
        val prefs = dataStore.data.first()
        val user = prefs[KEY_USER] ?: return null
        val txn = prefs[KEY_TXN] ?: return null
        return user to txn
    }

    override suspend fun write(userId: String, txnId: String) {
        dataStore.edit {
            it[KEY_USER] = userId
            it[KEY_TXN] = txnId
        }
    }

    override suspend fun clear() {
        dataStore.edit {
            it.remove(KEY_USER)
            it.remove(KEY_TXN)
        }
    }

    override suspend fun readOverride(): BillingRegion? = when (dataStore.data.first()[KEY_REGION]) {
        BillingRegion.India.wire -> BillingRegion.India
        BillingRegion.International.wire -> BillingRegion.International
        else -> null
    }

    override suspend fun writeOverride(region: BillingRegion) {
        dataStore.edit { it[KEY_REGION] = region.wire }
    }

    override suspend fun clearOverride() {
        dataStore.edit { it.remove(KEY_REGION) }
    }

    private companion object {
        val KEY_USER = stringPreferencesKey("pending_payment_user")
        val KEY_TXN = stringPreferencesKey("pending_payment_txn")
        val KEY_REGION = stringPreferencesKey("billing_region_override")
    }
}

/**
 * Starts PayU checkouts and follows them to an answer. Whether Pro is granted is always decided by the server after it
 * checked the payment with PayU; this only asks. [refreshPlan] reloads entitlements once a payment settles (SV9).
 */
class BillingRepository(
    private val backend: BillingBackend,
    private val store: PendingPaymentStore,
    private val regionStore: BillingRegionStore,
    private val refreshPlan: suspend (userId: String) -> Unit,
    private val sleep: suspend (Long) -> Unit = { delay(it) },
) {
    private val _regionOverride = MutableStateFlow<BillingRegion?>(null)

    /** Manual India / International choice; null until loaded or when the user has not overridden IP. */
    val regionOverride: StateFlow<BillingRegion?> = _regionOverride.asStateFlow()
    /** Creates the link for [interval] and [region] and remembers its transaction for [userId]. */
    suspend fun startCheckout(
        userId: String,
        phone: String?,
        interval: BillingInterval,
        region: BillingRegion,
    ): CheckoutLink {
        val link = backend.startCheckout(phone, interval, region)
        store.write(userId, link.txnId)
        return link
    }

    suspend fun loadRegionOverride() {
        _regionOverride.value = regionStore.readOverride()
    }

    suspend fun setRegionOverride(region: BillingRegion) {
        _regionOverride.value = region
        regionStore.writeOverride(region)
    }

    /** The transaction still waiting for an answer for [userId], if any. */
    suspend fun pendingTxnId(userId: String): String? = store.read()?.takeIf { it.first == userId }?.second

    /**
     * Asks the server about [txnId] (or the remembered attempt) up to [attempts] times, [intervalMs] apart, until PayU
     * has a final answer. Returns the last answer, or null when there is nothing to check. Throws [BillingException].
     */
    suspend fun confirm(
        userId: String,
        txnId: String? = null,
        attempts: Int = 1,
        intervalMs: Long = POLL_INTERVAL_MS,
    ): PaymentSummary? {
        val pending = pendingTxnId(userId)
        val id = txnId ?: pending ?: return null
        var summary: PaymentSummary? = null
        for (attempt in 1..attempts.coerceAtLeast(1)) {
            summary = try {
                backend.paymentStatus(id)
            } catch (e: BillingException) {
                if (e.error == BillingError.NotFound && id == pending) store.clear()
                throw e
            }
            if (summary.state.settled) break
            if (attempt < attempts) sleep(intervalMs)
        }
        val answer = checkNotNull(summary)
        if (answer.state.settled) {
            if (id == pending) store.clear()
            refreshPlan(userId)
        }
        return answer
    }

    /** Turns off autopay; Pro stays until the paid period ends (CN1). */
    suspend fun cancel(userId: String): CancelResult = backend.cancelSubscription().also { refreshPlan(userId) }

    suspend fun history(): List<PaymentRecord> = backend.payments()

    /** On sign-out. */
    suspend fun clear() {
        store.clear()
        regionStore.clearOverride()
        _regionOverride.value = null
    }

    companion object {
        const val POLL_INTERVAL_MS = 3_000L
    }
}
