package com.limelight.profiles;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import android.content.Context;

import androidx.test.core.app.ApplicationProvider;

import com.limelight.TestLogSuppressor;

import org.junit.After;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/** A save that fails half way must not cost the stored stream profiles (C-11). */
@Config(sdk = {33}, shadows = {com.limelight.shadows.ShadowMoonBridge.class, com.limelight.shadows.ShadowGameManager.class})
@RunWith(RobolectricTestRunner.class)
public class ProfilesAtomicSaveTest {
    private Context context;
    private File file;

    @BeforeClass
    public static void quiet() {
        TestLogSuppressor.install();
    }

    @Before
    public void setUp() {
        ProfilesManager.instance = null;
        context = ApplicationProvider.getApplicationContext();
        file = new File(new File(context.getFilesDir(), "profiles"), "profiles.json");
        delete();
    }

    @After
    public void delete() {
        File[] files = file.getParentFile().listFiles();
        if (files != null) {
            for (File f : files) {
                f.delete();
            }
        }
    }

    private static SettingsProfile profile(String name, Map<String, Object> options) {
        return new SettingsProfile(UUID.randomUUID(), name, 1, 1, options);
    }

    @Test
    public void aSaveThatFailsInTheMiddleKeepsTheStoredProfiles() throws Exception {
        ProfilesManager manager = ProfilesManager.getInstance();
        assertTrue(manager.load(context));
        manager.add(profile("Handheld", null));
        manager.add(profile("Docked", null));
        String stored = new String(Files.readAllBytes(file.toPath()), StandardCharsets.UTF_8);
        assertTrue(stored.contains("Handheld") && stored.contains("Docked"));

        // JSON cannot hold NaN: Gson throws after it has already written the first two profiles
        Map<String, Object> unwritable = new HashMap<>();
        unwritable.put("bitrate", Double.NaN);
        manager.add(profile("Broken", unwritable));
        assertFalse(manager.save(context));

        assertEquals(stored, new String(Files.readAllBytes(file.toPath()), StandardCharsets.UTF_8));

        ProfilesManager.instance = null;
        ProfilesManager fresh = ProfilesManager.getInstance();
        assertTrue(fresh.load(context));
        assertEquals(2, fresh.getProfiles().size());
        assertEquals("Handheld", fresh.getProfiles().get(0).getName());
    }

    @Test
    public void aCompletedSaveReplacesTheFileAndLeavesNoTemporaryOne() {
        ProfilesManager manager = ProfilesManager.getInstance();
        assertTrue(manager.load(context));
        manager.add(profile("One", null));
        manager.add(profile("Two", null));

        String[] names = file.getParentFile().list();
        assertEquals(1, names.length);
        assertEquals("profiles.json", names[0]);
    }
}
