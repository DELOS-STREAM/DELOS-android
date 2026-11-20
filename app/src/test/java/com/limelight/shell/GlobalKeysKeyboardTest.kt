package com.limelight.shell

import android.view.KeyEvent
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/** The shell's keyboard map (GlobalKeys.keyboardButton, docs/GUI.md, "Keyboard and pointer"). */
class GlobalKeysKeyboardTest {
    private fun key(code: Int, typing: Boolean = false, modified: Boolean = false) = GlobalKeys.keyboardButton(code, typing, modified)

    @Test fun `every shell action has its key`() {
        assertEquals(KeyEvent.KEYCODE_BUTTON_A, key(KeyEvent.KEYCODE_ENTER))
        assertEquals(KeyEvent.KEYCODE_BUTTON_A, key(KeyEvent.KEYCODE_NUMPAD_ENTER))
        assertEquals(KeyEvent.KEYCODE_BUTTON_A, key(KeyEvent.KEYCODE_SPACE))
        assertEquals(KeyEvent.KEYCODE_BUTTON_B, key(KeyEvent.KEYCODE_ESCAPE))
        assertEquals(KeyEvent.KEYCODE_BUTTON_SELECT, key(KeyEvent.KEYCODE_TAB))
        assertEquals(KeyEvent.KEYCODE_BUTTON_X, key(KeyEvent.KEYCODE_O))
        assertEquals(KeyEvent.KEYCODE_BUTTON_X, key(KeyEvent.KEYCODE_MENU))
        assertEquals(KeyEvent.KEYCODE_BUTTON_Y, key(KeyEvent.KEYCODE_F))
        assertEquals(KeyEvent.KEYCODE_BUTTON_L1, key(KeyEvent.KEYCODE_LEFT_BRACKET))
        assertEquals(KeyEvent.KEYCODE_BUTTON_R1, key(KeyEvent.KEYCODE_RIGHT_BRACKET))
    }

    @Test fun `the arrows stay the D-pad and other keys are left alone`() {
        for (code in listOf(KeyEvent.KEYCODE_DPAD_UP, KeyEvent.KEYCODE_DPAD_DOWN, KeyEvent.KEYCODE_DPAD_LEFT, KeyEvent.KEYCODE_DPAD_RIGHT, KeyEvent.KEYCODE_A, KeyEvent.KEYCODE_1)) {
            assertNull(key(code))
        }
    }

    @Test fun `while typing only Esc is the shell's`() {
        for (code in listOf(KeyEvent.KEYCODE_O, KeyEvent.KEYCODE_F, KeyEvent.KEYCODE_SPACE, KeyEvent.KEYCODE_ENTER, KeyEvent.KEYCODE_TAB, KeyEvent.KEYCODE_LEFT_BRACKET)) {
            assertNull(key(code, typing = true))
        }
        assertEquals(KeyEvent.KEYCODE_BUTTON_B, key(KeyEvent.KEYCODE_ESCAPE, typing = true))
    }

    @Test fun `a key with Ctrl, Alt or Meta is never the shell's`() {
        for (code in listOf(KeyEvent.KEYCODE_TAB, KeyEvent.KEYCODE_ESCAPE, KeyEvent.KEYCODE_O, KeyEvent.KEYCODE_ENTER)) {
            assertNull(key(code, modified = true))
        }
    }
}
