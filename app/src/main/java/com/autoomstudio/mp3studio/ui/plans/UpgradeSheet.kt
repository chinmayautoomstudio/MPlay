package com.autoomstudio.mp3studio.ui.plans

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.WorkspacePremium
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.autoomstudio.mp3studio.R
import com.autoomstudio.mp3studio.data.plan.Feature

/**
 * Shown instead of a locked feature (PRD PL3, flow 6): what Pro adds and its price. Go Pro dismisses this and opens
 * the checkout sheet; "See plans" dismisses this and asks the main screen to open Settings > Plans.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun UpgradeSheet(
    feature: Feature,
    onDismiss: () -> Unit,
    /** Set when the Free weekly separator limit was reached (PRD US7): the sheet leads with the reset date. */
    limitResetsAt: Long? = null,
    viewModel: PlansViewModel = viewModel(factory = PlansViewModel.Factory),
    billingViewModel: BillingViewModel = viewModel(factory = BillingViewModel.Factory),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp)
                .padding(bottom = 24.dp)
                .navigationBarsPadding(),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Icon(
                Icons.Outlined.WorkspacePremium,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(40.dp),
            )
            Text(
                stringResource(
                    when {
                        limitResetsAt != null -> R.string.upgrade_title_limit
                        feature == Feature.BpmDetector -> R.string.upgrade_title_bpm
                        feature == Feature.SingAlong -> R.string.upgrade_title_singalong
                        else -> R.string.upgrade_title_separator
                    },
                ),
                style = MaterialTheme.typography.titleLarge,
            )
            if (limitResetsAt != null) {
                Text(
                    stringResource(R.string.upgrade_limit_resets, FREE_WEEKLY_LIMIT, resetDay(limitResetsAt)),
                    style = MaterialTheme.typography.bodyLarge,
                )
            }
            if (state.stale) {
                Text(
                    stringResource(R.string.plans_stale),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.error,
                )
            }
            Text(stringResource(R.string.upgrade_message), style = MaterialTheme.typography.bodyLarge)
            Benefit(stringResource(R.string.upgrade_benefit_separator))
            Benefit(stringResource(R.string.upgrade_benefit_bpm))
            Benefit(stringResource(R.string.upgrade_benefit_singalong))
            Text(
                stringResource(R.string.upgrade_kept),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(stringResource(R.string.plans_price), style = MaterialTheme.typography.titleMedium)
            val canPay = state.checkedAt != null && state.billing.notice != BillingNotice.PaymentsUnavailable
            Button(
                onClick = {
                    onDismiss()
                    billingViewModel.openCheckout(CheckoutPurpose.Subscribe)
                },
                enabled = canPay,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(stringResource(R.string.plans_go_pro))
            }
            if (!canPay && state.checkedAt != null) {
                Text(
                    stringResource(R.string.plans_payments_unavailable),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            TextButton(
                onClick = {
                    onDismiss()
                    viewModel.openPlans()
                },
                modifier = Modifier.align(Alignment.CenterHorizontally),
            ) {
                Text(stringResource(R.string.upgrade_see_plans))
            }
        }
    }
}

/** Matches `usage_summary` on the server and the "10 songs a week" row of the plan comparison. */
private const val FREE_WEEKLY_LIMIT = 10

@Composable
private fun Benefit(text: String) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        Icon(
            Icons.Outlined.CheckCircle,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(20.dp),
        )
        Text(text, style = MaterialTheme.typography.bodyMedium)
    }
}
