package com.limelight.binding.video;

import android.opengl.GLES31;
import android.opengl.GLES32;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;

/**
 * Frame generation, phase 1 (docs/VIDEO_FRAME_GENERATION_RESEARCH.md): between two frames of the game the SGSR stage shows frames the
 * Adreno driver extrapolates from the two newest ones (GL_QCOM_frame_extrapolation), so a 30 fps game fills a 60 or 120 Hz display
 * without the latency of interpolation.
 * <p>
 * The pace is the game's, not the stream's: a decoded frame that repeats the previous one (a 30 fps game in a 60 fps stream) is
 * recognised and left out of the pair, and a generated frame takes its refresh. Every step comes from the same pair, at
 * t = offset / refreshes of the game's frame interval, spread evenly over its refreshes (at most the chosen multiplier per game
 * frame); a generated frame is never fed back in. GL thread only (SgsrRenderer's), with its context current.
 */
final class FrameGenerator implements AutoCloseable {
    /** The game's frame interval is not trusted beyond this many expected intervals (a hitch, a pause): the next frame starts over. */
    private static final int MAX_GAP_INTERVALS = 3;
    /** The repeat test compares the frames in a grid of cells, 4 × 4 samples each. */
    private static final int CELLS_X = 64, CELLS_Y = 36;
    /**
     * A cell changed when one of its samples moved by more than about 3 / 255 (the shader scales the difference by 8); a frame repeats
     * the previous one when at most this many cells changed (re-encoding noise, not a new picture).
     */
    private static final int CHANGED_LEVEL = 24, MAX_CHANGED_CELLS = CELLS_X * CELLS_Y / 200;

    private static final String DIFF_VERTEX = "#version 310 es\n" +
            "void main(){ vec2 p=vec2(float(gl_VertexID&1),float(gl_VertexID>>1)); gl_Position=vec4(p*2.0-1.0,0,1); }";
    private static final String DIFF = "#version 310 es\n" +
            "precision highp float; uniform sampler2D a; uniform sampler2D b; out vec4 color;\n" +
            "void main(){ vec2 cells=vec2(" + CELLS_X + ".0," + CELLS_Y + ".0); vec2 cell=floor(gl_FragCoord.xy); float d=0.0;\n" +
            " for(int j=0;j<4;j++) for(int i=0;i<4;i++){ vec2 uv=(cell+(vec2(i,j)+0.5)/4.0)/cells;\n" +
            "  vec3 e=abs(texture(a,uv).rgb-texture(b,uv).rgb); d=max(d,max(e.r,max(e.g,e.b))); }\n" +
            " color=vec4(min(d*8.0,1.0)); }";

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
    private final int[] recentChanged = new int[12];
    private int recentCount;
    private long generated, repeats;
    private int generatedChanged, pairChanged;
    private int diffProgram, diffFramebuffer, diffTexture, diffA, diffB;
    private final ByteBuffer diffPixels = ByteBuffer.allocateDirect(CELLS_X * CELLS_Y * 4).order(ByteOrder.nativeOrder());

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

    /** Frames generated so far. */
    long generated() { return generated; }

    /** Decoded frames recognised as repeats of the previous one so far. */
    long repeats() { return repeats; }

    /**
     * A decoded frame was drawn into [source] ([w] × [h]). A new picture becomes the newest of the pair and the steps until the next
     * one are planned from the interval to the previous picture and the display refresh ([periodNs]); a repeat of the newest picture
     * changes nothing and returns true (its refresh may show the next generated step instead).
     */
    boolean onRealFrame(int source, int w, int h, long timestampNs, long periodNs) {
        GLES31.glBindTexture(GLES31.GL_TEXTURE_2D, source);
        GLES31.glGetTexLevelParameteriv(GLES31.GL_TEXTURE_2D, 0, GLES31.GL_TEXTURE_INTERNAL_FORMAT, query, 0);
        final int format = query[0];
        if (w != width || h != height || format != internalFormat || ring[0] == 0) allocate(w, h, format);
        if (frames > 0) {
            int changed = changedCells(source, ring[newest]);
            recentChanged[recentCount++ % recentChanged.length] = changed;
            if (changed <= MAX_CHANGED_CELLS) {
                repeats++;
                return true;
            }
        }
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
        return false;
    }

    /** Whether a generated frame belongs before the next picture. */
    boolean hasStep() { return frames >= 2 && nextStep < steps; }

    /** Whether there is a pair to extrapolate from (a repeat of its newest picture adds nothing to show then). */
    boolean paired() { return frames >= 2; }

    /**
     * The refresh after the newest picture that the next step is shown on: the steps spread evenly over the refreshes of one game
     * frame interval (1 .. refreshes − 1).
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
        // Now and then: how far the generated picture moved from the newest one, next to how far the pair moved (a generated
        // picture that never moves while the pair does means the driver only copies)
        if (generated++ % 30 == 0) {
            generatedChanged = changedCells(output, ring[newest]);
            pairChanged = changedCells(ring[1 - newest], ring[newest]);
        }
        return output;
    }

    /** The latest sample: changed cells generated vs newest, the pair's own, and the texture format (for the log). */
    String generatedChanged() { return generatedChanged + " (pair " + pairChanged + ", format 0x" + Integer.toHexString(internalFormat) + ")"; }

    /** Forgets the pair (a new stream size, a mode change): the next decoded frame starts over. */
    void reset() {
        frames = 0;
        newest = -1;
        intervalNs = 0;
        steps = refreshes = nextStep = 1;
    }

    /**
     * How many cells of a 64 × 36 grid differ between [a] and [b]: a small grid of differences drawn on the GPU and read back (about
     * 9 KB, one short wait for this pass).
     */
    private int changedCells(int a, int b) {
        if (diffProgram == 0) {
            diffProgram = SgsrRenderer.program(DIFF_VERTEX, DIFF);
            diffA = GLES31.glGetUniformLocation(diffProgram, "a");
            diffB = GLES31.glGetUniformLocation(diffProgram, "b");
            int[] id = new int[1];
            GLES31.glGenTextures(1, id, 0);
            diffTexture = id[0];
            GLES31.glBindTexture(GLES31.GL_TEXTURE_2D, diffTexture);
            GLES31.glTexStorage2D(GLES31.GL_TEXTURE_2D, 1, GLES31.GL_RGBA8, CELLS_X, CELLS_Y);
            GLES31.glGenFramebuffers(1, id, 0);
            diffFramebuffer = id[0];
            GLES31.glBindFramebuffer(GLES31.GL_FRAMEBUFFER, diffFramebuffer);
            GLES31.glFramebufferTexture2D(GLES31.GL_FRAMEBUFFER, GLES31.GL_COLOR_ATTACHMENT0, GLES31.GL_TEXTURE_2D, diffTexture, 0);
        }
        GLES31.glBindFramebuffer(GLES31.GL_FRAMEBUFFER, diffFramebuffer);
        GLES31.glViewport(0, 0, CELLS_X, CELLS_Y);
        GLES31.glUseProgram(diffProgram);
        GLES31.glActiveTexture(GLES31.GL_TEXTURE0);
        GLES31.glBindTexture(GLES31.GL_TEXTURE_2D, a);
        GLES31.glUniform1i(diffA, 0);
        GLES31.glActiveTexture(GLES31.GL_TEXTURE1);
        GLES31.glBindTexture(GLES31.GL_TEXTURE_2D, b);
        GLES31.glUniform1i(diffB, 1);
        GLES31.glDrawArrays(GLES31.GL_TRIANGLE_STRIP, 0, 4);
        GLES31.glActiveTexture(GLES31.GL_TEXTURE0);
        diffPixels.clear();
        GLES31.glReadPixels(0, 0, CELLS_X, CELLS_Y, GLES31.GL_RGBA, GLES31.GL_UNSIGNED_BYTE, diffPixels);
        int changed = 0;
        for (int i = 0; i < CELLS_X * CELLS_Y; i++) {
            if ((diffPixels.get(i * 4) & 0xFF) > CHANGED_LEVEL) changed++;
        }
        return changed;
    }

    /** The changed cells of the latest repeat tests, oldest first (for the log). */
    String recentChangedCells() {
        StringBuilder out = new StringBuilder();
        int n = Math.min(recentCount, recentChanged.length);
        for (int i = recentCount - n; i < recentCount; i++) out.append(i > recentCount - n ? " " : "").append(recentChanged[i % recentChanged.length]);
        return out.toString();
    }

    private void allocate(int w, int h, int format) {
        deleteRing();
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

    private void deleteRing() {
        if (ring[0] != 0) GLES31.glDeleteTextures(3, new int[]{ring[0], ring[1], output}, 0);
        ring[0] = ring[1] = output = 0;
    }

    @Override
    public void close() {
        deleteRing();
        if (diffProgram != 0) GLES31.glDeleteProgram(diffProgram);
        if (diffTexture != 0) GLES31.glDeleteTextures(1, new int[]{diffTexture}, 0);
        if (diffFramebuffer != 0) GLES31.glDeleteFramebuffers(1, new int[]{diffFramebuffer}, 0);
        diffProgram = diffTexture = diffFramebuffer = 0;
    }

    private static native boolean nativeInit();
    private static native int nativeExtrapolate(int older, int newer, int output, float scale);
}
