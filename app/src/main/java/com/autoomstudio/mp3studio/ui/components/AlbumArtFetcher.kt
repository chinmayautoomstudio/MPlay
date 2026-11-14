package com.autoomstudio.mp3studio.ui.components

import coil3.ImageLoader
import coil3.Uri
import coil3.decode.DataSource
import coil3.decode.ImageSource
import coil3.fetch.FetchResult
import coil3.fetch.Fetcher
import coil3.fetch.SourceFetchResult
import coil3.request.Options
import coil3.size.Dimension
import coil3.size.Size
import com.autoomstudio.mp3studio.data.library.AlbumArtLoader
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okio.Buffer
import java.io.FileNotFoundException

/** Loads MediaStore album-art URIs through [AlbumArtLoader], so songs in Downloads still get their art. */
class AlbumArtFetcher(
    private val uri: android.net.Uri,
    private val options: Options,
    private val loader: AlbumArtLoader,
) : Fetcher {

    override suspend fun fetch(): FetchResult {
        val bytes = withContext(Dispatchers.IO) {
            val raw = loader.load(uri) ?: throw FileNotFoundException("No album art for $uri")
            val maxSide = options.size.maxSidePx()
            if (maxSide != null) AlbumArtLoader.subsampledJpeg(raw, maxSide) else raw
        }
        return SourceFetchResult(
            source = ImageSource(Buffer().write(bytes), options.fileSystem),
            mimeType = "image/jpeg",
            dataSource = DataSource.DISK,
        )
    }

    class Factory(private val loader: AlbumArtLoader) : Fetcher.Factory<Uri> {
        override fun create(data: Uri, options: Options, imageLoader: ImageLoader): Fetcher? {
            val uriString = data.toString()
            if (AlbumArtLoader.albumIdOf(uriString) == null) return null
            return AlbumArtFetcher(android.net.Uri.parse(uriString), options, loader)
        }
    }
}

private fun Size.maxSidePx(): Int? {
    if (this == Size.ORIGINAL) return null
    val w = (width as? Dimension.Pixels)?.px ?: return null
    val h = (height as? Dimension.Pixels)?.px ?: return null
    return maxOf(w, h).coerceAtLeast(1)
}
