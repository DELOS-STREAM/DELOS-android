package com.limelight.shell

import android.view.InputDevice
import android.view.KeyEvent
import com.limelight.shell.ui.InputKinds
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.mockito.Mockito.`when`
import org.mockito.Mockito.mock

/** The shell's TV remote map (GlobalKeys.remoteButton). */
class GlobalKeysRemoteTest {
    private fun key(code: Int) = GlobalKeys.remoteButton(code)

    @Test
    fun `every remote action has its key`() {
        assertEquals(KeyEvent.KEYCODE_BUTTON_X, key(KeyEvent.KEYCODE_MENU))
        assertEquals(KeyEvent.KEYCODE_BUTTON_X, key(KeyEvent.KEYCODE_INFO))
        assertEquals(KeyEvent.KEYCODE_BUTTON_START, key(KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE))
        assertEquals(KeyEvent.KEYCODE_BUTTON_R1, key(KeyEvent.KEYCODE_CHANNEL_UP))
        assertEquals(KeyEvent.KEYCODE_BUTTON_R1, key(KeyEvent.KEYCODE_PAGE_UP))
        assertEquals(KeyEvent.KEYCODE_BUTTON_L1, key(KeyEvent.KEYCODE_CHANNEL_DOWN))
        assertEquals(KeyEvent.KEYCODE_BUTTON_L1, key(KeyEvent.KEYCODE_PAGE_DOWN))
        assertEquals(KeyEvent.KEYCODE_BUTTON_Y, key(KeyEvent.KEYCODE_PROG_YELLOW))
        assertEquals(KeyEvent.KEYCODE_BUTTON_X, key(KeyEvent.KEYCODE_PROG_BLUE))
    }

    @Test
    fun `navigation and standard keys are left alone`() {
        for (code in listOf(
            KeyEvent.KEYCODE_DPAD_UP,
            KeyEvent.KEYCODE_DPAD_DOWN,
            KeyEvent.KEYCODE_DPAD_LEFT,
            KeyEvent.KEYCODE_DPAD_RIGHT,
            KeyEvent.KEYCODE_DPAD_CENTER,
            KeyEvent.KEYCODE_BACK,
            KeyEvent.KEYCODE_ENTER,
            KeyEvent.KEYCODE_A,
            KeyEvent.KEYCODE_0,
        )) {
            assertNull(key(code))
        }
    }

    @Test
    fun `tv remote detection distinguishes remotes from gamepads and keyboards`() {
        assertFalse(InputKinds.isTvRemote(null))

        val virtualDevice = mock(InputDevice::class.java).apply {
            `when`(isVirtual).thenReturn(true)
        }
        assertFalse(InputKinds.isTvRemote(virtualDevice))

        val gamepadDevice = mock(InputDevice::class.java).apply {
            `when`(isVirtual).thenReturn(false)
            `when`(sources).thenReturn(InputDevice.SOURCE_GAMEPAD or InputDevice.SOURCE_KEYBOARD)
        }
        assertFalse(InputKinds.isTvRemote(gamepadDevice))

        val keyboardDevice = mock(InputDevice::class.java).apply {
            `when`(isVirtual).thenReturn(false)
            `when`(sources).thenReturn(InputDevice.SOURCE_KEYBOARD)
            `when`(keyboardType).thenReturn(InputDevice.KEYBOARD_TYPE_ALPHABETIC)
        }
        assertFalse(InputKinds.isTvRemote(keyboardDevice))

        val tvRemoteDpad = mock(InputDevice::class.java).apply {
            `when`(isVirtual).thenReturn(false)
            `when`(sources).thenReturn(InputDevice.SOURCE_DPAD or InputDevice.SOURCE_KEYBOARD)
            `when`(keyboardType).thenReturn(InputDevice.KEYBOARD_TYPE_NON_ALPHABETIC)
        }
        assertTrue(InputKinds.isTvRemote(tvRemoteDpad))
    }
}
