package com.autoomstudio.mplay.separation

import android.content.Context
import android.net.Uri
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.IOException

/**
 * Copies a user-picked model into app storage (AI17), where the model lookup checks before the bundled
 * asset. Written to a temporary name first so a cancelled copy never looks like a model.
 */
class ModelImporter(private val context: Context) {

    suspend fun import(uri: Uri) = withContext(Dispatchers.IO) {
        val folder = File(context.filesDir, MODELS_DIR).apply { mkdirs() }
        val temp = File(folder, "$MODEL_NAME.part")
        try {
            context.contentResolver.openInputStream(uri)?.use { input ->
                temp.outputStream().use { input.copyTo(it) }
            } ?: throw IOException("Could not open $uri")
            if (temp.length() < MIN_MODEL_BYTES) throw IOException("File is too small to be the model")
            val target = File(folder, MODEL_NAME)
            target.delete()
            if (!temp.renameTo(target)) throw IOException("Could not move the model into place")
        } finally {
            temp.delete()
        }
    }

    private companion object {
        /** Must match `ModelSource.find` in the separation module. */
        const val MODELS_DIR = "models"
        const val MODEL_NAME = "htdemucs.onnx"
        const val MIN_MODEL_BYTES = 10L * 1024 * 1024
    }
}
