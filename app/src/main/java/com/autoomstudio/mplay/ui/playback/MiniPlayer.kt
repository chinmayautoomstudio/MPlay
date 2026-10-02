package com.autoomstudio.mplay.ui.playback

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.autoomstudio.mplay.R
import com.autoomstudio.mplay.playback.NowPlayingState
import com.autoomstudio.mplay.ui.components.ArtworkImage
import kotlinx.coroutines.flow.Flow

private val ProgressLineHeight = 2.dp
private val RowHeight = 64.dp
private val RowVerticalPadding = 6.dp

val MiniPlayerHeight = ProgressLineHeight + RowHeight
val MiniArtworkSize = 44.dp
val MiniArtworkStart = 10.dp
val MiniArtworkTop = ProgressLineHeight + (RowHeight - MiniArtworkSize) / 2

/** Mini player row without its own card; the surrounding card draws the background. */
@Composable
fun MiniPlayerContent(
    state: NowPlayingState,
    position: Flow<Long>,
    actions: PlayerActions,
    onExpand: () -> Unit,
    modifier: Modifier = Modifier,
    showArtwork: Boolean = true,
) {
    val positionMs by position.collectAsStateWithLifecycle(initialValue = 0L)
    val openLabel = stringResource(R.string.action_open_player, state.title)

    Column(modifier = modifier.fillMaxWidth()) {
        LinearProgressIndicator(
            progress = {
                if (state.durationMs > 0) {
                    (positionMs.toFloat() / state.durationMs).coerceIn(0f, 1f)
                } else {
                    0f
                }
            },
            modifier = Modifier
                .fillMaxWidth()
                .height(ProgressLineHeight),
            color = MaterialTheme.colorScheme.primary,
            trackColor = Color.Transparent,
            strokeCap = StrokeCap.Butt,
            gapSize = 0.dp,
            drawStopIndicator = {},
        )
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(RowHeight)
                .clickable(onClickLabel = openLabel, onClick = onExpand)
                .padding(start = MiniArtworkStart, end = 4.dp, top = RowVerticalPadding, bottom = RowVerticalPadding),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (showArtwork) {
                ArtworkImage(
                    uri = state.artworkUri,
                    contentDescription = null,
                    modifier = Modifier.size(MiniArtworkSize),
                    cornerRadius = 8.dp,
                )
            } else {
                Spacer(Modifier.size(MiniArtworkSize))
            }
            Spacer(Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = state.title,
                    style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.SemiBold),
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = state.artist,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            IconButton(onClick = actions::playPause) {
                Icon(
                    imageVector = if (state.isPlaying) Icons.Filled.Pause else Icons.Filled.PlayArrow,
                    contentDescription = stringResource(
                        if (state.isPlaying) R.string.action_pause else R.string.action_play,
                    ),
                    tint = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.size(30.dp),
                )
            }
            IconButton(onClick = actions::next, enabled = state.hasNext) {
                Icon(
                    imageVector = Icons.Filled.SkipNext,
                    contentDescription = stringResource(R.string.action_next),
                    tint = MaterialTheme.colorScheme.onSurface.copy(
                        alpha = if (state.hasNext) 1f else 0.38f,
                    ),
                    modifier = Modifier.size(30.dp),
                )
            }
        }
    }
}
