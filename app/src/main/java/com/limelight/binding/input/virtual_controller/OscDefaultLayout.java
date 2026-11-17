package com.limelight.binding.input.virtual_controller;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Where the default on-screen controls go on a screen: the numbers of the 128 x 72 grid of
 * {@link VirtualControllerConfigurationLoader}, placed by {@link OscGeometry.Panel}. Pure, so the layout can be checked for every
 * shape. The map is in the order the loader adds the elements (a later element is drawn and touched on top of an earlier one).
 */
public final class OscDefaultLayout {
    // Left and right clusters, triggers: the grid numbers of the classic layout
    static final int TRIGGER_L_BASE_X = 1;
    static final int TRIGGER_R_BASE_X = 92;
    static final int TRIGGER_DISTANCE = 23;
    static final int TRIGGER_BASE_Y = 31;
    static final int TRIGGER_WIDTH = 12;
    static final int TRIGGER_HEIGHT = 9;

    // Face buttons are defined based on the Y button (button number 9)
    static final int BUTTON_BASE_X = 106;
    static final int BUTTON_BASE_Y = 1;
    static final int BUTTON_SIZE = 10;

    static final int DPAD_BASE_X = 4;
    static final int DPAD_BASE_Y = 41;
    static final int DPAD_SIZE = 30;

    static final int ANALOG_L_BASE_X = 6;
    static final int ANALOG_L_BASE_Y = 4;
    static final int ANALOG_R_BASE_X = 98;
    static final int ANALOG_R_BASE_Y = 42;
    static final int ANALOG_SIZE = 26;

    static final int L3_R3_BASE_Y = 60;

    static final int START_BACK_Y = 64;
    static final int START_BACK_WIDTH = 12;
    static final int START_BACK_HEIGHT = 7;

    // Delos: the gap between the middle buttons, and the trackpad above them
    static final int MIDDLE_GAP = 2;
    static final int TRACKPAD_WIDTH = 20;
    static final int TRACKPAD_HEIGHT = 10;
    static final int GUIDE_Y = START_BACK_Y;

    private OscDefaultLayout() {
    }

    /** One element's rectangle in pixels. */
    public static final class Slot {
        public final int left;
        public final int top;
        public final int width;
        public final int height;

        Slot(int left, int top, int width, int height) {
            this.left = left;
            this.top = top;
            this.width = width;
            this.height = height;
        }

        public int right() {
            return left + width;
        }

        public int bottom() {
            return top + height;
        }
    }

    /**
     * The slots of the default layout, by element id ({@code VirtualControllerElement.EID_*}), for the buttons the settings turn on:
     * everything but Guide when {@code onlyL3R3} is off, only L3 and R3 when it is on, and Guide when {@code showGuide} is on.
     */
    public static Map<Integer, Slot> compute(OscGeometry.Panel p, boolean onlyL3R3, boolean showGuide) {
        Map<Integer, Slot> slots = new LinkedHashMap<>();

        // Back, Guide and Start in a row centred at the bottom (Guide in the middle, also when it is hidden, so Back and Start
        // stay symmetric), the trackpad centred above them
        int center = p.centerX();
        int middleWidth = p.size(START_BACK_WIDTH);
        int middleGap = p.size(MIDDLE_GAP);
        int guideX = center - middleWidth / 2;
        int backX = guideX - middleGap - middleWidth;
        int startX = guideX + middleWidth + middleGap;
        int trackpadWidth = p.size(TRACKPAD_WIDTH);
        int trackpadHeight = p.size(TRACKPAD_HEIGHT);
        int trackpadY = p.bottomLine(START_BACK_Y - MIDDLE_GAP) - trackpadHeight;

        if (!onlyL3R3) {
            slots.put(VirtualControllerElement.EID_DPAD, new Slot(
                    p.left(DPAD_BASE_X), p.bottom(DPAD_BASE_Y, DPAD_SIZE), p.size(DPAD_SIZE), p.size(DPAD_SIZE)));

            slots.put(VirtualControllerElement.EID_A, face(p, BUTTON_BASE_X, BUTTON_BASE_Y + 2 * BUTTON_SIZE));
            slots.put(VirtualControllerElement.EID_B, face(p, BUTTON_BASE_X + BUTTON_SIZE, BUTTON_BASE_Y + BUTTON_SIZE));
            slots.put(VirtualControllerElement.EID_X, face(p, BUTTON_BASE_X - BUTTON_SIZE, BUTTON_BASE_Y + BUTTON_SIZE));
            slots.put(VirtualControllerElement.EID_Y, face(p, BUTTON_BASE_X, BUTTON_BASE_Y));

            slots.put(VirtualControllerElement.EID_LT, trigger(p, false, TRIGGER_L_BASE_X, TRIGGER_BASE_Y));
            slots.put(VirtualControllerElement.EID_RT, trigger(p, true, TRIGGER_R_BASE_X + TRIGGER_DISTANCE, TRIGGER_BASE_Y));
            slots.put(VirtualControllerElement.EID_LB, trigger(p, false, TRIGGER_L_BASE_X + TRIGGER_DISTANCE, TRIGGER_BASE_Y));
            slots.put(VirtualControllerElement.EID_RB, trigger(p, true, TRIGGER_R_BASE_X, TRIGGER_BASE_Y));

            slots.put(VirtualControllerElement.EID_LS, new Slot(
                    p.left(ANALOG_L_BASE_X), p.top(ANALOG_L_BASE_Y), p.size(ANALOG_SIZE), p.size(ANALOG_SIZE)));
            slots.put(VirtualControllerElement.EID_RS, new Slot(
                    p.right(ANALOG_R_BASE_X, ANALOG_SIZE), p.bottom(ANALOG_R_BASE_Y, ANALOG_SIZE), p.size(ANALOG_SIZE), p.size(ANALOG_SIZE)));

            slots.put(VirtualControllerElement.EID_BACK, new Slot(
                    backX, p.bottom(START_BACK_Y, START_BACK_HEIGHT), p.size(START_BACK_WIDTH), p.size(START_BACK_HEIGHT)));
            slots.put(VirtualControllerElement.EID_START, new Slot(
                    startX, p.bottom(START_BACK_Y, START_BACK_HEIGHT), p.size(START_BACK_WIDTH), p.size(START_BACK_HEIGHT)));

            slots.put(VirtualControllerElement.EID_LSB, shoulderClick(p, false, TRIGGER_L_BASE_X));
            slots.put(VirtualControllerElement.EID_RSB, shoulderClick(p, true, TRIGGER_R_BASE_X + TRIGGER_DISTANCE));

            slots.put(VirtualControllerElement.EID_TOUCHPAD, new Slot(center - trackpadWidth / 2, trackpadY, trackpadWidth, trackpadHeight));
        } else {
            slots.put(VirtualControllerElement.EID_LSB, shoulderClick(p, false, TRIGGER_L_BASE_X));
            slots.put(VirtualControllerElement.EID_RSB, shoulderClick(p, true, TRIGGER_R_BASE_X + TRIGGER_DISTANCE));
        }

        if (showGuide) {
            slots.put(VirtualControllerElement.EID_GDB, new Slot(
                    guideX, p.bottom(GUIDE_Y, START_BACK_HEIGHT), p.size(START_BACK_WIDTH), p.size(START_BACK_HEIGHT)));
        }
        return slots;
    }

    /**
     * Whether a rectangle is where the default layout puts element {@code elementId} on a panel of {@code panelWidth} x
     * {@code panelHeight} pixels: a saved element that was never moved or resized there (the editor stores every element when it is
     * left, the untouched ones too).
     */
    public static boolean isDefault(int elementId, int left, int top, int width, int height, int panelWidth, int panelHeight) {
        if (panelWidth <= 0 || panelHeight <= 0) {
            return false;
        }
        // Every element has its slot here; where it goes does not depend on which others the settings turn on
        Slot slot = compute(new OscGeometry.Panel(panelWidth, panelHeight), false, true).get(elementId);
        return slot != null && slot.left == left && slot.top == top && slot.width == width && slot.height == height;
    }

    // The default layout on the 16:9 grid itself, where the anchors are read from
    private static final Map<Integer, Slot> GRID = compute(new OscGeometry.Panel(OscGeometry.GRID_WIDTH * 10, OscGeometry.GRID_HEIGHT * 10), false, true);

    /**
     * The side element {@code elementId} keeps to horizontally: where the default layout puts it on the 16:9 grid, by thirds of the
     * width (the left cluster, Back / Guide / Start and the trackpad in the middle, the right cluster).
     */
    public static OscPlacement.AnchorX anchorX(int elementId) {
        Slot s = GRID.get(elementId);
        if (s == null) {
            return OscPlacement.AnchorX.LEFT;
        }
        float center = s.left + s.width / 2f;
        float third = OscGeometry.GRID_WIDTH * 10 / 3f;
        return center < third ? OscPlacement.AnchorX.LEFT : center > 2 * third ? OscPlacement.AnchorX.RIGHT : OscPlacement.AnchorX.CENTER;
    }

    /** The edge element {@code elementId} keeps to vertically: the top for the upper half of the default layout, else the bottom. */
    public static OscPlacement.AnchorY anchorY(int elementId) {
        Slot s = GRID.get(elementId);
        if (s == null) {
            return OscPlacement.AnchorY.TOP;
        }
        return s.top + s.height / 2f < OscGeometry.GRID_HEIGHT * 10 / 2f ? OscPlacement.AnchorY.TOP : OscPlacement.AnchorY.BOTTOM;
    }

    // The middle of the layout before Delos (Artemis), grid units: Back and Start in fixed places (Start and Guide kept to the right
    // edge), the trackpad above them, left of the centre
    static final int ARTEMIS_BACK_X = 34;
    static final int ARTEMIS_START_X = 83;
    static final int ARTEMIS_GUIDE_X = ARTEMIS_START_X - ARTEMIS_BACK_X;
    static final int ARTEMIS_TRACKPAD_X = 50;
    static final int ARTEMIS_TRACKPAD_Y = 50;
    static final int ARTEMIS_TRACKPAD_HEIGHT = 12;

    /** The tallest panel searched for a layout saved without its size (8K). */
    static final int MAX_SEARCHED_HEIGHT = 4320;

    /**
     * The elements of a layout saved without its panel's size (versions before the size was stored) that are only an old default:
     * the defaults of Delos or of Artemis before it, on one panel ({@code saved}: element id to {left, top, width, height}, the
     * elements that are on). The editor used to store every element when it was left, the untouched ones too, and Artemis did the
     * same: such a copy freezes the default of the panel it was made on (the size the display metrics gave then), which keeps the
     * middle controls where that panel's centre was. Empty unless at least two elements are that default on the same panel, so a
     * real edit is not taken for one by chance.
     */
    public static Set<Integer> oldDefaults(Map<Integer, int[]> saved) {
        Set<Integer> best = Collections.emptySet();
        if (saved.size() < 2) {
            return best;
        }
        for (int height = GRID_HEIGHT_MIN; height <= MAX_SEARCHED_HEIGHT; height++) {
            for (boolean artemis : new boolean[] { false, true }) {
                // Sizes and heights do not depend on the width (from 3:2 up): a panel height that gives none of them is skipped cheaply
                int start = height * 2;
                Map<Integer, Slot> atStart = layout(artemis, start, height);
                if (sameSizes(saved, atStart) < 2) {
                    continue;
                }
                Map<Integer, Slot> atStartPlus2 = layout(artemis, start + 2, height);
                for (int width : widths(saved, atStart, atStartPlus2, start)) {
                    Set<Integer> matched = matching(saved, layout(artemis, width, height));
                    if (matched.size() > best.size()) {
                        best = matched;
                    }
                }
            }
        }
        return best.size() >= 2 ? best : Collections.<Integer>emptySet();
    }

    // The lowest panel height searched: one pixel per grid unit
    private static final int GRID_HEIGHT_MIN = OscGeometry.GRID_HEIGHT;

    /** The default of Delos, or of Artemis (its own middle), on a panel of {@code width} x {@code height}. */
    static Map<Integer, Slot> layout(boolean artemis, int width, int height) {
        OscGeometry.Panel p = new OscGeometry.Panel(width, height);
        Map<Integer, Slot> slots = compute(p, false, true);
        if (artemis) {
            int y = p.top(START_BACK_Y);
            slots.put(VirtualControllerElement.EID_BACK, new Slot(p.left(ARTEMIS_BACK_X), y, p.size(START_BACK_WIDTH), p.size(START_BACK_HEIGHT)));
            slots.put(VirtualControllerElement.EID_START, new Slot(p.right(ARTEMIS_START_X, 0), y, p.size(START_BACK_WIDTH), p.size(START_BACK_HEIGHT)));
            slots.put(VirtualControllerElement.EID_GDB, new Slot(p.right(ARTEMIS_GUIDE_X, 0), y, p.size(START_BACK_WIDTH), p.size(START_BACK_HEIGHT)));
            slots.put(VirtualControllerElement.EID_TOUCHPAD, new Slot(p.left(ARTEMIS_TRACKPAD_X), p.top(ARTEMIS_TRACKPAD_Y),
                    p.size(TRACKPAD_WIDTH), p.size(ARTEMIS_TRACKPAD_HEIGHT)));
        }
        return slots;
    }

    /** How many saved elements have their slot's width, height and top. */
    private static int sameSizes(Map<Integer, int[]> saved, Map<Integer, Slot> slots) {
        int count = 0;
        for (Map.Entry<Integer, int[]> e : saved.entrySet()) {
            Slot s = slots.get(e.getKey());
            int[] r = e.getValue();
            if (s != null && s.top == r[1] && s.width == r[2] && s.height == r[3]) {
                count++;
            }
        }
        return count;
    }

    /**
     * The panel widths that put a saved element where it is: an element kept to the right edge moves with the width, a centred one
     * by half of it, one kept to the left edge not at all (then any width, {@code start} stands for them).
     */
    private static List<Integer> widths(Map<Integer, int[]> saved, Map<Integer, Slot> atStart, Map<Integer, Slot> atStartPlus2, int start) {
        Set<Integer> widths = new LinkedHashSet<>();
        widths.add(start);
        for (Map.Entry<Integer, int[]> e : saved.entrySet()) {
            Slot a = atStart.get(e.getKey());
            Slot b = atStartPlus2.get(e.getKey());
            if (a == null || b == null) {
                continue;
            }
            int dx = e.getValue()[0] - a.left;
            int moves = b.left - a.left;
            if (moves == 2) {
                widths.add(start + dx);
            } else if (moves == 1) {
                widths.add(start + 2 * dx);
                widths.add(start + 2 * dx + 1);
            }
        }
        List<Integer> plausible = new ArrayList<>();
        for (int w : widths) {
            if (w > 0) {
                plausible.add(w);
            }
        }
        return plausible;
    }

    /** The saved elements exactly in their slot. */
    private static Set<Integer> matching(Map<Integer, int[]> saved, Map<Integer, Slot> slots) {
        Set<Integer> matched = new LinkedHashSet<>();
        for (Map.Entry<Integer, int[]> e : saved.entrySet()) {
            Slot s = slots.get(e.getKey());
            int[] r = e.getValue();
            if (s != null && s.left == r[0] && s.top == r[1] && s.width == r[2] && s.height == r[3]) {
                matched.add(e.getKey());
            }
        }
        return matched;
    }

    /** A face button, the right cluster against the right edge, the top row against the top. */
    private static Slot face(OscGeometry.Panel p, int x, int y) {
        return new Slot(p.right(x, BUTTON_SIZE), p.top(y), p.size(BUTTON_SIZE), p.size(BUTTON_SIZE));
    }

    /** A trigger or bumper: the upper half, against the top edge. */
    private static Slot trigger(OscGeometry.Panel p, boolean rightSide, int x, int y) {
        int left = rightSide ? p.right(x, TRIGGER_WIDTH) : p.left(x);
        return new Slot(left, p.top(y), p.size(TRIGGER_WIDTH), p.size(TRIGGER_HEIGHT));
    }

    /** L3 or R3: the lower half, against the bottom edge. */
    private static Slot shoulderClick(OscGeometry.Panel p, boolean rightSide, int x) {
        int left = rightSide ? p.right(x, TRIGGER_WIDTH) : p.left(x);
        return new Slot(left, p.bottom(L3_R3_BASE_Y, TRIGGER_HEIGHT), p.size(TRIGGER_WIDTH), p.size(TRIGGER_HEIGHT));
    }
}
