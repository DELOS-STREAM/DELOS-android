package com.limelight.shell.settings;

/** A plain start may offer the new tutorial; first-run and stream entry own their existing flows. */
public final class TutorialStart {
    private TutorialStart() {}

    public static boolean shouldShow(boolean seen, boolean hasProfiles, boolean deferForStream) {
        return !seen && hasProfiles && !deferForStream;
    }
}
