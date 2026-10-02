package com.autoomstudio.mplay.ui.playback

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Repeat
import androidx.compose.material.icons.filled.RepeatOne
import androidx.compose.material.icons.filled.Shuffle
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.SkipPrevious
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.autoomstudio.mplay.R
import com.autoomstudio.mplay.playback.NowPlayingState
import com.autoomstudio.mplay.playback.RepeatMode
import com.autoomstudio.mplay.ui.common.formatDuration
import com.autoomstudio.mplay.ui.components.ArtworkImage
import kotlinx.coroutines.flow.Flow

@Composable
fun NowPlayingScreen(
    state: NowPlayingState,
    position: Flow<Long>,
    actions: PlayerActions,
    onCollapse: () -> Unit,
    modifier: Modifier = Modifier,
) {
    BackHandler(onBack = onCollapse)

    Surface(modifier = modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .safeDrawingPadding()
                .padding(horizontal = 24.dp, vertical = 8.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Row(modifier = Modifier.fillMaxWidth()) {
                IconButton(onClick = onCollapse) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = stringResource(R.string.action_collapse_player),
                        tint = MaterialTheme.colorScheme.onSurface,
                    )
                }
            }

            Column(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth(),
                verticalArrangement = Arrangement.Center,
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                val artShape = RoundedCornerShape(28.dp)
                ArtworkImage(
                    uri = state.artworkUri,
                    contentDescription = stringResource(R.string.album_art_description, state.title),
                    cornerRadius = 28.dp,
                    prominent = true,
                    modifier = Modifier
                        .weight(1f, fill = false)
                        .aspectRatio(1f)
                        .shadow(
                            elevation = 32.dp,
                            shape = artShape,
                            ambientColor = MaterialTheme.colorScheme.primary,
                            spotColor = MaterialTheme.colorScheme.primary,
                        ),
                )

                Spacer(Modifier.height(32.dp))
                Column(modifier = Modifier.fillMaxWidth()) {
                    Text(
                        text = state.title,
                        style = MaterialTheme.typography.headlineSmall,
                        color = MaterialTheme.colorScheme.onSurface,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Text(
                        text = state.artist,
                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Normal),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }

                Spacer(Modifier.height(20.dp))
                SeekBar(durationMs = state.durationMs, position = position, onSeek = actions::seekTo)

                Spacer(Modifier.height(12.dp))
                TransportControls(state = state, actions = actions)
            }
            Spacer(Modifier.height(24.dp))
        }
    }
}

@Composable
private fun SeekBar(durationMs: Long, position: Flow<Long>, onSeek: (Long) -> Unit) {
    val positionMs by position.collectAsStateWithLifecycle(initialValue = 0L)
    var dragValue by remember { mutableStateOf<Float?>(null) }
    val max = durationMs.coerceAtLeast(1L).toFloat()
    val shown = (dragValue ?: positionMs.toFloat()).coerceIn(0f, max)
    val seekDescription = stringResource(R.string.seek_bar_description)

    Column(modifier = Modifier.fillMaxWidth()) {
        Slider(
            value = shown,
            onValueChange = { dragValue = it },
            onValueChangeFinished = {
                dragValue?.let { onSeek(it.toLong()) }
                dragValue = null
            },
            valueRange = 0f..max,
            enabled = durationMs > 0,
            colors = SliderDefaults.colors(
                thumbColor = MaterialTheme.colorScheme.primary,
                activeTrackColor = MaterialTheme.colorScheme.primary,
                inactiveTrackColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.18f),
            ),
            modifier = Modifier.semantics { contentDescription = seekDescription },
        )
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Text(
                text = formatDuration(shown.toLong()),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(
                text = formatDuration(durationMs),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun TransportControls(state: NowPlayingState, actions: PlayerActions) {
    val active = MaterialTheme.colorScheme.primary
    val inactive = MaterialTheme.colorScheme.onSurface

    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        IconButton(onClick = actions::toggleShuffle) {
            Icon(
                imageVector = Icons.Filled.Shuffle,
                contentDescription = stringResource(
                    if (state.shuffleEnabled) R.string.action_shuffle_on else R.string.action_shuffle_off,
                ),
                tint = if (state.shuffleEnabled) active else inactive.copy(alpha = 0.7f),
            )
        }
        IconButton(onClick = actions::previous, modifier = Modifier.size(56.dp)) {
            Icon(
                imageVector = Icons.Filled.SkipPrevious,
                contentDescription = stringResource(R.string.action_previous),
                tint = inactive,
                modifier = Modifier.size(36.dp),
            )
        }
        FilledIconButton(
            onClick = actions::playPause,
            modifier = Modifier.size(76.dp),
            shape = CircleShape,
            colors = IconButtonDefaults.filledIconButtonColors(
                containerColor = active,
                contentColor = Color.White,
            ),
        ) {
            Icon(
                imageVector = if (state.isPlaying) Icons.Filled.Pause else Icons.Filled.PlayArrow,
                contentDescription = stringResource(
                    if (state.isPlaying) R.string.action_pause else R.string.action_play,
                ),
                modifier = Modifier.size(40.dp),
            )
        }
        IconButton(
            onClick = actions::next,
            enabled = state.hasNext,
            modifier = Modifier.size(56.dp),
        ) {
            Icon(
                imageVector = Icons.Filled.SkipNext,
                contentDescription = stringResource(R.string.action_next),
                tint = inactive.copy(alpha = if (state.hasNext) 1f else 0.38f),
                modifier = Modifier.size(36.dp),
            )
        }
        IconButton(onClick = actions::cycleRepeat) {
            Icon(
                imageVector = if (state.repeatMode == RepeatMode.One) Icons.Filled.RepeatOne else Icons.Filled.Repeat,
                contentDescription = stringResource(
                    when (state.repeatMode) {
                        RepeatMode.Off -> R.string.action_repeat_off
                        RepeatMode.All -> R.string.action_repeat_all
                        RepeatMode.One -> R.string.action_repeat_one
                    },
                ),
                tint = if (state.repeatMode == RepeatMode.Off) inactive.copy(alpha = 0.7f) else active,
            )
        }
    }
}
