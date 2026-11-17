package com.limelight.shell

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SideMenuSwipeTest {
    private val width = 1000f
    private val fling = 1500f
    private val slop = 16f

    @Test fun onlyATouchInTheEdgeBandStartsTheOpeningDrag() {
        val edge = 100f
        assertTrue(SideMenuSwipe.startsAtEdge(0f, edge))
        assertTrue(SideMenuSwipe.startsAtEdge(99.9f, edge))
        assertFalse(SideMenuSwipe.startsAtEdge(100f, edge))
        assertFalse(SideMenuSwipe.startsAtEdge(-1f, edge))
    }

    @Test fun theExclusionIsCentredAndNeverTallerThanAndroidAllows() {
        assertEquals(EdgeBand(0f, 340f, 100f, 740f), SideMenuSwipe.exclusion(1080f, 100f, 400f))
        // A window lower than the limit is excluded whole
        assertEquals(EdgeBand(0f, 0f, 100f, 300f), SideMenuSwipe.exclusion(300f, 100f, 400f))
    }

    @Test fun aDragBelongsToTheMenuOnlyWhenItGoesSidewaysTheRightWay() {
        // Still within the slop: undecided
        assertNull(SideMenuSwipe.claims(true, 10f, 4f, slop))
        // Opening: to the right, more sideways than vertical
        assertEquals(true, SideMenuSwipe.claims(true, 30f, 10f, slop))
        assertEquals(false, SideMenuSwipe.claims(true, -30f, 0f, slop))
        // A vertical scroll is left to the screen
        assertEquals(false, SideMenuSwipe.claims(true, 20f, 40f, slop))
        // Closing: to the left
        assertEquals(true, SideMenuSwipe.claims(false, -30f, 5f, slop))
        assertEquals(false, SideMenuSwipe.claims(false, 30f, 5f, slop))
    }

    @Test fun theProgressFollowsTheFingerAndIsDampedPastFullyOpen() {
        assertEquals(.25f, SideMenuSwipe.progress(0f, 250f, width), 1e-4f)
        assertEquals(.6f, SideMenuSwipe.progress(1f, -400f, width), 1e-4f)
        // Never below closed
        assertEquals(0f, SideMenuSwipe.progress(0f, -300f, width), 0f)
        assertEquals(0f, SideMenuSwipe.progress(1f, -1500f, width), 0f)
        // Past fully open it moves less than the finger and never reaches the limit
        val pulled = SideMenuSwipe.progress(0f, 1100f, width)
        assertTrue(pulled > 1f && pulled < 1.1f)
        assertTrue(SideMenuSwipe.progress(0f, 100_000f, width) < 1f + SideMenuSwipe.RUBBER)
        // The pull starts as fast as the finger: no jump at fully open
        assertEquals(1.001f, SideMenuSwipe.progress(0f, 1001f, width), 1e-4f)
    }

    @Test fun aSlowReleaseOpensOrClosesByTheDistance() {
        assertFalse(SideMenuSwipe.settlesOpen(true, .4f, 0f, fling))
        assertTrue(SideMenuSwipe.settlesOpen(true, .41f, 0f, fling))
        // From open, 40 % of the width to the left closes it
        assertTrue(SideMenuSwipe.settlesOpen(false, .61f, 0f, fling))
        assertFalse(SideMenuSwipe.settlesOpen(false, .59f, 0f, fling))
        // A speed below the fling does not decide
        assertFalse(SideMenuSwipe.settlesOpen(true, .2f, fling - 1f, fling))
    }

    @Test fun aFlingGoesTheWayItMoves() {
        assertTrue(SideMenuSwipe.settlesOpen(true, .1f, fling, fling))
        assertFalse(SideMenuSwipe.settlesOpen(true, .9f, -fling, fling))
        assertFalse(SideMenuSwipe.settlesOpen(false, .9f, -fling, fling))
        assertTrue(SideMenuSwipe.settlesOpen(false, .2f, fling, fling))
    }

    @Test fun reducedMotionTogglesOnlyAfterTheThreshold() {
        assertFalse(SideMenuSwipe.reducedToggles(true, 399f, width))
        assertTrue(SideMenuSwipe.reducedToggles(true, 400f, width))
        assertFalse(SideMenuSwipe.reducedToggles(true, -500f, width))
        assertTrue(SideMenuSwipe.reducedToggles(false, -400f, width))
        assertFalse(SideMenuSwipe.reducedToggles(false, 400f, width))
    }
}
