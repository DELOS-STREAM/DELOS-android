package com.limelight.shell.ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ControllerGlyphsTest {
    private fun pad(vendor: Int, product: Int, name: String, gamepad: Boolean = true) = PadDescriptor(vendor, product, name, gamepad)

    private val dualSense = pad(0x054c, 0x0ce6, "DualSense Wireless Controller")
    private val dualShock = pad(0x054c, 0x09cc, "Wireless Controller")
    private val proController = pad(0x057e, 0x2009, "Pro Controller")
    private val joyConL = pad(0x057e, 0x2006, "Joy-Con (L)")
    private val xbox = pad(0x045e, 0x0b12, "Xbox Wireless Controller")
    private val handheld = pad(0x2020, 0x0001, "Odin Controller")

    @Test fun `vendor picks the family`() {
        assertEquals(PadFamily.DUALSENSE, dualSense.family)
        assertEquals(PadFamily.DUALSENSE, pad(0x054c, 0x0df2, "DualSense Edge Wireless Controller").family)
        assertEquals(PadFamily.DUALSHOCK, dualShock.family)
        assertEquals(PadFamily.DUALSHOCK, pad(0x054c, 0x05c4, "Wireless Controller").family)
        assertEquals(PadFamily.NINTENDO, proController.family)
        assertEquals(PadFamily.NINTENDO, joyConL.family)
        assertEquals(PadFamily.XBOX, xbox.family)
    }

    @Test fun `name picks the family when the vendor is another`() {
        assertEquals(PadFamily.DUALSENSE, pad(0x1234, 1, "DualSense").family)
        assertEquals(PadFamily.DUALSHOCK, pad(0x1234, 1, "PlayStation 4 pad").family)
        assertEquals(PadFamily.NINTENDO, pad(0x1234, 1, "Nintendo Switch Pro Controller").family)
        assertEquals(PadFamily.XBOX, pad(0x1234, 1, "Xbox 360 Controller").family)
        assertEquals(PadFamily.GENERIC, handheld.family)
        assertEquals(PadFamily.GENERIC, pad(0, 0, "").family)
    }

    @Test fun `last used controller wins, else the first connected pad, else generic`() {
        assertEquals(PadFamily.NINTENDO, ControllerGlyphs.choose(proController, listOf(dualSense, proController)))
        assertEquals(PadFamily.DUALSENSE, ControllerGlyphs.choose(null, listOf(pad(0x046d, 1, "Keyboard", gamepad = false), dualSense, xbox)))
        assertEquals(PadFamily.GENERIC, ControllerGlyphs.choose(null, emptyList()))
        assertEquals(PadFamily.GENERIC, ControllerGlyphs.choose(null, listOf(pad(0x054c, 1, "Touchpad mouse", gamepad = false))))
        assertEquals(PadFamily.XBOX, ControllerGlyphs.choose(pad(0x054c, 1, "x", gamepad = false), listOf(xbox)))
    }

    @Test fun `confirm is the south button on every layout, only the mark changes`() {
        assertEquals(GlyphMark.Letter("A"), PadFamily.GENERIC.mark(GlyphRole.SOUTH))
        assertEquals(GlyphMark.Letter("A"), PadFamily.XBOX.mark(GlyphRole.SOUTH))
        assertEquals(GlyphMark.Cross, PadFamily.DUALSENSE.mark(GlyphRole.SOUTH))
        assertEquals(GlyphMark.Cross, PadFamily.DUALSHOCK.mark(GlyphRole.SOUTH))
        assertEquals(GlyphMark.Letter("B"), PadFamily.NINTENDO.mark(GlyphRole.SOUTH))
    }

    @Test fun `face buttons by position`() {
        val ps = listOf(GlyphRole.EAST, GlyphRole.WEST, GlyphRole.NORTH).map { PadFamily.DUALSENSE.mark(it) }
        assertEquals(listOf(GlyphMark.Circle, GlyphMark.Square, GlyphMark.Triangle), ps)
        val nintendo = listOf(GlyphRole.EAST, GlyphRole.WEST, GlyphRole.NORTH).map { PadFamily.NINTENDO.mark(it).text }
        assertEquals(listOf("A", "Y", "X"), nintendo)
        val xbox = listOf(GlyphRole.EAST, GlyphRole.WEST, GlyphRole.NORTH).map { PadFamily.XBOX.mark(it).text }
        assertEquals(listOf("B", "X", "Y"), xbox)
    }

    @Test fun `shoulders, triggers and menu buttons`() {
        fun texts(f: PadFamily) = listOf(GlyphRole.LEFT_SHOULDER, GlyphRole.RIGHT_SHOULDER, GlyphRole.LEFT_TRIGGER, GlyphRole.RIGHT_TRIGGER).map { f.mark(it).text }
        assertEquals(listOf("L1", "R1", "L2", "R2"), texts(PadFamily.DUALSENSE))
        assertEquals(listOf("L1", "R1", "L2", "R2"), texts(PadFamily.GENERIC))
        assertEquals(listOf("LB", "RB", "LT", "RT"), texts(PadFamily.XBOX))
        assertEquals(listOf("L", "R", "ZL", "ZR"), texts(PadFamily.NINTENDO))
        assertEquals(GlyphMark.View, PadFamily.DUALSENSE.mark(GlyphRole.MENU_LEFT))
        assertEquals(GlyphMark.Menu, PadFamily.DUALSENSE.mark(GlyphRole.MENU_RIGHT))
        assertEquals("\u2212", PadFamily.NINTENDO.mark(GlyphRole.MENU_LEFT).text)
        assertEquals("+", PadFamily.NINTENDO.mark(GlyphRole.MENU_RIGHT).text)
        assertEquals(GlyphMark.Touchpad, PadFamily.DUALSENSE.mark(GlyphRole.TOUCHPAD))
        assertNull(PadFamily.DUALSENSE.mark(GlyphRole.TOUCHPAD).text)
    }
}
