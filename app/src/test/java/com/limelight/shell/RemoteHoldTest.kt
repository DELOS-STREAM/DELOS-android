package com.limelight.shell

import android.view.KeyEvent
import com.limelight.shell.RemoteHold.Step
import org.junit.Assert.assertEquals
import org.junit.Test

/** Holding a TV remote's OK key (RemoteHold): X once, and the focused element is not activated as well. */
class RemoteHoldTest {
    private val hold = RemoteHold()
    private fun down(repeat: Int = 0) = hold.step(KeyEvent.ACTION_DOWN, repeat)
    private fun up() = hold.step(KeyEvent.ACTION_UP, 0)

    @Test
    fun `a short press passes on whole`() {
        assertEquals(Step.PASS, down())
        assertEquals(Step.PASS, up())
    }

    @Test
    fun `a hold fires once and its release is consumed`() {
        assertEquals(Step.PASS, down())
        assertEquals(Step.FIRE, down(1))
        assertEquals(Step.CONSUME, down(2))
        assertEquals(Step.CONSUME, down(3))
        assertEquals(Step.CONSUME, up())
    }

    @Test
    fun `the press after a hold is a short one again`() {
        down(); down(1); up()
        assertEquals(Step.PASS, down())
        assertEquals(Step.PASS, up())
    }

    @Test
    fun `a hold whose release was lost does not swallow the next press`() {
        down(); down(1)
        assertEquals(Step.PASS, down())
        assertEquals(Step.PASS, up())
    }
}
