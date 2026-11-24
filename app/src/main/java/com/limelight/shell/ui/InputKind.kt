package com.limelight.shell.ui

import android.view.InputDevice
import android.view.KeyEvent
import android.view.MotionEvent
import kotlin.math.abs
import kotlin.math.max

/** The kind of input the player used last: what the hints are drawn for (`InputKind` of DELOS for Apple). */
enum class InputKind {
    CONTROLLER, KEYBOARD, POINTER, TOUCH;

    companion object {
        /**
         * The kind the hints are drawn for. [last]: the kind of the last input (null: none yet); [lastOther]: the kind of the last input that
         * was not a controller's (what comes back when the controller goes away); [hasController]: a gamepad is connected; [fallback]: the
         * device's default (touch with a touchscreen, else the controller). The last input wins, except that a controller that is gone
         * cannot be the answer.
         */
        fun resolve(last: InputKind?, lastOther: InputKind?, hasController: Boolean, fallback: InputKind): InputKind = when (last) {
            CONTROLLER, null -> if (hasController) CONTROLLER else lastOther ?: fallback
            else -> last
        }
    }
}

/**
 * What the input kind is made of: the last deliberate input and whether a gamepad is connected. Only a deliberate input changes the last
 * one ([used]: a touch begins, a pad button is pressed, the stick passes [StickPress.PRESS], a key is pressed, the mouse moves or
 * clicks). A controller connecting or disconnecting is no input ([controllersChanged]): the system reports those on its own (a pad that
 * wakes up), and a touch followed by one of them must still be a touch.
 */
class InputKindState {
    /** The kind of the last deliberate input; null: none yet. */
    var last: InputKind? = null
        private set

    /** The kind of the last deliberate input that was not a controller's. */
    var lastOther: InputKind? = null
        private set

    /** A gamepad is connected. */
    var hasController = false
        private set

    /** The player used [input] on purpose. */
    fun used(input: InputKind) {
        last = input
        if (input != InputKind.CONTROLLER) lastOther = input
    }

    /** The gamepads that are connected changed. */
    fun controllersChanged(hasGamepad: Boolean) {
        hasController = hasGamepad
    }

    fun kind(fallback: InputKind): InputKind = InputKind.resolve(last, lastOther, hasController, fallback)
}

/**
 * A stick or hat pushed on purpose: past [PRESS] of its travel (half, the threshold at which Android turns a stick into d-pad keys), then
 * held until it is back inside [RELEASE]. Wobbling above the release point is the same push, so stick noise is no input.
 */
class StickPress {
    private var held = false

    /** [magnitude]: the largest axis of the stick (or hat), 0..1. True once for each push. */
    fun update(magnitude: Float): Boolean {
        if (held) {
            if (magnitude <= RELEASE) held = false
            return false
        }
        if (magnitude < PRESS) return false
        held = true
        return true
    }

    companion object {
        const val PRESS = .5f
        const val RELEASE = .3f
    }
}

/** Which kind of input an Android event is: pure functions of the event's numbers, so the rules are tested without devices. */
object InputKinds {
    private fun has(source: Int, wanted: Int) = source and wanted == wanted

    /** The event comes from a pad's buttons or its stick (Android turns a stick into d-pad keys with the joystick source). */
    fun fromPad(source: Int): Boolean = has(source, InputDevice.SOURCE_GAMEPAD) || has(source, InputDevice.SOURCE_JOYSTICK)

    /** Keys that the system or the hardware sends on its own, or that change the device, not the interface: never an input kind. */
    private fun systemKey(keyCode: Int): Boolean = when (keyCode) {
        KeyEvent.KEYCODE_VOLUME_UP, KeyEvent.KEYCODE_VOLUME_DOWN, KeyEvent.KEYCODE_VOLUME_MUTE, KeyEvent.KEYCODE_POWER,
        KeyEvent.KEYCODE_SLEEP, KeyEvent.KEYCODE_WAKEUP, KeyEvent.KEYCODE_CAMERA, KeyEvent.KEYCODE_FOCUS,
        KeyEvent.KEYCODE_BRIGHTNESS_UP, KeyEvent.KEYCODE_BRIGHTNESS_DOWN, KeyEvent.KEYCODE_HEADSETHOOK, KeyEvent.KEYCODE_UNKNOWN,
        -> true
        else -> false
    }

    /**
     * The kind of a key event, or null when it is none. A press (not a release or a repeat's release) from a pad is the controller; from a
     * physical keyboard with letters ([keyboard]) it is the keyboard. Keys of other devices (the handheld's own Back and volume keys, a TV
     * remote, the navigation bar and the on-screen keyboard, which a touch already counted) are none.
     */
    fun key(action: Int, keyCode: Int, source: Int, keyboard: Boolean): InputKind? = when {
        action != KeyEvent.ACTION_DOWN || systemKey(keyCode) -> null
        fromPad(source) -> InputKind.CONTROLLER
        keyboard -> InputKind.KEYBOARD
        else -> null
    }

    /** A physical keyboard with letters: not virtual (the on-screen keyboard, injected keys), not a pad. */
    fun isKeyboard(device: InputDevice?): Boolean =
        device != null && !device.isVirtual && device.keyboardType == InputDevice.KEYBOARD_TYPE_ALPHABETIC && !fromPad(device.sources)

    /** A TV remote or media control device: non-alphabetic, has D-pad or keyboard keys, not a gamepad. */
    fun isTvRemote(device: InputDevice?): Boolean =
        device != null && !device.isVirtual && (has(device.sources, InputDevice.SOURCE_DPAD) || has(device.sources, InputDevice.SOURCE_KEYBOARD)) &&
            !fromPad(device.sources) && device.keyboardType != InputDevice.KEYBOARD_TYPE_ALPHABETIC

    private fun mouseLike(source: Int): Boolean =
        has(source, InputDevice.SOURCE_MOUSE) || has(source, InputDevice.SOURCE_MOUSE_RELATIVE) || has(source, InputDevice.SOURCE_TOUCHPAD)

    /** The kind of a touch event: a finger or a pen going down is touch, a mouse button going down is the pointer; anything else is none. */
    fun touch(actionMasked: Int, toolType: Int, source: Int): InputKind? {
        if (actionMasked != MotionEvent.ACTION_DOWN && actionMasked != MotionEvent.ACTION_POINTER_DOWN) return null
        return when (toolType) {
            MotionEvent.TOOL_TYPE_MOUSE -> InputKind.POINTER
            MotionEvent.TOOL_TYPE_FINGER, MotionEvent.TOOL_TYPE_STYLUS, MotionEvent.TOOL_TYPE_ERASER -> InputKind.TOUCH
            else -> when {
                has(source, InputDevice.SOURCE_TOUCHSCREEN) -> InputKind.TOUCH
                mouseLike(source) -> InputKind.POINTER
                else -> null
            }
        }
    }

    /**
     * The kind of a generic motion event of a mouse or a trackpad (also captured, in the stream): it moves, scrolls or clicks. A pen's
     * hover is not the pointer; the cursor entering or leaving the window is the system's.
     */
    fun pointer(actionMasked: Int, toolType: Int, source: Int): InputKind? {
        if (!mouseLike(source) || toolType == MotionEvent.TOOL_TYPE_STYLUS || toolType == MotionEvent.TOOL_TYPE_ERASER) return null
        return when (actionMasked) {
            MotionEvent.ACTION_HOVER_MOVE, MotionEvent.ACTION_MOVE, MotionEvent.ACTION_SCROLL, MotionEvent.ACTION_BUTTON_PRESS,
            MotionEvent.ACTION_DOWN,
            -> InputKind.POINTER
            else -> null
        }
    }

    /** The push of a pad's left stick or hat: the largest of the four axes, 0..1. */
    fun stickMagnitude(x: Float, y: Float, hatX: Float, hatY: Float): Float = max(max(abs(x), abs(y)), max(abs(hatX), abs(hatY)))
}
