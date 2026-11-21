package com.limelight.shell.screens

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class OverviewDockTest {
    @Test fun `a video starts on the timeline, Down reaches play and Up reaches close`() {
        assertEquals(DockSlot.SEEK, dockDefault(video = true))
        assertEquals(DockSlot.PLAY, dockStep(DockSlot.SEEK, 0, 1, video = true))
        assertEquals(DockSlot.CLOSE, dockStep(DockSlot.SEEK, 0, -1, video = true))
        assertEquals(DockSlot.SEEK, dockStep(DockSlot.CLOSE, 0, 1, video = true))
        assertEquals(DockSlot.SEEK, dockStep(DockSlot.MUTE, 0, -1, video = true))
    }

    @Test fun `Left and Right act on the timeline and the volume, and walk the transport to the sound`() {
        assertNull(dockStep(DockSlot.SEEK, -1, 0, video = true))
        assertNull(dockStep(DockSlot.VOLUME, 1, 0, video = true))
        assertEquals(DockSlot.BACK, dockStep(DockSlot.PLAY, -1, 0, video = true))
        assertEquals(DockSlot.MUTE, dockStep(DockSlot.NEXT, 1, 0, video = true))
        assertEquals(DockSlot.PREVIOUS, dockStep(DockSlot.PREVIOUS, -1, 0, video = true))
    }

    @Test fun `a screenshot never browses with Left and Right`() {
        assertEquals(DockSlot.NEXT, dockDefault(video = false))
        assertEquals(DockSlot.PREVIOUS, dockStep(DockSlot.NEXT, -1, 0, video = false))
        assertEquals(DockSlot.NEXT, dockStep(DockSlot.NEXT, 1, 0, video = false))
        assertEquals(DockSlot.CLOSE, dockStep(DockSlot.NEXT, 0, -1, video = false))
        assertEquals(DockSlot.NEXT, dockStep(DockSlot.CLOSE, 0, 1, video = false))
    }

    @Test fun `morphing preserves every shared focus stop`() {
        listOf(DockSlot.CLOSE, DockSlot.PREVIOUS, DockSlot.NEXT).forEach { slot ->
            assertEquals(slot, dockSurviving(slot, video = false))
            assertEquals(slot, dockSurviving(slot, video = true))
        }
        DockSlot.entries.forEach { assertEquals(it, dockSurviving(it, video = true)) }
    }

    @Test fun `disappearing video controls hand focus to the nearest surviving edge`() {
        listOf(DockSlot.SEEK, DockSlot.BACK).forEach {
            assertEquals(DockSlot.PREVIOUS, dockSurviving(it, video = false))
        }
        listOf(DockSlot.PLAY, DockSlot.FORWARD, DockSlot.MUTE, DockSlot.VOLUME).forEach {
            assertEquals(DockSlot.NEXT, dockSurviving(it, video = false))
        }
    }

    @Test fun `repeated seeks one way add up, a pause or the other way starts again`() {
        assertEquals(20, seekFlashTotal(10, 1, 1_000, 1, 1_500))
        assertEquals(10, seekFlashTotal(20, 1, 1_000, -1, 1_200))
        assertEquals(10, seekFlashTotal(30, 1, 1_000, 1, 1_000 + SEEK_FLASH_CHAIN_MS + 1))
    }

    @Test fun `a long gallery shows a window of page dots around the current item`() {
        assertEquals(0 until 5, pageWindow(5, 4))
        assertEquals(0 until PAGE_DOTS, pageWindow(29, 2))
        assertEquals(1 until 1 + PAGE_DOTS, pageWindow(29, 5))
        assertEquals(29 - PAGE_DOTS until 29, pageWindow(29, 28))
        (0 until 29).forEach { assertTrue(it in pageWindow(29, it)) }
    }

    @Test fun `input brings the hidden overlay back and marks the reveal`() {
        val input = OverviewViewerInput(now = 0)
        input.hideIfIdle(3_000)
        assertFalse(input.controlsVisible)
        input.onInput(5_000)
        assertTrue(input.controlsVisible)
        assertEquals(5_000L, input.revealedAt)
        input.onInput(5_400)
        assertEquals(5_000L, input.revealedAt)
        input.hide()
        assertFalse(input.controlsVisible)
    }
}
