package com.autoomstudio.mp3studio.data.plan

import android.annotation.SuppressLint
import android.content.Context
import android.provider.Settings
import com.autoomstudio.mp3studio.data.account.Nonce

/**
 * Identifies this phone for the one-trial-per-device check (PRD TR5) without sending the raw ID: a SHA-256 of
 * `ANDROID_ID`, which is fixed per app signing key, user and device and survives reinstalling the app.
 */
object DeviceId {
    @SuppressLint("HardwareIds")
    fun of(context: Context): String? =
        Settings.Secure.getString(context.contentResolver, Settings.Secure.ANDROID_ID)
            ?.takeIf { it.isNotBlank() }
            ?.let { Nonce.sha256Hex("mp3studio:$it") }
}
