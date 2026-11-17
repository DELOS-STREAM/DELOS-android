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
            listOf(Glyph.Key(KeyCap.ARROWS), Glyph.Key(KeyCap.RETURN), Glyph.Key(KeyCap.ESCAPE)),
            settings.adaptedTo(InputKind.KEYBOARD).map { it.glyph },
        )
        // No key for the tabs, search (Y) or the menu buttons
        assertEquals(listOf(Hint(Glyph.Key(KeyCap.RETURN), "Start")), home.adaptedTo(InputKind.KEYBOARD))
    }

    @Test fun `the labels and short labels stay`() {
        val hint = Hint(Glyph.A, "Use image", "Choose")
        assertEquals(Hint(Glyph.TAP, "Use image", "Choose"), listOf(hint).adaptedTo(InputKind.TOUCH).single())
    }

    @Test fun `a pill's glyph shows only for the kinds with its button`() {
        assertEquals(Glyph.X, Glyph.X.forKind(InputKind.CONTROLLER))
        assertNull(Glyph.X.forKind(InputKind.KEYBOARD))
        assertEquals(Glyph.TAP, Glyph.A.forKind(InputKind.TOUCH))
        assertEquals(Glyph.Key(KeyCap.ESCAPE), Glyph.B.forKind(InputKind.KEYBOARD))
    }

    @Test fun `the adapted primary action ranks first in a cramped bar`() {
        assertEquals(0, hintRank(Glyph.TAP))
        assertEquals(0, hintRank(Glyph.CLICK))
        assertEquals(0, hintRank(Glyph.Key(KeyCap.RETURN)))
        assertEquals(1, hintRank(Glyph.Key(KeyCap.ESCAPE)))
    }

    @Test fun `the touch-only chrome is for every input but a controller`() {
        for (kind in InputKind.entries) assertEquals(kind != InputKind.CONTROLLER, InputKindRule.WITHOUT_CONTROLLER.shows(kind))
        assertTrue(InputKindRule.WITH_CONTROLLER.shows(InputKind.CONTROLLER))
        assertTrue(!InputKindRule.WITH_CONTROLLER.shows(InputKind.TOUCH))
    }
}
