package com.limelight.binding.video;

import java.util.Locale;

/**
 * Once-a-second pacing figures of Fast V-Sync and Phase lock in one format, so the two can be compared on a device
 * ({@code adb logcat -s DelosPacing}). The renderer thread reports every decoded frame it dropped and every frame it
 * released with its target refresh; the codec's frame-rendered callback reports when a released frame was presented
 * (direct output only: with SGSR it marks the hand-over to the GL thread, so those figures are left out and the
 * DelosLatency stages show the presentation). A preallocated ring matches the two by PTS; only the summary line
 * allocates. Methods are synchronized: the lock is held for a few assignments.
 */
final class PacingStats {
    static final String TAG = "DelosPacing";
    private static final int RING = 32;

    private final long[] ptsUs = new long[RING];
    private final long[] dequeueNs = new long[RING];
    private final long[] targetNs = new long[RING];
    private final long[] releaseNs = new long[RING];
    private int head, count;

    private long windowStartNs = Long.MIN_VALUE;
    private long periodNs;
    private int released, dropped, presented, late, unshown;
    private long offsetSum, offsetMin, offsetMax;
    private long submitSum, submitMax;
    private long latencySum, latencyMax;

    PacingStats() {
        clearWindow();
    }

    /** A decoded frame was replaced by a newer one before it was released. */
    synchronized void onDrop() {
        dropped++;
    }

    /** A frame left the codec at {@code frameDequeueNs} and was released at {@code frameReleaseNs} for {@code frameTargetNs}. */
    synchronized void onRelease(long pts, long frameDequeueNs, long frameTargetNs, long frameReleaseNs, long refreshPeriodNs) {
        released++;
        if (refreshPeriodNs > 0) periodNs = refreshPeriodNs;
        if (count == RING) {
            head = (head + 1) % RING;
            count--;
        }
        int tail = (head + count) % RING;
        ptsUs[tail] = pts;
        dequeueNs[tail] = frameDequeueNs;
        targetNs[tail] = frameTargetNs;
        releaseNs[tail] = frameReleaseNs;
        count++;
    }

    /**
     * The codec presented the frame with this PTS at {@code presentNs}. Released frames before it that were never
     * presented were dropped downstream (a newer one took their refresh) and count as unshown. A presentation more
     * than half a refresh after the target missed its refresh and counts as late.
     */
    synchronized void onPresented(long pts, long presentNs) {
        int skippedBefore = 0;
        for (int i = 0; i < count; i++) {
            int slot = (head + i) % RING;
            if (ptsUs[slot] != pts) {
                skippedBefore++;
                continue;
            }
            presented++;
            unshown += skippedBefore;
            long offset = presentNs - targetNs[slot];
            offsetSum += offset;
            offsetMin = Math.min(offsetMin, offset);
            offsetMax = Math.max(offsetMax, offset);
            if (periodNs > 0 && offset > periodNs / 2) late++;
            long submit = presentNs - releaseNs[slot];
            submitSum += submit;
            submitMax = Math.max(submitMax, submit);
            long latency = presentNs - dequeueNs[slot];
            latencySum += latency;
            latencyMax = Math.max(latencyMax, latency);
            head = (slot + 1) % RING;
            count -= i + 1;
            return;
        }
    }

    /** Refreshes of the window without a newly presented frame: elapsed refreshes minus presented frames. */
    static long repeats(long windowNs, long refreshPeriodNs, long presentedFrames) {
        if (refreshPeriodNs <= 0 || windowNs <= 0) return 0;
        long refreshes = (windowNs + refreshPeriodNs / 2) / refreshPeriodNs;
        return Math.max(0, refreshes - presentedFrames);
    }

    /**
     * The window's line, then a new window. {@code presentKnown} is false with SGSR (no presentation callback per
     * frame); {@code extra} is appended as is (Phase lock's decision counters), may be null.
     */
    synchronized String drainWindow(String mode, boolean presentKnown, long nowNs, long displayPeriodNs, String extra) {
        long windowNs = windowStartNs == Long.MIN_VALUE ? 0 : nowNs - windowStartNs;
        long period = displayPeriodNs > 0 ? displayPeriodNs : periodNs;
        StringBuilder line = new StringBuilder(256);
        line.append("mode=").append(mode)
                .append(" out=").append(presentKnown ? "direct" : "sgsr")
                .append(" win=").append(ms(windowNs))
                .append(" period=").append(ms(period))
                .append(" released=").append(released)
                .append(" drop=").append(dropped);
        if (presentKnown) {
            line.append(" presented=").append(presented)
                    .append(" repeat=").append(repeats(windowNs, period, presented))
                    .append(" late=").append(late)
                    .append(" unshown=").append(unshown);
            if (presented > 0) {
                line.append(" offset avg/min/max=").append(ms(offsetSum / presented)).append('/')
                        .append(ms(offsetMin)).append('/').append(ms(offsetMax))
                        .append(" submit->present avg/max=").append(ms(submitSum / presented)).append('/').append(ms(submitMax))
                        .append(" decoder->present avg/max=").append(ms(latencySum / presented)).append('/').append(ms(latencyMax));
            }
        } else {
            line.append(" repeat=").append(repeats(windowNs, period, released))
                    .append(" (present: DelosLatency avail->present)");
        }
        if (extra != null) line.append(' ').append(extra);
        windowStartNs = nowNs;
        clearWindow();
        return line.toString();
    }

    private void clearWindow() {
        released = dropped = presented = late = unshown = 0;
        offsetSum = submitSum = latencySum = 0;
        offsetMin = Long.MAX_VALUE;
        offsetMax = Long.MIN_VALUE;
        submitMax = latencyMax = Long.MIN_VALUE;
    }

    static String ms(long ns) {
        return String.format(Locale.US, "%.2fms", ns / 1_000_000.0);
    }
}
