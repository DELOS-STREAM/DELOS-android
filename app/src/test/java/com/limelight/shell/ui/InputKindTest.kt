package com.limelight.shell.ui

import android.view.InputDevice
import android.view.KeyEvent
import android.view.MotionEvent
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** The rules of DELOS for Apple's `ControllerGlyphsTests` (input kind), played with Android's events. */
class InputKindTest {
    @Test fun `the last input decides the kind`() {
        // Nothing used yet: the device's default, or the controller that is connected
        assertEquals(InputKind.TOUCH, InputKind.resolve(null, null, hasController = false, fallback = InputKind.TOUCH))
        assertEquals(InputKind.CONTROLLER, InputKind.resolve(null, null, hasController = true, fallback = InputKind.TOUCH))
        // A touch, a key or the pointer wins over a connected controller until the controller is used again
        for (kind in listOf(InputKind.TOUCH, InputKind.KEYBOARD, InputKind.POINTER)) {
            assertEquals(kind, InputKind.resolve(kind, kind, hasController = true, fallback = InputKind.CONTROLLER))
        }
        assertEquals(InputKind.CONTROLLER, InputKind.resolve(InputKind.CONTROLLER, InputKind.TOUCH, hasController = true, fallback = InputKind.POINTER))
        // The controller went away: back to what was used before it, else the default
        assertEquals(InputKind.KEYBOARD, InputKind.resolve(InputKind.CONTROLLER, InputKind.KEYBOARD, hasController = false, fallback = InputKind.TOUCH))
        assertEquals(InputKind.TOUCH, InputKind.resolve(InputKind.CONTROLLER, null, hasController = false, fallback = InputKind.TOUCH))
    }

    /** What reaches the input kind on the device, in order: Android's events and the system's device changes. */
    private sealed interface Event {
        data object Touch : Event
        data object Click : Event
        data object MouseMove : Event
        data object Key : Event
        data object Button : Event
        data class Stick(val x: Float, val y: Float) : Event
        data object Connect : Event
        data object Disconnect : Event
    }

    private val gamepadKeys = InputDevice.SOURCE_GAMEPAD or InputDevice.SOURCE_KEYBOARD or InputDevice.SOURCE_DPAD

    /** Plays [events] through [InputKinds] (the classification of each Android event) and [InputKindState]; the kind after each. */
    private fun kinds(events: List<Event>, padAtStart: Boolean = false): List<InputKind> {
        val state = InputKindState()
        state.controllersChanged(padAtStart)
        val stick = StickPress()
        return events.map { event ->
            val kind = when (event) {
                Event.Touch -> InputKinds.touch(MotionEvent.ACTION_DOWN, MotionEvent.TOOL_TYPE_FINGER, InputDevice.SOURCE_TOUCHSCREEN)
                Event.Click -> InputKinds.touch(MotionEvent.ACTION_DOWN, MotionEvent.TOOL_TYPE_MOUSE, InputDevice.SOURCE_MOUSE)
                Event.MouseMove -> InputKinds.pointer(MotionEvent.ACTION_HOVER_MOVE, MotionEvent.TOOL_TYPE_MOUSE, InputDevice.SOURCE_MOUSE)
                Event.Key -> InputKinds.key(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_ENTER, InputDevice.SOURCE_KEYBOARD, keyboard = true)
                Event.Button -> InputKinds.key(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_BUTTON_A, gamepadKeys, keyboard = false)
                is Event.Stick -> InputKind.CONTROLLER.takeIf { stick.update(InputKinds.stickMagnitude(event.x, event.y, 0f, 0f)) }
                Event.Connect -> { state.controllersChanged(true); null }
                Event.Disconnect -> { state.controllersChanged(false); null }
            }
            kind?.let(state::used)
            state.kind(InputKind.TOUCH)
        }
    }

    private val touch = InputKind.TOUCH
    private val controller = InputKind.CONTROLLER

    @Test fun `a touch with a pad connected is touch until the pad is used`() {
        assertEquals(listOf(touch, touch), kinds(listOf(Event.Touch, Event.Connect), padAtStart = true))
        assertEquals(listOf(touch, controller), kinds(listOf(Event.Touch, Event.Button), padAtStart = true))
        assertEquals(listOf(touch, controller), kinds(listOf(Event.Touch, Event.Stick(.6f, 0f)), padAtStart = true))
    }

    @Test fun `stick noise is no input`() {
        val noise = listOf(Event.Stick(.05f, -.02f), Event.Stick(-.12f, .08f), Event.Stick(.3f, .2f), Event.Stick(.45f, 0f), Event.Stick(0f, 0f))
        assertTrue(kinds(listOf(Event.Touch) + noise, padAtStart = true).all { it == touch })
        // Held past the threshold, then wobbling above the release point: one press
        assertEquals(
            listOf(touch, controller, touch, touch),
            kinds(listOf(Event.Touch, Event.Stick(.7f, 0f), Event.Touch, Event.Stick(.4f, 0f)), padAtStart = true),
        )
        // Back inside the release point (no input), then pushed again: a new press
        assertEquals(
            listOf(controller, touch, touch, touch, controller),
            kinds(listOf(Event.Stick(.7f, 0f), Event.Touch, Event.Stick(.2f, 0f), Event.Touch, Event.Stick(0f, -.8f)), padAtStart = true),
        )
    }

    @Test fun `the last deliberate input wins`() {
        assertEquals(
            listOf(controller, InputKind.KEYBOARD, InputKind.POINTER, touch, controller, InputKind.POINTER),
            kinds(listOf(Event.Button, Event.Key, Event.Click, Event.Touch, Event.Button, Event.MouseMove), padAtStart = true),
        )
    }

    @Test fun `a pad connecting is no input but a pad at start is the controller`() {
        assertEquals(emptyList<InputKind>(), kinds(emptyList(), padAtStart = true))
        assertEquals(touch, InputKindState().kind(InputKind.TOUCH))
        assertEquals(listOf(controller), kinds(listOf(Event.Connect)))
        assertEquals(listOf(touch, touch), kinds(listOf(Event.Touch, Event.Connect)))
        assertEquals(listOf(InputKind.KEYBOARD, InputKind.KEYBOARD), kinds(listOf(Event.Key, Event.Connect)))
    }

    @Test fun `the pad going away brings back what was used before it`() {
        assertEquals(listOf(InputKind.KEYBOARD, controller, InputKind.KEYBOARD), kinds(listOf(Event.Key, Event.Button, Event.Disconnect), padAtStart = true))
        assertEquals(listOf(controller, touch), kinds(listOf(Event.Button, Event.Disconnect), padAtStart = true))
        // The pad was the last input: plugged in again, it is the controller again
        assertEquals(listOf(controller, touch, controller), kinds(listOf(Event.Button, Event.Disconnect, Event.Connect), padAtStart = true))
    }

    @Test fun `keys count only as presses of a pad or a keyboard`() {
        val down = KeyEvent.ACTION_DOWN
        assertNull(InputKinds.key(KeyEvent.ACTION_UP, KeyEvent.KEYCODE_BUTTON_A, gamepadKeys, keyboard = false))
        assertEquals(controller, InputKinds.key(down, KeyEvent.KEYCODE_DPAD_LEFT, gamepadKeys, keyboard = false))
        // A stick turned into d-pad keys by Android carries the joystick source
        assertEquals(controller, InputKinds.key(down, KeyEvent.KEYCODE_DPAD_UP, InputDevice.SOURCE_JOYSTICK, keyboard = false))
        assertEquals(InputKind.KEYBOARD, InputKinds.key(down, KeyEvent.KEYCODE_ESCAPE, InputDevice.SOURCE_KEYBOARD, keyboard = true))
        // The handheld's own Back key, a TV remote, the volume keys: none
        assertNull(InputKinds.key(down, KeyEvent.KEYCODE_BACK, InputDevice.SOURCE_KEYBOARD, keyboard = false))
        assertNull(InputKinds.key(down, KeyEvent.KEYCODE_DPAD_CENTER, InputDevice.SOURCE_KEYBOARD or InputDevice.SOURCE_DPAD, keyboard = false))
        assertNull(InputKinds.key(down, KeyEvent.KEYCODE_VOLUME_UP, InputDevice.SOURCE_KEYBOARD, keyboard = true))
        assertNull(InputKinds.key(down, KeyEvent.KEYCODE_VOLUME_DOWN, gamepadKeys, keyboard = false))
    }

    @Test fun `touches and the pointer`() {
        assertEquals(touch, InputKinds.touch(MotionEvent.ACTION_POINTER_DOWN, MotionEvent.TOOL_TYPE_FINGER, InputDevice.SOURCE_TOUCHSCREEN))
        assertEquals(touch, InputKinds.touch(MotionEvent.ACTION_DOWN, MotionEvent.TOOL_TYPE_STYLUS, InputDevice.SOURCE_STYLUS))
        assertNull(InputKinds.touch(MotionEvent.ACTION_MOVE, MotionEvent.TOOL_TYPE_FINGER, InputDevice.SOURCE_TOUCHSCREEN))
        assertNull(InputKinds.touch(MotionEvent.ACTION_UP, MotionEvent.TOOL_TYPE_FINGER, InputDevice.SOURCE_TOUCHSCREEN))
        assertEquals(InputKind.POINTER, InputKinds.touch(MotionEvent.ACTION_DOWN, MotionEvent.TOOL_TYPE_MOUSE, InputDevice.SOURCE_MOUSE))
        // A mouse moving, scrolling or clicking, also captured in the stream; a pen's hover and the cursor entering are none
        assertEquals(InputKind.POINTER, InputKinds.pointer(MotionEvent.ACTION_SCROLL, MotionEvent.TOOL_TYPE_MOUSE, InputDevice.SOURCE_MOUSE))
        assertEquals(InputKind.POINTER, InputKinds.pointer(MotionEvent.ACTION_MOVE, MotionEvent.TOOL_TYPE_MOUSE, InputDevice.SOURCE_MOUSE_RELATIVE))
        assertEquals(InputKind.POINTER, InputKinds.pointer(MotionEvent.ACTION_MOVE, MotionEvent.TOOL_TYPE_FINGER, InputDevice.SOURCE_TOUCHPAD))
        assertNull(InputKinds.pointer(MotionEvent.ACTION_HOVER_MOVE, MotionEvent.TOOL_TYPE_STYLUS, InputDevice.SOURCE_STYLUS))
        assertNull(InputKinds.pointer(MotionEvent.ACTION_HOVER_ENTER, MotionEvent.TOOL_TYPE_MOUSE, InputDevice.SOURCE_MOUSE))
        assertNull(InputKinds.pointer(MotionEvent.ACTION_HOVER_MOVE, MotionEvent.TOOL_TYPE_FINGER, InputDevice.SOURCE_TOUCHSCREEN))
    }

    @Test fun `the hat counts like the stick`() {
        assertEquals(1f, InputKinds.stickMagnitude(0f, .1f, -1f, 0f))
        assertEquals(.6f, InputKinds.stickMagnitude(-.6f, .2f, 0f, 0f))
    }
}
