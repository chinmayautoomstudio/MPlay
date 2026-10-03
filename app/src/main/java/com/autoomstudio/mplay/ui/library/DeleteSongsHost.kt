package com.autoomstudio.mplay.ui.library

import android.Manifest
import android.app.Activity
import android.content.pm.PackageManager
import android.os.Build
import android.util.Log
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.IntentSenderRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.core.content.ContextCompat
import com.autoomstudio.mplay.R
import com.autoomstudio.mplay.data.library.SongDeleter
import com.autoomstudio.mplay.data.model.Song
import kotlinx.coroutines.launch

sealed interface DeleteResult {
    /** [requested] is how many songs the user asked to delete. */
    data class Deleted(val ids: Set<Long>, val requested: Int) : DeleteResult
    data object Cancelled : DeleteResult
    data object Failed : DeleteResult
    data object PermissionDenied : DeleteResult
}

/**
 * Deletes the songs in [request] from the device: through the system confirmation on Android 11+,
 * or after an in-app confirmation (and the storage permission) on Android 8 to 10.
 * Keep it composed while [request] is null so a pending system result is never dropped.
 */
@Composable
fun DeleteSongsHost(
    request: LongArray?,
    allSongs: List<Song>,
    deleter: SongDeleter,
    onResult: (DeleteResult) -> Unit,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val currentRequest by rememberUpdatedState(request)
    val currentOnResult by rememberUpdatedState(onResult)
    val songs = remember(request) { request?.let { ids -> allSongs.filter { it.id in ids } }.orEmpty() }
    val currentSongs by rememberUpdatedState(songs)

    var systemDialogShown by rememberSaveable { mutableStateOf(false) }
    val systemLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartIntentSenderForResult(),
    ) { result ->
        systemDialogShown = false
        val ids = currentRequest ?: return@rememberLauncherForActivityResult
        currentOnResult(
            if (result.resultCode == Activity.RESULT_OK) {
                DeleteResult.Deleted(ids.toSet(), ids.size)
            } else {
                DeleteResult.Cancelled
            },
        )
    }

    var deleting by remember { mutableStateOf(false) }
    val deleteDirectly: () -> Unit = {
        val targets = currentSongs
        deleting = true
        scope.launch {
            val deleted = deleter.deleteDirectly(targets)
            deleting = false
            currentOnResult(
                if (deleted.isEmpty()) DeleteResult.Failed else DeleteResult.Deleted(deleted, targets.size),
            )
        }
    }
    var awaitingPermission by rememberSaveable { mutableStateOf(false) }
    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { granted ->
        awaitingPermission = false
        if (granted) deleteDirectly() else currentOnResult(DeleteResult.PermissionDenied)
    }

    if (request == null) return

    if (deleter.needsSystemConfirmation) {
        LaunchedEffect(request) {
            if (systemDialogShown) return@LaunchedEffect
            if (songs.isEmpty() || Build.VERSION.SDK_INT < Build.VERSION_CODES.R) {
                currentOnResult(DeleteResult.Cancelled)
                return@LaunchedEffect
            }
            try {
                systemLauncher.launch(IntentSenderRequest.Builder(deleter.deleteRequest(songs)).build())
                systemDialogShown = true
            } catch (e: Exception) {
                Log.w("DeleteSongsHost", "Could not open the delete confirmation", e)
                currentOnResult(DeleteResult.Failed)
            }
        }
        return
    }

    if (songs.isEmpty()) {
        LaunchedEffect(request) { currentOnResult(DeleteResult.Cancelled) }
        return
    }
    if (deleting || awaitingPermission) return
    AlertDialog(
        onDismissRequest = { currentOnResult(DeleteResult.Cancelled) },
        title = {
            Text(
                if (songs.size == 1) {
                    stringResource(R.string.delete_song_title, songs.first().title)
                } else {
                    pluralStringResource(R.plurals.delete_songs_title, songs.size, songs.size)
                },
            )
        },
        text = { Text(pluralStringResource(R.plurals.delete_songs_message, songs.size)) },
        confirmButton = {
            TextButton(onClick = {
                val granted = ContextCompat.checkSelfPermission(
                    context,
                    Manifest.permission.WRITE_EXTERNAL_STORAGE,
                ) == PackageManager.PERMISSION_GRANTED
                if (granted) {
                    deleteDirectly()
                } else {
                    awaitingPermission = true
                    permissionLauncher.launch(Manifest.permission.WRITE_EXTERNAL_STORAGE)
                }
            }) { Text(stringResource(R.string.action_delete)) }
        },
        dismissButton = {
            TextButton(onClick = { currentOnResult(DeleteResult.Cancelled) }) {
                Text(stringResource(R.string.action_cancel))
            }
        },
    )
}
