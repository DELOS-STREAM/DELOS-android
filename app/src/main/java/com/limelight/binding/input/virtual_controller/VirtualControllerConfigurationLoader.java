/**
 * Created by Karim Mreisi.
 */

package com.limelight.binding.input.virtual_controller;

import android.app.Activity;
import android.content.Context;
import android.content.SharedPreferences;
import android.util.DisplayMetrics;

import com.limelight.R;
import com.limelight.nvstream.input.ControllerPacket;
import com.limelight.preferences.PreferenceConfiguration;
import com.limelight.shell.ui.Symbols;

import java.util.Map;

public class VirtualControllerConfigurationLoader {
    public static final String OSC_PREFERENCE = "OSC";

    private static DigitalPad createDigitalPad(
            final VirtualController controller,
            final Context context) {

        DigitalPad digitalPad = new DigitalPad(controller, context);
        digitalPad.addDigitalPadListener(new DigitalPad.DigitalPadListener() {
            @Override
            public void onDirectionChange(int direction) {
                VirtualController.ControllerInputContext inputContext =
                        controller.getControllerInputContext();

                if ((direction & DigitalPad.DIGITAL_PAD_DIRECTION_LEFT) != 0) {
                    inputContext.inputMap |= ControllerPacket.LEFT_FLAG;
                }
                else {
                    inputContext.inputMap &= ~ControllerPacket.LEFT_FLAG;
                }
                if ((direction & DigitalPad.DIGITAL_PAD_DIRECTION_RIGHT) != 0) {
                    inputContext.inputMap |= ControllerPacket.RIGHT_FLAG;
                }
                else {
                    inputContext.inputMap &= ~ControllerPacket.RIGHT_FLAG;
                }
                if ((direction & DigitalPad.DIGITAL_PAD_DIRECTION_UP) != 0) {
                    inputContext.inputMap |= ControllerPacket.UP_FLAG;
                }
                else {
                    inputContext.inputMap &= ~ControllerPacket.UP_FLAG;
                }
                if ((direction & DigitalPad.DIGITAL_PAD_DIRECTION_DOWN) != 0) {
                    inputContext.inputMap |= ControllerPacket.DOWN_FLAG;
                }
                else {
                    inputContext.inputMap &= ~ControllerPacket.DOWN_FLAG;
                }

                controller.sendControllerInputContext(10, 0x22);
            }
        });

        return digitalPad;
    }

    private static DigitalButton createDigitalButton(
            final int elementId,
            final int keyShort,
            final int keyLong,
            final int layer,
            final String text,
            final VirtualController controller,
            final Context context) {
        DigitalButton button = new DigitalButton(controller, elementId, layer, context);
        button.setText(text);
        button.addDigitalButtonListener(new DigitalButton.DigitalButtonListener() {
            @Override
            public void onClick() {
                VirtualController.ControllerInputContext inputContext =
                        controller.getControllerInputContext();
                inputContext.inputMap |= keyShort;

                controller.sendControllerInputContext();
            }

            @Override
            public void onLongClick() {
                VirtualController.ControllerInputContext inputContext =
                        controller.getControllerInputContext();
                inputContext.inputMap |= keyLong;

                controller.sendControllerInputContext();
            }

            @Override
            public void onRelease() {
                VirtualController.ControllerInputContext inputContext =
                        controller.getControllerInputContext();
                inputContext.inputMap &= ~keyShort;
                inputContext.inputMap &= ~keyLong;

                controller.sendControllerInputContext();
            }
        });

        return button;
    }

    private static DigitalButton createLeftTrigger(
            final int layer,
            final String text,
            final VirtualController controller,
            final Context context) {
        LeftTrigger button = new LeftTrigger(controller, layer, context);
        button.setText(text);
        return button;
    }

    private static DigitalButton createRightTrigger(
            final int layer,
            final String text,
            final VirtualController controller,
            final Context context) {
        RightTrigger button = new RightTrigger(controller, layer, context);
        button.setText(text);
        return button;
    }

    private static AnalogStick createLeftStick(
            final VirtualController controller,
            final Context context) {
        return new LeftAnalogStick(controller, context);
    }

    private static AnalogStick createRightStick(
            final VirtualController controller,
            final Context context) {
        return new RightAnalogStick(controller, context);
    }

    private static AnalogStickFree createLeftStick2(
            final VirtualController controller,
            final Context context) {
        return new LeftAnalogStickFree(controller, context);
    }

    private static AnalogStickFree createRightStick2(
            final VirtualController controller,
            final Context context) {
        return new RightAnalogStickFree(controller, context);
    }


    /**
     * The default layout on a screen of {@code width} x {@code height} pixels. The positions come from {@link OscDefaultLayout}
     * (docs/GUI.md, "Screen shapes"): the 16:9 layout of the prototype from 3:2 up, shrunk to fit on squarer screens.
     */
    public static void createDefaultLayout(final VirtualController controller, final Context context, int width, int height) {
        createDefaultLayout(controller, context, width, height, PreferenceConfiguration.readPreferences(context));
    }

    /** The same with the settings given (the debug previews cannot read them: that loads the native library, which the emulator cannot). */
    public static void createDefaultLayout(final VirtualController controller, final Context context, int width, int height,
                                           PreferenceConfiguration config) {
        OscGeometry.Panel panel = new OscGeometry.Panel(width, height);
        // A PlayStation stream (owner, 2026-11-24): the DualSense's own glyphs and names, ✕ always the south button, Create as the
        // pad's share button (not Select's tap-or-hold, PsConnection), the PS button always there and a touchpad fingers move on
        boolean ps = controller.isPlayStation();
        boolean flip = config.flipFaceButtons && !ps;
        boolean guide = config.showGuideButton || ps;
        Map<Integer, OscDefaultLayout.Slot> slots = OscDefaultLayout.compute(panel, config.onlyL3R3, guide);

        if (!config.onlyL3R3)
        {
            add(controller, slots, VirtualControllerElement.EID_DPAD, createDigitalPad(controller, context));

            add(controller, slots, VirtualControllerElement.EID_A, createDigitalButton(
                    VirtualControllerElement.EID_A,
                    !flip ? ControllerPacket.A_FLAG : ControllerPacket.B_FLAG, 0, 1,
                    ps ? Symbols.FACE_CROSS : !flip ? "A" : "B",  controller, context));

            add(controller, slots, VirtualControllerElement.EID_B, createDigitalButton(
                    VirtualControllerElement.EID_B,
                    flip ? ControllerPacket.A_FLAG : ControllerPacket.B_FLAG, 0, 1,
                    ps ? Symbols.FACE_CIRCLE : flip ? "A" : "B",  controller, context));

            add(controller, slots, VirtualControllerElement.EID_X, createDigitalButton(
                    VirtualControllerElement.EID_X,
                    !flip ? ControllerPacket.X_FLAG : ControllerPacket.Y_FLAG, 0, 1,
                    ps ? Symbols.FACE_SQUARE : !flip ? "X" : "Y",  controller, context));

            add(controller, slots, VirtualControllerElement.EID_Y, createDigitalButton(
                    VirtualControllerElement.EID_Y,
                    flip ? ControllerPacket.X_FLAG : ControllerPacket.Y_FLAG, 0, 1,
                    ps ? Symbols.FACE_TRIANGLE : flip ? "X" : "Y",  controller, context));

            add(controller, slots, VirtualControllerElement.EID_LT, createLeftTrigger(1, ps ? "L2" : "LT",  controller, context));
            add(controller, slots, VirtualControllerElement.EID_RT, createRightTrigger(1, ps ? "R2" : "RT",  controller, context));

            add(controller, slots, VirtualControllerElement.EID_LB, createDigitalButton(
                    VirtualControllerElement.EID_LB,
                    ControllerPacket.LB_FLAG, 0, 1, ps ? "L1" : "LB",  controller, context));

            add(controller, slots, VirtualControllerElement.EID_RB, createDigitalButton(
                    VirtualControllerElement.EID_RB,
                    ControllerPacket.RB_FLAG, 0, 1, ps ? "R1" : "RB",  controller, context));

            if(config.enableNewAnalogStick){
                add(controller, slots, VirtualControllerElement.EID_LS, createLeftStick2(controller, context));
                add(controller, slots, VirtualControllerElement.EID_RS, createRightStick2(controller, context));
            }else{
                add(controller, slots, VirtualControllerElement.EID_LS, createLeftStick(controller, context));
                add(controller, slots, VirtualControllerElement.EID_RS, createRightStick(controller, context));
            }

            add(controller, slots, VirtualControllerElement.EID_BACK, createDigitalButton(
                    VirtualControllerElement.EID_BACK,
                    ps ? ControllerPacket.MISC_FLAG : ControllerPacket.BACK_FLAG, 0, 2, ps ? Symbols.PAD_DUALSENSE_CREATE : "BACK",  controller, context));

            add(controller, slots, VirtualControllerElement.EID_START, createDigitalButton(
                    VirtualControllerElement.EID_START,
                    ControllerPacket.PLAY_FLAG, 0, 3, ps ? Symbols.PAD_DUALSENSE_OPTIONS : "START",  controller, context));

            add(controller, slots, VirtualControllerElement.EID_LSB, createDigitalButton(
                    VirtualControllerElement.EID_LSB,
                    ControllerPacket.LS_CLK_FLAG, 0, 1, "L3",  controller, context));

            add(controller, slots, VirtualControllerElement.EID_RSB, createDigitalButton(
                    VirtualControllerElement.EID_RSB,
                    ControllerPacket.RS_CLK_FLAG, 0, 1, "R3",  controller, context));

            add(controller, slots, VirtualControllerElement.EID_TOUCHPAD, ps ? new OscTouchpad(controller, context) : createDigitalButton(
                    VirtualControllerElement.EID_TOUCHPAD,
                    ControllerPacket.TOUCHPAD_FLAG, 0, 1, "TRACKPAD",  controller, context));
        }
        else {
            add(controller, slots, VirtualControllerElement.EID_LSB, createDigitalButton(
                    VirtualControllerElement.EID_LSB,
                    ControllerPacket.LS_CLK_FLAG, 0, 1, "L3", controller, context));

            add(controller, slots, VirtualControllerElement.EID_RSB, createDigitalButton(
                    VirtualControllerElement.EID_RSB,
                    ControllerPacket.RS_CLK_FLAG, 0, 1, "R3",  controller, context));
        }

        if (guide) {
            add(controller, slots, VirtualControllerElement.EID_GDB, createDigitalButton(VirtualControllerElement.EID_GDB,
                    ControllerPacket.SPECIAL_BUTTON_FLAG, 0, 1, ps ? Symbols.PAD_DUALSENSE_PS : "GUIDE",  controller, context));
        }

        controller.setOpacity(config.oscOpacity);
    }

    private static void add(VirtualController controller, Map<Integer, OscDefaultLayout.Slot> slots, int elementId,
                            VirtualControllerElement element) {
        OscDefaultLayout.Slot slot = slots.get(elementId);
        controller.addElement(element, slot.left, slot.top, slot.width, slot.height);
    }

    /**
     * Leaving the layout editor: the controls the user changed are stored against their anchor in layout units
     * ({@link OscLayoutStore}, {@link OscPlacement}); one that is on and where the default layout puts it is not stored, it follows the
     * default. Controls that are not on screen now (settings that hide them) keep what was stored for them.
     */
    public static void saveProfile(final VirtualController controller,
                                   final Context context) {
        // Delos: editable layouts belong to the active user profile.
        SharedPreferences pref = com.limelight.shell.settings.ProfileLayouts.preferences(context, OSC_PREFERENCE);
        OscGeometry.Panel panel = new OscGeometry.Panel(controller.getPanelWidth(), controller.getPanelHeight());
        Map<Integer, OscPlacement> placements = OscLayoutStore.read(pref, OscLayoutStore.GLOBAL);
        for (VirtualControllerElement element : controller.getElements()) {
            if (element.isDefaultPlacement()) {
                placements.remove(element.elementId);
            } else {
                placements.put(element.elementId, OscPlacement.fromRect(element.elementId, element.rect(), element.enabled, panel));
            }
        }
        SharedPreferences.Editor editor = pref.edit();
        OscLayoutStore.write(editor, OscLayoutStore.GLOBAL, placements);
        editor.apply();
    }

    /** The stored changes over the default layout, on the panel the controls are laid out on (an old layout is converted first). */
    public static void loadFromPreferences(final VirtualController controller, final Context context) {
        // Delos: editable layouts belong to the active user profile.
        SharedPreferences pref = com.limelight.shell.settings.ProfileLayouts.preferences(context, OSC_PREFERENCE);
        DisplayMetrics screen = context.getResources().getDisplayMetrics();
        OscLayoutStore.migrate(pref, screen.widthPixels, screen.heightPixels);

        OscGeometry.Panel panel = new OscGeometry.Panel(controller.getPanelWidth(), controller.getPanelHeight());
        Map<Integer, OscPlacement> placements = OscLayoutStore.read(pref, OscLayoutStore.GLOBAL);
        for (VirtualControllerElement element : controller.getElements()) {
            OscPlacement placement = placements.get(element.elementId);
            if (placement != null) {
                element.place(placement.toRect(panel), placement.enabled);
            }
        }
    }
}
