package com.limelight.binding.input.virtual_controller;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import java.util.Arrays;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;

/** Controls the user changed, stored against their anchor in layout units, on any resolution and shape (#101). */
public class OscPlacementTest {
    private static final int[][] PANELS = { { 1920, 1080 }, { 2400, 1080 }, { 1280, 800 }, { 2560, 1600 } };

    private static final int BACK = VirtualControllerElement.EID_BACK;
    private static final int GUIDE = VirtualControllerElement.EID_GDB;
    private static final int START = VirtualControllerElement.EID_START;
    private static final int TRACKPAD = VirtualControllerElement.EID_TOUCHPAD;

    private static int[] rect(OscDefaultLayout.Slot s) {
        return new int[] { s.left, s.top, s.width, s.height };
    }

    @Test
    public void anchorsFollowTheDefaultSides() {
        assertEquals(OscPlacement.AnchorX.LEFT, OscDefaultLayout.anchorX(VirtualControllerElement.EID_DPAD));
        assertEquals(OscPlacement.AnchorX.LEFT, OscDefaultLayout.anchorX(VirtualControllerElement.EID_LB));
        assertEquals(OscPlacement.AnchorX.RIGHT, OscDefaultLayout.anchorX(VirtualControllerElement.EID_A));
        assertEquals(OscPlacement.AnchorX.RIGHT, OscDefaultLayout.anchorX(VirtualControllerElement.EID_RS));
        for (int middle : new int[] { BACK, GUIDE, START, TRACKPAD }) {
            assertEquals(OscPlacement.AnchorX.CENTER, OscDefaultLayout.anchorX(middle));
            assertEquals(OscPlacement.AnchorY.BOTTOM, OscDefaultLayout.anchorY(middle));
        }
        assertEquals(OscPlacement.AnchorY.TOP, OscDefaultLayout.anchorY(VirtualControllerElement.EID_Y));
        assertEquals(OscPlacement.AnchorY.TOP, OscDefaultLayout.anchorY(VirtualControllerElement.EID_LT));
        assertEquals(OscPlacement.AnchorY.TOP, OscDefaultLayout.anchorY(VirtualControllerElement.EID_LS));
        assertEquals(OscPlacement.AnchorY.BOTTOM, OscDefaultLayout.anchorY(VirtualControllerElement.EID_DPAD));
        assertEquals(OscPlacement.AnchorY.BOTTOM, OscDefaultLayout.anchorY(VirtualControllerElement.EID_RSB));
    }

    @Test
    public void aPlacementComesBackOnThePanelItWasMadeOn() {
        for (int[] size : PANELS) {
            OscGeometry.Panel panel = new OscGeometry.Panel(size[0], size[1]);
            for (Map.Entry<Integer, OscDefaultLayout.Slot> e : OscDefaultLayout.compute(panel, false, true).entrySet()) {
                int[] r = rect(e.getValue());
                // Moved towards the middle (still on the panel) and up
                r[0] += r[0] + r[2] / 2 > size[0] / 2 ? -37 : 37;
                r[1] -= 11;
                assertArrayEquals(size[0] + "x" + size[1] + " element " + e.getKey(),
                        r, OscPlacement.fromRect(e.getKey(), r, true, panel).toRect(panel));
            }
        }
    }

    @Test
    public void theDefaultMadeOnOnePanelIsTheDefaultOfEveryOther() {
        for (int[] from : PANELS) {
            OscGeometry.Panel a = new OscGeometry.Panel(from[0], from[1]);
            for (int[] to : PANELS) {
                OscGeometry.Panel b = new OscGeometry.Panel(to[0], to[1]);
                Map<Integer, OscDefaultLayout.Slot> there = OscDefaultLayout.compute(b, false, true);
                for (Map.Entry<Integer, OscDefaultLayout.Slot> e : OscDefaultLayout.compute(a, false, true).entrySet()) {
                    int[] mapped = OscPlacement.fromRect(e.getKey(), rect(e.getValue()), true, a).toRect(b);
                    int[] expected = rect(there.get(e.getKey()));
                    String what = Arrays.toString(from) + " -> " + Arrays.toString(to) + " element " + e.getKey();
                    for (int i = 0; i < 4; i++) {
                        assertEquals(what + " [" + i + "]", expected[i], mapped[i], 2);
                    }
                }
            }
        }
    }

    @Test
    public void middleControlsStayCentredOnEveryPanel() {
        OscGeometry.Panel odin = new OscGeometry.Panel(1920, 1080);
        Map<Integer, OscDefaultLayout.Slot> slots = OscDefaultLayout.compute(odin, false, true);
        for (int[] to : PANELS) {
            OscGeometry.Panel b = new OscGeometry.Panel(to[0], to[1]);
            int[] guide = OscPlacement.fromRect(GUIDE, rect(slots.get(GUIDE)), true, odin).toRect(b);
            int[] trackpad = OscPlacement.fromRect(TRACKPAD, rect(slots.get(TRACKPAD)), true, odin).toRect(b);
            int[] back = OscPlacement.fromRect(BACK, rect(slots.get(BACK)), true, odin).toRect(b);
            int[] start = OscPlacement.fromRect(START, rect(slots.get(START)), true, odin).toRect(b);
            assertEquals(to[0] / 2f, guide[0] + guide[2] / 2f, 1f);
            assertEquals(to[0] / 2f, trackpad[0] + trackpad[2] / 2f, 1f);
            assertEquals("Back's gap to the left edge = Start's to the right", back[0], to[0] - start[0] - start[2], 2);
        }
    }

    @Test
    public void aMovedControlKeepsItsDistanceFromItsAnchorEdge() {
        OscGeometry.Panel odin = new OscGeometry.Panel(1920, 1080);
        Map<Integer, OscDefaultLayout.Slot> slots = OscDefaultLayout.compute(odin, false, true);
        // The right stick pulled 150 px in from the right edge and 60 px up
        int[] rs = rect(slots.get(VirtualControllerElement.EID_RS));
        rs[0] -= 150;
        rs[1] -= 60;
        OscPlacement stick = OscPlacement.fromRect(VirtualControllerElement.EID_RS, rs, true, odin);
        // The d-pad pushed 40 px further from the left edge
        int[] dpad = rect(slots.get(VirtualControllerElement.EID_DPAD));
        dpad[0] += 40;
        OscPlacement pad = OscPlacement.fromRect(VirtualControllerElement.EID_DPAD, dpad, true, odin);
        // Guide moved 90 px right of the centre
        int[] guide = rect(slots.get(GUIDE));
        guide[0] += 90;
        OscPlacement g = OscPlacement.fromRect(GUIDE, guide, true, odin);
        for (int[] to : PANELS) {
            OscGeometry.Panel b = new OscGeometry.Panel(to[0], to[1]);
            float k = b.unit() / odin.unit();
            int[] s = stick.toRect(b);
            assertEquals("right gap " + Arrays.toString(to), (1920 - rs[0] - rs[2]) * k, to[0] - s[0] - s[2], 1.5f);
            assertEquals("bottom gap " + Arrays.toString(to), (1080 - rs[1] - rs[3]) * k, to[1] - s[1] - s[3], 1.5f);
            assertEquals("size " + Arrays.toString(to), rs[2] * k, s[2], 1f);
            int[] d = pad.toRect(b);
            assertEquals("left gap " + Arrays.toString(to), dpad[0] * k, d[0], 1f);
            int[] gr = g.toRect(b);
            assertEquals("from the centre " + Arrays.toString(to), (guide[0] + guide[2] / 2f - 960) * k, gr[0] + gr[2] / 2f - to[0] / 2f, 1.5f);
        }
    }

    @Test
    public void aPlacementIsAlwaysOnThePanel() {
        OscPlacement far = new OscPlacement(OscPlacement.AnchorX.RIGHT, OscPlacement.AnchorY.BOTTOM, -50f, -50f, 10f, 10f, true);
        int[] r = far.toRect(new OscGeometry.Panel(1280, 800));
        assertTrue(r[0] >= 0 && r[1] >= 0 && r[0] + r[2] <= 1280 && r[1] + r[3] <= 800);
    }

    /** The owner's Odin: Back, Guide, Start and the trackpad as Artemis put them on 1920 x 1025 (the display metrics without the bar). */
    static Map<Integer, int[]> ownersOldLayout() {
        Map<Integer, int[]> m = new LinkedHashMap<>();
        m.put(BACK, new int[] { 484, 911, 170, 99 });
        m.put(GUIDE, new int[] { 795, 911, 170, 99 });
        m.put(START, new int[] { 1279, 911, 170, 99 });
        m.put(TRACKPAD, new int[] { 711, 711, 284, 170 });
        return m;
    }

    @Test
    public void theOwnersOldSavedLayoutIsAnOldDefault() {
        assertEquals(new HashSet<>(Arrays.asList(BACK, GUIDE, START, TRACKPAD)), OscDefaultLayout.oldDefaults(ownersOldLayout()));
    }

    @Test
    public void aDelosDefaultSavedWithoutItsSizeIsAnOldDefault() {
        Map<Integer, int[]> saved = new LinkedHashMap<>();
        for (Map.Entry<Integer, OscDefaultLayout.Slot> e : OscDefaultLayout.compute(new OscGeometry.Panel(1809, 1080), false, true).entrySet()) {
            saved.put(e.getKey(), rect(e.getValue()));
        }
        assertEquals(saved.keySet(), OscDefaultLayout.oldDefaults(saved));
    }

    @Test
    public void aRealEditIsNotAnOldDefault() {
        Map<Integer, int[]> saved = ownersOldLayout();
        saved.get(START)[0] -= 200;
        saved.get(TRACKPAD)[1] -= 50;
        Set<Integer> old = OscDefaultLayout.oldDefaults(saved);
        assertEquals(new HashSet<>(Arrays.asList(BACK, GUIDE)), old);
        // One element alone is never taken for a default
        Map<Integer, int[]> one = new LinkedHashMap<>();
        one.put(BACK, ownersOldLayout().get(BACK));
        assertTrue(OscDefaultLayout.oldDefaults(one).isEmpty());
    }
}
