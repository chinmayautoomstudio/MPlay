package com.autoomstudio.mplay.data.clip

import android.media.RingtoneManager
import android.provider.MediaStore

enum class SoundType(val ringtoneManagerType: Int, val mediaStoreColumn: String) {
    Ringtone(RingtoneManager.TYPE_RINGTONE, MediaStore.Audio.Media.IS_RINGTONE),
    Notification(RingtoneManager.TYPE_NOTIFICATION, MediaStore.Audio.Media.IS_NOTIFICATION),
    Alarm(RingtoneManager.TYPE_ALARM, MediaStore.Audio.Media.IS_ALARM),
}
