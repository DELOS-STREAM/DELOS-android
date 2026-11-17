package com.limelight.shell.ui

import androidx.compose.ui.geometry.Rect
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Locale

/** Issue #92: the stepping, typing and holding of the slider popup, and where the popups of a settings row lie. */
class RowPickerLogicTest {
    /** The video bitrate: kbit/s on a 1 Mbit/s grid, read in Mbit/s. */
    private val bitrate = RowValue.Slider(40_000, 1_000, 150_000, 1_000, " Mbps", 1_000) {}
    private val percent = RowValue.Slider(60, 0, 120, 10, " %") {}
    private val gyro = RowValue.Slider(0, 0, 100, 5, " %", minText = "Off") {}

    // ---- stepping ----

    @Test fun stepMovesOneGridStepEitherWay() {
        assertEquals(41_000, SliderEdit.step(bitrate, 40_000, 1))
        assertEquals(39_000, SliderEdit.step(bitrate, 40_000, -1))
        assertEquals(50_000, SliderEdit.step(bitrate, 40_000, 10))
    }

    @Test fun stepClampsAtBothEnds() {
        assertEquals(150_000, SliderEdit.step(bitrate, 149_500, 1))
        assertEquals(150_000, SliderEdit.step(bitrate, 150_000, 50))
        assertEquals(1_000, SliderEdit.step(bitrate, 1_000, -1))
        assertEquals(1_000, SliderEdit.step(bitrate, 3_000, -50))
    }

    @Test fun aStoredValueBetweenStepsSnapsToTheGrid() {
        // 40250 is off the 1000 grid: one step up lands on 41000, not 41250
        assertEquals(41_000, SliderEdit.step(bitrate, 40_250, 1))
        assertEquals(40_000, SliderEdit.step(bitrate, 40_250, 0))
        assertEquals(60, SliderEdit.snap(percent, 64))
        assertEquals(70, SliderEdit.snap(percent, 65))
    }

    @Test fun snapStaysInsideTheRange() {
        assertEquals(120, SliderEdit.snap(percent, 9_999))
        assertEquals(0, SliderEdit.snap(percent, -50))
        assertEquals(Int.MAX_VALUE.coerceAtMost(120), SliderEdit.step(percent, Int.MAX_VALUE, Int.MAX_VALUE))
    }

    // ---- reading ----

    @Test fun numberAndLabelReadInTheRowsUnit() {
        assertEquals("40", SliderEdit.numberText(bitrate, 40_000, Locale.US))
        assertEquals("37.5", SliderEdit.numberText(bitrate, 37_500, Locale.US))
        assertEquals("37,5", SliderEdit.numberText(bitrate, 37_500, Locale.GERMANY))
        assertEquals("40 Mbps", SliderEdit.label(bitrate, 40_000, Locale.US))
        assertEquals("60 %", SliderEdit.label(percent, 60, Locale.US))
    }

    @Test fun theLowestValueOfASliderWithAnOffTextReadsThatText() {
        assertEquals("Off", SliderEdit.label(gyro, 0, Locale.US))
        assertEquals("5 %", SliderEdit.label(gyro, 5, Locale.US))
    }

    // ---- typing ----

    @Test fun typedMbitIsConvertedToKbit() {
        assertEquals(40_000, SliderEdit.parse(bitrate, "40"))
        assertEquals(40_000, SliderEdit.parse(bitrate, " 40 "))
        assertEquals(100_000, SliderEdit.parse(bitrate, "100"))
    }

    @Test fun typedDecimalsRoundToTheGridWithAPointOrAComma() {
        assertEquals(38_000, SliderEdit.parse(bitrate, "37.5"))
        assertEquals(37_000, SliderEdit.parse(bitrate, "37,4"))
        assertEquals(1_000, SliderEdit.parse(bitrate, "0.6"))
    }

    @Test fun typedValuesOutsideTheRangeAreClamped() {
        assertEquals(150_000, SliderEdit.parse(bitrate, "400"))
        assertEquals(150_000, SliderEdit.parse(bitrate, "99999999999999999999"))
        assertEquals(1_000, SliderEdit.parse(bitrate, "0"))
        assertEquals(1_000, SliderEdit.parse(bitrate, "-5"))
        assertEquals(120, SliderEdit.parse(percent, "500"))
    }

    @Test fun aSliderWithoutADivisorTakesTheNumberAsIs() {
        assertEquals(60, SliderEdit.parse(percent, "55"))
        assertEquals(50, SliderEdit.parse(percent, "54"))
        assertEquals(0, SliderEdit.parse(percent, "0"))
    }

    @Test fun textThatIsNoNumberIsRejected() {
        listOf("", "   ", "abc", ".", ",", "1 2", "4o", "Mbps", "--3").forEach {
            assertNull("'$it'", SliderEdit.parse(bitrate, it))
        }
    }

    @Test fun onlyDigitsAndOneSeparatorCanBeTyped() {
        assertTrue(SliderEdit.typable(""))
        assertTrue(SliderEdit.typable("125"))
        assertTrue(SliderEdit.typable("12.5"))
        assertTrue(SliderEdit.typable("12,"))
        assertFalse(SliderEdit.typable("12.5.1"))
        assertFalse(SliderEdit.typable("12,5.1"))
        assertFalse(SliderEdit.typable("1a"))
        assertFalse(SliderEdit.typable("-3"))
        assertFalse(SliderEdit.typable("1 2"))
    }

    // ---- holding ----

    @Test fun aHeldButtonAcceleratesLikeAHeldKey() {
        val wide = RowValue.Slider(5_000, 1_000, 1_000_000, 1_000, " Mbps", 1_000) {}
        assertEquals(1, HoldRepeat.steps(wide, 1))
        assertEquals(1, HoldRepeat.steps(wide, 7))
        assertEquals(5, HoldRepeat.steps(wide, 8))
        assertEquals(10, HoldRepeat.steps(wide, 20))
        // The longest hold is capped at a fortieth of the slider's range
        assertEquals(24, HoldRepeat.steps(wide, 35))
        assertEquals(1, HoldRepeat.steps(percent, 99))
    }

    @Test fun holdingUpReachesTheEndExactlyAndNeverPassesIt() {
        val wide = RowValue.Slider(5_000, 1_000, 1_000_000, 1_000, " Mbps", 1_000) {}
        var value = 5_000
        var repeat = 0
        var presses = 0
        while (value < wide.max && presses < 10_000) {
            val before = value
            value = SliderEdit.step(wide, value, HoldRepeat.steps(wide, ++repeat))
            assertTrue("never goes back", value >= before)
            assertTrue("stays on the grid", (value - wide.min) % wide.step == 0)
            assertTrue("stays in range", value <= wide.max)
            presses++
        }
        assertEquals(wide.max, value)
        // And the same hold downwards ends at the minimum
        repeat = 0
        while (value > wide.min && presses < 20_000) {
            value = SliderEdit.step(wide, value, -HoldRepeat.steps(wide, ++repeat))
            assertTrue(value >= wide.min)
            presses++
        }
        assertEquals(wide.min, value)
    }

    // ---- where the card lies ----

    /** A row of the settings list: x 700..1860, 92 high, its middle at y 346. */
    private val row = Rect(700f, 300f, 1860f, 392f)
    private val w = 1920f
    private val h = 1080f

    private val point = row.right - PickerLayout.VALUE_INSET
    private val d = PickerLayout.CARET_HEIGHT

    @Test fun theCardLiesLeftOfTheRowsValueCentredOnTheRowWithItsCaretInTheRow() {
        val p = PickerLayout.options(row, count = 3, selected = 1, stageW = w, stageH = h)
        assertEquals(PickerLayout.OPTIONS_WIDTH, p.width, 0f)
        assertTrue(p.caretRight)
        assertEquals(point - d, p.x + p.width, .001f)
        assertEquals(point, p.tipX, .001f)
        assertTrue(p.tipX > row.left && p.tipX < row.right)
        assertEquals(PickerLayout.CHROME + PickerLayout.listHeight(3), p.height, 0f)
        assertEquals(row.center.y, p.y + p.height / 2f, .001f)
        // The caret points at the middle of the row
        assertEquals(row.center.y, p.y + p.caretY, .001f)
    }

    @Test fun theCardFlipsToTheRightWhenTheLeftHasNoRoom() {
        // A row near the left edge: only the right side has room for the card
        val near = Rect(40f, 300f, 600f, 392f)
        val p = PickerLayout.options(near, count = 3, selected = 0, stageW = w, stageH = h)
        assertTrue(!p.caretRight)
        assertEquals(near.right - PickerLayout.VALUE_INSET + d, p.x, .001f)
        assertEquals(p.x - d, p.tipX, .001f)
    }

    @Test fun theCaretStaysClearOfTheCornersWhereTheRowIsNearTheCardsEdge() {
        val high = PickerLayout.value(Rect(700f, 100f, 1860f, 192f), w, h)
        assertEquals(PickerLayout.TOP_MARGIN, high.y, 0f)
        assertTrue(high.caretY >= PickerLayout.CORNER + PickerLayout.CARET_WIDTH / 2f)
        assertTrue(high.caretY <= high.height - PickerLayout.CORNER - PickerLayout.CARET_WIDTH / 2f)
    }

    @Test fun theCardStaysBelowTheStatusBarAndAboveTheBottomEdge() {
        val high = PickerLayout.options(Rect(700f, 100f, 1860f, 192f), count = 4, selected = 0, stageW = w, stageH = h)
        assertEquals(PickerLayout.TOP_MARGIN, high.y, 0f)
        val low = PickerLayout.options(Rect(700f, 994f, 1860f, 1086f), count = 4, selected = 3, stageW = w, stageH = h)
        assertEquals(h - PickerLayout.BOTTOM_MARGIN, low.y + low.height, .001f)
    }

    @Test fun aLongListIsCutToTheRoomAndScrolls() {
        val p = PickerLayout.options(row, count = 40, selected = 20, stageW = w, stageH = h)
        val room = h - PickerLayout.TOP_MARGIN - PickerLayout.BOTTOM_MARGIN
        assertEquals(room, p.height, .001f)
        assertEquals(room - PickerLayout.CHROME, p.list, .001f)
        assertEquals(PickerLayout.TOP_MARGIN, p.y, 0f)
        assertTrue(p.list < PickerLayout.listHeight(40))
    }

    @Test fun theCardKeepsItsDistanceFromTheSidesAndIsNarrowedToTheRoom() {
        val p = PickerLayout.value(row, w, h)
        assertTrue(p.x >= PickerLayout.SIDE_MARGIN && p.x + p.width <= w - PickerLayout.SIDE_MARGIN)
        val narrow = PickerLayout.options(Rect(0f, 300f, 390f, 392f), 3, 0, stageW = 400f, stageH = h)
        assertTrue(narrow.x >= PickerLayout.SIDE_MARGIN && narrow.x + narrow.width <= 400f - PickerLayout.SIDE_MARGIN)
        assertTrue(narrow.width < PickerLayout.OPTIONS_WIDTH)
    }

    @Test fun withoutARowTheCardIsCentredWithoutACaret() {
        val p = PickerLayout.options(null, count = 3, selected = 0, stageW = w, stageH = h)
        assertEquals((w - p.width) / 2f, p.x, .001f)
        assertEquals((h - p.height) / 2f, p.y, .001f)
        assertTrue(!p.hasCaret)
    }

    @Test fun aCompactStageKeepsItsOwnMargins() {
        val p = PickerLayout.options(Rect(100f, 20f, 800f, 100f), count = 3, selected = 0, stageW = 900f, stageH = 600f, top = 60f, bottom = 120f)
        assertEquals(60f, p.y, 0f)
        val low = PickerLayout.options(Rect(100f, 540f, 800f, 600f), count = 3, selected = 2, stageW = 900f, stageH = 600f, top = 60f, bottom = 120f)
        assertEquals(480f, low.y + low.height, .001f)
    }

    @Test fun theSliderPopupIsWideAndItsControlsAreForAFinger() {
        val p = PickerLayout.value(row, w, h)
        assertEquals(PickerLayout.VALUE_HEIGHT, p.height, 0f)
        assertEquals(PickerLayout.VALUE_WIDTH, p.width, 0f)
        assertTrue(PickerLayout.VALUE_WIDTH >= 2f * 520f)
        // 56 dp is 72 stage pixels
        assertTrue(PickerLayout.VALUE_BUTTON >= 72f && PickerLayout.VALUE_FIELD >= 72f && PickerLayout.VALUE_CONTROLS >= 72f)
        assertEquals(row.center.y, p.y + p.height / 2f, .001f)
        // At the bottom it is pushed up, never off the screen
        val low = PickerLayout.value(Rect(700f, 980f, 1860f, 1072f), w, h)
        assertEquals(h - PickerLayout.BOTTOM_MARGIN, low.y + low.height, .001f)
    }
}
