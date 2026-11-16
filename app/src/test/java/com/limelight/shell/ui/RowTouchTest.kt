package com.limelight.shell.ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/** Issue #92: what a touch tap does on each kind of settings row. */
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
            assertEquals(it.toString(), RowTouch.FOCUS_ONLY, rowTouch(it, wasFocused = false, enabled = true, canPick = true))
        }
    }

    @Test fun tapOnTheFocusedRowChangesIt() {
        assertEquals(RowTouch.ACTIVATE, rowTouch(toggle, wasFocused = true, enabled = true, canPick = true))
        assertEquals(RowTouch.ACTIVATE, rowTouch(stepOnly, wasFocused = true, enabled = true, canPick = true))
        assertEquals(RowTouch.PICK, rowTouch(listed, wasFocused = true, enabled = true, canPick = true))
    }

    @Test fun aChoiceWithoutAPickerStillSteps() {
        assertEquals(RowTouch.ACTIVATE, rowTouch(listed, wasFocused = true, enabled = true, canPick = false))
    }

    @Test fun aSliderIsSetOnItsTrackNotByTappingTheRow() {
        assertEquals(RowTouch.NONE, rowTouch(slider, wasFocused = true, enabled = true, canPick = true))
    }

    @Test fun rowsThatOpenSomethingOpenOnTheFirstTap() {
        assertEquals(RowTouch.ACTIVATE, rowTouch(action, wasFocused = false, enabled = true, canPick = true))
        assertEquals(RowTouch.ACTIVATE, rowTouch(text, wasFocused = false, enabled = true, canPick = true))
        assertEquals(RowTouch.NONE, rowTouch(info, wasFocused = true, enabled = true, canPick = true))
    }

    @Test fun aDisabledRowFocusesFirstSoItsHelpCanSayWhy() {
        assertEquals(RowTouch.FOCUS_ONLY, rowTouch(toggle, wasFocused = false, enabled = false, canPick = true))
        assertEquals(RowTouch.ERROR, rowTouch(toggle, wasFocused = true, enabled = false, canPick = true))
        assertEquals(RowTouch.ERROR, rowTouch(action, wasFocused = false, enabled = false, canPick = true))
    }

    @Test fun thePickerListsEveryOptionWithTheCurrentOneChecked() {
        val menu = choicePickerMenu("Frame rate", listed)!!
        assertEquals("Frame rate", menu.title)
        assertEquals(listOf("30 FPS", "60 FPS", "120 FPS"), menu.items.map { it.label })
        assertEquals(listOf("", Symbols.CHECK, ""), menu.items.map { it.icon })
        menu.items[2].action()
        assertEquals(2, picked)
    }

    @Test fun noPickerWithoutOptions() {
        assertNull(choicePickerMenu("Theme", stepOnly))
        assertNull(choicePickerMenu("Empty", listed.copy(options = emptyList())))
    }
}
