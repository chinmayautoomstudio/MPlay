package com.autoomstudio.mp3studio.data.library

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import androidx.core.graphics.scale
import com.autoomstudio.mp3studio.R
import java.io.ByteArrayOutputStream

/** Decodes [R.drawable.ic_mp3studio_logo] for album-art placeholders and MP4 cover embedding. */
object AppLogoArtwork {
    const val SIZE_PX = 512

    /** JPEG bytes of a square logo at [sizePx], suitable for MP4 `covr` and notification bitmaps. */
    fun jpegBytes(context: Context, sizePx: Int = SIZE_PX): ByteArray {
        val bitmap = bitmap(context, sizePx)
        return try {
            ByteArrayOutputStream().use { out ->
                check(bitmap.compress(Bitmap.CompressFormat.JPEG, 90, out)) { "Could not compress logo" }
                out.toByteArray()
            }
        } finally {
            bitmap.recycle()
        }
    }

    /** Square logo bitmap at [sizePx], center-cropped. Caller owns the result. */
    fun bitmap(context: Context, sizePx: Int = SIZE_PX): Bitmap {
        val source = BitmapFactory.decodeResource(context.resources, R.drawable.ic_mp3studio_logo)
            ?: error("Missing R.drawable.ic_mp3studio_logo")
        val side = minOf(source.width, source.height)
        val cropped = Bitmap.createBitmap(
            source,
            (source.width - side) / 2,
            (source.height - side) / 2,
            side,
            side,
        )
        if (cropped !== source) source.recycle()
        if (side == sizePx) return cropped
        val scaled = cropped.scale(sizePx, sizePx)
        cropped.recycle()
        return scaled
    }
}
