package com.autoomstudio.mp3studio.playback

import android.content.Context
import android.graphics.Bitmap
import android.net.Uri
import androidx.media3.common.util.BitmapLoader
import androidx.media3.common.util.UnstableApi
import com.autoomstudio.mp3studio.data.library.AlbumArtLoader
import com.autoomstudio.mp3studio.data.library.AppLogoArtwork
import com.google.common.util.concurrent.ListenableFuture
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.guava.await
import kotlinx.coroutines.guava.future

/** Session artwork: album-art URIs go through [AlbumArtLoader], everything else through [delegate]. */
@UnstableApi
class AlbumArtBitmapLoader(
    private val context: Context,
    private val albumArt: AlbumArtLoader,
    private val delegate: BitmapLoader,
    private val scope: CoroutineScope,
) : BitmapLoader {

    override fun supportsMimeType(mimeType: String): Boolean = delegate.supportsMimeType(mimeType)

    override fun decodeBitmap(data: ByteArray): ListenableFuture<Bitmap> = delegate.decodeBitmap(data)

    override fun loadBitmap(uri: Uri): ListenableFuture<Bitmap> {
        if (AlbumArtLoader.albumIdOf(uri.toString()) == null) return delegate.loadBitmap(uri)
        return scope.future(Dispatchers.IO) {
            val bytes = albumArt.load(uri) ?: AppLogoArtwork.jpegBytes(context)
            delegate.decodeBitmap(bytes).await()
        }
    }
}
