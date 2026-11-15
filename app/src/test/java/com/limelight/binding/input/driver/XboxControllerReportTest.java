package com.limelight.binding.input.driver;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import android.hardware.usb.UsbDevice;
import android.hardware.usb.UsbDeviceConnection;

import com.limelight.nvstream.input.ControllerPacket;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;

/**
 * Raw USB input reports of Xbox 360 and Xbox One pads decoded into buttons, triggers and sticks.
 * The byte layouts are those of the Linux xpad driver.
 */
@RunWith(RobolectricTestRunner.class)
@Config(sdk = 33)
public class XboxControllerReportTest {
    private static final float TOLERANCE = 0.001f;

    /** The last state a driver handed to the app. */
    private static final class State implements UsbDriverListener {
        int reports;
        int buttons;
        float leftX, leftY, rightX, rightY, leftTrigger, rightTrigger;

        @Override
        public void reportControllerState(int controllerId, int buttonFlags, float leftStickX, float leftStickY,
                                          float rightStickX, float rightStickY, float leftTrigger, float rightTrigger) {
            reports++;
            buttons = buttonFlags;
            leftX = leftStickX;
            leftY = leftStickY;
            rightX = rightStickX;
            rightY = rightStickY;
            this.leftTrigger = leftTrigger;
            this.rightTrigger = rightTrigger;
        }

        @Override public void reportControllerMotion(int controllerId, byte motionType, float x, float y, float z) {}
        @Override public void deviceRemoved(AbstractController controller) {}
        @Override public void deviceAdded(AbstractController controller) {}
    }

    private final State state = new State();
    private Xbox360Controller pad360;
    private XboxOneController padOne;

    @Before
    public void setUp() {
        UsbDevice device = mock(UsbDevice.class);
        when(device.getVendorId()).thenReturn(0x045e);
        when(device.getProductId()).thenReturn(0x028e);
        UsbDeviceConnection connection = mock(UsbDeviceConnection.class);
        pad360 = new Xbox360Controller(device, connection, 7, state);
        padOne = new XboxOneController(device, connection, 8, state);
    }

    private static ByteBuffer little(int size) {
        return ByteBuffer.allocate(size).order(ByteOrder.LITTLE_ENDIAN);
    }

    /** An Xbox 360 input report: type, length, two button bytes, two trigger bytes, four stick axes. */
    private static ByteBuffer report360(int buttons1, int buttons2, int lt, int rt, int lx, int ly, int rx, int ry) {
        ByteBuffer buffer = little(20);
        buffer.put((byte) 0x00).put((byte) 0x14).put((byte) buttons1).put((byte) buttons2)
                .put((byte) lt).put((byte) rt)
                .putShort((short) lx).putShort((short) ly).putShort((short) rx).putShort((short) ry);
        buffer.position(0);
        return buffer;
    }

    /** An Xbox One input report (0x20): header, two button bytes, two 10-bit triggers, four stick axes. */
    private static ByteBuffer reportOne(int buttons1, int buttons2, int lt, int rt, int lx, int ly, int rx, int ry) {
        ByteBuffer buffer = little(18);
        buffer.put((byte) 0x20).put((byte) 0x00).put((byte) 0x01).put((byte) 0x0e)
                .put((byte) buttons1).put((byte) buttons2)
                .putShort((short) lt).putShort((short) rt)
                .putShort((short) lx).putShort((short) ly).putShort((short) rx).putShort((short) ry);
        buffer.position(0);
        return buffer;
    }

    private void read360(ByteBuffer report) {
        assertTrue(pad360.handleRead(report));
        pad360.reportInput();
    }

    private void readOne(ByteBuffer report) {
        assertTrue(padOne.handleRead(report));
        padOne.reportInput();
    }

    // ---- Xbox 360 ----

    @Test
    public void xbox360ButtonBits() {
        int[][] table = {
                // byte 2, byte 3, expected flag
                {0x01, 0, ControllerPacket.UP_FLAG},
                {0x02, 0, ControllerPacket.DOWN_FLAG},
                {0x04, 0, ControllerPacket.LEFT_FLAG},
                {0x08, 0, ControllerPacket.RIGHT_FLAG},
                {0x10, 0, ControllerPacket.PLAY_FLAG},
                {0x20, 0, ControllerPacket.BACK_FLAG},
                {0x40, 0, ControllerPacket.LS_CLK_FLAG},
                {0x80, 0, ControllerPacket.RS_CLK_FLAG},
                {0, 0x01, ControllerPacket.LB_FLAG},
                {0, 0x02, ControllerPacket.RB_FLAG},
                {0, 0x04, ControllerPacket.SPECIAL_BUTTON_FLAG},
                {0, 0x10, ControllerPacket.A_FLAG},
                {0, 0x20, ControllerPacket.B_FLAG},
                {0, 0x40, ControllerPacket.X_FLAG},
                {0, 0x80, ControllerPacket.Y_FLAG},
        };
        for (int[] row : table) {
            read360(report360(row[0], row[1], 0, 0, 0, 0, 0, 0));
            assertEquals("bits " + row[0] + "/" + row[1], row[2], state.buttons);
        }

        read360(report360(0, 0, 0, 0, 0, 0, 0, 0));
        assertEquals("everything released", 0, state.buttons);
    }

    @Test
    public void xbox360TriggersAreUnsignedBytes() {
        read360(report360(0, 0, 0xFF, 0x80, 0, 0, 0, 0));

        assertEquals(1.0f, state.leftTrigger, TOLERANCE);
        assertEquals(128 / 255f, state.rightTrigger, TOLERANCE);
    }

    @Test
    public void xbox360SticksAreScaledAndYIsFlippedToAndroidsDirection() {
        // The pad reports up as positive; Android (and the rest of the input path) uses down as positive.
        read360(report360(0, 0, 0, 0, 32767, 32767, -32768, -32768));

        assertEquals(1.0f, state.leftX, TOLERANCE);
        assertEquals(-1.0f, state.leftY, TOLERANCE);
        assertEquals(-1.0f, state.rightX, TOLERANCE);
        assertEquals(1.0f, state.rightY, TOLERANCE);

        read360(report360(0, 0, 0, 0, 0, 0, 0, 0));
        assertEquals(0f, state.leftX, TOLERANCE);
        assertEquals(0f, state.leftY, TOLERANCE);
    }

    @Test
    public void aShortXbox360ReportIsIgnored() {
        ByteBuffer cut = little(10);

        assertFalse(pad360.handleRead(cut));
    }

    // ---- Xbox One ----

    @Test
    public void xboxOneButtonBits() {
        int[][] table = {
                {0x04, 0, ControllerPacket.PLAY_FLAG},
                {0x08, 0, ControllerPacket.BACK_FLAG},
                {0x10, 0, ControllerPacket.A_FLAG},
                {0x20, 0, ControllerPacket.B_FLAG},
                {0x40, 0, ControllerPacket.X_FLAG},
                {0x80, 0, ControllerPacket.Y_FLAG},
                {0, 0x01, ControllerPacket.UP_FLAG},
                {0, 0x02, ControllerPacket.DOWN_FLAG},
                {0, 0x04, ControllerPacket.LEFT_FLAG},
                {0, 0x08, ControllerPacket.RIGHT_FLAG},
                {0, 0x10, ControllerPacket.LB_FLAG},
                {0, 0x20, ControllerPacket.RB_FLAG},
                {0, 0x40, ControllerPacket.LS_CLK_FLAG},
                {0, 0x80, ControllerPacket.RS_CLK_FLAG},
        };
        for (int[] row : table) {
            readOne(reportOne(row[0], row[1], 0, 0, 0, 0, 0, 0));
            assertEquals("bits " + row[0] + "/" + row[1], row[2], state.buttons);
        }

        readOne(reportOne(0, 0, 0, 0, 0, 0, 0, 0));
        assertEquals(0, state.buttons);
    }

    @Test
    public void xboxOneTriggersAreTenBit() {
        readOne(reportOne(0, 0, 1023, 512, 0, 0, 0, 0));

        assertEquals(1.0f, state.leftTrigger, TOLERANCE);
        assertEquals(512 / 1023f, state.rightTrigger, TOLERANCE);
    }

    @Test
    public void xboxOneSticksAreScaledAndYIsFlipped() {
        readOne(reportOne(0, 0, 0, 0, -32768, 32767, 32767, -32768));

        assertEquals(-1.0f, state.leftX, TOLERANCE);
        assertEquals(-1.0f, state.leftY, TOLERANCE);
        assertEquals(1.0f, state.rightX, TOLERANCE);
        assertEquals(1.0f, state.rightY, TOLERANCE);
    }

    @Test
    public void xboxOneGuideButtonComesInItsOwnReport() {
        ByteBuffer pressed = little(6).put(new byte[]{0x07, 0x20, 0x00, 0x02, 0x01, 0x00});
        pressed.position(0);
        readOne(pressed);
        assertEquals(ControllerPacket.SPECIAL_BUTTON_FLAG, state.buttons & ControllerPacket.SPECIAL_BUTTON_FLAG);

        ByteBuffer released = little(6).put(new byte[]{0x07, 0x20, 0x00, 0x02, 0x00, 0x00});
        released.position(0);
        readOne(released);
        assertEquals(0, state.buttons & ControllerPacket.SPECIAL_BUTTON_FLAG);

        // The other buttons are not touched by the guide report.
        readOne(reportOne(0x10, 0, 0, 0, 0, 0, 0, 0));
        assertEquals(ControllerPacket.A_FLAG, state.buttons);
    }

    @Test
    public void xboxOneGuideButtonIsNotHeldBeforeItsFirstGuideReport() {
        // Regression: the constructor stored the supported buttons as the pressed ones, and the input report
        // of an Xbox One pad never touches the guide bit, so guide stayed held until the first guide report.
        readOne(reportOne(0, 0, 0, 0, 0, 0, 0, 0));
        assertEquals(0, state.buttons);

        readOne(reportOne(0x10, 0, 0, 0, 0, 0, 0, 0));
        assertEquals(ControllerPacket.A_FLAG, state.buttons);
    }

    @Test
    public void noButtonIsPressedBeforeTheFirstReport() {
        pad360.reportInput();
        assertEquals(0, state.buttons);
        padOne.reportInput();
        assertEquals(0, state.buttons);
    }

    @Test
    public void thePadsAnnounceTheButtonsTheyHave() {
        // Regression: the supported buttons were announced as 0, which tells the PC the pad has no buttons.
        int expected = ControllerPacket.A_FLAG | ControllerPacket.B_FLAG | ControllerPacket.X_FLAG | ControllerPacket.Y_FLAG
                | ControllerPacket.UP_FLAG | ControllerPacket.DOWN_FLAG | ControllerPacket.LEFT_FLAG | ControllerPacket.RIGHT_FLAG
                | ControllerPacket.LB_FLAG | ControllerPacket.RB_FLAG
                | ControllerPacket.LS_CLK_FLAG | ControllerPacket.RS_CLK_FLAG
                | ControllerPacket.BACK_FLAG | ControllerPacket.PLAY_FLAG | ControllerPacket.SPECIAL_BUTTON_FLAG;

        assertEquals(expected, pad360.getSupportedButtonFlags());
        assertEquals(expected, padOne.getSupportedButtonFlags());
    }

    @Test
    public void shortOrUnknownXboxOneReportsAreIgnored() {
        ByteBuffer cutInput = little(8).put((byte) 0x20);
        cutInput.position(0);
        assertFalse(padOne.handleRead(cutInput));

        ByteBuffer cutMode = little(3).put((byte) 0x07);
        cutMode.position(0);
        assertFalse(padOne.handleRead(cutMode));

        ByteBuffer heartbeat = little(8).put((byte) 0x03);
        heartbeat.position(0);
        assertFalse(padOne.handleRead(heartbeat));

        assertEquals(0, state.reports);
    }

    @Test
    public void thePadsIdentifyThemselves() {
        assertEquals(7, pad360.getControllerId());
        assertEquals(8, padOne.getControllerId());
        assertEquals(0x045e, pad360.getVendorId());
        assertEquals(0x028e, pad360.getProductId());
    }
}
