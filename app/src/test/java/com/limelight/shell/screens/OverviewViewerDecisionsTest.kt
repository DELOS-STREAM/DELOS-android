package com.limelight.shell.screens

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class OverviewViewerDecisionsTest {
    @Test fun `close during a released swipe prevents its late index commit`() {
        val target = OverviewViewerDecisions.slideTarget(1, 4, -100f, 1f, closing = false)
        assertEquals(2, target)
        assertTrue(OverviewViewerDecisions.canCommit(1, 1, closing = false))
        assertFalse(OverviewViewerDecisions.canCommit(1, 1, closing = true))
        assertFalse(OverviewViewerDecisions.swapInPlace(slidIn = true, closing = true))
        assertNull(OverviewViewerDecisions.slideTarget(1, 4, -100f, 1f, closing = true))
    }

    @Test fun `a newer browse cannot be overwritten by an older swipe`() {
        assertFalse(OverviewViewerDecisions.canCommit(1, 2, closing = false))
        assertTrue(OverviewViewerDecisions.swapInPlace(slidIn = true, closing = false))
        assertFalse(OverviewViewerDecisions.swapInPlace(slidIn = false, closing = false))
    }

    @Test fun `slide threshold is strict scaled and bounded by neighbors`() {
        assertNull(OverviewViewerDecisions.slideTarget(1, 3, -180f, 2f, false))
        assertEquals(2, OverviewViewerDecisions.slideTarget(1, 3, -181f, 2f, false))
        assertEquals(0, OverviewViewerDecisions.slideTarget(1, 3, 181f, 2f, false))
        assertNull(OverviewViewerDecisions.slideTarget(0, 3, 181f, 2f, false))
        assertNull(OverviewViewerDecisions.slideTarget(2, 3, -181f, 2f, false))
        assertNull(OverviewViewerDecisions.slideTarget(0, 0, -181f, 2f, false))
    }

    @Test fun `end swipes rubber band but neighboring items follow the finger`() {
        assertEquals(25f, OverviewViewerDecisions.follow(0, 3, 100f), 0f)
        assertEquals(-25f, OverviewViewerDecisions.follow(2, 3, -100f), 0f)
        assertEquals(-100f, OverviewViewerDecisions.follow(0, 3, -100f), 0f)
    }

    @Test fun `button browse rejects closing unchanged and out of range targets`() {
        assertTrue(OverviewViewerDecisions.canBrowse(2, 1, 3, false))
        assertFalse(OverviewViewerDecisions.canBrowse(2, 1, 3, true))
        assertFalse(OverviewViewerDecisions.canBrowse(1, 1, 3, false))
        assertFalse(OverviewViewerDecisions.canBrowse(-1, 1, 3, false))
        assertFalse(OverviewViewerDecisions.canBrowse(3, 1, 3, false))
    }

    @Test fun `only a downward predominantly vertical pull closes`() {
        assertFalse(OverviewViewerDecisions.closesDrag(0f, 120f))
        assertTrue(OverviewViewerDecisions.closesDrag(20f, 121f))
        assertFalse(OverviewViewerDecisions.closesDrag(150f, 121f))
        assertFalse(OverviewViewerDecisions.closesDrag(0f, -150f))
    }

    @Test fun `held controls cannot hide`() {
        assertFalse(OverviewViewerDecisions.canHide(held = true))
        assertTrue(OverviewViewerDecisions.canHide(held = false))
    }
}
