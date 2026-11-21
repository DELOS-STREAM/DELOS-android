package com.limelight.binding.video;

import java.util.function.LongSupplier;

/**
 * Glue between the display refresh callbacks, the decoder output and {@link PhaseLockPacer}. The Choreographer thread
 * publishes refresh deadlines here; the renderer thread reports decoded frames and releases, and asks for a decision.
 * The monitor of this object is the phase-lock state lock: every method holds it for a few assignments and never
 * touches a codec, a Surface or EGL, so the caller releases buffers after the call, outside the lock.
 * Allocation-free; all times share {@code System.nanoTime()}.
 */
final class PhaseLockDriver {
    enum Action {
        /** Release the held frame now, with {@link Snapshot#releaseTimestampNs}. */
        DRAW,
        /** A refresh is parked for a predicted frame: wait for a decoded frame until {@link Snapshot#latestNs}. */
        WAIT_FRAME,
        /** Nothing to submit: keep any held frame and wait for the next refresh (or for a frame when none is held). */
        WAIT_REFRESH
    }

    /** Owner-provided result holder, filled while the lock is held so the release happens on a copy. */
    static final class Snapshot {
        Action action = Action.WAIT_REFRESH;
        long targetNs;
        long latestNs;
        long periodNs;
        long releaseTimestampNs;
        /** The held frame missed {@link #MAX_HELD_EXPIRIES} refreshes in a row and is shown at once. */
        boolean forced;
    }

    /** Counter slots of {@link #counters(long[])}. */
    static final int C_DRAWS = 0, C_PARKS = 1, C_EXPIRES = 2, C_SKIPPED = 3, C_FORCED = 4, C_WORK_NS = 5, C_COUNT = 6;

    /** A held frame is never kept back for more refreshes than this: a frame expiring again and again is shown late. */
    static final int MAX_HELD_EXPIRIES = 2;
    /** Extra room a timeline must leave for the renderer thread's wake-up after the callback picked it. */
    static final long PICK_SLACK_NS = PhaseLockPacer.GUARD_NS;

    private final LongSupplier clock;
    private final PhaseLockPacer pacer;
    private final ArrivalCadence cadence = new ArrivalCadence();
    private final WorkBudget work = new WorkBudget();

    private boolean refreshPending;
    private long pendingPresentNs, pendingDeadlineNs, pendingPeriodNs;
    private long periodNs;
    private int heldExpiries;
    // Totals that survive reset(): the pacer clears its own counters at every new epoch
    private long draws, parks, expires, skipped, forced;

    PhaseLockDriver() {
        this(System::nanoTime);
    }

    PhaseLockDriver(LongSupplier clock) {
        this.clock = clock;
        this.pacer = new PhaseLockPacer(clock);
    }

    /**
     * A refresh with its presentation time and CPU deadline. A refresh still pending (the renderer thread did not get
     * to it) is replaced and counted as skipped. Returns true when one was skipped.
     */
    synchronized boolean publishRefresh(long presentNs, long deadlineNs, long refreshPeriodNs) {
        boolean skip = refreshPending;
        if (skip) skipped++;
        refreshPending = true;
        pendingPresentNs = presentNs;
        pendingDeadlineNs = deadlineNs;
        pendingPeriodNs = refreshPeriodNs;
        return skip;
    }

    /**
     * Publishes the earliest of the platform's frame timelines that still leaves room for the measured
     * work, see {@link #pickTimeline}. {@code extraWorkNs} is the output stage's time (SGSR frame-to-swap).
     */
    synchronized boolean publishTimelines(long[] presentNs, long[] deadlineNs, int count, int preferred,
                                          long refreshPeriodNs, long extraWorkNs) {
        if (count <= 0) return false;
        int i = pickTimeline(deadlineNs, count, preferred, clock.getAsLong(),
                budgetNs(work.valueNs(), extraWorkNs, refreshPeriodNs));
        return publishRefresh(presentNs[i], deadlineNs[i], refreshPeriodNs);
    }

    /** A decoded frame left the codec at {@code nowNs}: feeds the arrival cadence. */
    synchronized void onFrameDequeued(long nowNs) {
        cadence.onFrame(nowNs);
    }

    /** The release of a drawn frame took {@code workNs} (from frame or wake-up to the release's return). */
    synchronized void onReleased(long workNs) {
        work.sample(workNs);
    }

    /**
     * One decision for the renderer thread. A parked refresh is served first (the held frame joins it or the deadline
     * expires it); otherwise a pending refresh begins. An expired refresh keeps the held frame for the next one,
     * but not forever: after {@link #MAX_HELD_EXPIRIES} it is drawn at once ({@link Snapshot#forced}).
     */
    synchronized void decide(boolean frameHeld, long extraWorkNs, Snapshot out) {
        PhaseLockPacer.Decision d = null;
        if (pacer.isParked()) d = frameHeld ? pacer.frameArrived() : pacer.expire();
        if (d != PhaseLockPacer.Decision.DRAW && refreshPending) {
            refreshPending = false;
            periodNs = pendingPeriodNs;
            long now = clock.getAsLong();
            d = pacer.beginRefresh(pendingPresentNs, pendingDeadlineNs, pendingPeriodNs,
                    budgetNs(work.valueNs(), extraWorkNs, pendingPeriodNs), frameHeld, cadence.expectedNextNs(now));
            if (d == PhaseLockPacer.Decision.EXPIRE && frameHeld) heldExpiries++;
        }
        out.targetNs = pacer.targetTimeNs();
        out.latestNs = pacer.latestDrawingTimeNs();
        out.periodNs = periodNs;
        out.forced = false;
        if (d == PhaseLockPacer.Decision.DRAW) {
            heldExpiries = 0;
            out.action = Action.DRAW;
            out.releaseTimestampNs = releaseTimestampNs(out.targetNs, periodNs);
        } else if (frameHeld && heldExpiries >= MAX_HELD_EXPIRIES) {
            heldExpiries = 0;
            forced++;
            out.action = Action.DRAW;
            out.forced = true;
            out.releaseTimestampNs = clock.getAsLong();
        } else {
            out.action = pacer.isParked() ? Action.WAIT_FRAME : Action.WAIT_REFRESH;
        }
    }

    /** Flush, recovery or a new output surface: a new timing epoch. The caller forgets its held buffer itself. */
    synchronized void reset() {
        accumulate();
        pacer.reset();
        cadence.reset();
        work.reset();
        refreshPending = false;
        heldExpiries = 0;
    }

    /** Cumulative counters since creation (they survive reset) and the current work budget, see the C_ slots. */
    synchronized void counters(long[] out) {
        out[C_DRAWS] = draws + pacer.drawDecisions();
        out[C_PARKS] = parks + pacer.parkedRefreshes();
        out[C_EXPIRES] = expires + pacer.expiredRefreshes();
        out[C_SKIPPED] = skipped;
        out[C_FORCED] = forced;
        out[C_WORK_NS] = work.valueNs();
    }

    private void accumulate() {
        draws += pacer.drawDecisions();
        parks += pacer.parkedRefreshes();
        expires += pacer.expiredRefreshes();
    }

    /**
     * The work budget handed to the pacer: measured release work plus the output stage, at most half a refresh (a
     * wrong or stale peak can never starve every refresh), or unknown (-1) while nothing was measured.
     */
    static long budgetNs(long measuredWorkNs, long extraWorkNs, long refreshPeriodNs) {
        long extra = Math.max(0L, extraWorkNs);
        if (measuredWorkNs < 0 && extra == 0) return -1;
        long total = Math.max(0L, measuredWorkNs) + extra;
        return refreshPeriodNs > 0 ? Math.min(total, refreshPeriodNs / 2) : total;
    }

    /**
     * The earliest timeline whose deadline still leaves the budget, the pacer's guard and the wake-up slack after
     * {@code nowNs}; the platform's preferred one when none does. The timelines may come in any order.
     */
    static int pickTimeline(long[] deadlineNs, int count, int preferred, long nowNs, long budgetNs) {
        long need = Math.max(0L, budgetNs) + PhaseLockPacer.GUARD_NS + PICK_SLACK_NS;
        int best = -1;
        for (int i = 0; i < count; i++) {
            if (deadlineNs[i] - nowNs > need && (best < 0 || deadlineNs[i] < deadlineNs[best])) best = i;
        }
        return best >= 0 ? best : Math.max(0, Math.min(preferred, count - 1));
    }

    /** Refresh timing without FrameData: the hardware vsync of the callback plus a period, the deadline before it. */
    static long legacyPresentationNs(long frameTimeNanos, long appVsyncOffsetNs, long refreshPeriodNs) {
        return frameTimeNanos - appVsyncOffsetNs + refreshPeriodNs;
    }

    static long legacyDeadlineNs(long presentationNs, long presentationDeadlineNs) {
        return presentationNs - Math.max(0L, presentationDeadlineNs);
    }

    /**
     * The timestamp a frame aimed at {@code targetNs} is released (or presented) with: half a refresh before it.
     * SurfaceFlinger shows a buffer at the first refresh whose expected present time is not before the timestamp,
     * so half a period pins it to the target refresh, neither the one before nor, with timing jitter, the one after.
     */
    static long releaseTimestampNs(long targetNs, long refreshPeriodNs) {
        return targetNs - Math.max(0L, refreshPeriodNs) / 2;
    }

    /** When the stream's frames come out of the decoder: a smoothed interval and the next expected arrival. */
    static final class ArrivalCadence {
        static final int MIN_SAMPLES = 4;
        private long lastNs;
        private boolean seen;
        private long intervalNs;
        private int samples;

        void onFrame(long nowNs) {
            if (seen) {
                long delta = nowNs - lastNs;
                if (delta > 0) {
                    if (samples == 0) {
                        intervalNs = delta;
                        samples = 1;
                    } else if (delta <= 4 * intervalNs) {
                        // A stall (a lost frame, a host hiccup) is not a cadence: it does not bend the interval
                        intervalNs += (delta - intervalNs) / 8;
                        samples++;
                    }
                }
            }
            lastNs = nowNs;
            seen = true;
        }

        /** The next arrival, or UNKNOWN_FRAME_TIME while unmeasured or when the stream stopped for two intervals. */
        long expectedNextNs(long nowNs) {
            if (samples < MIN_SAMPLES || intervalNs <= 0) return PhaseLockPacer.UNKNOWN_FRAME_TIME;
            if (nowNs - lastNs > 2 * intervalNs) return PhaseLockPacer.UNKNOWN_FRAME_TIME;
            return lastNs + intervalNs;
        }

        long intervalNs() { return intervalNs; }

        void reset() {
            seen = false;
            lastNs = intervalNs = 0;
            samples = 0;
        }
    }

    /** A conservative work estimate: the peak of the samples, decaying by a sixteenth toward each lower one. */
    static final class WorkBudget {
        private long peakNs = -1;

        void sample(long ns) {
            if (ns < 0) return;
            if (peakNs < 0 || ns >= peakNs) peakNs = ns;
            else peakNs -= (peakNs - ns) / 16;
        }

        /** -1 while nothing was measured. */
        long valueNs() { return peakNs; }

        void reset() { peakNs = -1; }
    }
}
