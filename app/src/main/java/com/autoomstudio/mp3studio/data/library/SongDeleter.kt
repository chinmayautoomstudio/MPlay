package com.autoomstudio.mp3studio.data.library

import android.content.Context
import android.content.IntentSender
import android.os.Build
import android.provider.MediaStore
import android.util.Log
import androidx.annotation.RequiresApi
import com.autoomstudio.mp3studio.data.model.Song
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** Deletes audio files from the device through MediaStore. */
class SongDeleter(context: Context) {
    private val contentResolver = context.applicationContext.contentResolver

    /** Android 11+ needs the user to confirm in a system dialog before files the app didn't create can go. */
    val needsSystemConfirmation: Boolean get() = Build.VERSION.SDK_INT >= Build.VERSION_CODES.R

    /** The system confirmation for deleting [songs]; the files are gone once it returns RESULT_OK. */
    @RequiresApi(Build.VERSION_CODES.R)
    fun deleteRequest(songs: List<Song>): IntentSender =
        MediaStore.createDeleteRequest(contentResolver, songs.map { it.uri }).intentSender

    /**
     * Deletes [songs] directly on Android 8 to 10, which needs WRITE_EXTERNAL_STORAGE
     * (and legacy storage on Android 10). Returns the IDs that were deleted.
     */
    suspend fun deleteDirectly(songs: List<Song>): Set<Long> = withContext(Dispatchers.IO) {
        songs.filterTo(mutableListOf()) { song ->
            try {
                contentResolver.delete(song.uri, null, null) > 0
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.w(TAG, "Could not delete ${song.uri}", e)
                false
            }
        }.mapTo(LinkedHashSet()) { it.id }
    }

    private companion object {
        const val TAG = "SongDeleter"
    }
}
