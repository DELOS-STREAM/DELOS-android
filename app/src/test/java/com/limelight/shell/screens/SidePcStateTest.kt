package com.limelight.shell.screens

import org.junit.Assert.assertEquals
import org.junit.Test

class SidePcStateTest {
    @Test fun connectingWinsOverEverything() {
        assertEquals(SidePcState.CONNECTING, SidePcState.of(online = true, running = true, connecting = true, canWake = true))
        assertEquals(SidePcState.CONNECTING, SidePcState.of(online = false, running = false, connecting = true, canWake = false))
    }

    @Test fun anOnlinePcStreamsWhileItRunsAGame() {
        assertEquals(SidePcState.STREAMING, SidePcState.of(online = true, running = true, connecting = false, canWake = false))
        assertEquals(SidePcState.ONLINE, SidePcState.of(online = true, running = false, connecting = false, canWake = false))
    }

    @Test fun anOfflinePcSleepsWhenItCanBeWoken() {
        assertEquals(SidePcState.ASLEEP, SidePcState.of(online = false, running = false, connecting = false, canWake = true))
        assertEquals(SidePcState.OFFLINE, SidePcState.of(online = false, running = true, connecting = false, canWake = false))
    }
}
