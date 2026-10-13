package com.autoomstudio.mp3studio.ui.playback

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
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.text.TextAutoSize
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Bedtime
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Repeat
import androidx.compose.material.icons.filled.RepeatOne
import androidx.compose.material.icons.filled.Shuffle
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.SkipPrevious
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material.icons.outlined.AutoAwesome
import androidx.compose.material.icons.outlined.Bedtime
import androidx.compose.material.icons.outlined.HourglassEmpty
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material.icons.outlined.Mic
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
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
import androidx.lifecycle.viewmodel.compose.viewModel
import com.autoomstudio.mp3studio.R
import com.autoomstudio.mp3studio.data.model.Song
import com.autoomstudio.mp3studio.data.plan.Feature
import com.autoomstudio.mp3studio.data.stems.JobState
import com.autoomstudio.mp3studio.data.stems.StemMode
import com.autoomstudio.mp3studio.playback.NowPlayingState
import com.autoomstudio.mp3studio.playback.RepeatMode
import com.autoomstudio.mp3studio.playback.SleepTimer
import com.autoomstudio.mp3studio.playback.SleepTimerStatus
import com.autoomstudio.mp3studio.ui.common.formatDuration
import com.autoomstudio.mp3studio.ui.components.ArtworkImage
import com.autoomstudio.mp3studio.ui.components.LofiWaveIcon
import com.autoomstudio.mp3studio.ui.components.MetronomeIcons
import com.autoomstudio.mp3studio.ui.components.popOnChange
import com.autoomstudio.mp3studio.ui.components.pressBounce
import com.autoomstudio.mp3studio.ui.library.LocalSeparatedSongIds
import com.autoomstudio.mp3studio.ui.library.SongActions
import com.autoomstudio.mp3studio.ui.library.SongMenuButton
import com.autoomstudio.mp3studio.ui.metronome.MetronomeSheet
import com.autoomstudio.mp3studio.ui.metronome.MetronomeViewModel
import com.autoomstudio.mp3studio.ui.plans.PlansViewModel
import com.autoomstudio.mp3studio.ui.plans.UpgradeSheet
import com.autoomstudio.mp3studio.ui.separation.SeparatorUi
import com.autoomstudio.mp3studio.ui.separation.isCoolingDown
import com.autoomstudio.mp3studio.ui.singalong.SingAlongSheet
import com.autoomstudio.mp3studio.ui.theme.MotionMedium
import com.autoomstudio.mp3studio.ui.theme.MotionShort
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.launch

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
    onOpenEqualizer: () -> Unit = {},
    plansViewModel: PlansViewModel = viewModel(factory = PlansViewModel.Factory),
) {
    BackHandler(enabled = backEnabled, onBack = onCollapse)
    var showSleepSheet by rememberSaveable { mutableStateOf(false) }
    var showMetronomeSheet by rememberSaveable { mutableStateOf(false) }
    var showSingAlongSheet by rememberSaveable { mutableStateOf(false) }
    var showSingAlongUpgrade by rememberSaveable { mutableStateOf(false) }
    val plan by plansViewModel.state.collectAsStateWithLifecycle()
    val scope = rememberCoroutineScope()
    val openPlansRequest by plansViewModel.openPlansRequest.collectAsStateWithLifecycle()
    // The main screen leaves Now Playing for Settings > Plans; sheets opened here must not stay on top.
    LaunchedEffect(openPlansRequest) {
        if (openPlansRequest) {
            showMetronomeSheet = false
            showSingAlongSheet = false
        }
    }

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
                PlayerFeatureTiles(
                    state = state,
                    separated = separated,
                    separator = separator,
                    songAvailable = song != null,
                    singAlongLocked = !plan.unlocks(Feature.SingAlong),
                    onToggleLofi = actions::setLofi,
                    onOpenEqualizer = onOpenEqualizer,
                    onOpenMetronome = { showMetronomeSheet = true },
                    onOpenSingAlong = {
                        scope.launch {
                            if (plansViewModel.canUse(Feature.SingAlong)) {
                                showSingAlongSheet = true
                            } else {
                                showSingAlongUpgrade = true
                            }
                        }
                    },
                )
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
    if (showSingAlongUpgrade) {
        UpgradeSheet(feature = Feature.SingAlong, onDismiss = { showSingAlongUpgrade = false })
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

private val FeatureTileShape = RoundedCornerShape(18.dp)
private val FeatureTileIconSize = 22.dp

@Composable
private fun PlayerFeatureTiles(
    state: NowPlayingState,
    separated: Boolean,
    separator: SeparatorUi?,
    songAvailable: Boolean,
    singAlongLocked: Boolean,
    onToggleLofi: (Boolean) -> Unit,
    onOpenEqualizer: () -> Unit,
    onOpenMetronome: () -> Unit,
    onOpenSingAlong: () -> Unit,
    metronomeViewModel: MetronomeViewModel = viewModel(factory = MetronomeViewModel.Factory),
) {
    val metronome by metronomeViewModel.state.collectAsStateWithLifecycle()
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        PlayerFeatureTile(
            selected = state.lofiEnabled,
            onClick = { onToggleLofi(!state.lofiEnabled) },
            label = stringResource(R.string.lofi_mode),
            modifier = Modifier.weight(1f),
        ) {
            CompositionLocalProvider(LocalContentColor provides MaterialTheme.colorScheme.primary) {
                LofiWaveIcon(
                    active = state.lofiEnabled && state.isPlaying,
                    modifier = Modifier
                        .size(FeatureTileIconSize)
                        .popOnChange(state.lofiEnabled, enabled = state.lofiEnabled),
                )
            }
        }
        PlayerFeatureTile(
            selected = state.equalizerActive,
            onClick = onOpenEqualizer,
            label = stringResource(R.string.equalizer_chip),
            icon = Icons.Filled.Tune,
            modifier = Modifier.weight(1f),
        )
        PlayerFeatureTile(
            selected = metronome.running,
            onClick = onOpenMetronome,
            label = if (metronome.running) {
                stringResource(R.string.metronome_chip_bpm, metronome.settings.bpm)
            } else {
                stringResource(R.string.metronome_title)
            },
            icon = MetronomeIcons.Default,
            modifier = Modifier.weight(1f),
        )
        SeparatorFeatureTile(
            separator = separator,
            separated = separated,
            title = state.title,
            modifier = Modifier.weight(1f),
        )
        PlayerFeatureTile(
            selected = false,
            onClick = onOpenSingAlong,
            label = stringResource(R.string.singalong_chip),
            icon = Icons.Outlined.Mic,
            enabled = songAvailable,
            badgeIcon = if (singAlongLocked) Icons.Outlined.Lock else null,
            modifier = Modifier.weight(1f),
        )
    }
}

@Composable
private fun PlayerFeatureTile(
    selected: Boolean,
    onClick: () -> Unit,
    label: String,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    maxLines: Int = 1,
    icon: ImageVector? = null,
    badgeIcon: ImageVector? = null,
    iconContent: (@Composable () -> Unit)? = null,
) {
    val borderColor by animateColorAsState(
        targetValue = when {
            !enabled -> MaterialTheme.colorScheme.outline.copy(alpha = 0.35f)
            selected -> MaterialTheme.colorScheme.primary.copy(alpha = 0.7f)
            else -> MaterialTheme.colorScheme.outline.copy(alpha = 0.55f)
        },
        animationSpec = tween(MotionMedium),
        label = "featureTileBorder",
    )
    val contentAlpha = if (enabled) 1f else 0.38f
    Box(
        modifier = modifier
            .height(76.dp)
            .clip(FeatureTileShape)
            .border(BorderStroke(1.dp, borderColor), FeatureTileShape)
            .background(MaterialTheme.colorScheme.surfaceContainerHigh.copy(alpha = 0.35f))
            .clickable(enabled = enabled, onClick = onClick)
            .padding(horizontal = 4.dp, vertical = 8.dp)
            .graphicsLayer { alpha = contentAlpha },
    ) {
        Column(
            modifier = Modifier.fillMaxSize(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            when {
                iconContent != null -> iconContent()
                icon != null -> Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(FeatureTileIconSize),
                )
            }
            Spacer(Modifier.height(6.dp))
            Text(
                text = label,
                style = MaterialTheme.typography.labelSmall.copy(fontSize = 9.sp),
                color = MaterialTheme.colorScheme.onSurface,
                textAlign = TextAlign.Center,
                maxLines = maxLines,
                overflow = TextOverflow.Ellipsis,
                // Prefer shrinking over wrapping so labels like "Metronome" stay on one line.
                autoSize = TextAutoSize.StepBased(minFontSize = 7.sp, maxFontSize = 9.sp),
            )
        }
        if (badgeIcon != null) {
            Icon(
                imageVector = badgeIcon,
                contentDescription = stringResource(R.string.upgrade_locked_description),
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(2.dp)
                    .size(12.dp),
            )
        }
    }
}

@Composable
private fun SeparatorFeatureTile(
    separator: SeparatorUi?,
    separated: Boolean,
    title: String,
    modifier: Modifier = Modifier,
) {
    var confirmingCancel by rememberSaveable { mutableStateOf(false) }
    val job = separator?.job
    val jobState = job?.state
    val label = when {
        separated -> stringResource(R.string.separator_chip)
        job == null -> stringResource(R.string.separator_chip)
        job.isCoolingDown -> stringResource(R.string.separator_chip_cooling, (job.progress * 100).toInt())
        jobState == JobState.Running.name && job.progress <= 0f ->
            stringResource(R.string.separator_chip_preparing)
        jobState == JobState.Running.name ->
            stringResource(R.string.separator_chip_running, (job.progress * 100).toInt())
        jobState == JobState.Failed.name -> stringResource(R.string.separator_chip_retry)
        job.pauseReason != null -> stringResource(R.string.separator_chip_waiting)
        else -> stringResource(R.string.separator_chip_waiting)
    }
    PlayerFeatureTile(
        selected = separated || job != null,
        onClick = {
            when {
                separated || separator == null -> Unit
                job == null -> separator.onSeparate()
                jobState == JobState.Failed.name -> separator.onRetry(job.id)
                else -> confirmingCancel = true
            }
        },
        label = label,
        enabled = separator != null || separated,
        maxLines = 2,
        modifier = modifier,
        iconContent = {
            when {
                separated -> Icon(
                    imageVector = Icons.Filled.Check,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(FeatureTileIconSize),
                )
                jobState == JobState.Running.name -> if (job != null && job.progress > 0f) {
                    CircularProgressIndicator(
                        progress = { job.progress },
                        modifier = Modifier.size(FeatureTileIconSize),
                        strokeWidth = 2.dp,
                        color = MaterialTheme.colorScheme.primary,
                    )
                } else {
                    CircularProgressIndicator(
                        modifier = Modifier.size(FeatureTileIconSize),
                        strokeWidth = 2.dp,
                        color = MaterialTheme.colorScheme.primary,
                    )
                }
                jobState == JobState.Failed.name -> Icon(
                    imageVector = Icons.Outlined.Refresh,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.error,
                    modifier = Modifier.size(FeatureTileIconSize),
                )
                job != null -> Icon(
                    imageVector = Icons.Outlined.HourglassEmpty,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(FeatureTileIconSize),
                )
                else -> Icon(
                    imageVector = Icons.Outlined.AutoAwesome,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(FeatureTileIconSize),
                )
            }
        },
    )
    if (confirmingCancel && job != null && separator != null) {
        AlertDialog(
            onDismissRequest = { confirmingCancel = false },
            title = { Text(stringResource(R.string.separator_cancel_title)) },
            text = { Text(stringResource(R.string.separator_cancel_message, title)) },
            confirmButton = {
                TextButton(
                    onClick = {
                        confirmingCancel = false
                        separator.onCancel(job.id)
                    },
                ) { Text(stringResource(R.string.separator_cancel_confirm)) }
            },
            dismissButton = {
                TextButton(onClick = { confirmingCancel = false }) {
                    Text(stringResource(R.string.separator_cancel_keep))
                }
            },
        )
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
                thumbColor = Color.White,
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
    val onSurface = MaterialTheme.colorScheme.onSurface
    val shuffleTint by animateColorAsState(
        if (state.shuffleEnabled) active else onSurface.copy(alpha = 0.7f),
        tween(MotionMedium),
        label = "shuffleTint",
    )
    val repeatTint by animateColorAsState(
        if (state.repeatMode == RepeatMode.Off) onSurface.copy(alpha = 0.7f) else active,
        tween(MotionMedium),
        label = "repeatTint",
    )
    val nextTint by animateColorAsState(
        onSurface.copy(alpha = if (state.hasNext) 1f else 0.38f),
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
        OutlinedCircleButton(
            onClick = actions::toggleShuffle,
            active = state.shuffleEnabled,
            interactionSource = shuffleSource,
            modifier = Modifier.size(48.dp),
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
        OutlinedCircleButton(
            onClick = actions::previous,
            interactionSource = previousSource,
            modifier = Modifier.size(56.dp),
        ) {
            Icon(
                imageVector = Icons.Filled.SkipPrevious,
                contentDescription = stringResource(R.string.action_previous),
                tint = onSurface,
                modifier = Modifier.size(32.dp),
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
        OutlinedCircleButton(
            onClick = actions::next,
            enabled = state.hasNext,
            interactionSource = nextSource,
            modifier = Modifier.size(56.dp),
        ) {
            Icon(
                imageVector = Icons.Filled.SkipNext,
                contentDescription = stringResource(R.string.action_next),
                tint = nextTint,
                modifier = Modifier.size(32.dp),
            )
        }
        OutlinedCircleButton(
            onClick = actions::cycleRepeat,
            active = state.repeatMode != RepeatMode.Off,
            interactionSource = repeatSource,
            modifier = Modifier.size(48.dp),
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

@Composable
private fun OutlinedCircleButton(
    onClick: () -> Unit,
    interactionSource: MutableInteractionSource,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    active: Boolean = false,
    content: @Composable () -> Unit,
) {
    val borderColor by animateColorAsState(
        targetValue = when {
            active -> MaterialTheme.colorScheme.primary
            enabled -> MaterialTheme.colorScheme.onSurface.copy(alpha = 0.35f)
            else -> MaterialTheme.colorScheme.onSurface.copy(alpha = 0.15f)
        },
        animationSpec = tween(MotionMedium),
        label = "outlinedCircleBorder",
    )
    IconButton(
        onClick = onClick,
        enabled = enabled,
        interactionSource = interactionSource,
        modifier = modifier
            .border(BorderStroke(1.5.dp, borderColor), CircleShape)
            .background(
                MaterialTheme.colorScheme.surfaceContainerHigh.copy(alpha = 0.55f),
                CircleShape,
            )
            .pressBounce(interactionSource),
    ) {
        content()
    }
}
