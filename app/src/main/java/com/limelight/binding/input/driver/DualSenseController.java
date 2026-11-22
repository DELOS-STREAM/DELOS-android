package com.limelight.binding.input.driver;

import android.hardware.usb.UsbConstants;
import android.hardware.usb.UsbDevice;
import android.hardware.usb.UsbDeviceConnection;
import android.hardware.usb.UsbEndpoint;
import android.hardware.usb.UsbInterface;

import com.limelight.LimeLog;
import com.limelight.nvstream.input.ControllerPacket;
import com.limelight.nvstream.jni.MoonBridge;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;

/**
 * A wired DualSense (and DualSense Edge) driven over its USB HID interface (issue #125): Android has no API for its adaptive
 * triggers, so DELOS reads input report 0x01 itself and writes output report 0x02 with rumble, light bar, player LEDs and the
 * trigger effects a PC host (Sunshine's 0x5503) or a PS5 (data type 11) sends. Claiming the HID interface takes the pad from
 * Android while DELOS holds it; its USB audio interface (speaker, voice-coil haptics) stays with Android. Used when the USB
 * driver setting claims all controllers ("bind all USB"), like the other drivers for pads Android already knows.
 * Report layouts: Linux hid-playstation.c (dualsense_input_report, dualsense_output_report_common) and SDL SDL_hidapi_ps5.c.
 */
public class DualSenseController extends AbstractController {
    private static final int VENDOR_SONY = 0x054c;
    private static final int PRODUCT_DUALSENSE = 0x0ce6;
    private static final int PRODUCT_DUALSENSE_EDGE = 0x0df2;

    /** USB output report 0x02: the report id and the 47-byte common block, padded to 63 bytes. */
    private static final int OUTPUT_SIZE = 63;
    private static final int FLAG0_RUMBLE = 0x01, FLAG0_HAPTICS_SELECT = 0x02, FLAG0_RIGHT_TRIGGER = 0x04, FLAG0_LEFT_TRIGGER = 0x08;
    private static final int FLAG1_LIGHTBAR = 0x04, FLAG1_PLAYER_LEDS = 0x10;
    private static final int FLAG2_LIGHTBAR_SETUP = 0x02;
    /** Report offsets (report id at 0). */
    private static final int OUT_FLAG0 = 1, OUT_FLAG1 = 2, OUT_MOTOR_RIGHT = 3, OUT_MOTOR_LEFT = 4, OUT_RIGHT_TRIGGER = 11, OUT_LEFT_TRIGGER = 22,
            OUT_FLAG2 = 39, OUT_LIGHTBAR_SETUP = 42, OUT_PLAYER_LEDS = 44, OUT_RED = 45;
    /**
     * Raw sensor counts (hid-playstation without calibration data): the gyro's 32767 is 2048 deg/s (about 16 per deg/s); the
     * accelerometer's 8192 is 1 g.
     */
    private static final float GYRO_PER_DEG_S = 32767f / 2048f, ACCEL_PER_G = 8192f;

    private final UsbDevice device;
    private final UsbDeviceConnection connection;
    private UsbInterface hid;
    private UsbEndpoint inEndpt, outEndpt;
    private Thread inputThread;
    private volatile boolean stopped = false;

    // The output state: every write sends all of it, with the flags of what changed
    private final Object outputLock = new Object();
    private int motorLeft, motorRight;
    private final byte[] rightEffect = new byte[11], leftEffect = new byte[11];
    private int red, green, blue = 64;

    public static boolean canClaimDevice(UsbDevice device) {
        return device.getVendorId() == VENDOR_SONY && (device.getProductId() == PRODUCT_DUALSENSE || device.getProductId() == PRODUCT_DUALSENSE_EDGE);
    }

    public DualSenseController(UsbDevice device, UsbDeviceConnection connection, int deviceId, UsbDriverListener listener) {
        super(deviceId, listener, device.getVendorId(), device.getProductId());
        this.device = device;
        this.connection = connection;
        this.type = MoonBridge.LI_CTYPE_PS;
        this.capabilities = (short) (MoonBridge.LI_CCAP_GYRO | MoonBridge.LI_CCAP_ACCEL | MoonBridge.LI_CCAP_RUMBLE | MoonBridge.LI_CCAP_RGB_LED);
        this.supportedButtonFlags = ControllerPacket.A_FLAG | ControllerPacket.B_FLAG | ControllerPacket.X_FLAG | ControllerPacket.Y_FLAG |
                ControllerPacket.UP_FLAG | ControllerPacket.DOWN_FLAG | ControllerPacket.LEFT_FLAG | ControllerPacket.RIGHT_FLAG |
                ControllerPacket.LB_FLAG | ControllerPacket.RB_FLAG | ControllerPacket.LS_CLK_FLAG | ControllerPacket.RS_CLK_FLAG |
                ControllerPacket.PLAY_FLAG | ControllerPacket.BACK_FLAG | ControllerPacket.SPECIAL_BUTTON_FLAG |
                ControllerPacket.TOUCHPAD_FLAG | ControllerPacket.MISC_FLAG;
    }

    @Override
    public boolean start() {
        for (int i = 0; i < device.getInterfaceCount(); i++) {
            UsbInterface iface = device.getInterface(i);
            if (iface.getInterfaceClass() == UsbConstants.USB_CLASS_HID) {
                hid = iface;
                break;
            }
        }
        if (hid == null || !connection.claimInterface(hid, true)) {
            LimeLog.warning("DualSense: HID interface not claimed");
            return false;
        }
        for (int i = 0; i < hid.getEndpointCount(); i++) {
            UsbEndpoint e = hid.getEndpoint(i);
            if (e.getDirection() == UsbConstants.USB_DIR_IN) inEndpt = e;
            else outEndpt = e;
        }
        if (inEndpt == null || outEndpt == null) {
            LimeLog.warning("DualSense: missing endpoint");
            return false;
        }
        inputThread = new Thread(this::readInput, "dualsense-input");
        inputThread.start();
        return true;
    }

    private void readInput() {
        // The light bar takes a colour only after its setup is sent on its own (as hid-playstation does), then the colour
        synchronized (outputLock) {
            writeOutput(0, 0, FLAG2_LIGHTBAR_SETUP);
            writeOutput(0, FLAG1_LIGHTBAR | FLAG1_PLAYER_LEDS, 0);
        }
        notifyDeviceAdded();
        byte[] buffer = new byte[64];
        while (!stopped && !Thread.currentThread().isInterrupted()) {
            int res = connection.bulkTransfer(inEndpt, buffer, buffer.length, 1000);
            if (res < 0) {
                if (stopped) break;
                continue;
            }
            if (res >= 41 && buffer[0] == 0x01 && handleRead(ByteBuffer.wrap(buffer, 0, res).order(ByteOrder.LITTLE_ENDIAN))) {
                reportInput();
                reportMotion();
            }
        }
    }

    /** Input report 0x01 over USB: sticks 1..4, triggers 5..6, buttons 8..10, gyro 16..21, accelerometer 22..27. */
    private boolean handleRead(ByteBuffer b) {
        leftStickX = axis(b.get(1));
        leftStickY = -axis(b.get(2));
        rightStickX = axis(b.get(3));
        rightStickY = -axis(b.get(4));
        leftTrigger = (b.get(5) & 0xFF) / 255f;
        rightTrigger = (b.get(6) & 0xFF) / 255f;
        int b0 = b.get(8) & 0xFF, b1 = b.get(9) & 0xFF, b2 = b.get(10) & 0xFF;
        int hat = b0 & 0x0F;
        buttonFlags = 0;
        setButtonFlag(ControllerPacket.UP_FLAG, hat == 0 || hat == 1 || hat == 7 ? 1 : 0);
        setButtonFlag(ControllerPacket.RIGHT_FLAG, hat >= 1 && hat <= 3 ? 1 : 0);
        setButtonFlag(ControllerPacket.DOWN_FLAG, hat >= 3 && hat <= 5 ? 1 : 0);
        setButtonFlag(ControllerPacket.LEFT_FLAG, hat >= 5 && hat <= 7 ? 1 : 0);
        setButtonFlag(ControllerPacket.X_FLAG, b0 & 0x10);  // Square
        setButtonFlag(ControllerPacket.A_FLAG, b0 & 0x20);  // Cross
        setButtonFlag(ControllerPacket.B_FLAG, b0 & 0x40);  // Circle
        setButtonFlag(ControllerPacket.Y_FLAG, b0 & 0x80);  // Triangle
        setButtonFlag(ControllerPacket.LB_FLAG, b1 & 0x01);
        setButtonFlag(ControllerPacket.RB_FLAG, b1 & 0x02);
        setButtonFlag(ControllerPacket.BACK_FLAG, b1 & 0x10);  // Create
        setButtonFlag(ControllerPacket.PLAY_FLAG, b1 & 0x20);  // Options
        setButtonFlag(ControllerPacket.LS_CLK_FLAG, b1 & 0x40);
        setButtonFlag(ControllerPacket.RS_CLK_FLAG, b1 & 0x80);
        setButtonFlag(ControllerPacket.SPECIAL_BUTTON_FLAG, b2 & 0x01);  // PS
        setButtonFlag(ControllerPacket.TOUCHPAD_FLAG, b2 & 0x02);
        setButtonFlag(ControllerPacket.MISC_FLAG, b2 & 0x04);  // Mute
        gyroX = b.getShort(16) / GYRO_PER_DEG_S;
        gyroY = b.getShort(18) / GYRO_PER_DEG_S;
        gyroZ = b.getShort(20) / GYRO_PER_DEG_S;
        accelX = b.getShort(22) / ACCEL_PER_G;
        accelY = b.getShort(24) / ACCEL_PER_G;
        accelZ = b.getShort(26) / ACCEL_PER_G;
        return true;
    }

    private static float axis(byte raw) {
        return Math.max(-1f, ((raw & 0xFF) - 128) / 127f);
    }

    /** Sends the whole output state with the given valid flags (call with [outputLock] held). */
    private void writeOutput(int flag0, int flag1, int flag2) {
        if (outEndpt == null || stopped) return;
        byte[] r = new byte[OUTPUT_SIZE];
        r[0] = 0x02;
        r[OUT_FLAG0] = (byte) flag0;
        r[OUT_FLAG1] = (byte) flag1;
        r[OUT_MOTOR_RIGHT] = (byte) motorRight;
        r[OUT_MOTOR_LEFT] = (byte) motorLeft;
        System.arraycopy(rightEffect, 0, r, OUT_RIGHT_TRIGGER, 11);
        System.arraycopy(leftEffect, 0, r, OUT_LEFT_TRIGGER, 11);
        r[OUT_FLAG2] = (byte) flag2;
        r[OUT_LIGHTBAR_SETUP] = (byte) (flag2 != 0 ? 0x02 : 0);
        r[OUT_PLAYER_LEDS] = (byte) (1 << 2 | 0x20);  // the centre LED, lit at once
        r[OUT_RED] = (byte) red;
        r[OUT_RED + 1] = (byte) green;
        r[OUT_RED + 2] = (byte) blue;
        connection.bulkTransfer(outEndpt, r, r.length, 100);
    }

    @Override
    public void rumble(short lowFreqMotor, short highFreqMotor) {
        synchronized (outputLock) {
            motorLeft = (lowFreqMotor & 0xFFFF) >> 8;
            motorRight = (highFreqMotor & 0xFFFF) >> 8;
            writeOutput(FLAG0_RUMBLE | FLAG0_HAPTICS_SELECT, 0, 0);
        }
    }

    @Override
    public void rumbleTriggers(short leftTrigger, short rightTrigger) {
        // A DualSense has no trigger motors: its triggers take effects (setAdaptiveTriggers)
    }

    /** The light bar colour. */
    public void setLed(byte r, byte g, byte b) {
        synchronized (outputLock) {
            red = r & 0xFF; green = g & 0xFF; blue = b & 0xFF;
            writeOutput(0, FLAG1_LIGHTBAR, 0);
        }
    }

    /**
     * Trigger effects: [eventFlags] says which trigger changes (0x04 right, 0x08 left, as the output report's valid flags), each a
     * mode and 10 parameter bytes in the DualSense format (Sunshine 0x5503, PS5 data type 11).
     */
    public void setAdaptiveTriggers(byte eventFlags, byte typeLeft, byte typeRight, byte[] left, byte[] right) {
        synchronized (outputLock) {
            int flags = eventFlags & (FLAG0_RIGHT_TRIGGER | FLAG0_LEFT_TRIGGER);
            if ((flags & FLAG0_RIGHT_TRIGGER) != 0) {
                rightEffect[0] = typeRight;
                System.arraycopy(right, 0, rightEffect, 1, Math.min(10, right.length));
            }
            if ((flags & FLAG0_LEFT_TRIGGER) != 0) {
                leftEffect[0] = typeLeft;
                System.arraycopy(left, 0, leftEffect, 1, Math.min(10, left.length));
            }
            if (flags != 0) writeOutput(flags, 0, 0);
        }
    }

    @Override
    public void stop() {
        if (stopped) return;
        // Triggers and motors back to rest before letting go
        synchronized (outputLock) {
            java.util.Arrays.fill(rightEffect, (byte) 0);
            java.util.Arrays.fill(leftEffect, (byte) 0);
            motorLeft = motorRight = 0;
            writeOutput(FLAG0_RUMBLE | FLAG0_HAPTICS_SELECT | FLAG0_RIGHT_TRIGGER | FLAG0_LEFT_TRIGGER, 0, 0);
        }
        stopped = true;
        if (inputThread != null) {
            inputThread.interrupt();
            inputThread = null;
        }
        if (hid != null) connection.releaseInterface(hid);
        connection.close();
        notifyDeviceRemoved();
    }
}
