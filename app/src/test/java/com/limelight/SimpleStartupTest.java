package com.limelight;

import android.content.Context;
import androidx.test.core.app.ApplicationProvider;
import com.limelight.profiles.ProfilesManager;
import com.limelight.profiles.ProfilesManagerReset;
import com.limelight.profiles.SettingsProfile;

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

import static org.junit.Assert.*;

/** What the application does when its process starts: the profiles are loaded and usable whatever is on disk. */
@Config(sdk = {33}, shadows = {com.limelight.shadows.ShadowMoonBridge.class, com.limelight.shadows.ShadowGameManager.class})
@RunWith(RobolectricTestRunner.class)
public class SimpleStartupTest {
    private Context context;
    private File profilesFile;

    @BeforeClass
    public static void suppressInvalidIdLogs() {
        TestLogSuppressor.install();
    }

    @Before
    public void setUp() {
        ProfilesManagerReset.reset();
        context = ApplicationProvider.getApplicationContext();
        File profilesDir = new File(context.getFilesDir(), "profiles");
        profilesFile = new File(profilesDir, "profiles.json");
        profilesFile.delete();
    }

    private static SettingsProfile profile(String name) {
        Map<String, Object> options = new HashMap<>();
        options.put("list_fps", "120");
        return new SettingsProfile(UUID.randomUUID(), name, 1000, 2000, options);
    }

    @Test
    public void applicationStartLoadsTheStoredProfiles() {
        SettingsProfile stored = profile("Couch");
        ProfilesManager writer = ProfilesManager.getInstance();
        writer.load(context);
        writer.add(stored);
        writer.setActive(stored.getUuid());
        assertTrue(writer.save(context));
        ProfilesManagerReset.reset();

        // The application Robolectric created from the manifest (it has a base context)
        DelosApplication app = ApplicationProvider.getApplicationContext();
        app.onCreate();

        ProfilesManager manager = ProfilesManager.getInstance();
        assertEquals(1, manager.getProfiles().size());
        assertEquals("Couch", manager.getProfiles().get(0).getName());
        assertEquals(stored.getUuid(), manager.getActive().getUuid());
        assertEquals("120", manager.getActive().getOptions().get("list_fps"));
    }

    @Test
    public void applicationStartOnAFreshInstallHasNoProfilesAndNoActiveOne() {
        DelosApplication app = ApplicationProvider.getApplicationContext();
        app.onCreate();

        ProfilesManager manager = ProfilesManager.getInstance();
        assertTrue(manager.getProfiles().isEmpty());
        assertNull(manager.getActive());
    }

    @Test
    public void theManagerIsOnePerProcess() {
        assertSame(ProfilesManager.getInstance(), ProfilesManager.getInstance());
    }

    @Test
    public void aFreshInstallLoadsWithoutProfiles() {
        ProfilesManager manager = ProfilesManager.getInstance();

        assertTrue(manager.load(context));
        assertTrue(manager.getProfiles().isEmpty());
    }

    @Test
    public void savedProfilesComeBackAfterARestart() {
        ProfilesManager manager = ProfilesManager.getInstance();
        manager.load(context);
        SettingsProfile first = profile("Desk");
        SettingsProfile second = profile("Travel");
        manager.add(first);
        manager.add(second);
        manager.setActive(second.getUuid());
        assertTrue(manager.save(context));

        ProfilesManagerReset.reset();
        ProfilesManager restarted = ProfilesManager.getInstance();
        assertTrue(restarted.load(context));

        assertEquals(2, restarted.getProfiles().size());
        assertEquals(second.getUuid(), restarted.getActive().getUuid());
        assertEquals("Travel", restarted.getActiveName());
        assertEquals(1000, restarted.getActive().getCreatedUtc());
    }

    @Test
    public void aDeletedProfileStaysDeletedAfterARestart() {
        ProfilesManager manager = ProfilesManager.getInstance();
        manager.load(context);
        SettingsProfile keep = profile("Keep");
        SettingsProfile drop = profile("Drop");
        manager.add(keep);
        manager.add(drop);
        manager.delete(drop.getUuid());
        manager.save(context);

        ProfilesManagerReset.reset();
        ProfilesManager restarted = ProfilesManager.getInstance();
        restarted.load(context);

        assertEquals(1, restarted.getProfiles().size());
        assertEquals(keep.getUuid(), restarted.getProfiles().get(0).getUuid());
    }

    @Test
    public void aCorruptProfilesFileIsReportedAndLeavesTheManagerUsable() throws Exception {
        profilesFile.getParentFile().mkdirs();
        Files.write(profilesFile.toPath(), "{\"profiles\": [ {".getBytes(StandardCharsets.UTF_8));
        ProfilesManager manager = ProfilesManager.getInstance();

        assertFalse("the caller must learn that the profiles could not be read", manager.load(context));

        assertTrue(manager.getProfiles().isEmpty());
        assertNull(manager.getActive());
        // The app keeps working: a new profile can be made and stored.
        manager.add(profile("New"));
        assertTrue(manager.save(context));
        ProfilesManagerReset.reset();
        assertTrue(ProfilesManager.getInstance().load(context));
        assertEquals(1, ProfilesManager.getInstance().getProfiles().size());
    }

    @Test
    public void withoutAContextNothingIsLoadedOrSaved() {
        ProfilesManager manager = ProfilesManager.getInstance();

        assertFalse(manager.load(null));
        assertFalse(manager.save(null));
        assertTrue(manager.getProfiles().isEmpty());
        assertFalse(profilesFile.exists());
    }
}
