package com.limelight.binding.input;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

/** Guide/Home and Back (issue #102): a tap opens the stream menu, a hold sends Guide; driven by a fake clock. */
public class GuideTapHoldTest {
    /** The fake clock: milliseconds since the test started. */
    private long now = 1_000;

    private final GuideTapHold g = new GuideTapHold();

    private void advance(long ms) {
        now += ms;
    }

    @Test
    public void aTapOpensTheMenuOnReleaseAndSendsNoGuide() {
        assertTrue("the first down starts the hold timer", g.onDown(now));
        advance(120);
        assertEquals(GuideTapHold.Action.TAP_MENU, g.onUp());
        assertFalse(g.isGuideDown());
    }

    @Test
    public void aHoldPressesGuideWhileHeldAndReleasesItOnUp() {
        g.onDown(now);
        advance(GuideTapHold.HOLD_MS);
        assertEquals("decided while held, not on release", GuideTapHold.Action.GUIDE_DOWN, g.onHoldTimeout(now));
        assertTrue(g.isGuideDown());
        advance(2_000);
        assertEquals("no menu follows a hold", GuideTapHold.Action.GUIDE_UP, g.onUp());
        assertFalse(g.isGuideDown());
    }

    @Test
    public void aReleaseJustBeforeTheHoldTimeIsATap() {
        g.onDown(now);
        advance(GuideTapHold.HOLD_MS - 1);
        assertEquals(GuideTapHold.Action.NONE, g.onHoldTimeout(now));
        assertEquals(GuideTapHold.Action.TAP_MENU, g.onUp());
    }

    @Test
    public void keyRepeatsOfAHeldButtonDoNotRestartTheHold() {
        assertTrue(g.onDown(now));
        advance(300);
        assertFalse(g.onDown(now));
        advance(200);
        assertFalse(g.onDown(now));
        assertEquals(GuideTapHold.Action.GUIDE_DOWN, g.onHoldTimeout(now));
        assertEquals(GuideTapHold.Action.GUIDE_UP, g.onUp());
    }

    @Test
    public void aLateTimerAfterReleaseAndAStrayUpDoNothing() {
        g.onDown(now);
        g.onUp();
        advance(GuideTapHold.HOLD_MS);
        assertEquals(GuideTapHold.Action.NONE, g.onHoldTimeout(now));
        assertEquals(GuideTapHold.Action.NONE, g.onUp());
    }

    @Test
    public void aStaleTimerOfAnEarlierPressDoesNotCutTheNextPressShort() {
        g.onDown(now);
        advance(100);
        g.onUp();
        advance(100);
        g.onDown(now);
        advance(GuideTapHold.HOLD_MS - 200);
        assertEquals("the first press's timer fires 300 ms into the second", GuideTapHold.Action.NONE, g.onHoldTimeout(now));
        advance(200);
        assertEquals(GuideTapHold.Action.GUIDE_DOWN, g.onHoldTimeout(now));
    }

    @Test
    public void theTimerFiresOncePerPress() {
        g.onDown(now);
        advance(GuideTapHold.HOLD_MS);
        assertEquals(GuideTapHold.Action.GUIDE_DOWN, g.onHoldTimeout(now));
        assertEquals(GuideTapHold.Action.NONE, g.onHoldTimeout(now));
    }

    @Test
    public void resetForgetsAPressAndSaysWhetherGuideWasDown() {
        g.onDown(now);
        advance(GuideTapHold.HOLD_MS);
        g.onHoldTimeout(now);
        assertTrue(g.reset());
        assertEquals(GuideTapHold.Action.NONE, g.onUp());
        assertTrue("a new press works after a reset", g.onDown(now));
        assertFalse(g.reset());
    }

    @Test
    public void theHoldTimeIsHalfASecond() {
        assertEquals(500, GuideTapHold.HOLD_MS);
    }

    @Test
    public void windowsBackTapRequestsOneGuidePulseAndNeverTheMenu() {
        assertTrue(g.onDown(now, true));
        advance(80);
        assertEquals(GuideTapHold.Action.GUIDE_TAP, g.onUp());
        assertFalse(g.isGuideDown());
        assertEquals(GuideTapHold.Action.NONE, g.onUp());
        advance(500);
        assertEquals(GuideTapHold.Action.NONE, g.onHoldTimeout(now));
    }

    @Test
    public void windowsBackHoldRequestsTaskViewWhileDownAndNothingOnRelease() {
        g.onDown(now, true);
        advance(499);
        assertEquals(GuideTapHold.Action.NONE, g.onHoldTimeout(now));
        advance(1);
        assertEquals(GuideTapHold.Action.TASK_VIEW, g.onHoldTimeout(now));
        assertFalse(g.isGuideDown());
        advance(100);
        assertEquals(GuideTapHold.Action.NONE, g.onHoldTimeout(now));
        assertEquals(GuideTapHold.Action.NONE, g.onUp());
    }

    @Test
    public void repeatCannotChangeTheModeOfAnExistingPress() {
        g.onDown(now, true);
        advance(300);
        assertFalse(g.onDown(now, false));
        advance(200);
        assertEquals(GuideTapHold.Action.TASK_VIEW, g.onHoldTimeout(now));
        assertEquals(GuideTapHold.Action.NONE, g.onUp());
        g.onDown(now, false);
        advance(500);
        assertEquals(GuideTapHold.Action.GUIDE_DOWN, g.onHoldTimeout(now));
        assertEquals(GuideTapHold.Action.GUIDE_UP, g.onUp());
    }

    @Test
    public void resetCancelsWindowsBackWithoutSendingAnything() {
        g.onDown(now, true);
        assertFalse(g.reset());
        advance(500);
        assertEquals(GuideTapHold.Action.NONE, g.onHoldTimeout(now));
        assertEquals(GuideTapHold.Action.NONE, g.onUp());
        g.onDown(now);
        assertEquals(GuideTapHold.Action.TAP_MENU, g.onUp());
    }
}
