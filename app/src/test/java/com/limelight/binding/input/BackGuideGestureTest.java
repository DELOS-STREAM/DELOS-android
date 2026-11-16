package com.limelight.binding.input;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

/** Hold Back for Guide (issue #90): what reaches the PC for a short press, a hold and the edge cases around them. */
public class BackGuideGestureTest {
    @Test
    public void aShortPressIsATapOfBackOnRelease() {
        BackGuideGesture g = new BackGuideGesture();
        assertTrue("the first down starts the hold timer", g.onDown());
        assertEquals(BackGuideGesture.Action.TAP_BACK, g.onUp());
        assertFalse(g.isGuideDown());
    }

    @Test
    public void aHoldPressesGuideAndReleasesItOnUp() {
        BackGuideGesture g = new BackGuideGesture();
        g.onDown();
        assertEquals(BackGuideGesture.Action.GUIDE_DOWN, g.onHoldTimeout());
        assertTrue(g.isGuideDown());
        assertEquals("no Back follows a hold", BackGuideGesture.Action.GUIDE_UP, g.onUp());
        assertFalse(g.isGuideDown());
    }

    @Test
    public void keyRepeatsOfAHeldButtonDoNotRestartTheHold() {
        BackGuideGesture g = new BackGuideGesture();
        assertTrue(g.onDown());
        assertFalse(g.onDown());
        assertFalse(g.onDown());
        assertEquals(BackGuideGesture.Action.GUIDE_DOWN, g.onHoldTimeout());
        assertEquals(BackGuideGesture.Action.GUIDE_UP, g.onUp());
    }

    @Test
    public void aLateTimerAfterReleaseAndAStrayUpSendNothing() {
        BackGuideGesture g = new BackGuideGesture();
        g.onDown();
        g.onUp();
        assertEquals(BackGuideGesture.Action.NONE, g.onHoldTimeout());
        assertEquals(BackGuideGesture.Action.NONE, g.onUp());
    }

    @Test
    public void theTimerFiresOncePerPress() {
        BackGuideGesture g = new BackGuideGesture();
        g.onDown();
        assertEquals(BackGuideGesture.Action.GUIDE_DOWN, g.onHoldTimeout());
        assertEquals(BackGuideGesture.Action.NONE, g.onHoldTimeout());
    }

    @Test
    public void resetForgetsAPressAndSaysWhetherGuideWasDown() {
        BackGuideGesture g = new BackGuideGesture();
        g.onDown();
        g.onHoldTimeout();
        assertTrue(g.reset());
        assertEquals(BackGuideGesture.Action.NONE, g.onUp());
        assertTrue("a new press works after a reset", g.onDown());
        assertFalse(g.reset());
    }

    @Test
    public void theHoldTimeIsHalfASecond() {
        assertEquals(500, BackGuideGesture.HOLD_MS);
    }
}
