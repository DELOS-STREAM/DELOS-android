package com.limelight.profiles;

import android.content.Context;

import androidx.test.core.app.ApplicationProvider;

import org.junit.After;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;
import org.robolectric.annotation.Config;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;

import java.io.File;
import java.util.UUID;

import static org.junit.Assert.*;

import com.limelight.TestLogSuppressor;

@Config(sdk = {33}, shadows = {com.limelight.shadows.ShadowMoonBridge.class, com.limelight.shadows.ShadowGameManager.class})
@RunWith(RobolectricTestRunner.class)
public class ProfilesManagerTest {
    private Context context;
    private ProfilesManager manager;
    private File profilesDir;

    @BeforeClass
    public static void suppressInvalidIdLogs() {
        TestLogSuppressor.install();
    }

    @Before
    public void setUp() {
        ProfilesManager.instance = null; // Reset the singleton before each test
        context = ApplicationProvider.getApplicationContext();
        profilesDir = new File(context.getFilesDir(), "profiles");
        deleteRecursively(profilesDir);
        manager = ProfilesManager.getInstance();
        manager.load(context);
    }

    @After
    public void tearDown() {
        deleteRecursively(profilesDir);
    }

    @Test
    public void addAndRetrieveProfile() {
        SettingsProfile p = new SettingsProfile(UUID.randomUUID(), "Test", System.currentTimeMillis(), System.currentTimeMillis(), null);
        manager.add(p);
        assertEquals(1, manager.getProfiles().size());
        assertEquals(p.getUuid(), manager.getProfiles().get(0).getUuid());
    }

    @Test
    public void setActivePersists() {
        SettingsProfile p = new SettingsProfile(UUID.randomUUID(), "Active", System.currentTimeMillis(), System.currentTimeMillis(), null);
        manager.add(p);
        manager.setActive(p.getUuid());
        // Reload from disk to verify persistence
        ProfilesManager fresh = ProfilesManager.getInstance();
        fresh.load(context);
        assertNotNull(fresh.getActive());
        assertEquals(p.getUuid(), fresh.getActive().getUuid());
    }

    @Test
    public void updateAndSaveProfile() {
        SettingsProfile p = new SettingsProfile(UUID.randomUUID(), "Original", System.currentTimeMillis(), System.currentTimeMillis(), null);
        manager.add(p);
        p.setName("Updated");
        manager.update(p);

        ProfilesManager fresh = ProfilesManager.getInstance();
        fresh.load(context);
        assertEquals("Updated", fresh.getProfiles().get(0).getName());
    }

    @Test
    public void deleteProfile() {
        SettingsProfile p = new SettingsProfile(UUID.randomUUID(), "ToDelete", System.currentTimeMillis(), System.currentTimeMillis(), null);
        manager.add(p);
        assertEquals(1, manager.getProfiles().size());

        manager.delete(p.getUuid());
        assertEquals(0, manager.getProfiles().size());
    }

    @Test
    public void deleteActiveProfile_resetsActive() {
        SettingsProfile p = new SettingsProfile(UUID.randomUUID(), "ActiveToDelete", System.currentTimeMillis(), System.currentTimeMillis(), null);
        manager.add(p);
        manager.setActive(p.getUuid());
        assertNotNull(manager.getActive());

        manager.delete(p.getUuid());
        assertNull(manager.getActive());

        // Verify persistence
        ProfilesManager fresh = ProfilesManager.getInstance();
        fresh.load(context);
        assertNull(fresh.getActive());
    }

    @Test
    public void aProfilesFileWithoutProfilesIsNoProfiles() throws Exception {
        SettingsProfile p = new SettingsProfile(UUID.randomUUID(), "Kept in memory", 1L, 1L, null);
        manager.add(p);

        // An empty file and a file without the list: both load, and neither wipes what is in memory
        for (String content : new String[] {"", "{}", "{\"activeProfileId\":null}"}) {
            java.nio.file.Files.write(new File(profilesDir, "profiles.json").toPath(),
                    content.getBytes(java.nio.charset.StandardCharsets.UTF_8));
            assertTrue(content, manager.load(context));
            assertEquals(content, 1, manager.getProfiles().size());
        }
    }

    @Test
    public void aProfilesFileThatIsNotJsonFailsToLoadAndChangesNothing() throws Exception {
        SettingsProfile p = new SettingsProfile(UUID.randomUUID(), "Kept in memory", 1L, 1L, null);
        manager.add(p);
        manager.setActive(p.getUuid());
        java.nio.file.Files.write(new File(profilesDir, "profiles.json").toPath(),
                "{\"profiles\": [".getBytes(java.nio.charset.StandardCharsets.UTF_8));

        assertFalse(manager.load(context));

        assertEquals(1, manager.getProfiles().size());
        assertEquals(p.getUuid(), manager.getActive().getUuid());
    }

    @Test
    public void theActiveNameIsEmptyWithoutAnActiveProfile() {
        assertEquals("", manager.getActiveName());

        SettingsProfile p = new SettingsProfile(UUID.randomUUID(), "Couch", 1L, 1L, null);
        manager.add(p);
        manager.setActive(p.getUuid());
        assertEquals("Couch", manager.getActiveName());

        // An id that names no profile (the profile was deleted elsewhere) is no active profile
        manager.setActive(UUID.randomUUID());
        assertNull(manager.getActive());
        assertEquals("", manager.getActiveName());
    }

    @Test
    public void listenersHearEveryChangeUntilTheyAreRemoved() {
        int[] heard = {0};
        ProfilesManager.ProfileChangeListener listener = () -> heard[0]++;
        manager.addListener(listener);
        SettingsProfile p = new SettingsProfile(UUID.randomUUID(), "Heard", 1L, 1L, null);

        manager.add(p);
        manager.update(p);
        manager.setActive(p.getUuid());
        manager.delete(p.getUuid());
        assertEquals(4, heard[0]);

        manager.removeListener(listener);
        manager.add(p);
        assertEquals(4, heard[0]);
    }

    @Test
    public void loadAndSaveWithoutAContextDoNothing() {
        assertFalse(manager.load(null));
        assertFalse(manager.save(null));
    }

    private void deleteRecursively(File f) {
        if (f == null || !f.exists()) return;
        if (f.isDirectory()) {
            for (File c : f.listFiles()) {
                deleteRecursively(c);
            }
        }
        f.delete();
    }
}