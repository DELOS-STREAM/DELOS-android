package com.limelight.binding.video;

import org.junit.Test;

import static org.junit.Assert.*;

public class PacingStatsTest {
    private static final long MS = 1_000_000L;
    private static final long PERIOD = 16 * MS;

    @Test public void aPresentedFrameGivesOffsetSubmitAndDecoderToPresent() {
        PacingStats stats = new PacingStats();
        stats.drainWindow("phase-lock", true, 0, PERIOD, null);
        stats.onRelease(1000, 10 * MS, 30 * MS, 20 * MS, PERIOD);
        stats.onPresented(1000, 31 * MS);
        String line = stats.drainWindow("phase-lock", true, 1000 * MS, PERIOD, "draws=1");
        assertTrue(line, line.contains("released=1 drop=0 presented=1"));
        assertTrue(line, line.contains("late=0 unshown=0"));
        assertTrue(line, line.contains("offset avg/min/max=1.00ms/1.00ms/1.00ms"));
        assertTrue(line, line.contains("submit->present avg/max=11.00ms/11.00ms"));
        assertTrue(line, line.contains("decoder->present avg/max=21.00ms/21.00ms"));
        assertTrue(line, line.endsWith(" draws=1"));
    }

    @Test public void aFrameShownARefreshAfterItsTargetIsLateAndSkippedOnesAreUnshown() {
        PacingStats stats = new PacingStats();
        stats.onRelease(1, 0, 10 * MS, 5 * MS, PERIOD);
        stats.onRelease(2, 0, 26 * MS, 20 * MS, PERIOD);
        stats.onDrop();
        // Frame 1 never reached the display, frame 2 came a refresh late
        stats.onPresented(2, 26 * MS + PERIOD);
        String line = stats.drainWindow("fast-vsync", true, 0, PERIOD, null);
        assertTrue(line, line.contains("released=2 drop=1 presented=1"));
        assertTrue(line, line.contains("late=1 unshown=1"));
    }

    @Test public void anUnknownPresentationIsIgnored() {
        PacingStats stats = new PacingStats();
        stats.onRelease(1, 0, 10 * MS, 5 * MS, PERIOD);
        stats.onPresented(99, 20 * MS);
        String line = stats.drainWindow("phase-lock", true, 0, PERIOD, null);
        assertTrue(line, line.contains("presented=0"));
        // Still matched later
        stats.onPresented(1, 11 * MS);
        line = stats.drainWindow("phase-lock", true, 0, PERIOD, null);
        assertTrue(line, line.contains("presented=1"));
    }

    @Test public void sgsrOutputHasNoPresentationFigures() {
        PacingStats stats = new PacingStats();
        String line = stats.drainWindow("phase-lock", false, 0, PERIOD, null);
        assertTrue(line, line.contains("out=sgsr"));
        assertFalse(line, line.contains("offset"));
    }

    @Test public void repeatsAreRefreshesWithoutANewFrame() {
        assertEquals(60, PacingStats.repeats(1000 * MS, 1000 * MS / 120, 60));
        assertEquals(0, PacingStats.repeats(1000 * MS, 1000 * MS / 60, 61));
        assertEquals(0, PacingStats.repeats(0, PERIOD, 0));
        assertEquals(0, PacingStats.repeats(1000 * MS, 0, 0));
    }
}
