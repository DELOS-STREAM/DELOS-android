package com.limelight.nvstream;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import com.limelight.nvstream.http.NvApp;
import com.limelight.nvstream.jni.MoonBridge;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;

/** What a stream is started with when a caller sets only some of the values, and how values are encoded for the host. */
@RunWith(RobolectricTestRunner.class)
@Config(sdk = 33, shadows = {com.limelight.shadows.ShadowMoonBridge.class})
public class StreamConfigurationTest {
    @Test
    public void builderDefaults() {
        StreamConfiguration config = new StreamConfiguration.Builder().build();

        assertEquals(1280, config.getWidth());
        assertEquals(720, config.getHeight());
        assertEquals(60, config.getRefreshRate());
        assertEquals(60, config.getLaunchRefreshRate());
        assertEquals(100, config.getResolutionScaleFactor());
        assertEquals(StreamConfiguration.STREAM_CFG_AUTO, config.getRemote());
        assertEquals(MoonBridge.AUDIO_CONFIGURATION_STEREO, config.getAudioConfiguration());
        assertEquals(MoonBridge.VIDEO_FORMAT_H264, config.getSupportedVideoFormats());
        assertEquals(0, config.getAttachedGamepadMask());
        assertTrue(config.getSops());
        assertFalse(config.getVirtualDisplay());
        assertFalse(config.getPlayLocalAudio());
        assertFalse(config.getAdaptiveResolutionEnabled());
        assertFalse(config.getEnableUltraLowLatency());
        assertFalse(config.getPersistGamepadsAfterDisconnect());
        assertTrue(config.getBitrate() > 0);
        assertTrue(config.getMaxPacketSize() > 0);
    }

    @Test
    public void whatIsSetIsWhatIsRead() {
        NvApp app = new NvApp("Hades", "APP-UUID", 42, true);
        StreamConfiguration config = new StreamConfiguration.Builder()
                .setApp(app)
                .setResolution(2400, 1080)
                .setRefreshRate(120)
                .setLaunchRefreshRate(90)
                .setBitrate(80_000)
                .setVirtualDisplay(true)
                .setResolutionScaleFactor(150)
                .setEnableSops(false)
                .enableLocalAudioPlayback(true)
                .setMaxPacketSize(1392)
                .setRemoteConfiguration(StreamConfiguration.STREAM_CFG_REMOTE)
                .setAudioConfiguration(MoonBridge.AUDIO_CONFIGURATION_51_SURROUND)
                .setSupportedVideoFormats(MoonBridge.VIDEO_FORMAT_H265 | MoonBridge.VIDEO_FORMAT_H264)
                .setClientRefreshRateX100(11988)
                .setColorRange(1)
                .setColorSpace(2)
                .setEnableUltraLowLatency(true)
                .setPersistGamepadsAfterDisconnect(true)
                .build();

        assertEquals(app, config.getApp());
        assertEquals(2400, config.getWidth());
        assertEquals(1080, config.getHeight());
        assertEquals(120, config.getRefreshRate());
        assertEquals(90, config.getLaunchRefreshRate());
        assertEquals(80_000, config.getBitrate());
        assertTrue(config.getVirtualDisplay());
        assertEquals(150, config.getResolutionScaleFactor());
        assertFalse(config.getSops());
        assertTrue(config.getPlayLocalAudio());
        assertEquals(1392, config.getMaxPacketSize());
        assertEquals(StreamConfiguration.STREAM_CFG_REMOTE, config.getRemote());
        assertEquals(MoonBridge.AUDIO_CONFIGURATION_51_SURROUND, config.getAudioConfiguration());
        assertEquals(MoonBridge.VIDEO_FORMAT_H265 | MoonBridge.VIDEO_FORMAT_H264, config.getSupportedVideoFormats());
        assertEquals(11988, config.getClientRefreshRateX100());
        assertEquals(1, config.getColorRange());
        assertEquals(2, config.getColorSpace());
        assertTrue(config.getEnableUltraLowLatency());
        assertTrue(config.getPersistGamepadsAfterDisconnect());
    }

    @Test
    public void aFractionalRefreshRateIsSentInThousandths() {
        StreamConfiguration config = new StreamConfiguration.Builder()
                .setRefreshRate(59.94f).setLaunchRefreshRate(119.88f).build();

        // The host tells whole hertz from millihertz by the size of the number.
        assertEquals(59940, config.getRefreshRate());
        assertEquals(119880, config.getLaunchRefreshRate());
    }

    @Test
    public void gamepadMaskByCountSetsOneBitPerPadUpToFour() {
        int[] expected = {0b0000, 0b0001, 0b0011, 0b0111, 0b1111, 0b1111, 0b1111};
        for (int count = 0; count < expected.length; count++) {
            StreamConfiguration config = new StreamConfiguration.Builder().setAttachedGamepadMaskByCount(count).build();

            assertEquals("pads: " + count, expected[count], config.getAttachedGamepadMask());
        }
        assertEquals(0, new StreamConfiguration.Builder().setAttachedGamepadMaskByCount(-1).build().getAttachedGamepadMask());
    }

    @Test
    public void anExplicitGamepadMaskIsKept() {
        assertEquals(0b0101,
                new StreamConfiguration.Builder().setAttachedGamepadMask(0b0101).build().getAttachedGamepadMask());
    }
}
