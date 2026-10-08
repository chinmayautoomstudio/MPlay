package com.autoomstudio.mp3studio.ui.admin

import android.content.Context
import android.content.res.Resources
import android.text.format.DateUtils
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.autoomstudio.mp3studio.R
import com.autoomstudio.mp3studio.data.admin.AddAdminResult
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

@Composable
internal fun SectionTitle(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.titleMedium,
        modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 20.dp, bottom = 4.dp),
    )
}
