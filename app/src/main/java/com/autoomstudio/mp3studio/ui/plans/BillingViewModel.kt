package com.autoomstudio.mp3studio.ui.plans

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider.AndroidViewModelFactory.Companion.APPLICATION_KEY
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.autoomstudio.mp3studio.MPlayApp
import com.autoomstudio.mp3studio.data.billing.BillingError
import com.autoomstudio.mp3studio.data.billing.BillingException
import com.autoomstudio.mp3studio.data.billing.BillingRepository
import com.autoomstudio.mp3studio.data.billing.CancelResult
import com.autoomstudio.mp3studio.data.billing.PaymentState
import com.autoomstudio.mp3studio.data.billing.PaymentSummary
import com.autoomstudio.mp3studio.data.billing.PhoneNumber
import com.autoomstudio.mp3studio.data.plan.BillingMode
import com.autoomstudio.mp3studio.data.plan.Entitlements
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** Why the checkout sheet is open; only changes the wording. */
enum class CheckoutPurpose { Subscribe, FixPayment, SetUpAutopay, Renew }

/** The checkout sheet (payments PRD CK1, CK13). */
data class CheckoutUi(
    val purpose: CheckoutPurpose,
    val mode: BillingMode,
    val needsPhone: Boolean,
    val phone: String = "",
    val phoneInvalid: Boolean = false,
    val busy: Boolean = false,
    val error: BillingError? = null,
)

/** What the payment result screen shows after the user comes back from PayU (CK5 to CK8). */
sealed interface PaymentResultUi {
    data object Confirming : PaymentResultUi

    data class Success(val expiresAt: Long?) : PaymentResultUi

    data class Failed(val reason: String?) : PaymentResultUi

    /** The link expired before the payment went through. */
    data object Expired : PaymentResultUi

    /** The page was closed without paying; the same link can be reopened while it lasts. */
    data object NotCompleted : PaymentResultUi

    /** The bank hasn't answered (UPI or eNACH can take a while); the server keeps checking until [resolveUntil]. */
    data class Waiting(val resolveUntil: Long?) : PaymentResultUi

    /** The server couldn't be asked; it will settle the payment by itself. */
    data class Unchecked(val error: BillingError) : PaymentResultUi

    /** No browser could open the PayU page. */
    data object NoBrowser : PaymentResultUi
}

/** The cancel-autopay dialog (CN1 to CN4). */
sealed interface CancelUi {
    data class Confirm(val expiresAt: Long?, val busy: Boolean = false, val error: BillingError? = null) : CancelUi

    data class Done(val result: CancelResult) : CancelUi
}

data class BillingUiState(
    val checkout: CheckoutUi? = null,
    val result: PaymentResultUi? = null,
    val cancel: CancelUi? = null,
)

class BillingViewModel(
    private val repository: BillingRepository,
    private val signedInUserId: () -> String?,
    private val entitlements: () -> Entitlements?,
) : ViewModel() {

    private val _state = MutableStateFlow(BillingUiState())
    val state: StateFlow<BillingUiState> = _state.asStateFlow()

    private val _openUrl = Channel<String>(Channel.BUFFERED)

    /** PayU links to open in a Custom Tab. */
    val openUrl: Flow<String> = _openUrl.receiveAsFlow()

    /** Set while the Custom Tab is open, so coming back confirms this payment (CK6). */
    private var awaitingReturn: String? = null
    private var checking = false

    fun openCheckout(purpose: CheckoutPurpose = CheckoutPurpose.Subscribe) {
        val current = entitlements()
        _state.update {
            it.copy(
                checkout = CheckoutUi(
                    purpose = purpose,
                    mode = current?.billingMode ?: BillingMode.Autopay,
                    needsPhone = current?.hasPhone != true,
                ),
                result = null,
            )
        }
    }

    fun onPhoneChange(phone: String) {
        _state.update { s -> s.copy(checkout = s.checkout?.copy(phone = phone.take(MAX_PHONE_INPUT), phoneInvalid = false)) }
    }

    fun dismissCheckout() {
        _state.update { s -> if (s.checkout?.busy == true) s else s.copy(checkout = null) }
    }

    /** "Pay ₹99": asks the server for a PayU link and opens it. */
    fun pay() {
        val sheet = _state.value.checkout ?: return
        val userId = signedInUserId() ?: return
        if (sheet.busy) return
        val phone = if (sheet.needsPhone) {
            PhoneNumber.normalize(sheet.phone) ?: run {
                _state.update { s -> s.copy(checkout = s.checkout?.copy(phoneInvalid = true)) }
                return
            }
        } else {
            null
        }
        _state.update { s -> s.copy(checkout = s.checkout?.copy(busy = true, error = null)) }
        viewModelScope.launch {
            try {
                val link = repository.startCheckout(userId, phone)
                awaitingReturn = link.txnId
                _state.update { it.copy(checkout = null) }
                _openUrl.send(link.url)
            } catch (e: BillingException) {
                val phoneProblem = e.error == BillingError.PhoneRequired || e.error == BillingError.InvalidPhone
                _state.update { s ->
                    s.copy(
                        checkout = s.checkout?.let { c ->
                            c.copy(
                                busy = false,
                                error = e.error.takeUnless { phoneProblem },
                                needsPhone = c.needsPhone || phoneProblem,
                                phoneInvalid = e.error == BillingError.InvalidPhone,
                            )
                        },
                    )
                }
            }
        }
    }

    /** The Custom Tab couldn't open: no browser. */
    fun onOpenFailed() {
        awaitingReturn = null
        _state.update { it.copy(result = PaymentResultUi.NoBrowser) }
    }

    /** The return page opened the app (App Link), with our transaction ID when PayU passed it on. */
    fun onReturned(txnId: String?) {
        val id = txnId ?: awaitingReturn
        awaitingReturn = null
        confirm(id, visible = true)
    }

    /** Every time the app comes to the foreground: finish the payment the user just left, or quietly check an old one. */
    fun onForeground() {
        val returning = awaitingReturn
        if (returning != null) {
            awaitingReturn = null
            confirm(returning, visible = true)
        } else if (_state.value.result == null) {
            confirm(null, visible = false)
        }
    }

    private fun confirm(txnId: String?, visible: Boolean) {
        val userId = signedInUserId() ?: return
        if (checking) return
        checking = true
        if (visible) _state.update { it.copy(result = PaymentResultUi.Confirming) }
        viewModelScope.launch {
            val result = try {
                repository.confirm(userId, txnId, attempts = if (visible) VISIBLE_ATTEMPTS else 1)?.let(::resultOf)
            } catch (e: BillingException) {
                PaymentResultUi.Unchecked(e.error).takeIf { visible && e.error != BillingError.NotFound }
            }
            checking = false
            val shown = when {
                visible -> result
                // A quiet check only speaks up for good news; anything else stays on the Plans screen.
                result is PaymentResultUi.Success -> result
                else -> null
            }
            _state.update { it.copy(result = shown ?: it.result.takeUnless { r -> r == PaymentResultUi.Confirming }) }
        }
    }

    private fun resultOf(summary: PaymentSummary): PaymentResultUi = when (summary.state) {
        PaymentState.Success -> PaymentResultUi.Success(entitlements()?.subscriptionExpiresAt)
        PaymentState.Failed -> PaymentResultUi.Failed(summary.failureReason)
        PaymentState.Cancelled ->
            if (summary.failureReason == LINK_EXPIRED) PaymentResultUi.Expired else PaymentResultUi.Failed(summary.failureReason)
        PaymentState.Pending -> PaymentResultUi.Waiting(summary.resolveUntil)
        PaymentState.Created -> PaymentResultUi.NotCompleted
        else -> PaymentResultUi.Failed(summary.failureReason)
    }

    fun dismissResult() {
        _state.update { it.copy(result = null) }
    }

    /** "Try again" on the result screen. A link that is still live is reopened by the server. */
    fun retry() {
        openCheckout(CheckoutPurpose.Subscribe)
    }

    fun requestCancel() {
        _state.update { it.copy(cancel = CancelUi.Confirm(entitlements()?.billing?.expiresAt)) }
    }

    fun confirmCancel() {
        val dialog = _state.value.cancel as? CancelUi.Confirm ?: return
        val userId = signedInUserId() ?: return
        if (dialog.busy) return
        _state.update { it.copy(cancel = dialog.copy(busy = true, error = null)) }
        viewModelScope.launch {
            val next = try {
                CancelUi.Done(repository.cancel(userId))
            } catch (e: BillingException) {
                dialog.copy(busy = false, error = e.error)
            }
            _state.update { it.copy(cancel = next) }
        }
    }

    fun dismissCancel() {
        _state.update { s -> if ((s.cancel as? CancelUi.Confirm)?.busy == true) s else s.copy(cancel = null) }
    }

    companion object {
        /** About 30 seconds of checking while the user watches; the server keeps going after that. */
        const val VISIBLE_ATTEMPTS = 10
        const val LINK_EXPIRED = "link_expired"
        private const val MAX_PHONE_INPUT = 16

        val Factory = viewModelFactory {
            initializer {
                val container = (this[APPLICATION_KEY] as MPlayApp).container
                BillingViewModel(
                    repository = container.billingRepository,
                    signedInUserId = container::signedInUserId,
                    entitlements = {
                        container.entitlementsRepository.entitlements.value
                            ?.takeIf { it.userId == container.signedInUserId() }
                    },
                )
            }
        }
    }
}
