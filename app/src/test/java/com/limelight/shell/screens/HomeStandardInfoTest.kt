package com.limelight.shell.screens

import com.limelight.shell.ui.Compact
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** The 16:9 home keeps the prototype's numbers without streaming time and makes room for the stat block with it, never closer to the covers than the compact minimum. */
class HomeStandardInfoTest {
    private val scales = listOf(1f, 1.05f, 1.1f, 1.15f, 1.2f, 1.3f)

    @Test
    fun withoutTimeTheLayoutIsThePrototypes() {
        for (scale in scales) {
            val l = HomeStandard.layout(scale, withBlock = false)
            assertEquals(150f, l.top, 0f)
            assertEquals(200f, l.titleBox, 0f)
        }
        assertEquals(430f, HomeStandard.rowTop(1f), 0f)
        assertEquals(430f - .15f * 92f, HomeStandard.rowTop(1.15f), .001f)
    }

    @Test
    fun withTimeThePillsStillClearTheFocusedRing() {
        for (scale in scales) {
            val l = HomeStandard.layout(scale, withBlock = true)
            val clearance = HomeStandard.ringTop(scale) - HomeStandard.chipsBottom(scale, l.top, l.titleBox, withBlock = true)
            assertTrue("scale $scale clearance $clearance", clearance >= HomeCompact.GAP_CHIPS - .01f)
            // The logo keeps the compact stages' air under the status bar, and the title box never goes under its minimum
            assertTrue("scale $scale logo top ${l.top + HomeStandard.LOGO_PAD}", l.top + HomeStandard.LOGO_PAD >= Compact.STATUS_BAR * scale + HomeCompact.GAP_STATUS - .01f)
            assertTrue("scale $scale title box ${l.titleBox}", l.titleBox >= HomeStandard.MIN_TITLE_BOX && l.titleBox <= HomeStandard.TITLE_BOX)
            assertTrue("scale $scale moved up", l.top <= HomeStandard.INFO_TOP)
        }
    }

    @Test
    fun theBlockHasClearAirAboveAndBelow() {
        assertTrue(HomeStandard.TITLE_BLOCK_GAP >= 10f && HomeStandard.TITLE_BLOCK_GAP <= 12f)
        assertTrue(HomeCompact.BLOCK_GAP >= 28f)
        for (scale in scales) {
            val l = HomeStandard.layout(scale, withBlock = true)
            // The logo's own air and the gap: at least 10 between the logo and the eyebrow
            assertTrue("scale $scale", l.logoPad + HomeStandard.TITLE_BLOCK_GAP >= 10f)
            // The logo keeps its 180 px until the room runs out
            if (scale <= 1f) assertEquals(180f, l.titleBox - 2f * l.logoPad, .01f)
        }
    }

    @Test
    fun atScaleOneTheInfoBlockMovesUpALittleAndKeepsItsLogo() {
        val l = HomeStandard.layout(1f, withBlock = true)
        assertEquals(188f, l.titleBox, 0f)
        assertTrue("top ${l.top}", l.top in 130f..150f)
    }

    @Test
    fun theRoomOfALargeInterfaceScaleComesFromTheTitleBoxToo() {
        val l = HomeStandard.layout(1.15f, withBlock = true)
        assertTrue("moved up as far as the status bar allows: ${l.top}", l.top < 135f)
        assertTrue("and the title box gave some: ${l.titleBox}", l.titleBox < 188f && l.titleBox > 150f)
        val big = HomeStandard.layout(1.3f, withBlock = true)
        assertTrue("the largest scale: ${big.titleBox}", big.titleBox >= HomeStandard.MIN_TITLE_BOX)
    }
}
