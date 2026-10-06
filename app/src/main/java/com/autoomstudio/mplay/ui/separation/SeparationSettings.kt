package com.autoomstudio.mplay.ui.separation

import android.text.format.Formatter
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.selection.toggleable
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import com.autoomstudio.mplay.R
import com.autoomstudio.mplay.separation.ModelState
import com.autoomstudio.mplay.separation.UnsupportedReason

/** The Settings section (AI23, AI24); on unsupported phones it only explains why (AI2). */
@Composable
fun SeparationSettingsSection(
    state: SeparationUiState,
    viewModel: SeparationViewModel,
    onOpenQueue: () -> Unit,
    sectionHeader: @Composable (String) -> Unit,
) {
    val hasContent = state.stemSets.isNotEmpty() || state.jobs.isNotEmpty()
    val context = LocalContext.current
    val listColors = ListItemDefaults.colors(containerColor = MaterialTheme.colorScheme.background)

    sectionHeader(stringResource(R.string.settings_section_separation))
    if (!state.deviceEligible) {
        UnsupportedItem(state.unsupportedReasons)
    } else if (state.modelState is ModelState.NotInstalled) {
        ModelMissingItem(viewModel)
    }
    if (state.available || hasContent) {
        ListItem(
            headlineContent = { Text(stringResource(R.string.settings_separation_queue)) },
            supportingContent = {
                Text(
                    if (state.cacheBytes > 0) {
                        stringResource(
                            R.string.settings_separation_cache,
                            Formatter.formatShortFileSize(context, state.cacheBytes),
                        )
                    } else {
                        stringResource(R.string.settings_separation_cache_empty)
                    },
                )
            },
            colors = listColors,
            modifier = Modifier.clickable(onClick = onOpenQueue),
        )
    }
    if (state.available) {
        SwitchItem(
            title = stringResource(R.string.settings_separation_charging),
            summary = stringResource(R.string.settings_separation_charging_summary),
            checked = state.settings.chargingOnly,
            onChange = viewModel::setChargingOnly,
        )
        SwitchItem(
            title = stringResource(R.string.settings_separation_battery),
            summary = stringResource(R.string.settings_separation_battery_summary),
            checked = state.settings.pauseOnLowBattery,
            onChange = viewModel::setPauseOnLowBattery,
        )
    }
    if (state.cacheBytes > 0) {
        var confirming by rememberSaveable { mutableStateOf(false) }
        ListItem(
            headlineContent = {
                Text(stringResource(R.string.settings_separation_delete_all), color = MaterialTheme.colorScheme.error)
            },
            colors = listColors,
            modifier = Modifier.clickable { confirming = true },
        )
        if (confirming) {
            AlertDialog(
                onDismissRequest = { confirming = false },
                title = { Text(stringResource(R.string.settings_separation_delete_all_title)) },
                text = { Text(stringResource(R.string.settings_separation_delete_all_message)) },
                confirmButton = {
                    TextButton(
                        onClick = {
                            confirming = false
                            viewModel.deleteAllStems()
                        },
                    ) { Text(stringResource(R.string.settings_separation_delete_confirm)) }
                },
                dismissButton = {
                    TextButton(onClick = { confirming = false }) {
                        Text(stringResource(R.string.separation_notice_cancel))
                    }
                },
            )
        }
    }
}

@Composable
private fun UnsupportedItem(reasons: List<UnsupportedReason>) {
    ListItem(
        headlineContent = { Text(stringResource(R.string.settings_separation_unsupported_title)) },
        supportingContent = {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                reasons.forEach { reason ->
                    Text(
                        stringResource(
                            when (reason) {
                                UnsupportedReason.Architecture -> R.string.settings_separation_reason_architecture
                                UnsupportedReason.Memory -> R.string.settings_separation_reason_memory
                                UnsupportedReason.Storage -> R.string.settings_separation_reason_storage
                            },
                        ),
                    )
                }
            }
        },
        colors = ListItemDefaults.colors(containerColor = MaterialTheme.colorScheme.background),
    )
}

/** Only reachable in builds made without the bundled model. */
@Composable
private fun ModelMissingItem(viewModel: SeparationViewModel) {
    val picker = rememberModelPicker(viewModel)
    ListItem(
        headlineContent = { Text(stringResource(R.string.settings_separation_model_missing_title)) },
        supportingContent = {
            Column {
                Text(stringResource(R.string.settings_separation_reason_model))
                OutlinedButton(
                    onClick = { picker.launch(MODEL_MIME_TYPES) },
                    modifier = Modifier.padding(top = 8.dp),
                ) { Text(stringResource(R.string.settings_separation_import_model)) }
            }
        },
        colors = ListItemDefaults.colors(containerColor = MaterialTheme.colorScheme.background),
    )
}

@Composable
private fun rememberModelPicker(viewModel: SeparationViewModel) =
    rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        uri?.let(viewModel::importModel)
    }

private val MODEL_MIME_TYPES = arrayOf("application/octet-stream", "*/*")

/**
 * Shown when the separator is tapped but the model isn't installed. Bundled builds only get here when built without
 * the model; the on-demand download will offer to fetch the model from here instead.
 */
@Composable
fun ModelMissingDialog(state: ModelState.NotInstalled, viewModel: SeparationViewModel) {
    val context = LocalContext.current
    val picker = rememberModelPicker(viewModel)
    AlertDialog(
        onDismissRequest = viewModel::dismissModelRequest,
        title = { Text(stringResource(R.string.model_missing_title)) },
        text = {
            Text(
                stringResource(
                    R.string.model_missing_message,
                    Formatter.formatShortFileSize(context, state.sizeBytes),
                ),
            )
        },
        confirmButton = {
            TextButton(onClick = { picker.launch(MODEL_MIME_TYPES) }) {
                Text(stringResource(R.string.settings_separation_import_model))
            }
        },
        dismissButton = {
            TextButton(onClick = viewModel::dismissModelRequest) {
                Text(stringResource(R.string.separation_notice_cancel))
            }
        },
    )
}

@Composable
private fun SwitchItem(title: String, summary: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    ListItem(
        headlineContent = { Text(title) },
        supportingContent = { Text(summary) },
        trailingContent = { Switch(checked = checked, onCheckedChange = null) },
        colors = ListItemDefaults.colors(containerColor = MaterialTheme.colorScheme.background),
        modifier = Modifier.toggleable(value = checked, role = Role.Switch, onValueChange = onChange),
    )
}

/** The one-time notice before the first separation (AI4), with the personal-use note. */
@Composable
fun SeparationNoticeDialog(onConfirm: () -> Unit, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.separation_notice_title)) },
        text = { Text(stringResource(R.string.separation_notice_message)) },
        confirmButton = {
            TextButton(onClick = onConfirm) { Text(stringResource(R.string.separation_notice_confirm)) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.separation_notice_cancel)) }
        },
    )
}

/** Snackbar text for [message]. */
fun separationMessageText(context: android.content.Context, message: SeparationMessage): String {
    val res = context.resources
    return when (message) {
        is SeparationMessage.Queued ->
            res.getQuantityString(R.plurals.message_separation_queued, message.count, message.count)
        SeparationMessage.NothingNew -> res.getString(R.string.message_separation_nothing_new)
        is SeparationMessage.NotEnoughStorage -> res.getString(
            R.string.message_separation_no_storage,
            Formatter.formatShortFileSize(context, message.requiredBytes),
        )
        SeparationMessage.Unavailable -> res.getString(R.string.message_separation_unavailable)
        SeparationMessage.StemsDeleted -> res.getString(R.string.message_stems_deleted)
        SeparationMessage.Exported -> res.getString(R.string.message_stem_exported)
        SeparationMessage.ExportFailed -> res.getString(R.string.message_stem_export_failed)
        SeparationMessage.ExportPermissionDenied -> res.getString(R.string.message_stem_export_permission)
        SeparationMessage.ModelImported -> res.getString(R.string.message_model_imported)
        SeparationMessage.ModelImportFailed -> res.getString(R.string.message_model_import_failed)
        SeparationMessage.ModelImportWrongFile -> res.getString(R.string.message_model_import_wrong_file)
        is SeparationMessage.Ready -> res.getString(R.string.message_separation_ready, message.title)
        is SeparationMessage.Failed ->
            res.getString(R.string.message_separation_failed, message.title, res.getString(errorTextRes(message.error)))
    }
}
