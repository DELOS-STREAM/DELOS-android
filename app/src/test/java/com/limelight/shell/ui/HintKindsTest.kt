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

    @Test fun `touch keeps only the primary action, the pointer also the right click`() {
        assertEquals(listOf(Hint(Glyph.TAP, "Start")), home.adaptedTo(InputKind.TOUCH))
        assertEquals(listOf(Hint(Glyph.CLICK, "Start"), Hint(Glyph.SECONDARY_CLICK, "Options")), home.adaptedTo(InputKind.POINTER))
        assertEquals(listOf(Hint(Glyph.TAP, "Change")), settings.adaptedTo(InputKind.TOUCH))
        assertEquals(listOf(Hint(Glyph.CLICK, "Change")), settings.adaptedTo(InputKind.POINTER))
        // A screen with only B (the PIN): nothing to show, Cancel is on the screen
        assertTrue(listOf(Hint(Glyph.B, "Cancel")).adaptedTo(InputKind.TOUCH).isEmpty())
    }

    @Test fun `the keyboard shows every button as its key`() {
        assertEquals(
            listOf(InputCap.ARROWS, InputCap.LEFT_BRACKET, InputCap.RIGHT_BRACKET, InputCap.LETTER_F, InputCap.RETURN, InputCap.ESCAPE).map(Glyph::Cap),
            settings.adaptedTo(InputKind.KEYBOARD).map { it.glyph },
        )
        assertEquals(
            listOf(InputCap.RETURN, InputCap.LETTER_F, InputCap.TAB, InputCap.LETTER_O).map(Glyph::Cap),
            home.adaptedTo(InputKind.KEYBOARD).map { it.glyph },
        )
    }

    @Test fun `X and the right menu button are both O, shown once`() {
        val game = listOf(Hint(Glyph.A, "Change"), Hint(Glyph.X, "Reset"), Hint(Glyph.MENU_RIGHT, "Options"), Hint(Glyph.B, "Back"))
        assertEquals(
            listOf(Hint(Glyph.Cap(InputCap.RETURN), "Change"), Hint(Glyph.Cap(InputCap.LETTER_O), "Reset"), Hint(Glyph.Cap(InputCap.ESCAPE), "Back")),
            game.adaptedTo(InputKind.KEYBOARD),
        )
    }

    @Test fun `the labels and short labels stay`() {
        val hint = Hint(Glyph.A, "Use image", "Choose")
        assertEquals(Hint(Glyph.TAP, "Use image", "Choose"), listOf(hint).adaptedTo(InputKind.TOUCH).single())
    }

    @Test fun `the library's Filter is a pill for touch and the pointer, its key for the keyboard`() {
        val filter = Hint(Glyph.Y, "Filter • 2 active", tappable = true)
        val library = listOf(Hint(Glyph.A, "Start"), filter, Hint(Glyph.MENU_LEFT, "Menu"))
        assertEquals(listOf(Hint(Glyph.TAP, "Start"), filter), library.adaptedTo(InputKind.TOUCH))
        assertEquals(listOf(Hint(Glyph.CLICK, "Start"), filter), library.adaptedTo(InputKind.POINTER))
        assertEquals(
            listOf(Hint(Glyph.Cap(InputCap.RETURN), "Start"), Hint(Glyph.Cap(InputCap.LETTER_F), "Filter • 2 active"), Hint(Glyph.Cap(InputCap.TAB), "Menu")),
            library.adaptedTo(InputKind.KEYBOARD),
        )
        assertEquals(library, library.adaptedTo(InputKind.CONTROLLER))
    }

    @Test fun `a pill's glyph shows only for the kinds with its button`() {
        assertEquals(Glyph.X, Glyph.X.forKind(InputKind.CONTROLLER))
        assertEquals(Glyph.Cap(InputCap.LETTER_O), Glyph.X.forKind(InputKind.KEYBOARD))
        assertNull(Glyph.X.forKind(InputKind.POINTER))
        assertNull(Glyph.X.forKind(InputKind.TOUCH))
        assertEquals(Glyph.TAP, Glyph.A.forKind(InputKind.TOUCH))
        assertEquals(Glyph.Cap(InputCap.ESCAPE), Glyph.B.forKind(InputKind.KEYBOARD))
        assertNull(Glyph.Pad(GlyphRole.GUIDE).forKind(InputKind.KEYBOARD))
    }

    @Test fun `the adapted hints rank like their buttons in a cramped bar`() {
        assertEquals(0, hintRank(Glyph.TAP))
        assertEquals(0, hintRank(Glyph.CLICK))
        assertEquals(0, hintRank(Glyph.Cap(InputCap.RETURN)))
        assertEquals(1, hintRank(Glyph.Cap(InputCap.ESCAPE)))
        assertEquals(3, hintRank(Glyph.Cap(InputCap.LEFT_BRACKET)))
        assertEquals(hintGroup(Glyph.L1), hintGroup(Glyph.Cap(InputCap.RIGHT_BRACKET)))
        assertEquals(4, hintRank(Glyph.Cap(InputCap.TAB)))
        assertEquals(4, hintRank(Glyph.SECONDARY_CLICK))
    }

    @Test fun `the touch-only chrome is for every input but a controller`() {
        for (kind in InputKind.entries) assertEquals(kind != InputKind.CONTROLLER, InputKindRule.WITHOUT_CONTROLLER.shows(kind))
        assertTrue(InputKindRule.WITH_CONTROLLER.shows(InputKind.CONTROLLER))
        assertTrue(!InputKindRule.WITH_CONTROLLER.shows(InputKind.TOUCH))
    }
}
