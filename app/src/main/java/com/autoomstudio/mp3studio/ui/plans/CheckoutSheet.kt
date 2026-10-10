package com.autoomstudio.mp3studio.ui.plans

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material3.Button
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.autoomstudio.mp3studio.R
import com.autoomstudio.mp3studio.data.plan.BillingInterval
import com.autoomstudio.mp3studio.data.plan.BillingMode

/**
 * Before PayU opens (payments PRD CK1, CK13): monthly or yearly, the price, what autopay means, the policies, and the
 * mobile number PayU needs when the profile has none. "Pay ₹99" or "Pay ₹999" asks the server for the link.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CheckoutSheet(
    state: CheckoutUi,
    onPhoneChange: (String) -> Unit,
    onIntervalChange: (BillingInterval) -> Unit,
    onPay: () -> Unit,
    onDismiss: () -> Unit,
) {
    val uriHandler = LocalUriHandler.current
    val context = LocalContext.current
    val yearly = state.interval == BillingInterval.Year
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 24.dp)
                .padding(bottom = 24.dp)
                .navigationBarsPadding()
                .imePadding(),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(
                stringResource(
                    when (state.purpose) {
                        CheckoutPurpose.Subscribe -> R.string.checkout_title
                        CheckoutPurpose.FixPayment -> R.string.checkout_title_fix
                        CheckoutPurpose.SetUpAutopay -> R.string.checkout_title_autopay
                        CheckoutPurpose.Renew -> R.string.checkout_title_renew
                        CheckoutPurpose.SwitchInterval ->
                            if (yearly) R.string.checkout_title_switch_yearly else R.string.checkout_title_switch_monthly
                    },
                ),
                style = MaterialTheme.typography.titleLarge,
            )
            if (!state.intervalLocked) {
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.selectableGroup()) {
                    IntervalOption(
                        title = stringResource(R.string.checkout_interval_monthly),
                        price = stringResource(R.string.checkout_interval_monthly_price),
                        tag = null,
                        selected = !yearly,
                        enabled = !state.busy,
                        onClick = { onIntervalChange(BillingInterval.Month) },
                        modifier = Modifier.weight(1f),
                    )
                    IntervalOption(
                        title = stringResource(R.string.checkout_interval_yearly),
                        price = stringResource(R.string.checkout_interval_yearly_price),
                        tag = stringResource(R.string.checkout_interval_save),
                        selected = yearly,
                        enabled = !state.busy,
                        onClick = { onIntervalChange(BillingInterval.Year) },
                        modifier = Modifier.weight(1f),
                    )
                }
            }
            Text(
                stringResource(
                    when {
                        state.mode == BillingMode.Autopay && yearly -> R.string.checkout_autopay_terms_yearly
                        state.mode == BillingMode.Autopay -> R.string.checkout_autopay_terms
                        yearly -> R.string.checkout_manual_terms_yearly
                        else -> R.string.checkout_manual_terms
                    },
                ),
                style = MaterialTheme.typography.bodyMedium,
            )
            state.switchStartsAt?.let {
                Text(
                    stringResource(R.string.checkout_switch_note, billingDate(context, it)),
                    style = MaterialTheme.typography.bodyMedium,
                )
            }
            if (state.needsPhone) {
                OutlinedTextField(
                    value = state.phone,
                    onValueChange = onPhoneChange,
                    label = { Text(stringResource(R.string.checkout_phone_label)) },
                    prefix = { Text("+91 ") },
                    singleLine = true,
                    enabled = !state.busy,
                    isError = state.phoneInvalid,
                    supportingText = {
                        Text(
                            stringResource(
                                if (state.phoneInvalid) R.string.checkout_phone_invalid else R.string.checkout_phone_help,
                            ),
                        )
                    },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone, imeAction = ImeAction.Done),
                    keyboardActions = KeyboardActions(onDone = { onPay() }),
                    modifier = Modifier.fillMaxWidth(),
                )
            }
            state.error?.let {
                Text(
                    stringResource(billingErrorText(it)),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.error,
                )
            }
            Button(onClick = onPay, enabled = !state.busy, modifier = Modifier.fillMaxWidth()) {
                if (state.busy) {
                    CircularProgressIndicator(strokeWidth = 2.dp, modifier = Modifier.size(18.dp))
                } else {
                    Text(stringResource(R.string.checkout_pay, wholeRupees(pricePaise(state.interval))))
                }
            }
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Icon(
                    Icons.Outlined.Lock,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(16.dp),
                )
                Text(
                    stringResource(R.string.checkout_secure_note),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            val termsUrl = stringResource(R.string.about_terms_url)
            val refundUrl = stringResource(R.string.checkout_refund_url)
            val privacyUrl = stringResource(R.string.about_privacy_url)
            Row(horizontalArrangement = Arrangement.Center, modifier = Modifier.fillMaxWidth()) {
                TextButton(onClick = { uriHandler.openUri(termsUrl) }) { Text(stringResource(R.string.checkout_terms)) }
                TextButton(onClick = { uriHandler.openUri(refundUrl) }) { Text(stringResource(R.string.checkout_refunds)) }
                TextButton(onClick = { uriHandler.openUri(privacyUrl) }) { Text(stringResource(R.string.checkout_privacy)) }
            }
        }
    }
}

@Composable
private fun IntervalOption(
    title: String,
    price: String,
    tag: String?,
    selected: Boolean,
    enabled: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = MaterialTheme.colorScheme
    OutlinedCard(
        border = BorderStroke(if (selected) 2.dp else 1.dp, if (selected) colors.primary else colors.outlineVariant),
        colors = CardDefaults.outlinedCardColors(
            containerColor = if (selected) colors.primaryContainer else colors.surface,
            contentColor = if (selected) colors.onPrimaryContainer else colors.onSurface,
        ),
        modifier = modifier.selectable(selected = selected, enabled = enabled, role = Role.RadioButton, onClick = onClick),
    ) {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(title, style = MaterialTheme.typography.titleSmall, modifier = Modifier.weight(1f))
                if (selected) Icon(Icons.Filled.CheckCircle, contentDescription = null, modifier = Modifier.size(18.dp))
            }
            Text(price, style = MaterialTheme.typography.bodyMedium)
            if (tag != null) {
                Text(
                    tag,
                    style = MaterialTheme.typography.labelMedium,
                    color = if (selected) colors.onPrimaryContainer else colors.primary,
                )
            }
        }
    }
}
