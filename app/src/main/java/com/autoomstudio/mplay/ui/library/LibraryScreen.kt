package com.autoomstudio.mplay.ui.library

import androidx.annotation.StringRes
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Album
import androidx.compose.material.icons.outlined.Person
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.PrimaryTabRow
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import com.autoomstudio.mplay.R
import com.autoomstudio.mplay.data.model.Song
import com.autoomstudio.mplay.ui.components.ComingSoon

private enum class LibraryTab(@StringRes val label: Int) {
    Songs(R.string.tab_songs),
    Albums(R.string.tab_albums),
    Artists(R.string.tab_artists),
}

@Composable
fun LibraryScreen(
    state: LibraryUiState,
    isRefreshing: Boolean,
    onRefresh: () -> Unit,
    onSongClick: (Song) -> Unit,
    modifier: Modifier = Modifier,
) {
    var selectedIndex by rememberSaveable { mutableIntStateOf(0) }
    val selectedTab = LibraryTab.entries[selectedIndex]

    Column(modifier = modifier) {
        PrimaryTabRow(
            selectedTabIndex = selectedIndex,
            containerColor = MaterialTheme.colorScheme.background,
            contentColor = MaterialTheme.colorScheme.primary,
            divider = { HorizontalDivider(color = MaterialTheme.colorScheme.outline) },
        ) {
            LibraryTab.entries.forEachIndexed { index, tab ->
                Tab(
                    selected = index == selectedIndex,
                    onClick = { selectedIndex = index },
                    text = {
                        Text(
                            text = stringResource(tab.label),
                            style = MaterialTheme.typography.titleSmall,
                        )
                    },
                    selectedContentColor = MaterialTheme.colorScheme.primary,
                    unselectedContentColor = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }

        val contentModifier = Modifier.fillMaxSize()
        when (selectedTab) {
            LibraryTab.Songs -> SongsTab(
                state = state,
                isRefreshing = isRefreshing,
                onRefresh = onRefresh,
                onSongClick = onSongClick,
                modifier = contentModifier,
            )

            LibraryTab.Albums -> ComingSoon(
                icon = Icons.Outlined.Album,
                title = stringResource(R.string.tab_albums),
                message = stringResource(R.string.coming_soon_albums),
                modifier = contentModifier,
            )

            LibraryTab.Artists -> ComingSoon(
                icon = Icons.Outlined.Person,
                title = stringResource(R.string.tab_artists),
                message = stringResource(R.string.coming_soon_artists),
                modifier = contentModifier,
            )
        }
    }
}
