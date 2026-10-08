package com.autoomstudio.mp3studio.ui.admin

import android.content.Context
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.PersonAdd
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.autoomstudio.mp3studio.R
import com.autoomstudio.mp3studio.data.admin.AuditEntry
import com.autoomstudio.mp3studio.ui.library.DetailBackButton
import kotlinx.serialization.json.JsonPrimitive

@Composable
private fun PageTitle(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.headlineSmall,
        modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
    )
}

/** Current Admins, pending invites and "Add admin" by email. */
@Composable
internal fun AdminAdminsScreen(viewModel: AdminViewModel, onBack: () -> Unit, modifier: Modifier) {
    val admins by viewModel.admins.collectAsStateWithLifecycle()
    val busy by viewModel.busy.collectAsStateWithLifecycle()
    val context = LocalContext.current
    var adding by rememberSaveable { mutableStateOf(false) }

    LazyColumn(modifier) {
        item { DetailBackButton(onBack = onBack) }
        item { PageTitle(stringResource(R.string.admin_overview_admins)) }
        item {
            Button(
                onClick = { adding = true },
                enabled = !busy,
                modifier = Modifier.padding(horizontal = 16.dp),
            ) {
                Icon(Icons.Outlined.PersonAdd, contentDescription = null)
                Text(stringResource(R.string.admin_add_admin), modifier = Modifier.padding(start = 8.dp))
            }
        }
        item {
            LoadableContent(admins, onRetry = viewModel::reload) { list ->
                Column {
                    SectionTitle(stringResource(R.string.admin_current_admins))
                    list.admins.forEach { admin ->
                        ListItem(
                            headlineContent = {
                                Text(
                                    (admin.name ?: admin.email.orEmpty()) +
                                        if (admin.id == viewModel.me) " ${stringResource(R.string.admin_you)}" else "",
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                )
                            },
                            supportingContent = admin.email?.let { { Text(it) } },
                            trailingContent = if (admin.disabled) {
                                { Badge(stringResource(R.string.admin_badge_disabled)) }
                            } else {
                                null
                            },
                            colors = ListItemDefaults.colors(containerColor = MaterialTheme.colorScheme.background),
                            modifier = Modifier.clickable { viewModel.open(AdminPage.User(admin.id)) },
                        )
                    }
                    SectionTitle(stringResource(R.string.admin_invites))
                    if (list.invites.isEmpty()) {
                        Text(stringResource(R.string.admin_no_invites), modifier = Modifier.padding(horizontal = 16.dp))
                    }
                    list.invites.forEach { invite ->
                        ListItem(
                            headlineContent = { Text(invite.email) },
                            supportingContent = {
                                Text(
                                    stringResource(
                                        R.string.admin_invite_row,
                                        invite.invitedBy ?: stringResource(R.string.admin_unknown_user),
                                        formatDate(context, invite.createdAt),
                                    ),
                                )
                            },
                            trailingContent = {
                                TextButton(onClick = { viewModel.revokeInvite(invite.email) }, enabled = !busy) {
                                    Text(stringResource(R.string.admin_revoke))
                                }
                            },
                            colors = ListItemDefaults.colors(containerColor = MaterialTheme.colorScheme.background),
                        )
                    }
                }
            }
        }
    }

    if (adding) {
        AddAdminDialog(
            busy = busy,
            onAdd = { email -> viewModel.addAdmin(email) { added -> if (added) adding = false } },
            onDismiss = { adding = false },
        )
    }
}

@Composable
private fun AddAdminDialog(busy: Boolean, onAdd: (String) -> Unit, onDismiss: () -> Unit) {
    var email by rememberSaveable { mutableStateOf("") }
    val valid = Regex("^[^@\\s]+@[^@\\s]+\\.[^@\\s]+$").matches(email.trim())
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.admin_add_admin)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(stringResource(R.string.admin_add_admin_message))
                OutlinedTextField(
                    value = email,
                    onValueChange = { email = it.take(254) },
                    label = { Text(stringResource(R.string.admin_email_label)) },
                    singleLine = true,
                    enabled = !busy,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email),
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        },
        confirmButton = {
            TextButton(onClick = { onAdd(email.trim()) }, enabled = valid && !busy) {
                Text(stringResource(R.string.admin_add_admin_confirm))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss, enabled = !busy) { Text(stringResource(android.R.string.cancel)) }
        },
    )
}

/** AI Vocal Separator use per week and this week's top users (PRD AD5). */
@Composable
internal fun AdminUsageScreen(viewModel: AdminViewModel, onBack: () -> Unit, modifier: Modifier) {
    val usage by viewModel.usage.collectAsStateWithLifecycle()
    val context = LocalContext.current

    LazyColumn(modifier) {
        item { DetailBackButton(onBack = onBack) }
        item { PageTitle(stringResource(R.string.admin_open_usage)) }
        item {
            LoadableContent(usage, onRetry = viewModel::reload) { data ->
                Column {
                    SectionTitle(stringResource(R.string.admin_section_weeks))
                    data.weeks.forEach { week ->
                        ListItem(
                            headlineContent = { Text(formatWeek(context, week.weekStart)) },
                            supportingContent = {
                                Text(
                                    pluralStringResource(
                                        R.plurals.admin_usage_week,
                                        week.users,
                                        week.completed,
                                        week.users,
                                        week.released,
                                        week.denied,
                                    ),
                                )
                            },
                            colors = ListItemDefaults.colors(containerColor = MaterialTheme.colorScheme.background),
                        )
                    }
                    SectionTitle(stringResource(R.string.admin_top_users))
                    if (data.top.isEmpty()) {
                        Text(stringResource(R.string.admin_no_usage), modifier = Modifier.padding(horizontal = 16.dp))
                    }
                    data.top.forEach { user ->
                        ListItem(
                            headlineContent = {
                                Text(user.name ?: user.email.orEmpty(), maxLines = 1, overflow = TextOverflow.Ellipsis)
                            },
                            supportingContent = user.email?.let { { Text(it) } },
                            trailingContent = {
                                Text(pluralStringResource(R.plurals.admin_top_row, user.completed, user.completed))
                            },
                            colors = ListItemDefaults.colors(containerColor = MaterialTheme.colorScheme.background),
                            modifier = Modifier.clickable { viewModel.open(AdminPage.User(user.id)) },
                        )
                    }
                }
            }
        }
    }
}

/** Every Admin action, newest first (PRD AD9). */
@Composable
internal fun AdminAuditScreen(viewModel: AdminViewModel, onBack: () -> Unit, modifier: Modifier) {
    val audit by viewModel.audit.collectAsStateWithLifecycle()
    val context = LocalContext.current

    LazyColumn(modifier) {
        item { DetailBackButton(onBack = onBack) }
        item { PageTitle(stringResource(R.string.admin_open_audit)) }
        items(audit.entries, key = { it.id }) { entry ->
            ListItem(
                headlineContent = { Text(auditText(context, entry)) },
                supportingContent = {
                    Text(
                        stringResource(
                            R.string.admin_audit_by,
                            entry.actorEmail ?: stringResource(R.string.admin_unknown_user),
                            formatDateTime(context, entry.createdAt),
                        ),
                    )
                },
                colors = ListItemDefaults.colors(containerColor = MaterialTheme.colorScheme.background),
                modifier = entry.targetId?.let { id -> Modifier.clickable { viewModel.open(AdminPage.User(id)) } }
                    ?: Modifier,
            )
        }
        item {
            when {
                audit.error != null -> ErrorBlock(audit.error!!) { viewModel.loadAudit(more = audit.entries.isNotEmpty()) }
                audit.loading -> Row(Modifier.padding(16.dp)) { CircularProgressIndicator() }
                audit.entries.isEmpty() -> Text(stringResource(R.string.admin_no_audit), modifier = Modifier.padding(16.dp))
                audit.hasMore -> OutlinedButton(
                    onClick = { viewModel.loadAudit(more = true) },
                    modifier = Modifier.padding(16.dp),
                ) { Text(stringResource(R.string.admin_load_more)) }
            }
        }
    }
}

private fun auditText(context: Context, entry: AuditEntry): String {
    val res = context.resources
    fun detail(key: String) = (entry.details?.get(key) as? JsonPrimitive)?.content
    val target = entry.targetEmail ?: detail("email") ?: res.getString(R.string.admin_unknown_user)
    return when (entry.action) {
        "set_role" -> res.getString(
            R.string.admin_audit_set_role,
            target,
            res.getString(if (detail("to") == "admin") R.string.admin_role_admin else R.string.admin_role_user),
        )
        "disable" -> res.getString(R.string.admin_audit_disable, target)
        "enable" -> res.getString(R.string.admin_audit_enable, target)
        "invite_admin" -> res.getString(R.string.admin_audit_invite, target)
        "invite_accepted" -> res.getString(R.string.admin_audit_invite_accepted, target)
        "revoke_invite" -> res.getString(R.string.admin_audit_revoke_invite, target)
        "grant_pro" -> res.getString(R.string.admin_audit_grant_pro, target, formatDate(context, detail("until")))
        "revoke_pro" -> res.getString(R.string.admin_audit_revoke_pro, target)
        else -> res.getString(R.string.admin_audit_other, entry.action, target)
    }
}
