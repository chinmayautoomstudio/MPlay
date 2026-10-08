package com.autoomstudio.mp3studio.ui.separation

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Schedule
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import com.autoomstudio.mp3studio.R
import com.autoomstudio.mp3studio.data.plan.SeparatorUsage
import com.autoomstudio.mp3studio.ui.plans.resetDay

/** The short "may take a while" line, shared by every separation progress surface (SN2, SN5). */
@Composable
fun SeparationTimeNotice(modifier: Modifier = Modifier) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        modifier = modifier,
    ) {
        Icon(
            imageVector = Icons.Outlined.Schedule,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(16.dp),
        )
        Text(
            text = stringResource(R.string.separation_time_notice),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

/** Shown before every separation until "Don't show again" (SN1), with this phone's estimate when known (SN3). */
@Composable
fun SeparationNoticeDialog(
    request: NoticeRequest,
    onConfirm: (dontShowAgain: Boolean) -> Unit,
    onDismiss: () -> Unit,
    /** Free users' uses this week; null on Trial and Pro. */
    usage: SeparatorUsage? = null,
) {
    var dontShowAgain by rememberSaveable { mutableStateOf(false) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.separation_notice_title)) },
        text = {
            Column(
                verticalArrangement = Arrangement.spacedBy(12.dp),
                modifier = Modifier.verticalScroll(rememberScrollState()),
            ) {
                request.estimateMinutes?.let { minutes ->
                    Text(
                        text = pluralStringResource(
                            if (request.songs.size == 1) {
                                R.plurals.separation_notice_estimate
                            } else {
                                R.plurals.separation_notice_estimate_many
                            },
                            minutes,
                            minutes,
                        ),
                        style = MaterialTheme.typography.titleSmall,
                        color = MaterialTheme.colorScheme.primary,
                    )
                }
                Text(stringResource(R.string.separation_notice_message))
                if (usage != null) {
                    Text(
                        text = if (usage.remaining > 0) {
                            pluralStringResource(
                                R.plurals.separation_notice_usage,
                                usage.remaining,
                                minOf(request.songs.size, usage.remaining),
                                usage.remaining,
                            )
                        } else {
                            stringResource(R.string.upgrade_limit_resets, usage.limit, resetDay(usage.resetsAt))
                        },
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.primary,
                    )
                }
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .fillMaxWidth()
                        .toggleable(value = dontShowAgain, role = Role.Checkbox) { dontShowAgain = it },
                ) {
                    Checkbox(checked = dontShowAgain, onCheckedChange = null)
                    Text(
                        text = stringResource(R.string.separation_notice_dont_show),
                        modifier = Modifier.padding(start = 12.dp),
                    )
                }
            }
        },
        confirmButton = {
            TextButton(onClick = { onConfirm(dontShowAgain) }) {
                Text(stringResource(R.string.separation_notice_confirm))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.separation_notice_cancel)) }
        },
    )
}
