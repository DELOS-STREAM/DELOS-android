package com.limelight.binding.audio;

import java.util.function.IntToDoubleFunction;

/** One 0–15 user scale, independent of a device's Android media-index range. */
public final class VolumeLevels {
    public static final int STEPS = 15;
    public static final int UNKNOWN_LEVEL = 9;

    private VolumeLevels() {}

    public static int clamp(int level) {
        return Math.max(0, Math.min(STEPS, level));
    }

    public static int toIndex(int level, int maximum) {
        return Math.round(clamp(level) * Math.max(0, maximum) / (float) STEPS);
    }

    public static int fromIndex(int index, int maximum) {
        return maximum <= 0 ? 0 : clamp(Math.round(index * STEPS / (float) maximum));
    }

    /** Unknown initial output stays neutral; a muted reference is unlocked only by an explicit volume-up press. */
    public static float gain(int level, int referenceIndex, int maximum, boolean volumeUpAfterMute,
                             IntToDoubleFunction db) {
        if (level <= 0) return 0f;
        if (referenceIndex < 0) return 1f;
        if (referenceIndex == 0 && !volumeUpAfterMute) return 0f;
        int reference = Math.max(1, Math.min(maximum, referenceIndex));
        int target = toIndex(level, maximum);
        if (target == 0) return 0f;
        double delta = db.applyAsDouble(target) - db.applyAsDouble(reference);
        return (float) Math.pow(10.0, delta / 20.0);
    }

    public static short amplified(short sample, float gain) {
        return (short) Math.max(Short.MIN_VALUE, Math.min(Short.MAX_VALUE, Math.round(sample * gain)));
    }
}
