package com.limelight.binding.video;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import android.view.Surface;
import android.view.SurfaceHolder;
import android.view.SurfaceView;

import com.limelight.binding.video.StreamSurfaceController.Resize;
import com.limelight.nvstream.jni.MoonBridge;
import com.limelight.preferences.PreferenceConfiguration;
import com.limelight.preferences.UpscalerMode;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.InOrder;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;

/** Characterisation tests: the surface and upscaler decisions DelosStreamActivity made before the controller was extracted. */
@RunWith(RobolectricTestRunner.class)
@Config(sdk = {33})
public class StreamSurfaceControllerTest {
    private static final int SDR = MoonBridge.VIDEO_FORMAT_H265;
    private static final int HDR = MoonBridge.VIDEO_FORMAT_H265_MAIN10;

    private final PreferenceConfiguration prefs = new PreferenceConfiguration();
    private final SurfaceHolder holder = mock(SurfaceHolder.class);
    private final SurfaceView view = mock(SurfaceView.class);
    private final MediaCodecDecoderRenderer decoder = mock(MediaCodecDecoderRenderer.class);
    private final FakeHost host = new FakeHost();
    private StreamSurfaceController controller;

    private final class FakeHost implements StreamSurfaceController.Host {
        SurfaceView surfaceView = view;
        final Surface surface = mock(Surface.class);
        boolean connected;
        final List<String> calls = new ArrayList<>();

        @Override public SurfaceView streamSurfaceView() { return surfaceView; }
        @Override public Surface activeSurface() { return surface; }
        @Override public int displayWidth() { return 1920; }
        @Override public int displayHeight() { return 1080; }
        @Override public boolean isConnected() { return connected; }
        @Override public void startConnection() { calls.add("start"); }
        @Override public void stopConnection() { calls.add("stop"); }
    }

    @Before
    public void setUp() {
        when(view.getHolder()).thenReturn(holder);
        prefs.width = 1280;
        prefs.height = 720;
        prefs.sgsrMode = UpscalerMode.STANDARD;
        controller = new StreamSurfaceController(Runnable::run, prefs, host);
        controller.setDecoder(decoder);
    }

    // --- Pure decisions ---

    @Test
    public void onlySdrScalingUpscalerStartsLayoutSized() {
        for (UpscalerMode mode : UpscalerMode.values()) {
            assertEquals(mode.toString(), mode.scales(), StreamSurfaceController.layoutSizedAtStart(true, mode, false));
            assertFalse(mode.toString(), StreamSurfaceController.layoutSizedAtStart(true, mode, true));
            assertFalse(mode.toString(), StreamSurfaceController.layoutSizedAtStart(false, mode, false));
        }
    }

    @Test
    public void liveUpscalerResizeFollowsModeOnlyOnEightBitOutput() {
        assertEquals(Resize.LAYOUT, StreamSurfaceController.resizeForUpscaler(SDR, true, UpscalerMode.STANDARD));
        assertEquals(Resize.LAYOUT, StreamSurfaceController.resizeForUpscaler(SDR, true, UpscalerMode.VIDEO_QUALITY));
        assertEquals(Resize.STREAM, StreamSurfaceController.resizeForUpscaler(SDR, true, UpscalerMode.SHARPEN_ONLY));
        assertEquals(Resize.STREAM, StreamSurfaceController.resizeForUpscaler(0, true, UpscalerMode.SHARPEN_ONLY));
        assertEquals(Resize.NONE, StreamSurfaceController.resizeForUpscaler(HDR, true, UpscalerMode.STANDARD));
        assertEquals(Resize.NONE, StreamSurfaceController.resizeForUpscaler(HDR, true, UpscalerMode.SHARPEN_ONLY));
        assertEquals(Resize.NONE, StreamSurfaceController.resizeForUpscaler(MoonBridge.VIDEO_FORMAT_AV1_MAIN10, true, UpscalerMode.HIGH));
        // Off: back to the stream's size whatever the mode, except on 10-bit output
        for (UpscalerMode mode : UpscalerMode.values()) {
            assertEquals(mode.toString(), Resize.STREAM, StreamSurfaceController.resizeForUpscaler(SDR, false, mode));
            assertEquals(mode.toString(), Resize.NONE, StreamSurfaceController.resizeForUpscaler(HDR, false, mode));
        }
    }

    @Test
    public void streamSurfaceSizeFallsBackToTheDisplay() {
        assertEquals(1280, StreamSurfaceController.streamSurfaceSize(1280, 1920));
        assertEquals(1920, StreamSurfaceController.streamSurfaceSize(0, 1920));
        assertEquals(1920, StreamSurfaceController.streamSurfaceSize(-1, 1920));
    }

    // --- Live upscaler controls ---

    @Test
    public void enablingAScalingModeSizesTheSurfaceFromLayoutBeforeTheDecoder() {
        when(decoder.getActiveVideoFormat()).thenReturn(SDR);
        controller.setUpscalingEnabled(true);
        InOrder order = inOrder(holder, decoder);
        order.verify(holder).setSizeFromLayout();
        order.verify(decoder).setUpscalingEnabled(true);
        verify(holder, never()).setFixedSize(1280, 720);
    }

    @Test
    public void enablingSharpenOnlyKeepsTheStreamSize() {
        prefs.sgsrMode = UpscalerMode.SHARPEN_ONLY;
        prefs.width = 0;
        when(decoder.getActiveVideoFormat()).thenReturn(SDR);
        controller.setUpscalingEnabled(true);
        InOrder order = inOrder(holder, decoder);
        order.verify(holder).setFixedSize(1920, 720);
        order.verify(decoder).setUpscalingEnabled(true);
        verify(holder, never()).setSizeFromLayout();
    }

    @Test
    public void disablingAScalingModeSetsTheSurfaceBackToTheStreamSizeBeforeTheDecoder() {
        when(decoder.getActiveVideoFormat()).thenReturn(SDR);
        controller.setUpscalingEnabled(true);
        controller.setUpscalingEnabled(false);
        InOrder order = inOrder(holder, decoder);
        order.verify(holder).setSizeFromLayout();
        order.verify(decoder).setUpscalingEnabled(true);
        order.verify(holder).setFixedSize(1280, 720);
        order.verify(decoder).setUpscalingEnabled(false);
    }

    @Test
    public void tenBitOutputLeavesTheSurfaceAlone() {
        when(decoder.getActiveVideoFormat()).thenReturn(HDR);
        controller.setUpscalingEnabled(true);
        controller.setUpscalingEnabled(false);
        verify(decoder).setUpscalingEnabled(true);
        verify(decoder).setUpscalingEnabled(false);
        verifyNoInteractions(holder);
    }

    @Test
    public void noSurfaceViewSkipsTheResizeWithoutReadingTheFormat() {
        host.surfaceView = null;
        controller.setUpscalingEnabled(true);
        verify(decoder, never()).getActiveVideoFormat();
        verify(decoder).setUpscalingEnabled(true);
    }

    @Test
    public void modeChangeResizesOnlyWhileTheUpscalerIsRequested() {
        when(decoder.getActiveVideoFormat()).thenReturn(SDR);
        controller.setUpscalingMode(UpscalerMode.SHARPEN_ONLY.index());
        verify(decoder).setUpscalingMode(UpscalerMode.SHARPEN_ONLY.index());
        verifyNoInteractions(holder);

        when(decoder.isUpscalingRequested()).thenReturn(true);
        controller.setUpscalingMode(UpscalerMode.SHARPEN_ONLY.index());
        InOrder order = inOrder(holder, decoder);
        order.verify(holder).setFixedSize(1280, 720);
        order.verify(decoder).setUpscalingMode(UpscalerMode.SHARPEN_ONLY.index());

        controller.setUpscalingMode(UpscalerMode.VIDEO.index());
        order.verify(holder).setSizeFromLayout();
        order.verify(decoder).setUpscalingMode(UpscalerMode.VIDEO.index());
    }

    @Test
    public void sharpnessAndSupersamplingLevelNeverResizeTheSurface() {
        when(decoder.getActiveVideoFormat()).thenReturn(SDR);
        when(decoder.isUpscalingRequested()).thenReturn(true);
        controller.setSupersamplingLevel(2);
        controller.setSupersamplingLevel(1);
        controller.setUpscalingSharpness(70);
        verify(decoder).setSupersamplingLevel(2);
        verify(decoder).setSupersamplingLevel(1);
        verify(decoder).setUpscalingSharpness(70);
        verifyNoInteractions(view);
    }

    @Test
    public void readsComeFromTheDecoder() {
        when(decoder.isUpscalingRequested()).thenReturn(true);
        when(decoder.isPyroWave()).thenReturn(true);
        when(decoder.getUpscalingStatus()).thenReturn(UpscalerStatus.ON);
        when(decoder.isSupersamplingMode()).thenReturn(true);
        when(decoder.getSupersamplingLevel()).thenReturn(2);
        assertTrue(controller.isUpscalingRequested());
        assertTrue(controller.isPyroWave());
        assertEquals(UpscalerStatus.ON, controller.getUpscalingStatus());
        assertTrue(controller.isSupersamplingMode());
        assertEquals(2, controller.getSupersamplingLevel());
        assertSame(view, controller.getStreamSurface());
    }

    // --- Surface callbacks ---

    @Test
    public void firstAvailableSurfaceSetsTheRenderTargetThenStartsTheConnectionOnce() {
        controller.onSurfaceAvailable();
        controller.onSurfaceAvailable();
        verify(decoder).setRenderTarget(host.surface);
        assertEquals(List.of("start"), host.calls);
    }

    @Test
    public void surfaceCallbacksBeforeCreationThrow() {
        assertThrows(IllegalStateException.class, () -> controller.onSurfaceChanged(1920, 1080));
        assertThrows(IllegalStateException.class, () -> controller.onSurfaceDestroyed());
    }

    @Test
    public void destroyedSurfaceStopsTheDecoderAndAConnectedStream() {
        SurfaceHolder created = mock(SurfaceHolder.class);
        when(created.getSurface()).thenReturn(mock(Surface.class));
        controller.onSurfaceCreated(created, 60f);
        controller.onSurfaceChanged(1920, 1080);

        // No connection attempt yet: nothing to stop
        controller.onSurfaceDestroyed();
        verify(decoder, never()).prepareForStop();

        controller.onSurfaceAvailable();
        controller.onSurfaceDestroyed();
        verify(decoder).prepareForStop();
        assertEquals(List.of("start"), host.calls);

        host.connected = true;
        controller.onSurfaceDestroyed();
        assertEquals(List.of("start", "stop"), host.calls);
    }

    @Test
    public void hdrModeGoesToTheDecoder() {
        byte[] metadata = {1, 2};
        controller.setHdrMode(true, metadata);
        verify(decoder).setHdrMode(true, metadata);
    }

    // --- Decoder thread to UI thread handshake ---

    /** A wait whose time limit has already passed: the timeout case without waiting for a real clock. */
    private static final StreamSurfaceController.Waiter TIMED_OUT = (done, timeoutMs) -> false;

    @Test
    public void layoutSizedSurfaceRunsOnTheUiExecutorAndWaitsForIt() throws Exception {
        List<Runnable> posted = java.util.Collections.synchronizedList(new ArrayList<>());
        CountDownLatch waiting = new CountDownLatch(1);
        AtomicLong limitMs = new AtomicLong();
        // The limit never runs out in this test, so only the UI thread's work can end the wait.
        StreamSurfaceController.Waiter untilDone = (done, timeoutMs) -> {
            limitMs.set(timeoutMs);
            waiting.countDown();
            done.await();
            return true;
        };
        StreamSurfaceController posting = new StreamSurfaceController(posted::add, prefs, host, untilDone);
        AtomicBoolean returned = new AtomicBoolean();
        Thread decoderThread = new Thread(() -> {
            posting.useLayoutSizedStreamSurface();
            returned.set(true);
        });
        decoderThread.start();

        assertTrue("the decoder thread never started waiting", waiting.await(30, TimeUnit.SECONDS));
        assertEquals(1, posted.size());
        assertFalse("the decoder thread waits for the UI thread", returned.get());
        verify(holder, never()).setSizeFromLayout();
        assertTrue("the wait for the UI thread must be bounded", limitMs.get() > 0);
        assertEquals(StreamSurfaceController.RESIZE_WAIT_MS, limitMs.get());

        posted.get(0).run();
        decoderThread.join(30_000);
        assertTrue(returned.get());
        verify(holder).setSizeFromLayout();
    }

    @Test
    public void handshakeGivesUpAfterTheBoundedWait() {
        AtomicLong limitMs = new AtomicLong();

        // The UI thread never runs the task and the limit passes.
        assertFalse(StreamSurfaceController.runAndAwait(task -> { }, () -> { }, 100, (done, timeoutMs) -> {
            limitMs.set(timeoutMs);
            return false;
        }));

        assertEquals(100, limitMs.get());
    }

    @Test
    public void theRealWaitReturnsWhenItsLimitPasses() {
        // The production wait, with a limit so short that the test does not depend on its length.
        assertFalse(StreamSurfaceController.runAndAwait(task -> { }, () -> { }, 1));
    }

    @Test
    public void aMissedHandshakeStillResizesWhenTheUiThreadGetsToIt() {
        List<Runnable> posted = new ArrayList<>();
        AtomicBoolean resized = new AtomicBoolean();

        // The UI thread is busy for longer than the decoder waits
        assertFalse(StreamSurfaceController.runAndAwait(posted::add, () -> resized.set(true), 50, TIMED_OUT));
        assertFalse(resized.get());

        // The request was not withdrawn: it applies as soon as the UI thread runs it
        assertEquals(1, posted.size());
        posted.get(0).run();
        assertTrue(resized.get());
    }

    @Test
    public void handshakeInTimeReportsTheResize() {
        assertTrue(StreamSurfaceController.runAndAwait(Runnable::run, () -> { }, 5000));
    }

    @Test
    public void failedResizeStillReleasesTheDecoderThread() {
        Executor otherThread = task -> new Thread(task).start();

        // Released by the failed task itself, not by the time limit (which is far away).
        assertTrue(StreamSurfaceController.runAndAwait(otherThread,
                () -> { throw new IllegalStateException("boom"); }, 600_000));
    }

    @Test
    public void interruptEndsTheWaitAndStaysSet() {
        Thread.currentThread().interrupt();
        assertFalse(StreamSurfaceController.runAndAwait(task -> { }, () -> { }, 5000));
        assertTrue(Thread.interrupted());
    }

    @Test
    public void noSurfaceViewStillReleasesTheHandshake() {
        host.surfaceView = null;
        controller.useLayoutSizedStreamSurface();
        verifyNoInteractions(holder);
    }

    // --- Launch arrival ---

    @Test
    public void launchArrivalBeforeTheDecoderExists() {
        StreamSurfaceController early = new StreamSurfaceController(Runnable::run, prefs, host);
        assertFalse(early.isLaunchFrameReady());
        assertFalse(early.isLaunchArrivalReady());
        assertTrue(early.isLaunchArrivalComplete());
        assertNull(early.getLaunchArrivalError());
        assertNull(early.getLinkMonitor());
        early.setLaunchArrivalProgress(.5f);
        early.reportLaunchArrivalError("none");
    }

    @Test
    public void launchArrivalFollowsTheDecoder() {
        when(decoder.hasPresentedFrame()).thenReturn(true);
        when(decoder.isLaunchArrivalReady()).thenReturn(true);
        when(decoder.isLaunchArrivalComplete()).thenReturn(false);
        when(decoder.getLaunchArrivalError()).thenReturn("gpu");
        assertTrue(controller.isLaunchFrameReady());
        assertTrue(controller.isLaunchArrivalReady());
        assertFalse(controller.isLaunchArrivalComplete());
        assertEquals("gpu", controller.getLaunchArrivalError());

        controller.setLaunchArrivalProgress(.25f);
        controller.setLaunchArrivalProgress(1f);
        controller.reportLaunchArrivalError("timed out");
        InOrder order = inOrder(decoder);
        order.verify(decoder).setLaunchArrivalProgress(.25f);
        order.verify(decoder).setLaunchArrivalProgress(1f);
        order.verify(decoder).setLaunchArrivalProgress(-1f);
    }
}
