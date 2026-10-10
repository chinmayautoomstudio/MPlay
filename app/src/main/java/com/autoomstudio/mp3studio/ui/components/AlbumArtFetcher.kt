package com.autoomstudio.mp3studio.ui.components

import coil3.ImageLoader
import coil3.Uri
import coil3.decode.DataSource
import coil3.decode.ImageSource
import coil3.fetch.FetchResult
import coil3.fetch.Fetcher
import coil3.fetch.SourceFetchResult
import coil3.request.Options
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
        val bytes = withContext(Dispatchers.IO) { loader.load(uri) }
            ?: throw FileNotFoundException("No album art for $uri")
        return SourceFetchResult(
            source = ImageSource(Buffer().write(bytes), options.fileSystem),
            mimeType = null,
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
