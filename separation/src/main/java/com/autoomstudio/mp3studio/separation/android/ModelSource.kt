package com.autoomstudio.mp3studio.separation.android

import android.content.Context
import com.autoomstudio.mp3studio.separation.pipeline.SeparationError
import com.autoomstudio.mp3studio.separation.pipeline.SeparationException
import java.io.File
import java.io.FileInputStream
import java.io.FileNotFoundException
import java.io.RandomAccessFile
import java.nio.ByteBuffer
import java.nio.channels.FileChannel

/** Where the ONNX model lives. Both kinds are memory-mapped, never copied or read into the Java heap. */
sealed interface ModelSource {
    /** A file in app storage, for example one imported by the user or pushed with adb while testing. */
    data class FileModel(val file: File) : ModelSource

    /** An APK asset. Must be stored uncompressed (`androidResources.noCompress`) to be mappable. */
    data class AssetModel(val name: String) : ModelSource

    companion object {
        const val DEFAULT_NAME = "htdemucs.onnx"

        /** A model file in app storage wins over the bundled asset; null when neither exists. */
        fun find(context: Context, name: String = DEFAULT_NAME): ModelSource? {
            val file = File(File(context.filesDir, "models"), name)
            if (file.isFile) return FileModel(file)
            return if (assetExists(context, name)) AssetModel(name) else null
        }

        private fun assetExists(context: Context, name: String): Boolean =
            try {
                context.assets.openFd(name).close()
                true
            } catch (_: FileNotFoundException) {
                false
            }
    }
}

object ModelMapper {
    fun map(context: Context, source: ModelSource): ByteBuffer = try {
        when (source) {
            is ModelSource.FileModel -> RandomAccessFile(source.file, "r").use { file ->
                file.channel.map(FileChannel.MapMode.READ_ONLY, 0, file.length())
            }
            is ModelSource.AssetModel -> context.assets.openFd(source.name).use { fd ->
                FileInputStream(fd.fileDescriptor).use { stream ->
                    stream.channel.map(FileChannel.MapMode.READ_ONLY, fd.startOffset, fd.declaredLength)
                }
            }
        }
    } catch (e: FileNotFoundException) {
        throw SeparationException(
            SeparationError.ModelUnavailable,
            "Model $source is missing or stored compressed",
            e,
        )
    }
}
