package com.limelight.binding.audio;

import org.junit.Test;
import static org.junit.Assert.*;

public class VolumeLevelsTest {
    @Test public void stepsClampWithoutExposingBoost() {
        assertEquals(0, VolumeLevels.clamp(-1));
        assertEquals(15, VolumeLevels.clamp(16));
        assertEquals(25, VolumeLevels.toIndex(99, 25));
        assertEquals(0, VolumeLevels.toIndex(-5, 25));
    }

    @Test public void hardwareRangesKeepTheSameNormalizedScale() {
        assertEquals(15, VolumeLevels.toIndex(9, 25));
        assertEquals(9, VolumeLevels.fromIndex(15, 25));
        assertEquals(15, VolumeLevels.fromIndex(100, 25));
        assertEquals(0, VolumeLevels.fromIndex(0, 0));
    }

    @Test public void unknownFirstLaunchLeavesSoundUnchanged() {
        assertEquals(1f, VolumeLevels.gain(9, -1, 15, false, index -> {
            throw new AssertionError("unknown reference must not query an invented curve");
        }), 0f);
    }

    @Test public void decibelDifferenceControlsAmplitudeNotLinearIndex() {
        assertEquals((float) Math.sqrt(10), VolumeLevels.gain(10, 5, 15, false, index -> -60 + index * 2), .0001f);
        assertEquals((float) (1 / Math.sqrt(10)), VolumeLevels.gain(5, 10, 15, false, index -> -60 + index * 2), .0001f);
    }

    @Test public void mutedOutputStaysQuietUntilExplicitVolumeUp() {
        assertEquals(0f, VolumeLevels.gain(9, 0, 15, false, index -> {
            throw new AssertionError("do not evaluate negative infinity for a muted reference");
        }), 0f);
        float gain = VolumeLevels.gain(10, 0, 15, true, index -> index == 0 ? Double.NEGATIVE_INFINITY : -60 + index * 2);
        assertEquals((float) Math.pow(10, 18 / 20.0), gain, .0001f);
        assertTrue(Float.isFinite(gain));
    }

    @Test public void zeroAlwaysMutesAndTheMaximumCapsGain() {
        assertEquals(0f, VolumeLevels.gain(0, -1, 15, true, index -> 0), 0f);
        assertEquals(10f, VolumeLevels.gain(100, 5, 15, true, index -> -60 + index * 2), .0001f);
    }

    @Test public void amplificationSaturatesInsteadOfWrappingSamples() {
        assertEquals(Short.MAX_VALUE, VolumeLevels.amplified((short) 20000, 2));
        assertEquals(Short.MIN_VALUE, VolumeLevels.amplified((short) -20000, 2));
        assertEquals((short) 1234, VolumeLevels.amplified((short) 1234, 1));
    }
}
