package com.limelight.binding.video;

import org.junit.Test;

import java.util.function.LongSupplier;

import static org.junit.Assert.*;

public class PhaseLockDriverTest {
    private static final long MS = 1_000_000L;
    private static final long US = 1_000L;
    private static final long BASE = 1_000 * MS;
    private static final long PERIOD = 16 * MS;

    private static final class Clock implements LongSupplier {
        long now = BASE;
        @Override public long getAsLong() { return now; }
    }

    private final Clock clock = new Clock();
    private final PhaseLockDriver driver = new PhaseLockDriver(clock);
    private final PhaseLockDriver.Snapshot out = new PhaseLockDriver.Snapshot();

    /** Frames every PERIOD up to BASE, the last one exactly at BASE: the next one is expected at BASE + PERIOD. */
    private void steadyCadence() {
        for (int i = 5; i >= 0; i--) driver.onFrameDequeued(BASE - i * PERIOD);
    }

    @Test public void aHeldFrameDrawsForTheRefreshHalfAPeriodEarly() {
        driver.onReleased(MS);
        driver.publishRefresh(BASE + 20 * MS, BASE + 18 * MS, PERIOD);
        driver.decide(true, 0, out);
        assertEquals(PhaseLockDriver.Action.DRAW, out.action);
        assertEquals(BASE + 20 * MS, out.targetNs);
        assertEquals(BASE + 20 * MS - PERIOD / 2, out.releaseTimestampNs);
        assertFalse(out.forced);
    }

    @Test public void aPredictedFrameParksAndJoinsTheSameRefresh() {
        steadyCadence();
        driver.onReleased(MS);
        clock.now = BASE + 2 * MS;
        // Next frame expected at BASE + 16 ms; latest = min(30 - 1 - 0.5, now + 16 - 0.5) = 17.5 ms
        driver.publishRefresh(BASE + 32 * MS, BASE + 30 * MS, PERIOD);
        driver.decide(false, 0, out);
        assertEquals(PhaseLockDriver.Action.WAIT_FRAME, out.action);
        assertEquals(BASE + 17 * MS + 500 * US, out.latestNs);

        clock.now = BASE + 17 * MS;
        driver.decide(true, 0, out);
        assertEquals(PhaseLockDriver.Action.DRAW, out.action);
        assertEquals(BASE + 32 * MS, out.targetNs);
    }

    @Test public void aParkedRefreshExpiresAtItsDeadlineAndTheLateFrameTakesTheNextOne() {
        steadyCadence();
        driver.onReleased(MS);
        clock.now = BASE + 2 * MS;
        driver.publishRefresh(BASE + 32 * MS, BASE + 30 * MS, PERIOD);
        driver.decide(false, 0, out);
        assertEquals(PhaseLockDriver.Action.WAIT_FRAME, out.action);

        clock.now = out.latestNs + 1;
        driver.decide(false, 0, out);
        assertEquals(PhaseLockDriver.Action.WAIT_REFRESH, out.action);

        // The frame comes after the deadline: it is kept, not submitted into the expired refresh
        clock.now = BASE + 29 * MS;
        driver.onFrameDequeued(clock.now);
        driver.decide(true, 0, out);
        assertEquals(PhaseLockDriver.Action.WAIT_REFRESH, out.action);

        driver.publishRefresh(BASE + 48 * MS, BASE + 46 * MS, PERIOD);
        clock.now = BASE + 31 * MS;
        driver.decide(true, 0, out);
        assertEquals(PhaseLockDriver.Action.DRAW, out.action);
        assertEquals(BASE + 48 * MS, out.targetNs);

        long[] c = new long[PhaseLockDriver.C_COUNT];
        driver.counters(c);
        assertEquals(1, c[PhaseLockDriver.C_DRAWS]);
        assertEquals(1, c[PhaseLockDriver.C_PARKS]);
        assertEquals(1, c[PhaseLockDriver.C_EXPIRES]);
    }

    @Test public void anEarlyWakeDoesNotConsumeTheParkedRefresh() {
        steadyCadence();
        driver.onReleased(MS);
        clock.now = BASE + 2 * MS;
        driver.publishRefresh(BASE + 32 * MS, BASE + 30 * MS, PERIOD);
        driver.decide(false, 0, out);
        clock.now = BASE + 10 * MS;
        driver.decide(false, 0, out);
        assertEquals(PhaseLockDriver.Action.WAIT_FRAME, out.action);
    }

    @Test public void withoutAMeasuredBudgetItNeverParks() {
        steadyCadence();
        clock.now = BASE + 2 * MS;
        driver.publishRefresh(BASE + 32 * MS, BASE + 30 * MS, PERIOD);
        driver.decide(false, 0, out);
        assertEquals(PhaseLockDriver.Action.WAIT_REFRESH, out.action);
    }

    @Test public void aHeldFrameIsForcedOutAfterRepeatedExpiries() {
        driver.onReleased(MS);
        // Every refresh's latest drawing time is already past when the renderer thread gets to it
        for (int i = 0; i < PhaseLockDriver.MAX_HELD_EXPIRIES; i++) {
            long refresh = BASE + i * PERIOD;
            driver.publishRefresh(refresh + 2 * MS, refresh + MS, PERIOD);
            clock.now = refresh + MS;
            driver.decide(true, 0, out);
        }
        assertEquals(PhaseLockDriver.Action.DRAW, out.action);
        assertTrue(out.forced);
        assertEquals(clock.now, out.releaseTimestampNs);
        long[] c = new long[PhaseLockDriver.C_COUNT];
        driver.counters(c);
        assertEquals(1, c[PhaseLockDriver.C_FORCED]);
    }

    @Test public void anUnconsumedRefreshIsReplacedAndCountedAsSkipped() {
        assertFalse(driver.publishRefresh(BASE + 20 * MS, BASE + 18 * MS, PERIOD));
        assertTrue(driver.publishRefresh(BASE + 36 * MS, BASE + 34 * MS, PERIOD));
        driver.decide(true, 0, out);
        assertEquals(BASE + 36 * MS, out.targetNs);
        long[] c = new long[PhaseLockDriver.C_COUNT];
        driver.counters(c);
        assertEquals(1, c[PhaseLockDriver.C_SKIPPED]);
    }

    @Test public void resetStartsANewEpochButKeepsTheTotals() {
        driver.onReleased(MS);
        driver.publishRefresh(BASE + 20 * MS, BASE + 18 * MS, PERIOD);
        driver.decide(true, 0, out);
        driver.reset();
        long[] c = new long[PhaseLockDriver.C_COUNT];
        driver.counters(c);
        assertEquals(1, c[PhaseLockDriver.C_DRAWS]);
        assertEquals(-1, c[PhaseLockDriver.C_WORK_NS]);
        // The same target is accepted again after the reset
        driver.publishRefresh(BASE + 20 * MS, BASE + 18 * MS, PERIOD);
        driver.decide(true, 0, out);
        assertEquals(PhaseLockDriver.Action.DRAW, out.action);
    }

    @Test public void theSgsrStageMovesTheLatestDrawingTimeEarlier() {
        steadyCadence();
        driver.onReleased(MS);
        clock.now = BASE + 2 * MS;
        driver.publishRefresh(BASE + 16 * MS, BASE + 14 * MS, PERIOD);
        driver.decide(false, 3 * MS, out);
        // 14 - (1 + 3) - 0.5: the predicted frame (16 ms) would be too late, so the refresh is not parked
        assertEquals(BASE + 9 * MS + 500 * US, out.latestNs);
        assertEquals(PhaseLockDriver.Action.WAIT_REFRESH, out.action);
    }

    @Test public void theBudgetIsCappedAtHalfARefreshAndUnknownStaysUnknown() {
        assertEquals(-1, PhaseLockDriver.budgetNs(-1, 0, PERIOD));
        assertEquals(2 * MS, PhaseLockDriver.budgetNs(-1, 2 * MS, PERIOD));
        assertEquals(3 * MS, PhaseLockDriver.budgetNs(MS, 2 * MS, PERIOD));
        assertEquals(PERIOD / 2, PhaseLockDriver.budgetNs(40 * MS, 2 * MS, PERIOD));
    }

    @Test public void theEarliestTimelineWithRoomIsPicked() {
        long[] deadlines = {BASE + 20 * MS, BASE + 2 * MS, BASE + 4 * MS};
        // 1 ms budget + 0.5 guard + 0.5 slack: 2 ms is not enough, 4 ms is
        assertEquals(2, PhaseLockDriver.pickTimeline(deadlines, 3, 0, BASE, MS));
        assertEquals(1, PhaseLockDriver.pickTimeline(deadlines, 3, 0, BASE, 0));
        // None has room: the preferred one
        assertEquals(0, PhaseLockDriver.pickTimeline(deadlines, 3, 0, BASE + 30 * MS, MS));
    }

    @Test public void publishedTimelinesUseTheMeasuredWork() {
        driver.onReleased(2 * MS);
        long[] present = {BASE + 10 * MS, BASE + 26 * MS};
        long[] deadline = {BASE + 3 * MS, BASE + 19 * MS};
        driver.publishTimelines(present, deadline, 2, 1, PERIOD, 0);
        driver.decide(true, 0, out);
        assertEquals(BASE + 26 * MS, out.targetNs);
    }

    @Test public void legacyTimingStartsFromTheHardwareVsync() {
        long present = PhaseLockDriver.legacyPresentationNs(BASE + MS, MS, PERIOD);
        assertEquals(BASE + PERIOD, present);
        assertEquals(BASE + PERIOD - 5 * MS, PhaseLockDriver.legacyDeadlineNs(present, 5 * MS));
        assertEquals(present, PhaseLockDriver.legacyDeadlineNs(present, -1));
    }

    @Test public void theCadenceIgnoresStallsAndGoesQuietWhenTheStreamStops() {
        PhaseLockDriver.ArrivalCadence cadence = new PhaseLockDriver.ArrivalCadence();
        assertEquals(PhaseLockPacer.UNKNOWN_FRAME_TIME, cadence.expectedNextNs(BASE));
        for (int i = 0; i < 5; i++) cadence.onFrame(BASE + i * PERIOD);
        long last = BASE + 4 * PERIOD;
        assertEquals(last + PERIOD, cadence.expectedNextNs(last));
        // A 200 ms stall does not stretch the interval
        cadence.onFrame(last + 200 * MS);
        assertEquals(PERIOD, cadence.intervalNs());
        assertEquals(PhaseLockPacer.UNKNOWN_FRAME_TIME, cadence.expectedNextNs(last + 200 * MS + 3 * PERIOD));
    }

    @Test public void theWorkBudgetHoldsItsPeakAndDecaysSlowly() {
        PhaseLockDriver.WorkBudget work = new PhaseLockDriver.WorkBudget();
        assertEquals(-1, work.valueNs());
        work.sample(4 * MS);
        work.sample(0);
        assertEquals(4 * MS - 4 * MS / 16, work.valueNs());
        work.sample(8 * MS);
        assertEquals(8 * MS, work.valueNs());
        work.sample(-5);
        assertEquals(8 * MS, work.valueNs());
    }
}
