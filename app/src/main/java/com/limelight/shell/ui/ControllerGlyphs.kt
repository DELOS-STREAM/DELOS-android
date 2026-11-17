package com.limelight.shell.ui

import android.content.Context
import android.hardware.input.InputManager
import android.os.Handler
import android.os.Looper
import android.view.InputDevice
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue

/** The family of face-button labels a controller shows: what every hint is drawn with. [GENERIC] (no controller, or an unknown one) is the Xbox lettering. */
enum class PadFamily { GENERIC, XBOX, DUALSENSE, DUALSHOCK, NINTENDO }

/**
 * A button's place on a pad, named by its position, not its label: the same action sits at the same place on every layout (confirm is
 * always the south button, `KEYCODE_BUTTON_A` of Android) and only the glyph drawn for the place changes.
 */
enum class GlyphRole {
    /** A, cross, B of a Nintendo pad: confirm. */
    SOUTH,
    /** B, circle, A of a Nintendo pad: back. */
    EAST,
    /** X, square, Y of a Nintendo pad. */
    WEST,
    /** Y, triangle, X of a Nintendo pad. */
    NORTH,
    LEFT_SHOULDER, RIGHT_SHOULDER, LEFT_TRIGGER, RIGHT_TRIGGER,
    LEFT_STICK, RIGHT_STICK,
    /** The left menu button: View, Create, Share, -. */
    MENU_LEFT,
    /** The right menu button: Menu, Options, +. */
    MENU_RIGHT,
    TOUCHPAD,
}

/** What a glyph shows. */
sealed interface GlyphMark {
    data class Letter(val label: String) : GlyphMark
    data object Cross : GlyphMark
    data object Circle : GlyphMark
    data object Square : GlyphMark
    data object Triangle : GlyphMark
    /** Two overlapping windows: View, Create, Share (the AYN square on AYN handhelds). */
    data object View : GlyphMark
    /** Three lines: Menu, Options (the AYN play icon on AYN handhelds). */
    data object Menu : GlyphMark
    data object Touchpad : GlyphMark

    /** The mark as text, for a sentence or the companion window; null for the icons. */
    val text: String?
        get() = when (this) {
            is Letter -> label
            Cross -> "\u2715"
            Circle -> "\u25CB"
            Square -> "\u25A1"
            Triangle -> "\u25B3"
            View, Menu, Touchpad -> null
        }
}

/** The mark of the button at [role] on this family's pads. */
fun PadFamily.mark(role: GlyphRole): GlyphMark = when (role) {
    GlyphRole.SOUTH -> face("A", GlyphMark.Cross, "B")
    GlyphRole.EAST -> face("B", GlyphMark.Circle, "A")
    GlyphRole.WEST -> face("X", GlyphMark.Square, "Y")
    GlyphRole.NORTH -> face("Y", GlyphMark.Triangle, "X")
    GlyphRole.LEFT_SHOULDER -> shoulder("LB", "L", "L1")
    GlyphRole.RIGHT_SHOULDER -> shoulder("RB", "R", "R1")
    GlyphRole.LEFT_TRIGGER -> shoulder("LT", "ZL", "L2")
    GlyphRole.RIGHT_TRIGGER -> shoulder("RT", "ZR", "R2")
    GlyphRole.LEFT_STICK -> GlyphMark.Letter("L3")
    GlyphRole.RIGHT_STICK -> GlyphMark.Letter("R3")
    GlyphRole.MENU_LEFT -> if (this == PadFamily.NINTENDO) GlyphMark.Letter("\u2212") else GlyphMark.View
    GlyphRole.MENU_RIGHT -> if (this == PadFamily.NINTENDO) GlyphMark.Letter("+") else GlyphMark.Menu
    GlyphRole.TOUCHPAD -> GlyphMark.Touchpad
}

private fun PadFamily.face(xbox: String, playStation: GlyphMark, nintendo: String): GlyphMark = when (this) {
    PadFamily.GENERIC, PadFamily.XBOX -> GlyphMark.Letter(xbox)
    PadFamily.DUALSENSE, PadFamily.DUALSHOCK -> playStation
    PadFamily.NINTENDO -> GlyphMark.Letter(nintendo)
}

private fun PadFamily.shoulder(xbox: String, nintendo: String, other: String): GlyphMark = GlyphMark.Letter(
    when (this) {
        PadFamily.XBOX -> xbox
        PadFamily.NINTENDO -> nintendo
        PadFamily.GENERIC, PadFamily.DUALSENSE, PadFamily.DUALSHOCK -> other
    },
)

/** What Android says about an input device, as far as the glyphs need it (a value, so tests can fake controllers). */
data class PadDescriptor(val vendorId: Int, val productId: Int, val name: String, val gamepad: Boolean = true) {
    /** The glyph family: the vendor first (Sony, Nintendo, Microsoft), then the device name (a pad through a driver or a dongle that reports another vendor). */
    val family: PadFamily
        get() {
            when (vendorId) {
                VENDOR_SONY -> return if (productId in DUALSHOCK_PRODUCTS || name.lowercase().let { "dualshock" in it || "ds4" in it }) PadFamily.DUALSHOCK else PadFamily.DUALSENSE
                VENDOR_NINTENDO -> return PadFamily.NINTENDO
                VENDOR_MICROSOFT -> return PadFamily.XBOX
            }
            val n = name.lowercase()
            fun has(vararg words: String) = words.any { it in n }
            return when {
                has("dualsense") -> PadFamily.DUALSENSE
                has("dualshock", "ds4", "playstation") -> PadFamily.DUALSHOCK
                has("nintendo", "joy-con", "joycon", "pro controller") -> PadFamily.NINTENDO
                has("xbox") -> PadFamily.XBOX
                else -> PadFamily.GENERIC
            }
        }

    companion object {
        const val VENDOR_SONY = 0x054c
        const val VENDOR_NINTENDO = 0x057e
        const val VENDOR_MICROSOFT = 0x045e

        /** DualShock 4 (both revisions and the USB wireless adapter); the other Sony pads are DualSense (0x0ce6) and DualSense Edge (0x0df2). */
        private val DUALSHOCK_PRODUCTS = setOf(0x05c4, 0x09cc, 0x0ba0)

        fun of(device: InputDevice) = PadDescriptor(device.vendorId, device.productId, device.name.orEmpty(), isGamepad(device))

        /** A pad with face buttons to press (not a keyboard, a mouse or a virtual device). */
        fun isGamepad(device: InputDevice): Boolean {
            if (device.isVirtual) return false
            val s = device.sources
            return s and InputDevice.SOURCE_GAMEPAD == InputDevice.SOURCE_GAMEPAD || s and InputDevice.SOURCE_JOYSTICK == InputDevice.SOURCE_JOYSTICK
        }
    }
}

/**
 * The glyph family in use: the controller used last, else the first one connected, else [PadFamily.GENERIC]. It is evaluated again when a
 * controller connects or disconnects and when a key or motion event comes from another one. Every hint reads [family] (Compose state),
 * so one change redraws them all.
 */
object ControllerGlyphs {
    var family by mutableStateOf(PadFamily.GENERIC)
        private set

    private var lastUsed: Int? = null
    private var input: InputManager? = null

    /** The family for the controller used last ([lastUsed]) and the ones connected, in the order the system lists them. */
    fun choose(lastUsed: PadDescriptor?, connected: List<PadDescriptor>): PadFamily {
        if (lastUsed != null && lastUsed.gamepad) return lastUsed.family
        return connected.firstOrNull { it.gamepad }?.family ?: PadFamily.GENERIC
    }

    /** Starts following the controllers (connect and disconnect); calling it again does nothing. */
    fun start(context: Context) {
        if (input != null) return
        val manager = context.applicationContext.getSystemService(Context.INPUT_SERVICE) as InputManager
        input = manager
        manager.registerInputDeviceListener(object : InputManager.InputDeviceListener {
            override fun onInputDeviceAdded(deviceId: Int) = refresh()
            override fun onInputDeviceRemoved(deviceId: Int) = refresh()
            override fun onInputDeviceChanged(deviceId: Int) = refresh()
        }, Handler(Looper.getMainLooper()))
        refresh()
    }

    /** A key or motion event came from [device]: it is the controller used last. */
    fun used(device: InputDevice?) {
        val id = device?.takeIf { PadDescriptor.isGamepad(it) }?.id ?: return
        if (id == lastUsed) return
        lastUsed = id
        refresh()
    }

    private fun refresh() {
        val manager = input ?: return
        val devices = manager.inputDeviceIds.asList().mapNotNull { InputDevice.getDevice(it) }
        val last = lastUsed?.let { id -> devices.firstOrNull { it.id == id } }
        if (last == null) lastUsed = null
        family = choose(last?.let(PadDescriptor::of), devices.map(PadDescriptor::of))
    }

    fun mark(role: GlyphRole): GlyphMark = family.mark(role)
}
