package com.limelight.shell.data

import java.util.Locale
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PlaytimeTrackerTest {
    private var now = 0L
    private val totals = HashMap<PlaytimeKey, Long>()
    private val tracker = PlaytimeTracker({ now }, PlaytimeTracker.Sink { key, delta -> totals.merge(key, delta, Long::plus) })
    private val a = PlaytimeKey("p1", "pc", 1)

    @Test fun countsOnlyWhileConnected() {
        tracker.begin(a)
        now = 5_000 // connecting
        tracker.setConnected(true)
        now = 15_000
        tracker.end()
        assertEquals(10_000L, totals[a])
    }

    @Test fun pausesWhenConnectionDropsOrSuspended() {
        tracker.begin(a); tracker.setConnected(true)
        now = 10_000; tracker.setConnected(false)
        now = 50_000; tracker.setConnected(true)
        now = 60_000; tracker.setSuspended(true)
        now = 90_000; tracker.setSuspended(false)
        now = 95_000; tracker.end()
        assertEquals(25_000L, totals[a])
    }

    @Test fun flushWritesSlicesWithoutDoubleCounting() {
        tracker.begin(a); tracker.setConnected(true)
        now = 30_000; tracker.flush()
        assertEquals(30_000L, totals[a])
        tracker.flush()
        now = 40_000; tracker.end(); tracker.end()
        assertEquals(40_000L, totals[a])
        assertFalse(tracker.running)
    }

    @Test fun crashKeepsFlushedTimeAndNextSessionAddsOnly() {
        tracker.begin(a); tracker.setConnected(true)
        now = 30_000; tracker.flush()
        now = 50_000 // killed here: the 20 s after the last flush are lost, nothing else
        val next = PlaytimeTracker({ now }, PlaytimeTracker.Sink { key, delta -> totals.merge(key, delta, Long::plus) })
        next.begin(a); next.setConnected(true)
        now = 60_000; next.end()
        assertEquals(40_000L, totals[a])
    }

    @Test fun beginEndsThePreviousSessionAndKeysStaySeparate() {
        val b = PlaytimeKey("p2", "pc", 1)
        tracker.begin(a); tracker.setConnected(true)
        now = 10_000
        tracker.begin(b); tracker.setConnected(true)
        now = 25_000; tracker.end()
        assertEquals(10_000L, totals[a])
        assertEquals(15_000L, totals[b])
    }

    @Test fun nothingCountsWithoutSession() {
        tracker.setConnected(true); now = 10_000; tracker.end()
        assertTrue(totals.isEmpty())
    }

    @Test fun amountIsZeroHoursForNothingAndPicksUnit() {
        val min = 60_000L
        assertEquals(PlaytimeFormat.Amount(PlaytimeFormat.Amount.Unit.HOURS, 0), PlaytimeFormat.amount(0))
        assertEquals(PlaytimeFormat.Amount(PlaytimeFormat.Amount.Unit.MINUTES, 1), PlaytimeFormat.amount(5_000))
        assertEquals(PlaytimeFormat.Amount(PlaytimeFormat.Amount.Unit.MINUTES, 45), PlaytimeFormat.amount(45 * min))
        assertEquals(PlaytimeFormat.Amount(PlaytimeFormat.Amount.Unit.HOURS, 10), PlaytimeFormat.amount(60 * min))
        assertEquals(PlaytimeFormat.Amount(PlaytimeFormat.Amount.Unit.HOURS, 204), PlaytimeFormat.amount(20 * 60 * min + 24 * min))
        assertEquals(PlaytimeFormat.Amount(PlaytimeFormat.Amount.Unit.HOURS, 1000), PlaytimeFormat.amount(100 * 60 * min))
    }

    @Test fun decimalFollowsLocale() {
        assertEquals("20.4", PlaytimeFormat.decimal(204, Locale.ENGLISH))
        assertEquals("20,4", PlaytimeFormat.decimal(204, Locale("cs")))
        assertEquals("20,4", PlaytimeFormat.decimal(204, Locale.GERMAN))
    }
}
