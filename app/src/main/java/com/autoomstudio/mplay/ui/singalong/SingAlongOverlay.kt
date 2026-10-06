package com.autoomstudio.mplay.ui.singalong

import android.os.SystemClock
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material.icons.outlined.ErrorOutline
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.autoomstudio.mplay.R
import com.autoomstudio.mplay.singalong.EndReason
import com.autoomstudio.mplay.singalong.FailReason
import com.autoomstudio.mplay.singalong.MixSettings
import com.autoomstudio.mplay.singalong.RecordingNames
import com.autoomstudio.mplay.singalong.SingAlongMixer
import com.autoomstudio.mplay.singalong.SingAlongState
import com.autoomstudio.mplay.singalong.TakePreview
import com.autoomstudio.mplay.ui.common.formatDuration
import com.autoomstudio.mplay.ui.components.rememberAnimationsEnabled
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive

/** Full-screen count-in, recording, review and saving screens; shown above everything while a session runs. */
@Composable
fun SingAlongOverlay(viewModel: SingAlongViewModel = viewModel(factory = SingAlongViewModel.Factory)) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    if (state == SingAlongState.Idle) return
    Dialog(
        onDismissRequest = {},
        properties = DialogProperties(
            usePlatformDefaultWidth = false,
            dismissOnBackPress = false,
            dismissOnClickOutside = false,
            decorFitsSystemWindows = false,
        ),
    ) {
        Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.surface) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .safeDrawingPadding()
                    .padding(24.dp),
            ) {
                val animate = rememberAnimationsEnabled()
                AnimatedContent(
                    targetState = state,
                    contentKey = { it::class },
                    transitionSpec = {
                        if (animate) {
                            (fadeIn(tween(300)) + scaleIn(tween(300), initialScale = 0.96f)) togetherWith fadeOut(tween(200))
                        } else {
                            EnterTransition.None togetherWith ExitTransition.None
                        }
                    },
                    label = "singAlongState",
                    modifier = Modifier.fillMaxSize(),
                ) { current ->
                    when (current) {
                        SingAlongState.Idle -> Unit
                        is SingAlongState.Preparing -> Preparing(current.title, null, viewModel::cancel)
                        is SingAlongState.CountIn -> Preparing(current.title, current.count, viewModel::cancel)
                        is SingAlongState.Recording -> Recording(current, viewModel, animate)
                        is SingAlongState.Review -> Review(current, viewModel)
                        is SingAlongState.Saving -> Saving(current.progress, animate)
                        is SingAlongState.Saved -> Outcome(
                            text = stringResource(R.string.singalong_saved, current.name),
                            button = stringResource(R.string.singalong_done),
                            onClose = viewModel::close,
                            animate = animate,
                        ) { SavedCheck(animate) }
                        is SingAlongState.Failed -> Outcome(
                            text = stringResource(current.reason.message()),
                            button = stringResource(R.string.singalong_close),
                            onClose = viewModel::close,
                            animate = animate,
                        ) {
                            ShakeOnEnter(animate) {
                                Icon(
                                    Icons.Outlined.ErrorOutline,
                                    contentDescription = null,
                                    modifier = Modifier.size(64.dp),
                                    tint = MaterialTheme.colorScheme.primary,
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun Preparing(title: String, count: Int?, onCancel: () -> Unit) {
    BackHandler(onBack = onCancel)
    Column(
        modifier = Modifier.fillMaxSize(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Text(title, style = MaterialTheme.typography.titleLarge, maxLines = 2, overflow = TextOverflow.Ellipsis)
        Spacer(Modifier.height(48.dp))
        Box(modifier = Modifier.size(160.dp), contentAlignment = Alignment.Center) {
            if (count == null) {
                CircularProgressIndicator()
            } else {
                AnimatedContent(
                    targetState = count,
                    transitionSpec = { (fadeIn() + scaleIn(initialScale = 1.6f)) togetherWith fadeOut() },
                    label = "countIn",
                ) { value ->
                    Text(
                        text = value.toString(),
                        fontSize = 120.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary,
                    )
                }
            }
        }
        if (count == null) {
            Spacer(Modifier.height(16.dp))
            Text(stringResource(R.string.singalong_preparing), color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Spacer(Modifier.height(48.dp))
        TextButton(onClick = onCancel) { Text(stringResource(R.string.singalong_cancel)) }
    }
}

@Composable
private fun Recording(state: SingAlongState.Recording, viewModel: SingAlongViewModel, animate: Boolean) {
    BackHandler(onBack = viewModel::stop)
    var elapsedMs by remember { mutableLongStateOf(0L) }
    var level by remember { mutableFloatStateOf(0f) }
    val history = remember { mutableStateListOf<Float>() }
    var samples by remember { mutableLongStateOf(0L) }
    LaunchedEffect(state.startedAtMs) {
        while (isActive) {
            elapsedMs = (SystemClock.elapsedRealtime() - state.startedAtMs).coerceAtLeast(0L)
            // Fast attack, slow release, so peaks stay readable.
            val input = viewModel.level()
            level = if (input > level) input else level * LEVEL_RELEASE + input * (1 - LEVEL_RELEASE)
            history.add(level)
            if (history.size > WAVE_BARS + 1) history.removeAt(0)
            samples++
            delay(LEVEL_POLL_MS)
        }
    }
    Column(
        modifier = Modifier.fillMaxSize(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            PulsingDot(MaterialTheme.colorScheme.error, animate)
            Text(
                stringResource(R.string.singalong_recording_label),
                color = MaterialTheme.colorScheme.error,
                style = MaterialTheme.typography.labelLarge,
                modifier = Modifier.padding(start = 8.dp),
            )
        }
        Spacer(Modifier.height(8.dp))
        Text(state.title, style = MaterialTheme.typography.titleLarge, maxLines = 2, overflow = TextOverflow.Ellipsis)
        Spacer(Modifier.height(32.dp))
        RollingTimer(formatDuration(elapsedMs), animate)
        Spacer(Modifier.height(32.dp))
        LiveWaveform(
            levels = history,
            sampleCount = samples,
            clipLevel = CLIP_LEVEL,
            animate = animate,
            modifier = Modifier
                .fillMaxWidth()
                .height(64.dp),
        )
        Spacer(Modifier.height(8.dp))
        Box(modifier = Modifier.size(STOP_AREA), contentAlignment = Alignment.Center) {
            VoiceRings(
                level = level,
                color = MaterialTheme.colorScheme.error,
                buttonSize = STOP_SIZE,
                animate = animate,
                modifier = Modifier.fillMaxSize(),
            )
            FilledIconButton(
                onClick = viewModel::stop,
                modifier = Modifier.size(STOP_SIZE),
                colors = IconButtonDefaults.filledIconButtonColors(
                    containerColor = MaterialTheme.colorScheme.error,
                    contentColor = MaterialTheme.colorScheme.onError,
                ),
            ) {
                Icon(
                    Icons.Filled.Stop,
                    contentDescription = stringResource(R.string.singalong_stop),
                    modifier = Modifier.size(44.dp),
                )
            }
        }
        Spacer(Modifier.height(8.dp))
        OutlinedButton(onClick = viewModel::retake) { Text(stringResource(R.string.singalong_retake)) }
    }
}

@Composable
private fun Review(state: SingAlongState.Review, viewModel: SingAlongViewModel) {
    val take = state.take
    val mix by viewModel.mix.collectAsStateWithLifecycle()
    var name by rememberSaveable(take.voiceFile.path) { mutableStateOf(RecordingNames.suggested(take.title)) }
    var confirmingDiscard by rememberSaveable { mutableStateOf(false) }
    BackHandler { confirmingDiscard = true }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Text(stringResource(R.string.singalong_review_title), style = MaterialTheme.typography.headlineSmall)
        Text(take.title, style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)

        when (take.endReason) {
            EndReason.Interrupted -> Notice(Icons.Outlined.Info, stringResource(R.string.singalong_interrupted))
            EndReason.SongEnded -> Notice(Icons.Outlined.Info, stringResource(R.string.singalong_song_ended))
            EndReason.Failed -> Notice(
                Icons.Outlined.ErrorOutline,
                stringResource(R.string.singalong_failed_midway),
                MaterialTheme.colorScheme.error,
            )
            EndReason.Stopped -> Unit
        }

        viewModel.preview?.let { PreviewPlayer(it, take.lengthFrames) }

        MixSlider(
            label = stringResource(R.string.singalong_voice_level),
            value = mix.voiceGain,
            range = 0f..MixSettings.MAX_GAIN,
            valueText = "${(mix.voiceGain * 100).toInt()}%",
            onChange = { value -> viewModel.updateMix { it.copy(voiceGain = value) } },
        )
        MixSlider(
            label = stringResource(R.string.singalong_music_level),
            value = mix.instrumentalGain,
            range = 0f..MixSettings.MAX_GAIN,
            valueText = "${(mix.instrumentalGain * 100).toInt()}%",
            onChange = { value -> viewModel.updateMix { it.copy(instrumentalGain = value) } },
        )
        MixSlider(
            label = stringResource(R.string.singalong_offset),
            value = mix.offsetMs.toFloat(),
            range = -MixSettings.MAX_OFFSET_MS.toFloat()..MixSettings.MAX_OFFSET_MS.toFloat(),
            steps = MixSettings.MAX_OFFSET_MS * 2 / OFFSET_STEP_MS - 1,
            valueText = stringResource(R.string.singalong_offset_value, mix.offsetMs),
            onChange = { value -> viewModel.updateMix { it.copy(offsetMs = value.toInt()) } },
        )
        Text(
            stringResource(R.string.singalong_offset_hint),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )

        OutlinedTextField(
            value = name,
            onValueChange = { name = it.take(RecordingNames.MAX_LENGTH) },
            label = { Text(stringResource(R.string.singalong_name)) },
            singleLine = true,
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
            modifier = Modifier.fillMaxWidth(),
        )
        if (state.saveFailed) {
            Text(stringResource(R.string.singalong_save_failed), color = MaterialTheme.colorScheme.error)
        }

        val cleanName = RecordingNames.clean(name)
        Button(
            onClick = { viewModel.save(cleanName) },
            enabled = cleanName.isNotBlank(),
            modifier = Modifier.fillMaxWidth(),
        ) { Text(stringResource(R.string.singalong_save)) }
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.fillMaxWidth()) {
            OutlinedButton(onClick = viewModel::retake, modifier = Modifier.weight(1f)) {
                Text(stringResource(R.string.singalong_retake))
            }
            OutlinedButton(onClick = { confirmingDiscard = true }, modifier = Modifier.weight(1f)) {
                Text(stringResource(R.string.singalong_discard))
            }
        }
    }

    if (confirmingDiscard) {
        AlertDialog(
            onDismissRequest = { confirmingDiscard = false },
            title = { Text(stringResource(R.string.singalong_discard_confirm_title)) },
            text = { Text(stringResource(R.string.singalong_discard_confirm_text)) },
            confirmButton = {
                TextButton(onClick = {
                    confirmingDiscard = false
                    viewModel.close()
                }) { Text(stringResource(R.string.singalong_discard)) }
            },
            dismissButton = {
                TextButton(onClick = { confirmingDiscard = false }) { Text(stringResource(R.string.singalong_cancel)) }
            },
        )
    }
}

@Composable
private fun PreviewPlayer(preview: TakePreview, lengthFrames: Long) {
    val playing by preview.playing.collectAsStateWithLifecycle()
    val positionFrames by preview.positionFrames.collectAsStateWithLifecycle()
    var dragFrames by remember { mutableStateOf<Float?>(null) }
    val max = lengthFrames.coerceAtLeast(1L).toFloat()
    val shown = (dragFrames ?: positionFrames.toFloat()).coerceIn(0f, max)
    Row(verticalAlignment = Alignment.CenterVertically) {
        FilledIconButton(onClick = preview::toggle, modifier = Modifier.size(56.dp)) {
            Icon(
                if (playing) Icons.Filled.Pause else Icons.Filled.PlayArrow,
                contentDescription = stringResource(
                    if (playing) R.string.singalong_pause_preview else R.string.singalong_play_preview,
                ),
            )
        }
        Column(modifier = Modifier
            .weight(1f)
            .padding(start = 12.dp)) {
            Slider(
                value = shown,
                onValueChange = { dragFrames = it },
                onValueChangeFinished = {
                    dragFrames?.let { preview.seekTo(it.toLong()) }
                    dragFrames = null
                },
                valueRange = 0f..max,
            )
            Row {
                Text(formatDuration(shown.toLong() * 1000 / SingAlongMixer.SAMPLE_RATE), style = MaterialTheme.typography.bodySmall)
                Spacer(Modifier.weight(1f))
                Text(formatDuration(lengthFrames * 1000 / SingAlongMixer.SAMPLE_RATE), style = MaterialTheme.typography.bodySmall)
            }
        }
    }
}

@Composable
private fun MixSlider(
    label: String,
    value: Float,
    range: ClosedFloatingPointRange<Float>,
    valueText: String,
    onChange: (Float) -> Unit,
    steps: Int = 0,
) {
    Column {
        Row {
            Text(label, style = MaterialTheme.typography.titleSmall, modifier = Modifier.weight(1f))
            Text(valueText, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Slider(value = value, onValueChange = onChange, valueRange = range, steps = steps)
    }
}

@Composable
private fun Saving(progress: Float, animate: Boolean) {
    BackHandler {}
    Column(
        modifier = Modifier.fillMaxSize(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        SavingRing(progress, animate)
        Spacer(Modifier.height(24.dp))
        Text(stringResource(R.string.singalong_saving), style = MaterialTheme.typography.titleLarge)
    }
}

@Composable
private fun Outcome(
    text: String,
    button: String,
    onClose: () -> Unit,
    animate: Boolean,
    icon: @Composable () -> Unit,
) {
    BackHandler(onBack = onClose)
    Column(
        modifier = Modifier.fillMaxSize(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        icon()
        Spacer(Modifier.height(24.dp))
        StaggeredIn(delayMs = 450, animate = animate) {
            Text(text, style = MaterialTheme.typography.titleMedium, textAlign = TextAlign.Center)
        }
        Spacer(Modifier.height(32.dp))
        StaggeredIn(delayMs = 600, animate = animate) {
            Button(onClick = onClose) { Text(button) }
        }
    }
}

private fun FailReason.message(): Int = when (this) {
    FailReason.Microphone -> R.string.singalong_fail_mic
    FailReason.NoStems -> R.string.singalong_fail_stems
    FailReason.Playback -> R.string.singalong_fail_playback
    FailReason.TooShort -> R.string.singalong_fail_short
}

private const val LEVEL_POLL_MS = WAVE_STEP_MS.toLong()
private val STOP_SIZE = 88.dp
private val STOP_AREA = 200.dp
private const val LEVEL_RELEASE = 0.85f
/** About -2 dBFS on the meter's -60 dB scale. */
private const val CLIP_LEVEL = 0.97f
private const val OFFSET_STEP_MS = 10
