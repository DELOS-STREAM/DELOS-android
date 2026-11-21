package com.limelight.binding.video;

import android.opengl.GLES31;
import android.opengl.GLES32;

/**
 * Frame generation, phase 1 (docs/VIDEO_FRAME_GENERATION_RESEARCH.md): between two decoded frames the SGSR stage shows frames the
 * Adreno driver extrapolates from the two newest ones (GL_QCOM_frame_extrapolation), so a 30 fps stream fills a 60 or 120 Hz
 * display without the latency of interpolation. Every step comes from the same pair, at t = k / n of the source interval, where n is
 * the number of display refreshes in that interval (at most the chosen multiplier); a generated frame is never fed back in.
 * GL thread only (SgsrRenderer's), with its context current.
 */
final class FrameGenerator implements AutoCloseable {
    /** The source interval is not trusted beyond this many expected intervals (a hitch, a pause): the next frame starts over. */
    private static final int MAX_GAP_INTERVALS = 3;

    private final int[] ring = new int[2];
    private int output;
    private int newest = -1;
    private int frames;
    private int width, height, internalFormat;
    private long newestTimestampNs;
    private long intervalNs;
    private int steps, nextStep, refreshes;
    private final int[] query = new int[1];
    private volatile int multiplier;
    private long generated;

    private FrameGenerator(int multiplier) {
        this.multiplier = multiplier;
    }

    /** A generator when the driver offers frame extrapolation in the current context, null otherwise. */
    static FrameGenerator create(int multiplier) {
        try {
            System.loadLibrary("moonlight-core");
            return nativeInit() ? new FrameGenerator(multiplier) : null;
        } catch (UnsatisfiedLinkError e) {
            return null;
        }
    }

    void setMultiplier(int multiplier) { this.multiplier = multiplier; }

    int multiplier() { return multiplier; }

    /** Frames generated so far (the statistics read the rate from it). */
    long generated() { return generated; }

    /**
     * A decoded frame was drawn into [source] ([w] × [h]): it becomes the newest of the pair and the steps until the next one are
     * planned from the interval to the previous frame and the display refresh ([periodNs]).
     */
    void onRealFrame(int source, int w, int h, long timestampNs, long periodNs) {
        GLES31.glBindTexture(GLES31.GL_TEXTURE_2D, source);
        GLES31.glGetTexLevelParameteriv(GLES31.GL_TEXTURE_2D, 0, GLES31.GL_TEXTURE_INTERNAL_FORMAT, query, 0);
        final int format = query[0];
        if (w != width || h != height || format != internalFormat || ring[0] == 0) allocate(w, h, format);
        int slot = newest < 0 ? 0 : 1 - newest;
        GLES32.glCopyImageSubData(source, GLES31.GL_TEXTURE_2D, 0, 0, 0, 0, ring[slot], GLES31.GL_TEXTURE_2D, 0, 0, 0, 0, w, h, 1);
        long interval = frames > 0 ? timestampNs - newestTimestampNs : 0;
        newest = slot;
        newestTimestampNs = timestampNs;
        steps = refreshes = nextStep = 1;
        if (frames > 0 && interval > 0 && periodNs > 0 && (intervalNs == 0 || interval <= intervalNs * MAX_GAP_INTERVALS)) {
            intervalNs = intervalNs == 0 ? interval : (intervalNs * 3 + interval) / 4;
            refreshes = Math.max(1, Math.round(interval / (float) periodNs));
            steps = Math.min(refreshes, multiplier);
            frames++;
        } else {
            // The first frame, or one after a gap: no pair to extrapolate from yet
            frames = 1;
            intervalNs = 0;
        }
    }

    /** Whether a generated frame belongs before the next decoded one. */
    boolean hasStep() { return frames >= 2 && nextStep < steps; }

    /**
     * The refresh after the decoded frame that the next step is shown on: the steps spread evenly over the refreshes of one source
     * interval (1 .. refreshes − 1).
     */
    int nextOffset() { return Math.round(nextStep * refreshes / (float) steps); }

    /** The next step is not shown (a decoded frame or a busy GPU came first, or its refresh has passed). */
    void skipStep() { nextStep++; }

    /** Generates the next step into the output texture and returns it; a driver error is returned as an exception. */
    int generate() {
        float t = nextOffset() / (float) refreshes;
        nextStep++;
        int error = nativeExtrapolate(ring[1 - newest], ring[newest], output, t);
        if (error != 0) throw new IllegalStateException("glExtrapolateTex2DQCOM error 0x" + Integer.toHexString(error));
        generated++;
        return output;
    }

    /** Forgets the pair (a new stream size, a mode change): the next decoded frame starts over. */
    void reset() {
        frames = 0;
        newest = -1;
        intervalNs = 0;
        steps = nextStep = 1;
    }

    private void allocate(int w, int h, int format) {
        close();
        int[] ids = new int[3];
        GLES31.glGenTextures(3, ids, 0);
        for (int id : ids) {
            GLES31.glBindTexture(GLES31.GL_TEXTURE_2D, id);
            GLES31.glTexStorage2D(GLES31.GL_TEXTURE_2D, 1, format, w, h);
            GLES31.glTexParameteri(GLES31.GL_TEXTURE_2D, GLES31.GL_TEXTURE_MIN_FILTER, GLES31.GL_LINEAR);
            GLES31.glTexParameteri(GLES31.GL_TEXTURE_2D, GLES31.GL_TEXTURE_MAG_FILTER, GLES31.GL_LINEAR);
            GLES31.glTexParameteri(GLES31.GL_TEXTURE_2D, GLES31.GL_TEXTURE_WRAP_S, GLES31.GL_CLAMP_TO_EDGE);
            GLES31.glTexParameteri(GLES31.GL_TEXTURE_2D, GLES31.GL_TEXTURE_WRAP_T, GLES31.GL_CLAMP_TO_EDGE);
        }
        ring[0] = ids[0];
        ring[1] = ids[1];
        output = ids[2];
        width = w;
        height = h;
        internalFormat = format;
        reset();
    }

    @Override
    public void close() {
        if (ring[0] != 0) GLES31.glDeleteTextures(3, new int[]{ring[0], ring[1], output}, 0);
        ring[0] = ring[1] = output = 0;
    }

    private static native boolean nativeInit();
    private static native int nativeExtrapolate(int older, int newer, int output, float scale);
}
