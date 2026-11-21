package com.limelight.shell.ui

import androidx.compose.foundation.ScrollState
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** The pointer's focus (no scroll follows it), the hover and the press values: docs/GUI.md, "Keyboard and pointer". */
class PointerLookTest {
    @After fun keysAgain() = PointerFocus.used(InputKind.KEYBOARD)

    /** A list's focus scroller as the screens make it; reduced motion applies a scroll at once, so the test sees it. */
    private fun list(): Pair<ScrollState, MarginBringIntoView> {
        val state = ScrollState(0)
        return state to MarginBringIntoView(before = 50f, after = 50f, scroller = FocusScroller(state, CoroutineScope(Dispatchers.Unconfined), 1f, reducedMotion = true))
    }

    @Test fun `a focus the pointer moved never scrolls its list`() {
        PointerFocus.used(InputKind.POINTER)
        val (state, spec) = list()
        // An element below the visible part: a key's focus would scroll it in
        assertEquals(0f, spec.calculateScrollDistance(offset = 1200f, size = 100f, containerSize = 1000f), 0f)
        assertEquals(0, state.value)
        assertFalse(PointerFocus.followsFocus)
    }

    @Test fun `a key, the pad or a touch hands the scrolling back to the focus`() {
        for (kind in listOf(InputKind.KEYBOARD, InputKind.CONTROLLER, InputKind.TOUCH)) {
            PointerFocus.used(InputKind.POINTER)
            PointerFocus.used(kind)
            val (state, spec) = list()
            spec.calculateScrollDistance(offset = 1200f, size = 100f, containerSize = 1000f)
            assertEquals(350, state.value)
            assertTrue(PointerFocus.followsFocus)
        }
    }

    @Test fun `content moving under a pointer at rest is no hover`() {
        assertTrue(PointerFocus.moved(400f, 300f))
        // The row scrolled a cover under the resting pointer: the same window position
        assertFalse(PointerFocus.moved(400f, 300f))
        assertFalse(PointerFocus.moved(400.2f, 300.3f))
        assertTrue(PointerFocus.moved(420f, 300f))
    }

    @Test fun `the hover's move cue plays at most every 90 ms`() {
        val t = 1_000_000L
        assertTrue(PointerFocus.cue(t))
        assertFalse(PointerFocus.cue(t + 40))
        assertFalse(PointerFocus.cue(t + 89))
        assertTrue(PointerFocus.cue(t + 90))
    }

    @Test fun `a finger or a pen never hovers`() {
        assertTrue(PointerFocus.hovers(androidx.compose.ui.input.pointer.PointerType.Mouse))
        assertFalse(PointerFocus.hovers(androidx.compose.ui.input.pointer.PointerType.Touch))
        assertFalse(PointerFocus.hovers(androidx.compose.ui.input.pointer.PointerType.Stylus))
        assertFalse(PointerFocus.hovers(androidx.compose.ui.input.pointer.PointerType.Eraser))
    }

    @Test fun `the press and hover values are the shared ones`() {
        assertEquals(.96f, Motion.PRESS_SCALE, 0f)
        assertEquals(120, Motion.PRESS)
        assertEquals(130L, Motion.PRESS_HOLD)
        assertEquals(420, Motion.FOCUS)
        assertEquals(1.6f, Motion.PRESS_GLOW, 0f)
        assertEquals(1.03f, PointerLook.HOVER_SCALE, 0f)
        assertEquals(.08f, PointerLook.HOVER_ALPHA, 0f)
        assertEquals(140, PointerLook.HOVER_IN)
        assertEquals(110, PointerLook.HOVER_OUT)
    }

    @Test fun `a pressed element stands at 0,96 of its size at rest, below every lift`() {
        assertEquals(1f, PointerLook.scale(0f, 0f), 0f)
        assertEquals(.96f, PointerLook.scale(0f, 1f), 1e-6f)
        assertEquals(1.03f, PointerLook.scale(1f, 0f), 1e-6f)
        // Hovered and pressed: the hover lift is gone, 0.96 of the size at rest
        assertEquals(.96f, PointerLook.scale(1f, 1f), 1e-6f)
        assertTrue(PointerLook.scale(1f, 1f) < 1f)
    }

    @Test fun `a tap acts once the squeeze has landed`() {
        assertEquals(120L, Motion.pressLandsIn(0))
        assertEquals(70L, Motion.pressLandsIn(50))
        // Held longer than the squeeze: at once
        assertEquals(0L, Motion.pressLandsIn(400))
    }

    @Test fun `a drag or scroll that consumes the finger's move lets go of the press`() {
        fun move(consumed: Boolean, to: Float = 40f) = androidx.compose.ui.input.pointer.PointerInputChange(
            id = androidx.compose.ui.input.pointer.PointerId(1), uptimeMillis = 16, position = androidx.compose.ui.geometry.Offset(10f, to),
            pressed = true, previousUptimeMillis = 0, previousPosition = androidx.compose.ui.geometry.Offset(10f, 60f), previousPressed = true,
            isInitiallyConsumed = false,
        ).also { if (consumed) it.consume() }
        // A drag took the move: the squeeze lets go (the move counts although the change is consumed)
        assertTrue(scrollTookOver(move(consumed = true)))
        // The finger moved but nobody took it (it may still be a tap), or a consumed change without a move: the press holds
        assertFalse(scrollTookOver(move(consumed = false)))
        assertFalse(scrollTookOver(move(consumed = true, to = 60f)))
    }
}
