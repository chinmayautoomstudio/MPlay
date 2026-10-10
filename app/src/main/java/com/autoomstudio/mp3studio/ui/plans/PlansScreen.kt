package com.autoomstudio.mp3studio.ui.plans

import android.text.format.DateUtils
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.ContentCut
import androidx.compose.material.icons.filled.GraphicEq
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.MicExternalOn
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.Person
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.autoomstudio.mp3studio.R
import com.autoomstudio.mp3studio.data.plan.BillingInterval
import com.autoomstudio.mp3studio.data.plan.Plan
import com.autoomstudio.mp3studio.data.plan.TrialClaim
import com.autoomstudio.mp3studio.ui.library.DetailBackButton
import com.autoomstudio.mp3studio.ui.theme.NeonBrush

/**
 * Settings > Plans (PRD PL6): the Free and Pro cards with a Monthly/Yearly toggle, what each plan includes, and the
 * current plan with the PayU billing status and its actions.
 */
@Composable
fun PlansScreen(
    onBack: () -> Unit,
    onMessage: (String) -> Unit,
    onOpenHistory: () -> Unit,
    modifier: Modifier = Modifier,
    backEnabled: Boolean = true,
    viewModel: PlansViewModel = viewModel(factory = PlansViewModel.Factory),
    billingViewModel: BillingViewModel = viewModel(factory = BillingViewModel.Factory),
) {
    BackHandler(enabled = backEnabled, onBack = onBack)
    val state by viewModel.state.collectAsStateWithLifecycle()
    val refreshing by viewModel.refreshing.collectAsStateWithLifecycle()
    val resources = LocalResources.current
    LaunchedEffect(viewModel) { viewModel.refresh() }
    LaunchedEffect(viewModel) {
        viewModel.refreshFailed.collect { onMessage(resources.getString(R.string.plans_refresh_failed)) }
    }
    var yearly by rememberSaveable(state.subscriptionInterval) {
        mutableStateOf(state.subscriptionInterval == BillingInterval.Year)
    }

    Column(modifier.verticalScroll(rememberScrollState())) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            DetailBackButton(onBack = onBack)
            Text(
                stringResource(if (state.plan == Plan.Pro) R.string.plans_current else R.string.plans_title_upgrade),
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.SemiBold,
            )
        }
        Column(
            verticalArrangement = Arrangement.spacedBy(20.dp),
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 8.dp),
        ) {
            SubscriptionSection(
                state = state,
                refreshing = refreshing,
                onRefresh = viewModel::refresh,
                billing = billingViewModel,
                onOpenHistory = onOpenHistory,
            )
            IntervalToggle(yearly = yearly, onChange = { yearly = it })
            PlanCards(
                state = state,
                selected = if (yearly) BillingInterval.Year else BillingInterval.Month,
                billing = billingViewModel,
            )
            ComparePlans()
            Text(
                stringResource(R.string.plans_no_ads),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun IntervalToggle(yearly: Boolean, onChange: (Boolean) -> Unit) {
    Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
        Row(
            modifier = Modifier
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.surfaceContainerHigh)
                .border(1.dp, MaterialTheme.colorScheme.outlineVariant, CircleShape)
                .padding(4.dp)
                .selectableGroup(),
        ) {
            ToggleSegment(stringResource(R.string.checkout_interval_monthly), selected = !yearly) { onChange(false) }
            ToggleSegment(stringResource(R.string.checkout_interval_yearly), selected = yearly) { onChange(true) }
        }
    }
}

@Composable
private fun ToggleSegment(label: String, selected: Boolean, onClick: () -> Unit) {
    Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier
            .width(124.dp)
            .clip(CircleShape)
            .background(if (selected) MaterialTheme.colorScheme.primary else Color.Transparent)
            .selectable(selected = selected, role = Role.RadioButton, onClick = onClick)
            .padding(vertical = 10.dp),
    ) {
        Text(
            label,
            style = MaterialTheme.typography.labelLarge,
            color = if (selected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun PlanCards(state: PlanUiState, selected: BillingInterval, billing: BillingViewModel) {
    Row(
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        modifier = Modifier
            .fillMaxWidth()
            .height(IntrinsicSize.Min)
            .padding(top = 12.dp),
    ) {
        FreeCard(state, Modifier.weight(1f).fillMaxHeight())
        ProCard(state, selected, billing, Modifier.weight(1f).fillMaxHeight())
    }
}

private val CardShape = RoundedCornerShape(20.dp)

@Composable
private fun FreeCard(state: PlanUiState, modifier: Modifier) {
    Surface(
        shape = CardShape,
        color = MaterialTheme.colorScheme.surfaceContainer,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
        modifier = modifier,
    ) {
        Column(Modifier.fillMaxSize().padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier
                    .size(44.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.surfaceContainerHighest),
            ) {
                Icon(Icons.Filled.Person, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Spacer(Modifier.height(6.dp))
            Text(stringResource(R.string.plan_free), style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
            Text(
                stringResource(R.string.plans_free_tagline),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(6.dp))
            Text(
                stringResource(R.string.plans_free_price),
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Bold,
            )
            Spacer(Modifier.weight(1f))
            if (state.checkedAt != null && state.plan == Plan.Free) {
                OutlinedButton(onClick = {}, enabled = false, shape = RoundedCornerShape(14.dp), modifier = Modifier.fillMaxWidth()) {
                    Text(stringResource(R.string.plans_current_plan))
                }
            }
        }
    }
}

@Composable
private fun ProCard(state: PlanUiState, selected: BillingInterval, billing: BillingViewModel, modifier: Modifier) {
    val primary = MaterialTheme.colorScheme.primary
    val yearly = selected == BillingInterval.Year
    Box(modifier) {
        Surface(
            shape = CardShape,
            color = MaterialTheme.colorScheme.surfaceContainer,
            border = BorderStroke(2.dp, NeonBrush),
            modifier = Modifier.fillMaxSize(),
        ) {
            Column(
                verticalArrangement = Arrangement.spacedBy(4.dp),
                modifier = Modifier
                    .fillMaxSize()
                    .background(Brush.verticalGradient(listOf(primary.copy(alpha = 0.14f), primary.copy(alpha = 0.03f))))
                    .padding(16.dp),
            ) {
                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier
                        .size(44.dp)
                        .clip(CircleShape)
                        .background(primary.copy(alpha = 0.12f))
                        .border(2.dp, NeonBrush, CircleShape),
                ) {
                    Icon(painterResource(R.drawable.ic_crown), contentDescription = null, tint = primary, modifier = Modifier.size(22.dp))
                }
                Spacer(Modifier.height(6.dp))
                Text(stringResource(R.string.plan_pro), style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                Text(
                    stringResource(R.string.plans_pro_tagline),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.height(6.dp))
                Row {
                    Text(
                        stringResource(if (yearly) R.string.plans_year_price else R.string.plans_month_price),
                        style = MaterialTheme.typography.headlineMedium,
                        fontWeight = FontWeight.Bold,
                        color = primary,
                        modifier = Modifier.alignByBaseline(),
                    )
                    Spacer(Modifier.width(4.dp))
                    Text(
                        stringResource(if (yearly) R.string.plans_per_year else R.string.plans_per_month),
                        style = MaterialTheme.typography.titleSmall,
                        color = primary,
                        modifier = Modifier.alignByBaseline(),
                    )
                }
                Text(
                    stringResource(if (yearly) R.string.checkout_interval_save else R.string.plans_or_yearly),
                    style = MaterialTheme.typography.bodySmall,
                    color = if (yearly) MaterialTheme.colorScheme.tertiary else MaterialTheme.colorScheme.onSurfaceVariant,
                    fontWeight = if (yearly) FontWeight.SemiBold else null,
                )
                Spacer(Modifier.weight(1f))
                Spacer(Modifier.height(8.dp))
                when (val button = ProCardButton.of(state.plan, state.subscriptionInterval, state.billing, selected)) {
                    is ProCardButton.Buy -> GradientButton(
                        stringResource(if (button.action == BillingAction.GoProAgain) R.string.plans_resubscribe else R.string.plans_go_pro),
                    ) { billing.openCheckout(CheckoutPurpose.Subscribe, preselect = selected) }
                    is ProCardButton.Switch -> GradientButton(
                        stringResource(
                            if (button.to == BillingInterval.Year) R.string.plans_switch_to_yearly else R.string.plans_switch_to_monthly,
                        ),
                        enabled = button.enabled,
                    ) { billing.openCheckout(CheckoutPurpose.SwitchInterval) }
                    ProCardButton.CurrentPlan -> GradientButton(stringResource(R.string.plans_current_plan), enabled = false) {}
                    ProCardButton.Unavailable -> GradientButton(stringResource(R.string.plans_go_pro), enabled = false) {}
                }
            }
        }
        BestValueBadge(
            Modifier
                .align(Alignment.TopEnd)
                .offset(x = 4.dp, y = (-12).dp),
        )
    }
}

@Composable
private fun BestValueBadge(modifier: Modifier) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = modifier
            .clip(CircleShape)
            .background(NeonBrush)
            .padding(horizontal = 10.dp, vertical = 4.dp),
    ) {
        Icon(painterResource(R.drawable.ic_crown), contentDescription = null, tint = Color.White, modifier = Modifier.size(14.dp))
        Spacer(Modifier.width(4.dp))
        Text(stringResource(R.string.plans_best_value), style = MaterialTheme.typography.labelSmall, color = Color.White)
    }
}

@Composable
private fun GradientButton(label: String, enabled: Boolean = true, onClick: () -> Unit) {
    val disabled = MaterialTheme.colorScheme.onSurface
    Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier
            .fillMaxWidth()
            .height(44.dp)
            .clip(RoundedCornerShape(14.dp))
            .background(if (enabled) NeonBrush else SolidColor(disabled.copy(alpha = 0.12f)))
            .clickable(enabled = enabled, role = Role.Button, onClick = onClick),
    ) {
        Text(
            label,
            style = MaterialTheme.typography.labelLarge,
            fontWeight = FontWeight.SemiBold,
            color = if (enabled) Color.White else disabled.copy(alpha = 0.38f),
        )
    }
}

/** What a plan gives for one feature in the Compare Plans table. */
private sealed interface PlanCell {
    data object Yes : PlanCell
    data object No : PlanCell
    data class Label(val text: String, val strong: Boolean = false) : PlanCell
}

private const val FEATURE_WEIGHT = 2.2f
private const val PLAN_WEIGHT = 1f

@Composable
private fun ComparePlans() {
    val primary = MaterialTheme.colorScheme.primary
    val unlimited = PlanCell.Label(stringResource(R.string.plans_unlimited), strong = true)
    Box {
        Row(Modifier.matchParentSize()) {
            Spacer(Modifier.weight(FEATURE_WEIGHT + PLAN_WEIGHT))
            Box(
                Modifier
                    .weight(PLAN_WEIGHT)
                    .fillMaxHeight()
                    .clip(RoundedCornerShape(16.dp))
                    .background(primary.copy(alpha = 0.08f))
                    .border(1.dp, primary.copy(alpha = 0.30f), RoundedCornerShape(16.dp)),
            )
        }
        Column {
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(vertical = 14.dp)) {
                Text(
                    stringResource(R.string.plans_compare),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.weight(FEATURE_WEIGHT),
                )
                PlanHeader(
                    { Icon(Icons.Filled.Person, null, Modifier.size(16.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant) },
                    stringResource(R.string.plan_free),
                    MaterialTheme.colorScheme.onSurfaceVariant,
                )
                PlanHeader(
                    { Icon(painterResource(R.drawable.ic_crown), null, Modifier.size(16.dp), tint = primary) },
                    stringResource(R.string.plan_pro),
                    primary,
                )
            }
            FeatureRow(
                { FeatureIcon(Icons.Filled.MusicNote) },
                R.string.plans_feature_player, R.string.plans_feature_player_hint, PlanCell.Yes, PlanCell.Yes,
            )
            FeatureRow(
                { FeatureIcon(Icons.Filled.ContentCut) },
                R.string.plans_feature_cutter, R.string.plans_feature_cutter_hint, PlanCell.Yes, PlanCell.Yes,
            )
            FeatureRow(
                { FeatureIcon(Icons.Filled.GraphicEq) },
                R.string.plans_feature_metronome, R.string.plans_feature_metronome_hint, PlanCell.Yes, PlanCell.Yes,
            )
            FeatureRow(
                { FeatureIcon(Icons.Filled.Mic) },
                R.string.plans_feature_separator, R.string.plans_feature_separator_hint,
                PlanCell.Label(stringResource(R.string.plans_separator_free)), unlimited,
            )
            FeatureRow(
                {
                    Text(
                        stringResource(R.string.plans_feature_bpm_tile),
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.Bold,
                        color = primary,
                    )
                },
                R.string.plans_feature_bpm, R.string.plans_feature_bpm_hint, PlanCell.No, PlanCell.Yes,
            )
            FeatureRow(
                { FeatureIcon(Icons.Filled.MicExternalOn) },
                R.string.plans_feature_singalong, R.string.plans_feature_singalong_hint, PlanCell.No, PlanCell.Yes,
            )
        }
    }
}

@Composable
private fun RowScope.PlanHeader(icon: @Composable () -> Unit, label: String, color: Color) {
    Row(
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.weight(PLAN_WEIGHT),
    ) {
        icon()
        Spacer(Modifier.width(4.dp))
        Text(label, style = MaterialTheme.typography.titleSmall, color = color)
    }
}

@Composable
private fun FeatureIcon(icon: ImageVector) {
    Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(22.dp))
}

@Composable
private fun FeatureRow(icon: @Composable () -> Unit, title: Int, hint: Int, free: PlanCell, pro: PlanCell) {
    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(vertical = 12.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(FEATURE_WEIGHT)) {
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier
                    .size(40.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(MaterialTheme.colorScheme.surfaceContainerHigh),
            ) { icon() }
            Spacer(Modifier.width(12.dp))
            Column {
                Text(stringResource(title), style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium)
                Text(
                    stringResource(hint),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        CellContent(free)
        CellContent(pro)
    }
}

@Composable
private fun RowScope.CellContent(cell: PlanCell) {
    val included = stringResource(R.string.plans_included)
    val notIncluded = stringResource(R.string.plans_not_included)
    Box(contentAlignment = Alignment.Center, modifier = Modifier.weight(PLAN_WEIGHT)) {
        when (cell) {
            PlanCell.Yes -> Icon(
                Icons.Filled.CheckCircle,
                contentDescription = included,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(24.dp),
            )
            PlanCell.No -> Text(
                "—",
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.semantics { contentDescription = notIncluded },
            )
            is PlanCell.Label -> Text(
                cell.text,
                textAlign = TextAlign.Center,
                style = if (cell.strong) MaterialTheme.typography.titleSmall else MaterialTheme.typography.bodySmall,
                fontWeight = if (cell.strong) FontWeight.Bold else null,
                color = if (cell.strong) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
            )
        }
    }
}

/** The current plan and its dates, then what the PayU subscription is doing and what can be done about it (PS1, PS2, CN1). */
@Composable
private fun SubscriptionSection(
    state: PlanUiState,
    refreshing: Boolean,
    onRefresh: () -> Unit,
    billing: BillingViewModel,
    onOpenHistory: () -> Unit,
) {
    val context = LocalContext.current
    fun date(millis: Long) = DateUtils.formatDateTime(context, millis, DateUtils.FORMAT_SHOW_DATE or DateUtils.FORMAT_SHOW_YEAR)
    fun billingDay(millis: Long?) = millis?.let { billingDate(context, it) } ?: "—"
    Surface(
        shape = CardShape,
        color = MaterialTheme.colorScheme.surfaceContainer,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(
                        stringResource(R.string.plans_subscription),
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Text(planLabel(state), style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold)
                }
                OutlinedButton(onClick = onRefresh, enabled = !refreshing) {
                    Text(stringResource(R.string.plans_refresh))
                }
            }
            if (state.plan == Plan.Trial) state.trialEndsAt?.let { Text(stringResource(R.string.plans_trial_ends, date(it))) }
            if (state.plan == Plan.Pro) {
                val renews = state.subscriptionNextBillingAt?.takeUnless { state.cancelAtPeriodEnd }
                when {
                    renews != null -> Text(stringResource(R.string.plans_pro_renews, date(renews)))
                    state.subscriptionExpiresAt != null ->
                        Text(stringResource(R.string.plans_pro_ends, date(state.subscriptionExpiresAt)))
                }
            }
            if (state.plan == Plan.Free && state.trialClaim == TrialClaim.Denied) {
                Text(stringResource(R.string.plans_trial_used), style = MaterialTheme.typography.bodyMedium)
            }
            state.usage?.let { usage ->
                Text(
                    stringResource(R.string.usage_title) + ": " + usageSummary(usage),
                    style = MaterialTheme.typography.bodyMedium,
                )
            }
            if (state.stale) {
                Text(
                    stringResource(R.string.plans_stale),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.error,
                )
            }
            state.checkedAt?.let {
                Text(
                    stringResource(
                        R.string.plans_checked,
                        DateUtils.getRelativeTimeSpanString(it, System.currentTimeMillis(), DateUtils.MINUTE_IN_MILLIS),
                    ),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            if (state.checkedAt != null) BillingDetails(state, billing, onOpenHistory, ::billingDay)
        }
    }
}

@Composable
private fun BillingDetails(
    state: PlanUiState,
    billing: BillingViewModel,
    onOpenHistory: () -> Unit,
    date: (Long?) -> String,
) {
    val status = state.billing
    when (val notice = status.notice) {
        null -> Unit
        BillingNotice.PaymentsUnavailable -> Notice(stringResource(R.string.plans_payments_unavailable))
        is BillingNotice.PaymentPending -> Notice(stringResource(R.string.plans_payment_pending))
        is BillingNotice.AutopayOn -> Notice(
            stringResource(R.string.plans_autopay_on, date(notice.nextBillingAt), wholeRupees(pricePaise(notice.interval))),
        )
        is BillingNotice.AutopayNotSet -> Notice(stringResource(R.string.plans_autopay_not_set, date(notice.expiresAt)))
        is BillingNotice.RenewSoon -> Notice(stringResource(R.string.plans_renew_reminder, date(notice.expiresAt)))
        is BillingNotice.PastDue -> Notice(stringResource(R.string.plans_past_due, date(notice.graceEnd)), warning = true)
        is BillingNotice.CancelPending -> Notice(stringResource(R.string.plans_cancel_pending))
        is BillingNotice.Cancelled -> Notice(stringResource(R.string.plans_cancelled_until, date(notice.expiresAt)))
        is BillingNotice.MandateEnding -> Notice(stringResource(R.string.plans_mandate_ends, date(notice.mandateEnd)), warning = true)
        BillingNotice.Expired -> Notice(stringResource(R.string.plans_expired))
    }
    state.lastPayment?.let {
        Text(
            stringResource(R.string.plans_last_payment, rupees(it.amountPaise), date(it.at)),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
    status.actions.filter { it !in ProCardButton.HANDLED }.forEach { action ->
        when (action) {
            BillingAction.GoPro, BillingAction.GoProAgain, BillingAction.SwitchToYearly, BillingAction.SwitchToMonthly -> Unit
            BillingAction.FixPayment -> Button(
                onClick = { billing.openCheckout(CheckoutPurpose.FixPayment) },
                modifier = Modifier.fillMaxWidth(),
            ) { Text(stringResource(R.string.plans_fix_payment)) }
            BillingAction.SetUpAutopay -> Button(
                onClick = { billing.openCheckout(CheckoutPurpose.SetUpAutopay) },
                modifier = Modifier.fillMaxWidth(),
            ) { Text(stringResource(R.string.plans_set_up_autopay)) }
            BillingAction.Renew -> Button(
                onClick = { billing.openCheckout(CheckoutPurpose.Renew) },
                modifier = Modifier.fillMaxWidth(),
            ) { Text(stringResource(R.string.plans_renew)) }
            BillingAction.CheckPayment -> OutlinedButton(
                onClick = { billing.onReturned((status.notice as? BillingNotice.PaymentPending)?.txnId) },
                modifier = Modifier.fillMaxWidth(),
            ) { Text(stringResource(R.string.plans_check_payment)) }
            BillingAction.CancelAutopay -> OutlinedButton(
                onClick = billing::requestCancel,
                modifier = Modifier.fillMaxWidth(),
            ) { Text(stringResource(R.string.plans_cancel_autopay)) }
        }
    }
    if (state.hasPayments) {
        TextButton(onClick = onOpenHistory) { Text(stringResource(R.string.plans_payment_history)) }
    }
}

@Composable
private fun Notice(text: String, warning: Boolean = false) {
    Card(
        colors = CardDefaults.cardColors(
            containerColor = if (warning) MaterialTheme.colorScheme.errorContainer else MaterialTheme.colorScheme.secondaryContainer,
            contentColor = if (warning) MaterialTheme.colorScheme.onErrorContainer else MaterialTheme.colorScheme.onSecondaryContainer,
        ),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Text(text, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(12.dp))
    }
}

/** "Free trial, 23 days left", "Pro" or "Free"; "Checking your plan…" before the first answer. */
@Composable
fun planLabel(state: PlanUiState): String {
    val daysLeft = state.trialDaysLeft
    return when {
        state.checkedAt == null -> stringResource(R.string.plan_checking)
        daysLeft != null -> pluralStringResource(R.plurals.plan_trial_days_left, daysLeft, daysLeft)
        state.subscriptionInterval == BillingInterval.Year -> stringResource(R.string.plans_pro_yearly)
        state.subscriptionInterval == BillingInterval.Month -> stringResource(R.string.plans_pro_monthly)
        else -> planName(state.plan)
    }
}

@Composable
private fun planName(plan: Plan): String = stringResource(
    when (plan) {
        Plan.Free -> R.string.plan_free
        Plan.Trial -> R.string.plan_trial
        Plan.Pro -> R.string.plan_pro
    },
)
