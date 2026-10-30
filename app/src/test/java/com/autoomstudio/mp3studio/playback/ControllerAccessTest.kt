package com.autoomstudio.mp3studio.playback

import androidx.media3.common.Player
import androidx.media3.session.MediaSession
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ControllerAccessTest {

    @Test
    fun `nobody connects while signed out`() {
        assertEquals(ControllerAccess.Rejected, controllerAccess(signedIn = false, ownPackage = true, trusted = true))
        assertEquals(ControllerAccess.Rejected, controllerAccess(signedIn = false, ownPackage = false, trusted = true))
        assertEquals(
            ControllerAccess.Rejected,
            controllerAccess(signedIn = false, ownPackage = false, trusted = false, legacy = true),
        )
    }

    @Test
    fun `MPlay itself gets full access`() {
        assertEquals(ControllerAccess.Full, controllerAccess(signedIn = true, ownPackage = true, trusted = false))
    }

    @Test
    fun `trusted system controllers get playback controls only`() {
        assertEquals(ControllerAccess.Transport, controllerAccess(signedIn = true, ownPackage = false, trusted = true))
    }

    @Test
    fun `legacy platform controllers get transport when signed in`() {
        assertEquals(
            ControllerAccess.Transport,
            controllerAccess(signedIn = true, ownPackage = false, trusted = false, legacy = true),
        )
    }

    @Test
    fun `other apps are refused`() {
        assertEquals(ControllerAccess.Rejected, controllerAccess(signedIn = true, ownPackage = false, trusted = false))
        assertEquals(
            ControllerAccess.Rejected,
            controllerAccess(signedIn = true, ownPackage = false, trusted = false, legacy = false),
        )
    }

    @Test
    fun `legacy package name is recognized`() {
        assertEquals(
            MediaSession.ControllerInfo.LEGACY_CONTROLLER_PACKAGE_NAME,
            "android.media.session.MediaController",
        )
        assertEquals(0, MediaSession.ControllerInfo.LEGACY_CONTROLLER_VERSION)
    }

    @Test
    fun `transport keeps playback controls`() {
        listOf(
            Player.COMMAND_PLAY_PAUSE,
            Player.COMMAND_SEEK_TO_NEXT,
            Player.COMMAND_SEEK_TO_PREVIOUS,
            Player.COMMAND_SEEK_IN_CURRENT_MEDIA_ITEM,
            Player.COMMAND_STOP,
            Player.COMMAND_SET_SHUFFLE_MODE,
            Player.COMMAND_SET_REPEAT_MODE,
            Player.COMMAND_GET_CURRENT_MEDIA_ITEM,
            Player.COMMAND_GET_TIMELINE,
            Player.COMMAND_GET_METADATA,
        ).forEach { assertFalse("command $it", it in NON_TRANSPORT_COMMANDS) }
    }

    @Test
    fun `transport can't change the queue`() {
        assertTrue(Player.COMMAND_SET_MEDIA_ITEM in NON_TRANSPORT_COMMANDS)
        assertTrue(Player.COMMAND_CHANGE_MEDIA_ITEMS in NON_TRANSPORT_COMMANDS)
    }
}
