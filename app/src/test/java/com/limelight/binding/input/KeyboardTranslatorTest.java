package com.limelight.binding.input;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotEquals;

import android.app.Application;
import android.view.KeyEvent;

import com.limelight.nvstream.input.KeyboardPacket;
import com.limelight.preferences.PreferenceConfiguration;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;

/**
 * Android key codes to the Windows virtual-key codes the host injects. The expected values are
 * the VK_* constants of the Windows API (winuser.h), written out here as the second source.
 */
@RunWith(RobolectricTestRunner.class)
@Config(sdk = {33, 36}, application = Application.class)
public class KeyboardTranslatorTest {
    private KeyboardTranslator translator;

    @Before
    public void setUp() {
        translator = new KeyboardTranslator(new PreferenceConfiguration());
    }

    /** The virtual-key code inside the value sent to the host, or -1 when the key is not sent. */
    private int vk(int androidKeyCode) {
        return vk(androidKeyCode, 0);
    }

    private int vk(int androidKeyCode, int scanCode) {
        short translated = translator.translate(androidKeyCode, scanCode, -1);
        if (translated == 0) {
            return -1;
        }
        assertEquals("every key carries the 0x80 prefix", 0x80, (translated >> 8) & 0xFF);
        return translated & 0xFF;
    }

    @Test
    public void lettersAndDigits() {
        assertEquals('A', vk(KeyEvent.KEYCODE_A));
        assertEquals('M', vk(KeyEvent.KEYCODE_M));
        assertEquals('Z', vk(KeyEvent.KEYCODE_Z));
        assertEquals('0', vk(KeyEvent.KEYCODE_0));
        assertEquals('5', vk(KeyEvent.KEYCODE_5));
        assertEquals('9', vk(KeyEvent.KEYCODE_9));
    }

    @Test
    public void functionKeys() {
        assertEquals(0x70, vk(KeyEvent.KEYCODE_F1));
        assertEquals(0x73, vk(KeyEvent.KEYCODE_F4));
        assertEquals(0x7B, vk(KeyEvent.KEYCODE_F12));
    }

    @Test
    public void modifiersKeepTheirSide() {
        assertEquals(0xA0, vk(KeyEvent.KEYCODE_SHIFT_LEFT));
        assertEquals(0xA1, vk(KeyEvent.KEYCODE_SHIFT_RIGHT));
        assertEquals(0xA2, vk(KeyEvent.KEYCODE_CTRL_LEFT));
        assertEquals(0xA3, vk(KeyEvent.KEYCODE_CTRL_RIGHT));
        assertEquals(0xA4, vk(KeyEvent.KEYCODE_ALT_LEFT));
        assertEquals(0xA5, vk(KeyEvent.KEYCODE_ALT_RIGHT));
        assertEquals(0x5B, vk(KeyEvent.KEYCODE_META_LEFT));
        assertEquals(0x5C, vk(KeyEvent.KEYCODE_META_RIGHT));
    }

    @Test
    public void editingAndNavigation() {
        assertEquals(0x0D, vk(KeyEvent.KEYCODE_ENTER));
        assertEquals(0x1B, vk(KeyEvent.KEYCODE_ESCAPE));
        assertEquals(0x08, vk(KeyEvent.KEYCODE_DEL));
        assertEquals(0x2E, vk(KeyEvent.KEYCODE_FORWARD_DEL));
        assertEquals(0x2D, vk(KeyEvent.KEYCODE_INSERT));
        assertEquals(0x09, vk(KeyEvent.KEYCODE_TAB));
        assertEquals(0x20, vk(KeyEvent.KEYCODE_SPACE));
        assertEquals(0x24, vk(KeyEvent.KEYCODE_MOVE_HOME));
        assertEquals(0x23, vk(KeyEvent.KEYCODE_MOVE_END));
        assertEquals(0x21, vk(KeyEvent.KEYCODE_PAGE_UP));
        assertEquals(0x22, vk(KeyEvent.KEYCODE_PAGE_DOWN));
        assertEquals(0x25, vk(KeyEvent.KEYCODE_DPAD_LEFT));
        assertEquals(0x26, vk(KeyEvent.KEYCODE_DPAD_UP));
        assertEquals(0x27, vk(KeyEvent.KEYCODE_DPAD_RIGHT));
        assertEquals(0x28, vk(KeyEvent.KEYCODE_DPAD_DOWN));
    }

    @Test
    public void locksAndSystemKeys() {
        assertEquals(0x14, vk(KeyEvent.KEYCODE_CAPS_LOCK));
        assertEquals(0x90, vk(KeyEvent.KEYCODE_NUM_LOCK));
        assertEquals(0x91, vk(KeyEvent.KEYCODE_SCROLL_LOCK));
        assertEquals(0x13, vk(KeyEvent.KEYCODE_BREAK));
        assertEquals(0x5D, vk(KeyEvent.KEYCODE_MENU));
    }

    @Test
    public void punctuationOfAUsLayout() {
        assertEquals(0xBA, vk(KeyEvent.KEYCODE_SEMICOLON));
        assertEquals(0xBB, vk(KeyEvent.KEYCODE_EQUALS));
        assertEquals(0xBC, vk(KeyEvent.KEYCODE_COMMA));
        assertEquals(0xBD, vk(KeyEvent.KEYCODE_MINUS));
        assertEquals(0xBE, vk(KeyEvent.KEYCODE_PERIOD));
        assertEquals(0xBF, vk(KeyEvent.KEYCODE_SLASH));
        assertEquals(0xC0, vk(KeyEvent.KEYCODE_GRAVE));
        assertEquals(0xDB, vk(KeyEvent.KEYCODE_LEFT_BRACKET));
        assertEquals(0xDC, vk(KeyEvent.KEYCODE_BACKSLASH));
        assertEquals(0xDD, vk(KeyEvent.KEYCODE_RIGHT_BRACKET));
        assertEquals(0xDE, vk(KeyEvent.KEYCODE_APOSTROPHE));
        // "+" is the shifted "=" key.
        assertEquals(vk(KeyEvent.KEYCODE_EQUALS), vk(KeyEvent.KEYCODE_PLUS));
    }

    @Test
    public void numpad() {
        assertEquals(0x60, vk(KeyEvent.KEYCODE_NUMPAD_0));
        assertEquals(0x69, vk(KeyEvent.KEYCODE_NUMPAD_9));
        assertEquals(0x6A, vk(KeyEvent.KEYCODE_NUMPAD_MULTIPLY));
        assertEquals(0x6B, vk(KeyEvent.KEYCODE_NUMPAD_ADD));
        assertEquals(0x6D, vk(KeyEvent.KEYCODE_NUMPAD_SUBTRACT));
        assertEquals(0x6E, vk(KeyEvent.KEYCODE_NUMPAD_DOT));
        assertEquals(0x6F, vk(KeyEvent.KEYCODE_NUMPAD_DIVIDE));
        // The numpad digits are not the top-row digits.
        assertNotEquals(vk(KeyEvent.KEYCODE_1), vk(KeyEvent.KEYCODE_NUMPAD_1));
    }

    @Test
    public void aKeyAndroidDoesNotNameFallsBackToItsScanCode() {
        // Linux evdev: KEY_ESC = 1, KEY_A = 30.
        assertEquals(0x1B, vk(KeyEvent.KEYCODE_UNKNOWN, 1));
        assertEquals('A', vk(KeyEvent.KEYCODE_UNKNOWN, 30));
    }

    @Test
    public void theAndroidKeyCodeWinsOverTheScanCode() {
        assertEquals('B', vk(KeyEvent.KEYCODE_B, 30));
    }

    @Test
    public void anUnknownKeyWithAnUnusableScanCodeIsNotSent() {
        assertEquals(-1, vk(KeyEvent.KEYCODE_UNKNOWN, -5));
        assertEquals(-1, vk(KeyEvent.KEYCODE_UNKNOWN, 1_000_000));
    }

    @Test
    public void everyLetterAndDigitHasItsOwnCode() {
        java.util.Set<Integer> seen = new java.util.HashSet<>();
        for (int code = KeyEvent.KEYCODE_A; code <= KeyEvent.KEYCODE_Z; code++) {
            seen.add(vk(code));
        }
        for (int code = KeyEvent.KEYCODE_0; code <= KeyEvent.KEYCODE_9; code++) {
            seen.add(vk(code));
        }
        assertEquals(36, seen.size());
    }

    @Test
    public void modifierFlagsOfTheLeftModifierKeys() {
        assertEquals(KeyboardPacket.MODIFIER_SHIFT, KeyboardTranslator.getModifier((short) 0xA0));
        assertEquals(KeyboardPacket.MODIFIER_CTRL, KeyboardTranslator.getModifier((short) 0xA2));
        assertEquals(KeyboardPacket.MODIFIER_ALT, KeyboardTranslator.getModifier((short) 0xA4));
        assertEquals(KeyboardPacket.MODIFIER_META, KeyboardTranslator.getModifier((short) 0x5B));
        assertEquals(0, KeyboardTranslator.getModifier((short) 'A'));
    }

    @Test
    public void aKeyboardThatWasNeverSeenHasNoLayoutMapping() {
        assertEquals(false, translator.hasNormalizedMapping(KeyEvent.KEYCODE_A, 7));
        assertEquals(false, translator.hasNormalizedMapping(KeyEvent.KEYCODE_A, -1));
        // Removing or changing an unknown device must not throw.
        translator.onInputDeviceRemoved(7);
        translator.onInputDeviceChanged(7);
        translator.onInputDeviceAdded(7);
        assertEquals('A', vk(KeyEvent.KEYCODE_A));
    }
}
