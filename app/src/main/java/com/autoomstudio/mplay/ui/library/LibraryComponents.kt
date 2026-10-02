package com.autoomstudio.mplay.ui.library

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Shuffle
import androidx.compose.material.icons.outlined.LibraryMusic
import androidx.compose.material.icons.outlined.SearchOff
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.autoomstudio.mplay.R
import com.autoomstudio.mplay.ui.components.ComingSoon

/** Empty-library message, or "No results" while a search filters everything out. */
@Composable
fun LibraryEmptyMessage(isFiltered: Boolean, searchQuery: String, modifier: Modifier = Modifier) {
    if (isFiltered) {
        ComingSoon(
            icon = Icons.Outlined.SearchOff,
            title = stringResource(R.string.search_no_results_title),
            message = stringResource(R.string.search_no_results_message, searchQuery.trim()),
            modifier = modifier,
        )
    } else {
        ComingSoon(
            icon = Icons.Outlined.LibraryMusic,
            title = stringResource(R.string.library_empty_title),
            message = stringResource(R.string.library_empty_message),
            modifier = modifier,
        )
    }
}

@Composable
fun songCountText(songCount: Int): String =
    pluralStringResource(R.plurals.song_count, songCount, songCount)

@Composable
fun songAndAlbumCountText(songCount: Int, albumCount: Int): String = stringResource(
    R.string.count_separator,
    pluralStringResource(R.plurals.song_count, songCount, songCount),
    pluralStringResource(R.plurals.album_count, albumCount, albumCount),
)

/** Round placeholder showing the first letter of an artist's name. */
@Composable
fun ArtistInitial(name: String, size: Dp, modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .size(size)
            .background(MaterialTheme.colorScheme.surfaceContainerHigh, CircleShape),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = name.firstOrNull { it.isLetterOrDigit() }?.uppercase() ?: "?",
            color = MaterialTheme.colorScheme.primary,
            fontSize = (size.value * 0.42f).sp,
            style = MaterialTheme.typography.titleLarge,
        )
    }
}

@Composable
fun DetailBackButton(onBack: () -> Unit) {
    IconButton(onClick = onBack, modifier = Modifier.padding(start = 4.dp)) {
        Icon(
            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
            contentDescription = stringResource(R.string.action_back),
            tint = MaterialTheme.colorScheme.onSurface,
        )
    }
}

/** Centered artwork, title, subtitle and Play / Shuffle buttons for album and artist screens. */
@Composable
fun DetailHeader(
    title: String,
    subtitle: String,
    onPlay: () -> Unit,
    onShuffle: () -> Unit,
    actionsEnabled: Boolean = true,
    artwork: @Composable () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 24.dp, vertical = 8.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        artwork()
        Spacer(Modifier.height(20.dp))
        Text(
            text = title,
            style = MaterialTheme.typography.headlineSmall,
            color = MaterialTheme.colorScheme.onSurface,
            textAlign = TextAlign.Center,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )
        Spacer(Modifier.height(4.dp))
        Text(
            text = subtitle,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )
        Spacer(Modifier.height(20.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Button(
                onClick = onPlay,
                enabled = actionsEnabled,
                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary),
            ) {
                Icon(Icons.Filled.PlayArrow, contentDescription = null)
                Spacer(Modifier.width(8.dp))
                Text(stringResource(R.string.action_play_all))
            }
            OutlinedButton(onClick = onShuffle, enabled = actionsEnabled) {
                Icon(Icons.Filled.Shuffle, contentDescription = null)
                Spacer(Modifier.width(8.dp))
                Text(stringResource(R.string.action_shuffle_all))
            }
        }
        Spacer(Modifier.height(8.dp))
    }
}
