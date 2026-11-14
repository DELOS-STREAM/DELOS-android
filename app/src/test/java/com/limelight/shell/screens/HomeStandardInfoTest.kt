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
    fun theBlockCostsTheCoverClearanceNothingAtScaleOne() {
        // At scale 1 the block (44) and its 16 px of air replace the label's 10 px of padding: 50 px, of the 72 px of clearance
        val without = HomeStandard.ringTop(1f) - HomeStandard.chipsBottom(1f, 150f, 200f, withBlock = false)
        val with = HomeStandard.ringTop(1f) - HomeStandard.chipsBottom(1f, 150f, 200f, withBlock = true)
        assertEquals(50f, without - with, .01f)
        assertTrue("clearance $without without the block", without > 70f)
        // Only about 2 px move up to keep the compact minimum
        assertEquals(148f, HomeStandard.layout(1f, withBlock = true).top, 1f)
    }

    @Test
    fun theRoomOfALargeInterfaceScaleComesFromTheTitleBoxToo() {
        val l = HomeStandard.layout(1.15f, withBlock = true)
        assertTrue("moved up as far as the status bar allows: ${l.top}", l.top < 130f)
        assertTrue("and the title box gave a little: ${l.titleBox}", l.titleBox < 200f && l.titleBox > 185f)
    }
}
