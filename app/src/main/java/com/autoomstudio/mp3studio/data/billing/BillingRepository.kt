package com.autoomstudio.mp3studio.data.billing

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.autoomstudio.mp3studio.data.plan.BillingInterval
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first

/** The payment attempt the user last opened, so it is still checked after the app was closed (payments PRD CK8). */
interface PendingPaymentStore {
    /** `(userId, txnId)`, or null when nothing is waiting. */
    suspend fun read(): Pair<String, String>?
    suspend fun write(userId: String, txnId: String)
    suspend fun clear()
}

private val Context.billingDataStore: DataStore<Preferences> by preferencesDataStore(name = "billing")

class DataStorePendingPaymentStore(context: Context) : PendingPaymentStore {

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

    private companion object {
        val KEY_USER = stringPreferencesKey("pending_payment_user")
        val KEY_TXN = stringPreferencesKey("pending_payment_txn")
    }
}

/**
 * Starts PayU checkouts and follows them to an answer. Whether Pro is granted is always decided by the server after it
 * checked the payment with PayU; this only asks. [refreshPlan] reloads entitlements once a payment settles (SV9).
 */
class BillingRepository(
    private val backend: BillingBackend,
    private val store: PendingPaymentStore,
    private val refreshPlan: suspend (userId: String) -> Unit,
    private val sleep: suspend (Long) -> Unit = { delay(it) },
) {
    /** Creates the link for [interval] and remembers its transaction for [userId]; throws [BillingException]. */
    suspend fun startCheckout(userId: String, phone: String?, interval: BillingInterval): CheckoutLink {
        val link = backend.startCheckout(phone, interval)
        store.write(userId, link.txnId)
        return link
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
    suspend fun clear() = store.clear()

    companion object {
        const val POLL_INTERVAL_MS = 3_000L
    }
}
