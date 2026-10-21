package com.autoomstudio.mp3studio.data.plan

import android.annotation.SuppressLint
import android.content.Context
import android.os.Build
import android.provider.Settings
import com.autoomstudio.mp3studio.data.account.Nonce

/**
 * Identifies this phone for the one-trial-per-device check (PRD TR5) and the two-device sign-in limit
 * without sending the raw ID: a SHA-256 of `ANDROID_ID`, which is fixed per app signing key, user and
 * device and survives reinstalling the app.
 */
object DeviceId {
    const val FALLBACK_NAME = "Android device"
    const val MAX_NAME_LENGTH = 80

    @SuppressLint("HardwareIds")
    fun of(context: Context): String? =
        Settings.Secure.getString(context.contentResolver, Settings.Secure.ANDROID_ID)
            ?.takeIf { it.isNotBlank() }
            ?.let { Nonce.sha256Hex("mp3studio:$it") }

    fun name(): String = formatName(Build.MANUFACTURER, Build.MODEL)

    /** "Google Pixel 8" when the model already starts with the brand; otherwise "Brand Model". */
    fun formatName(manufacturer: String?, model: String?): String {
        val brand = manufacturer?.trim().orEmpty()
        val device = model?.trim().orEmpty()
        val combined = when {
            brand.isEmpty() -> device
            device.isEmpty() -> brand
            device.startsWith(brand, ignoreCase = true) -> device
            else -> "$brand $device"
        }.replace(WHITESPACE, " ").trim()
        return combined.take(MAX_NAME_LENGTH).ifBlank { FALLBACK_NAME }
    }

    private val WHITESPACE = Regex("\\s+")
}
