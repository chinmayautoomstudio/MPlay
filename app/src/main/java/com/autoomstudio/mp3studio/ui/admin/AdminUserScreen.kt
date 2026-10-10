package com.autoomstudio.mp3studio.ui.admin

import android.text.format.DateUtils
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.RadioButton
import androidx.compose.material3.SelectableDates
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.autoomstudio.mp3studio.R
import com.autoomstudio.mp3studio.data.admin.AdminJob
import com.autoomstudio.mp3studio.data.admin.AdminUserDetail
import com.autoomstudio.mp3studio.ui.library.DetailBackButton
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZoneOffset
import java.time.ZonedDateTime

private enum class Confirm { MakeAdmin, RemoveAdmin, Disable, RemovePro, CancelSubscription }

/**
 * One user's details and the Admin actions on them (PRD AD3, AD5-AD8, AD10), with their PayU payments, refunds and
 * subscription cancellation (payments PRD AD1, AD3, AD4, RF2).
 */
@Composable
internal fun AdminUserScreen(viewModel: AdminViewModel, userId: String, onBack: () -> Unit, modifier: Modifier) {
    val detail by viewModel.detail.collectAsStateWithLifecycle()
    val busy by viewModel.busy.collectAsStateWithLifecycle()

    Column(modifier.verticalScroll(rememberScrollState())) {
        DetailBackButton(onBack = onBack)
        LoadableContent(detail, onRetry = viewModel::reload) { user ->
            if (user.profile.id == userId) {
                UserDetail(user, isMe = userId == viewModel.me, busy = busy, viewModel = viewModel)
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun UserDetail(user: AdminUserDetail, isMe: Boolean, busy: Boolean, viewModel: AdminViewModel) {
    val context = LocalContext.current
    val profile = user.profile
    val label = profile.email ?: profile.name.orEmpty()
    var confirm by rememberSaveable { mutableStateOf<Confirm?>(null) }
    var granting by rememberSaveable { mutableStateOf(false) }
    var refunding by rememberSaveable { mutableStateOf<String?>(null) }

    Column(
        verticalArrangement = Arrangement.spacedBy(4.dp),
        modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
    ) {
        Text(
            (profile.name?.takeIf { it.isNotBlank() } ?: stringResource(R.string.admin_no_name)) +
                if (isMe) " ${stringResource(R.string.admin_you)}" else "",
            style = MaterialTheme.typography.headlineSmall,
        )
        profile.email?.let { Text(it, style = MaterialTheme.typography.bodyLarge) }
        Text(stringResource(R.string.admin_detail_role, roleName(profile.role)))
        Text(stringResource(R.string.admin_detail_plan, planName(user.entitlements?.plan ?: "free")))
        Text(stringResource(R.string.admin_detail_joined, formatDate(context, profile.createdAt)))
        val trial = user.entitlements?.trial
        Text(
            if (trial != null) {
                stringResource(R.string.admin_detail_trial, formatDate(context, trial.startedAt), formatDate(context, trial.endsAt))
            } else {
                stringResource(R.string.admin_detail_no_trial)
            },
        )
        user.subscriptions.forEach { sub ->
            Text(
                stringResource(
                    R.string.admin_detail_subscription,
                    providerName(sub.provider),
                    sub.status,
                    formatDate(context, sub.expiresAt),
                ),
            )
            sub.paymentStatus?.let { Text(stringResource(R.string.admin_detail_payment, it)) }
            if (sub.provider == "payu") {
                sub.autopayStatus?.let { Text(stringResource(R.string.admin_detail_autopay, it)) }
                sub.graceEnd?.let { Text(stringResource(R.string.admin_detail_grace, formatDate(context, it))) }
                sub.mandateEnd?.let { Text(stringResource(R.string.admin_detail_mandate_end, formatDate(context, it))) }
                if (sub.cancelPending) {
                    Text(stringResource(R.string.admin_detail_cancel_pending), color = MaterialTheme.colorScheme.error)
                }
            }
        }
        Text(stringResource(R.string.admin_detail_usage, user.usage.used, user.usage.reserved))
        if (profile.disabled) {
            Text(stringResource(R.string.admin_detail_disabled), color = MaterialTheme.colorScheme.error)
        }
    }

    SectionTitle(stringResource(R.string.admin_section_actions))
    FlowRow(
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
        modifier = Modifier.padding(horizontal = 16.dp),
    ) {
        if (profile.role == "admin") {
            OutlinedButton(onClick = { confirm = Confirm.RemoveAdmin }, enabled = !busy) {
                Text(stringResource(R.string.admin_remove_admin))
            }
        } else {
            OutlinedButton(onClick = { confirm = Confirm.MakeAdmin }, enabled = !busy) {
                Text(stringResource(R.string.admin_make_admin))
            }
        }
        if (profile.disabled) {
            OutlinedButton(onClick = { viewModel.setDisabled(profile.id, false) }, enabled = !busy) {
                Text(stringResource(R.string.admin_enable))
            }
        } else if (!isMe) {
            OutlinedButton(onClick = { confirm = Confirm.Disable }, enabled = !busy) {
                Text(stringResource(R.string.admin_disable))
            }
        }
        val grant = user.adminGrant
        OutlinedButton(onClick = { granting = true }, enabled = !busy) {
            Text(stringResource(if (grant != null) R.string.admin_change_pro else R.string.admin_grant_pro))
        }
        if (grant != null) {
            OutlinedButton(onClick = { confirm = Confirm.RemovePro }, enabled = !busy) {
                Text(stringResource(R.string.admin_remove_pro))
            }
        }
        if (user.payuSubscription != null) {
            OutlinedButton(onClick = { confirm = Confirm.CancelSubscription }, enabled = !busy) {
                Text(stringResource(R.string.admin_cancel_subscription))
            }
        }
    }
    user.adminGrant?.let {
        Text(
            stringResource(R.string.admin_granted_until, formatDate(context, it.expiresAt)),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
        )
    }

    SectionTitle(stringResource(R.string.admin_section_weeks))
    user.weeks.forEach { week ->
        Text(
            formatWeek(context, week.weekStart) + ": " +
                stringResource(R.string.admin_week_row, week.completed, week.released, week.denied),
            style = MaterialTheme.typography.bodyMedium,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 2.dp),
        )
    }

    SectionTitle(stringResource(R.string.admin_section_jobs))
    if (user.jobs.isEmpty()) {
        Text(stringResource(R.string.admin_no_jobs), modifier = Modifier.padding(horizontal = 16.dp))
    }
    user.jobs.forEach { JobRow(it) }

    if (user.payments.isNotEmpty()) {
        SectionTitle(stringResource(R.string.admin_section_payment_list))
        user.payments.forEach { payment ->
            AdminPaymentItem(
                payment = payment,
                busy = busy,
                showEmail = false,
                onReverify = { viewModel.reverifyPayment(payment.txnId) },
                onRefund = { refunding = payment.txnId },
                onOpenUser = null,
            )
        }
    }

    SectionTitle(stringResource(R.string.admin_section_payments))
    if (user.events.isEmpty()) {
        Text(stringResource(R.string.admin_no_payments), modifier = Modifier.padding(horizontal = 16.dp))
    }
    user.events.forEach { event ->
        Text(
            "${formatDateTime(context, event.createdAt)} · ${providerName(event.provider)} · ${event.type}" +
                (event.txnId?.let { " · $it" } ?: "") +
                (event.amountPaise?.let { " · %.2f %s".format(it / 100.0, event.currency.orEmpty()) } ?: ""),
            style = MaterialTheme.typography.bodyMedium,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 2.dp),
        )
    }
    Text("", modifier = Modifier.padding(bottom = 24.dp))

    confirm?.let { which ->
        val (title, message) = when (which) {
            Confirm.MakeAdmin -> R.string.admin_make_admin_title to R.string.admin_make_admin_message
            Confirm.RemoveAdmin -> R.string.admin_remove_admin_title to R.string.admin_remove_admin_message
            Confirm.Disable -> R.string.admin_disable_title to R.string.admin_disable_message
            Confirm.RemovePro -> R.string.admin_remove_pro_title to R.string.admin_remove_pro_message
            Confirm.CancelSubscription ->
                R.string.admin_cancel_subscription_title to R.string.admin_cancel_subscription_message
        }
        AlertDialog(
            onDismissRequest = { confirm = null },
            title = { Text(stringResource(title)) },
            text = { Text(stringResource(message, label)) },
            confirmButton = {
                TextButton(onClick = {
                    confirm = null
                    when (which) {
                        Confirm.MakeAdmin -> viewModel.setRole(profile.id, label, admin = true)
                        Confirm.RemoveAdmin -> viewModel.setRole(profile.id, label, admin = false)
                        Confirm.Disable -> viewModel.setDisabled(profile.id, true)
                        Confirm.RemovePro -> viewModel.revokePro(profile.id)
                        Confirm.CancelSubscription -> viewModel.cancelSubscription(profile.id)
                    }
                }) { Text(stringResource(android.R.string.ok)) }
            },
            dismissButton = {
                TextButton(onClick = { confirm = null }) { Text(stringResource(android.R.string.cancel)) }
            },
        )
    }
    user.payments.firstOrNull { it.txnId == refunding }?.let { payment ->
        RefundDialog(
            payment = payment,
            onConfirm = {
                refunding = null
                viewModel.refundPayment(payment.txnId)
            },
            onDismiss = { refunding = null },
        )
    }
    if (granting) {
        GrantProDialog(
            onGrant = { until ->
                granting = false
                viewModel.grantPro(profile.id, until)
            },
            onDismiss = { granting = false },
        )
    }
}

@Composable
private fun JobRow(job: AdminJob) {
    val context = LocalContext.current
    val status = stringResource(
        when (job.status) {
            "completed" -> R.string.admin_job_completed
            "released" -> R.string.admin_job_released
            "reserved" -> R.string.admin_job_reserved
            else -> R.string.admin_job_denied
        },
    )
    Column(Modifier.padding(horizontal = 16.dp, vertical = 4.dp)) {
        Text(job.songRef ?: job.jobRef, style = MaterialTheme.typography.bodyMedium)
        Text(
            "$status · ${formatDateTime(context, job.completedAt ?: job.reservedAt)}",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
internal fun providerName(provider: String): String = when (provider) {
    "admin" -> stringResource(R.string.admin_provider_admin)
    "payu" -> stringResource(R.string.admin_provider_payu)
    else -> provider
}

private enum class GrantLength { OneMonth, ThreeMonths, OneYear, Pick }

/** 1 month, 3 months, 1 year or a picked date (end of that day on this phone), at most 5 years ahead. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun GrantProDialog(onGrant: (Long) -> Unit, onDismiss: () -> Unit) {
    val context = LocalContext.current
    var length by rememberSaveable { mutableStateOf(GrantLength.OneMonth) }
    var picked by rememberSaveable { mutableStateOf<Long?>(null) }
    var picking by rememberSaveable { mutableStateOf(false) }
    val now = ZonedDateTime.now()
    val until = when (length) {
        GrantLength.OneMonth -> now.plusMonths(1).toInstant().toEpochMilli()
        GrantLength.ThreeMonths -> now.plusMonths(3).toInstant().toEpochMilli()
        GrantLength.OneYear -> now.plusYears(1).toInstant().toEpochMilli()
        GrantLength.Pick -> picked
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.admin_grant_pro)) },
        text = {
            Column(Modifier.selectableGroup()) {
                GrantLength.entries.forEach { option ->
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier
                            .fillMaxWidth()
                            .selectable(
                                selected = length == option,
                                role = Role.RadioButton,
                                onClick = {
                                    length = option
                                    if (option == GrantLength.Pick) picking = true
                                },
                            )
                            .padding(vertical = 4.dp),
                    ) {
                        RadioButton(selected = length == option, onClick = null)
                        Text(
                            stringResource(
                                when (option) {
                                    GrantLength.OneMonth -> R.string.admin_grant_1m
                                    GrantLength.ThreeMonths -> R.string.admin_grant_3m
                                    GrantLength.OneYear -> R.string.admin_grant_1y
                                    GrantLength.Pick -> R.string.admin_grant_pick
                                },
                            ),
                            modifier = Modifier.padding(start = 12.dp),
                        )
                    }
                }
                until?.let {
                    Text(
                        stringResource(
                            R.string.admin_grant_until,
                            DateUtils.formatDateTime(context, it, DateUtils.FORMAT_SHOW_DATE or DateUtils.FORMAT_SHOW_YEAR),
                        ),
                        style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier.padding(top = 8.dp),
                    )
                }
            }
        },
        confirmButton = {
            TextButton(onClick = { until?.let(onGrant) }, enabled = until != null) {
                Text(stringResource(R.string.admin_grant_confirm))
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(android.R.string.cancel)) } },
    )

    if (picking) {
        val today = LocalDate.now()
        val state = rememberDatePickerState(
            selectableDates = object : SelectableDates {
                override fun isSelectableDate(utcTimeMillis: Long): Boolean {
                    val date = Instant.ofEpochMilli(utcTimeMillis).atZone(ZoneOffset.UTC).toLocalDate()
                    return date.isAfter(today) && !date.isAfter(today.plusYears(5).minusDays(1))
                }
            },
        )
        DatePickerDialog(
            onDismissRequest = { picking = false },
            confirmButton = {
                TextButton(
                    onClick = {
                        picked = state.selectedDateMillis?.let { millis ->
                            Instant.ofEpochMilli(millis).atZone(ZoneOffset.UTC).toLocalDate()
                                .atTime(23, 59, 59).atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()
                        }
                        picking = false
                    },
                    enabled = state.selectedDateMillis != null,
                ) { Text(stringResource(android.R.string.ok)) }
            },
            dismissButton = {
                TextButton(onClick = { picking = false }) { Text(stringResource(android.R.string.cancel)) }
            },
        ) { DatePicker(state = state) }
    }
}
