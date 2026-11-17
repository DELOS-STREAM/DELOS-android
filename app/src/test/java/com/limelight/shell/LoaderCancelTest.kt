package com.limelight.shell

import android.view.KeyEvent
import com.limelight.shell.ui.LaunchHandoff
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class LoaderCancelTest {
    @Test
    fun startsConnectingAndCancellable() {
        val c = LoaderCancel()
        assertEquals(LoaderPhase.CONNECTING, c.phase)
        assertTrue(c.cancellable)
    }

    @Test
    fun streamingThenGone() {
        val c = LoaderCancel()
        c.streaming()
        assertEquals(LoaderPhase.STREAMING, c.phase)
        assertTrue(c.cancellable)
        c.gone()
        assertEquals(LoaderPhase.GONE, c.phase)
        assertFalse(c.cancellable)
        c.streaming()
        assertEquals(LoaderPhase.GONE, c.phase)
    }

    @Test
    fun requestWhileConnectingOrStreamingSucceedsOnce() {
        val connecting = LoaderCancel()
        assertTrue(connecting.request())
        assertEquals(LoaderPhase.CANCELLED, connecting.phase)
        assertFalse(connecting.request())
        assertFalse(connecting.cancellable)

        val streaming = LoaderCancel().also { it.streaming() }
        assertTrue(streaming.request())
        assertFalse(streaming.request())
    }

    @Test
    fun requestAfterGoneIsRefused() {
        val c = LoaderCancel().also { it.streaming(); it.gone() }
        assertFalse(c.request())
        assertEquals(LoaderPhase.GONE, c.phase)
    }

    @Test
    fun cancelledIsTerminal() {
        val c = LoaderCancel().also { it.request() }
        c.streaming()
        assertEquals(LoaderPhase.CANCELLED, c.phase)
        c.gone()
        assertEquals(LoaderPhase.CANCELLED, c.phase)
    }

    @Test
    fun plansStopTheConnectionOnlyWhenOneRuns() {
        val connecting = LoaderCancel.planFor(LoaderPhase.CONNECTING, connecting = true, connected = false)
        assertTrue(connecting.stopConnection)
        assertTrue(connecting.returnToLibrary)
        assertFalse(connecting.showResult)
        assertTrue(LoaderCancel.planFor(LoaderPhase.STREAMING, connecting = false, connected = true).stopConnection)
        // The hand-off gap: nothing has started yet
        assertFalse(LoaderCancel.planFor(LoaderPhase.CONNECTING, connecting = false, connected = false).stopConnection)
        assertFalse(LoaderCancel.planFor(LoaderPhase.GONE, connecting = false, connected = true).stopConnection)
    }

    @Test
    fun cancellingNeverQuitsTheGame() {
        for (phase in LoaderPhase.entries) for (a in listOf(true, false)) for (b in listOf(true, false)) {
            assertFalse(LoaderCancel.planFor(phase, a, b).quitGame)
        }
    }

    @Test
    fun backBAndEscapeCancelOnRelease() {
        for (key in listOf(KeyEvent.KEYCODE_BACK, KeyEvent.KEYCODE_BUTTON_B, KeyEvent.KEYCODE_ESCAPE)) {
            assertEquals(LoaderKey.CANCEL, LoaderKeys.policy(key, KeyEvent.ACTION_UP, false))
            assertEquals(LoaderKey.CONSUME, LoaderKeys.policy(key, KeyEvent.ACTION_DOWN, false))
            assertEquals(LoaderKey.CONSUME, LoaderKeys.policy(key, KeyEvent.ACTION_UP, true))
        }
    }

    @Test
    fun otherKeysPassThrough() {
        for (key in listOf(KeyEvent.KEYCODE_BUTTON_A, KeyEvent.KEYCODE_DPAD_UP, KeyEvent.KEYCODE_ENTER)) {
            assertEquals(LoaderKey.NONE, LoaderKeys.policy(key, KeyEvent.ACTION_UP, false))
            assertEquals(LoaderKey.NONE, LoaderKeys.policy(key, KeyEvent.ACTION_DOWN, false))
        }
    }

    @Test
    fun cancelRequestIsReadAndCleared() {
        LaunchHandoff.reset()
        assertFalse(LaunchHandoff.takeCancelRequest())
        LaunchHandoff.cancelRequested = true
        assertTrue(LaunchHandoff.takeCancelRequest())
        assertFalse(LaunchHandoff.takeCancelRequest())
    }

    @Test
    fun resetClearsTheCancelRequest() {
        LaunchHandoff.cancelRequested = true
        LaunchHandoff.reset()
        assertFalse(LaunchHandoff.cancelRequested)
    }
}
