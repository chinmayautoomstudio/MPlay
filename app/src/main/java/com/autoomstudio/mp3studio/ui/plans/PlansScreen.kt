package com.autoomstudio.mp3studio.ui.plans

import android.text.format.DateUtils
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.autoomstudio.mp3studio.R
import com.autoomstudio.mp3studio.data.plan.Plan
import com.autoomstudio.mp3studio.data.plan.TrialClaim
import com.autoomstudio.mp3studio.ui.library.DetailBackButton

/** Settings > Plans (PRD PL6): the current plan with its dates, and what Free and Pro include. */
@Composable
fun PlansScreen(
    onBack: () -> Unit,
    onMessage: (String) -> Unit,
    modifier: Modifier = Modifier,
    backEnabled: Boolean = true,
    viewModel: PlansViewModel = viewModel(factory = PlansViewModel.Factory),
) {
    BackHandler(enabled = backEnabled, onBack = onBack)
    val state by viewModel.state.collectAsStateWithLifecycle()
    val refreshing by viewModel.refreshing.collectAsStateWithLifecycle()
    val resources = LocalResources.current
    LaunchedEffect(viewModel) { viewModel.refresh() }
    LaunchedEffect(viewModel) {
        viewModel.refreshFailed.collect { onMessage(resources.getString(R.string.plans_refresh_failed)) }
    }

    Column(modifier.verticalScroll(rememberScrollState())) {
        DetailBackButton(onBack = onBack)
        Column(
            verticalArrangement = Arrangement.spacedBy(16.dp),
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 8.dp),
        ) {
            Text(stringResource(R.string.plans_title), style = MaterialTheme.typography.headlineSmall)
            CurrentPlanCard(state = state, refreshing = refreshing, onRefresh = viewModel::refresh)
            Text(stringResource(R.string.plans_compare), style = MaterialTheme.typography.titleMedium)
            ComparisonTable()
            Text(stringResource(R.string.plans_trial_note), style = MaterialTheme.typography.bodyMedium)
            Text(
                stringResource(R.string.plans_no_ads),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            if (state.plan != Plan.Pro) {
                Text(stringResource(R.string.plans_price), style = MaterialTheme.typography.titleMedium)
                Button(onClick = {}, enabled = false, modifier = Modifier.fillMaxWidth()) {
                    Text(stringResource(R.string.plans_go_pro_soon))
                }
            }
        }
    }
}

@Composable
private fun CurrentPlanCard(state: PlanUiState, refreshing: Boolean, onRefresh: () -> Unit) {
    val context = LocalContext.current
    fun date(millis: Long) = DateUtils.formatDateTime(context, millis, DateUtils.FORMAT_SHOW_DATE or DateUtils.FORMAT_SHOW_YEAR)
    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(
                stringResource(R.string.plans_current),
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(
                planLabel(state),
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.SemiBold,
            )
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
            OutlinedButton(onClick = onRefresh, enabled = !refreshing) {
                Text(stringResource(R.string.plans_refresh))
            }
        }
    }
}

@Composable
private fun ComparisonTable() {
    val included = stringResource(R.string.plans_included)
    val notIncluded = stringResource(R.string.plans_not_included)
    val unlimited = stringResource(R.string.plans_unlimited)
    Column {
        ComparisonRow("", stringResource(R.string.plan_free), stringResource(R.string.plan_pro), header = true)
        HorizontalDivider()
        ComparisonRow(stringResource(R.string.plans_feature_player), included, included)
        ComparisonRow(stringResource(R.string.plans_feature_tools), included, included)
        ComparisonRow(stringResource(R.string.plans_feature_separator), stringResource(R.string.plans_separator_free), unlimited)
        ComparisonRow(stringResource(R.string.plans_feature_bpm), notIncluded, included)
        ComparisonRow(stringResource(R.string.plans_feature_singalong), notIncluded, included)
    }
}

@Composable
private fun ComparisonRow(feature: String, free: String, pro: String, header: Boolean = false) {
    val style = if (header) MaterialTheme.typography.labelLarge else MaterialTheme.typography.bodyMedium
    Row(
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp),
    ) {
        Text(feature, style = style, modifier = Modifier.weight(1.6f))
        Text(free, style = style, modifier = Modifier.weight(1f))
        Text(pro, style = style, modifier = Modifier.weight(1f))
    }
}

/** "Free trial, 23 days left", "Pro" or "Free"; "Checking your plan…" before the first answer. */
@Composable
fun planLabel(state: PlanUiState): String {
    val daysLeft = state.trialDaysLeft
    return when {
        state.checkedAt == null -> stringResource(R.string.plan_checking)
        daysLeft != null -> pluralStringResource(R.plurals.plan_trial_days_left, daysLeft, daysLeft)
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
