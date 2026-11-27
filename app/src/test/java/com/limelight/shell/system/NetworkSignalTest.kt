package com.limelight.shell.system

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** The network signal's pure logic: the modem's reading, the waiting before a problem counts, Wi-Fi stability and their sum. */
class NetworkSignalTest {
    @Test
    fun `no cells is unknown, not zero bars`() {
        assertNull(cellReading(emptyList()))
    }

    @Test
    fun `the bars are the weakest cell's and the name the newest technology`() {
        assertEquals(CellReading(2, "5G"), cellReading(listOf(CellSample("LTE", 4), CellSample("5G", 2))))
        assertEquals(CellReading(3, "LTE"), cellReading(listOf(CellSample("LTE", 3))))
        assertEquals(CellReading(1, ""), cellReading(listOf(CellSample("", 1))))
    }

    @Test
    fun `an LTE cell at minus 105 dBm or less is two bars at most`() {
        assertEquals(2, cellReading(listOf(CellSample("LTE", 4, lteRsrp = -105)))!!.bars)
        assertEquals(4, cellReading(listOf(CellSample("LTE", 4, lteRsrp = -104)))!!.bars)
        assertEquals(1, cellReading(listOf(CellSample("LTE", 1, lteRsrp = -118)))!!.bars)
    }

    @Test
    fun `a condition counts only after it lasted and ends only after it was gone`() {
        val d = Debounced(raiseMs = 5_000, clearMs = 10_000)
        assertFalse(d.update(true, 0))
        assertEquals(5_000L, d.dueAt())
        assertFalse(d.update(true, 4_999))
        assertTrue(d.update(true, 5_000))
        assertNull(d.dueAt())
        assertTrue(d.update(false, 6_000))
        assertEquals(16_000L, d.dueAt())
        assertTrue(d.update(false, 15_999))
        assertFalse(d.update(false, 16_000))
    }

    @Test
    fun `a value that flips back in time changes nothing`() {
        val d = Debounced(raiseMs = 5_000, clearMs = 10_000)
        d.update(true, 0)
        d.update(false, 3_000)
        assertFalse(d.update(true, 4_000))
        // The wait started again at 4 s
        assertFalse(d.update(true, 8_999))
        assertTrue(d.update(true, 9_000))
    }

    @Test
    fun `too few Wi-Fi samples say nothing`() {
        val w = WifiStability()
        w.sample(-50, 866, 0)
        w.sample(-80, 100, 1_000)
        w.sample(-50, 866, 2_000)
        assertNull(w.unstable(2_000))
    }

    @Test
    fun `a steady link is stable and a swinging one is not`() {
        val steady = WifiStability()
        for (i in 0 until 5) steady.sample(-55 - i, 866, i * 1_000L)
        assertEquals(false, steady.unstable(5_000))
        val swinging = WifiStability()
        listOf(-55, -62, -70, -58).forEachIndexed { i, rssi -> swinging.sample(rssi, 866, i * 1_000L) }
        assertEquals(true, swinging.unstable(4_000))
    }

    @Test
    fun `a halved link speed counts only on a weak signal`() {
        val weak = WifiStability()
        listOf(-68 to 866, -70 to 866, -72 to 433, -74 to 390).forEachIndexed { i, (rssi, mbps) -> weak.sample(rssi, mbps, i * 1_000L) }
        assertEquals(true, weak.unstable(4_000))
        val strong = WifiStability()
        listOf(-50 to 866, -51 to 866, -52 to 433, -53 to 390).forEachIndexed { i, (rssi, mbps) -> strong.sample(rssi, mbps, i * 1_000L) }
        assertEquals(false, strong.unstable(4_000))
    }

    @Test
    fun `two losses in the window are unstable and old samples leave it`() {
        val w = WifiStability()
        w.lost(0)
        assertNull(w.unstable(1_000))
        w.lost(10_000)
        assertEquals(true, w.unstable(10_000))
        assertEquals(30_000L, w.nextExpiry())
        // The first loss left the window
        assertNull(w.unstable(30_000))
    }

    @Test
    fun `a weak cellular signal is a problem only after five seconds on the cellular route`() {
        val j = SignalJudge()
        j.route = Route.CELLULAR
        j.cell = CellReading(2, "LTE")
        assertEquals(NetworkSignal(CellStatus(2, "LTE", SignalSeverity.OK), null), j.state(0))
        assertEquals(5_000L, j.nextCheck())
        assertEquals(NetworkSignal(CellStatus(2, "LTE", SignalSeverity.WARN), SignalProblem.WEAK_CELLULAR), j.state(5_000))
        // Good again: the pill stays for ten more seconds
        j.cell = CellReading(4, "LTE")
        assertEquals(SignalProblem.WEAK_CELLULAR, j.state(6_000).problem)
        assertNull(j.state(16_000).problem)
        assertEquals(SignalSeverity.OK, j.state(16_000).cell!!.severity)
    }

    @Test
    fun `on Wi-Fi a weak cellular signal shows its bars without a problem`() {
        val j = SignalJudge()
        j.route = Route.WIFI
        j.cell = CellReading(1, "5G")
        j.state(0)
        assertEquals(NetworkSignal(CellStatus(1, "5G", SignalSeverity.OK), null), j.state(60_000))
    }

    @Test
    fun `no service is the error and outranks a weak signal`() {
        val j = SignalJudge()
        j.route = Route.NONE
        j.cell = CellReading(0, "LTE")
        j.state(0)
        assertEquals(NetworkSignal(CellStatus(0, "LTE", SignalSeverity.BAD), SignalProblem.NO_SERVICE), j.state(5_000))
    }

    @Test
    fun `a device without a modem shows no cellular indicator but still warns about Wi-Fi`() {
        val j = SignalJudge()
        j.route = Route.WIFI
        listOf(-55, -62, -72, -58).forEachIndexed { i, rssi -> j.wifi.sample(rssi, null, i * 1_000L) }
        assertEquals(NetworkSignal(null, null), j.state(3_000))
        assertEquals(NetworkSignal(null, SignalProblem.UNSTABLE_WIFI), j.state(8_000))
        // The samples left the window: unknown again, and the pill goes after its ten seconds
        assertEquals(SignalProblem.UNSTABLE_WIFI, j.state(40_000).problem)
        assertNull(j.state(50_000).problem)
    }
}
