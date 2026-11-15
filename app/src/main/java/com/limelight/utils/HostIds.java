package com.limelight.utils;

import java.io.File;

/**
 * Validation of identifiers that come from outside the app: the host's {@code uniqueid} (from
 * {@code serverinfo}, intent extras, shortcut files) and app ids. They name cache and artwork
 * directories, so only values that cannot leave their directory are accepted.
 */
public final class HostIds {
    /** Hosts send a UUID (36 characters) or a hex string; nothing legitimate is longer. */
    public static final int MAX_UUID_LENGTH = 64;

    private HostIds() {}

    /** True for 1..{@link #MAX_UUID_LENGTH} ASCII letters, digits and hyphens. */
    public static boolean isValidUuid(String uuid) {
        if (uuid == null || uuid.isEmpty() || uuid.length() > MAX_UUID_LENGTH) {
            return false;
        }
        for (int i = 0; i < uuid.length(); i++) {
            char c = uuid.charAt(i);
            boolean ok = (c >= '0' && c <= '9') || (c >= 'A' && c <= 'Z') || (c >= 'a' && c <= 'z') || c == '-';
            if (!ok) {
                return false;
            }
        }
        return true;
    }

    /** @throws IllegalArgumentException when {@code uuid} is not a valid host id */
    public static String requireUuid(String uuid) {
        if (!isValidUuid(uuid)) {
            throw new IllegalArgumentException("Invalid host id");
        }
        return uuid;
    }

    /**
     * The directory of one app of one host below {@code root}; it is always a grandchild of {@code root}.
     *
     * @throws IllegalArgumentException when the host id is invalid
     */
    public static File appDir(File root, String uuid, int appId) {
        return new File(new File(root, requireUuid(uuid)), Integer.toString(appId));
    }
}
