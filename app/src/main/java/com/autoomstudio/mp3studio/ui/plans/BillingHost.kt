package com.autoomstudio.mp3studio.ui.plans

import android.content.ActivityNotFoundException
import android.content.Context
import android.net.Uri
import androidx.browser.customtabs.CustomTabsIntent
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.size
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.core.net.toUri
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.autoomstudio.mp3studio.R
import com.autoomstudio.mp3studio.data.billing.CancelResult

/**
 * Everything around a payment that can appear over any screen: the checkout sheet, the PayU Custom Tab, the result
 * screen after returning, and the cancel-autopay dialog. [returnRequest] is set when the return page's App Link opened
 * the app; it holds the transaction ID from the link, or "" when there was none.
 */
@Composable
fun BillingHost(
    returnRequest: String?,
    onReturnHandled: () -> Unit,
    viewModel: BillingViewModel = viewModel(factory = BillingViewModel.Factory),
) {
    val context = LocalContext.current
    val state by viewModel.state.collectAsStateWithLifecycle()

    LaunchedEffect(viewModel) {
        viewModel.openUrl.collect { url -> if (!openPaymentPage(context, url)) viewModel.onOpenFailed() }
    }
    LaunchedEffect(returnRequest) {
        if (returnRequest != null) {
            viewModel.onReturned(returnRequest.ifEmpty { null })
            onReturnHandled()
        }
    }
    LifecycleResumeEffect(viewModel) {
        viewModel.onForeground()
        onPauseOrDispose { }
    }

    state.checkout?.let {
        CheckoutSheet(
            state = it,
            onPhoneChange = viewModel::onPhoneChange,
            onIntervalChange = viewModel::onIntervalChange,
            onPay = viewModel::pay,
            onDismiss = viewModel::dismissCheckout,
        )
    }
    state.result?.let {
        PaymentResultScreen(result = it, onRetry = viewModel::retry, onDismiss = viewModel::dismissResult)
    }
    state.cancel?.let { CancelAutopayDialog(it, onConfirm = viewModel::confirmCancel, onDismiss = viewModel::dismissCancel) }
}

@Composable
private fun CancelAutopayDialog(state: CancelUi, onConfirm: () -> Unit, onDismiss: () -> Unit) {
    val context = LocalContext.current
    when (state) {
        is CancelUi.Confirm -> AlertDialog(
            onDismissRequest = onDismiss,
            title = { Text(stringResource(R.string.cancel_title)) },
            text = {
                Column {
                    Text(
                        state.expiresAt?.let { stringResource(R.string.cancel_message, billingDate(context, it)) }
                            ?: stringResource(R.string.cancel_message_no_date),
                    )
                    state.error?.let {
                        Text(stringResource(billingErrorText(it)), color = MaterialTheme.colorScheme.error)
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = onConfirm, enabled = !state.busy) {
                    if (state.busy) {
                        CircularProgressIndicator(strokeWidth = 2.dp, modifier = Modifier.size(18.dp))
                    } else {
                        Text(stringResource(R.string.cancel_confirm))
                    }
                }
            },
            dismissButton = {
                TextButton(onClick = onDismiss, enabled = !state.busy) { Text(stringResource(R.string.cancel_keep)) }
            },
        )
        is CancelUi.Done -> {
            val result = state.result
            AlertDialog(
                onDismissRequest = onDismiss,
                title = {
                    Text(
                        stringResource(
                            if (result is CancelResult.Cancelled) R.string.cancel_done_title else R.string.cancel_pending_title,
                        ),
                    )
                },
                text = {
                    Text(
                        when (result) {
                            is CancelResult.Cancelled -> result.expiresAt
                                ?.let { stringResource(R.string.cancel_done_message, billingDate(context, it)) }
                                ?: stringResource(R.string.cancel_message_no_date)
                            CancelResult.Pending -> stringResource(R.string.cancel_pending_message)
                        },
                    )
                },
                confirmButton = { TextButton(onClick = onDismiss) { Text(stringResource(android.R.string.ok)) } },
            )
        }
    }
}

/** Opens a PayU link in a Custom Tab, or any browser; false when there is none. Only https links are opened. */
private fun openPaymentPage(context: Context, url: String): Boolean {
    val uri: Uri = url.toUri()
    if (uri.scheme != "https") return false
    return try {
        CustomTabsIntent.Builder().setShowTitle(true).build().launchUrl(context, uri)
        true
    } catch (_: ActivityNotFoundException) {
        false
    }
}
