package com.limelight.preferences;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import android.content.Context;
import android.content.SharedPreferences;

import androidx.preference.PreferenceManager;
import androidx.test.core.app.ApplicationProvider;

import com.limelight.nvstream.jni.MoonBridge;
import com.limelight.profiles.ProfilesManagerReset;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;

/**
 * Settings stored by earlier versions under keys that no longer exist must arrive in today's
 * keys with the same meaning. The key names below are the upgrade contract: they are what is on
 * users' devices, so they are spelled out here and not read from the production constants.
 */
@RunWith(RobolectricTestRunner.class)
@Config(sdk = 33, shadows = {com.limelight.shadows.ShadowMoonBridge.class, com.limelight.shadows.ShadowGameManager.class})
public class PreferenceMigrationTest {
    private Context context;
    private SharedPreferences prefs;

    @Before
    public void setUp() {
        ProfilesManagerReset.reset();
        context = ApplicationProvider.getApplicationContext();
        prefs = PreferenceManager.getDefaultSharedPreferences(context);
        prefs.edit().clear().commit();
    }

    private PreferenceConfiguration read() {
        return PreferenceConfiguration.readPreferences(context);
    }

    // ---- frame pacing ----

    @Test
    public void legacyNeverDropFramesBecomesBalancedFramePacing() {
        prefs.edit().putBoolean("checkbox_disable_frame_drop", true).commit();

        PreferenceConfiguration config = read();

        assertEquals(PreferenceConfiguration.FRAME_PACING_BALANCED, config.framePacing);
        assertEquals("balanced", prefs.getString("frame_pacing", null));
        assertFalse(prefs.contains("checkbox_disable_frame_drop"));
    }

    @Test
    public void legacyDropFramesAllowedBecomesLowestLatency() {
        prefs.edit().putBoolean("checkbox_disable_frame_drop", false).commit();

        PreferenceConfiguration config = read();

        assertEquals(PreferenceConfiguration.FRAME_PACING_MIN_LATENCY, config.framePacing);
        assertEquals("latency", prefs.getString("frame_pacing", null));
        assertFalse(prefs.contains("checkbox_disable_frame_drop"));
    }

    @Test
    public void theMigratedFramePacingSurvivesTheNextStart() {
        prefs.edit().putBoolean("checkbox_disable_frame_drop", true).commit();
        read();

        assertEquals(PreferenceConfiguration.FRAME_PACING_BALANCED, read().framePacing);
    }

    // ---- audio ----

    @Test
    public void legacy51SurroundBecomesAudioConfig() {
        prefs.edit().putBoolean("checkbox_51_surround", true).commit();

        PreferenceConfiguration config = read();

        assertEquals(MoonBridge.AUDIO_CONFIGURATION_51_SURROUND, config.audioConfiguration);
        assertEquals("51", prefs.getString("list_audio_config", null));
        assertFalse(prefs.contains("checkbox_51_surround"));
    }

    @Test
    public void legacy51SurroundOffStaysStereo() {
        prefs.edit().putBoolean("checkbox_51_surround", false).commit();

        assertEquals(MoonBridge.AUDIO_CONFIGURATION_STEREO, read().audioConfiguration);
    }

    @Test
    public void audioConfigValuesMapToChannelLayouts() {
        prefs.edit().putString("list_audio_config", "71").commit();
        assertEquals(MoonBridge.AUDIO_CONFIGURATION_71_SURROUND, read().audioConfiguration);

        prefs.edit().putString("list_audio_config", "51").commit();
        assertEquals(MoonBridge.AUDIO_CONFIGURATION_51_SURROUND, read().audioConfiguration);

        prefs.edit().putString("list_audio_config", "2").commit();
        assertEquals(MoonBridge.AUDIO_CONFIGURATION_STEREO, read().audioConfiguration);
    }

    // ---- resolution and frame rate ----

    @Test
    public void legacyResFpsStringsMap() {
        Object[][] table = {
                // stored, width, height, fps
                {"360p30", 640, 360, 30},
                {"360p60", 640, 360, 60},
                {"720p30", 1280, 720, 30},
                {"720p60", 1280, 720, 60},
                {"1080p30", 1920, 1080, 30},
                {"1080p60", 1920, 1080, 60},
                {"4K30", 3840, 2160, 30},
                {"4K60", 3840, 2160, 60},
        };
        for (Object[] row : table) {
            prefs.edit().clear().putString("list_resolution_fps", (String) row[0]).commit();

            PreferenceConfiguration config = read();

            assertEquals((String) row[0], (int) row[1], config.width);
            assertEquals((String) row[0], (int) row[2], config.height);
            assertEquals((String) row[0], (int) row[3], Math.round(config.fps));
            // The combined key is gone and the two new keys give the same answer on the next start.
            assertFalse((String) row[0], prefs.contains("list_resolution_fps"));
            assertEquals((String) row[0], row[1] + "x" + row[2], prefs.getString("list_resolution", null));
            assertEquals((String) row[0], (int) row[3], Math.round(Float.parseFloat(prefs.getString("list_fps", null))));
            PreferenceConfiguration again = read();
            assertEquals((String) row[0], (int) row[1], again.width);
            assertEquals((String) row[0], (int) row[2], again.height);
            assertEquals((String) row[0], (int) row[3], Math.round(again.fps));
        }
    }

    @Test
    public void anUnknownLegacyResFpsFallsBackTo720p60() {
        prefs.edit().putString("list_resolution_fps", "8K240").commit();

        PreferenceConfiguration config = read();

        assertEquals(1280, config.width);
        assertEquals(720, config.height);
        assertEquals(60, Math.round(config.fps));
    }

    @Test
    public void legacyResolutionNamesBecomeDimensions() {
        Object[][] table = {
                {"360p", 640, 360},
                {"480p", 854, 480},
                {"720p", 1280, 720},
                {"1080p", 1920, 1080},
                {"1440p", 2560, 1440},
                {"4K", 3840, 2160},
                {"4k", 3840, 2160},
        };
        for (Object[] row : table) {
            prefs.edit().clear().putString("list_resolution", (String) row[0]).commit();

            PreferenceConfiguration config = read();

            assertEquals((String) row[0], (int) row[1], config.width);
            assertEquals((String) row[0], (int) row[2], config.height);
            assertEquals((String) row[0], row[1] + "x" + row[2], prefs.getString("list_resolution", null));
        }
    }

    @Test
    public void aStoredDimensionStringIsReadAsIs() {
        prefs.edit().putString("list_resolution", "2400x1080").putString("list_fps", "120").commit();

        PreferenceConfiguration config = read();

        assertEquals(2400, config.width);
        assertEquals(1080, config.height);
        assertEquals(120, Math.round(config.fps));
        assertEquals("2400x1080", prefs.getString("list_resolution", null));
    }

    // ---- scaling and display mode ----

    @Test
    public void legacyStretchBecomesScaleMode() {
        prefs.edit().putBoolean("checkbox_stretch_video", true).commit();

        PreferenceConfiguration config = read();

        assertEquals(PreferenceConfiguration.ScaleMode.STRETCH, config.videoScaleMode);
        assertEquals("stretch", prefs.getString("list_video_scale_mode", null));
        assertFalse(prefs.contains("checkbox_stretch_video"));
    }

    @Test
    public void legacyStretchOffBecomesFit() {
        prefs.edit().putBoolean("checkbox_stretch_video", false).commit();

        PreferenceConfiguration config = read();

        assertEquals(PreferenceConfiguration.ScaleMode.FIT, config.videoScaleMode);
        assertEquals("fit", prefs.getString("list_video_scale_mode", null));
    }

    @Test
    public void legacyEnforceRefreshRateBecomesEnforceDisplayMode() {
        prefs.edit().putBoolean("checkbox_enforce_refresh_rate", true).commit();

        PreferenceConfiguration config = read();

        assertTrue(config.enforceDisplayMode);
        assertTrue(prefs.getBoolean("checkbox_enforce_display_mode", false));
        assertFalse(prefs.contains("checkbox_enforce_refresh_rate"));
    }

    // ---- bitrate ----

    @Test
    public void legacyMbpsBitrateIsReadAsKbps() {
        prefs.edit().putInt("seekbar_bitrate", 25).commit();

        assertEquals(25000, read().bitrate);
    }

    @Test
    public void theKbpsBitrateWinsOverTheLegacyOne() {
        prefs.edit().putInt("seekbar_bitrate", 25).putInt("seekbar_bitrate_kbps", 12345).commit();

        assertEquals(12345, read().bitrate);
    }

    @Test
    public void withoutAStoredBitrateTheDefaultForTheResolutionIsUsed() {
        prefs.edit().putString("list_resolution", "1920x1080").putString("list_fps", "60").commit();

        PreferenceConfiguration config = read();

        assertEquals(PreferenceConfiguration.getDefaultBitrate("1920x1080", "60"), config.bitrate);
        assertTrue(config.bitrate > 0);
        // On a metered network a quarter is used unless the user chose a value.
        assertEquals(config.bitrate / 4, config.meteredBitrate);
    }

    // ---- a fresh install ----

    @Test
    public void freshInstallDefaults() {
        PreferenceConfiguration config = read();

        assertEquals(1280, config.width);
        assertEquals(720, config.height);
        assertEquals(60, Math.round(config.fps));
        assertEquals(MoonBridge.AUDIO_CONFIGURATION_STEREO, config.audioConfiguration);
        assertEquals(PreferenceConfiguration.FRAME_PACING_MIN_LATENCY, config.framePacing);
        assertEquals(PreferenceConfiguration.ScaleMode.FIT, config.videoScaleMode);
        assertFalse(config.enforceDisplayMode);
        assertFalse(config.useVirtualDisplay);
        assertFalse(config.enableUltraLowLatency);
        assertFalse(config.playHostAudio);
        assertTrue(config.multiController);
        assertTrue(config.usbDriver);
        assertEquals(5, config.deadzonePercentage);
        assertTrue(config.gamepadMotionSensors);
        assertEquals(0, config.gyroSmoothing);
        assertFalse(config.sgsrEnabled);
        assertEquals(UpscalerMode.STANDARD, config.sgsrMode);
        assertFalse(config.asyncDecode);
        assertEquals(PreferenceConfiguration.getDefaultBitrate("1280x720", "60"), config.bitrate);
    }

    @Test
    public void readingAFreshInstallCreatesNoLegacyKeys() {
        read();

        for (String legacy : new String[]{"list_resolution_fps", "checkbox_51_surround", "checkbox_stretch_video",
                "checkbox_enforce_refresh_rate", "checkbox_disable_frame_drop", "seekbar_bitrate"}) {
            assertFalse(legacy, prefs.contains(legacy));
        }
    }

    @Test
    public void outOfRangeStoredValuesAreClamped() {
        prefs.edit().putInt("seekbar_sgsr_sharpness", 500).putInt("seekbar_gyro_smoothing", -3).commit();

        PreferenceConfiguration config = read();

        assertEquals(100, config.sgsrSharpness);
        assertEquals(0, config.gyroSmoothing);
    }
}
