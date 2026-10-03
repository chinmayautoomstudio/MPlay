package com.autoomstudio.mplay.ui.playback

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
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
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.autoomstudio.mplay.R
import com.autoomstudio.mplay.data.model.Song
import com.autoomstudio.mplay.playback.NowPlayingState
import com.autoomstudio.mplay.playback.RepeatMode
import com.autoomstudio.mplay.ui.common.formatDuration
import com.autoomstudio.mplay.ui.components.ArtworkImage
import com.autoomstudio.mplay.ui.components.popOnChange
import com.autoomstudio.mplay.ui.components.pressBounce
import com.autoomstudio.mplay.ui.theme.MotionMedium
import com.autoomstudio.mplay.ui.theme.MotionShort
import com.autoomstudio.mplay.ui.library.SongActions
import com.autoomstudio.mplay.ui.library.SongMenuButton
import kotlinx.coroutines.flow.Flow

/** Full player layout without its own background; the surrounding card draws it. */
@Composable
fun NowPlayingContent(
    state: NowPlayingState,
    position: Flow<Long>,
    actions: PlayerActions,
    onCollapse: () -> Unit,
    modifier: Modifier = Modifier,
    showArtwork: Boolean = true,
    onArtworkPositioned: (LayoutCoordinates) -> Unit = {},
    backEnabled: Boolean = true,
    song: Song? = null,
    songActions: SongActions? = null,
) {
    BackHandler(enabled = backEnabled, onBack = onCollapse)

    Box(modifier = modifier.fillMaxSize()) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .safeDrawingPadding()
                .padding(horizontal = 24.dp, vertical = 8.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Box(
                modifier = Modifier
                    .padding(top = 4.dp)
                    .size(width = 36.dp, height = 4.dp)
                    .background(MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f), CircleShape),
            )
            Row(modifier = Modifier.fillMaxWidth()) {
                IconButton(onClick = onCollapse) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = stringResource(R.string.action_collapse_player),
                        tint = MaterialTheme.colorScheme.onSurface,
                    )
                }
                Spacer(Modifier.weight(1f))
                if (song != null && songActions != null) {
                    SongMenuButton(song = song, actions = songActions, tint = MaterialTheme.colorScheme.onSurface)
                }
            }

            Column(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth(),
                verticalArrangement = Arrangement.Center,
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                val artModifier = Modifier
                    .weight(1f, fill = false)
                    .aspectRatio(1f)
                    .onGloballyPositioned(onArtworkPositioned)
                if (showArtwork) {
                    val artShape = RoundedCornerShape(28.dp)
                    val artScale by animateArtworkScale(state.isPlaying)
                    val artShadow by animateArtworkShadow(state.isPlaying, 32.dp)
                    ArtworkImage(
                        uri = state.artworkUri,
                        contentDescription = stringResource(R.string.album_art_description, state.title),
                        cornerRadius = 28.dp,
                        prominent = true,
                        modifier = artModifier
                            .graphicsLayer {
                                scaleX = artScale
                                scaleY = artScale
                            }
                            .shadow(
                                elevation = artShadow,
                                shape = artShape,
                                ambientColor = MaterialTheme.colorScheme.primary,
                                spotColor = MaterialTheme.colorScheme.primary,
                            ),
                    )
                } else {
                    Spacer(artModifier)
                }

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

private val ArtworkSpring = spring<Float>(dampingRatio = 0.6f, stiffness = Spring.StiffnessLow)

/** Full-size artwork while playing; it settles back slightly when paused. */
@Composable
fun animateArtworkScale(isPlaying: Boolean): State<Float> =
    animateFloatAsState(if (isPlaying) 1f else 0.9f, ArtworkSpring, label = "artworkScale")

/** The primary-colored glow under the artwork dims while paused. */
@Composable
fun animateArtworkShadow(isPlaying: Boolean, playingElevation: Dp): State<Dp> =
    animateDpAsState(
        targetValue = if (isPlaying) playingElevation else 8.dp,
        animationSpec = spring(dampingRatio = 0.6f, stiffness = Spring.StiffnessLow),
        label = "artworkShadow",
    )

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
    val shuffleTint by animateColorAsState(
        if (state.shuffleEnabled) active else inactive.copy(alpha = 0.7f),
        tween(MotionMedium),
        label = "shuffleTint",
    )
    val repeatTint by animateColorAsState(
        if (state.repeatMode == RepeatMode.Off) inactive.copy(alpha = 0.7f) else active,
        tween(MotionMedium),
        label = "repeatTint",
    )
    val nextTint by animateColorAsState(
        inactive.copy(alpha = if (state.hasNext) 1f else 0.38f),
        tween(MotionMedium),
        label = "nextTint",
    )
    val shuffleSource = remember { MutableInteractionSource() }
    val previousSource = remember { MutableInteractionSource() }
    val playSource = remember { MutableInteractionSource() }
    val nextSource = remember { MutableInteractionSource() }
    val repeatSource = remember { MutableInteractionSource() }

    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        IconButton(
            onClick = actions::toggleShuffle,
            interactionSource = shuffleSource,
            modifier = Modifier.pressBounce(shuffleSource),
        ) {
            Icon(
                imageVector = Icons.Filled.Shuffle,
                contentDescription = stringResource(
                    if (state.shuffleEnabled) R.string.action_shuffle_on else R.string.action_shuffle_off,
                ),
                tint = shuffleTint,
                modifier = Modifier.popOnChange(state.shuffleEnabled, enabled = state.shuffleEnabled),
            )
        }
        IconButton(
            onClick = actions::previous,
            interactionSource = previousSource,
            modifier = Modifier
                .size(56.dp)
                .pressBounce(previousSource),
        ) {
            Icon(
                imageVector = Icons.Filled.SkipPrevious,
                contentDescription = stringResource(R.string.action_previous),
                tint = inactive,
                modifier = Modifier.size(36.dp),
            )
        }
        FilledIconButton(
            onClick = actions::playPause,
            interactionSource = playSource,
            modifier = Modifier
                .size(76.dp)
                .pressBounce(playSource),
            shape = CircleShape,
            colors = IconButtonDefaults.filledIconButtonColors(
                containerColor = active,
                contentColor = Color.White,
            ),
        ) {
            PlayPauseIcon(isPlaying = state.isPlaying, tint = Color.White, modifier = Modifier.size(44.dp))
        }
        IconButton(
            onClick = actions::next,
            enabled = state.hasNext,
            interactionSource = nextSource,
            modifier = Modifier
                .size(56.dp)
                .pressBounce(nextSource),
        ) {
            Icon(
                imageVector = Icons.Filled.SkipNext,
                contentDescription = stringResource(R.string.action_next),
                tint = nextTint,
                modifier = Modifier.size(36.dp),
            )
        }
        IconButton(
            onClick = actions::cycleRepeat,
            interactionSource = repeatSource,
            modifier = Modifier.pressBounce(repeatSource),
        ) {
            AnimatedContent(
                targetState = state.repeatMode == RepeatMode.One,
                transitionSpec = {
                    (fadeIn(tween(MotionShort)) + scaleIn(tween(MotionShort), initialScale = 0.6f)) togetherWith
                        (fadeOut(tween(MotionShort)) + scaleOut(tween(MotionShort), targetScale = 0.6f))
                },
                label = "repeatIcon",
                modifier = Modifier.popOnChange(state.repeatMode, enabled = state.repeatMode != RepeatMode.Off),
            ) { repeatOne ->
                Icon(
                    imageVector = if (repeatOne) Icons.Filled.RepeatOne else Icons.Filled.Repeat,
                    contentDescription = stringResource(
                        when (state.repeatMode) {
                            RepeatMode.Off -> R.string.action_repeat_off
                            RepeatMode.All -> R.string.action_repeat_all
                            RepeatMode.One -> R.string.action_repeat_one
                        },
                    ),
                    tint = repeatTint,
                )
            }
        }
    }
}
