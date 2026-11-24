package com.limelight.binding.input.virtual_controller;

import android.content.Context;
import android.graphics.Canvas;
import android.os.SystemClock;
import android.view.MotionEvent;

import com.limelight.nvstream.input.ControllerPacket;
import com.limelight.nvstream.jni.MoonBridge;
import com.limelight.shell.ui.Symbols;

/**
 * A PlayStation stream's on-screen touchpad: every finger on it is a finger on the DualSense's touchpad (its place over the
 * element), and a short tap that hardly moved also clicks the pad, as pressing it down would.
 */
public class OscTouchpad extends VirtualControllerElement {
    /** A tap: let go within this long, and moved less than {@link #TAP_SLOP} of the pad's width. */
    private static final long TAP_MS = 250;
    private static final float TAP_SLOP = .04f;
    /** How long a tap's click is held. */
    private static final long CLICK_MS = 80;

    private long downAt;
    private float downX;
    private float downY;
    private boolean moved;

    private final Runnable release = () -> {
        VirtualController.ControllerInputContext input = virtualController.getControllerInputContext();
        input.inputMap &= ~ControllerPacket.TOUCHPAD_FLAG;
        virtualController.sendControllerInputContext();
    };

    public OscTouchpad(VirtualController controller, Context context) {
        super(controller, context, EID_TOUCHPAD);
    }

    @Override
    protected void onElementDraw(Canvas canvas) {
        OscStyle.drawButton(canvas, getWidth(), getHeight(), Symbols.PAD_DUALSENSE_TOUCHPAD, isPressed(), elementOpacity(),
                getResources().getDisplayMetrics().density, true);
        drawConfigFrame(canvas);
    }

    private float x(MotionEvent event) {
        return Math.max(0f, Math.min(1f, event.getX() / Math.max(1, getWidth())));
    }

    private float y(MotionEvent event) {
        return Math.max(0f, Math.min(1f, event.getY() / Math.max(1, getHeight())));
    }

    /** One finger (the controls take no second pointer, {@link VirtualControllerElement#onTouchEvent}). */
    @Override
    public boolean onElementTouchEvent(MotionEvent event) {
        int id = event.getPointerId(0);
        switch (event.getActionMasked()) {
            case MotionEvent.ACTION_DOWN:
                downAt = SystemClock.uptimeMillis();
                downX = x(event);
                downY = y(event);
                moved = false;
                setPressed(true);
                invalidate();
                virtualController.sendTouchpad(MoonBridge.LI_TOUCH_EVENT_DOWN, id, downX, downY);
                break;
            case MotionEvent.ACTION_MOVE:
                if (Math.hypot(x(event) - downX, (y(event) - downY) * getHeight() / Math.max(1f, getWidth())) > TAP_SLOP) {
                    moved = true;
                }
                virtualController.sendTouchpad(MoonBridge.LI_TOUCH_EVENT_MOVE, id, x(event), y(event));
                break;
            case MotionEvent.ACTION_UP:
                virtualController.sendTouchpad(MoonBridge.LI_TOUCH_EVENT_UP, id, x(event), y(event));
                setPressed(false);
                invalidate();
                if (!moved && SystemClock.uptimeMillis() - downAt <= TAP_MS) {
                    click();
                }
                break;
            case MotionEvent.ACTION_CANCEL:
                virtualController.sendTouchpad(MoonBridge.LI_TOUCH_EVENT_CANCEL_ALL, 0, 0f, 0f);
                setPressed(false);
                invalidate();
                break;
            default:
                break;
        }
        return true;
    }

    private void click() {
        VirtualController.ControllerInputContext input = virtualController.getControllerInputContext();
        input.inputMap |= ControllerPacket.TOUCHPAD_FLAG;
        virtualController.sendControllerInputContext();
        virtualController.getHandler().removeCallbacks(release);
        virtualController.getHandler().postDelayed(release, CLICK_MS);
    }
}
