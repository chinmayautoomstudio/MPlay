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
import androidx.compose.foundation.layout.FlowRow
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
import androidx.compose.foundation.text.TextAutoSize
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Bedtime
import androidx.compose.material.icons.filled.Repeat
import androidx.compose.material.icons.filled.RepeatOne
import androidx.compose.material.icons.filled.Shuffle
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.SkipPrevious
import androidx.compose.material.icons.outlined.Bedtime
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
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
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.autoomstudio.mplay.R
import com.autoomstudio.mplay.data.model.Song
import com.autoomstudio.mplay.data.stems.StemMode
import com.autoomstudio.mplay.playback.NowPlayingState
import com.autoomstudio.mplay.playback.RepeatMode
import com.autoomstudio.mplay.playback.SleepTimer
import com.autoomstudio.mplay.playback.SleepTimerStatus
import com.autoomstudio.mplay.ui.common.formatDuration
import com.autoomstudio.mplay.ui.components.ArtworkImage
import com.autoomstudio.mplay.ui.components.LofiWaveIcon
import com.autoomstudio.mplay.ui.components.popOnChange
import com.autoomstudio.mplay.ui.components.pressBounce
import com.autoomstudio.mplay.ui.theme.MotionMedium
import com.autoomstudio.mplay.ui.theme.MotionShort
import com.autoomstudio.mplay.ui.library.LocalSeparatedSongIds
import com.autoomstudio.mplay.ui.library.SongActions
import com.autoomstudio.mplay.ui.library.SongMenuButton
import com.autoomstudio.mplay.ui.metronome.MetronomeChip
import com.autoomstudio.mplay.ui.metronome.MetronomeSheet
import com.autoomstudio.mplay.ui.separation.SeparatorChip
import com.autoomstudio.mplay.ui.separation.SeparatorUi
import com.autoomstudio.mplay.ui.singalong.SingAlongChip
import com.autoomstudio.mplay.ui.singalong.SingAlongSheet
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
    suggestedSleepMinutes: Int = SleepTimer.DEFAULT_MINUTES,
    separator: SeparatorUi? = null,
) {
    BackHandler(enabled = backEnabled, onBack = onCollapse)
    var showSleepSheet by rememberSaveable { mutableStateOf(false) }
    var showMetronomeSheet by rememberSaveable { mutableStateOf(false) }
    var showSingAlongSheet by rememberSaveable { mutableStateOf(false) }

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
                SleepTimerButton(status = state.sleepTimer, onClick = { showSleepSheet = true })
                if (song != null && songActions != null) {
                    SongMenuButton(
                        song = song,
                        actions = songActions,
                        tint = MaterialTheme.colorScheme.onSurface,
                        extraItems = { dismiss ->
                            DropdownMenuItem(
                                text = { Text(stringResource(R.string.menu_sleep_timer)) },
                                onClick = {
                                    dismiss()
                                    showSleepSheet = true
                                },
                            )
                            DropdownMenuItem(
                                text = {
                                    Text(
                                        stringResource(
                                            if (state.lofiEnabled) R.string.menu_lofi_off else R.string.menu_lofi_on,
                                        ),
                                    )
                                },
                                onClick = {
                                    dismiss()
                                    actions.setLofi(!state.lofiEnabled)
                                },
                            )
                            HorizontalDivider()
                        },
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
                    SleepTimerChip(
                        status = state.sleepTimer,
                        onOpen = { showSleepSheet = true },
                        onExtend = { actions.extendSleepTimer(SleepTimer.EXTEND_MINUTES) },
                        onCancel = actions::cancelSleepTimer,
                    )
                }

                Spacer(Modifier.height(20.dp))
                SeekBar(durationMs = state.durationMs, position = position, onSeek = actions::seekTo)

                Spacer(Modifier.height(12.dp))
                TransportControls(state = state, actions = actions)

                val separated = state.songId != null && state.songId in LocalSeparatedSongIds.current
                if (separated) {
                    Spacer(Modifier.height(12.dp))
                    StemModeSelector(mode = state.stemMode, onSelect = actions::setStemMode)
                }

                Spacer(Modifier.height(12.dp))
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally),
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        val chipModifier = Modifier.align(Alignment.CenterVertically)
                        LofiChip(
                            enabled = state.lofiEnabled,
                            isPlaying = state.isPlaying,
                            onToggle = actions::setLofi,
                            modifier = chipModifier,
                        )
                        MetronomeChip(onClick = { showMetronomeSheet = true }, modifier = chipModifier)
                        if (separator != null && !separated) {
                            SeparatorChip(ui = separator, title = state.title, modifier = chipModifier)
                        }
                        if (song != null) {
                            SingAlongChip(onClick = { showSingAlongSheet = true }, modifier = chipModifier)
                        }
                    }
                    Text(
                        text = stringResource(R.string.lofi_info),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
            Spacer(Modifier.height(16.dp))
        }
    }

    if (showSleepSheet) {
        SleepTimerSheet(
            status = state.sleepTimer,
            suggestedMinutes = suggestedSleepMinutes,
            onStart = {
                actions.setSleepTimer(it)
                showSleepSheet = false
            },
            onEndOfSong = {
                actions.setSleepTimerEndOfSong()
                showSleepSheet = false
            },
            onExtend = { actions.extendSleepTimer(SleepTimer.EXTEND_MINUTES) },
            onCancel = {
                actions.cancelSleepTimer()
                showSleepSheet = false
            },
            onDismiss = { showSleepSheet = false },
        )
    }
    if (showMetronomeSheet) {
        MetronomeSheet(song = song, onDismiss = { showMetronomeSheet = false })
    }
    if (showSingAlongSheet) {
        SingAlongSheet(
            song = song,
            separated = state.songId != null && state.songId in LocalSeparatedSongIds.current,
            separator = separator,
            position = position,
            onDismiss = { showSingAlongSheet = false },
        )
    }
}

@Composable
private fun SleepTimerButton(status: SleepTimerStatus, onClick: () -> Unit) {
    val remaining by rememberSleepRemainingMs(status)
    val description = when (status) {
        SleepTimerStatus.Off -> stringResource(R.string.sleep_timer_action)
        SleepTimerStatus.EndOfSong -> stringResource(R.string.sleep_timer_action_end_of_song)
        is SleepTimerStatus.Running ->
            stringResource(R.string.sleep_timer_action_active, formatDuration(remaining ?: 0L))
    }
    val tint by animateColorAsState(
        if (status == SleepTimerStatus.Off) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.primary,
        tween(MotionMedium),
        label = "sleepTint",
    )
    IconButton(onClick = onClick) {
        Icon(
            imageVector = if (status == SleepTimerStatus.Off) Icons.Outlined.Bedtime else Icons.Filled.Bedtime,
            contentDescription = description,
            tint = tint,
        )
    }
}

/** Original, instrumental or vocals; the choice carries over to the next separated songs. */
@Composable
private fun StemModeSelector(mode: StemMode, onSelect: (StemMode) -> Unit) {
    val labels = mapOf(
        StemMode.Original to stringResource(R.string.stem_mode_original),
        StemMode.Instrumental to stringResource(R.string.stem_mode_instrumental),
        StemMode.Vocals to stringResource(R.string.stem_mode_vocals),
    )
    val groupLabel = stringResource(R.string.stem_mode_label)
    SingleChoiceSegmentedButtonRow(
        modifier = Modifier
            .fillMaxWidth()
            .semantics { contentDescription = groupLabel },
    ) {
        StemMode.entries.forEachIndexed { index, entry ->
            SegmentedButton(
                selected = entry == mode,
                onClick = { if (entry != mode) onSelect(entry) },
                shape = SegmentedButtonDefaults.itemShape(index, StemMode.entries.size),
                // The fill already marks the selection; the default checkmark leaves "Instrumental" too little room.
                icon = {},
                label = {
                    Text(
                        text = labels.getValue(entry),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        autoSize = TextAutoSize.StepBased(
                            minFontSize = 10.sp,
                            maxFontSize = LocalTextStyle.current.fontSize,
                        ),
                    )
                },
            )
        }
    }
}

@Composable
private fun LofiChip(enabled: Boolean, isPlaying: Boolean, onToggle: (Boolean) -> Unit, modifier: Modifier = Modifier) {
    FilterChip(
        modifier = modifier,
        selected = enabled,
        onClick = { onToggle(!enabled) },
        label = { Text(stringResource(R.string.lofi_mode)) },
        leadingIcon = {
            LofiWaveIcon(
                active = enabled && isPlaying,
                modifier = Modifier
                    .size(FilterChipDefaults.IconSize)
                    .popOnChange(enabled, enabled = enabled),
            )
        },
    )
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
