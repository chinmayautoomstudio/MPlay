package com.autoomstudio.mp3studio.ui.equalizer

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.GraphicEq
import androidx.compose.material.icons.filled.Headphones
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.Piano
import androidx.compose.material.icons.filled.Speaker
import androidx.compose.material.icons.filled.SurroundSound
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material.icons.filled.ViewInAr
import androidx.compose.material.icons.filled.Waves
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
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
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.autoomstudio.mp3studio.R
import com.autoomstudio.mp3studio.data.equalizer.EqualizerRepository
import com.autoomstudio.mp3studio.playback.equalizer.BuiltInPreset
import com.autoomstudio.mp3studio.playback.equalizer.EqualizerBands
import com.autoomstudio.mp3studio.playback.equalizer.EqualizerPresetIds
import com.autoomstudio.mp3studio.playback.equalizer.EqualizerState

@Composable
fun EqualizerScreen(
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    backEnabled: Boolean = true,
    viewModel: EqualizerViewModel = viewModel(factory = EqualizerViewModel.Factory),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val lofiEnabled by viewModel.lofiEnabled.collectAsStateWithLifecycle()
    val message by viewModel.message.collectAsStateWithLifecycle()
    var showSaveDialog by rememberSaveable { mutableStateOf(false) }
    var renameTarget by rememberSaveable { mutableStateOf<String?>(null) }

    BackHandler(enabled = backEnabled, onBack = onBack)

    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp)
            .padding(bottom = 24.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        EqualizerHeader(
            enabled = state.enabled,
            onEnabledChange = viewModel::setEnabled,
            onBack = onBack,
        )

        if (lofiEnabled) {
            Text(
                text = stringResource(R.string.equalizer_lofi_note),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.padding(horizontal = 4.dp),
            )
        }

        PresetDropdownCard(
            state = state,
            enabled = state.enabled,
            onSelectBuiltIn = viewModel::selectBuiltIn,
            onSelectCustom = viewModel::selectCustom,
            onSelectSaved = viewModel::selectSaved,
            onSave = { showSaveDialog = true },
            onReset = viewModel::reset,
            onRename = { renameTarget = it },
            onDelete = viewModel::deleteSavedPreset,
        )

        BandSlidersCard(
            gainsDb = state.gainsDb,
            enabled = state.enabled,
            onGainChange = viewModel::setBandGain,
        )

        PresetGrid(
            selectedId = state.presetId,
            enabled = state.enabled,
            onSelectCustom = viewModel::selectCustom,
            onSelectBuiltIn = viewModel::selectBuiltIn,
        )

        PreampCard(
            valueDb = state.preampDb,
            enabled = state.enabled,
            onValueChange = viewModel::setPreamp,
        )

        EffectsCard(
            bassBoost = state.bassBoost,
            virtualizer = state.virtualizer,
            loudness = state.loudness,
            enabled = state.enabled,
            onBassBoost = viewModel::setBassBoost,
            onVirtualizer = viewModel::setVirtualizer,
            onLoudness = viewModel::setLoudness,
        )
    }

    if (showSaveDialog) {
        SavePresetDialog(
            onDismiss = { showSaveDialog = false },
            onConfirm = { name ->
                viewModel.saveCurrentAsPreset(name)
                showSaveDialog = false
            },
        )
    }
    renameTarget?.let { oldName ->
        SavePresetDialog(
            title = stringResource(R.string.equalizer_rename_preset),
            initial = oldName,
            onDismiss = { renameTarget = null },
            onConfirm = { name ->
                viewModel.renameSavedPreset(oldName, name)
                renameTarget = null
            },
        )
    }
    message?.let { text ->
        AlertDialog(
            onDismissRequest = viewModel::clearMessage,
            confirmButton = {
                TextButton(onClick = viewModel::clearMessage) {
                    Text(stringResource(android.R.string.ok))
                }
            },
            text = { Text(text) },
        )
    }
}

@Composable
private fun EqualizerHeader(
    enabled: Boolean,
    onEnabledChange: (Boolean) -> Unit,
    onBack: () -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        IconButton(onClick = onBack) {
            Icon(
                Icons.AutoMirrored.Filled.ArrowBack,
                contentDescription = stringResource(R.string.action_back),
            )
        }
        Text(
            text = stringResource(R.string.equalizer_title),
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.SemiBold,
            textAlign = TextAlign.Center,
            modifier = Modifier.weight(1f),
        )
        Switch(checked = enabled, onCheckedChange = onEnabledChange)
    }
}

@Composable
private fun PresetDropdownCard(
    state: EqualizerState,
    enabled: Boolean,
    onSelectBuiltIn: (BuiltInPreset) -> Unit,
    onSelectCustom: () -> Unit,
    onSelectSaved: (String) -> Unit,
    onSave: () -> Unit,
    onReset: () -> Unit,
    onRename: (String) -> Unit,
    onDelete: (String) -> Unit,
) {
    var expanded by remember { mutableStateOf(false) }
    val shape = RoundedCornerShape(16.dp)
    Box {
        Surface(
            shape = shape,
            color = MaterialTheme.colorScheme.surfaceContainer,
            modifier = Modifier
                .fillMaxWidth()
                .alpha(if (enabled) 1f else 0.45f)
                .clickable(enabled = enabled) { expanded = true },
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 14.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(
                    Icons.Filled.GraphicEq,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                )
                Spacer(Modifier.width(12.dp))
                Text(
                    text = state.displayPresetName(),
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.weight(1f),
                )
                Icon(
                    Icons.Filled.KeyboardArrowDown,
                    contentDescription = stringResource(R.string.equalizer_preset_menu),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            DropdownMenuItem(
                text = { Text(stringResource(R.string.equalizer_preset_custom)) },
                onClick = {
                    expanded = false
                    onSelectCustom()
                },
            )
            BuiltInPreset.entries.forEach { preset ->
                DropdownMenuItem(
                    text = { Text(preset.displayName) },
                    onClick = {
                        expanded = false
                        onSelectBuiltIn(preset)
                    },
                )
            }
            if (state.savedPresets.isNotEmpty()) {
                HorizontalDivider()
                state.savedPresets.forEach { saved ->
                    DropdownMenuItem(
                        text = { Text(saved.name) },
                        onClick = {
                            expanded = false
                            onSelectSaved(saved.name)
                        },
                        trailingIcon = {
                            Row {
                                TextButton(onClick = {
                                    expanded = false
                                    onRename(saved.name)
                                }) { Text(stringResource(R.string.equalizer_rename)) }
                                TextButton(onClick = {
                                    expanded = false
                                    onDelete(saved.name)
                                }) { Text(stringResource(R.string.equalizer_delete)) }
                            }
                        },
                    )
                }
            }
            HorizontalDivider()
            DropdownMenuItem(
                text = { Text(stringResource(R.string.equalizer_save_preset)) },
                onClick = {
                    expanded = false
                    onSave()
                },
            )
            DropdownMenuItem(
                text = { Text(stringResource(R.string.equalizer_reset)) },
                onClick = {
                    expanded = false
                    onReset()
                },
            )
        }
    }
}

@Composable
private fun BandSlidersCard(
    gainsDb: FloatArray,
    enabled: Boolean,
    onGainChange: (Int, Float) -> Unit,
) {
    Surface(
        shape = RoundedCornerShape(20.dp),
        color = MaterialTheme.colorScheme.surfaceContainer,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(
            modifier = Modifier
                .padding(16.dp)
                .alpha(if (enabled) 1f else 0.45f),
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(220.dp),
            ) {
                Column(
                    modifier = Modifier
                        .width(48.dp)
                        .fillMaxSize()
                        .padding(end = 4.dp),
                    verticalArrangement = Arrangement.SpaceBetween,
                ) {
                    ScaleLabel("+12 dB")
                    ScaleLabel("0 dB")
                    ScaleLabel("−12 dB")
                }
                Row(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxSize(),
                    horizontalArrangement = Arrangement.SpaceEvenly,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    for (i in 0 until EqualizerBands.COUNT) {
                        VerticalEqSlider(
                            valueDb = gainsDb[i],
                            onValueChange = { onGainChange(i, it) },
                            enabled = enabled,
                            label = EqualizerBands.LABELS[i],
                            modifier = Modifier.weight(1f),
                        )
                    }
                }
            }
            Spacer(Modifier.height(8.dp))
            Row(modifier = Modifier.fillMaxWidth()) {
                Spacer(modifier = Modifier.width(48.dp))
                Row(
                    modifier = Modifier.weight(1f),
                    horizontalArrangement = Arrangement.SpaceEvenly,
                ) {
                    EqualizerBands.LABELS.forEach { label ->
                        Text(
                            text = label,
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            textAlign = TextAlign.Center,
                            modifier = Modifier.weight(1f),
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun ScaleLabel(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.labelSmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
}

@Composable
private fun PresetGrid(
    selectedId: String,
    enabled: Boolean,
    onSelectCustom: () -> Unit,
    onSelectBuiltIn: (BuiltInPreset) -> Unit,
) {
    val tiles = remember {
        listOf<Pair<BuiltInPreset?, String>>(null to "Custom") +
            BuiltInPreset.entries.map { it to it.displayName }
    }
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .alpha(if (enabled) 1f else 0.45f),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        tiles.chunked(5).forEach { row ->
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                row.forEach { (preset, label) ->
                    val id = preset?.id ?: EqualizerPresetIds.CUSTOM
                    val selected = selectedId == id
                    PresetTile(
                        label = label,
                        icon = presetIcon(preset),
                        selected = selected,
                        enabled = enabled,
                        onClick = {
                            if (preset == null) onSelectCustom() else onSelectBuiltIn(preset)
                        },
                        modifier = Modifier.weight(1f),
                    )
                }
            }
        }
    }
}

@Composable
private fun PresetTile(
    label: String,
    icon: ImageVector,
    selected: Boolean,
    enabled: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val shape = RoundedCornerShape(14.dp)
    val border = if (selected) {
        Modifier.border(2.dp, MaterialTheme.colorScheme.primary, shape)
    } else {
        Modifier
    }
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = modifier
            .then(border)
            .background(MaterialTheme.colorScheme.surfaceContainer, shape)
            .clickable(enabled = enabled, role = Role.Button, onClick = onClick)
            .padding(vertical = 10.dp, horizontal = 4.dp),
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(22.dp),
        )
        Spacer(Modifier.height(4.dp))
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            textAlign = TextAlign.Center,
        )
    }
}

private fun presetIcon(preset: BuiltInPreset?): ImageVector = when (preset) {
    null -> Icons.Filled.Tune
    BuiltInPreset.Normal -> Icons.Filled.GraphicEq
    BuiltInPreset.Pop -> Icons.Filled.Waves
    BuiltInPreset.Rock -> Icons.Filled.MusicNote
    BuiltInPreset.HipHop -> Icons.Filled.Headphones
    BuiltInPreset.Jazz -> Icons.Filled.MusicNote
    BuiltInPreset.Classical -> Icons.Filled.Piano
    BuiltInPreset.Electronic -> Icons.Filled.SurroundSound
    BuiltInPreset.Vocal -> Icons.Filled.Mic
    BuiltInPreset.BassBoost -> Icons.Filled.Speaker
}

@Composable
private fun PreampCard(
    valueDb: Float,
    enabled: Boolean,
    onValueChange: (Float) -> Unit,
) {
    Surface(
        shape = RoundedCornerShape(20.dp),
        color = MaterialTheme.colorScheme.surfaceContainer,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(
            modifier = Modifier
                .padding(16.dp)
                .alpha(if (enabled) 1f else 0.45f),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = stringResource(R.string.equalizer_preamp),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                )
                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = MaterialTheme.colorScheme.surfaceContainerHigh,
                ) {
                    Text(
                        text = formatDb(valueDb),
                        style = MaterialTheme.typography.labelMedium,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                    )
                }
            }
            Slider(
                value = valueDb,
                onValueChange = { onValueChange(EqualizerBands.clampGain(it)) },
                valueRange = EqualizerBands.MIN_GAIN_DB..EqualizerBands.MAX_GAIN_DB,
                steps = 47,
                enabled = enabled,
                colors = SliderDefaults.colors(
                    thumbColor = MaterialTheme.colorScheme.primary,
                    activeTrackColor = MaterialTheme.colorScheme.primary,
                ),
            )
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                ScaleLabel("−12 dB")
                ScaleLabel("0")
                ScaleLabel("+12 dB")
            }
        }
    }
}

@Composable
private fun EffectsCard(
    bassBoost: Boolean,
    virtualizer: Boolean,
    loudness: Boolean,
    enabled: Boolean,
    onBassBoost: (Boolean) -> Unit,
    onVirtualizer: (Boolean) -> Unit,
    onLoudness: (Boolean) -> Unit,
) {
    Surface(
        shape = RoundedCornerShape(20.dp),
        color = MaterialTheme.colorScheme.surfaceContainer,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(modifier = Modifier.alpha(if (enabled) 1f else 0.45f)) {
            EffectRow(
                icon = Icons.Filled.Speaker,
                title = stringResource(R.string.equalizer_bass_boost),
                checked = bassBoost,
                enabled = enabled,
                onCheckedChange = onBassBoost,
            )
            HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp))
            EffectRow(
                icon = Icons.Filled.ViewInAr,
                title = stringResource(R.string.equalizer_virtualizer),
                checked = virtualizer,
                enabled = enabled,
                onCheckedChange = onVirtualizer,
            )
            HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp))
            EffectRow(
                icon = Icons.Filled.GraphicEq,
                title = stringResource(R.string.equalizer_loudness),
                checked = loudness,
                enabled = enabled,
                onCheckedChange = onLoudness,
            )
        }
    }
}

@Composable
private fun EffectRow(
    icon: ImageVector,
    title: String,
    checked: Boolean,
    enabled: Boolean,
    onCheckedChange: (Boolean) -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
        Spacer(Modifier.width(12.dp))
        Text(
            text = title,
            style = MaterialTheme.typography.bodyLarge,
            modifier = Modifier.weight(1f),
        )
        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange,
            enabled = enabled,
        )
    }
}

@Composable
private fun SavePresetDialog(
    onDismiss: () -> Unit,
    onConfirm: (String) -> Unit,
    title: String = stringResource(R.string.equalizer_save_preset_title),
    initial: String = "",
) {
    var name by rememberSaveable(initial) { mutableStateOf(initial) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            OutlinedTextField(
                value = name,
                onValueChange = {
                    if (it.length <= EqualizerRepository.MAX_NAME_LENGTH) name = it
                },
                singleLine = true,
                label = { Text(stringResource(R.string.equalizer_preset_name)) },
            )
        },
        confirmButton = {
            TextButton(
                onClick = { onConfirm(name) },
                enabled = name.trim().isNotEmpty(),
            ) {
                Text(stringResource(R.string.action_save))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.action_cancel))
            }
        },
    )
}
