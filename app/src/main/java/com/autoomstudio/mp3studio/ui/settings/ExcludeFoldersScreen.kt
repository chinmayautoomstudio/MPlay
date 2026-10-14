package com.autoomstudio.mp3studio.ui.settings

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.selection.toggleable
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.FolderOff
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.autoomstudio.mp3studio.R
import com.autoomstudio.mp3studio.data.library.AudioFolder
import com.autoomstudio.mp3studio.ui.components.ComingSoon
import com.autoomstudio.mp3studio.ui.library.DetailBackButton
import com.autoomstudio.mp3studio.ui.library.ExcludeFoldersViewModel

@Composable
fun ExcludeFoldersScreen(
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    backEnabled: Boolean = true,
    viewModel: ExcludeFoldersViewModel = viewModel(factory = ExcludeFoldersViewModel.Factory),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    BackHandler(enabled = backEnabled, onBack = onBack)

    when {
        state.loading -> {
            Column(modifier = modifier.fillMaxSize()) {
                DetailBackButton(onBack = onBack)
                Column(
                    modifier = Modifier.fillMaxSize(),
                    verticalArrangement = Arrangement.Center,
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    CircularProgressIndicator()
                }
            }
        }
        state.folders.isEmpty() -> {
            Column(modifier = modifier.fillMaxSize()) {
                DetailBackButton(onBack = onBack)
                ComingSoon(
                    icon = Icons.Outlined.FolderOff,
                    title = stringResource(R.string.exclude_folders_empty_title),
                    message = stringResource(R.string.exclude_folders_empty_message),
                )
            }
        }
        else -> {
            LazyColumn(
                modifier = modifier.fillMaxSize(),
                contentPadding = PaddingValues(bottom = 16.dp),
            ) {
                item(key = "header") {
                    SubPageHeader(
                        title = stringResource(R.string.settings_exclude_folders),
                        onBack = onBack,
                    )
                    Text(
                        text = stringResource(R.string.exclude_folders_intro),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                    )
                }
                items(state.folders, key = { it.relativePath }) { folder ->
                    FolderExcludeRow(
                        folder = folder,
                        excluded = folder.relativePath in state.excluded,
                        onExcludedChange = { viewModel.setFolderExcluded(folder.relativePath, it) },
                    )
                }
            }
        }
    }
}

@Composable
private fun FolderExcludeRow(
    folder: AudioFolder,
    excluded: Boolean,
    onExcludedChange: (Boolean) -> Unit,
) {
    ListItem(
        headlineContent = {
            Text(
                text = folder.relativePath.trimEnd('/'),
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
        },
        supportingContent = {
            Text(
                pluralStringResource(
                    R.plurals.exclude_folders_song_count,
                    folder.songCount,
                    folder.songCount,
                ),
            )
        },
        trailingContent = {
            Switch(checked = excluded, onCheckedChange = null)
        },
        colors = ListItemDefaults.colors(containerColor = MaterialTheme.colorScheme.background),
        modifier = Modifier
            .fillMaxWidth()
            .toggleable(
                value = excluded,
                role = Role.Switch,
                onValueChange = onExcludedChange,
            ),
    )
}
