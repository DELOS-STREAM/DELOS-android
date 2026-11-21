package com.limelight.binding.video;

import java.util.function.LongSupplier;

/**
 * Allocation-free decisions for one display refresh. All times use the same monotonic nanosecond clock.
 * The owner serializes calls, snapshots target/latest while serialized, then releases codec/EGL objects outside
 * its state lock. This model never owns buffers, platform objects, threads, or a blocking wait.
 */
final class PhaseLockPacer {
    enum Decision { DRAW, PARK, EXPIRE }

    static final long GUARD_NS = 500_000L;
    static final long UNKNOWN_FRAME_TIME = Long.MIN_VALUE;

    private final LongSupplier clock;
    private long targetNs;
    private long latestNs;
    private long lastRefreshNs = Long.MIN_VALUE;
    private boolean parked;
    private long draws;
    private long parks;
    private long expirations;

    PhaseLockPacer() {
        this(System::nanoTime);
    }

    PhaseLockPacer(LongSupplier clock) {
        this.clock = clock;
    }

    /**
     * Latest safe submission: measured rendering work and a guard before the earlier CPU/presentation deadline,
     * and never beyond the next callback. Negative work means no measurement yet: draw now, but do not park.
     * A result before now is deliberately not clamped forward; an expired refresh cannot gain a fake budget.
     */
    static long latestDrawingTimeNs(long presentationNs, long cpuDeadlineNs, long refreshPeriodNs,
                                    long measuredRenderWorkNs, long nowNs) {
        if (refreshPeriodNs <= GUARD_NS) return nowNs - 1;
        long deadline = Math.min(presentationNs, cpuDeadlineNs);
        if (deadline - nowNs <= GUARD_NS) return nowNs - 1;
        if (measuredRenderWorkNs < 0) return nowNs;
        return Math.min(deadline - measuredRenderWorkNs - GUARD_NS, nowNs + refreshPeriodNs - GUARD_NS);
    }

    /**
     * A new refresh arrives. frameReady means the owner already has a decoded frame; otherwise park only when
     * its next frame is predicted by latest. UNKNOWN_FRAME_TIME never invents an arrival prediction. A callback
     * for an already-seen target cannot submit a second picture or replace a still-valid parked refresh.
     */
    Decision beginRefresh(long presentationNs, long cpuDeadlineNs, long refreshPeriodNs,
                          long measuredRenderWorkNs, boolean frameReady, long expectedFrameNs) {
        if (presentationNs <= lastRefreshNs) return Decision.PARK;
        if (parked) expireParked();
        long now = clock.getAsLong();
        lastRefreshNs = presentationNs;
        targetNs = presentationNs;
        latestNs = latestDrawingTimeNs(presentationNs, cpuDeadlineNs, refreshPeriodNs, measuredRenderWorkNs, now);
        if (now > latestNs) return expireCurrent();
        if (frameReady) return draw();
        if (now < latestNs && expectedFrameNs != UNKNOWN_FRAME_TIME && expectedFrameNs <= latestNs) {
            parked = true;
            parks++;
            return Decision.PARK;
        }
        return expireCurrent();
    }

    /** A newly decoded frame may join the parked refresh, including exactly at its latest safe submission time. */
    Decision frameArrived() {
        if (!parked) return Decision.PARK;
        return clock.getAsLong() <= latestNs ? draw() : expireCurrent();
    }

    /** A deadline wake-up may expire a parked refresh; an early wake-up must not consume it. */
    Decision expire() {
        if (parked && clock.getAsLong() > latestNs) return expireCurrent();
        return Decision.PARK;
    }

    private Decision draw() {
        parked = false;
        draws++;
        return Decision.DRAW;
    }

    private void expireParked() {
        parked = false;
        expirations++;
    }

    private Decision expireCurrent() {
        parked = false;
        expirations++;
        return Decision.EXPIRE;
    }

    /** Flush/recovery/display reattachment starts a new timing epoch; the owner drains its buffers separately. */
    void reset() {
        targetNs = latestNs = 0;
        lastRefreshNs = Long.MIN_VALUE;
        parked = false;
        draws = parks = expirations = 0;
    }

    long targetTimeNs() { return targetNs; }
    long latestDrawingTimeNs() { return latestNs; }
    boolean isParked() { return parked; }
    long drawDecisions() { return draws; }
    long parkedRefreshes() { return parks; }
    /** Refresh decisions with no new picture, not measured platform presentation/repeat callbacks. */
    long expiredRefreshes() { return expirations; }
}
