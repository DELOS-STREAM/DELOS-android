package com.limelight.binding.video;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.robolectric.Shadows.shadowOf;

import android.app.ActivityManager;
import android.app.Application;
import android.content.Context;
import android.content.pm.ConfigurationInfo;
import android.media.MediaCodecInfo;
import android.media.MediaCodecInfo.CodecCapabilities;
import android.media.MediaCodecInfo.CodecProfileLevel;
import android.media.MediaFormat;

import androidx.test.core.app.ApplicationProvider;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;
import org.robolectric.shadows.MediaCodecInfoBuilder;
import org.robolectric.shadows.ShadowMediaCodecList;

/**
 * Decoder quirks are tables keyed by decoder-name prefix. These tests state, for the decoder
 * names of real devices, which quirks apply on a current Snapdragon handheld (Adreno 7xx, OpenGL
 * ES 3.2): the device class Delos is built for.
 *
 * MediaCodecHelper initialises once per process, so every test here shares that one device.
 */
@RunWith(RobolectricTestRunner.class)
@Config(sdk = {33, 36}, application = Application.class)
public class MediaCodecHelperTest {
    private static final String QTI_AVC = "c2.qti.avc.decoder";
    private static final String QTI_HEVC = "c2.qti.hevc.decoder";
    private static final String QTI_AV1 = "c2.qti.av1.decoder";
    private static final String OMX_QCOM_AVC = "OMX.qcom.video.decoder.avc";
    private static final String MTK_AVC = "c2.mtk.avc.decoder";
    private static final String OMX_MTK_HEVC = "OMX.MTK.VIDEO.DECODER.HEVC";
    private static final String EXYNOS_AVC = "c2.exynos.h264.decoder";
    private static final String EXYNOS_HEVC = "c2.exynos.hevc.decoder";
    private static final String KIRIN_AVC = "OMX.hisi.video.decoder.avc";
    private static final String AMLOGIC_AVC = "OMX.amlogic.avc.decoder.awesome";
    private static final String NVIDIA_AVC = "OMX.Nvidia.h264.decode";
    private static final String INTEL_AVC = "OMX.Intel.hw_vd.h264";
    private static final String SOFT_AVC = "c2.android.avc.decoder";
    private static final String SOFT_HEVC = "c2.android.hevc.decoder";
    private static final String OLD_SOFT_AVC = "OMX.google.h264.decoder";

    @Before
    public void setUp() {
        Context context = ApplicationProvider.getApplicationContext();
        ConfigurationInfo gles32 = new ConfigurationInfo();
        gles32.reqGlEsVersion = 0x30002;
        shadowOf((ActivityManager) context.getSystemService(Context.ACTIVITY_SERVICE))
                .setDeviceConfigurationInfo(gles32);
        MediaCodecHelper.initialize(context, "Adreno (TM) 740");
        ShadowMediaCodecList.reset();
    }

    @After
    public void tearDown() {
        ShadowMediaCodecList.reset();
    }

    // ---- helpers that build a decoder as the platform would list it ----

    private static MediaCodecInfo decoder(String name, String mime, boolean hardware, boolean lowLatency,
                                          int... profiles) {
        MediaFormat format = new MediaFormat();
        format.setString(MediaFormat.KEY_MIME, mime);
        if (lowLatency) {
            format.setFeatureEnabled(CodecCapabilities.FEATURE_LowLatency, true);
        }
        CodecProfileLevel[] levels = new CodecProfileLevel[profiles.length];
        for (int i = 0; i < profiles.length; i++) {
            levels[i] = new CodecProfileLevel();
            levels[i].profile = profiles[i];
            levels[i].level = CodecProfileLevel.AVCLevel42;
        }
        CodecCapabilities capabilities = MediaCodecInfoBuilder.CodecCapabilitiesBuilder.newBuilder()
                .setMediaFormat(format)
                .setProfileLevels(levels)
                .setColorFormats(new int[]{CodecCapabilities.COLOR_FormatSurface})
                .build();
        return MediaCodecInfoBuilder.newBuilder()
                .setName(name)
                .setIsVendor(hardware)
                .setIsHardwareAccelerated(hardware)
                .setIsSoftwareOnly(!hardware)
                .setCapabilities(capabilities)
                .build();
    }

    private static MediaCodecInfo hardware(String name, String mime) {
        return decoder(name, mime, true, false, CodecProfileLevel.AVCProfileHigh);
    }

    private static MediaCodecInfo software(String name, String mime) {
        return decoder(name, mime, false, false, CodecProfileLevel.AVCProfileHigh);
    }

    private static MediaFormat avcFormat() {
        return MediaFormat.createVideoFormat("video/avc", 1920, 1080);
    }

    // ---- vendor detection ----

    @Test
    public void vendorIsRecognisedByNamePrefixInAnyCase() {
        for (String name : new String[]{QTI_AVC, QTI_HEVC, OMX_QCOM_AVC, "omx.qcom.video.decoder.avc", "c2.qcom.x"}) {
            assertTrue(name, MediaCodecHelper.isQualcommDecoder(name));
            assertFalse(name, MediaCodecHelper.isNvidiaDecoder(name));
        }
        for (String name : new String[]{MTK_AVC, EXYNOS_AVC, SOFT_AVC, KIRIN_AVC, "c2", ""}) {
            assertFalse(name, MediaCodecHelper.isQualcommDecoder(name));
        }
        assertTrue(MediaCodecHelper.isNvidiaDecoder(NVIDIA_AVC));
        assertTrue(MediaCodecHelper.isNvidiaDecoder("c2.nvidia.h264.decoder"));
    }

    // ---- per-decoder quirks ----

    @Test
    public void directSubmitPrefixes() {
        for (String name : new String[]{QTI_AVC, MTK_AVC, EXYNOS_AVC, SOFT_AVC, OMX_QCOM_AVC, NVIDIA_AVC, INTEL_AVC,
                "OMX.Exynos.avc.dec", "OMX.SEC.avc.dec"}) {
            assertTrue(name, MediaCodecHelper.decoderCanDirectSubmit(name));
        }
        for (String name : new String[]{OLD_SOFT_AVC, OMX_MTK_HEVC, KIRIN_AVC, AMLOGIC_AVC}) {
            assertFalse(name, MediaCodecHelper.decoderCanDirectSubmit(name));
        }
    }

    @Test
    public void bitstreamFixupsAreForOldOmxDecodersOnly() {
        assertTrue(MediaCodecHelper.decoderNeedsSpsBitstreamRestrictions(OMX_QCOM_AVC));
        assertTrue(MediaCodecHelper.decoderNeedsSpsBitstreamRestrictions(NVIDIA_AVC));
        assertFalse(MediaCodecHelper.decoderNeedsSpsBitstreamRestrictions(QTI_AVC));
        assertFalse(MediaCodecHelper.decoderNeedsSpsBitstreamRestrictions(MTK_AVC));

        assertTrue(MediaCodecHelper.decoderNeedsBaselineSpsHack(INTEL_AVC));
        assertTrue(MediaCodecHelper.decoderNeedsConstrainedHighProfile(INTEL_AVC));
        for (String name : new String[]{QTI_AVC, OMX_QCOM_AVC, MTK_AVC, EXYNOS_AVC, SOFT_AVC}) {
            assertFalse(name, MediaCodecHelper.decoderNeedsBaselineSpsHack(name));
            assertFalse(name, MediaCodecHelper.decoderNeedsConstrainedHighProfile(name));
        }
    }

    @Test
    public void softwareDecodersGetFourSlicesHardwareOne() {
        assertEquals(4, MediaCodecHelper.getDecoderOptimalSlicesPerFrame(SOFT_AVC));
        assertEquals(4, MediaCodecHelper.getDecoderOptimalSlicesPerFrame(OLD_SOFT_AVC));
        assertEquals(4, MediaCodecHelper.getDecoderOptimalSlicesPerFrame("OMX.ffmpeg.h264.decoder"));
        assertEquals(1, MediaCodecHelper.getDecoderOptimalSlicesPerFrame(QTI_AVC));
        // Only pre-Adreno-4xx Qualcomm decoders need four slices; this device is newer.
        assertEquals(1, MediaCodecHelper.getDecoderOptimalSlicesPerFrame(OMX_QCOM_AVC));
        assertEquals(1, MediaCodecHelper.getDecoderOptimalSlicesPerFrame(MTK_AVC));
        assertEquals(1, MediaCodecHelper.getDecoderOptimalSlicesPerFrame(EXYNOS_AVC));
    }

    @Test
    public void refFrameInvalidationAvcByDecoderAndHeight() {
        for (int height : new int[]{720, 1080, 2160}) {
            assertTrue(MediaCodecHelper.decoderSupportsRefFrameInvalidationAvc(QTI_AVC, height));
            assertTrue(MediaCodecHelper.decoderSupportsRefFrameInvalidationAvc(OMX_QCOM_AVC, height));
            assertTrue(MediaCodecHelper.decoderSupportsRefFrameInvalidationAvc(MTK_AVC, height));
            assertTrue(MediaCodecHelper.decoderSupportsRefFrameInvalidationAvc(NVIDIA_AVC, height));
            assertFalse(MediaCodecHelper.decoderSupportsRefFrameInvalidationAvc(EXYNOS_AVC, height));
            assertFalse(MediaCodecHelper.decoderSupportsRefFrameInvalidationAvc(SOFT_AVC, height));
            assertFalse(MediaCodecHelper.decoderSupportsRefFrameInvalidationAvc(INTEL_AVC, height));
        }
    }

    @Test
    public void refFrameInvalidationHevcByNameOrLowLatencyFeature() {
        assertTrue(MediaCodecHelper.decoderSupportsRefFrameInvalidationHevc(hardware(QTI_HEVC, "video/hevc")));
        assertTrue(MediaCodecHelper.decoderSupportsRefFrameInvalidationHevc(hardware(EXYNOS_HEVC, "video/hevc")));
        assertFalse(MediaCodecHelper.decoderSupportsRefFrameInvalidationHevc(software(SOFT_HEVC, "video/hevc")));
        assertFalse(MediaCodecHelper.decoderSupportsRefFrameInvalidationHevc(
                hardware("OMX.amlogic.hevc.decoder.awesome", "video/hevc")));
        // A decoder that declares the platform low-latency feature is trusted whatever its name.
        assertTrue(MediaCodecHelper.decoderSupportsRefFrameInvalidationHevc(
                decoder("OMX.amlogic.hevc.decoder.awesome", "video/hevc", true, true)));
    }

    @Test
    public void refFrameInvalidationAv1NeedsTheLowLatencyFeature() {
        assertFalse(MediaCodecHelper.decoderSupportsRefFrameInvalidationAv1(hardware(QTI_AV1, "video/av01")));
        assertTrue(MediaCodecHelper.decoderSupportsRefFrameInvalidationAv1(
                decoder(QTI_AV1, "video/av01", true, true)));
    }

    @Test
    public void hevcWhitelist() {
        assertTrue(MediaCodecHelper.decoderIsWhitelistedForHevc(hardware(QTI_HEVC, "video/hevc")));
        assertTrue(MediaCodecHelper.decoderIsWhitelistedForHevc(hardware(EXYNOS_HEVC, "video/hevc")));
        assertTrue(MediaCodecHelper.decoderIsWhitelistedForHevc(hardware("OMX.qcom.video.decoder.hevc", "video/hevc")));
        assertTrue(MediaCodecHelper.decoderIsWhitelistedForHevc(hardware("OMX.Exynos.hevc.dec", "video/hevc")));
        // An old MediaTek OMX decoder is not trusted by name ...
        assertFalse(MediaCodecHelper.decoderIsWhitelistedForHevc(hardware(OMX_MTK_HEVC, "video/hevc")));
        // ... unless it declares the platform low-latency feature.
        assertTrue(MediaCodecHelper.decoderIsWhitelistedForHevc(decoder(OMX_MTK_HEVC, "video/hevc", true, true)));
    }

    @Test
    public void softwareHevcDecodersAreNeverWhitelisted() {
        assertFalse(MediaCodecHelper.decoderIsWhitelistedForHevc(software(SOFT_HEVC, "video/hevc")));
        // Vendor software decoders are recognised by name even when they claim to be hardware.
        assertFalse(MediaCodecHelper.decoderIsWhitelistedForHevc(
                hardware("OMX.qcom.video.decoder.hevcswvdec", "video/hevc")));
        assertFalse(MediaCodecHelper.decoderIsWhitelistedForHevc(hardware("OMX.SEC.hevc.sw.dec", "video/hevc")));
        assertFalse(MediaCodecHelper.decoderIsWhitelistedForHevc(
                decoder(SOFT_HEVC, "video/hevc", false, true)));
    }

    @Test
    public void av1NeedsAHardwareDecoder() {
        assertTrue(MediaCodecHelper.isDecoderWhitelistedForAv1(hardware(QTI_AV1, "video/av01")));
        assertTrue(MediaCodecHelper.isDecoderWhitelistedForAv1(hardware("c2.mtk.av1.decoder", "video/av01")));
        assertFalse(MediaCodecHelper.isDecoderWhitelistedForAv1(software("c2.android.av1.decoder", "video/av01")));
        assertFalse(MediaCodecHelper.isDecoderWhitelistedForAv1(hardware("c2.vendor.av1.sw.decoder", "video/av01")));
    }

    @Test
    public void adaptivePlaybackFollowsTheFeatureExceptOnBlacklistedDecoders() {
        MediaFormat format = new MediaFormat();
        format.setString(MediaFormat.KEY_MIME, "video/avc");
        format.setFeatureEnabled(CodecCapabilities.FEATURE_AdaptivePlayback, true);
        CodecCapabilities adaptive = MediaCodecInfoBuilder.CodecCapabilitiesBuilder.newBuilder()
                .setMediaFormat(format).setProfileLevels(new CodecProfileLevel[0])
                .setColorFormats(new int[]{CodecCapabilities.COLOR_FormatSurface}).build();
        MediaCodecInfo qti = MediaCodecInfoBuilder.newBuilder().setName(QTI_AVC).setCapabilities(adaptive).build();
        MediaCodecInfo intel = MediaCodecInfoBuilder.newBuilder().setName(INTEL_AVC).setCapabilities(adaptive).build();
        MediaCodecInfo oldMtk = MediaCodecInfoBuilder.newBuilder().setName("OMX.MTK.VIDEO.DECODER.AVC")
                .setCapabilities(adaptive).build();

        assertTrue(MediaCodecHelper.decoderSupportsAdaptivePlayback(qti, "video/avc"));
        assertTrue(MediaCodecHelper.decoderSupportsFusedIdrFrame(qti, "video/avc"));
        assertFalse(MediaCodecHelper.decoderSupportsAdaptivePlayback(intel, "video/avc"));
        assertFalse(MediaCodecHelper.decoderSupportsAdaptivePlayback(oldMtk, "video/avc"));
        assertFalse(MediaCodecHelper.decoderSupportsAdaptivePlayback(hardware(QTI_AVC, "video/avc"), "video/avc"));
        assertFalse(MediaCodecHelper.decoderSupportsFusedIdrFrame(hardware(QTI_AVC, "video/avc"), "video/avc"));
    }

    // ---- low-latency options: each vendor gets its own keys, in a ladder of retries ----

    private static boolean hasKeyStartingWith(MediaFormat format, String prefix) {
        for (String key : format.getKeys()) {
            if (key.startsWith(prefix)) {
                return true;
            }
        }
        return false;
    }

    @Test
    public void qualcommDecodersGetTheirLowLatencyKeys() {
        MediaFormat format = avcFormat();

        assertTrue(MediaCodecHelper.setDecoderLowLatencyOptions(format, hardware(QTI_AVC, "video/avc"), false, 0));

        assertEquals(1, format.getInteger("low-latency"));
        assertEquals(1, format.getInteger("vendor.qti-ext-dec-low-latency.enable"));
        assertEquals(1, format.getInteger("vendor.qti-ext-dec-picture-order.enable"));
        assertEquals(Short.MAX_VALUE, format.getInteger(MediaFormat.KEY_OPERATING_RATE));
        assertFalse(hasKeyStartingWith(format, "vendor.mtk"));
        assertFalse(hasKeyStartingWith(format, "vendor.rtc"));
        assertFalse(hasKeyStartingWith(format, "vendor.hisi"));
    }

    @Test
    public void oldQualcommOmxDecodersDisablePictureReordering() {
        MediaFormat format = avcFormat();

        MediaCodecHelper.setDecoderLowLatencyOptions(format, hardware(OMX_QCOM_AVC, "video/avc"), false, 0);

        assertEquals(0, format.getInteger("vendor.qti-ext-dec-picture-order.enable"));
        assertEquals(1, format.getInteger("vendor.qti-ext-dec-low-latency.enable"));
    }

    @Test
    public void eachRetryDropsTheRiskiestRemainingOptionUntilNoneIsLeft() {
        MediaCodecInfo qti = hardware(QTI_AVC, "video/avc");
        int previousCount = Integer.MAX_VALUE;
        boolean exhausted = false;
        for (int attempt = 0; attempt < 8; attempt++) {
            MediaFormat format = avcFormat();
            int baseline = format.getKeys().size();

            boolean anySet = MediaCodecHelper.setDecoderLowLatencyOptions(format, qti, false, attempt);

            int added = format.getKeys().size() - baseline;
            assertEquals("attempt " + attempt, added > 0, anySet);
            assertTrue("attempt " + attempt + " must not add options back", added <= previousCount);
            if (exhausted) {
                assertEquals("no options may reappear after the ladder ran out", 0, added);
            }
            exhausted |= !anySet;
            previousCount = added;
        }
        assertTrue("the ladder must end so that the decoder is finally tried with no options", exhausted);
    }

    @Test
    public void theGenericLowLatencyKeyIsOnlyInTheFirstAttempt() {
        MediaFormat first = avcFormat();
        MediaFormat second = avcFormat();

        MediaCodecHelper.setDecoderLowLatencyOptions(first, hardware(QTI_AVC, "video/avc"), false, 0);
        MediaCodecHelper.setDecoderLowLatencyOptions(second, hardware(QTI_AVC, "video/avc"), false, 1);

        assertTrue(first.containsKey("low-latency"));
        assertFalse(second.containsKey("low-latency"));
        assertEquals(1, second.getInteger("vendor.qti-ext-dec-low-latency.enable"));
    }

    @Test
    public void aDecoderWithThePlatformFeatureUsesItAloneFirst() {
        MediaCodecInfo lowLatencyDecoder = decoder(QTI_AVC, "video/avc", true, true, CodecProfileLevel.AVCProfileHigh);
        MediaFormat format = avcFormat();

        assertTrue(MediaCodecHelper.setDecoderLowLatencyOptions(format, lowLatencyDecoder, false, 0));

        assertEquals(1, format.getInteger("low-latency"));
        assertFalse(hasKeyStartingWith(format, "vendor."));
    }

    @Test
    public void ultraLowLatencyAddsTheVendorKeysOnTopOfThePlatformFeature() {
        MediaCodecInfo lowLatencyDecoder = decoder(QTI_AVC, "video/avc", true, true, CodecProfileLevel.AVCProfileHigh);
        MediaFormat format = avcFormat();

        assertTrue(MediaCodecHelper.setDecoderLowLatencyOptions(format, lowLatencyDecoder, true, 0));

        assertEquals(1, format.getInteger("low-latency"));
        assertEquals(1, format.getInteger("vendor.qti-ext-dec-low-latency.enable"));
    }

    @Test
    public void exynosKirinAmlogicAndMediaTekGetTheirOwnKeys() {
        MediaFormat exynos = avcFormat();
        MediaCodecHelper.setDecoderLowLatencyOptions(exynos, hardware(EXYNOS_AVC, "video/avc"), false, 0);
        assertEquals(1, exynos.getInteger("vendor.rtc-ext-dec-low-latency.enable"));
        assertFalse(hasKeyStartingWith(exynos, "vendor.qti"));

        MediaFormat kirin = avcFormat();
        MediaCodecHelper.setDecoderLowLatencyOptions(kirin, hardware(KIRIN_AVC, "video/avc"), false, 0);
        assertEquals(1, kirin.getInteger("vendor.hisi-ext-low-latency-video-dec.video-scene-for-low-latency-req"));
        assertFalse(hasKeyStartingWith(kirin, "vendor.qti"));

        MediaFormat amlogic = avcFormat();
        MediaCodecHelper.setDecoderLowLatencyOptions(amlogic, hardware(AMLOGIC_AVC, "video/avc"), false, 0);
        assertEquals(1, amlogic.getInteger("vendor.low-latency.enable"));
        assertFalse(hasKeyStartingWith(amlogic, "vendor.qti"));

        MediaFormat mtk = avcFormat();
        MediaCodecHelper.setDecoderLowLatencyOptions(mtk, hardware(MTK_AVC, "video/avc"), false, 0);
        assertEquals(1, mtk.getInteger("vdec-lowlatency"));
        assertEquals(1, mtk.getInteger("vendor.mtk.vdec.low-latency.mode"));
        assertFalse(hasKeyStartingWith(mtk, "vendor.qti"));
    }

    @Test
    public void softwareDecodersGetNoVendorKeys() {
        MediaFormat format = avcFormat();

        MediaCodecHelper.setDecoderLowLatencyOptions(format, software(SOFT_AVC, "video/avc"), false, 0);

        assertFalse(hasKeyStartingWith(format, "vendor."));
        // Realtime priority instead of the Qualcomm-style maximum operating rate.
        assertEquals(0, format.getInteger(MediaFormat.KEY_PRIORITY));
        assertFalse(format.containsKey(MediaFormat.KEY_OPERATING_RATE));
    }

    @Test
    public void extraVendorOptionsAreVendorScopedAndNullSafe() {
        MediaFormat qti = avcFormat();
        MediaCodecHelper.applyExtraVendorOptions(qti, QTI_AVC);
        assertEquals(1, qti.getInteger("vendor.qti-ext-dec-low-latency.enable"));

        MediaFormat nvidia = avcFormat();
        MediaCodecHelper.applyExtraVendorOptions(nvidia, NVIDIA_AVC);
        assertEquals(1, nvidia.getInteger("vendor.nvidia.disable-output-reorder"));
        assertFalse(hasKeyStartingWith(nvidia, "vendor.qti"));

        MediaFormat soft = avcFormat();
        int before = soft.getKeys().size();
        MediaCodecHelper.applyExtraVendorOptions(soft, SOFT_AVC);
        assertEquals(before, soft.getKeys().size());

        MediaCodecHelper.applyExtraVendorOptions(null, QTI_AVC);
        MediaCodecHelper.applyExtraVendorOptions(avcFormat(), null);
    }

    // ---- choosing a decoder from the device's list ----

    @Test
    public void softwareDecodersAreNeverChosenWhenHardwareExists() {
        ShadowMediaCodecList.addCodec(software(SOFT_AVC, "video/avc"));
        ShadowMediaCodecList.addCodec(hardware(QTI_AVC, "video/avc"));

        assertEquals(QTI_AVC, MediaCodecHelper.findFirstDecoder("video/avc").getName());
        assertEquals(QTI_AVC,
                MediaCodecHelper.findProbableSafeDecoder("video/avc", CodecProfileLevel.AVCProfileHigh).getName());
    }

    @Test
    public void aDeviceWithOnlySoftwareDecodersHasNoDecoder() {
        ShadowMediaCodecList.addCodec(software(SOFT_AVC, "video/avc"));
        ShadowMediaCodecList.addCodec(software(OLD_SOFT_AVC, "video/avc"));

        assertNull(MediaCodecHelper.findFirstDecoder("video/avc"));
        assertNull(MediaCodecHelper.findProbableSafeDecoder("video/avc", -1));
    }

    @Test
    public void blacklistedDecodersAreNeverChosen() {
        ShadowMediaCodecList.addCodec(hardware("OMX.qcom.video.decoder.hevcswvdec", "video/hevc"));
        ShadowMediaCodecList.addCodec(hardware("OMX.SEC.hevc.sw.dec", "video/hevc"));
        ShadowMediaCodecList.addCodec(hardware(QTI_HEVC, "video/hevc"));

        assertEquals(QTI_HEVC, MediaCodecHelper.findFirstDecoder("video/hevc").getName());
        assertEquals(QTI_HEVC, MediaCodecHelper.findProbableSafeDecoder("video/hevc", -1).getName());
    }

    @Test
    public void theMimeTypeDecidesNotTheOrder() {
        ShadowMediaCodecList.addCodec(hardware(QTI_AVC, "video/avc"));
        ShadowMediaCodecList.addCodec(hardware(QTI_HEVC, "video/hevc"));

        assertEquals(QTI_HEVC, MediaCodecHelper.findFirstDecoder("video/hevc").getName());
        assertEquals(QTI_AVC, MediaCodecHelper.findFirstDecoder("VIDEO/AVC").getName());
        assertNull(MediaCodecHelper.findFirstDecoder("video/av01"));
        assertNull(MediaCodecHelper.findProbableSafeDecoder("video/av01", -1));
    }

    @Test
    public void aLowLatencyDecoderIsPreferredOverAnEarlierOneWithoutTheFeature() {
        ShadowMediaCodecList.addCodec(hardware(QTI_AVC, "video/avc"));
        ShadowMediaCodecList.addCodec(
                decoder(QTI_AVC + ".low_latency", "video/avc", true, true, CodecProfileLevel.AVCProfileHigh));

        assertEquals(QTI_AVC + ".low_latency",
                MediaCodecHelper.findProbableSafeDecoder("video/avc", CodecProfileLevel.AVCProfileHigh).getName());
        // The plain "first decoder" fallback keeps the platform's order.
        assertEquals(QTI_AVC, MediaCodecHelper.findFirstDecoder("video/avc").getName());
    }

    @Test
    public void theRequiredProfileBeatsTheLowLatencyPreference() {
        ShadowMediaCodecList.addCodec(
                decoder(QTI_AVC + ".low_latency", "video/avc", true, true, CodecProfileLevel.AVCProfileBaseline));
        ShadowMediaCodecList.addCodec(hardware(QTI_AVC, "video/avc"));

        assertEquals(QTI_AVC,
                MediaCodecHelper.findProbableSafeDecoder("video/avc", CodecProfileLevel.AVCProfileHigh).getName());
    }

    @Test
    public void noDecoderSupportsTheRequiredProfile() {
        ShadowMediaCodecList.addCodec(
                decoder(QTI_AVC, "video/avc", true, false, CodecProfileLevel.AVCProfileBaseline));

        assertNull(MediaCodecHelper.findProbableSafeDecoder("video/avc", CodecProfileLevel.AVCProfileHigh));
    }

    // ---- GPU model parsing that feeds the tables ----

    @Test
    public void adrenoModelParsing() {
        assertEquals(740, MediaCodecHelper.getAdrenoRendererModelNumber("Adreno (TM) 740"));
        assertEquals(620, MediaCodecHelper.getAdrenoRendererModelNumber("Adreno (TM) 620"));
        assertEquals(405, MediaCodecHelper.getAdrenoRendererModelNumber("  adreno (tm) 405  "));
        assertEquals(-1, MediaCodecHelper.getAdrenoRendererModelNumber("Mali-G715"));
        assertEquals(-1, MediaCodecHelper.getAdrenoRendererModelNumber("PowerVR Rogue GX6250"));
        assertEquals(-1, MediaCodecHelper.getAdrenoRendererModelNumber("Adreno"));
    }

    @Test
    public void lowEndSnapdragonIsAZeroInTheMiddleDigit() {
        assertTrue(MediaCodecHelper.isLowEndSnapdragonRenderer("Adreno (TM) 405"));
        assertTrue(MediaCodecHelper.isLowEndSnapdragonRenderer("Adreno (TM) 506"));
        assertFalse(MediaCodecHelper.isLowEndSnapdragonRenderer("Adreno (TM) 740"));
        assertFalse(MediaCodecHelper.isLowEndSnapdragonRenderer("Adreno (TM) 660"));
        assertFalse(MediaCodecHelper.isLowEndSnapdragonRenderer("Mali-G78"));
    }

    @Test
    public void powerVrIsRecognisedInAnyCase() {
        assertTrue(MediaCodecHelper.isPowerVR("PowerVR Rogue GX6250"));
        assertTrue(MediaCodecHelper.isPowerVR("POWERVR SGX 544MP"));
        assertFalse(MediaCodecHelper.isPowerVR("Adreno (TM) 740"));
    }

    @Test
    public void theDecoderDumpListsDecodersAndTheirTypesButNoEncoders() throws Exception {
        ShadowMediaCodecList.addCodec(hardware(QTI_AVC, "video/avc"));
        MediaFormat encoderFormat = new MediaFormat();
        encoderFormat.setString(MediaFormat.KEY_MIME, "video/avc");
        ShadowMediaCodecList.addCodec(MediaCodecInfoBuilder.newBuilder().setName("c2.qti.avc.encoder")
                .setIsEncoder(true)
                .setCapabilities(MediaCodecInfoBuilder.CodecCapabilitiesBuilder.newBuilder()
                        .setMediaFormat(encoderFormat).setIsEncoder(true)
                        .setProfileLevels(new CodecProfileLevel[0])
                        .setColorFormats(new int[]{CodecCapabilities.COLOR_FormatSurface}).build())
                .build());

        String dump = MediaCodecHelper.dumpDecoders();

        assertTrue(dump.contains(QTI_AVC));
        assertTrue(dump.contains("video/avc"));
        assertFalse(dump.contains("c2.qti.avc.encoder"));
    }
}
