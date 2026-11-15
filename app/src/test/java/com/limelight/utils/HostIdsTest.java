package com.limelight.utils;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import java.io.File;

public class HostIdsTest {
    @Test
    public void acceptsTheIdsHostsSend() {
        assertTrue(HostIds.isValidUuid("7B0A3A5E-1C2D-4E5F-8A9B-0C1D2E3F4A5B"));
        assertTrue(HostIds.isValidUuid("0123456789abcdef"));
        assertTrue(HostIds.isValidUuid("a"));
    }

    @Test
    public void rejectsAnythingThatCouldLeaveADirectory() {
        for (String bad : new String[] {null, "", "..", ".", "../x", "a/b", "a\\b", "a\u0000b", "a b", "a.b", "é",
                "%2e%2e", new String(new char[HostIds.MAX_UUID_LENGTH + 1]).replace('\0', 'a')}) {
            assertFalse(String.valueOf(bad), HostIds.isValidUuid(bad));
        }
        assertTrue(HostIds.isValidUuid(new String(new char[HostIds.MAX_UUID_LENGTH]).replace('\0', 'a')));
    }

    @Test
    public void appDirStaysBelowItsRoot() throws Exception {
        File root = new File("/data/artwork");
        File dir = HostIds.appDir(root, "ABC-1", 42);
        assertEquals(new File(root, "ABC-1/42"), dir);
        assertEquals(root.getCanonicalFile(), dir.getCanonicalFile().getParentFile().getParentFile());
        assertThrows(IllegalArgumentException.class, () -> HostIds.appDir(root, "../../databases", 1));
        assertThrows(IllegalArgumentException.class, () -> HostIds.appDir(root, "..", 1));
        // An app id is a number: whatever its value, it is one path segment
        assertEquals(new File(root, "ABC/-1"), HostIds.appDir(root, "ABC", -1));
    }
}
