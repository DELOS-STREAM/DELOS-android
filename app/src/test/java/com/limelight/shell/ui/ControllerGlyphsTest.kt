package com.limelight.shell.ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
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

    @Test fun `family face labels preserve physical button positions`() {
        val roles = listOf(GlyphRole.SOUTH, GlyphRole.EAST, GlyphRole.WEST, GlyphRole.NORTH)
        val expected = mapOf(
            PadFamily.GENERIC to listOf(Symbols.PAD_XBOX_A, Symbols.PAD_XBOX_B, Symbols.PAD_XBOX_X, Symbols.PAD_XBOX_Y),
            PadFamily.XBOX to listOf(Symbols.PAD_XBOX_A, Symbols.PAD_XBOX_B, Symbols.PAD_XBOX_X, Symbols.PAD_XBOX_Y),
            PadFamily.DUALSENSE to listOf(Symbols.PAD_DUALSENSE_CROSS, Symbols.PAD_DUALSENSE_CIRCLE, Symbols.PAD_DUALSENSE_SQUARE, Symbols.PAD_DUALSENSE_TRIANGLE),
            PadFamily.DUALSHOCK to listOf(Symbols.PAD_DUALSHOCK4_CROSS, Symbols.PAD_DUALSHOCK4_CIRCLE, Symbols.PAD_DUALSHOCK4_SQUARE, Symbols.PAD_DUALSHOCK4_TRIANGLE),
            PadFamily.NINTENDO to listOf(Symbols.PAD_JOYCON_B, Symbols.PAD_JOYCON_A, Symbols.PAD_JOYCON_Y, Symbols.PAD_JOYCON_X),
        )
        expected.forEach { (family, symbols) -> assertEquals(family.name, symbols, roles.map(family::fontSymbol)) }
    }

    @Test fun `shoulders and triggers preserve each family and generic labels`() {
        val roles = listOf(GlyphRole.LEFT_SHOULDER, GlyphRole.RIGHT_SHOULDER, GlyphRole.LEFT_TRIGGER, GlyphRole.RIGHT_TRIGGER)
        val expected = mapOf(
            PadFamily.GENERIC to listOf(Symbols.PAD_DUALSENSE_L1, Symbols.PAD_DUALSENSE_R1, Symbols.PAD_DUALSENSE_L2, Symbols.PAD_DUALSENSE_R2),
            PadFamily.XBOX to listOf(Symbols.PAD_XBOX_LB, Symbols.PAD_XBOX_RB, Symbols.PAD_XBOX_LT, Symbols.PAD_XBOX_RT),
            PadFamily.DUALSENSE to listOf(Symbols.PAD_DUALSENSE_L1, Symbols.PAD_DUALSENSE_R1, Symbols.PAD_DUALSENSE_L2, Symbols.PAD_DUALSENSE_R2),
            PadFamily.DUALSHOCK to listOf(Symbols.PAD_DUALSHOCK4_L1, Symbols.PAD_DUALSHOCK4_R1, Symbols.PAD_DUALSHOCK4_L2, Symbols.PAD_DUALSHOCK4_R2),
            PadFamily.NINTENDO to listOf(Symbols.PAD_JOYCON_L, Symbols.PAD_JOYCON_R, Symbols.PAD_JOYCON_ZL, Symbols.PAD_JOYCON_ZR),
        )
        expected.forEach { (family, symbols) -> assertEquals(family.name, symbols, roles.map(family::fontSymbol)) }
    }

    @Test fun `menu stick dpad and guide glyphs belong to their physical family`() {
        val roles = listOf(GlyphRole.MENU_LEFT, GlyphRole.MENU_RIGHT, GlyphRole.LEFT_STICK, GlyphRole.RIGHT_STICK, GlyphRole.DPAD, GlyphRole.GUIDE, GlyphRole.TOUCHPAD)
        val expected = mapOf(
            PadFamily.GENERIC to listOf(Symbols.PAD_XBOX_VIEW, Symbols.PAD_XBOX_MENU, Symbols.PAD_XBOX_STICK_L_CLICK, Symbols.PAD_XBOX_STICK_R_CLICK, Symbols.PAD_XBOX_DPAD_NEUTRAL, Symbols.PAD_XBOX_GUIDE, Symbols.PAD_DUALSENSE_TOUCHPAD),
            PadFamily.XBOX to listOf(Symbols.PAD_XBOX_VIEW, Symbols.PAD_XBOX_MENU, Symbols.PAD_XBOX_STICK_L_CLICK, Symbols.PAD_XBOX_STICK_R_CLICK, Symbols.PAD_XBOX_DPAD_NEUTRAL, Symbols.PAD_XBOX_GUIDE, Symbols.PAD_DUALSENSE_TOUCHPAD),
            PadFamily.DUALSENSE to listOf(Symbols.PAD_DUALSENSE_CREATE, Symbols.PAD_DUALSENSE_OPTIONS, Symbols.PAD_DUALSENSE_STICK_L_CLICK, Symbols.PAD_DUALSENSE_STICK_R_CLICK, Symbols.PAD_DUALSENSE_DPAD_NEUTRAL, Symbols.PAD_DUALSENSE_PS, Symbols.PAD_DUALSENSE_TOUCHPAD),
            PadFamily.DUALSHOCK to listOf(Symbols.PAD_DUALSHOCK4_SHARE, Symbols.PAD_DUALSHOCK4_OPTIONS, Symbols.PAD_DUALSHOCK4_STICK_L_CLICK, Symbols.PAD_DUALSHOCK4_STICK_R_CLICK, Symbols.PAD_DUALSHOCK4_DPAD_NEUTRAL, Symbols.PAD_DUALSHOCK4_PS, Symbols.PAD_DUALSHOCK4_TOUCHPAD),
            PadFamily.NINTENDO to listOf(Symbols.PAD_JOYCON_MINUS, Symbols.PAD_JOYCON_PLUS, Symbols.PAD_JOYCON_STICK_L_CLICK, Symbols.PAD_JOYCON_STICK_R_CLICK, Symbols.PAD_JOYCON_DPAD_NEUTRAL, Symbols.PAD_JOYCON_HOME, Symbols.PAD_DUALSENSE_TOUCHPAD),
        )
        expected.forEach { (family, symbols) -> assertEquals(family.name, symbols, roles.map(family::fontSymbol)) }
    }

    @Test fun `every role on every controller resolves in both bundled fonts`() {
        val fonts = FontCoverageTest()
        for (style in listOf("phosphor_regular", "phosphor_fill")) {
            val covered = fonts.cmap(style)
            for (family in PadFamily.entries) for (role in GlyphRole.entries) {
                val codepoint = family.fontSymbol(role).single().code
                assertTrue("$family / $role is missing from $style", codepoint in covered)
            }
        }
    }
}
