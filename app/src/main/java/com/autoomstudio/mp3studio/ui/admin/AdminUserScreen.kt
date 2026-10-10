package com.autoomstudio.mp3studio.ui.admin

import android.content.ClipData
import android.content.Context
import android.text.format.DateUtils
import androidx.annotation.StringRes
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.relocation.BringIntoViewRequester
import androidx.compose.foundation.relocation.bringIntoViewRequester
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.automirrored.filled.Undo
import androidx.compose.material.icons.automirrored.outlined.HelpOutline
import androidx.compose.material.icons.automirrored.outlined.ReceiptLong
import androidx.compose.material.icons.filled.Cancel
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Error
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.outlined.AudioFile
import androidx.compose.material.icons.outlined.Autorenew
import androidx.compose.material.icons.outlined.BarChart
import androidx.compose.material.icons.outlined.Block
import androidx.compose.material.icons.outlined.CalendarMonth
import androidx.compose.material.icons.outlined.CreditCard
import androidx.compose.material.icons.outlined.Description
import androidx.compose.material.icons.outlined.GraphicEq
import androidx.compose.material.icons.outlined.HighlightOff
import androidx.compose.material.icons.outlined.HourglassTop
import androidx.compose.material.icons.outlined.LockOpen
import androidx.compose.material.icons.outlined.MusicNote
import androidx.compose.material.icons.outlined.Person
import androidx.compose.material.icons.outlined.PersonAdd
import androidx.compose.material.icons.outlined.PersonRemove
import androidx.compose.material.icons.outlined.RemoveCircleOutline
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.RadioButton
import androidx.compose.material3.SelectableDates
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.VerticalDivider
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.rememberVectorPainter
import androidx.compose.ui.platform.ClipEntry
import androidx.compose.ui.platform.LocalClipboard
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.coerceAtLeast
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.autoomstudio.mp3studio.R
import com.autoomstudio.mp3studio.data.admin.AdminJob
import com.autoomstudio.mp3studio.data.admin.AdminPaymentEvent
import com.autoomstudio.mp3studio.data.admin.AdminSubscription
import com.autoomstudio.mp3studio.data.admin.AdminUserDetail
import com.autoomstudio.mp3studio.data.admin.AdminWeek
import com.autoomstudio.mp3studio.data.billing.PaymentState
import com.autoomstudio.mp3studio.data.plan.EntitlementsResponse
import com.autoomstudio.mp3studio.data.plan.epochMillis
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZoneOffset
import java.time.ZonedDateTime

private enum class Confirm { MakeAdmin, RemoveAdmin, Disable, RemovePro, CancelSubscription, RevokeSubscription }

private const val RECENT_PAYMENTS = 3
private const val RECENT_EVENTS = 5

/**
 * One user's profile and the Admin actions on them (PRD AD3, AD5-AD8, AD10), with their PayU payments, refunds and
 * subscription cancellation (payments PRD AD1, AD3, AD4, RF2).
 */
@Composable
internal fun AdminUserScreen(viewModel: AdminViewModel, userId: String, onBack: () -> Unit, modifier: Modifier) {
    val detail by viewModel.detail.collectAsStateWithLifecycle()
    val busy by viewModel.busy.collectAsStateWithLifecycle()

    Column(modifier.verticalScroll(rememberScrollState())) {
        TopBar(onBack)
        LoadableContent(detail, onRetry = viewModel::reload) { user ->
            if (user.profile.id == userId) {
                UserDetail(user, isMe = userId == viewModel.me, busy = busy, viewModel = viewModel)
            }
        }
    }
}

@Composable
private fun TopBar(onBack: () -> Unit) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = 4.dp, end = 16.dp, top = 4.dp, bottom = 4.dp),
    ) {
        IconButton(onClick = onBack) {
            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.action_back))
        }
        Text(
            stringResource(R.string.admin_user_profile_title),
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(start = 4.dp),
        )
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun UserDetail(user: AdminUserDetail, isMe: Boolean, busy: Boolean, viewModel: AdminViewModel) {
    val profile = user.profile
    val label = profile.email ?: profile.name.orEmpty()
    var confirm by rememberSaveable { mutableStateOf<Confirm?>(null) }
    var granting by rememberSaveable { mutableStateOf(false) }
    var refunding by rememberSaveable { mutableStateOf<String?>(null) }
    val paymentsRequester = remember { BringIntoViewRequester() }
    val scope = rememberCoroutineScope()

    Column(
        verticalArrangement = Arrangement.spacedBy(12.dp),
        modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 4.dp, bottom = 24.dp),
    ) {
        ProfileHeader(user, isMe)
        PlanCard(
            user,
            onOpenPayments = if (user.payments.isNotEmpty()) {
                { scope.launch { paymentsRequester.bringIntoView() } }
            } else {
                null
            },
        )
        AccountDetails(user)
        AdminActions(user, isMe, busy, onConfirm = { confirm = it }, onGrant = { granting = true }) {
            viewModel.setDisabled(profile.id, false)
        }
        UsageCard(user)
        WeeksCard(user.weeks, onOpenUsage = { viewModel.open(AdminPage.Usage) })
        if (user.payments.isNotEmpty()) {
            RecentPayments(
                user,
                busy = busy,
                viewModel = viewModel,
                onRefund = { refunding = it },
                modifier = Modifier.bringIntoViewRequester(paymentsRequester),
            )
        }
        PaymentEvents(user.events)
    }

    confirm?.let { which ->
        val (title, message) = when (which) {
            Confirm.MakeAdmin -> R.string.admin_make_admin_title to R.string.admin_make_admin_message
            Confirm.RemoveAdmin -> R.string.admin_remove_admin_title to R.string.admin_remove_admin_message
            Confirm.Disable -> R.string.admin_disable_title to R.string.admin_disable_message
            Confirm.RemovePro -> R.string.admin_remove_pro_title to R.string.admin_remove_pro_message
            Confirm.CancelSubscription ->
                R.string.admin_cancel_subscription_title to R.string.admin_cancel_subscription_message
            Confirm.RevokeSubscription ->
                R.string.admin_revoke_subscription_title to R.string.admin_revoke_subscription_message
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
                        Confirm.RevokeSubscription -> viewModel.revokeSubscription(profile.id)
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
private fun ProfileHeader(user: AdminUserDetail, isMe: Boolean) {
    val profile = user.profile
    val name = profile.name?.takeIf { it.isNotBlank() } ?: stringResource(R.string.admin_no_name)
    AdminCard(Modifier.fillMaxWidth()) {
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(16.dp)) {
            Box(
                Modifier
                    .size(72.dp)
                    .clip(CircleShape)
                    .background(Brush.linearGradient(listOf(Color(0xFFB794F6), Color(0xFF6D28D9)))),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    initials(profile.name?.takeIf { it.isNotBlank() } ?: profile.email.orEmpty()),
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold,
                    color = Color.White,
                )
            }
            Column(
                verticalArrangement = Arrangement.spacedBy(4.dp),
                modifier = Modifier
                    .weight(1f)
                    .padding(start = 16.dp),
            ) {
                Text(
                    if (isMe) "$name ${stringResource(R.string.admin_you)}" else name,
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                profile.email?.let {
                    Text(
                        it,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(top = 4.dp)) {
                    ProfileChip(
                        rememberVectorPainter(Icons.Outlined.Person),
                        roleName(profile.role),
                        MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    ProfileChip(
                        painterResource(R.drawable.ic_crown),
                        planName(user.entitlements?.plan ?: "free"),
                        MaterialTheme.colorScheme.primary,
                    )
                    if (profile.disabled) {
                        ProfileChip(
                            rememberVectorPainter(Icons.Outlined.Block),
                            stringResource(R.string.admin_badge_disabled),
                            MaterialTheme.colorScheme.error,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun ProfileChip(icon: Painter, text: String, tint: Color) {
    val shape = RoundedCornerShape(10.dp)
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .clip(shape)
            .background(tint.copy(alpha = 0.12f))
            .border(1.dp, tint.copy(alpha = 0.4f), shape)
            .padding(horizontal = 10.dp, vertical = 4.dp),
    ) {
        Icon(icon, contentDescription = null, tint = tint, modifier = Modifier.size(16.dp))
        Text(
            text,
            style = MaterialTheme.typography.labelMedium,
            color = tint,
            modifier = Modifier.padding(start = 6.dp),
        )
    }
}

@Composable
private fun PlanCard(user: AdminUserDetail, onOpenPayments: (() -> Unit)?) {
    val context = LocalContext.current
    val plan = user.entitlements?.plan ?: "free"
    val trial = user.entitlements?.trial
    val sub = user.subscriptions.firstOrNull { it.status == "active" || it.status == "past_due" }
        ?: user.revocableSubscription(System.currentTimeMillis())
    val pill: Pair<String, Color>? = when {
        sub != null -> when (sub.status) {
            "active" -> stringResource(R.string.admin_status_active) to AdminColors.Green
            "past_due" -> stringResource(R.string.admin_status_past_due) to AdminColors.Amber
            "cancelled" -> stringResource(R.string.admin_status_cancelled) to AdminColors.Red
            else -> humanize(sub.status) to MaterialTheme.colorScheme.onSurfaceVariant
        }
        plan == "trial" -> stringResource(R.string.admin_status_active) to AdminColors.Green
        else -> null
    }
    val source = stringResource(
        when {
            sub?.provider == "payu" -> R.string.admin_plan_payu
            sub?.provider == "admin" -> R.string.admin_provider_admin
            plan == "trial" -> R.string.admin_plan_trial_source
            else -> R.string.admin_plan_none
        },
    )
    val renews = sub?.takeIf { it.provider == "payu" && it.status == "active" && !it.cancelAtPeriodEnd }
        ?.let { it.nextBillingAt ?: it.expiresAt }
    val ends = sub?.expiresAt ?: trial?.endsAt?.takeIf { plan == "trial" }

    AdminCard(Modifier.fillMaxWidth(), onClick = onOpenPayments) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconTile(painterResource(R.drawable.ic_crown), AdminColors.Violet, size = 52.dp)
                Column(
                    verticalArrangement = Arrangement.spacedBy(2.dp),
                    modifier = Modifier
                        .weight(1f)
                        .padding(start = 14.dp),
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            stringResource(R.string.admin_plan_title, planName(plan)),
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                        )
                        pill?.let { (text, color) ->
                            Spacer(Modifier.width(8.dp))
                            StatusPill(text, color)
                        }
                    }
                    Text(source, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    when {
                        renews != null -> BoldDateLine(R.string.admin_renews_on, shortDate(context, renews))
                        ends != null -> BoldDateLine(R.string.admin_ends_on, shortDate(context, ends))
                    }
                }
                if (onOpenPayments != null) {
                    Icon(
                        Icons.AutoMirrored.Filled.KeyboardArrowRight,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            if (sub != null) {
                val shape = RoundedCornerShape(12.dp)
                Row(
                    Modifier
                        .fillMaxWidth()
                        .height(IntrinsicSize.Min)
                        .clip(shape)
                        .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.05f))
                        .border(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.6f), shape),
                ) {
                    PlanFact(
                        Icons.Outlined.CalendarMonth,
                        stringResource(R.string.admin_payment_status_label),
                        sub.paymentStatus?.let(::humanize) ?: "—",
                        paymentStatusColor(sub.paymentStatus),
                        Modifier.weight(1f),
                    )
                    VerticalDivider(
                        Modifier
                            .fillMaxHeight()
                            .padding(vertical = 10.dp),
                        color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.6f),
                    )
                    PlanFact(
                        Icons.Outlined.Autorenew,
                        stringResource(R.string.admin_expires_on),
                        sub.expiresAt?.let { shortDate(context, it) } ?: "—",
                        MaterialTheme.colorScheme.onSurface,
                        Modifier.weight(1f),
                    )
                }
                if (sub.cancelPending) {
                    Text(
                        stringResource(R.string.admin_detail_cancel_pending),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error,
                    )
                }
            }
        }
    }
}

@Composable
private fun BoldDateLine(@StringRes template: Int, date: String) {
    val text = stringResource(template, date)
    val start = text.indexOf(date)
    Text(
        buildAnnotatedString {
            append(text)
            if (start >= 0) addStyle(SpanStyle(fontWeight = FontWeight.Bold), start, start + date.length)
        },
        style = MaterialTheme.typography.bodySmall,
    )
}

@Composable
private fun PlanFact(icon: ImageVector, label: String, value: String, valueColor: Color, modifier: Modifier) {
    Row(verticalAlignment = Alignment.CenterVertically, modifier = modifier.padding(14.dp)) {
        Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(22.dp))
        Column(Modifier.padding(start = 12.dp)) {
            Text(label, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(value, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold, color = valueColor)
        }
    }
}

@Composable
private fun paymentStatusColor(status: String?): Color = when (status?.lowercase()) {
    "paid", "success", "captured" -> AdminColors.Green
    "failed", "declined" -> AdminColors.Red
    "pending" -> AdminColors.Amber
    else -> MaterialTheme.colorScheme.onSurface
}

@Composable
private fun AccountDetails(user: AdminUserDetail) {
    val context = LocalContext.current
    val profile = user.profile
    val trial: EntitlementsResponse.TrialDto? = user.entitlements?.trial
    AdminCard(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp)) {
            CardHeader(rememberVectorPainter(Icons.Outlined.Description), stringResource(R.string.admin_account_details))
            Spacer(Modifier.height(10.dp))
            DetailRow(stringResource(R.string.admin_label_joined), formatDate(context, profile.createdAt))
            DetailRow(
                stringResource(R.string.admin_label_trial),
                if (trial != null) {
                    stringResource(R.string.admin_label_range, formatDate(context, trial.startedAt), formatDate(context, trial.endsAt))
                } else {
                    stringResource(R.string.admin_detail_no_trial)
                },
            )
            user.subscriptions.forEach { SubscriptionRows(it, context) }
            user.adminGrant?.let {
                DetailRow(stringResource(R.string.admin_label_granted), formatDate(context, it.expiresAt))
            }
            DetailRow(
                stringResource(R.string.admin_label_usage),
                stringResource(R.string.admin_usage_value, user.usage.used, user.usage.reserved),
            )
        }
    }
}

@Composable
private fun SubscriptionRows(sub: AdminSubscription, context: Context) {
    DetailRow(
        stringResource(R.string.admin_label_subscription, providerShortName(sub.provider), sub.status.replace('_', ' ')),
        formatDate(context, sub.expiresAt),
    )
    sub.paymentStatus?.let {
        DetailRow(stringResource(R.string.admin_payment_status_label), humanize(it))
    }
    if (sub.provider == "payu") {
        sub.autopayStatus?.let { DetailRow(stringResource(R.string.admin_label_autopay), humanize(it)) }
        sub.graceEnd?.let { DetailRow(stringResource(R.string.admin_label_grace), formatDate(context, it)) }
        sub.mandateEnd?.let { DetailRow(stringResource(R.string.admin_label_mandate_end), formatDate(context, it)) }
    }
}

@Composable
private fun providerShortName(provider: String): String = when (provider) {
    "admin" -> stringResource(R.string.admin_provider_admin_short)
    else -> providerName(provider)
}

private class ProfileAction(val label: String, val icon: Painter, val danger: Boolean, val onClick: () -> Unit)

@Composable
private fun AdminActions(
    user: AdminUserDetail,
    isMe: Boolean,
    busy: Boolean,
    onConfirm: (Confirm) -> Unit,
    onGrant: () -> Unit,
    onEnable: () -> Unit,
) {
    val context = LocalContext.current
    val profile = user.profile
    val grant = user.adminGrant
    val crown = painterResource(R.drawable.ic_crown)
    val personAdd = rememberVectorPainter(Icons.Outlined.PersonAdd)
    val personRemove = rememberVectorPainter(Icons.Outlined.PersonRemove)
    val block = rememberVectorPainter(Icons.Outlined.Block)
    val unlock = rememberVectorPainter(Icons.Outlined.LockOpen)
    val cancel = rememberVectorPainter(Icons.Outlined.HighlightOff)
    val remove = rememberVectorPainter(Icons.Outlined.RemoveCircleOutline)

    val left = buildList {
        if (profile.role == "admin") {
            add(ProfileAction(stringResource(R.string.admin_remove_admin), personRemove, false) { onConfirm(Confirm.RemoveAdmin) })
        } else {
            add(ProfileAction(stringResource(R.string.admin_make_admin), personAdd, false) { onConfirm(Confirm.MakeAdmin) })
        }
        add(
            ProfileAction(
                stringResource(if (grant != null) R.string.admin_change_pro else R.string.admin_grant_pro),
                crown,
                false,
                onGrant,
            ),
        )
        if (grant != null) {
            add(ProfileAction(stringResource(R.string.admin_remove_pro), remove, false) { onConfirm(Confirm.RemovePro) })
        }
    }
    val right = buildList {
        if (profile.disabled) {
            add(ProfileAction(stringResource(R.string.admin_enable), unlock, false, onEnable))
        } else if (!isMe) {
            add(ProfileAction(stringResource(R.string.admin_disable), block, true) { onConfirm(Confirm.Disable) })
        }
        if (user.payuSubscription != null) {
            add(
                ProfileAction(stringResource(R.string.admin_cancel_subscription), cancel, true) {
                    onConfirm(Confirm.CancelSubscription)
                },
            )
        }
        if (user.revocableSubscription(System.currentTimeMillis()) != null) {
            add(
                ProfileAction(stringResource(R.string.admin_revoke_subscription), remove, true) {
                    onConfirm(Confirm.RevokeSubscription)
                },
            )
        }
    }

    AdminCard(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            CardHeader(rememberVectorPainter(Icons.Outlined.Settings), stringResource(R.string.admin_admin_actions))
            for (i in 0 until maxOf(left.size, right.size)) {
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    left.getOrNull(i)?.let { ActionButton(it, busy, Modifier.weight(1f)) } ?: Spacer(Modifier.weight(1f))
                    right.getOrNull(i)?.let { ActionButton(it, busy, Modifier.weight(1f)) } ?: Spacer(Modifier.weight(1f))
                }
            }
            grant?.let {
                Text(
                    stringResource(R.string.admin_granted_until, formatDate(context, it.expiresAt)),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
private fun ActionButton(action: ProfileAction, busy: Boolean, modifier: Modifier) {
    val tint = if (action.danger) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary
    OutlinedButton(
        onClick = action.onClick,
        enabled = !busy,
        shape = RoundedCornerShape(12.dp),
        border = BorderStroke(1.dp, tint.copy(alpha = if (busy) 0.3f else 0.7f)),
        colors = ButtonDefaults.outlinedButtonColors(containerColor = tint.copy(alpha = 0.08f), contentColor = tint),
        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 10.dp),
        modifier = modifier,
    ) {
        Icon(action.icon, contentDescription = null, modifier = Modifier.size(18.dp))
        Text(
            action.label,
            style = MaterialTheme.typography.labelLarge,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier
                .weight(1f)
                .padding(start = 8.dp),
        )
    }
}

@Composable
private fun UsageCard(user: AdminUserDetail) {
    var allWeeks by rememberSaveable { mutableStateOf(false) }
    var showAll by rememberSaveable { mutableStateOf(false) }
    val separated = if (allWeeks) user.weeks.sumOf { it.completed } else user.usage.used

    AdminCard(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            CardHeader(rememberVectorPainter(Icons.Outlined.GraphicEq), stringResource(R.string.admin_vs_usage)) {
                RangePicker(allWeeks, onChange = { allWeeks = it })
            }
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                UsageStat(Icons.Outlined.AudioFile, separated, stringResource(R.string.admin_job_completed), Modifier.weight(1f))
                UsageStat(
                    Icons.Outlined.HourglassTop,
                    user.usage.reserved,
                    stringResource(R.string.admin_job_reserved),
                    Modifier.weight(1f),
                )
            }
            Text(
                stringResource(R.string.admin_recent_separation),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            if (user.jobs.isEmpty()) {
                Text(stringResource(R.string.admin_no_jobs), style = MaterialTheme.typography.bodyMedium)
            }
            val jobs = if (showAll) user.jobs else user.jobs.take(1)
            jobs.forEachIndexed { index, job ->
                JobRow(job, showStatus = showAll) {
                    if (index == 0) {
                        JobMenu(job, canExpand = user.jobs.size > 1, expanded = showAll, onToggle = { showAll = !showAll })
                    }
                }
            }
        }
    }
}

@Composable
private fun RangePicker(allWeeks: Boolean, onChange: (Boolean) -> Unit) {
    var open by remember { mutableStateOf(false) }
    val shape = RoundedCornerShape(10.dp)
    Box {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .clip(shape)
                .border(1.dp, MaterialTheme.colorScheme.outlineVariant, shape)
                .clickable { open = true }
                .padding(start = 10.dp, end = 6.dp, top = 4.dp, bottom = 4.dp),
        ) {
            Text(
                stringResource(if (allWeeks) R.string.admin_section_weeks else R.string.admin_range_this_week),
                style = MaterialTheme.typography.labelMedium,
            )
            Icon(Icons.Filled.KeyboardArrowDown, contentDescription = null, modifier = Modifier.size(18.dp))
        }
        DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
            DropdownMenuItem(
                text = { Text(stringResource(R.string.admin_range_this_week)) },
                onClick = {
                    open = false
                    onChange(false)
                },
            )
            DropdownMenuItem(
                text = { Text(stringResource(R.string.admin_section_weeks)) },
                onClick = {
                    open = false
                    onChange(true)
                },
            )
        }
    }
}

@Composable
private fun UsageStat(icon: ImageVector, value: Int, label: String, modifier: Modifier) {
    val shape = RoundedCornerShape(14.dp)
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = modifier
            .clip(shape)
            .background(MaterialTheme.colorScheme.surfaceContainerHigh)
            .border(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f), shape)
            .padding(12.dp),
    ) {
        Box(
            Modifier
                .size(40.dp)
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.14f)),
            contentAlignment = Alignment.Center,
        ) {
            Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(22.dp))
        }
        Column(Modifier.padding(start = 12.dp)) {
            Text(value.toString(), style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
            Text(label, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun JobRow(job: AdminJob, showStatus: Boolean, trailing: @Composable () -> Unit) {
    val context = LocalContext.current
    val status = stringResource(
        when (job.status) {
            "completed" -> R.string.admin_job_completed
            "released" -> R.string.admin_job_released
            "reserved" -> R.string.admin_job_reserved
            else -> R.string.admin_job_denied
        },
    )
    val time = formatDateTime(context, job.completedAt ?: job.reservedAt)
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
        IconTile(rememberVectorPainter(Icons.Outlined.MusicNote), AdminColors.Violet, size = 44.dp)
        Column(
            Modifier
                .weight(1f)
                .padding(start = 12.dp),
        ) {
            Text(
                job.songRef ?: job.jobRef,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.SemiBold,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                if (showStatus) "$status · $time" else time,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        trailing()
    }
}

@Composable
private fun JobMenu(job: AdminJob, canExpand: Boolean, expanded: Boolean, onToggle: () -> Unit) {
    val clipboard = LocalClipboard.current
    val scope = rememberCoroutineScope()
    var open by remember { mutableStateOf(false) }
    Box {
        IconButton(onClick = { open = true }) {
            Icon(Icons.Filled.MoreVert, contentDescription = stringResource(R.string.admin_more_actions))
        }
        DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
            if (canExpand) {
                DropdownMenuItem(
                    text = {
                        Text(stringResource(if (expanded) R.string.admin_show_latest_separation else R.string.admin_show_all_separations))
                    },
                    onClick = {
                        open = false
                        onToggle()
                    },
                )
            }
            DropdownMenuItem(
                text = { Text(stringResource(R.string.admin_copy_job_id)) },
                onClick = {
                    open = false
                    scope.launch { clipboard.setClipEntry(ClipEntry(ClipData.newPlainText("job", job.jobRef))) }
                },
            )
        }
    }
}

@Composable
private fun WeeksCard(weeks: List<AdminWeek>, onOpenUsage: () -> Unit) {
    val context = LocalContext.current
    val shown = weeks.sortedByDescending { it.weekStart }.take(8)
    val max = shown.maxOfOrNull { it.completed }?.coerceAtLeast(1) ?: 1
    AdminCard(Modifier.fillMaxWidth(), onClick = onOpenUsage) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            CardHeader(rememberVectorPainter(Icons.Outlined.BarChart), stringResource(R.string.admin_last_8_weeks)) {
                Icon(
                    Icons.AutoMirrored.Filled.KeyboardArrowRight,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Row(Modifier.fillMaxWidth()) {
                shown.forEach { week ->
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        modifier = Modifier.weight(1f),
                    ) {
                        Text(week.completed.toString(), style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold)
                        Box(
                            Modifier
                                .height(44.dp)
                                .padding(top = 4.dp),
                            contentAlignment = Alignment.BottomCenter,
                        ) {
                            Box(
                                Modifier
                                    .fillMaxWidth(0.7f)
                                    .height((40f * week.completed / max).dp.coerceAtLeast(6.dp))
                                    .clip(RoundedCornerShape(topStart = 6.dp, topEnd = 6.dp, bottomStart = 2.dp, bottomEnd = 2.dp))
                                    .background(
                                        if (week.completed > 0) {
                                            AdminColors.Violet
                                        } else {
                                            MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.2f)
                                        },
                                    ),
                            )
                        }
                        val end = LocalDate.parse(week.weekStart).plusDays(6).toString()
                        Text(
                            formatWeek(context, end),
                            style = MaterialTheme.typography.labelSmall,
                            maxLines = 1,
                            modifier = Modifier.padding(top = 4.dp),
                        )
                        Text(
                            formatWeek(context, week.weekStart),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun RecentPayments(
    user: AdminUserDetail,
    busy: Boolean,
    viewModel: AdminViewModel,
    onRefund: (String) -> Unit,
    modifier: Modifier,
) {
    val email = user.profile.email
    AdminCard(modifier.fillMaxWidth()) {
        Column(Modifier.padding(start = 16.dp, end = 8.dp, top = 12.dp)) {
            CardHeader(rememberVectorPainter(Icons.Outlined.CreditCard), stringResource(R.string.admin_recent_payments)) {
                if (email != null) {
                    TextButton(onClick = {
                        viewModel.setPaymentQuery(email)
                        viewModel.open(AdminPage.Payments)
                    }) {
                        Text(stringResource(R.string.admin_see_all))
                        Icon(
                            Icons.AutoMirrored.Filled.KeyboardArrowRight,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp),
                        )
                    }
                }
            }
        }
        user.payments.take(RECENT_PAYMENTS).forEachIndexed { index, payment ->
            if (index > 0) {
                HorizontalDivider(
                    Modifier.padding(horizontal = 16.dp),
                    color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f),
                )
            }
            AdminPaymentItem(
                payment = payment,
                busy = busy,
                showEmail = false,
                onReverify = { viewModel.reverifyPayment(payment.txnId) },
                onRefund = { onRefund(payment.txnId) },
                onOpenUser = null,
                leading = { PaymentStatusIcon(payment.status) },
            )
        }
    }
}

@Composable
private fun PaymentStatusIcon(status: String) {
    val (icon, tint) = when (PaymentState.of(status)) {
        PaymentState.Success -> Icons.Filled.CheckCircle to AdminColors.Green
        PaymentState.Failed, PaymentState.Cancelled -> Icons.Filled.Cancel to AdminColors.Red
        PaymentState.Pending, PaymentState.Created -> Icons.Filled.Schedule to AdminColors.Amber
        PaymentState.Refunded, PaymentState.PartiallyRefunded -> Icons.AutoMirrored.Filled.Undo to AdminColors.Blue
        PaymentState.Disputed -> Icons.Filled.Error to AdminColors.Red
        PaymentState.Unknown -> Icons.AutoMirrored.Outlined.HelpOutline to MaterialTheme.colorScheme.onSurfaceVariant
    }
    Icon(icon, contentDescription = null, tint = tint, modifier = Modifier.size(26.dp))
}

@Composable
private fun PaymentEvents(events: List<AdminPaymentEvent>) {
    val context = LocalContext.current
    var showAll by rememberSaveable { mutableStateOf(false) }
    AdminCard(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            CardHeader(
                rememberVectorPainter(Icons.AutoMirrored.Outlined.ReceiptLong),
                stringResource(R.string.admin_section_payments),
            ) {
                if (events.size > RECENT_EVENTS) {
                    TextButton(onClick = { showAll = !showAll }) {
                        Text(stringResource(if (showAll) R.string.admin_show_less else R.string.admin_show_all))
                    }
                }
            }
            if (events.isEmpty()) {
                Text(stringResource(R.string.admin_no_payments), style = MaterialTheme.typography.bodyMedium)
            }
            (if (showAll) events else events.take(RECENT_EVENTS)).forEach { event ->
                Text(
                    "${formatDateTime(context, event.createdAt)} · ${providerName(event.provider)} · ${event.type}" +
                        (event.txnId?.let { " · $it" } ?: "") +
                        (event.amountPaise?.let { " · %.2f %s".format(it / 100.0, event.currency.orEmpty()) } ?: ""),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

/** "Nov 10, 2026": the short date the plan card uses. */
private fun shortDate(context: Context, iso: String): String = DateUtils.formatDateTime(
    context,
    epochMillis(iso),
    DateUtils.FORMAT_SHOW_DATE or DateUtils.FORMAT_SHOW_YEAR or DateUtils.FORMAT_ABBREV_MONTH,
)

/** "past_due" to "Past due" for server status words that have no string of their own. */
private fun humanize(value: String): String = value.replace('_', ' ').replaceFirstChar { it.uppercase() }

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
