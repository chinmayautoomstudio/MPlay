package com.autoomstudio.mplay.ui.separation

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.autoomstudio.mplay.R
import com.autoomstudio.mplay.data.stems.SeparationJobEntity

/** Floats over every screen while a song is being separated; tapping it opens the queue. */
@Composable
fun SeparationProgressPill(job: SeparationJobEntity?, onClick: () -> Unit, modifier: Modifier = Modifier) {
    // Keeps the finished job on screen while the pill slides away.
    val shown = remember { mutableStateOf(job) }.apply { if (job != null) value = job }.value
    val description = stringResource(R.string.separation_pill_description)
    AnimatedVisibility(
        visible = job != null,
        enter = slideInVertically { it } + fadeIn(),
        exit = slideOutVertically { it } + fadeOut(),
        modifier = modifier,
    ) {
        if (shown == null) return@AnimatedVisibility
        Surface(
            onClick = onClick,
            shape = CircleShape,
            color = MaterialTheme.colorScheme.secondaryContainer,
            contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
            shadowElevation = 6.dp,
            modifier = Modifier
                .widthIn(max = 360.dp)
                .semantics { contentDescription = description },
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                modifier = Modifier.padding(start = 14.dp, end = 20.dp, top = 8.dp, bottom = 8.dp),
            ) {
                if (shown.progress > 0f) {
                    CircularProgressIndicator(
                        progress = { shown.progress },
                        modifier = Modifier.size(22.dp),
                        strokeWidth = 2.5.dp,
                        color = MaterialTheme.colorScheme.primary,
                    )
                } else {
                    CircularProgressIndicator(modifier = Modifier.size(22.dp), strokeWidth = 2.5.dp)
                }
                Column {
                    Text(
                        text = stringResource(R.string.separation_pill, shown.title),
                        style = MaterialTheme.typography.labelLarge,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Text(
                        text = progressText(shown),
                        style = MaterialTheme.typography.bodySmall,
                        maxLines = 1,
                    )
                }
            }
        }
    }
}

@Composable
private fun progressText(job: SeparationJobEntity): String {
    val percent = (job.progress * 100).toInt()
    val remaining = job.remainingMs
    return when {
        job.isCoolingDown -> stringResource(R.string.separation_pill_cooling, percent)
        job.progress <= 0f -> stringResource(R.string.separation_notification_preparing)
        remaining != null -> stringResource(R.string.separation_notification_percent_eta, percent, remainingText(remaining))
        else -> stringResource(R.string.separation_notification_percent, percent)
    }
}
