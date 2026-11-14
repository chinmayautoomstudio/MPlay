package com.autoomstudio.mp3studio.ui.review

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.autoomstudio.mp3studio.R
import com.autoomstudio.mp3studio.ui.components.ThemedLottie

@Composable
fun ReviewPromptDialog(
    onShown: () -> Unit,
    onRate: (Activity) -> Unit,
    onNotNow: () -> Unit,
    onDismissRequest: () -> Unit,
) {
    val context = LocalContext.current
    LaunchedEffect(Unit) { onShown() }
    AlertDialog(
        onDismissRequest = onDismissRequest,
        title = { Text(stringResource(R.string.review_prompt_title)) },
        text = {
            Column(
                modifier = Modifier.fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                ThemedLottie(
                    animation = R.raw.anim_review_stars,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(96.dp),
                    staticProgress = 0.5f,
                )
                Text(stringResource(R.string.review_prompt_message))
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    val activity = context.findActivity()
                    if (activity != null) onRate(activity) else onDismissRequest()
                },
            ) {
                Text(stringResource(R.string.review_prompt_rate))
            }
        },
        dismissButton = {
            TextButton(onClick = onNotNow) {
                Text(stringResource(R.string.review_prompt_not_now))
            }
        },
    )
}

private tailrec fun Context.findActivity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.findActivity()
    else -> null
}
