package com.autoomstudio.mp3studio.ui.plans

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.CloudOff
import androidx.compose.material.icons.outlined.ErrorOutline
import androidx.compose.material.icons.outlined.HourglassTop
import androidx.compose.material.icons.outlined.TimerOff
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.autoomstudio.mp3studio.R

/**
 * After PayU (payments PRD CK5 to CK8): confirming, success, failed with retry, expired, not finished, or waiting for
 * the bank. Nothing here grants Pro; it shows what the server decided after checking with PayU.
 */
@Composable
fun PaymentResultScreen(
    result: PaymentResultUi,
    onRetry: () -> Unit,
    onDismiss: () -> Unit,
) {
    val context = LocalContext.current
    val confirming = result == PaymentResultUi.Confirming
    Dialog(
        onDismissRequest = { if (!confirming) onDismiss() },
        properties = DialogProperties(usePlatformDefaultWidth = false, dismissOnClickOutside = false),
    ) {
        Surface(modifier = Modifier.fillMaxSize()) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(16.dp, Alignment.CenterVertically),
                modifier = Modifier
                    .fillMaxSize()
                    .safeDrawingPadding()
                    .padding(32.dp),
            ) {
                when (result) {
                    PaymentResultUi.Confirming -> {
                        CircularProgressIndicator(modifier = Modifier.size(56.dp))
                        Title(stringResource(R.string.payment_confirming))
                        Body(stringResource(R.string.payment_confirming_note))
                    }
                    is PaymentResultUi.Success -> {
                        Mark(Icons.Outlined.CheckCircle, MaterialTheme.colorScheme.primary)
                        Title(stringResource(R.string.payment_success_title))
                        result.expiresAt?.let { Body(stringResource(R.string.payment_success_until, billingDate(context, it))) }
                        Primary(stringResource(R.string.payment_done), onDismiss)
                    }
                    is PaymentResultUi.Failed -> {
                        Mark(Icons.Outlined.ErrorOutline, MaterialTheme.colorScheme.error)
                        Title(stringResource(R.string.payment_failed_title))
                        Body(stringResource(R.string.payment_failed_note))
                        result.reason?.takeIf { it.isNotBlank() && it != BillingViewModel.LINK_EXPIRED }?.let {
                            Body(stringResource(R.string.payment_failed_reason, it))
                        }
                        Primary(stringResource(R.string.payment_try_again), onRetry)
                        Secondary(stringResource(R.string.payment_done), onDismiss)
                    }
                    PaymentResultUi.Expired -> {
                        Mark(Icons.Outlined.TimerOff, MaterialTheme.colorScheme.error)
                        Title(stringResource(R.string.payment_expired_title))
                        Body(stringResource(R.string.payment_expired_note))
                        Primary(stringResource(R.string.payment_try_again), onRetry)
                        Secondary(stringResource(R.string.payment_done), onDismiss)
                    }
                    PaymentResultUi.NotCompleted -> {
                        Mark(Icons.Outlined.ErrorOutline, MaterialTheme.colorScheme.onSurfaceVariant)
                        Title(stringResource(R.string.payment_not_completed_title))
                        Body(stringResource(R.string.payment_not_completed_note))
                        Primary(stringResource(R.string.payment_back_to_payment), onRetry)
                        Secondary(stringResource(R.string.payment_done), onDismiss)
                    }
                    is PaymentResultUi.Waiting -> {
                        Mark(Icons.Outlined.HourglassTop, MaterialTheme.colorScheme.primary)
                        Title(stringResource(R.string.payment_waiting_title))
                        Body(stringResource(R.string.payment_waiting_note))
                        result.resolveUntil?.let {
                            Body(stringResource(R.string.payment_waiting_until, billingDateTime(context, it)))
                        }
                        Primary(stringResource(R.string.payment_done), onDismiss)
                    }
                    is PaymentResultUi.Unchecked -> {
                        Mark(Icons.Outlined.CloudOff, MaterialTheme.colorScheme.onSurfaceVariant)
                        Title(stringResource(R.string.payment_unchecked_title))
                        Body(stringResource(R.string.payment_unchecked_note))
                        Primary(stringResource(R.string.payment_done), onDismiss)
                    }
                    PaymentResultUi.NoBrowser -> {
                        Mark(Icons.Outlined.ErrorOutline, MaterialTheme.colorScheme.error)
                        Title(stringResource(R.string.checkout_no_browser))
                        Primary(stringResource(R.string.payment_done), onDismiss)
                    }
                }
            }
        }
    }
}

@Composable
private fun Mark(icon: ImageVector, tint: androidx.compose.ui.graphics.Color) {
    Icon(icon, contentDescription = null, tint = tint, modifier = Modifier.size(64.dp))
}

@Composable
private fun Title(text: String) {
    Text(text, style = MaterialTheme.typography.headlineSmall, textAlign = TextAlign.Center)
}

@Composable
private fun Body(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.bodyLarge,
        textAlign = TextAlign.Center,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
}

@Composable
private fun Primary(label: String, onClick: () -> Unit) {
    Button(onClick = onClick, modifier = Modifier.fillMaxWidth()) { Text(label) }
}

@Composable
private fun Secondary(label: String, onClick: () -> Unit) {
    OutlinedButton(onClick = onClick, modifier = Modifier.fillMaxWidth()) { Text(label) }
}
