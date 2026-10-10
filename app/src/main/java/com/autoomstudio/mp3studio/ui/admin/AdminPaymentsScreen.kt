package com.autoomstudio.mp3studio.ui.admin

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.autoomstudio.mp3studio.R
import com.autoomstudio.mp3studio.data.admin.AdminBillingHealth
import com.autoomstudio.mp3studio.data.admin.AdminPaymentRow
import com.autoomstudio.mp3studio.data.admin.PaymentFilter
import com.autoomstudio.mp3studio.data.billing.PaymentState
import com.autoomstudio.mp3studio.ui.library.DetailBackButton
import com.autoomstudio.mp3studio.ui.plans.paymentKindText
import com.autoomstudio.mp3studio.ui.plans.paymentStateText
import com.autoomstudio.mp3studio.ui.plans.rupees

/** Admin > Payments (payments PRD AD2-AD4, RF2): billing health, then every payment with search and filters. */
@Composable
internal fun AdminPaymentsScreen(viewModel: AdminViewModel, onBack: () -> Unit, modifier: Modifier) {
    val state by viewModel.payments.collectAsStateWithLifecycle()
    val health by viewModel.health.collectAsStateWithLifecycle()
    val busy by viewModel.busy.collectAsStateWithLifecycle()
    var refunding by remember { mutableStateOf<AdminPaymentRow?>(null) }

    LazyColumn(modifier, contentPadding = PaddingValues(bottom = 24.dp)) {
        item { DetailBackButton(onBack = onBack) }
        item {
            Text(
                stringResource(R.string.admin_payments_title),
                style = MaterialTheme.typography.headlineSmall,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
            )
        }
        item {
            Column(Modifier.padding(horizontal = 16.dp, vertical = 4.dp)) {
                LoadableContent(health, onRetry = viewModel::reload) { HealthCard(it) }
            }
        }
        item {
            OutlinedTextField(
                value = state.query,
                onValueChange = viewModel::setPaymentQuery,
                placeholder = { Text(stringResource(R.string.admin_payments_search)) },
                singleLine = true,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp),
            )
        }
        item {
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier
                    .horizontalScroll(rememberScrollState())
                    .padding(horizontal = 16.dp),
            ) {
                PaymentFilter.entries.forEach { filter ->
                    FilterChip(
                        selected = state.filter == filter,
                        onClick = { viewModel.setPaymentFilter(filter) },
                        label = { Text(filterName(filter)) },
                        shape = CircleShape,
                    )
                }
            }
        }
        item {
            Text(
                stringResource(R.string.admin_payments_total, state.total),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
            )
        }
        items(state.payments, key = { it.txnId }) { payment ->
            AdminPaymentItem(
                payment = payment,
                busy = busy,
                showEmail = true,
                onReverify = { viewModel.reverifyPayment(payment.txnId) },
                onRefund = { refunding = payment },
                onOpenUser = payment.userId?.let { id -> { viewModel.open(AdminPage.User(id)) } },
            )
            HorizontalDivider(Modifier.padding(horizontal = 16.dp))
        }
        item {
            when {
                state.error != null -> ErrorBlock(state.error!!, onRetry = viewModel::reload)
                state.loading -> Box(Modifier.fillMaxWidth().padding(16.dp), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator()
                }
                state.payments.isEmpty() -> Text(
                    stringResource(R.string.admin_no_payment_rows),
                    modifier = Modifier.padding(16.dp),
                )
                state.hasMore -> OutlinedButton(
                    onClick = viewModel::loadMorePayments,
                    modifier = Modifier.padding(16.dp),
                ) { Text(stringResource(R.string.admin_load_more)) }
            }
        }
    }

    refunding?.let { payment ->
        RefundDialog(
            payment = payment,
            onConfirm = {
                refunding = null
                viewModel.refundPayment(payment.txnId)
            },
            onDismiss = { refunding = null },
        )
    }
}

@Composable
private fun HealthCard(health: AdminBillingHealth) {
    val context = LocalContext.current
    AdminCard(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(stringResource(R.string.admin_health_title), style = MaterialTheme.typography.titleMedium)
            if (health.healthy) {
                Text(stringResource(R.string.admin_health_ok), style = MaterialTheme.typography.bodyMedium)
            }
            health.jobs.forEach { job ->
                val text = when {
                    job.lastRunAt == null -> stringResource(R.string.admin_health_job_never, job.job)
                    job.overdue -> stringResource(R.string.admin_health_job_overdue, job.job, formatDateTime(context, job.lastRunAt))
                    else -> stringResource(R.string.admin_health_job, job.job, formatDateTime(context, job.lastRunAt))
                }
                Text(
                    text,
                    style = MaterialTheme.typography.bodySmall,
                    color = if (job.overdue) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant,
                )
                job.error?.let {
                    Text(
                        stringResource(R.string.admin_health_job_error, it),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error,
                    )
                }
            }
            Count(stringResource(R.string.admin_health_flagged, health.flaggedWebhooks), health.flaggedWebhooks)
            Count(stringResource(R.string.admin_health_failed, health.failedWebhooks), health.failedWebhooks)
            Count(stringResource(R.string.admin_health_cancels, health.pendingMandateCancels), health.pendingMandateCancels)
            Count(stringResource(R.string.admin_health_review, health.needsReview), health.needsReview)
        }
    }
}

@Composable
private fun Count(text: String, value: Int) {
    Text(
        text,
        style = MaterialTheme.typography.bodySmall,
        color = if (value > 0) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant,
    )
}

/** One payment with its menu: re-check with PayU, refund, and (from the list) open the user. */
@Composable
internal fun AdminPaymentItem(
    payment: AdminPaymentRow,
    busy: Boolean,
    showEmail: Boolean,
    onReverify: () -> Unit,
    onRefund: () -> Unit,
    onOpenUser: (() -> Unit)?,
) {
    val context = LocalContext.current
    var menu by remember { mutableStateOf(false) }
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = 16.dp, top = 8.dp, bottom = 8.dp),
    ) {
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            if (showEmail) {
                Text(
                    payment.email ?: stringResource(R.string.admin_payment_deleted_user),
                    style = MaterialTheme.typography.bodyLarge,
                )
            }
            Text(
                "${rupees(payment.amountPaise)} · ${stringResource(paymentStateText(PaymentState.of(payment.status)))} · " +
                    stringResource(paymentKindText(payment.kind)),
                fontWeight = FontWeight.SemiBold,
                style = MaterialTheme.typography.bodyMedium,
            )
            Text(
                listOfNotNull(
                    formatDateTime(context, payment.completedAt ?: payment.createdAt),
                    payment.txnId,
                    payment.payuRef,
                    payment.method,
                ).joinToString(" · "),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            if (payment.refundedPaise > 0) {
                Text(
                    stringResource(R.string.receipt_refunded) + " " + rupees(payment.refundedPaise),
                    style = MaterialTheme.typography.bodySmall,
                )
            }
            if (payment.refundPending) {
                Text(
                    stringResource(R.string.admin_payment_refund_pending),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.primary,
                )
            }
            payment.failureReason?.let {
                Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
            }
        }
        Box {
            IconButton(onClick = { menu = true }, enabled = !busy) {
                Icon(Icons.Filled.MoreVert, contentDescription = null)
            }
            DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                DropdownMenuItem(
                    text = { Text(stringResource(R.string.admin_payment_reverify)) },
                    onClick = {
                        menu = false
                        onReverify()
                    },
                )
                if (payment.refundable) {
                    DropdownMenuItem(
                        text = { Text(stringResource(R.string.admin_payment_refund)) },
                        onClick = {
                            menu = false
                            onRefund()
                        },
                    )
                }
                onOpenUser?.let { open ->
                    DropdownMenuItem(
                        text = { Text(stringResource(R.string.admin_payment_open_user)) },
                        onClick = {
                            menu = false
                            open()
                        },
                    )
                }
            }
        }
    }
}

@Composable
internal fun RefundDialog(payment: AdminPaymentRow, onConfirm: () -> Unit, onDismiss: () -> Unit) {
    val amount = rupees(payment.amountPaise - payment.refundedPaise)
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.admin_payment_refund_title, amount)) },
        text = { Text(stringResource(R.string.admin_payment_refund_message, amount, payment.txnId)) },
        confirmButton = { TextButton(onClick = onConfirm) { Text(stringResource(R.string.admin_payment_refund)) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(android.R.string.cancel)) } },
    )
}

@Composable
private fun filterName(filter: PaymentFilter): String = stringResource(
    when (filter) {
        PaymentFilter.All -> R.string.admin_payment_filter_all
        PaymentFilter.Success -> R.string.admin_payment_filter_success
        PaymentFilter.Failed -> R.string.admin_payment_filter_failed
        PaymentFilter.Pending -> R.string.admin_payment_filter_pending
        PaymentFilter.Refunded -> R.string.admin_payment_filter_refunded
        PaymentFilter.Disputed -> R.string.admin_payment_filter_disputed
        PaymentFilter.PastDue -> R.string.admin_payment_filter_past_due
        PaymentFilter.Cancelled -> R.string.admin_payment_filter_cancelled
    },
)
