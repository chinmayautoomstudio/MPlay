package com.autoomstudio.mplay.ui.singalong

import android.Manifest
import android.app.Activity
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.media.AudioDeviceCallback
import android.media.AudioDeviceInfo
import android.media.AudioManager
import android.net.Uri
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import androidx.activity.compose.LocalActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.LifecycleResumeEffect

@Stable
class MicPermissionState(granted: Boolean) {
    var isGranted by mutableStateOf(granted)
        internal set
    var isPermanentlyDenied by mutableStateOf(false)
        internal set
    internal var launchRequest: () -> Unit = {}
    internal var launchSettings: () -> Unit = {}

    fun request() = launchRequest()

    fun openSettings() = launchSettings()
}

/** Microphone permission with "Open settings" once it has been denied for good (SA3). */
@Composable
fun rememberMicPermissionState(): MicPermissionState {
    val context = LocalContext.current
    val activity = LocalActivity.current
    val state = remember(context) { MicPermissionState(context.hasMicPermission()) }
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        state.isGranted = granted
        state.isPermanentlyDenied = !granted && activity != null &&
            !ActivityCompat.shouldShowRequestPermissionRationale(activity, Manifest.permission.RECORD_AUDIO)
    }
    state.launchRequest = { launcher.launch(Manifest.permission.RECORD_AUDIO) }
    state.launchSettings = { context.openAppSettings(activity) }
    LifecycleResumeEffect(state) {
        val granted = context.hasMicPermission()
        state.isGranted = granted
        if (granted) state.isPermanentlyDenied = false
        onPauseOrDispose { }
    }
    return state
}

private fun Context.hasMicPermission(): Boolean =
    ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED

private fun Context.openAppSettings(activity: Activity?) {
    val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.fromParts("package", packageName, null))
    if (activity == null) intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    (activity ?: this).startActivity(intent)
}

/** What the phone is playing through and recording from, for the headphone and mic advice (SA4, SA5). */
data class AudioRouting(val headphones: Boolean, val onlyBluetoothMic: Boolean)

@Composable
fun rememberAudioRouting(): AudioRouting {
    val context = LocalContext.current
    val audioManager = remember(context) { context.getSystemService(AudioManager::class.java) }
    var routing by remember { mutableStateOf(audioManager.routing()) }
    DisposableEffect(audioManager) {
        val callback = object : AudioDeviceCallback() {
            override fun onAudioDevicesAdded(addedDevices: Array<out AudioDeviceInfo>?) {
                routing = audioManager.routing()
            }

            override fun onAudioDevicesRemoved(removedDevices: Array<out AudioDeviceInfo>?) {
                routing = audioManager.routing()
            }
        }
        audioManager?.registerAudioDeviceCallback(callback, Handler(Looper.getMainLooper()))
        onDispose { audioManager?.unregisterAudioDeviceCallback(callback) }
    }
    return routing
}

private fun AudioManager?.routing(): AudioRouting {
    if (this == null) return AudioRouting(headphones = false, onlyBluetoothMic = false)
    val outputs = getDevices(AudioManager.GET_DEVICES_OUTPUTS).map { it.type }
    val inputs = getDevices(AudioManager.GET_DEVICES_INPUTS).map { it.type }
    val headphoneTypes = buildSet {
        add(AudioDeviceInfo.TYPE_WIRED_HEADSET)
        add(AudioDeviceInfo.TYPE_WIRED_HEADPHONES)
        add(AudioDeviceInfo.TYPE_USB_HEADSET)
        add(AudioDeviceInfo.TYPE_BLUETOOTH_A2DP)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) add(AudioDeviceInfo.TYPE_BLE_HEADSET)
    }
    val goodMics = setOf(
        AudioDeviceInfo.TYPE_BUILTIN_MIC,
        AudioDeviceInfo.TYPE_WIRED_HEADSET,
        AudioDeviceInfo.TYPE_USB_HEADSET,
        AudioDeviceInfo.TYPE_USB_DEVICE,
    )
    return AudioRouting(
        headphones = outputs.any { it in headphoneTypes },
        onlyBluetoothMic = inputs.isNotEmpty() && inputs.none { it in goodMics },
    )
}
