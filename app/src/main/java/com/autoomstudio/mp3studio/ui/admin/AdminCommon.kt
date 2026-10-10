package com.autoomstudio.mp3studio.ui.admin

import android.content.Context
import android.content.res.Resources
import android.text.format.DateUtils
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.autoomstudio.mp3studio.R
import com.autoomstudio.mp3studio.data.admin.AddAdminResult
import com.autoomstudio.mp3studio.data.admin.AdminBillingResult
import com.autoomstudio.mp3studio.data.admin.AdminError
import com.autoomstudio.mp3studio.data.plan.epochMillis
import java.time.LocalDate
import java.time.ZoneId

private val IST: ZoneId = ZoneId.of("Asia/Kolkata")

internal fun AdminError.text(resources: Resources): String = resources.getString(
    when (this) {
        AdminError.Forbidden -> R.string.admin_error_forbidden
        AdminError.LastAdmin -> R.string.admin_error_last_admin
        AdminError.Self -> R.string.admin_error_self
        AdminError.InvalidEmail -> R.string.admin_error_invalid_email
        AdminError.InvalidDate -> R.string.admin_error_invalid_date
        AdminError.NotFound -> R.string.admin_error_not_found
        AdminError.NotRefundable -> R.string.admin_error_not_refundable
        AdminError.NotSubscribed -> R.string.admin_error_not_subscribed
        AdminError.PayuRefused -> R.string.admin_error_payu_refused
        AdminError.PaymentsUnavailable -> R.string.admin_error_payments_unavailable
        AdminError.Offline -> R.string.admin_error_offline
        AdminError.Other -> R.string.admin_error_other
    },
)

internal fun AdminMessage.text(resources: Resources): String = when (this) {
    is AdminMessage.Failed -> error.text(resources)
    is AdminMessage.RoleChanged ->
        resources.getString(if (admin) R.string.admin_msg_role_admin else R.string.admin_msg_role_user, email)
    is AdminMessage.DisabledChanged ->
        resources.getString(if (disabled) R.string.admin_msg_disabled else R.string.admin_msg_enabled)
    AdminMessage.ProGranted -> resources.getString(R.string.admin_msg_pro_granted)
    AdminMessage.ProRemoved -> resources.getString(R.string.admin_msg_pro_removed)
    is AdminMessage.AdminAdded -> when (result) {
        AddAdminResult.Promoted -> resources.getString(R.string.admin_msg_role_admin, email)
        AddAdminResult.AlreadyAdmin -> resources.getString(R.string.admin_msg_already_admin, email)
        AddAdminResult.Invited -> resources.getString(R.string.admin_msg_invited, email)
    }
    AdminMessage.InviteRevoked -> resources.getString(R.string.admin_msg_invite_revoked)
    is AdminMessage.Billing -> resources.getString(
        when (result) {
            AdminBillingResult.Checked -> R.string.admin_msg_payment_checked
            AdminBillingResult.RefundRequested -> R.string.admin_msg_refund_requested
            AdminBillingResult.Cancelled -> R.string.admin_msg_subscription_cancelled
            AdminBillingResult.CancelPending -> R.string.admin_msg_cancel_pending
            AdminBillingResult.Revoked -> R.string.admin_msg_subscription_revoked
            AdminBillingResult.RevokePending -> R.string.admin_msg_revoke_pending
        },
    )
}

internal fun formatDate(context: Context, iso: String?): String = iso?.let {
    DateUtils.formatDateTime(context, epochMillis(it), DateUtils.FORMAT_SHOW_DATE or DateUtils.FORMAT_SHOW_YEAR)
}.orEmpty()

internal fun formatDateTime(context: Context, iso: String?): String = iso?.let {
    DateUtils.formatDateTime(
        context,
        epochMillis(it),
        DateUtils.FORMAT_SHOW_DATE or DateUtils.FORMAT_SHOW_TIME or DateUtils.FORMAT_ABBREV_MONTH,
    )
}.orEmpty()

/** "Week of 5 Oct" style label for a `yyyy-MM-dd` week start (Monday, India Standard Time). */
internal fun formatWeek(context: Context, weekStart: String): String {
    val millis = LocalDate.parse(weekStart).atStartOfDay(IST).toInstant().toEpochMilli()
    return DateUtils.formatDateTime(context, millis, DateUtils.FORMAT_SHOW_DATE or DateUtils.FORMAT_ABBREV_MONTH)
}

@Composable
internal fun planName(plan: String): String = stringResource(
    when (plan) {
        "pro" -> R.string.plan_pro
        "trial" -> R.string.plan_trial
        else -> R.string.plan_free
    },
)

@Composable
internal fun roleName(role: String): String =
    stringResource(if (role == "admin") R.string.admin_role_admin else R.string.admin_role_user)

/** A spinner before the first answer, the error with a retry button, or [content] once there is data. */
@Composable
internal fun <T> LoadableContent(state: Loadable<T>, onRetry: () -> Unit, content: @Composable (T) -> Unit) {
    val data = state.data
    when {
        data != null -> Column {
            if (state.loading) LinearProgressIndicator(Modifier.fillMaxWidth())
            content(data)
        }
        state.error != null -> ErrorBlock(state.error, onRetry)
        else -> Box(
            Modifier
                .fillMaxWidth()
                .padding(32.dp),
            contentAlignment = Alignment.Center,
        ) { CircularProgressIndicator() }
    }
}

@Composable
internal fun ErrorBlock(error: AdminError, onRetry: () -> Unit) {
    val resources = LocalResources.current
    Column(
        verticalArrangement = Arrangement.spacedBy(8.dp),
        modifier = Modifier
            .fillMaxWidth()
            .padding(16.dp),
    ) {
        Text(error.text(resources), color = MaterialTheme.colorScheme.error)
        OutlinedButton(onClick = onRetry) { Text(stringResource(R.string.admin_retry)) }
    }
}

/** Fixed accent hues for the dashboard icons; they read on both the light and the dark theme. */
internal object AdminColors {
    val Blue = Color(0xFF3B82F6)
    val Amber = Color(0xFFF59E0B)
    val Teal = Color(0xFF14B8A6)
    val Red = Color(0xFFEF4444)
    val Violet = Color(0xFF8B5CF6)
    val Green = Color(0xFF22C55E)
}

/** Rounded card with a faint primary outline, used across the dashboard and the Activity feed. */
@Composable
internal fun AdminCard(
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null,
    brush: Brush? = null,
    content: @Composable ColumnScope.() -> Unit,
) {
    val shape = RoundedCornerShape(20.dp)
    Column(
        modifier
            .clip(shape)
            .background(MaterialTheme.colorScheme.surfaceContainer)
            .then(if (brush != null) Modifier.background(brush) else Modifier)
            .border(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.12f), shape)
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier),
        content = content,
    )
}

@Composable
internal fun IconTile(icon: Painter, tint: Color, size: Dp = 40.dp) {
    Box(
        Modifier
            .size(size)
            .clip(RoundedCornerShape(size * 0.3f))
            .background(tint.copy(alpha = 0.14f)),
        contentAlignment = Alignment.Center,
    ) {
        Icon(icon, contentDescription = null, tint = tint, modifier = Modifier.size(size * 0.55f))
    }
}

@Composable
internal fun SectionTitle(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.titleMedium,
        modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 20.dp, bottom = 4.dp),
    )
}
