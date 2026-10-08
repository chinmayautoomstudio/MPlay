package com.autoomstudio.mp3studio.data.clip

import android.content.Context
import android.media.RingtoneManager
import android.net.Uri
import android.provider.Settings

class RingtoneSetter(private val context: Context) {

    fun canWrite(): Boolean = Settings.System.canWrite(context)

    fun current(type: SoundType): Uri? =
        RingtoneManager.getActualDefaultRingtoneUri(context, type.ringtoneManagerType)

    /** Returns false when the system refuses, which happens if the permission was revoked meanwhile. */
    fun setDefault(type: SoundType, uri: Uri?): Boolean = try {
        RingtoneManager.setActualDefaultRingtoneUri(context, type.ringtoneManagerType, uri)
        true
    } catch (_: SecurityException) {
        false
    }
}
