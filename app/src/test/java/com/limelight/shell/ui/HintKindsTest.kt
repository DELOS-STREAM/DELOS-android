package com.limelight.shell.ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** DELOS for Apple's `ShellHintsTests` (adapt) on Android's hints. */
class HintKindsTest {
    private val home = listOf(Hint(Glyph.A, "Start"), Hint(Glyph.Y, "Filter"), Hint(Glyph.MENU_LEFT, "Menu"), Hint(Glyph.MENU_RIGHT, "Options"))
    private val settings = listOf(
        Hint(Glyph.ADJUST, "Adjust"), Hint(Glyph.L1, "Previous tab"), Hint(Glyph.R1, "Next tab"), Hint(Glyph.Y, "Search"),
        Hint(Glyph.A, "Change"), Hint(Glyph.B, "Back"),
    )

    @Test fun `a controller keeps every hint`() {
        assertEquals(home, home.adaptedTo(InputKind.CONTROLLER))
        assertEquals(settings, settings.adaptedTo(InputKind.CONTROLLER))
    }

    @Test fun `touch and the pointer keep only the primary action`() {
        assertEquals(listOf(Hint(Glyph.TAP, "Start")), home.adaptedTo(InputKind.TOUCH))
        assertEquals(listOf(Hint(Glyph.CLICK, "Start")), home.adaptedTo(InputKind.POINTER))
        assertEquals(listOf(Hint(Glyph.TAP, "Change")), settings.adaptedTo(InputKind.TOUCH))
        // A screen with only B (the PIN): nothing to show, Cancel is on the screen
        assertTrue(listOf(Hint(Glyph.B, "Cancel")).adaptedTo(InputKind.TOUCH).isEmpty())
    }

    @Test fun `the keyboard has Enter, Esc and the arrows`() {
        assertEquals(
            listOf(Glyph.Cap(InputCap.ARROWS), Glyph.Cap(InputCap.RETURN), Glyph.Cap(InputCap.ESCAPE)),
            settings.adaptedTo(InputKind.KEYBOARD).map { it.glyph },
        )
        // No key for the tabs, search (Y) or the menu buttons
        assertEquals(listOf(Hint(Glyph.Cap(InputCap.RETURN), "Start")), home.adaptedTo(InputKind.KEYBOARD))
    }

    @Test fun `the labels and short labels stay`() {
        val hint = Hint(Glyph.A, "Use image", "Choose")
        assertEquals(Hint(Glyph.TAP, "Use image", "Choose"), listOf(hint).adaptedTo(InputKind.TOUCH).single())
    }

    @Test fun `the library's Filter stays a tappable hint for every kind`() {
        val filter = Hint(Glyph.Y, "Filter • 2 active", tappable = true)
        val library = listOf(Hint(Glyph.A, "Start"), filter, Hint(Glyph.MENU_LEFT, "Menu"))
        assertEquals(listOf(Hint(Glyph.TAP, "Start"), filter), library.adaptedTo(InputKind.TOUCH))
        assertEquals(listOf(Hint(Glyph.CLICK, "Start"), filter), library.adaptedTo(InputKind.POINTER))
        assertEquals(listOf(Hint(Glyph.Cap(InputCap.RETURN), "Start"), filter), library.adaptedTo(InputKind.KEYBOARD))
        assertEquals(library, library.adaptedTo(InputKind.CONTROLLER))
    }

    @Test fun `a pill's glyph shows only for the kinds with its button`() {
        assertEquals(Glyph.X, Glyph.X.forKind(InputKind.CONTROLLER))
        assertNull(Glyph.X.forKind(InputKind.KEYBOARD))
        assertEquals(Glyph.TAP, Glyph.A.forKind(InputKind.TOUCH))
        assertEquals(Glyph.Cap(InputCap.ESCAPE), Glyph.B.forKind(InputKind.KEYBOARD))
    }

    @Test fun `a hint written for the keyboard keeps its key`() {
        val filter = listOf(Hint(Glyph.Cap(InputCap.SPACE), "Select"), Hint(Glyph.Cap(InputCap.RETURN), "Apply"), Hint(Glyph.B, "Close"))
        assertEquals(listOf(Glyph.Cap(InputCap.SPACE), Glyph.Cap(InputCap.RETURN), Glyph.Cap(InputCap.ESCAPE)), filter.adaptedTo(InputKind.KEYBOARD).map { it.glyph })
    }

    @Test fun `the adapted primary action ranks first in a cramped bar`() {
        assertEquals(0, hintRank(Glyph.TAP))
        assertEquals(0, hintRank(Glyph.CLICK))
        assertEquals(0, hintRank(Glyph.Cap(InputCap.RETURN)))
        assertEquals(1, hintRank(Glyph.Cap(InputCap.ESCAPE)))
    }

    @Test fun `the touch-only chrome is for every input but a controller`() {
        for (kind in InputKind.entries) assertEquals(kind != InputKind.CONTROLLER, InputKindRule.WITHOUT_CONTROLLER.shows(kind))
        assertTrue(InputKindRule.WITH_CONTROLLER.shows(InputKind.CONTROLLER))
        assertTrue(!InputKindRule.WITH_CONTROLLER.shows(InputKind.TOUCH))
    }
}
