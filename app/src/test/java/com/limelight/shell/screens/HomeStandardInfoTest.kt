package com.limelight.shell.screens

import com.limelight.shell.ui.Compact
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** The 16:9 home: the logo is centred between the status bar and the info row, and the row keeps its distance from the focused cover's ring. */
class HomeStandardInfoTest {
    private val scales = listOf(1f, 1.05f, 1.1f, 1.15f, 1.2f, 1.3f)

    @Test
    fun theRowKeepsThePrototypesNumbers() {
        assertEquals(430f, HomeStandard.rowTop(1f), 0f)
        assertEquals(430f - .15f * 92f, HomeStandard.rowTop(1.15f), .001f)
    }

    @Test
    fun theLogoIsCentredWithTheLeastAirAboveAndUnder() {
        for (scale in scales) {
            val l = HomeStandard.layout(scale)
            val statusBottom = Compact.STATUS_BAR * scale
            // Centred: the same gap from the status bar to the box and from the box to the row
            assertEquals("scale $scale", l.titleTop - statusBottom, l.titleGap, .01f)
            assertEquals("scale $scale row", HomeStandard.infoTop(scale), l.titleTop + l.titleBox + l.titleGap, .01f)
            // The logo (180 high, 10 px of air in its box) keeps at least 16 from the bar and from the row
            assertTrue("scale $scale logo to the bar ${l.titleGap + HomeStandard.LOGO_PAD}", l.titleGap + HomeStandard.LOGO_PAD >= HomeCompact.GAP_STATUS)
            assertTrue("scale $scale gap ${l.titleGap}", l.titleGap >= HomeCompact.GAP_TITLE - .01f)
        }
    }

    @Test
    fun theLogoKeepsItsFullSizeAtEveryInterfaceScale() {
        for (scale in scales) assertEquals("scale $scale", 200f, HomeStandard.layout(scale).titleBox, 0f)
        assertEquals(180f, HomeStandard.LOGO_HEIGHT, 0f)
    }

    @Test
    fun theInfoRowClearsTheFocusedRing() {
        for (scale in scales) {
            val l = HomeStandard.layout(scale)
            val clearance = HomeStandard.ringTop(scale) - (HomeStandard.infoTop(scale) + l.infoHeight)
            assertTrue("scale $scale clearance $clearance", clearance >= HomeCompact.GAP_CHIPS)
            assertEquals(44f * scale, l.infoHeight, .01f)
        }
    }

    @Test
    fun aCutoutShrinksTheBoxOnlyWhenTheRoomIsUsedUp() {
        val l = HomeStandard.layout(1.3f, safeTop = 40f)
        assertTrue("box ${l.titleBox}", l.titleBox < 200f && l.titleBox > 0f)
        assertEquals(HomeCompact.GAP_STATUS, l.titleGap, .01f)
    }
}
