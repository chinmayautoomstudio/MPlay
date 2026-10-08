package com.autoomstudio.mp3studio.ui.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.automirrored.outlined.Logout
import androidx.compose.material.icons.outlined.AdminPanelSettings
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.Person
import androidx.compose.material.icons.outlined.WorkspacePremium
import androidx.compose.material3.AlertDialog
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
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import coil3.compose.SubcomposeAsyncImage
import com.autoomstudio.mp3studio.R
import com.autoomstudio.mp3studio.data.account.ProfileRepository
import com.autoomstudio.mp3studio.ui.plans.PlansViewModel
import com.autoomstudio.mp3studio.ui.plans.planLabel

/** The signed-in Google account at the top of Settings: photo, name, email, rename and log out (PRD AU5, AU6). */
@Composable
fun AccountSection(
    sectionHeader: @Composable (String) -> Unit,
    onOpenPlans: () -> Unit,
    onOpenAdmin: () -> Unit,
    viewModel: AccountViewModel = viewModel(factory = AccountViewModel.Factory),
    plansViewModel: PlansViewModel = viewModel(factory = PlansViewModel.Factory),
) {
    val account by viewModel.account.collectAsStateWithLifecycle()
    val plan by plansViewModel.state.collectAsStateWithLifecycle()
    val savingName by viewModel.savingName.collectAsStateWithLifecycle()
    var editingName by rememberSaveable { mutableStateOf(false) }
    var confirmingLogOut by rememberSaveable { mutableStateOf(false) }
    val current = account ?: return

    sectionHeader(stringResource(R.string.account_section))
    ListItem(
        leadingContent = { Avatar(current.avatarUrl) },
        headlineContent = { Text(current.name, maxLines = 1, overflow = TextOverflow.Ellipsis) },
        supportingContent = { Text(current.email, maxLines = 1, overflow = TextOverflow.Ellipsis) },
        colors = ListItemDefaults.colors(containerColor = MaterialTheme.colorScheme.background),
    )
    ListItem(
        leadingContent = { Icon(Icons.Outlined.WorkspacePremium, contentDescription = null) },
        headlineContent = { Text(stringResource(R.string.plan_row_label)) },
        supportingContent = { Text(planLabel(plan)) },
        trailingContent = { Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = null) },
        colors = ListItemDefaults.colors(containerColor = MaterialTheme.colorScheme.background),
        modifier = Modifier.clickable(onClick = onOpenPlans),
    )
    if (plan.isAdmin) {
        ListItem(
            leadingContent = { Icon(Icons.Outlined.AdminPanelSettings, contentDescription = null) },
            headlineContent = { Text(stringResource(R.string.admin_title)) },
            supportingContent = { Text(stringResource(R.string.admin_row_summary)) },
            trailingContent = { Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = null) },
            colors = ListItemDefaults.colors(containerColor = MaterialTheme.colorScheme.background),
            modifier = Modifier.clickable(onClick = onOpenAdmin),
        )
    }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        OutlinedButton(onClick = { editingName = true }) {
            Icon(Icons.Outlined.Edit, contentDescription = null, modifier = Modifier.size(18.dp))
            Text(stringResource(R.string.account_edit_name), modifier = Modifier.padding(start = 8.dp))
        }
        OutlinedButton(onClick = { confirmingLogOut = true }) {
            Icon(Icons.AutoMirrored.Outlined.Logout, contentDescription = null, modifier = Modifier.size(18.dp))
            Text(stringResource(R.string.account_log_out), modifier = Modifier.padding(start = 8.dp))
        }
    }

    if (editingName) {
        EditNameDialog(
            initial = current.name,
            saving = savingName,
            onSave = { name, onFailed ->
                viewModel.saveName(name) { saved -> if (saved) editingName = false else onFailed() }
            },
            onDismiss = { editingName = false },
        )
    }
    if (confirmingLogOut) {
        AlertDialog(
            onDismissRequest = { confirmingLogOut = false },
            title = { Text(stringResource(R.string.account_log_out_title)) },
            text = { Text(stringResource(R.string.account_log_out_message)) },
            confirmButton = {
                TextButton(onClick = {
                    confirmingLogOut = false
                    viewModel.signOut()
                }) { Text(stringResource(R.string.account_log_out_confirm)) }
            },
            dismissButton = {
                TextButton(onClick = { confirmingLogOut = false }) { Text(stringResource(android.R.string.cancel)) }
            },
        )
    }
}

@Composable
private fun Avatar(url: String?) {
    val modifier = Modifier
        .size(48.dp)
        .clip(CircleShape)
        .background(MaterialTheme.colorScheme.surfaceVariant)
    val placeholder = @Composable {
        Box(modifier, contentAlignment = Alignment.Center) {
            Icon(Icons.Outlined.Person, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
    if (url.isNullOrBlank()) {
        placeholder()
        return
    }
    SubcomposeAsyncImage(
        model = url,
        contentDescription = stringResource(R.string.account_photo_description),
        contentScale = ContentScale.Crop,
        modifier = modifier,
        loading = { placeholder() },
        error = { placeholder() },
    )
}

@Composable
private fun EditNameDialog(
    initial: String,
    saving: Boolean,
    onSave: (name: String, onFailed: () -> Unit) -> Unit,
    onDismiss: () -> Unit,
) {
    var name by rememberSaveable { mutableStateOf(initial) }
    var failed by remember { mutableStateOf(false) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.account_edit_name)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = name,
                    onValueChange = {
                        name = it.take(ProfileRepository.MAX_NAME_LENGTH)
                        failed = false
                    },
                    label = { Text(stringResource(R.string.account_name_label)) },
                    singleLine = true,
                    enabled = !saving,
                    keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Words),
                    modifier = Modifier.fillMaxWidth(),
                )
                if (failed) {
                    Text(
                        stringResource(R.string.account_name_failed),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error,
                    )
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = { onSave(name) { failed = true } },
                enabled = !saving && name.isNotBlank() && name.trim() != initial,
            ) { Text(stringResource(R.string.account_name_save)) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss, enabled = !saving) { Text(stringResource(android.R.string.cancel)) }
        },
    )
}
