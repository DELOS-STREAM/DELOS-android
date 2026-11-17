package com.limelight.binding.input.virtual_controller;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.robolectric.Shadows.shadowOf;

import android.content.Context;
import android.content.SharedPreferences;
import android.os.Looper;
import android.view.View;
import android.view.WindowManager;
import android.widget.FrameLayout;

import androidx.appcompat.app.AppCompatActivity;
import androidx.test.core.app.ApplicationProvider;

import com.limelight.R;
import com.limelight.shadows.ShadowMoonBridge;
import com.limelight.shell.settings.ProfileLayouts;

import org.json.JSONObject;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.Robolectric;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.android.controller.ActivityController;
import org.robolectric.annotation.Config;

import java.util.Map;

/**
 * #101: the middle controls (Back, Guide, Start, the trackpad) are centred on the frame they are drawn in, and on the screen in the
 * stream's window; stored layouts are anchored and resolution independent, old ones are converted. The display metrics here are the
 * AYN Odin 2 Portal's 1920 x 1080 without 111 px at the side (a side navigation bar), the frame is the full-screen one.
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
    }

    @Test
    public void fullScreenFrameCentresTheMiddleOnItsWidth() {
        assertEquals(METRICS_WIDTH, context.getResources().getDisplayMetrics().widthPixels);
        // The preview builds the controls before its frame is laid out, the stream may too: the frame's size takes over
        FrameLayout frame = new FrameLayout(context);
        VirtualController controller = new VirtualController(null, frame, context);
        controller.refreshLayout();
        layOut(frame, ODIN_WIDTH, ODIN_HEIGHT);

        assertMiddleCentredAt(controller, ODIN_WIDTH / 2f);
    }

    @Test
    public void frameAlreadyLaidOutIsUsedRightAway() {
        VirtualController controller = laidOutController(ODIN_WIDTH, ODIN_HEIGHT);
        assertMiddleCentredAt(controller, ODIN_WIDTH / 2f);
    }

    @Test
    public void frameThatLeavesOutTheNavigationBarCentresOnWhatIsLeft() {
        FrameLayout frame = new FrameLayout(context);
        VirtualController controller = new VirtualController(null, frame, context);
        controller.refreshLayout();
        layOut(frame, METRICS_WIDTH, ODIN_HEIGHT);

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
        layOut(frame, ODIN_WIDTH, ODIN_HEIGHT);

        assertMiddleCentredAt(controller, (ODIN_WIDTH - inset) / 2f);
    }

    @Test
    public void leavingTheEditorUntouchedSavesNothing() {
        VirtualController controller = laidOutController(ODIN_WIDTH, ODIN_HEIGHT);

        VirtualControllerConfigurationLoader.saveProfile(controller, context);

        assertTrue(saved.getAll().isEmpty());
    }

    @Test
    public void anEditIsStoredAnchoredAndKeepsItsEdgeDistanceOnAnotherPanel() throws Exception {
        VirtualController controller = laidOutController(ODIN_WIDTH, ODIN_HEIGHT);
        VirtualControllerElement a = element(controller, VirtualControllerElement.EID_A);
        FrameLayout.LayoutParams lp = margins(a);
        lp.leftMargin -= 120;
        int rightGap = ODIN_WIDTH - lp.leftMargin - lp.width;

        VirtualControllerConfigurationLoader.saveProfile(controller, context);

        // One layout document, only the changed control, no pixels of the screen
        assertEquals(1, saved.getAll().size());
        JSONObject document = new JSONObject(saved.getString("layout.global", null));
        assertEquals(OscLayoutStore.VERSION, document.getInt("version"));
        assertEquals(OscLayoutStore.GLOBAL, document.getString("id"));
        JSONObject stored = document.getJSONObject("elements").getJSONObject(String.valueOf(VirtualControllerElement.EID_A));
        assertEquals("RIGHT", stored.getString("anchorX"));
        assertEquals(1, document.getJSONObject("elements").length());

        // The same height on a wider panel: the same distance to the right edge
        VirtualController wide = laidOutController(2400, ODIN_HEIGHT);
        FrameLayout.LayoutParams moved = margins(element(wide, VirtualControllerElement.EID_A));
        assertEquals(rightGap, 2400 - moved.leftMargin - moved.width, 1);
        assertMiddleCentredAt(wide, 1200f);
    }

    @Test
    public void theOwnersOldLayoutIsDroppedAndTheMiddleCentred() throws Exception {
        // What the Odin had stored: Artemis' default of 1920 x 1025, without the screen's size
        writeOld(OscPlacementTest.ownersOldLayout(), false);

        VirtualController controller = laidOutController(ODIN_WIDTH, ODIN_HEIGHT);

        assertMiddleCentredAt(controller, ODIN_WIDTH / 2f);
        assertTrue("converted once, nothing left to store", saved.getAll().isEmpty());
    }

    @Test
    public void anOldDefaultWithItsScreenIsDroppedAndAnOldEditConverted() throws Exception {
        // Saved on the metrics' panel with its size: every element where the default put it, but Start moved 100 px right
        Map<Integer, OscDefaultLayout.Slot> slots = OscDefaultLayout.compute(new OscGeometry.Panel(METRICS_WIDTH, ODIN_HEIGHT), false, true);
        SharedPreferences.Editor editor = saved.edit();
        for (Map.Entry<Integer, OscDefaultLayout.Slot> e : slots.entrySet()) {
            OscDefaultLayout.Slot s = e.getValue();
            int left = e.getKey() == VirtualControllerElement.EID_START ? s.left + 100 : s.left;
            editor.putString(String.valueOf(e.getKey()), old(left, s.top, s.width, s.height, METRICS_WIDTH, ODIN_HEIGHT).toString());
        }
        editor.commit();

        VirtualController controller = laidOutController(ODIN_WIDTH, ODIN_HEIGHT);

        FrameLayout.LayoutParams back = margins(element(controller, VirtualControllerElement.EID_BACK));
        FrameLayout.LayoutParams start = margins(element(controller, VirtualControllerElement.EID_START));
        FrameLayout.LayoutParams trackpad = margins(element(controller, VirtualControllerElement.EID_TOUCHPAD));
        assertEquals(ODIN_WIDTH / 2f, trackpad.leftMargin + trackpad.width / 2f, 1f);
        // Start keeps its 100 px from where the default put it, now against the centre of 1920
        OscDefaultLayout.Slot defaultStart = OscDefaultLayout.compute(new OscGeometry.Panel(ODIN_WIDTH, ODIN_HEIGHT), false, true)
                .get(VirtualControllerElement.EID_START);
        assertEquals(defaultStart.left + 100, start.leftMargin, 1);
        assertEquals(OscDefaultLayout.compute(new OscGeometry.Panel(ODIN_WIDTH, ODIN_HEIGHT), false, true)
                .get(VirtualControllerElement.EID_BACK).left, back.leftMargin);
        // Only Start is stored, anchored; the old keys are gone
        assertFalse(saved.contains(String.valueOf(VirtualControllerElement.EID_START)));
        assertEquals(1, new JSONObject(saved.getString("layout.global", null)).getJSONObject("elements").length());
    }

    @Test
    public void defaultCheckMatchesOnlyTheDefaultSlot() {
        OscGeometry.Panel panel = new OscGeometry.Panel(ODIN_WIDTH, ODIN_HEIGHT);
        OscDefaultLayout.Slot back = OscDefaultLayout.compute(panel, false, false).get(VirtualControllerElement.EID_BACK);
        assertTrue(OscDefaultLayout.isDefault(VirtualControllerElement.EID_BACK, back.left, back.top, back.width, back.height, ODIN_WIDTH, ODIN_HEIGHT));
        assertFalse(OscDefaultLayout.isDefault(VirtualControllerElement.EID_BACK, back.left + 1, back.top, back.width, back.height, ODIN_WIDTH, ODIN_HEIGHT));
        assertFalse(OscDefaultLayout.isDefault(VirtualControllerElement.EID_BACK, back.left, back.top, back.width, back.height, 0, 0));
    }

    /**
     * The stream's arrangement: DelosStreamActivity is an AppCompatActivity in StreamTheme with activity_game, the stream's window and
     * system UI flags, the controls in the stream container's parent (AppCompat's content frame). AppCompat's FitWindowsLinearLayout
     * around it fits system windows; with LAYOUT_STABLE that is the gesture bar's inset even while it is hidden (55 px at the bottom
     * on the Odin), set here as its padding. Seen on the screen, Guide and the trackpad are on its centre and Back is as far from the
     * left edge as Start from the right.
     */
    @Test
    @Config(qualifiers = "w1920dp-h1080dp-land-mdpi")
    public void inTheStreamWindowTheMiddleIsOnTheScreensCentre() throws Exception {
        writeOld(OscPlacementTest.ownersOldLayout(), false);
        ActivityController<AppCompatActivity> activityController = Robolectric.buildActivity(AppCompatActivity.class);
        AppCompatActivity activity = activityController.get();
        activity.setTheme(R.style.StreamTheme);
        activityController.create();
        activity.getWindow().addFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN | WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN);
        View decor = activity.getWindow().getDecorView();
        decor.setSystemUiVisibility(View.SYSTEM_UI_FLAG_LAYOUT_STABLE | View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
                | View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN | View.SYSTEM_UI_FLAG_HIDE_NAVIGATION | View.SYSTEM_UI_FLAG_FULLSCREEN
                | View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY);
        activity.setContentView(R.layout.activity_game);
        activityController.start().resume().visible();
        shadowOf(Looper.getMainLooper()).idle();
        FrameLayout root = (FrameLayout) activity.findViewById(R.id.streamContainer).getParent();
        ((View) root.getParent()).setPadding(0, 0, 0, 55);
        decor.measure(View.MeasureSpec.makeMeasureSpec(ODIN_WIDTH, View.MeasureSpec.EXACTLY),
                View.MeasureSpec.makeMeasureSpec(ODIN_HEIGHT, View.MeasureSpec.EXACTLY));
        decor.layout(0, 0, ODIN_WIDTH, ODIN_HEIGHT);
        shadowOf(Looper.getMainLooper()).idle();
        assertEquals(ODIN_HEIGHT - 55, root.getHeight());

        assertEquals(ODIN_WIDTH, root.getWidth());
        VirtualController controller = new VirtualController(null, root, activity);
        controller.refreshLayout();
        controller.show();
        shadowOf(Looper.getMainLooper()).idle();

        int[] at = new int[2];
        root.getLocationInWindow(at);
        assertEquals(0, at[0]);
        int offsetX = at[0] + root.getPaddingLeft();
        int[] back = onScreen(element(controller, VirtualControllerElement.EID_BACK), offsetX);
        int[] start = onScreen(element(controller, VirtualControllerElement.EID_START), offsetX);
        int[] trackpad = onScreen(element(controller, VirtualControllerElement.EID_TOUCHPAD), offsetX);
        assertEquals("trackpad on the screen's centre", ODIN_WIDTH / 2f, trackpad[0] + trackpad[1] / 2f, 1f);
        assertEquals("Back's left gap = Start's right gap", back[0], ODIN_WIDTH - start[0] - start[1], 2);
        // Guide is on by default, as on the Odin
        int[] guide = onScreen(element(controller, VirtualControllerElement.EID_GDB), offsetX);
        assertEquals("Guide on the screen's centre", ODIN_WIDTH / 2f, guide[0] + guide[1] / 2f, 1f);
        assertNull("the old layout is gone", saved.getString(String.valueOf(VirtualControllerElement.EID_GDB), null));
    }

    /** {left on the screen, width} of an element in a frame whose content starts {@code offsetX} from the window's left edge. */
    private static int[] onScreen(VirtualControllerElement element, int offsetX) {
        FrameLayout.LayoutParams lp = margins(element);
        return new int[] { offsetX + lp.leftMargin, lp.width };
    }

    private VirtualController laidOutController(int width, int height) {
        FrameLayout frame = new FrameLayout(context);
        layOut(frame, width, height);
        VirtualController controller = new VirtualController(null, frame, context);
        controller.refreshLayout();
        return controller;
    }

    private void writeOld(Map<Integer, int[]> rects, boolean withScreen) throws Exception {
        SharedPreferences.Editor editor = saved.edit();
        for (Map.Entry<Integer, int[]> e : rects.entrySet()) {
            int[] r = e.getValue();
            editor.putString(String.valueOf(e.getKey()), old(r[0], r[1], r[2], r[3], withScreen ? ODIN_WIDTH : 0, withScreen ? ODIN_HEIGHT : 0).toString());
        }
        editor.commit();
    }

    /** A version 1 entry: absolute pixels, the screen's size when {@code refWidth} is over 0. */
    private static JSONObject old(int left, int top, int width, int height, int refWidth, int refHeight) throws Exception {
        JSONObject c = new JSONObject();
        c.put("LEFT", left);
        c.put("TOP", top);
        c.put("WIDTH", width);
        c.put("HEIGHT", height);
        c.put("ENABLED", true);
        if (refWidth > 0) {
            c.put("REF_W", refWidth);
            c.put("REF_H", refHeight);
        }
        return c;
    }

    /** Lays the frame out at {@code width} x {@code height} and runs what that posts (the controls laid out again). */
    private static void layOut(FrameLayout frame, int width, int height) {
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
