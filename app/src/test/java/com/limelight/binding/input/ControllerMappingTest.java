package com.limelight.binding.input;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.mockito.ArgumentMatchers.anyByte;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyShort;
import static org.mockito.Mockito.atLeast;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockingDetails;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.robolectric.Shadows.shadowOf;

import android.app.Activity;
import android.os.Looper;
import android.view.InputDevice;
import android.view.KeyEvent;
import android.view.MotionEvent;

import androidx.test.core.app.ApplicationProvider;

import com.limelight.nvstream.NvConnection;
import com.limelight.nvstream.input.ControllerPacket;
import com.limelight.preferences.PreferenceConfiguration;
import com.limelight.shadows.ShadowGameManager;
import com.limelight.shadows.ShadowMoonBridge;
import com.limelight.ui.GameGestures;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.ArgumentCaptor;
import org.robolectric.Robolectric;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Buttons, sticks, triggers and the d-pad of a gamepad as the host receives them. Events carry
 * device id 0, which ControllerHandler treats as a standard pad with the Android default axes
 * (the built-in controls of a handheld and every pad without a known layout).
 */
@RunWith(RobolectricTestRunner.class)
@Config(sdk = 33, shadows = {ShadowMoonBridge.class, ShadowGameManager.class})
public class ControllerMappingTest {
    /** Far from time zero, so that no button counts as "just released". */
    private static final long T = 1_000_000;

    private Activity activity;
    private NvConnection conn;
    private GameGestures gestures;
    private PreferenceConfiguration prefs;
    private ControllerHandler handler;

    /** One packet as the connection got it. */
    private static final class Packet {
        int buttons;
        int leftTrigger;
        int rightTrigger;
        int leftX;
        int leftY;
        int rightX;
        int rightY;
    }

    @Before
    public void setUp() {
        activity = Robolectric.buildActivity(Activity.class).setup().get();
        prefs = PreferenceConfiguration.readPreferences(ApplicationProvider.getApplicationContext());
        // A long Start press would otherwise toggle mouse emulation, which is not under test here.
        prefs.mouseEmulation = false;
        newHandler();
    }

    @After
    public void tearDown() {
        handler.destroy();
    }

    private void newHandler() {
        if (handler != null) {
            handler.destroy();
        }
        conn = mock(NvConnection.class);
        gestures = mock(GameGestures.class);
        handler = new ControllerHandler(activity, conn, gestures, prefs);
        // See ControllerHandlerSendTest: let the pending service bind resolve before the test acts.
        try {
            shadowOf(Looper.getMainLooper()).idle();
        } catch (NullPointerException expectedNullBinder) {
            // nothing else is pending
        }
    }

    private int packetsSent() {
        return mockingDetails(conn).getInvocations().size();
    }

    private Packet last() {
        ArgumentCaptor<Integer> buttons = ArgumentCaptor.forClass(Integer.class);
        ArgumentCaptor<Byte> lt = ArgumentCaptor.forClass(Byte.class);
        ArgumentCaptor<Byte> rt = ArgumentCaptor.forClass(Byte.class);
        ArgumentCaptor<Short> lx = ArgumentCaptor.forClass(Short.class);
        ArgumentCaptor<Short> ly = ArgumentCaptor.forClass(Short.class);
        ArgumentCaptor<Short> rx = ArgumentCaptor.forClass(Short.class);
        ArgumentCaptor<Short> ry = ArgumentCaptor.forClass(Short.class);
        verify(conn, atLeast(1)).sendControllerInput(anyShort(), anyShort(), buttons.capture(),
                lt.capture(), rt.capture(), lx.capture(), ly.capture(), rx.capture(), ry.capture());
        Packet packet = new Packet();
        packet.buttons = buttons.getValue();
        packet.leftTrigger = lt.getValue() & 0xFF;
        packet.rightTrigger = rt.getValue() & 0xFF;
        packet.leftX = lx.getValue();
        packet.leftY = ly.getValue();
        packet.rightX = rx.getValue();
        packet.rightY = ry.getValue();
        return packet;
    }

    private boolean down(int keyCode, long time) {
        return handler.handleButtonDown(new KeyEvent(time, time, KeyEvent.ACTION_DOWN, keyCode, 0, 0, 0, 0));
    }

    private boolean up(int keyCode, long downTime, long time) {
        return handler.handleButtonUp(new KeyEvent(downTime, time, KeyEvent.ACTION_UP, keyCode, 0, 0, 0, 0));
    }

    /** A joystick move with the given axis values; every other axis rests at zero. */
    private void axes(float... axisValuePairs) {
        MotionEvent.PointerProperties[] properties = {new MotionEvent.PointerProperties()};
        properties[0].id = 0;
        MotionEvent.PointerCoords[] coords = {new MotionEvent.PointerCoords()};
        for (int i = 0; i < axisValuePairs.length; i += 2) {
            coords[0].setAxisValue((int) axisValuePairs[i], axisValuePairs[i + 1]);
        }
        MotionEvent event = MotionEvent.obtain(T, T, MotionEvent.ACTION_MOVE, 1, properties, coords,
                0, 0, 1f, 1f, 0, 0, InputDevice.SOURCE_JOYSTICK, 0);
        assertTrue(handler.handleMotionEvent(event));
        event.recycle();
    }

    // ---- buttons ----

    @Test
    public void standardPadButtons() {
        Map<Integer, Integer> table = new LinkedHashMap<>();
        table.put(KeyEvent.KEYCODE_BUTTON_A, ControllerPacket.A_FLAG);
        table.put(KeyEvent.KEYCODE_BUTTON_B, ControllerPacket.B_FLAG);
        table.put(KeyEvent.KEYCODE_BUTTON_X, ControllerPacket.X_FLAG);
        table.put(KeyEvent.KEYCODE_BUTTON_Y, ControllerPacket.Y_FLAG);
        table.put(KeyEvent.KEYCODE_BUTTON_L1, ControllerPacket.LB_FLAG);
        table.put(KeyEvent.KEYCODE_BUTTON_R1, ControllerPacket.RB_FLAG);
        table.put(KeyEvent.KEYCODE_BUTTON_THUMBL, ControllerPacket.LS_CLK_FLAG);
        table.put(KeyEvent.KEYCODE_BUTTON_THUMBR, ControllerPacket.RS_CLK_FLAG);
        table.put(KeyEvent.KEYCODE_BUTTON_START, ControllerPacket.PLAY_FLAG);
        table.put(KeyEvent.KEYCODE_MENU, ControllerPacket.PLAY_FLAG);
        table.put(KeyEvent.KEYCODE_BUTTON_SELECT, ControllerPacket.BACK_FLAG);
        table.put(KeyEvent.KEYCODE_BUTTON_MODE, ControllerPacket.SPECIAL_BUTTON_FLAG);
        table.put(KeyEvent.KEYCODE_DPAD_UP, ControllerPacket.UP_FLAG);
        table.put(KeyEvent.KEYCODE_DPAD_DOWN, ControllerPacket.DOWN_FLAG);
        table.put(KeyEvent.KEYCODE_DPAD_LEFT, ControllerPacket.LEFT_FLAG);
        table.put(KeyEvent.KEYCODE_DPAD_RIGHT, ControllerPacket.RIGHT_FLAG);
        table.put(KeyEvent.KEYCODE_DPAD_UP_LEFT, ControllerPacket.UP_FLAG | ControllerPacket.LEFT_FLAG);
        table.put(KeyEvent.KEYCODE_DPAD_DOWN_RIGHT, ControllerPacket.DOWN_FLAG | ControllerPacket.RIGHT_FLAG);
        table.put(KeyEvent.KEYCODE_DPAD_CENTER, ControllerPacket.A_FLAG);
        table.put(KeyEvent.KEYCODE_MEDIA_RECORD, ControllerPacket.MISC_FLAG);
        table.put(KeyEvent.KEYCODE_BUTTON_1, ControllerPacket.TOUCHPAD_FLAG);

        for (Map.Entry<Integer, Integer> row : table.entrySet()) {
            newHandler();
            String name = KeyEvent.keyCodeToString(row.getKey());

            assertTrue(name, down(row.getKey(), T));
            assertEquals(name, (int) row.getValue(), last().buttons);

            assertTrue(name, up(row.getKey(), T, T + 100));
            assertEquals(name + " released", 0, last().buttons);
        }
    }

    @Test
    public void heldButtonsAddUpAndReleaseOneByOne() {
        down(KeyEvent.KEYCODE_BUTTON_A, T);
        down(KeyEvent.KEYCODE_BUTTON_X, T + 10);
        down(KeyEvent.KEYCODE_BUTTON_R1, T + 20);
        assertEquals(ControllerPacket.A_FLAG | ControllerPacket.X_FLAG | ControllerPacket.RB_FLAG, last().buttons);

        up(KeyEvent.KEYCODE_BUTTON_X, T + 10, T + 200);
        assertEquals(ControllerPacket.A_FLAG | ControllerPacket.RB_FLAG, last().buttons);

        up(KeyEvent.KEYCODE_BUTTON_A, T, T + 300);
        up(KeyEvent.KEYCODE_BUTTON_R1, T + 20, T + 400);
        assertEquals(0, last().buttons);
    }

    @Test
    public void keysThatAreNotGamepadButtonsAreLeftToTheKeyboardPath() {
        assertFalse(down(KeyEvent.KEYCODE_A, T));
        assertFalse(up(KeyEvent.KEYCODE_A, T, T + 100));
        assertFalse(down(KeyEvent.KEYCODE_VOLUME_UP, T));

        assertEquals(0, packetsSent());
    }

    @Test
    public void theSystemBackKeyIsNotCapturedAsAButton() {
        assertFalse(down(KeyEvent.KEYCODE_BACK, T));
        assertFalse(up(KeyEvent.KEYCODE_BACK, T, T + 100));

        assertEquals(0, packetsSent());
    }

    @Test
    public void flippedFaceButtonsSwapAWithBAndXWithY() {
        prefs.flipFaceButtons = true;
        newHandler();

        down(KeyEvent.KEYCODE_BUTTON_A, T);
        assertEquals(ControllerPacket.B_FLAG, last().buttons);
        up(KeyEvent.KEYCODE_BUTTON_A, T, T + 100);

        down(KeyEvent.KEYCODE_BUTTON_Y, T + 200);
        assertEquals(ControllerPacket.X_FLAG, last().buttons);
        up(KeyEvent.KEYCODE_BUTTON_Y, T + 200, T + 300);
        assertEquals(0, last().buttons);

        // Shoulder buttons are not affected.
        down(KeyEvent.KEYCODE_BUTTON_L1, T + 400);
        assertEquals(ControllerPacket.LB_FLAG, last().buttons);
    }

    // ---- button combinations that stand in for missing buttons ----

    @Test
    public void startPlusLbIsSelectOnAPadWithoutASelectButton() {
        down(KeyEvent.KEYCODE_BUTTON_L1, T);
        down(KeyEvent.KEYCODE_BUTTON_START, T + 10);

        assertEquals(ControllerPacket.BACK_FLAG, last().buttons);

        up(KeyEvent.KEYCODE_BUTTON_START, T + 10, T + 200);
        up(KeyEvent.KEYCODE_BUTTON_L1, T, T + 300);
        assertEquals(0, last().buttons);
    }

    @Test
    public void startPlusRbIsTheGuideButtonOnAPadWithoutOne() {
        down(KeyEvent.KEYCODE_BUTTON_R1, T);
        down(KeyEvent.KEYCODE_BUTTON_START, T + 10);

        assertEquals(ControllerPacket.SPECIAL_BUTTON_FLAG, last().buttons);

        up(KeyEvent.KEYCODE_BUTTON_START, T + 10, T + 200);
        up(KeyEvent.KEYCODE_BUTTON_R1, T, T + 300);
        assertEquals(0, last().buttons);
    }

    @Test
    public void startPlusSelectIsTheGuideButtonOnceASelectButtonExists() {
        down(KeyEvent.KEYCODE_BUTTON_SELECT, T);
        down(KeyEvent.KEYCODE_BUTTON_START, T + 10);

        assertEquals(ControllerPacket.SPECIAL_BUTTON_FLAG, last().buttons);

        up(KeyEvent.KEYCODE_BUTTON_START, T + 10, T + 200);
        up(KeyEvent.KEYCODE_BUTTON_SELECT, T, T + 300);
        assertEquals(0, last().buttons);
    }

    @Test
    public void aPadWithARealGuideButtonSendsStartAndSelectAsThemselves() {
        down(KeyEvent.KEYCODE_BUTTON_MODE, T);
        up(KeyEvent.KEYCODE_BUTTON_MODE, T, T + 100);

        down(KeyEvent.KEYCODE_BUTTON_SELECT, T + 200);
        down(KeyEvent.KEYCODE_BUTTON_START, T + 210);

        assertEquals(ControllerPacket.PLAY_FLAG | ControllerPacket.BACK_FLAG, last().buttons);
    }

    // ---- triggers ----

    @Test
    public void triggersAsButtons() {
        down(KeyEvent.KEYCODE_BUTTON_L2, T);
        assertEquals(0xFF, last().leftTrigger);
        assertEquals(0, last().rightTrigger);

        down(KeyEvent.KEYCODE_BUTTON_R2, T + 10);
        assertEquals(0xFF, last().rightTrigger);

        up(KeyEvent.KEYCODE_BUTTON_L2, T, T + 100);
        up(KeyEvent.KEYCODE_BUTTON_R2, T + 10, T + 200);
        assertEquals(0, last().leftTrigger);
        assertEquals(0, last().rightTrigger);
        assertEquals(0, last().buttons);
    }

    @Test
    public void triggersAsAxes() {
        axes(MotionEvent.AXIS_BRAKE, 1.0f);
        assertEquals(0xFF, last().leftTrigger);
        assertEquals(0, last().rightTrigger);

        axes(MotionEvent.AXIS_BRAKE, 0.5f, MotionEvent.AXIS_GAS, 1.0f);
        assertEquals(127, last().leftTrigger);
        assertEquals(0xFF, last().rightTrigger);

        axes();
        assertEquals(0, last().leftTrigger);
        assertEquals(0, last().rightTrigger);
    }

    @Test
    public void anAnalogTriggerSilencesItsDuplicateDigitalButton() {
        axes(MotionEvent.AXIS_BRAKE, 0.25f);
        int analog = last().leftTrigger;

        // Pads report the trigger both ways; the button must not jump the value to full.
        assertTrue(down(KeyEvent.KEYCODE_BUTTON_L2, T + 10));

        assertEquals(analog, last().leftTrigger);
        assertTrue(analog > 0 && analog < 0xFF);
    }

    // ---- sticks ----

    @Test
    public void sticksAreScaledToTheFullRangeWithUpPositive() {
        axes(MotionEvent.AXIS_X, 1.0f);
        assertEquals(0x7FFE, last().leftX);
        assertEquals(0, last().leftY);

        axes(MotionEvent.AXIS_X, -1.0f);
        assertEquals(-0x7FFE, last().leftX);

        // Android's Y grows downwards, the host's upwards.
        axes(MotionEvent.AXIS_Y, 1.0f);
        assertEquals(-0x7FFE, last().leftY);
        axes(MotionEvent.AXIS_Y, -1.0f);
        assertEquals(0x7FFE, last().leftY);
    }

    @Test
    public void theRightStickUsesZAndRz() {
        axes(MotionEvent.AXIS_Z, 1.0f, MotionEvent.AXIS_RZ, -1.0f);

        assertEquals(0x7FFE, last().rightX);
        assertEquals(0x7FFE, last().rightY);
        assertEquals(0, last().leftX);
        assertEquals(0, last().leftY);
    }

    @Test
    public void deadzoneAndStickScaling() {
        // The default dead zone is a few percent of the travel.
        axes(MotionEvent.AXIS_X, 0.02f, MotionEvent.AXIS_Y, 0.02f);
        assertEquals(0, last().leftX);
        assertEquals(0, last().leftY);

        // Outside it the value is passed on unscaled: the host applies its own dead zone.
        axes(MotionEvent.AXIS_X, 0.5f);
        assertEquals(0x7FFE / 2, last().leftX, 1);

        axes(MotionEvent.AXIS_Z, 0.02f);
        assertEquals(0, last().rightX);
        axes(MotionEvent.AXIS_Z, -0.5f);
        assertEquals(-0x7FFE / 2, last().rightX, 1);
    }

    @Test
    public void theDeadzoneIsRoundNotSquare() {
        // Each axis alone is inside the default 5 % radius, together they are outside it.
        axes(MotionEvent.AXIS_X, 0.04f, MotionEvent.AXIS_Y, 0.04f);

        assertTrue(last().leftX != 0);
        assertTrue(last().leftY != 0);
    }

    @Test
    public void aLargerDeadzoneSettingSwallowsMoreTravel() {
        prefs.deadzonePercentage = 30;
        newHandler();

        axes(MotionEvent.AXIS_X, 0.25f);
        assertEquals(0, last().leftX);

        axes(MotionEvent.AXIS_X, 0.6f);
        assertTrue(last().leftX > 0);
    }

    // ---- d-pad ----

    @Test
    public void dpadFromHatAxes() {
        axes(MotionEvent.AXIS_HAT_X, -1.0f);
        assertEquals(ControllerPacket.LEFT_FLAG, last().buttons);

        axes(MotionEvent.AXIS_HAT_X, 1.0f);
        assertEquals(ControllerPacket.RIGHT_FLAG, last().buttons);

        axes(MotionEvent.AXIS_HAT_Y, -1.0f);
        assertEquals(ControllerPacket.UP_FLAG, last().buttons);

        axes(MotionEvent.AXIS_HAT_Y, 1.0f);
        assertEquals(ControllerPacket.DOWN_FLAG, last().buttons);

        axes(MotionEvent.AXIS_HAT_X, 1.0f, MotionEvent.AXIS_HAT_Y, -1.0f);
        assertEquals(ControllerPacket.RIGHT_FLAG | ControllerPacket.UP_FLAG, last().buttons);

        axes();
        assertEquals(0, last().buttons);
    }

    @Test
    public void theHatDoesNotDisturbHeldButtons() {
        down(KeyEvent.KEYCODE_BUTTON_A, T);

        axes(MotionEvent.AXIS_HAT_X, 1.0f);
        assertEquals(ControllerPacket.A_FLAG | ControllerPacket.RIGHT_FLAG, last().buttons);

        axes();
        assertEquals(ControllerPacket.A_FLAG, last().buttons);
    }

    @Test
    public void aHatSilencesTheDuplicateDpadKeys() {
        axes(MotionEvent.AXIS_HAT_X, 1.0f, MotionEvent.AXIS_HAT_Y, 1.0f);
        axes();
        int before = packetsSent();

        // Pads with a hat also send d-pad key events for the same press.
        assertTrue(down(KeyEvent.KEYCODE_DPAD_LEFT, T + 10));
        assertTrue(down(KeyEvent.KEYCODE_DPAD_UP, T + 20));

        assertEquals(0, last().buttons);
        assertEquals(before, packetsSent());
    }

    // ---- the on-screen controls feed the same pad ----

    @Test
    public void onScreenControlsReportTheirStateAsIs() {
        handler.reportOscState(ControllerPacket.A_FLAG | ControllerPacket.LB_FLAG,
                (short) 1000, (short) -2000, (short) 3000, (short) -4000, (byte) 10, (byte) 20);

        Packet packet = last();
        assertEquals(ControllerPacket.A_FLAG | ControllerPacket.LB_FLAG, packet.buttons);
        assertEquals(1000, packet.leftX);
        assertEquals(-2000, packet.leftY);
        assertEquals(3000, packet.rightX);
        assertEquals(-4000, packet.rightY);
        assertEquals(10, packet.leftTrigger);
        assertEquals(20, packet.rightTrigger);
    }

    @Test
    public void nothingIsSentBeforeAnyInput() {
        verify(conn, never()).sendControllerInput(anyShort(), anyShort(), anyInt(),
                anyByte(), anyByte(), anyShort(), anyShort(), anyShort(), anyShort());
    }
}
