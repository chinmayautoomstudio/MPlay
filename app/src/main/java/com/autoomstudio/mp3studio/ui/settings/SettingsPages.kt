package com.autoomstudio.mp3studio.ui.settings

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.PowerManager
import android.provider.Settings
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
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
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.LifecycleResumeEffect
import com.autoomstudio.mp3studio.R
import com.autoomstudio.mp3studio.data.plan.SeparatorUsage
import com.autoomstudio.mp3studio.data.settings.ThemeMode
import com.autoomstudio.mp3studio.data.settings.ThemeSettings
import com.autoomstudio.mp3studio.ui.library.DetailBackButton
import com.autoomstudio.mp3studio.ui.separation.SeparationSettingsSection
import com.autoomstudio.mp3studio.ui.separation.SeparationUiState
import com.autoomstudio.mp3studio.ui.separation.SeparationViewModel

/** Back arrow and title at the top of a Profile sub-page. */
@Composable
internal fun SubPageHeader(title: String, onBack: () -> Unit) {
    DetailBackButton(onBack = onBack)
    Text(
        text = title,
        style = MaterialTheme.typography.headlineSmall,
        color = MaterialTheme.colorScheme.onSurface,
        modifier = Modifier.padding(start = 16.dp, end = 16.dp, bottom = 8.dp),
    )
}

@Composable
private fun SubPage(
    title: String,
    onBack: () -> Unit,
    backEnabled: Boolean,
    modifier: Modifier,
    content: @Composable ColumnScope.() -> Unit,
) {
    BackHandler(enabled = backEnabled, onBack = onBack)
    Column(
        modifier
            .verticalScroll(rememberScrollState())
            .padding(bottom = 16.dp),
    ) {
        SubPageHeader(title, onBack)
        content()
    }
}

@Composable
fun AppearanceSettingsScreen(
    theme: ThemeSettings,
    onThemeModeChange: (ThemeMode) -> Unit,
    onDynamicColorChange: (Boolean) -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    backEnabled: Boolean = true,
) {
    SubPage(stringResource(R.string.profile_appearance), onBack, backEnabled, modifier) {
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
    }
}

@Composable
fun LibrarySettingsScreen(
    hideDuplicates: Boolean,
    onHideDuplicatesChange: (Boolean) -> Unit,
    duplicateGroupCount: Int,
    onReviewDuplicates: () -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    backEnabled: Boolean = true,
) {
    SubPage(stringResource(R.string.profile_library), onBack, backEnabled, modifier) {
        ListItem(
            headlineContent = { Text(stringResource(R.string.settings_hide_duplicates)) },
            supportingContent = { Text(stringResource(R.string.settings_hide_duplicates_summary)) },
            trailingContent = { Switch(checked = hideDuplicates, onCheckedChange = null) },
            colors = ListItemDefaults.colors(containerColor = MaterialTheme.colorScheme.background),
            modifier = Modifier.toggleable(
                value = hideDuplicates,
                role = Role.Switch,
                onValueChange = onHideDuplicatesChange,
            ),
        )
        ListItem(
            headlineContent = { Text(stringResource(R.string.settings_review_duplicates)) },
            supportingContent = {
                Text(
                    if (duplicateGroupCount == 0) {
                        stringResource(R.string.settings_review_duplicates_none)
                    } else {
                        pluralStringResource(
                            R.plurals.settings_review_duplicates_summary,
                            duplicateGroupCount,
                            duplicateGroupCount,
                        )
                    },
                )
            },
            colors = ListItemDefaults.colors(containerColor = MaterialTheme.colorScheme.background),
            modifier = Modifier.clickable(onClick = onReviewDuplicates),
        )
    }
}

@Composable
fun SeparationSettingsScreen(
    state: SeparationUiState,
    viewModel: SeparationViewModel,
    onOpenQueue: () -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    backEnabled: Boolean = true,
    usage: SeparatorUsage? = null,
) {
    SubPage(stringResource(R.string.profile_separation), onBack, backEnabled, modifier) {
        SeparationSettingsSection(
            state = state,
            viewModel = viewModel,
            onOpenQueue = onOpenQueue,
            sectionHeader = {},
            usage = usage,
        )
    }
}

@Composable
fun PlaybackSettingsScreen(
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    backEnabled: Boolean = true,
) {
    SubPage(stringResource(R.string.profile_playback), onBack, backEnabled, modifier) {
        BatteryOptimizationItem()
    }
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
