package com.limelight.shell.screens

import org.junit.Assert.assertEquals
import org.junit.Test

/** The stat block never shrinks: it gives up the last-streamed column, then the long unit, then scrolls. */
class PlaytimeFitTest {
    private fun pick(available: Int) = PlaytimeFit.pick(full = 500, statOnly = 300, short = 200, available = available)

    @Test fun fullWhileEverythingFits() {
        assertEquals(PlaytimeFit.FULL, pick(900))
        assertEquals(PlaytimeFit.FULL, pick(500))
    }

    @Test fun dropsTheLastStreamedColumnFirst() {
        assertEquals(PlaytimeFit.STAT_ONLY, pick(499))
        assertEquals(PlaytimeFit.STAT_ONLY, pick(300))
    }

    @Test fun thenTheShortUnit() {
        assertEquals(PlaytimeFit.SHORT, pick(299))
        assertEquals(PlaytimeFit.SHORT, pick(200))
    }

    @Test fun thenItScrolls() {
        assertEquals(PlaytimeFit.SCROLL, pick(199))
        assertEquals(PlaytimeFit.SCROLL, pick(0))
    }

    @Test fun anAlreadyShortFormIsNotShortened() {
        // Without a last-streamed value the full form is as wide as the stat alone
        assertEquals(PlaytimeFit.FULL, PlaytimeFit.pick(full = 300, statOnly = 300, short = 200, available = 300))
    }
}
