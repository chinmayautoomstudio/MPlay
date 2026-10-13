package com.autoomstudio.mp3studio.ui.components

import android.net.Uri
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import coil3.compose.LocalPlatformContext
import coil3.request.ImageRequest
import com.autoomstudio.mp3studio.R
import com.autoomstudio.mp3studio.ui.theme.NeonBrush

/**
 * Album art with the app logo underneath, visible when the file has no art or the load fails.
 * [prominent] uses the neon gradient behind the logo for large surfaces such as Now Playing.
 */
@Composable
fun ArtworkImage(
    uri: Uri?,
    contentDescription: String?,
    modifier: Modifier = Modifier,
    cornerRadius: Dp = 10.dp,
    prominent: Boolean = false,
    /** Decode at this size instead of the first measured size; needed when the image grows after loading. */
    requestSizePx: Int? = null,
) {
    val context = LocalPlatformContext.current
    val model = remember(uri, requestSizePx, context) {
        if (uri == null || requestSizePx == null) {
            uri
        } else {
            ImageRequest.Builder(context).data(uri).size(requestSizePx).build()
        }
    }
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
        Image(
            painter = painterResource(R.drawable.ic_mp3studio_logo),
            contentDescription = if (uri == null) contentDescription else null,
            contentScale = ContentScale.Crop,
            modifier = Modifier.fillMaxSize(),
        )
        if (uri != null) {
            AsyncImage(
                model = model,
                contentDescription = contentDescription,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize(),
            )
        }
    }
}
