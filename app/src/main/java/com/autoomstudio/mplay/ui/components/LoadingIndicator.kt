package com.autoomstudio.mplay.ui.components

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.autoomstudio.mplay.R

/** Centered pulsing-equalizer loader in the theme colors. */
@Composable
fun LoadingIndicator(modifier: Modifier = Modifier) {
    val description = stringResource(R.string.loading)
    Box(modifier = modifier, contentAlignment = Alignment.Center) {
        ThemedLottie(
            animation = R.raw.anim_loading,
            staticProgress = 0.25f,
            modifier = Modifier
                .size(96.dp)
                .semantics { contentDescription = description },
        )
    }
}
