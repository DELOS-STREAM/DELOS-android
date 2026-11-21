package com.limelight.binding.video;

import org.junit.Test;

import java.util.function.LongSupplier;

import static org.junit.Assert.*;

public class PhaseLockPacerTest {
    private static final long MS = 1_000_000L;
    private static final long BASE = 100 * MS;
    private static final long PERIOD = 16 * MS;

    private static final class Clock implements LongSupplier {
        long now = BASE;
        @Override public long getAsLong() { return now; }
    }

    @Test public void latestSubtractsMeasuredWorkAndGuardFromTheEarlierDeadline() {
        assertEquals(BASE + 11 * MS + MS / 2,
                PhaseLockPacer.latestDrawingTimeNs(BASE + PERIOD, BASE + 14 * MS, PERIOD, 2 * MS, BASE));
        assertEquals(BASE + PERIOD - PhaseLockPacer.GUARD_NS,
                PhaseLockPacer.latestDrawingTimeNs(BASE + 3 * PERIOD, BASE + 3 * PERIOD, PERIOD, 0, BASE));
        assertTrue(PhaseLockPacer.latestDrawingTimeNs(BASE + PERIOD, BASE + MS, PERIOD, 2 * MS, BASE) < BASE);
    }

    @Test public void anAlreadyQueuedFrameDrawsWithoutWaiting() {
        Clock clock = new Clock();
        PhaseLockPacer pacer = new PhaseLockPacer(clock);
        assertEquals(PhaseLockPacer.Decision.DRAW,
                pacer.beginRefresh(BASE + PERIOD, BASE + 14 * MS, PERIOD, 2 * MS, true, PhaseLockPacer.UNKNOWN_FRAME_TIME));
        assertEquals(BASE + PERIOD, pacer.targetTimeNs());
        assertEquals(1, pacer.drawDecisions());
        assertFalse(pacer.isParked());
    }

    @Test public void aFrameCanJoinTheParkedRefreshExactlyAtLatest() {
        Clock clock = new Clock();
        PhaseLockPacer pacer = new PhaseLockPacer(clock);
        assertEquals(PhaseLockPacer.Decision.PARK,
                pacer.beginRefresh(BASE + PERIOD, BASE + 14 * MS, PERIOD, 2 * MS, false, BASE + 8 * MS));
        assertTrue(pacer.isParked());
        clock.now = pacer.latestDrawingTimeNs();
        assertEquals(PhaseLockPacer.Decision.DRAW, pacer.frameArrived());
        assertEquals(BASE + PERIOD, pacer.targetTimeNs());
        assertEquals(PhaseLockPacer.Decision.PARK, pacer.frameArrived());
        assertEquals(1, pacer.drawDecisions());
    }

    @Test public void aLateArrivalExpiresInsteadOfDrawingIntoAMissedRefresh() {
        Clock clock = new Clock();
        PhaseLockPacer pacer = new PhaseLockPacer(clock);
        pacer.beginRefresh(BASE + PERIOD, BASE + 14 * MS, PERIOD, 2 * MS, false, BASE + 8 * MS);
        clock.now = pacer.latestDrawingTimeNs() + 1;
        assertEquals(PhaseLockPacer.Decision.EXPIRE, pacer.frameArrived());
        assertEquals(0, pacer.drawDecisions());
        assertEquals(1, pacer.expiredRefreshes());
        assertEquals(PhaseLockPacer.Decision.PARK, pacer.frameArrived());
        assertEquals(PhaseLockPacer.Decision.DRAW,
                pacer.beginRefresh(BASE + 2 * PERIOD, BASE + 30 * MS, PERIOD, 2 * MS, true, PhaseLockPacer.UNKNOWN_FRAME_TIME));
        assertEquals(BASE + 2 * PERIOD, pacer.targetTimeNs());
    }

    @Test public void anEarlyDeadlineWakeDoesNotConsumeTheRefreshAndExpirationIsCountedOnce() {
        Clock clock = new Clock();
        PhaseLockPacer pacer = new PhaseLockPacer(clock);
        pacer.beginRefresh(BASE + PERIOD, BASE + 14 * MS, PERIOD, 2 * MS, false, BASE + 8 * MS);
        clock.now = pacer.latestDrawingTimeNs();
        assertEquals(PhaseLockPacer.Decision.PARK, pacer.expire());
        assertTrue(pacer.isParked());
        clock.now++;
        assertEquals(PhaseLockPacer.Decision.EXPIRE, pacer.expire());
        assertEquals(PhaseLockPacer.Decision.PARK, pacer.expire());
        assertEquals(1, pacer.expiredRefreshes());
    }

    @Test public void noPredictionOrAnArrivalAfterLatestNeverCreatesAnUnboundedWait() {
        Clock clock = new Clock();
        PhaseLockPacer pacer = new PhaseLockPacer(clock);
        assertEquals(PhaseLockPacer.Decision.EXPIRE,
                pacer.beginRefresh(BASE + PERIOD, BASE + 14 * MS, PERIOD, 2 * MS, false, PhaseLockPacer.UNKNOWN_FRAME_TIME));
        assertEquals(PhaseLockPacer.Decision.EXPIRE,
                pacer.beginRefresh(BASE + 2 * PERIOD, BASE + 30 * MS, PERIOD, 2 * MS, false, BASE + 4 * PERIOD));
        assertFalse(pacer.isParked());
    }

    @Test public void unknownWorkCanDrawNowButCannotParkAndExpiredBudgetsStayExpired() {
        Clock clock = new Clock();
        PhaseLockPacer pacer = new PhaseLockPacer(clock);
        assertEquals(PhaseLockPacer.Decision.EXPIRE,
                pacer.beginRefresh(BASE + PERIOD, BASE + 14 * MS, PERIOD, -1, false, BASE + MS));
        assertEquals(PhaseLockPacer.Decision.DRAW,
                pacer.beginRefresh(BASE + 2 * PERIOD, BASE + 30 * MS, PERIOD, -1, true, PhaseLockPacer.UNKNOWN_FRAME_TIME));
        clock.now = BASE + 3 * PERIOD;
        assertEquals(PhaseLockPacer.Decision.EXPIRE,
                pacer.beginRefresh(clock.now + 1, clock.now + 1, PERIOD, 0, true, PhaseLockPacer.UNKNOWN_FRAME_TIME));
        assertEquals(1, pacer.drawDecisions());
    }

    @Test public void duplicateCallbacksNeitherResetTheWindowNorDrawTheTargetTwice() {
        Clock clock = new Clock();
        PhaseLockPacer pacer = new PhaseLockPacer(clock);
        pacer.beginRefresh(BASE + PERIOD, BASE + 14 * MS, PERIOD, 2 * MS, false, BASE + 8 * MS);
        long latest = pacer.latestDrawingTimeNs();
        clock.now += MS;
        assertEquals(PhaseLockPacer.Decision.PARK,
                pacer.beginRefresh(BASE + PERIOD, BASE + 15 * MS, PERIOD, 0, true, BASE + MS));
        assertEquals(latest, pacer.latestDrawingTimeNs());
        assertEquals(1, pacer.parkedRefreshes());
        assertEquals(PhaseLockPacer.Decision.DRAW, pacer.frameArrived());
        assertEquals(PhaseLockPacer.Decision.PARK,
                pacer.beginRefresh(BASE + PERIOD, BASE + 15 * MS, PERIOD, 0, true, BASE + MS));
        assertEquals(1, pacer.drawDecisions());
    }

    @Test public void aNewRefreshExpiresTheOldParkAndResetStartsANewEpoch() {
        Clock clock = new Clock();
        PhaseLockPacer pacer = new PhaseLockPacer(clock);
        pacer.beginRefresh(BASE + PERIOD, BASE + 14 * MS, PERIOD, 2 * MS, false, BASE + 8 * MS);
        clock.now += PERIOD;
        assertEquals(PhaseLockPacer.Decision.PARK,
                pacer.beginRefresh(BASE + 2 * PERIOD, BASE + 30 * MS, PERIOD, 2 * MS, false, clock.now + MS));
        assertEquals(1, pacer.expiredRefreshes());
        pacer.reset();
        assertFalse(pacer.isParked());
        assertEquals(0, pacer.expiredRefreshes());
        assertEquals(0, pacer.drawDecisions());
        assertEquals(0, pacer.parkedRefreshes());
        clock.now = BASE;
        assertEquals(PhaseLockPacer.Decision.DRAW,
                pacer.beginRefresh(BASE + PERIOD, BASE + 14 * MS, PERIOD, 2 * MS, true, PhaseLockPacer.UNKNOWN_FRAME_TIME));
    }
}
