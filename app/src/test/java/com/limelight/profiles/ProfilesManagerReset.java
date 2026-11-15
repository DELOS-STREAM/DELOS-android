package com.limelight.profiles;

/** Test helper: forgets the process-wide profiles singleton so that a test starts without an active profile. */
public final class ProfilesManagerReset {
    private ProfilesManagerReset() {}

    public static void reset() {
        ProfilesManager.instance = null;
    }
}
