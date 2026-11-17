package com.limelight.binding.input.virtual_controller;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import static org.robolectric.Shadows.shadowOf;

import android.content.Context;
import android.content.SharedPreferences;
import android.os.Looper;
import android.view.View;
import android.widget.FrameLayout;

import androidx.test.core.app.ApplicationProvider;

import com.limelight.shadows.ShadowMoonBridge;
import com.limelight.shell.settings.ProfileLayouts;

import org.json.JSONObject;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;

/**
 * #101: the middle controls (Back, Guide, Start, the trackpad) are centred on the frame they are drawn in. The display metrics here
 * are the AYN Odin 2 Portal's 1920 x 1080 without a navigation bar of 111 px at the side (what Android 13 reports to an app there),
 * the frame is the full-screen stream's or the edge-to-edge shell's.
 */
@RunWith(RobolectricTestRunner.class)
@Config(sdk = 33, shadows = {ShadowMoonBridge.class}, qualifiers = "w1809dp-h1080dp-land-mdpi")
public class VirtualControllerFrameTest {
    private static final int ODIN_WIDTH = 1920;
    private static final int ODIN_HEIGHT = 1080;
    private static final int METRICS_WIDTH = 1809;

    private Context context;
    private SharedPreferences saved;

    @Before
    public void setUp() {
        context = ApplicationProvider.getApplicationContext();
        saved = ProfileLayouts.preferences(context, VirtualControllerConfigurationLoader.OSC_PREFERENCE);
        saved.edit().clear().commit();
        assertEquals(METRICS_WIDTH, context.getResources().getDisplayMetrics().widthPixels);
    }

    @Test
    public void fullScreenFrameCentresTheMiddleOnItsWidth() {
        // The preview builds the controls before its frame is laid out, the stream may too: the frame's size takes over
        FrameLayout frame = new FrameLayout(context);
        VirtualController controller = new VirtualController(null, frame, context);
        controller.refreshLayout();
        layOutFrame(frame, ODIN_WIDTH, ODIN_HEIGHT);

        assertMiddleCentredAt(controller, ODIN_WIDTH / 2f);
    }

    @Test
    public void frameAlreadyLaidOutIsUsedRightAway() {
        FrameLayout frame = new FrameLayout(context);
        layOutFrame(frame, ODIN_WIDTH, ODIN_HEIGHT);
        VirtualController controller = new VirtualController(null, frame, context);
        controller.refreshLayout();

        assertMiddleCentredAt(controller, ODIN_WIDTH / 2f);
    }

    @Test
    public void frameThatLeavesOutTheNavigationBarCentresOnWhatIsLeft() {
        FrameLayout frame = new FrameLayout(context);
        VirtualController controller = new VirtualController(null, frame, context);
        controller.refreshLayout();
        layOutFrame(frame, METRICS_WIDTH, ODIN_HEIGHT);

        assertMiddleCentredAt(controller, METRICS_WIDTH / 2f);
    }

    @Test
    public void insetOnOneSideCentresOnTheFreeArea() {
        // A cutout (or bar) inset as padding on the left: the margins start after it
        int inset = 111;
        FrameLayout frame = new FrameLayout(context);
        frame.setPadding(inset, 0, 0, 0);
        VirtualController controller = new VirtualController(null, frame, context);
        controller.refreshLayout();
        layOutFrame(frame, ODIN_WIDTH, ODIN_HEIGHT);

        assertMiddleCentredAt(controller, (ODIN_WIDTH - inset) / 2f);
    }

    @Test
    public void leavingTheEditorUntouchedSavesNothing() {
        FrameLayout frame = new FrameLayout(context);
        layOutFrame(frame, ODIN_WIDTH, ODIN_HEIGHT);
        VirtualController controller = new VirtualController(null, frame, context);
        controller.refreshLayout();

        VirtualControllerConfigurationLoader.saveProfile(controller, context);

        assertTrue(saved.getAll().isEmpty());
    }

    @Test
    public void editedElementIsSavedAndKept() throws Exception {
        FrameLayout frame = new FrameLayout(context);
        layOutFrame(frame, ODIN_WIDTH, ODIN_HEIGHT);
        VirtualController controller = new VirtualController(null, frame, context);
        controller.refreshLayout();
        VirtualControllerElement start = element(controller, VirtualControllerElement.EID_START);
        ((FrameLayout.LayoutParams) start.getLayoutParams()).leftMargin += 40;

        VirtualControllerConfigurationLoader.saveProfile(controller, context);

        assertEquals(1, saved.getAll().size());
        JSONObject stored = new JSONObject(saved.getString(String.valueOf(VirtualControllerElement.EID_START), null));
        controller.refreshLayout();
        assertEquals(stored.getInt("LEFT"), margins(element(controller, VirtualControllerElement.EID_START)).leftMargin);
    }

    @Test
    public void frozenDefaultOfTheMetricsIsDroppedAndRecentred() throws Exception {
        // What leaving the editor stored before: every element where the default put it on the display metrics' panel, left of the centre
        OscGeometry.Panel metricsPanel = new OscGeometry.Panel(METRICS_WIDTH, ODIN_HEIGHT);
        SharedPreferences.Editor editor = saved.edit();
        for (java.util.Map.Entry<Integer, OscDefaultLayout.Slot> slot : OscDefaultLayout.compute(metricsPanel, false, true).entrySet()) {
            OscDefaultLayout.Slot s = slot.getValue();
            JSONObject configuration = new JSONObject();
            configuration.put("LEFT", s.left);
            configuration.put("TOP", s.top);
            configuration.put("WIDTH", s.width);
            configuration.put("HEIGHT", s.height);
            configuration.put("ENABLED", true);
            // Back with its screen (since phase D), Start without it (older versions)
            if (slot.getKey() != VirtualControllerElement.EID_START) {
                configuration.put("REF_W", METRICS_WIDTH);
                configuration.put("REF_H", ODIN_HEIGHT);
            }
            editor.putString(String.valueOf(slot.getKey()), configuration.toString());
        }
        editor.commit();

        FrameLayout frame = new FrameLayout(context);
        layOutFrame(frame, ODIN_WIDTH, ODIN_HEIGHT);
        VirtualController controller = new VirtualController(null, frame, context);
        controller.refreshLayout();

        assertMiddleCentredAt(controller, ODIN_WIDTH / 2f);
        assertFalse(saved.contains(String.valueOf(VirtualControllerElement.EID_BACK)));
        assertFalse(saved.contains(String.valueOf(VirtualControllerElement.EID_START)));
    }

    @Test
    public void defaultCheckMatchesOnlyTheDefaultSlot() {
        OscGeometry.Panel panel = new OscGeometry.Panel(ODIN_WIDTH, ODIN_HEIGHT);
        OscDefaultLayout.Slot back = OscDefaultLayout.compute(panel, false, false).get(VirtualControllerElement.EID_BACK);
        assertNotNull(back);
        assertTrue(OscDefaultLayout.isDefault(VirtualControllerElement.EID_BACK, back.left, back.top, back.width, back.height, ODIN_WIDTH, ODIN_HEIGHT));
        assertFalse(OscDefaultLayout.isDefault(VirtualControllerElement.EID_BACK, back.left + 1, back.top, back.width, back.height, ODIN_WIDTH, ODIN_HEIGHT));
        assertFalse(OscDefaultLayout.isDefault(VirtualControllerElement.EID_BACK, back.left, back.top, back.width, back.height, 0, 0));
    }

    /** Lays the frame out at {@code width} x {@code height} and runs what that posts (the controls laid out again). */
    private static void layOutFrame(FrameLayout frame, int width, int height) {
        frame.measure(View.MeasureSpec.makeMeasureSpec(width, View.MeasureSpec.EXACTLY),
                View.MeasureSpec.makeMeasureSpec(height, View.MeasureSpec.EXACTLY));
        frame.layout(0, 0, width, height);
        shadowOf(Looper.getMainLooper()).idle();
    }

    /** Back and Start symmetric around {@code center}, the trackpad (and Guide, when on) centred on it, within the 1 px of rounding. */
    private static void assertMiddleCentredAt(VirtualController controller, float center) {
        FrameLayout.LayoutParams back = margins(element(controller, VirtualControllerElement.EID_BACK));
        FrameLayout.LayoutParams start = margins(element(controller, VirtualControllerElement.EID_START));
        FrameLayout.LayoutParams trackpad = margins(element(controller, VirtualControllerElement.EID_TOUCHPAD));
        assertEquals("Back / Start pair", center, (back.leftMargin + start.leftMargin + start.width) / 2f, 1f);
        assertEquals("Back to centre vs centre to Start", center - back.leftMargin, start.leftMargin + start.width - center, 1f);
        assertEquals("trackpad", center, trackpad.leftMargin + trackpad.width / 2f, 1f);
        for (VirtualControllerElement element : controller.getElements()) {
            if (element.elementId == VirtualControllerElement.EID_GDB) {
                FrameLayout.LayoutParams guide = margins(element);
                assertEquals("Guide", center, guide.leftMargin + guide.width / 2f, 1f);
            }
        }
    }

    private static VirtualControllerElement element(VirtualController controller, int elementId) {
        for (VirtualControllerElement element : controller.getElements()) {
            if (element.elementId == elementId) {
                return element;
            }
        }
        throw new AssertionError("no element " + elementId);
    }

    private static FrameLayout.LayoutParams margins(VirtualControllerElement element) {
        return (FrameLayout.LayoutParams) element.getLayoutParams();
    }
}
