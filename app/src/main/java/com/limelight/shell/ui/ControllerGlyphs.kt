package com.limelight.shell.ui

import android.content.Context
import android.content.pm.PackageManager
import android.hardware.input.InputManager
import android.os.Handler
import android.os.Looper
import android.view.InputDevice
import android.view.KeyEvent
import android.view.MotionEvent
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
    DPAD, GUIDE,
    /** The D-pad with its down arm pressed (Game info) and with left and right pressed (browse). */
    DPAD_DOWN, DPAD_HORIZONTAL,
}

/** Complete, owner-approved button glyph; physical positions never change with the printed label. */
fun PadFamily.fontSymbol(role: GlyphRole): String = when (role) {
    GlyphRole.SOUTH -> when (this) {
        PadFamily.GENERIC, PadFamily.XBOX -> Symbols.PAD_XBOX_A
        PadFamily.DUALSENSE -> Symbols.PAD_DUALSENSE_CROSS
        PadFamily.DUALSHOCK -> Symbols.PAD_DUALSHOCK4_CROSS
        PadFamily.NINTENDO -> Symbols.PAD_JOYCON_B
    }
    GlyphRole.EAST -> when (this) {
        PadFamily.GENERIC, PadFamily.XBOX -> Symbols.PAD_XBOX_B
        PadFamily.DUALSENSE -> Symbols.PAD_DUALSENSE_CIRCLE
        PadFamily.DUALSHOCK -> Symbols.PAD_DUALSHOCK4_CIRCLE
        PadFamily.NINTENDO -> Symbols.PAD_JOYCON_A
    }
    GlyphRole.WEST -> when (this) {
        PadFamily.GENERIC, PadFamily.XBOX -> Symbols.PAD_XBOX_X
        PadFamily.DUALSENSE -> Symbols.PAD_DUALSENSE_SQUARE
        PadFamily.DUALSHOCK -> Symbols.PAD_DUALSHOCK4_SQUARE
        PadFamily.NINTENDO -> Symbols.PAD_JOYCON_Y
    }
    GlyphRole.NORTH -> when (this) {
        PadFamily.GENERIC, PadFamily.XBOX -> Symbols.PAD_XBOX_Y
        PadFamily.DUALSENSE -> Symbols.PAD_DUALSENSE_TRIANGLE
        PadFamily.DUALSHOCK -> Symbols.PAD_DUALSHOCK4_TRIANGLE
        PadFamily.NINTENDO -> Symbols.PAD_JOYCON_X
    }
    GlyphRole.LEFT_SHOULDER -> when (this) {
        PadFamily.GENERIC, PadFamily.DUALSENSE -> Symbols.PAD_DUALSENSE_L1
        PadFamily.XBOX -> Symbols.PAD_XBOX_LB
        PadFamily.DUALSHOCK -> Symbols.PAD_DUALSHOCK4_L1
        PadFamily.NINTENDO -> Symbols.PAD_JOYCON_L
    }
    GlyphRole.RIGHT_SHOULDER -> when (this) {
        PadFamily.GENERIC, PadFamily.DUALSENSE -> Symbols.PAD_DUALSENSE_R1
        PadFamily.XBOX -> Symbols.PAD_XBOX_RB
        PadFamily.DUALSHOCK -> Symbols.PAD_DUALSHOCK4_R1
        PadFamily.NINTENDO -> Symbols.PAD_JOYCON_R
    }
    GlyphRole.LEFT_TRIGGER -> when (this) {
        PadFamily.GENERIC, PadFamily.DUALSENSE -> Symbols.PAD_DUALSENSE_L2
        PadFamily.XBOX -> Symbols.PAD_XBOX_LT
        PadFamily.DUALSHOCK -> Symbols.PAD_DUALSHOCK4_L2
        PadFamily.NINTENDO -> Symbols.PAD_JOYCON_ZL
    }
    GlyphRole.RIGHT_TRIGGER -> when (this) {
        PadFamily.GENERIC, PadFamily.DUALSENSE -> Symbols.PAD_DUALSENSE_R2
        PadFamily.XBOX -> Symbols.PAD_XBOX_RT
        PadFamily.DUALSHOCK -> Symbols.PAD_DUALSHOCK4_R2
        PadFamily.NINTENDO -> Symbols.PAD_JOYCON_ZR
    }
    GlyphRole.LEFT_STICK -> when (this) {
        PadFamily.GENERIC, PadFamily.XBOX -> Symbols.PAD_XBOX_STICK_L_CLICK
        PadFamily.DUALSENSE -> Symbols.PAD_DUALSENSE_STICK_L_CLICK
        PadFamily.DUALSHOCK -> Symbols.PAD_DUALSHOCK4_STICK_L_CLICK
        PadFamily.NINTENDO -> Symbols.PAD_JOYCON_STICK_L_CLICK
    }
    GlyphRole.RIGHT_STICK -> when (this) {
        PadFamily.GENERIC, PadFamily.XBOX -> Symbols.PAD_XBOX_STICK_R_CLICK
        PadFamily.DUALSENSE -> Symbols.PAD_DUALSENSE_STICK_R_CLICK
        PadFamily.DUALSHOCK -> Symbols.PAD_DUALSHOCK4_STICK_R_CLICK
        PadFamily.NINTENDO -> Symbols.PAD_JOYCON_STICK_R_CLICK
    }
    GlyphRole.MENU_LEFT -> when (this) {
        PadFamily.GENERIC, PadFamily.XBOX -> Symbols.PAD_XBOX_VIEW
        PadFamily.DUALSENSE -> Symbols.PAD_DUALSENSE_CREATE
        PadFamily.DUALSHOCK -> Symbols.PAD_DUALSHOCK4_SHARE
        PadFamily.NINTENDO -> Symbols.PAD_JOYCON_MINUS
    }
    GlyphRole.MENU_RIGHT -> when (this) {
        PadFamily.GENERIC, PadFamily.XBOX -> Symbols.PAD_XBOX_MENU
        PadFamily.DUALSENSE -> Symbols.PAD_DUALSENSE_OPTIONS
        PadFamily.DUALSHOCK -> Symbols.PAD_DUALSHOCK4_OPTIONS
        PadFamily.NINTENDO -> Symbols.PAD_JOYCON_PLUS
    }
    GlyphRole.TOUCHPAD -> if (this == PadFamily.DUALSHOCK) Symbols.PAD_DUALSHOCK4_TOUCHPAD else Symbols.PAD_DUALSENSE_TOUCHPAD
    GlyphRole.DPAD -> when (this) {
        PadFamily.GENERIC, PadFamily.XBOX -> Symbols.PAD_XBOX_DPAD_NEUTRAL
        PadFamily.DUALSENSE -> Symbols.PAD_DUALSENSE_DPAD_NEUTRAL
        PadFamily.DUALSHOCK -> Symbols.PAD_DUALSHOCK4_DPAD_NEUTRAL
        PadFamily.NINTENDO -> Symbols.PAD_JOYCON_DPAD_NEUTRAL
    }
    GlyphRole.DPAD_DOWN -> when (this) {
        PadFamily.GENERIC, PadFamily.XBOX -> Symbols.PAD_XBOX_DPAD_DOWN
        PadFamily.DUALSENSE -> Symbols.PAD_DUALSENSE_DPAD_DOWN
        PadFamily.DUALSHOCK -> Symbols.PAD_DUALSHOCK4_DPAD_DOWN
        PadFamily.NINTENDO -> Symbols.PAD_JOYCON_DPAD_DOWN
    }
    GlyphRole.DPAD_HORIZONTAL -> when (this) {
        PadFamily.GENERIC, PadFamily.XBOX -> Symbols.PAD_XBOX_DPAD_HORIZONTAL
        PadFamily.DUALSENSE -> Symbols.PAD_DUALSENSE_DPAD_HORIZONTAL
        PadFamily.DUALSHOCK -> Symbols.PAD_DUALSHOCK4_DPAD_HORIZONTAL
        // The Joy-Con caps have no left-right cut: the neutral pad
        PadFamily.NINTENDO -> Symbols.PAD_JOYCON_DPAD_NEUTRAL
    }
    GlyphRole.GUIDE -> when (this) {
        PadFamily.GENERIC, PadFamily.XBOX -> Symbols.PAD_XBOX_GUIDE
        PadFamily.DUALSENSE -> Symbols.PAD_DUALSENSE_PS
        PadFamily.DUALSHOCK -> Symbols.PAD_DUALSHOCK4_PS
        PadFamily.NINTENDO -> Symbols.PAD_JOYCON_HOME
    }
}

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
 * The glyph source of the interface: the kind of input used last ([kind], kept by [InputKindState]) and, for a controller, the glyph
 * family ([family]) of the controller used last, else the first one connected, else [PadFamily.GENERIC]. The family is evaluated again
 * when a controller connects or disconnects and when a key or motion event comes from another one; the kind changes only when a
 * deliberate input arrives ([key], [touch], [motion]: a touch, a key, the mouse, a pad's button or stick), or when the last controller
 * goes away. The shell's and the stream's windows feed it from their event dispatch. Every hint reads it (Compose state), so one change
 * redraws them all.
 */
object ControllerGlyphs {
    var family by mutableStateOf(PadFamily.GENERIC)
        private set

    var kind by mutableStateOf(InputKind.TOUCH)
        private set

    private var lastUsed: Int? = null
    private var input: InputManager? = null
    private val state = InputKindState()
    private val stick = StickPress()

    /** What a device without any input yet is taken to use: touch with a touchscreen, else the controller (a TV and its remote). */
    private var fallback = InputKind.TOUCH

    /** The family for the controller used last ([lastUsed]) and the ones connected, in the order the system lists them. */
    fun choose(lastUsed: PadDescriptor?, connected: List<PadDescriptor>): PadFamily {
        if (lastUsed != null && lastUsed.gamepad) return lastUsed.family
        return connected.firstOrNull { it.gamepad }?.family ?: PadFamily.GENERIC
    }

    /** Starts following the controllers (connect and disconnect); calling it again does nothing. */
    fun start(context: Context) {
        if (input != null) return
        val app = context.applicationContext
        fallback = if (app.packageManager.hasSystemFeature(PackageManager.FEATURE_TOUCHSCREEN)) InputKind.TOUCH else InputKind.CONTROLLER
        val manager = app.getSystemService(Context.INPUT_SERVICE) as InputManager
        input = manager
        manager.registerInputDeviceListener(object : InputManager.InputDeviceListener {
            override fun onInputDeviceAdded(deviceId: Int) = refresh()
            override fun onInputDeviceRemoved(deviceId: Int) = refresh()
            override fun onInputDeviceChanged(deviceId: Int) = refresh()
        }, Handler(Looper.getMainLooper()))
        refresh()
    }

    /** A key event reached a window: a press of a pad's button or of a keyboard's key is the input from now on. */
    fun key(event: KeyEvent) {
        if (event.action != KeyEvent.ACTION_DOWN) return
        val source = event.source
        val pad = InputKinds.fromPad(source)
        val kind = InputKinds.key(event.action, event.keyCode, source, keyboard = !pad && InputKinds.isKeyboard(event.device)) ?: return
        if (kind == InputKind.CONTROLLER) usedPad(event.deviceId)
        note(kind)
    }

    /** A touch event reached a window: a finger (or a pen) going down is touch, a mouse button the pointer. */
    fun touch(event: MotionEvent) {
        val index = event.actionIndex
        InputKinds.touch(event.actionMasked, event.getToolType(index), event.source)?.let(::note)
    }

    /** A generic motion event reached a window (or the stream's captured pointer): the mouse moving, a pad's stick or hat pushed. */
    fun motion(event: MotionEvent) {
        val source = event.source
        if (InputKinds.fromPad(source)) {
            if (event.actionMasked != MotionEvent.ACTION_MOVE) return
            val push = InputKinds.stickMagnitude(
                event.getAxisValue(MotionEvent.AXIS_X), event.getAxisValue(MotionEvent.AXIS_Y),
                event.getAxisValue(MotionEvent.AXIS_HAT_X), event.getAxisValue(MotionEvent.AXIS_HAT_Y),
            )
            if (!stick.update(push)) return
            usedPad(event.deviceId)
            note(InputKind.CONTROLLER)
            return
        }
        InputKinds.pointer(event.actionMasked, event.getToolType(0), source)?.let(::note)
    }

    /** The player used [input] on purpose: the hints show it from now on, and the pointer owns the scrolling while it is the input ([PointerFocus]). */
    fun note(input: InputKind) {
        state.used(input)
        PointerFocus.used(input)
        evaluate()
    }

    /** A press came from the pad [deviceId]: it is the controller used last. */
    private fun usedPad(deviceId: Int) {
        if (deviceId == lastUsed) return
        lastUsed = deviceId
        refresh()
    }

    private fun refresh() {
        val manager = input ?: return
        val devices = manager.inputDeviceIds.asList().mapNotNull { InputDevice.getDevice(it) }
        val last = lastUsed?.let { id -> devices.firstOrNull { it.id == id } }
        if (last == null) lastUsed = null
        val pads = devices.map(PadDescriptor::of)
        family = choose(last?.let(PadDescriptor::of), pads)
        state.controllersChanged(pads.any { it.gamepad })
        evaluate()
    }

    private fun evaluate() {
        val next = state.kind(fallback)
        if (next != kind) kind = next
    }

}
