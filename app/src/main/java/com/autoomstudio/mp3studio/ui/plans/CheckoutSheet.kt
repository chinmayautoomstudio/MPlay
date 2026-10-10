package com.autoomstudio.mp3studio.ui.plans

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.autoomstudio.mp3studio.R
import com.autoomstudio.mp3studio.data.plan.BillingMode

/**
 * Before PayU opens (payments PRD CK1, CK13): the price, what autopay means, the policies, and the mobile number PayU
 * needs when the profile has none. "Pay ₹99" asks the server for the link.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CheckoutSheet(
    state: CheckoutUi,
    onPhoneChange: (String) -> Unit,
    onPay: () -> Unit,
    onDismiss: () -> Unit,
) {
    val uriHandler = LocalUriHandler.current
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
                    },
                ),
                style = MaterialTheme.typography.titleLarge,
            )
            Text(
                stringResource(
                    if (state.mode == BillingMode.Autopay) R.string.checkout_autopay_terms else R.string.checkout_manual_terms,
                ),
                style = MaterialTheme.typography.bodyMedium,
            )
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
                    Text(stringResource(R.string.checkout_pay))
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
