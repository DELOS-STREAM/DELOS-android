package com.limelight.shell;

import static com.limelight.shell.CompactStatsHealth.Level.*;
import static org.junit.Assert.assertEquals;

import org.junit.Test;

public class CompactStatsHealthTest {
    @Test public void networkAndLossWarnAtTheThreshold() {
        assertEquals(GOOD, CompactStatsHealth.network(59));
        assertEquals(WARNING, CompactStatsHealth.network(60));
        assertEquals(WARNING, CompactStatsHealth.network(99));
        assertEquals(ERROR, CompactStatsHealth.network(100));
        assertEquals(GOOD, CompactStatsHealth.loss(1.49f));
        assertEquals(WARNING, CompactStatsHealth.loss(1.5f));
        assertEquals(WARNING, CompactStatsHealth.loss(4.99f));
        assertEquals(ERROR, CompactStatsHealth.loss(5f));
    }

    @Test public void fpsUsesRenderedRateRelativeToSentRate() {
        assertEquals(GOOD, CompactStatsHealth.fps(0, 0));
        assertEquals(GOOD, CompactStatsHealth.fps(85, 100));
        assertEquals(WARNING, CompactStatsHealth.fps(84, 100));
        assertEquals(WARNING, CompactStatsHealth.fps(60, 100));
        assertEquals(ERROR, CompactStatsHealth.fps(59, 100));
    }

    @Test public void onePercentLowWarnsOnlyForKnownRatesBelowSixtyPercent() {
        assertEquals(GOOD, CompactStatsHealth.low(-1, 100));
        assertEquals(GOOD, CompactStatsHealth.low(0, 0));
        assertEquals(GOOD, CompactStatsHealth.low(60, 100));
        assertEquals(WARNING, CompactStatsHealth.low(59, 100));
    }
}
