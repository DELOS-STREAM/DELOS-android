package com.limelight.shell.ui

import org.junit.Assert.assertEquals
import org.junit.Test

/** The there-and-back scroll of a row that does not fit (`MarqueeContent(bounce = true)`): still, forward, pause at the end, back, pause at the start. */
class MarqueeMotionTest {
    // 280 px at 56 px/s: a leg takes 5 s
    private val travel = 280f
    private val speed = 56f
    private fun at(ms: Long) = MarqueeMotion.bounceOffset(ms, travel, speed)
    private val start = MarqueeMotion.START_DELAY_MS
    private val pause = MarqueeMotion.LOOP_DELAY_MS

    @Test fun stillAtFirst() {
        assertEquals(0f, at(0), 0f)
        assertEquals(0f, at(start - 1), 0f)
        assertEquals(0f, at(start), 0f)
    }

    @Test fun forwardAtTheSpeed() {
        assertEquals(56f, at(start + 1000), .01f)
        assertEquals(140f, at(start + 2500), .01f)
        assertEquals(280f, at(start + 5000), .01f)
    }

    @Test fun pausesAtTheEnd() {
        assertEquals(280f, at(start + 5000 + pause / 2), 0f)
        assertEquals(280f, at(start + 5000 + pause - 1), 0f)
    }

    @Test fun backAtTheSameSpeed() {
        assertEquals(280f - 56f, at(start + 5000 + pause + 1000), .01f)
        assertEquals(0f, at(start + 10_000 + pause), .01f)
    }

    @Test fun pausesAtTheStartThenGoesAgain() {
        assertEquals(0f, at(start + 10_000 + pause + pause / 2), 0f)
        // The next lap starts after one pause at the start, not the longer first delay
        assertEquals(56f, at(start + 10_000 + 2 * pause + 1000), .01f)
    }

    @Test fun noRoomToTravelMeansNoMotion() {
        assertEquals(0f, MarqueeMotion.bounceOffset(5000, 0f, speed), 0f)
        assertEquals(0f, MarqueeMotion.bounceOffset(5000, travel, 0f), 0f)
    }
}
