package com.autoomstudio.mplay.ui.settings

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.PowerManager
import android.provider.Settings
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.LifecycleResumeEffect
import com.autoomstudio.mplay.BuildConfig
import com.autoomstudio.mplay.R
import com.autoomstudio.mplay.data.settings.ThemeMode
import com.autoomstudio.mplay.data.settings.ThemeSettings

@Composable
fun SettingsScreen(
    theme: ThemeSettings,
    onThemeModeChange: (ThemeMode) -> Unit,
    onDynamicColorChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .verticalScroll(rememberScrollState())
            .padding(vertical = 8.dp),
    ) {
        SectionHeader(stringResource(R.string.settings_section_appearance))
        ThemeModeRow(selected = theme.mode, onSelect = onThemeModeChange)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            ListItem(
                headlineContent = { Text(stringResource(R.string.settings_dynamic_color)) },
                supportingContent = { Text(stringResource(R.string.settings_dynamic_color_summary)) },
                trailingContent = { Switch(checked = theme.dynamicColor, onCheckedChange = null) },
                colors = ListItemDefaults.colors(containerColor = MaterialTheme.colorScheme.background),
                modifier = Modifier.toggleable(
                    value = theme.dynamicColor,
                    role = Role.Switch,
                    onValueChange = onDynamicColorChange,
                ),
            )
        }

        SectionHeader(stringResource(R.string.settings_section_playback))
        BatteryOptimizationItem()

        SectionHeader(stringResource(R.string.settings_section_about))
        ListItem(
            headlineContent = { Text(stringResource(R.string.app_name)) },
            supportingContent = {
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(stringResource(R.string.settings_version, BuildConfig.VERSION_NAME))
                    Text(stringResource(R.string.settings_offline_note))
                }
            },
            colors = ListItemDefaults.colors(containerColor = MaterialTheme.colorScheme.background),
        )
    }
}

@Composable
private fun SectionHeader(title: String) {
    Text(
        text = title,
        style = MaterialTheme.typography.titleSmall,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 20.dp, bottom = 8.dp),
    )
}

@Composable
private fun ThemeModeRow(selected: ThemeMode, onSelect: (ThemeMode) -> Unit) {
    val labels = mapOf(
        ThemeMode.System to stringResource(R.string.settings_theme_system),
        ThemeMode.Light to stringResource(R.string.settings_theme_light),
        ThemeMode.Dark to stringResource(R.string.settings_theme_dark),
    )
    Column(Modifier.padding(horizontal = 16.dp, vertical = 4.dp)) {
        Text(
            text = stringResource(R.string.settings_theme),
            style = MaterialTheme.typography.bodyLarge,
            modifier = Modifier.padding(bottom = 8.dp),
        )
        SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
            ThemeMode.entries.forEachIndexed { index, mode ->
                SegmentedButton(
                    selected = mode == selected,
                    onClick = { onSelect(mode) },
                    shape = SegmentedButtonDefaults.itemShape(index, ThemeMode.entries.size),
                    label = { Text(labels.getValue(mode), maxLines = 1) },
                )
            }
        }
    }
}

/** Re-checked on every resume, since the user changes this in system settings. */
@Composable
private fun BatteryOptimizationItem() {
    val context = LocalContext.current
    var unrestricted by remember { mutableStateOf(context.isIgnoringBatteryOptimizations()) }
    LifecycleResumeEffect(context) {
        unrestricted = context.isIgnoringBatteryOptimizations()
        onPauseOrDispose { }
    }
    ListItem(
        headlineContent = { Text(stringResource(R.string.settings_battery_title)) },
        supportingContent = {
            Column {
                Text(
                    stringResource(
                        if (unrestricted) R.string.settings_battery_unrestricted else R.string.settings_battery_restricted,
                    ),
                )
                if (!unrestricted) {
                    OutlinedButton(
                        onClick = { context.openBatteryOptimizationSettings() },
                        modifier = Modifier.padding(top = 8.dp),
                    ) {
                        Text(stringResource(R.string.settings_battery_open))
                    }
                }
            }
        },
        colors = ListItemDefaults.colors(containerColor = MaterialTheme.colorScheme.background),
    )
}

private fun Context.isIgnoringBatteryOptimizations(): Boolean =
    getSystemService(PowerManager::class.java)?.isIgnoringBatteryOptimizations(packageName) == true

private fun Context.openBatteryOptimizationSettings() {
    val intents = listOf(
        Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS),
        Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.fromParts("package", packageName, null)),
    )
    for (intent in intents) {
        try {
            startActivity(intent)
            return
        } catch (_: ActivityNotFoundException) {
            // Some OEM builds remove the battery list; fall through to the app details page.
        }
    }
}
