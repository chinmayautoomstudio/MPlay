package com.autoomstudio.mp3studio.ui.plans

import android.content.ClipData
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.ClipEntry
import androidx.compose.ui.platform.LocalClipboard
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider.AndroidViewModelFactory.Companion.APPLICATION_KEY
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.autoomstudio.mp3studio.MPlayApp
import com.autoomstudio.mp3studio.R
import com.autoomstudio.mp3studio.data.billing.BillingException
import com.autoomstudio.mp3studio.data.billing.BillingRepository
import com.autoomstudio.mp3studio.data.billing.PaymentRecord
import com.autoomstudio.mp3studio.ui.library.DetailBackButton
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

sealed interface HistoryUi {
    data object Loading : HistoryUi
    data class Loaded(val payments: List<PaymentRecord>) : HistoryUi
    data object Failed : HistoryUi
}

class PaymentHistoryViewModel(private val repository: BillingRepository) : ViewModel() {
    private val _state = MutableStateFlow<HistoryUi>(HistoryUi.Loading)
    val state: StateFlow<HistoryUi> = _state.asStateFlow()

    fun load() {
        _state.value = HistoryUi.Loading
        viewModelScope.launch {
            _state.value = try {
                HistoryUi.Loaded(repository.history())
            } catch (_: BillingException) {
                HistoryUi.Failed
            }
        }
    }

    companion object {
        val Factory = viewModelFactory {
            initializer { PaymentHistoryViewModel((this[APPLICATION_KEY] as MPlayApp).container.billingRepository) }
        }
    }
}

/** Settings > Plans > Payment history (payments PRD PS4) with an in-app receipt for each payment (PS5). */
@Composable
fun PaymentHistoryScreen(
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    backEnabled: Boolean = true,
    viewModel: PaymentHistoryViewModel = viewModel(factory = PaymentHistoryViewModel.Factory),
) {
    BackHandler(enabled = backEnabled, onBack = onBack)
    val state by viewModel.state.collectAsStateWithLifecycle()
    val context = LocalContext.current
    var receiptTxn by rememberSaveable { mutableStateOf<String?>(null) }
    LaunchedEffect(viewModel) { viewModel.load() }

    Column(modifier.fillMaxSize()) {
        DetailBackButton(onBack = onBack)
        Text(
            stringResource(R.string.history_title),
            style = MaterialTheme.typography.headlineSmall,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
        )
        when (val s = state) {
            HistoryUi.Loading -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
            HistoryUi.Failed -> Column(
                Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Text(stringResource(R.string.plans_refresh_failed))
                OutlinedButton(onClick = viewModel::load) { Text(stringResource(R.string.payment_try_again)) }
            }
            is HistoryUi.Loaded -> if (s.payments.isEmpty()) {
                Text(stringResource(R.string.history_empty), modifier = Modifier.padding(16.dp))
            } else {
                LazyColumn {
                    items(s.payments, key = { it.txnId }) { payment ->
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { receiptTxn = payment.txnId }
                                .padding(horizontal = 16.dp, vertical = 12.dp),
                        ) {
                            Column(Modifier.weight(1f)) {
                                Text(stringResource(paymentKindText(payment.kind)), style = MaterialTheme.typography.bodyLarge)
                                Text(
                                    billingDate(context, payment.completedAt ?: payment.createdAt),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                            Column(horizontalAlignment = Alignment.End) {
                                Text(rupees(payment.amountPaise), fontWeight = FontWeight.SemiBold)
                                Text(
                                    stringResource(paymentStateText(payment.state)),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        }
                        HorizontalDivider()
                    }
                }
            }
        }
    }

    val receipt = (state as? HistoryUi.Loaded)?.payments?.firstOrNull { it.txnId == receiptTxn }
    receipt?.let { ReceiptDialog(it, onDismiss = { receiptTxn = null }) }
}

@Composable
private fun ReceiptDialog(payment: PaymentRecord, onDismiss: () -> Unit) {
    val context = LocalContext.current
    val resources = LocalResources.current
    val clipboard = LocalClipboard.current
    val scope = rememberCoroutineScope()
    var copied by remember { mutableStateOf(false) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.receipt_title)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(stringResource(R.string.receipt_seller), style = MaterialTheme.typography.titleSmall)
                ReceiptRow(R.string.receipt_item, stringResource(paymentKindText(payment.kind)))
                ReceiptRow(R.string.receipt_amount, rupees(payment.amountPaise))
                if (payment.refundedPaise > 0) ReceiptRow(R.string.receipt_refunded, rupees(payment.refundedPaise))
                ReceiptRow(R.string.receipt_status, stringResource(paymentStateText(payment.state)))
                ReceiptRow(R.string.receipt_date, billingDateTime(context, payment.completedAt ?: payment.createdAt))
                if (payment.periodStart != null && payment.periodEnd != null) {
                    ReceiptRow(
                        R.string.receipt_period,
                        billingDate(context, payment.periodStart) + " – " + billingDate(context, payment.periodEnd),
                    )
                }
                payment.method?.let { ReceiptRow(R.string.receipt_method, it) }
                ReceiptRow(R.string.receipt_txn, payment.txnId)
                if (copied) {
                    Text(
                        stringResource(R.string.receipt_copied),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.primary,
                    )
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.payment_done)) } },
        dismissButton = {
            TextButton(onClick = {
                scope.launch {
                    val label = resources.getString(R.string.receipt_txn)
                    clipboard.setClipEntry(ClipEntry(ClipData.newPlainText(label, payment.txnId)))
                    copied = true
                }
            }) { Text(stringResource(R.string.receipt_copy)) }
        },
    )
}

@Composable
private fun ReceiptRow(label: Int, value: String) {
    Row(horizontalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.fillMaxWidth()) {
        Text(
            stringResource(label),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.weight(1f),
        )
        Text(value, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1.4f))
    }
}
