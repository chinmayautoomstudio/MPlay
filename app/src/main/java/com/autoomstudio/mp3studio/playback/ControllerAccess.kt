package com.autoomstudio.mp3studio.playback

import androidx.media3.common.Player

/** What a controller connecting to the exported [PlaybackService] may do. */
internal enum class ControllerAccess {
    /** MPlay's own app and widget: every player command and the custom commands. */
    Full,

    /** Trusted system controllers (notification, System UI, Bluetooth, cars, watches): playback controls only. */
    Transport,

    Rejected,
}

internal fun controllerAccess(signedIn: Boolean, ownPackage: Boolean, trusted: Boolean): ControllerAccess = when {
    !signedIn -> ControllerAccess.Rejected
    ownPackage -> ControllerAccess.Full
    trusted -> ControllerAccess.Transport
    else -> ControllerAccess.Rejected
}

/** Player commands a [ControllerAccess.Transport] controller doesn't get: changing the queue, volume, speed or tracks. */
@Suppress("DEPRECATION")
internal val NON_TRANSPORT_COMMANDS: Set<Int> = setOf(
    Player.COMMAND_SET_MEDIA_ITEM,
    Player.COMMAND_CHANGE_MEDIA_ITEMS,
    Player.COMMAND_SET_PLAYLIST_METADATA,
    Player.COMMAND_SET_VOLUME,
    Player.COMMAND_SET_DEVICE_VOLUME,
    Player.COMMAND_SET_DEVICE_VOLUME_WITH_FLAGS,
    Player.COMMAND_ADJUST_DEVICE_VOLUME,
    Player.COMMAND_ADJUST_DEVICE_VOLUME_WITH_FLAGS,
    Player.COMMAND_SET_SPEED_AND_PITCH,
    Player.COMMAND_SET_TRACK_SELECTION_PARAMETERS,
    Player.COMMAND_SET_VIDEO_SURFACE,
    Player.COMMAND_SET_AUDIO_ATTRIBUTES,
)

internal fun transportPlayerCommands(defaults: Player.Commands): Player.Commands =
    defaults.buildUpon().removeAll(*NON_TRANSPORT_COMMANDS.toIntArray()).build()
