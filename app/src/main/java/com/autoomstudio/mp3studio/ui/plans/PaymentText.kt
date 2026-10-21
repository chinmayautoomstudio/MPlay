package com.autoomstudio.mp3studio.ui.plans

import android.content.Context
import android.text.format.DateUtils
import androidx.annotation.StringRes
import com.autoomstudio.mp3studio.R
import com.autoomstudio.mp3studio.data.billing.BillingError
import com.autoomstudio.mp3studio.data.billing.PaymentState
import com.autoomstudio.mp3studio.data.plan.BillingCurrency
import com.autoomstudio.mp3studio.data.plan.BillingInterval
import com.autoomstudio.mp3studio.data.plan.BillingRegion
import com.autoomstudio.mp3studio.data.plan.PriceList
import java.util.Locale

/** "₹99.00" or "$1.99" from minor units. */
fun formatAmount(minor: Int, currency: BillingCurrency): String {
    val symbol = if (currency == BillingCurrency.Usd) "$" else "₹"
    return symbol + String.format(Locale.US, "%.2f", minor / 100.0)
}

/** "₹99" or "$1.99". USD keeps cents; INR drops them when they are zero. */
fun formatPrice(minor: Int, currency: BillingCurrency): String {
    if (currency == BillingCurrency.Usd) return formatAmount(minor, currency)
    return if (minor % 100 == 0) "₹${minor / 100}" else formatAmount(minor, currency)
}

fun formatPrice(interval: BillingInterval, prices: PriceList, currency: BillingCurrency): String =
    formatPrice(prices.of(interval), currency)

/** @deprecated Use [formatAmount] with a currency. Kept for history rows that are INR. */
fun rupees(paise: Int): String = formatAmount(paise, BillingCurrency.Inr)

fun billingDate(context: Context, millis: Long): String =
    DateUtils.formatDateTime(context, millis, DateUtils.FORMAT_SHOW_DATE or DateUtils.FORMAT_SHOW_YEAR)

fun billingDateTime(context: Context, millis: Long): String = DateUtils.formatDateTime(
    context,
    millis,
    DateUtils.FORMAT_SHOW_DATE or DateUtils.FORMAT_SHOW_YEAR or DateUtils.FORMAT_SHOW_TIME,
)

@StringRes
fun billingErrorText(error: BillingError): Int = when (error) {
    BillingError.PhoneRequired, BillingError.InvalidPhone -> R.string.checkout_phone_invalid
    BillingError.PaymentInProgress -> R.string.checkout_error_in_progress
    BillingError.AlreadySubscribed -> R.string.checkout_error_subscribed
    BillingError.SwitchNotYet -> R.string.checkout_error_switch_not_yet
    BillingError.InvalidInterval -> R.string.checkout_error_other
    BillingError.MandateUpdatePending -> R.string.checkout_error_mandate
    BillingError.RateLimited -> R.string.checkout_error_rate_limited
    BillingError.AccountDisabled -> R.string.checkout_error_disabled
    BillingError.Unavailable -> R.string.checkout_error_unavailable
    BillingError.NotSubscribed -> R.string.cancel_error_not_subscribed
    BillingError.Offline -> R.string.plans_refresh_failed
    BillingError.NotFound, BillingError.Other -> R.string.checkout_error_other
}

fun billingErrorText(error: BillingError, region: BillingRegion): Int =
    if ((error == BillingError.PhoneRequired || error == BillingError.InvalidPhone) &&
        region == BillingRegion.International
    ) {
        R.string.checkout_phone_invalid_intl
    } else {
        billingErrorText(error)
    }

@StringRes
fun paymentStateText(state: PaymentState): Int = when (state) {
    PaymentState.Created -> R.string.payment_state_created
    PaymentState.Pending -> R.string.payment_state_pending
    PaymentState.Success -> R.string.payment_state_success
    PaymentState.Failed -> R.string.payment_state_failed
    PaymentState.Cancelled -> R.string.payment_state_cancelled
    PaymentState.Refunded -> R.string.payment_state_refunded
    PaymentState.PartiallyRefunded -> R.string.payment_state_partially_refunded
    PaymentState.Disputed -> R.string.payment_state_disputed
    PaymentState.Unknown -> R.string.payment_state_unknown
}

@StringRes
fun paymentKindText(kind: String, interval: BillingInterval = BillingInterval.Month): Int = when (kind) {
    "renewal" -> if (interval == BillingInterval.Year) R.string.payment_kind_renewal_yearly else R.string.payment_kind_renewal
    "replace" -> R.string.payment_kind_replace
    else -> R.string.payment_kind_first
}

fun priceOf(interval: BillingInterval, region: BillingRegion, pricesIn: PriceList, pricesIntl: PriceList): Int =
    (if (region == BillingRegion.International) pricesIntl else pricesIn).of(interval)
