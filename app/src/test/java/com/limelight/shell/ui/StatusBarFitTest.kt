package com.limelight.shell.ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The compact status bar (`fitStatusBar`): the navigation keeps 24 px to the brand cluster and to the status cluster on every compact
 * stage width. The bar is zoomed by the interface scale (1.15 by default, 1 to 1.3), so a stage 1030 to 1500 px wide is a bar of 792 to
 * 1500 bar pixels. The widths are the ones the real bar measures (bar pixels, logged on the emulator): the logo 40, the wordmark 175, the
 * PC's dot pill 69, 18 between them; Wi-Fi 30, the battery 48, "100%" 58.3 and the widest clock there is, a 12-hour "12:59 PM", 104
 * (the charging bolt sits inside the battery glyph and adds no width).
 */
class StatusBarFitTest {
    private val c = StatusBarLayout

    private fun statusWidth(step: StatusBarStep, clock: Float = 104f, percent: Float = 58.3f): Float {
        val gap = if (step.tight) c.STATUS_GAP_TIGHT else c.STATUS_GAP
        val battery = 48f + if (step.percent) (if (step.tight) c.BATTERY_GAP_TIGHT else c.BATTERY_GAP) + percent else 0f
        return 30f + gap + battery + gap + clock
    }

    private fun brandWidth(barWidth: Float, pc: Boolean = true): Float =
        40f + (if (barWidth >= Compact.NARROW_STAGE) 18f + 175f else 0f) + if (pc) 18f + 69f else 0f

    /** The navigation pill keeps its focus-rule gaps in every step: only the status cluster tightens. */
    private val navWidth = c.navWidth(3, c.NAV_BUTTON, c.NAV_COMPACT.gap)

    private fun fit(width: Float, nav: Boolean = true, brand: Boolean = true, safeLeft: Float = 0f, safeRight: Float = 0f): StatusBarFit =
        fitStatusBar(width, c.SIDE + safeLeft, c.SIDE + safeRight, c.MIN_GAP) { step ->
            StatusBarWidths(if (brand) brandWidth(width) else 0f, if (nav) navWidth else 0f, statusWidth(step))
        }

    /** The free space between the clusters at [step], the gaps taken off. */
    private fun room(width: Float, step: StatusBarStep, safeLeft: Float = 0f, safeRight: Float = 0f): Float =
        (width - c.SIDE - safeRight - statusWidth(step) - c.MIN_GAP) - (c.SIDE + safeLeft + brandWidth(width) + c.MIN_GAP)

    private fun assertAir(name: String, width: Float, fit: StatusBarFit, safeLeft: Float = 0f, safeRight: Float = 0f) {
        val leftEnd = c.SIDE + safeLeft + brandWidth(width)
        val rightStart = width - c.SIDE - safeRight - statusWidth(fit.step)
        val nav = navWidth
        assertTrue("$name fits", fit.fits)
        assertTrue("$name left gap ${fit.navX - leftEnd}", fit.navX - leftEnd >= c.MIN_GAP - .01f)
        assertTrue("$name right gap ${rightStart - fit.navX - nav}", rightStart - (fit.navX + nav) >= c.MIN_GAP - .01f)
        // Nothing is given up before it has to be: every earlier step is too wide
        for (earlier in StatusBarStep.entries.takeWhile { it != fit.step }) assertTrue("$name ${earlier.name} would not fit", room(width, earlier, safeLeft, safeRight) < navWidth)
    }

    @Test
    fun theNavigationIsAPillOfThree56ButtonsThatKeepTheFocusRule() {
        // A focused button grows to 1.12 without a ring: 3.36 px to each side of a 56 px one (3.84 of a 64 px one) and 8 px of clear space
        assertEquals(c.NAV_BUTTON, c.NAV_COMPACT.size, 0f)
        assertEquals(14.72f, c.NAV_COMPACT.minGap, .001f)
        assertEquals(15.68f, c.NAV.minGap, .001f)
        assertTrue(c.NAV_COMPACT.gap >= c.NAV_COMPACT.minGap)
        assertTrue(c.NAV.gap >= c.NAV.minGap)
        assertEquals(214f, c.navWidth(3, c.NAV_BUTTON, c.NAV_COMPACT.gap), 0f)
        assertEquals(0f, c.navWidth(0, c.NAV_BUTTON, c.NAV_COMPACT.gap), 0f)
        // The 16:9 pill: three 64 px buttons
        assertEquals(240f, c.navWidth(3, c.NAV.size, c.NAV.gap), 0f)
    }

    @Test
    fun everyCompactWidthKeeps24ToBothClustersWithNothingDroppedAtTheDefaultScale() {
        for (scale in listOf(1f, 1.15f)) {
            var stage = 1030f
            while (stage <= 1500f) {
                val fit = fit(stage / scale)
                assertAir("stage $stage at $scale", stage / scale, fit)
                assertEquals("stage $stage at $scale gives nothing up", StatusBarStep.FULL, fit.step)
                stage += 1f
            }
        }
    }

    @Test
    fun theSixCompactStagesOfTheHandhelds() {
        for (stage in listOf(1099f, 1488f, 1191f, 1108f, 1051f, 1082f)) for (scale in listOf(1f, 1.1f, 1.15f, 1.2f, 1.3f)) {
            assertAir("stage $stage at $scale", stage / scale, fit(stage / scale))
        }
    }

    @Test
    fun centredWhileThatLeavesTheGapOtherwiseInTheMiddleOfTheFreeSpace() {
        // 1488 at 1.15 is a 1294 px bar: the brand with its wordmark ends at 392, the status cluster starts at 916: the middle is free
        val wide = fit(1488f / 1.15f)
        assertTrue(wide.centred)
        assertEquals((1488f / 1.15f - 214f) / 2f, wide.navX, .01f)
        // 1051 at 1.15 is a 914 px bar: the status cluster starts at 536, the centred pill would end at 564: it moves left, into the
        // middle of the free space, with the same air on both sides
        val bar = 1051f / 1.15f
        val narrow = fit(bar)
        assertFalse(narrow.centred)
        assertEquals(StatusBarStep.FULL, narrow.step)
        val leftEnd = c.SIDE + brandWidth(bar)
        val rightStart = bar - c.SIDE - statusWidth(StatusBarStep.FULL)
        assertEquals((leftEnd + rightStart - 214f) / 2f, narrow.navX, .01f)
        assertEquals(narrow.navX - leftEnd, rightStart - (narrow.navX + 214f), .01f)
    }

    @Test
    fun theLargestInterfaceScaleTightensThenDropsThePercentage() {
        // At 1.3 a 1082 stage is an 832 px bar: the full bar does not leave 214 + 2 x 24, the tighter status gaps do
        assertEquals(StatusBarStep.TIGHT, fit(1082f / 1.3f).step)
        // A 1051 stage at 1.3 (808 px) and the narrowest compact stage (792 px): the percentage goes too
        assertEquals(StatusBarStep.NO_PERCENT, fit(1051f / 1.3f).step)
        assertEquals(StatusBarStep.NO_PERCENT, fit(1030f / 1.3f).step)
        var stage = 1030f
        while (stage <= 1500f) {
            assertAir("stage $stage at 1.3", stage / 1.3f, fit(stage / 1.3f))
            stage += 1f
        }
    }

    @Test
    fun theStepsGoInOrderAndTheLastResortSaysSo() {
        assertEquals(listOf(StatusBarStep.FULL, StatusBarStep.TIGHT, StatusBarStep.NO_PERCENT), StatusBarStep.entries.toList())
        // Nothing helps on a bar this narrow: the last step, in the middle of what is left, flagged
        val fit = fit(560f)
        assertFalse(fit.fits)
        assertEquals(StatusBarStep.NO_PERCENT, fit.step)
        val leftEnd = c.SIDE + brandWidth(560f)
        val rightStart = 560f - c.SIDE - statusWidth(StatusBarStep.NO_PERCENT)
        assertEquals(leftEnd + (rightStart - leftEnd - 214f) / 2f, fit.navX, .01f)
    }

    @Test
    fun aCutoutShiftsTheMiddleAndIsRespected() {
        val fit = fit(1099f, safeLeft = 96f)
        assertAir("1099 with a 96 cutout", 1099f, fit, safeLeft = 96f)
    }

    @Test
    fun withoutNavigationOnlyTheClustersNeedTheirGap() {
        // Settings sub-screens: brand and status only
        val fit = fit(1051f, nav = false)
        assertTrue(fit.fits)
        assertEquals(StatusBarStep.FULL, fit.step)
        // The start screen: no brand, no navigation
        assertTrue(fit(1051f, nav = false, brand = false).fits)
    }

    @Test
    fun placeBetweenPrefersTheCentre() {
        assertEquals(400f, placeBetween(100f, 900f, 200f, 500f)!!, 0f)
        // The centre would cross the end: the middle of the free space
        assertEquals(100f + (500f - 100f - 200f) / 2f, placeBetween(100f, 500f, 200f, 450f)!!, 0f)
        assertNull(placeBetween(100f, 250f, 200f, 175f))
        assertEquals(100f, placeBetween(100f, 300f, 200f, 200f)!!, 0f)
    }

    /** The back button of the bar (Apple's `BackButton`): for fingers, pointers and keyboards, hidden while a controller is the input. */
    @Test
    fun theBackButtonIsForEveryInputButAController() {
        assertFalse(StatusBarLayout.BACK_RULE.shows(InputKind.CONTROLLER))
        assertTrue(StatusBarLayout.BACK_RULE.shows(InputKind.TOUCH))
        assertTrue(StatusBarLayout.BACK_RULE.shows(InputKind.POINTER))
        assertTrue(StatusBarLayout.BACK_RULE.shows(InputKind.KEYBOARD))
        assertEquals(68f, StatusBarLayout.BACK_SIZE, 0f)
        assertEquals(24f, StatusBarLayout.BACK_GAP, 0f)
    }
}
