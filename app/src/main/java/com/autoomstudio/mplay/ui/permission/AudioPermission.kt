package com.autoomstudio.mplay.ui.permission

import android.Manifest
import android.app.Activity
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.activity.compose.LocalActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.LibraryMusic
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.LifecycleResumeEffect
import com.autoomstudio.mplay.R
import com.autoomstudio.mplay.ui.components.MPlayWordmark
import com.autoomstudio.mplay.ui.theme.NeonBrush
import com.autoomstudio.mplay.ui.theme.WordmarkStyle

val audioPermission: String =
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        Manifest.permission.READ_MEDIA_AUDIO
    } else {
        Manifest.permission.READ_EXTERNAL_STORAGE
    }

fun Context.hasAudioPermission(): Boolean =
    ContextCompat.checkSelfPermission(this, audioPermission) == PackageManager.PERMISSION_GRANTED

@Stable
class AudioPermissionState internal constructor(initiallyGranted: Boolean) {
    var isGranted by mutableStateOf(initiallyGranted)
        internal set

    /** True once the system will no longer show the permission dialog. */
    var isPermanentlyDenied by mutableStateOf(false)
        internal set

    internal var launchRequest: () -> Unit = {}
    internal var launchSettings: () -> Unit = {}

    fun request() = launchRequest()

    fun openSettings() = launchSettings()
}

@Composable
fun rememberAudioPermissionState(): AudioPermissionState {
    val context = LocalContext.current
    val activity = LocalActivity.current
    val state = remember(context) { AudioPermissionState(context.hasAudioPermission()) }

    val launcher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { granted ->
        state.isGranted = granted
        state.isPermanentlyDenied = !granted && activity != null &&
            !ActivityCompat.shouldShowRequestPermissionRationale(activity, audioPermission)
    }
    state.launchRequest = { launcher.launch(audioPermission) }
    state.launchSettings = { context.openAppSettings(activity) }

    LifecycleResumeEffect(state) {
        val granted = context.hasAudioPermission()
        state.isGranted = granted
        if (granted) state.isPermanentlyDenied = false
        onPauseOrDispose { }
    }

    return state
}

private fun Context.openAppSettings(activity: Activity?) {
    val intent = Intent(
        Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
        Uri.fromParts("package", packageName, null),
    )
    if (activity == null) intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    (activity ?: this).startActivity(intent)
}

@Composable
fun PermissionRationaleScreen(
    permanentlyDenied: Boolean,
    onGrant: () -> Unit,
    onOpenSettings: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .safeDrawingPadding()
            .padding(32.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp, Alignment.CenterVertically),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        MPlayWordmark(style = WordmarkStyle.copy(fontSize = 40.sp, lineHeight = 48.sp))
        Spacer(Modifier.height(8.dp))
        Box(
            modifier = Modifier
                .size(88.dp)
                .background(NeonBrush, CircleShape),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = Icons.Filled.LibraryMusic,
                contentDescription = null,
                modifier = Modifier.size(44.dp),
                tint = Color.White,
            )
        }
        Text(
            text = stringResource(R.string.permission_title),
            style = MaterialTheme.typography.headlineSmall,
            color = MaterialTheme.colorScheme.onSurface,
            textAlign = TextAlign.Center,
        )
        Text(
            text = stringResource(
                if (permanentlyDenied) R.string.permission_denied_permanently
                else R.string.permission_rationale,
            ),
            style = MaterialTheme.typography.bodyLarge,
            textAlign = TextAlign.Center,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(8.dp))
        Button(
            onClick = if (permanentlyDenied) onOpenSettings else onGrant,
            modifier = Modifier
                .fillMaxWidth()
                .height(52.dp),
        ) {
            Text(
                stringResource(
                    if (permanentlyDenied) R.string.permission_open_settings
                    else R.string.permission_grant,
                ),
            )
        }
    }
}
