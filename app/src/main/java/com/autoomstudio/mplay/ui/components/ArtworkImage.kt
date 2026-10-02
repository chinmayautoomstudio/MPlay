package com.autoomstudio.mplay.ui.components

import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import com.autoomstudio.mplay.ui.theme.NeonBrush

/**
 * Album art with a music-note placeholder underneath, visible when the file has no art.
 * [prominent] uses the neon gradient placeholder for large surfaces such as Now Playing.
 */
@Composable
fun ArtworkImage(
    uri: Uri?,
    contentDescription: String?,
    modifier: Modifier = Modifier,
    cornerRadius: Dp = 10.dp,
    prominent: Boolean = false,
) {
    val shape = RoundedCornerShape(cornerRadius)
    Box(
        modifier = modifier
            .clip(shape)
            .then(
                if (prominent) {
                    Modifier.background(NeonBrush)
                } else {
                    Modifier.background(MaterialTheme.colorScheme.surfaceContainerHigh)
                },
            ),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            imageVector = Icons.Filled.MusicNote,
            contentDescription = null,
            tint = if (prominent) Color.White.copy(alpha = 0.9f) else MaterialTheme.colorScheme.primary,
            modifier = Modifier.fillMaxSize(if (prominent) 0.4f else 0.45f),
        )
        if (uri != null) {
            AsyncImage(
                model = uri,
                contentDescription = contentDescription,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize(),
            )
        }
    }
}
