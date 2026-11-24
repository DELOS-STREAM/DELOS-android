/**
 * Created by Karim Mreisi.
 */

package com.limelight.binding.input.virtual_controller;

import android.content.Context;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import android.os.VibrationEffect;
import android.os.Vibrator;
import android.util.DisplayMetrics;
import android.view.HapticFeedbackConstants;
import android.view.View;
import android.widget.Button;
import android.widget.FrameLayout;
import com.limelight.utils.DelosToast;

import com.limelight.LimeLog;
import com.limelight.R;
import com.limelight.binding.input.ControllerHandler;
import com.limelight.preferences.PreferenceConfiguration;

import java.util.ArrayList;
import java.util.List;

public class VirtualController {
    public static class ControllerInputContext {
//        public short inputMap = 0x0000;
        public int inputMap = 0;
        public byte leftTrigger = 0x00;
        public byte rightTrigger = 0x00;
        public short rightStickX = 0x0000;
        public short rightStickY = 0x0000;
        public short leftStickX = 0x0000;
        public short leftStickY = 0x0000;
    }

    public enum ControllerMode {
        Active,
        MoveButtons,
        ResizeButtons,
        DisableEnableButtons
    }

    private static final boolean _PRINT_DEBUG_INFORMATION = false;

    private final ControllerHandler controllerHandler;
    private final Context context;
    private final Handler handler;

    // The size the controls are laid out on (the frame they are drawn in); a saved layout is stored against it
    private int panelWidth;
    private int panelHeight;

    // Whether the layout follows the frame's size (refreshLayout), not a size given (the debug previews' layOutDefault)
    private boolean followFrame = false;

    private final boolean playStation;

    private final Runnable relayoutRunnable = new Runnable() {
        @Override
        public void run() {
            if (followFrame) {
                refreshLayout();
            }
        }
    };

    private final Runnable delayedRetransmitRunnable = new Runnable() {
        @Override
        public void run() {
            sendControllerInputContextInternal();
        }
    };

    private FrameLayout frame_layout = null;

    ControllerMode currentMode = ControllerMode.Active;
    ControllerInputContext inputContext = new ControllerInputContext();

    private Button buttonConfigure = null;

    private List<VirtualControllerElement> elements = new ArrayList<>();

    private Vibrator vibrator;

    private final VibrationEffect defaultVibrationEffect;

    public VirtualController(final ControllerHandler controllerHandler, FrameLayout layout, final Context context) {
        this(controllerHandler, layout, context, false);
    }

    /** [playStation]: a PlayStation stream's controls (the DualSense's names and glyphs, the PS button and a real touchpad). */
    public VirtualController(final ControllerHandler controllerHandler, FrameLayout layout, final Context context, boolean playStation) {
        this.controllerHandler = controllerHandler;
        this.frame_layout = layout;
        this.context = context;
        this.playStation = playStation;
        this.handler = new Handler(Looper.getMainLooper());
        DisplayMetrics screen = context.getResources().getDisplayMetrics();
        this.panelWidth = screen.widthPixels;
        this.panelHeight = screen.heightPixels;
        // The frame got another size (laid out for the first time, rotation, PiP, the window's insets): lay the controls out on it again,
        // after this layout pass
        frame_layout.addOnLayoutChangeListener((v, left, top, right, bottom, oldLeft, oldTop, oldRight, oldBottom) -> {
            if (followFrame && frameWidth() > 0 && frameHeight() > 0 && (frameWidth() != panelWidth || frameHeight() != panelHeight)) {
                handler.removeCallbacks(relayoutRunnable);
                handler.post(relayoutRunnable);
            }
        });
        // Delos: the profile's main color for pressed controls
        OscStyle.refresh(context);

        this.vibrator = (Vibrator) context.getSystemService(Context.VIBRATOR_SERVICE);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            defaultVibrationEffect = VibrationEffect.createOneShot(10, VibrationEffect.DEFAULT_AMPLITUDE);
        } else {
            defaultVibrationEffect = null;
        }

        buttonConfigure = new Button(context);
        buttonConfigure.setAlpha(0.25f);
        buttonConfigure.setFocusable(false);
        buttonConfigure.setBackgroundResource(R.drawable.ic_settings);
        buttonConfigure.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                String message;

                if (currentMode == ControllerMode.Active) {
                    currentMode = ControllerMode.DisableEnableButtons;
                    showElements();
                    message = context.getString(R.string.configuration_mode_disable_enable_buttons);
                } else if (currentMode == ControllerMode.DisableEnableButtons){
                    currentMode = ControllerMode.MoveButtons;
                    showEnabledElements();
                    message = context.getString(R.string.configuration_mode_move_buttons);
                } else if (currentMode == ControllerMode.MoveButtons) {
                    currentMode = ControllerMode.ResizeButtons;
                    message = context.getString(R.string.configuration_mode_resize_buttons);
                } else {
                    currentMode = ControllerMode.Active;
                    VirtualControllerConfigurationLoader.saveProfile(VirtualController.this, context);
                    message = context.getString(R.string.configuration_mode_exiting);
                }

                DelosToast.show(context, message, DelosToast.SHORT);

                buttonConfigure.invalidate();

                for (VirtualControllerElement element : elements) {
                    element.invalidate();
                }
            }
        });

    }

    /** Whether the user wants the overlay visible (unaffected by temporary hides). */
    public boolean shown = false;

    Handler getHandler() {
        return handler;
    }

    boolean isPlayStation() {
        return playStation;
    }

    /** A finger on the on-screen touchpad ({@link OscTouchpad}), sent as the controller's touchpad contact. */
    void sendTouchpad(byte eventType, int pointerId, float x, float y) {
        if (controllerHandler != null) {
            controllerHandler.reportOscTouch(eventType, pointerId, x, y);
        }
    }

    /**
     * Hides the overlay. A temporary hide (e.g. Picture-in-Picture) keeps {@link #shown}
     * so the state the user chose can be restored afterwards.
     */
    public void hide(boolean temporary) {
        for (VirtualControllerElement element : elements) {
            element.setVisibility(View.GONE);
        }

        buttonConfigure.setVisibility(View.GONE);
        if (!temporary) {
            shown = false;
        }
    }

    public void hide() {
        hide(false);
    }

    public void show() {
        showEnabledElements();

        buttonConfigure.setVisibility(View.VISIBLE);
        shown = true;
    }

    public int switchShowHide() {
        if (shown) {
            hide();
            return 0;
        } else {
            show();
            return 1;
        }
    }

    public void showElements(){
        for(VirtualControllerElement element : elements){
            element.setVisibility(View.VISIBLE);
        }
    }

    public void showEnabledElements(){
        for(VirtualControllerElement element: elements){
            element.setVisibility( element.enabled ? View.VISIBLE : View.GONE );
        }
    }

    public void removeElements() {
        for (VirtualControllerElement element : elements) {
            frame_layout.removeView(element);
        }
        elements.clear();

        frame_layout.removeView(buttonConfigure);
    }

    public void setOpacity(int opacity) {
        for (VirtualControllerElement element : elements) {
            element.setOpacity(opacity);
        }
    }


    public void addElement(VirtualControllerElement element, int x, int y, int width, int height) {
        elements.add(element);
        FrameLayout.LayoutParams layoutParams = new FrameLayout.LayoutParams(width, height);
        layoutParams.setMargins(x, y, 0, 0);

        frame_layout.addView(element, layoutParams);
    }

    public List<VirtualControllerElement> getElements() {
        return elements;
    }

    private static final void _DBG(String text) {
        if (_PRINT_DEBUG_INFORMATION) {
            LimeLog.info("VirtualController: " + text);
        }
    }

    /** The size in pixels of the screen the controls were laid out on (and that a saved layout is stored against). */
    int getPanelWidth() {
        return panelWidth;
    }

    int getPanelHeight() {
        return panelHeight;
    }

    /**
     * Lays the controls out on the frame they are drawn in, and again whenever its size changes. Their positions are margins in that
     * frame, so the default layout centres the middle controls on its width. The display metrics are not that size: on Android 13 they
     * leave out a navigation bar at the side (a screen under 600 dp tall, such as the AYN Odin 2 Portal at 369 dpi, keeps it at the right
     * in landscape) and a display cutout, while the full-screen stream and the edge-to-edge shell draw under both. They only stand in
     * until the frame has been laid out.
     */
    public void refreshLayout() {
        followFrame = true;
        handler.removeCallbacks(relayoutRunnable);
        if (frameWidth() > 0 && frameHeight() > 0) {
            layOut(frameWidth(), frameHeight(), true, null);
        } else {
            DisplayMetrics screen = context.getResources().getDisplayMetrics();
            layOut(screen.widthPixels, screen.heightPixels, true, null);
        }
    }

    /** The frame's inner width and height, where the margins of the controls start; 0 before it was laid out. */
    private int frameWidth() {
        return Math.max(0, frame_layout.getWidth() - frame_layout.getPaddingLeft() - frame_layout.getPaddingRight());
    }

    private int frameHeight() {
        return Math.max(0, frame_layout.getHeight() - frame_layout.getPaddingTop() - frame_layout.getPaddingBottom());
    }

    /**
     * Debug previews: the default layout only, on a screen of {@code width} x {@code height} pixels, without the user's saved one, for
     * the given settings.
     */
    public void layOutDefault(int width, int height, PreferenceConfiguration config) {
        followFrame = false;
        handler.removeCallbacks(relayoutRunnable);
        layOut(width, height, false, config);
    }

    private void layOut(int width, int height, boolean loadSaved, PreferenceConfiguration config) {
        removeElements();

        panelWidth = width;
        panelHeight = height;

        int buttonSize = (int)(height*0.06f);
        FrameLayout.LayoutParams params = new FrameLayout.LayoutParams(buttonSize, buttonSize);
        params.leftMargin = 15;
        params.topMargin = 15;
        frame_layout.addView(buttonConfigure, params);

        // Start with the default layout
        if (config == null) {
            VirtualControllerConfigurationLoader.createDefaultLayout(this, context, width, height);
        } else {
            VirtualControllerConfigurationLoader.createDefaultLayout(this, context, width, height, config);
        }

        // Apply user preferences onto the default layout
        if (loadSaved) {
            VirtualControllerConfigurationLoader.loadFromPreferences(this, context);
        }

        // Rebuilding adds the elements visible; keep the state the user chose
        if (shown) {
            show();
        } else {
            hide(true);
        }
    }

    public ControllerMode getControllerMode() {
        return currentMode;
    }

    public ControllerInputContext getControllerInputContext() {
        return inputContext;
    }

    private void sendControllerInputContextInternal() {
        _DBG("INPUT_MAP + " + inputContext.inputMap);
        _DBG("LEFT_TRIGGER " + inputContext.leftTrigger);
        _DBG("RIGHT_TRIGGER " + inputContext.rightTrigger);
        _DBG("LEFT STICK X: " + inputContext.leftStickX + " Y: " + inputContext.leftStickY);
        _DBG("RIGHT STICK X: " + inputContext.rightStickX + " Y: " + inputContext.rightStickY);

        if (controllerHandler != null) {
            controllerHandler.reportOscState(
                    inputContext.inputMap,
                    inputContext.leftStickX,
                    inputContext.leftStickY,
                    inputContext.rightStickX,
                    inputContext.rightStickY,
                    inputContext.leftTrigger,
                    inputContext.rightTrigger
            );
        }
    }

    public void sendControllerInputContext(long vibrationDuration, int vibrationAmplitude) {
        // Cancel retransmissions of prior gamepad inputs
        handler.removeCallbacks(delayedRetransmitRunnable);

        sendControllerInputContextInternal();
        if (frame_layout != null && PreferenceConfiguration.readPreferences(context).enableKeyboardVibrate) {
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
                VibrationEffect effect;
                if (vibrationDuration == 0) {
                    effect = defaultVibrationEffect;
                } else {
                    effect = VibrationEffect.createOneShot(vibrationDuration, vibrationAmplitude);
                }
                vibrator.vibrate(effect);
            } else {
                if (vibrationDuration == 0) {
                    vibrationDuration = 10;
                }
                vibrator.vibrate(vibrationDuration);
            }
        }
        // HACK: GFE sometimes discards gamepad packets when they are received
        // very shortly after another. This can be critical if an axis zeroing packet
        // is lost and causes an analog stick to get stuck. To avoid this, we retransmit
        // the gamepad state a few times unless another input event happens before then.
        handler.postDelayed(delayedRetransmitRunnable, 25);
        handler.postDelayed(delayedRetransmitRunnable, 50);
        handler.postDelayed(delayedRetransmitRunnable, 75);
    }

    public void sendControllerInputContext() {
        sendControllerInputContext(0, 0);
    }
}
