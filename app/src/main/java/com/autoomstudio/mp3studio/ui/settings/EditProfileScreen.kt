package com.autoomstudio.mp3studio.ui.settings

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.DeleteForever
import androidx.compose.material.icons.outlined.Person
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import coil3.compose.SubcomposeAsyncImage
import com.autoomstudio.mp3studio.R
import com.autoomstudio.mp3studio.data.account.DeletionError
import com.autoomstudio.mp3studio.data.account.ProfileRepository

/** Opened from Edit on the Profile screen: photo, rename and delete account (PRD AU5, AU9). */
@Composable
fun EditProfileScreen(
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    backEnabled: Boolean = true,
    viewModel: AccountViewModel = viewModel(factory = AccountViewModel.Factory),
) {
    BackHandler(enabled = backEnabled, onBack = onBack)
    val account by viewModel.account.collectAsStateWithLifecycle()
    val savingName by viewModel.savingName.collectAsStateWithLifecycle()
    val deleting by viewModel.deleting.collectAsStateWithLifecycle()
    val deleteError by viewModel.deleteError.collectAsStateWithLifecycle()
    var confirmingDelete by rememberSaveable { mutableStateOf(false) }
    val current = account

    Column(modifier.verticalScroll(rememberScrollState())) {
        SubPageHeader(title = stringResource(R.string.profile_edit_title), onBack = onBack)
        if (current == null) return@Column
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 8.dp),
        ) {
            Avatar(current.avatarUrl, size = 104.dp)
            Text(
                current.email,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        NameEditor(
            initial = current.name,
            saving = savingName,
            onSave = { name, onResult -> viewModel.saveName(name, onResult) },
        )
        Spacer(Modifier.height(24.dp))
        TextButton(
            onClick = {
                viewModel.clearDeleteError()
                confirmingDelete = true
            },
            colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error),
            modifier = Modifier.padding(horizontal = 12.dp),
        ) {
            Icon(Icons.Outlined.DeleteForever, contentDescription = null, modifier = Modifier.size(18.dp))
            Text(stringResource(R.string.account_delete), modifier = Modifier.padding(start = 8.dp))
        }
        Spacer(Modifier.height(16.dp))
    }

    if (confirmingDelete) {
        DeleteAccountDialog(
            deleting = deleting,
            error = deleteError,
            onDelete = viewModel::deleteAccount,
            onDismiss = { confirmingDelete = false },
        )
    }
}

@Composable
private fun NameEditor(
    initial: String,
    saving: Boolean,
    onSave: (name: String, onResult: (Boolean) -> Unit) -> Unit,
) {
    var name by rememberSaveable(initial) { mutableStateOf(initial) }
    var failed by remember { mutableStateOf(false) }
    Column(
        verticalArrangement = Arrangement.spacedBy(8.dp),
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp, vertical = 8.dp),
    ) {
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
        if (saving) LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
        Button(
            onClick = { onSave(name) { saved -> failed = !saved } },
            enabled = !saving && name.isNotBlank() && name.trim() != initial,
            modifier = Modifier.align(Alignment.End),
        ) { Text(stringResource(R.string.account_name_save)) }
    }
}

/** Round account photo; a person icon while it loads, on error, or when the account has none. */
@Composable
internal fun Avatar(url: String?, size: Dp, modifier: Modifier = Modifier) {
    val shape = modifier
        .size(size)
        .clip(CircleShape)
        .background(MaterialTheme.colorScheme.surfaceVariant)
    val placeholder = @Composable {
        Box(shape, contentAlignment = Alignment.Center) {
            Icon(
                Icons.Outlined.Person,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(size * 0.55f),
            )
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
        modifier = shape,
        loading = { placeholder() },
        error = { placeholder() },
    )
}

@Composable
internal fun LogOutDialog(onConfirm: () -> Unit, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.account_log_out_title)) },
        text = { Text(stringResource(R.string.account_log_out_message)) },
        confirmButton = {
            TextButton(onClick = onConfirm) { Text(stringResource(R.string.account_log_out_confirm)) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(android.R.string.cancel)) }
        },
    )
}

@Composable
private fun DeleteAccountDialog(
    deleting: Boolean,
    error: DeletionError?,
    onDelete: () -> Unit,
    onDismiss: () -> Unit,
) {
    val word = stringResource(R.string.account_delete_word)
    var typed by rememberSaveable { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = { if (!deleting) onDismiss() },
        title = { Text(stringResource(R.string.account_delete_title)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(stringResource(R.string.account_delete_message))
                OutlinedTextField(
                    value = typed,
                    onValueChange = { typed = it.take(word.length + 4) },
                    label = { Text(stringResource(R.string.account_delete_type_label)) },
                    singleLine = true,
                    enabled = !deleting,
                    keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Characters),
                    modifier = Modifier.fillMaxWidth(),
                )
                if (deleting) LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                if (error != null) {
                    Text(
                        stringResource(
                            when (error) {
                                DeletionError.LastAdmin -> R.string.account_delete_last_admin
                                DeletionError.MandateCancelFailed -> R.string.account_delete_mandate
                                DeletionError.Offline -> R.string.account_delete_offline
                                DeletionError.Other -> R.string.account_delete_failed
                            },
                        ),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error,
                    )
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = onDelete,
                enabled = !deleting && typed.trim() == word,
                colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error),
            ) { Text(stringResource(R.string.account_delete_confirm)) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss, enabled = !deleting) { Text(stringResource(android.R.string.cancel)) }
        },
    )
}
