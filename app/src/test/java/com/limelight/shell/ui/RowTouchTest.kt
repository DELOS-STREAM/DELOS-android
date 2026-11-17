package com.limelight.shell.ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** Issue #92: what a touch tap does on each kind of settings row, and which popup a row opens. */
class RowTouchTest {
    private val toggle = RowValue.Toggle(false) {}
    private val slider = RowValue.Slider(60, 0, 120, 10, " fps") {}
    private val stepOnly = RowValue.Choice("Dark", {})
    private var picked = -1
    private val listed = RowValue.Choice("60 FPS", {}, options = listOf("30 FPS", "60 FPS", "120 FPS"), selected = 1, onPick = { picked = it })
    private val action = RowValue.Action {}
    private val text = RowValue.Text("") {}
    private val info = RowValue.Info("1.1.0")

    @Test fun firstTapOnlyFocusesSoTheHelpShowsFirst() {
        listOf(toggle, slider, stepOnly, listed).forEach {
            assertEquals(it.toString(), RowTouch.FOCUS_ONLY, rowTouch(it, wasFocused = false, enabled = true, canPopup = true))
        }
    }

    @Test fun tapOnTheFocusedRowChangesIt() {
        assertEquals(RowTouch.ACTIVATE, rowTouch(toggle, wasFocused = true, enabled = true, canPopup = true))
        assertEquals(RowTouch.PICK, rowTouch(listed, wasFocused = true, enabled = true, canPopup = true))
    }

    @Test fun aChoiceWithoutItsOptionsStillSteps() {
        assertEquals(RowTouch.ACTIVATE, rowTouch(stepOnly, wasFocused = true, enabled = true, canPopup = false))
        assertEquals(RowTouch.ACTIVATE, rowTouch(listed, wasFocused = true, enabled = true, canPopup = false))
    }

    @Test fun aFocusedSliderRowOpensItsPopup() {
        assertEquals(RowTouch.ADJUST, rowTouch(slider, wasFocused = true, enabled = true, canPopup = true))
    }

    @Test fun aSliderWithoutAShellPopupKeepsItsTrackOnly() {
        assertEquals(RowTouch.NONE, rowTouch(slider, wasFocused = true, enabled = true, canPopup = false))
    }

    @Test fun rowsThatOpenSomethingOpenOnTheFirstTap() {
        assertEquals(RowTouch.ACTIVATE, rowTouch(action, wasFocused = false, enabled = true, canPopup = true))
        assertEquals(RowTouch.ACTIVATE, rowTouch(text, wasFocused = false, enabled = true, canPopup = true))
        assertEquals(RowTouch.NONE, rowTouch(info, wasFocused = true, enabled = true, canPopup = true))
    }

    @Test fun aDisabledRowFocusesFirstSoItsHelpCanSayWhy() {
        assertEquals(RowTouch.FOCUS_ONLY, rowTouch(toggle, wasFocused = false, enabled = false, canPopup = true))
        assertEquals(RowTouch.ERROR, rowTouch(toggle, wasFocused = true, enabled = false, canPopup = true))
        assertEquals(RowTouch.ERROR, rowTouch(slider, wasFocused = true, enabled = false, canPopup = true))
        assertEquals(RowTouch.ERROR, rowTouch(listed, wasFocused = true, enabled = false, canPopup = true))
        assertEquals(RowTouch.ERROR, rowTouch(action, wasFocused = false, enabled = false, canPopup = true))
    }

    @Test fun aChoiceOpensItsOptionsWithTheCurrentOneSelected() {
        val popup = rowPopupFor("Frame rate", listed) as RowPopup.Options
        assertEquals("Frame rate", popup.title)
        assertEquals(listOf("30 FPS", "60 FPS", "120 FPS"), popup.options)
        assertEquals(1, popup.selected)
        popup.onPick(2)
        assertEquals(2, picked)
    }

    @Test fun aSliderOpensItsPopup() {
        val popup = rowPopupFor("Bitrate", slider) as RowPopup.Value
        assertEquals("Bitrate", popup.title)
        assertEquals(slider, popup.slider)
    }

    @Test fun otherRowsHaveNoPopup() {
        assertNull(rowPopupFor("Theme", stepOnly))
        assertNull(rowPopupFor("Empty", listed.copy(options = emptyList())))
        assertNull(rowPopupFor("Picker", listed.copy(onPick = null)))
        listOf(toggle, action, text, info).forEach { assertNull(it.toString(), rowPopupFor("Row", it)) }
        assertTrue(rowPopupFor("Slider", slider) != null)
    }
}
