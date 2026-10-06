package com.autoomstudio.mplay.ui.trim

import android.content.ActivityNotFoundException
import android.content.Intent
import android.provider.Settings
import androidx.activity.compose.BackHandler
import androidx.annotation.StringRes
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Alarm
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.RingVolume
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.net.toUri
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.autoomstudio.mplay.R
import com.autoomstudio.mplay.data.clip.SoundType
import com.autoomstudio.mplay.ui.common.formatDuration
import com.autoomstudio.mplay.ui.common.formatPreciseDuration
import com.autoomstudio.mplay.ui.common.rememberWithLegacyStorage
import com.autoomstudio.mplay.ui.playlist.PlaylistNameDialog
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TrimEditorScreen(viewModel: TrimEditorViewModel, onClose: () -> Unit) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val playheadMs by viewModel.playheadMs.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val resources = LocalResources.current
    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()

    var showNameDialog by rememberSaveable { mutableStateOf(false) }
    var showSoundSheet by rememberSaveable { mutableStateOf(false) }
    var permissionPromptType by rememberSaveable { mutableStateOf<SoundType?>(null) }
    var awaitingPermissionType by rememberSaveable { mutableStateOf<SoundType?>(null) }

    val showSnackbar: (String, String?, () -> Unit) -> Unit = { text, action, onAction ->
        scope.launch {
            snackbarHostState.currentSnackbarData?.dismiss()
            val result = snackbarHostState.showSnackbar(
                message = text,
                actionLabel = action,
                duration = if (action != null) SnackbarDuration.Long else SnackbarDuration.Short,
            )
            if (result == SnackbarResult.ActionPerformed) onAction()
        }
    }
    val undo = resources.getString(R.string.action_undo)
    LaunchedEffect(viewModel) {
        viewModel.messages.collect { message ->
            when (message) {
                is TrimMessage.Saved -> showSnackbar(resources.getString(R.string.message_clip_saved), undo) {
                    viewModel.undoSave(message.uri)
                }
                is TrimMessage.SoundSet -> showSnackbar(resources.getString(message.type.setMessage), undo) {
                    viewModel.undoSound(message.type, message.previous)
                }
                is TrimMessage.SoundNotSet -> showSnackbar(
                    resources.getString(R.string.message_sound_not_set),
                    resources.getString(R.string.message_open_sound_settings),
                ) {
                    runCatching { context.startActivity(Intent(Settings.ACTION_SOUND_SETTINGS)) }
                }
                TrimMessage.SaveUndone -> showSnackbar(resources.getString(R.string.message_clip_deleted), null) {}
                TrimMessage.SoundUndone -> showSnackbar(resources.getString(R.string.message_sound_restored), null) {}
                is TrimMessage.Failed -> showSnackbar(resources.getString(message.error.message), null) {}
            }
        }
    }

    val withStorage = rememberWithLegacyStorage {
        showSnackbar(resources.getString(TrimError.PermissionDenied.message), null) {}
    }

    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) {
        awaitingPermissionType?.let { type ->
            awaitingPermissionType = null
            viewModel.setAsSound(type)
        }
    }
    LifecycleEventEffect(Lifecycle.Event.ON_STOP) { viewModel.stopPreview() }

    val exporting = state.export != null
    val back = { if (exporting) viewModel.cancelExport() else onClose() }
    BackHandler(onBack = back)

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(
                            text = stringResource(
                                if (state.mode == TrimMode.Ringtone) R.string.trim_title_ringtone else R.string.trim_title_cut,
                            ),
                            style = MaterialTheme.typography.titleLarge,
                        )
                        Text(
                            text = state.title,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                },
                navigationIcon = {
                    IconButton(onClick = back) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.action_back))
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background),
            )
        },
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 8.dp),
        ) {
            WaveformCard(state = state, playheadMs = playheadMs, viewModel = viewModel)
            Row(modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 4.dp)) {
                Text(formatDuration(0), style = MaterialTheme.typography.labelSmall)
                Spacer(Modifier.weight(1f))
                Text(formatDuration(state.durationMs), style = MaterialTheme.typography.labelSmall)
            }

            Spacer(Modifier.height(12.dp))
            NudgeRow(
                label = stringResource(R.string.trim_start),
                positionMs = state.range.startMs,
                enabled = state.canEdit,
                onNudge = viewModel::nudgeStart,
            )
            NudgeRow(
                label = stringResource(R.string.trim_end),
                positionMs = state.range.endMs,
                enabled = state.canEdit,
                onNudge = viewModel::nudgeEnd,
            )

            Spacer(Modifier.height(8.dp))
            Text(
                text = stringResource(R.string.trim_length, formatPreciseDuration(state.range.lengthMs)),
                style = MaterialTheme.typography.titleMedium,
            )
            if (state.mode == TrimMode.Ringtone && state.range.isLongForRingtone) {
                Text(
                    text = stringResource(R.string.trim_long_ringtone_warning),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.error,
                )
            }

            Spacer(Modifier.height(16.dp))
            FilledTonalButton(
                onClick = viewModel::togglePreview,
                enabled = state.canEdit,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Icon(
                    imageVector = if (state.isPreviewing) Icons.Filled.Stop else Icons.Filled.PlayArrow,
                    contentDescription = null,
                )
                Spacer(Modifier.width(8.dp))
                Text(stringResource(if (state.isPreviewing) R.string.trim_stop_preview else R.string.trim_preview))
            }

            Spacer(Modifier.height(24.dp))
            val export = state.export
            if (export != null) {
                ExportProgress(status = export, onCancel = viewModel::cancelExport)
            } else if (state.mode == TrimMode.Cut) {
                Button(
                    onClick = { showNameDialog = true },
                    enabled = state.canEdit,
                    modifier = Modifier.fillMaxWidth(),
                ) { Text(stringResource(R.string.trim_save_clip)) }
            } else {
                FilterChip(
                    selected = state.range.isFullTrack,
                    onClick = viewModel::toggleFullTrack,
                    enabled = state.canEdit,
                    label = { Text(stringResource(R.string.trim_use_full_track)) },
                    leadingIcon = if (state.range.isFullTrack) {
                        { Icon(Icons.Filled.Check, contentDescription = null, modifier = Modifier.size(18.dp)) }
                    } else {
                        null
                    },
                )
                Spacer(Modifier.height(8.dp))
                Button(
                    onClick = { showSoundSheet = true },
                    enabled = state.canEdit,
                    modifier = Modifier.fillMaxWidth(),
                ) { Text(stringResource(R.string.trim_set_as)) }
            }
            Spacer(Modifier.navigationBarsPadding())
        }
    }

    if (showNameDialog) {
        PlaylistNameDialog(
            title = stringResource(R.string.trim_clip_name_title),
            confirmLabel = stringResource(R.string.trim_save),
            label = stringResource(R.string.trim_clip_name_label),
            initialName = viewModel.defaultClipName,
            onConfirm = { name ->
                showNameDialog = false
                withStorage { viewModel.saveClip(name) }
            },
            onDismiss = { showNameDialog = false },
        )
    }

    if (showSoundSheet) {
        SoundTypeSheet(
            onPick = { type ->
                showSoundSheet = false
                withStorage {
                    if (viewModel.canWriteSettings()) viewModel.setAsSound(type) else permissionPromptType = type
                }
            },
            onDismiss = { showSoundSheet = false },
        )
    }

    permissionPromptType?.let { type ->
        AlertDialog(
            onDismissRequest = { permissionPromptType = null },
            title = { Text(stringResource(R.string.write_settings_title)) },
            text = { Text(stringResource(R.string.write_settings_message)) },
            confirmButton = {
                TextButton(
                    onClick = {
                        permissionPromptType = null
                        val intent = Intent(
                            Settings.ACTION_MANAGE_WRITE_SETTINGS,
                            "package:${context.packageName}".toUri(),
                        )
                        try {
                            context.startActivity(intent)
                            awaitingPermissionType = type
                        } catch (_: ActivityNotFoundException) {
                            viewModel.setAsSound(type)
                        }
                    },
                ) { Text(stringResource(R.string.write_settings_open)) }
            },
            dismissButton = {
                TextButton(
                    onClick = {
                        permissionPromptType = null
                        viewModel.setAsSound(type)
                    },
                ) { Text(stringResource(R.string.write_settings_not_now)) }
            },
        )
    }
}

@Composable
private fun WaveformCard(state: TrimEditorUiState, playheadMs: Long?, viewModel: TrimEditorViewModel) {
    Surface(
        color = MaterialTheme.colorScheme.surfaceContainer,
        shape = RoundedCornerShape(16.dp),
        modifier = Modifier
            .fillMaxWidth()
            .height(180.dp),
    ) {
        when (val waveform = state.waveform) {
            WaveformState.Failed -> Box(contentAlignment = Alignment.Center, modifier = Modifier.padding(24.dp)) {
                Text(
                    text = stringResource(R.string.trim_waveform_failed),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.error,
                    textAlign = TextAlign.Center,
                )
            }
            else -> {
                val loadingDescription = stringResource(R.string.trim_waveform_loading)
                WaveformView(
                    peaks = when (waveform) {
                        is WaveformState.Ready -> waveform.peaks
                        is WaveformState.Loading -> waveform.partial
                        WaveformState.Failed -> null
                    },
                    range = state.range,
                    playheadMs = playheadMs,
                    enabled = state.canEdit,
                    onMoveStart = viewModel::moveStart,
                    onMoveEnd = viewModel::moveEnd,
                    onMoveFinished = viewModel::onRangeChangeFinished,
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(vertical = 12.dp)
                        .then(
                            if (waveform is WaveformState.Loading) {
                                Modifier.semantics { contentDescription = loadingDescription }
                            } else {
                                Modifier
                            },
                        ),
                )
            }
        }
    }
}

@Composable
private fun NudgeRow(label: String, positionMs: Long, enabled: Boolean, onNudge: (forward: Boolean) -> Unit) {
    val backDescription = stringResource(R.string.trim_nudge_back, label)
    val forwardDescription = stringResource(R.string.trim_nudge_forward, label)
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text(text = label, style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
        OutlinedButton(
            onClick = { onNudge(false) },
            enabled = enabled,
            modifier = Modifier.semantics { contentDescription = backDescription },
        ) { Text(stringResource(R.string.trim_nudge_minus)) }
        Text(
            text = formatPreciseDuration(positionMs),
            style = MaterialTheme.typography.titleMedium.copy(fontFamily = FontFamily.Monospace),
            textAlign = TextAlign.Center,
            modifier = Modifier.width(72.dp),
        )
        OutlinedButton(
            onClick = { onNudge(true) },
            enabled = enabled,
            modifier = Modifier.semantics { contentDescription = forwardDescription },
        ) { Text(stringResource(R.string.trim_nudge_plus)) }
    }
}

@Composable
private fun ExportProgress(status: ExportStatus, onCancel: () -> Unit) {
    Column(modifier = Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        when (status) {
            is ExportStatus.Exporting -> {
                val progress = status.progress
                if (progress != null) {
                    Text(
                        text = stringResource(R.string.trim_exporting_percent, (progress * 100).toInt()),
                        style = MaterialTheme.typography.bodyLarge,
                    )
                    LinearProgressIndicator(progress = { progress }, modifier = Modifier.fillMaxWidth())
                } else {
                    Text(text = stringResource(R.string.trim_exporting), style = MaterialTheme.typography.bodyLarge)
                    LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                }
            }
            ExportStatus.Saving -> {
                Text(text = stringResource(R.string.trim_saving), style = MaterialTheme.typography.bodyLarge)
                LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
            }
        }
        TextButton(onClick = onCancel, modifier = Modifier.align(Alignment.End)) {
            Text(stringResource(R.string.trim_cancel_export))
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SoundTypeSheet(onPick: (SoundType) -> Unit, onDismiss: () -> Unit) {
    ModalBottomSheet(onDismissRequest = onDismiss, containerColor = MaterialTheme.colorScheme.surfaceContainerHigh) {
        Text(
            text = stringResource(R.string.sound_sheet_title),
            style = MaterialTheme.typography.titleLarge,
            modifier = Modifier.padding(horizontal = 24.dp, vertical = 8.dp),
        )
        SoundType.entries.forEach { type ->
            ListItem(
                headlineContent = { Text(stringResource(type.label)) },
                leadingContent = { Icon(type.icon, contentDescription = null) },
                colors = ListItemDefaults.colors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh),
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { onPick(type) }
                    .padding(horizontal = 8.dp),
            )
        }
        Spacer(Modifier.height(24.dp))
    }
}

private val SoundType.label: Int
    @StringRes get() = when (this) {
        SoundType.Ringtone -> R.string.sound_ringtone
        SoundType.Notification -> R.string.sound_notification
        SoundType.Alarm -> R.string.sound_alarm
    }

private val SoundType.icon: ImageVector
    get() = when (this) {
        SoundType.Ringtone -> Icons.Filled.RingVolume
        SoundType.Notification -> Icons.Filled.Notifications
        SoundType.Alarm -> Icons.Filled.Alarm
    }

private val SoundType.setMessage: Int
    @StringRes get() = when (this) {
        SoundType.Ringtone -> R.string.message_sound_set_ringtone
        SoundType.Notification -> R.string.message_sound_set_notification
        SoundType.Alarm -> R.string.message_sound_set_alarm
    }

private val TrimError.message: Int
    @StringRes get() = when (this) {
        TrimError.UnsupportedFile -> R.string.error_unsupported_file
        TrimError.NotEnoughStorage -> R.string.error_not_enough_storage
        TrimError.PermissionDenied -> R.string.error_permission_denied
        TrimError.ExportFailed -> R.string.error_export_failed
        TrimError.SaveFailed -> R.string.error_save_failed
    }
