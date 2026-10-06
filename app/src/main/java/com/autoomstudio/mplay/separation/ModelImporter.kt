package com.autoomstudio.mplay.separation

import android.content.Context
import android.net.Uri
import com.autoomstudio.mplay.BuildConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.IOException
import java.security.DigestInputStream
import java.security.MessageDigest

/**
 * Copies a user-picked model into app storage (AI17), where the model lookup checks before the bundled
 * asset. Written to a temporary name first so a cancelled copy never looks like a model, and only kept
 * when its SHA-256 matches the export the separator was built for.
 */
class ModelImporter(
    private val context: Context,
    private val expectedSha256: String = BuildConfig.MODEL_SHA256,
) {

    /** The picked file is readable but is not the expected model export. */
    class WrongModelException(message: String) : IOException(message)

    suspend fun import(uri: Uri) = withContext(Dispatchers.IO) {
        val folder = File(context.filesDir, MODELS_DIR).apply { mkdirs() }
        val temp = File(folder, "$MODEL_NAME.part")
        try {
            val digest = MessageDigest.getInstance("SHA-256")
            context.contentResolver.openInputStream(uri)?.use { input ->
                DigestInputStream(input, digest).use { hashing ->
                    temp.outputStream().use { hashing.copyTo(it, BUFFER_BYTES) }
                }
            } ?: throw IOException("Could not open $uri")
            if (temp.length() < MIN_MODEL_BYTES) throw WrongModelException("File is too small to be the model")
            val actual = digest.digest().toHex()
            if (!actual.equals(expectedSha256, ignoreCase = true)) {
                throw WrongModelException("SHA-256 $actual does not match the expected model")
            }
            val target = File(folder, MODEL_NAME)
            target.delete()
            if (!temp.renameTo(target)) throw IOException("Could not move the model into place")
        } finally {
            temp.delete()
        }
    }

    private fun ByteArray.toHex(): String = joinToString("") { "%02x".format(it) }

    private companion object {
        /** Must match `ModelSource.find` in the separation module. */
        const val MODELS_DIR = "models"
        const val MODEL_NAME = "htdemucs.onnx"
        const val MIN_MODEL_BYTES = 10L * 1024 * 1024
        const val BUFFER_BYTES = 1 shl 20
    }
}
