package com.limelight.binding.video;

import android.opengl.GLES30;
import android.opengl.GLES31;
import android.opengl.GLES32;

import com.limelight.LimeLog;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;

/**
 * Frame generation, phase 1 (docs/VIDEO_FRAME_GENERATION_RESEARCH.md): between two pictures of the game the SGSR stage shows frames
 * predicted from the two newest ones, so a 30 fps game fills a 60 or 120 Hz display without the latency of interpolation.
 * <p>
 * Two ways to predict, both on the Adreno driver: its frame extrapolation (GL_QCOM_frame_extrapolation), or an own warp of the
 * newest picture along the block motion its motion estimation (GL_QCOM_motion_estimation) finds between the pair. The driver's
 * extrapolation is tried first; when its frames do not move while the pair does, the warp takes over.
 * <p>
 * The pace is the game's, not the stream's: a decoded frame that repeats the previous one (a 30 fps game in a 60 fps stream) is
 * recognised and left out of the pair, and a generated frame takes its refresh. Every step comes from the same pair, at
 * t = offset / refreshes of the game's frame interval, spread evenly over its refreshes (at most the chosen multiplier per game
 * frame); a generated frame is never fed back in. GL thread only (SgsrRenderer's), with its context current.
 */
final class FrameGenerator implements AutoCloseable {
    private static final int OFFERS_EXTRAPOLATION = 1, OFFERS_MOTION_ESTIMATION = 2;
    private static final int GL_MOTION_ESTIMATION_SEARCH_BLOCK_X_QCOM = 0x8C90, GL_MOTION_ESTIMATION_SEARCH_BLOCK_Y_QCOM = 0x8C91;
    /** The game's frame interval is not trusted beyond this many expected intervals (a hitch, a pause): the next frame starts over. */
    private static final int MAX_GAP_INTERVALS = 3;
    /** The difference grid: cells of 4 × 4 samples each. */
    private static final int CELLS_X = 64, CELLS_Y = 36;
    /**
     * A cell changed when one of its samples moved by more than about 3 / 255 (the shader scales the difference by 8). A frame repeats
     * the previous picture when at most [MAX_CHANGED_CELLS] cells changed (re-encoding noise), or, after a picture that moved, when
     * fewer than [REFINE_PERCENT] % of the cells that picture changed did: on fast motion the console's encoder sends the repeat as a
     * refinement of the same picture (about 15–20 % of the cells, observed on the Odin), not a new one.
     */
    private static final int CHANGED_LEVEL = 24, MAX_CHANGED_CELLS = CELLS_X * CELLS_Y / 200, REFINE_PERCENT = 35;
    /** A pair that moved at least this many cells tells whether generated frames move and which way the motion vectors point. */
    private static final int MOVING_PAIR_CELLS = 60;
    /** The driver's step moved less than this share (in tenths) of the pair's changed cells: it did not follow the motion. */
    private static final int STUCK_TENTHS = 3;

    private static final String FULLSCREEN = "#version 310 es\n" +
            "void main(){ vec2 p=vec2(float(gl_VertexID&1),float(gl_VertexID>>1)); gl_Position=vec4(p*2.0-1.0,0,1); }";
    private static final String DIFF = "#version 310 es\n" +
            "precision highp float; uniform sampler2D a; uniform sampler2D b; out vec4 color;\n" +
            "void main(){ vec2 cells=vec2(" + CELLS_X + ".0," + CELLS_Y + ".0); vec2 cell=floor(gl_FragCoord.xy); float d=0.0;\n" +
            " for(int j=0;j<4;j++) for(int i=0;i<4;i++){ vec2 uv=(cell+(vec2(i,j)+0.5)/4.0)/cells;\n" +
            "  vec3 e=abs(texture(a,uv).rgb-texture(b,uv).rgb); d=max(d,max(e.r,max(e.g,e.b))); }\n" +
            " color=vec4(min(d*8.0,1.0)); }";
    private static final String LUMA = "#version 310 es\n" +
            "precision highp float; uniform sampler2D src; uniform vec2 outSize; out vec4 color;\n" +
            "void main(){ vec3 c=texture(src,gl_FragCoord.xy/outSize).rgb; color=vec4(dot(c,vec3(0.299,0.587,0.114)),0.0,0.0,1.0); }";
    // The newest picture, sampled back along the motion: a pixel that moved by v over the pair moves by v·t over the step
    private static final String WARP = "#version 310 es\n" +
            "precision highp float; uniform sampler2D src; uniform sampler2D motion; uniform vec2 outSize; uniform vec2 scale;\n" +
            "uniform float t; out vec4 color;\n" +
            "void main(){ vec2 uv=gl_FragCoord.xy/outSize; vec2 v=texture(motion,uv).rg*scale; color=texture(src,uv-v*t); }";

    private enum Mode { DRIVER, WARP }

    private final int offered;
    private Mode mode;
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
    private long generated, repeats;
    private final int[] recentChanged = new int[12];
    private int recentCount;
    // Per pair: how far it moved (cells, from the repeat test), and whether its steps use the warp because the driver's did not move
    private int pairCells, lastPictureCells;
    private long refinements;
    // Whether the previous decoded frame repeated or refined its picture
    private boolean lastWasRepeat;
    private boolean pairChecked, pairWarp;
    // For the log: pairs checked and stuck, the latest check, the warp's latest move, how many refreshes the game's frames took
    private long movingPairs, stuckPairs, warpSamples;
    private int generatedChanged, pairChanged, warpMoved, warpPair;
    private final int[] intervalRefreshes = new int[7];
    private int framebuffer, diffProgram, diffTexture;
    private final ByteBuffer diffPixels = ByteBuffer.allocateDirect(CELLS_X * CELLS_Y * 4).order(ByteOrder.nativeOrder());
    // The warp: a luma copy per ring slot, the motion between the pair, and the vectors' orientation (found on a moving pair)
    private final int[] luma = new int[2];
    private int lumaWidth, lumaHeight, motion, lumaProgram, warpProgram;
    private boolean motionReady;
    private float signX = 1f, signY = 1f;
    private boolean oriented;

    private FrameGenerator(int multiplier, int offered) {
        this.multiplier = multiplier;
        this.offered = offered;
        this.mode = (offered & OFFERS_EXTRAPOLATION) != 0 ? Mode.DRIVER : Mode.WARP;
    }

    /** A generator when the driver offers frame extrapolation or motion estimation in the current context, null otherwise. */
    static FrameGenerator create(int multiplier) {
        try {
            System.loadLibrary("moonlight-core");
            int offered = nativeInit();
            return offered != 0 ? new FrameGenerator(multiplier, offered) : null;
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
        pairCells = 0;
        if (frames > 0) {
            int changed = changedCells(source, ring[newest]);
            recentChanged[recentCount++ % recentChanged.length] = changed;
            // A refinement directly follows its picture: after a repeat the next frame is the game's next picture, however little
            // it moved (a slowing camera, a small object on a still scene)
            boolean refinement = !lastWasRepeat && changed > MAX_CHANGED_CELLS && lastPictureCells >= MOVING_PAIR_CELLS
                    && changed * 100 < lastPictureCells * REFINE_PERCENT;
            if (changed <= MAX_CHANGED_CELLS || refinement) {
                repeats++;
                lastWasRepeat = true;
                if (refinement) {
                    // The same picture in better quality: it replaces the newest of the pair (its motion is estimated again)
                    refinements++;
                    GLES32.glCopyImageSubData(source, GLES31.GL_TEXTURE_2D, 0, 0, 0, 0, ring[newest], GLES31.GL_TEXTURE_2D, 0, 0, 0, 0, w, h, 1);
                    motionReady = false;
                }
                return true;
            }
            pairCells = lastPictureCells = changed;
        }
        lastWasRepeat = false;
        pairChecked = pairWarp = false;
        motionReady = false;
        int slot = newest < 0 ? 0 : 1 - newest;
        GLES32.glCopyImageSubData(source, GLES31.GL_TEXTURE_2D, 0, 0, 0, 0, ring[slot], GLES31.GL_TEXTURE_2D, 0, 0, 0, 0, w, h, 1);
        long interval = frames > 0 ? timestampNs - newestTimestampNs : 0;
        newest = slot;
        newestTimestampNs = timestampNs;
        steps = refreshes = nextStep = 1;
        if (frames > 0 && interval > 0 && periodNs > 0 && (intervalNs == 0 || interval <= intervalNs * MAX_GAP_INTERVALS)) {
            intervalNs = intervalNs == 0 ? interval : (intervalNs * 3 + interval) / 4;
            // The smoothed interval: arrival jitter must not change the plan (and t) from one game frame to the next
            refreshes = Math.max(1, Math.round(intervalNs / (float) periodNs));
            steps = Math.min(refreshes, multiplier);
            frames++;
            intervalRefreshes[Math.min(refreshes, intervalRefreshes.length - 1)]++;
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

    /**
     * Generates the next step into the output texture and returns it; a driver error is returned as an exception. The driver's
     * extrapolation first; on a pair that moved, its first step is checked, and when it stayed (nearly) the newest picture (a motion
     * too large for the driver, as in a fast camera turn) this pair's steps are warped along the motion estimation instead.
     */
    int generate() {
        float t = nextOffset() / (float) refreshes;
        nextStep++;
        if (mode == Mode.DRIVER && !pairWarp) {
            int error = nativeExtrapolate(ring[1 - newest], ring[newest], output, t);
            if (error != 0) throw new IllegalStateException("glExtrapolateTex2DQCOM error 0x" + Integer.toHexString(error));
            if (!pairChecked && pairCells >= MOVING_PAIR_CELLS && (offered & OFFERS_MOTION_ESTIMATION) != 0) {
                pairChecked = true;
                movingPairs++;
                generatedChanged = changedCells(output, ring[newest]);
                pairChanged = pairCells;
                if (generatedChanged * 10 < pairCells * STUCK_TENTHS) {
                    stuckPairs++;
                    pairWarp = true;
                }
            }
        }
        if (mode == Mode.WARP || pairWarp) {
            if (!motionReady) {
                drawLuma(1 - newest);
                drawLuma(newest);
                estimateMotion();
            }
            drawWarp(ring[newest], t);
            if (warpSamples++ % 15 == 0) {
                warpMoved = changedCells(output, ring[newest]);
                warpPair = pairCells;
            }
        }
        generated++;
        return output;
    }

    /** For the log: the driver's checks on moving pairs, the warp's latest move, and the game's frame intervals in refreshes. */
    String generatedChanged() {
        StringBuilder out = new StringBuilder().append(mode).append(": refinements ").append(refinements).append(", moving pairs ")
                .append(movingPairs).append(", driver stuck ")
                .append(stuckPairs).append(" (last ").append(generatedChanged).append(" of ").append(pairChanged).append("), warp ")
                .append(warpSamples).append(" (last ").append(warpMoved).append(" of ").append(warpPair).append("), intervals");
        for (int i = 1; i < intervalRefreshes.length; i++) out.append(' ').append(i).append(':').append(intervalRefreshes[i]);
        return out.append(", format 0x").append(Integer.toHexString(internalFormat)).toString();
    }

    /** The changed cells of the latest repeat tests, oldest first (for the log). */
    String recentChangedCells() {
        StringBuilder out = new StringBuilder();
        int n = Math.min(recentCount, recentChanged.length);
        for (int i = recentCount - n; i < recentCount; i++) out.append(i > recentCount - n ? " " : "").append(recentChanged[i % recentChanged.length]);
        return out.toString();
    }

    /** Forgets the pair (a new stream size, a mode change): the next decoded frame starts over. */
    void reset() {
        frames = 0;
        newest = -1;
        intervalNs = 0;
        steps = refreshes = nextStep = 1;
        motionReady = false;
    }

    /** The luma of ring slot [slot] into its luma texture, at the motion estimation's size. */
    private void drawLuma(int slot) {
        if (lumaProgram == 0) lumaProgram = SgsrRenderer.program(FULLSCREEN, LUMA);
        if (luma[0] == 0) allocateWarp();
        bindTarget(luma[slot], lumaWidth, lumaHeight);
        GLES31.glUseProgram(lumaProgram);
        GLES31.glActiveTexture(GLES31.GL_TEXTURE0);
        GLES31.glBindTexture(GLES31.GL_TEXTURE_2D, ring[slot]);
        GLES31.glUniform1i(GLES31.glGetUniformLocation(lumaProgram, "src"), 0);
        GLES31.glUniform2f(GLES31.glGetUniformLocation(lumaProgram, "outSize"), lumaWidth, lumaHeight);
        GLES31.glDrawArrays(GLES31.GL_TRIANGLE_STRIP, 0, 4);
    }

    /** The motion from the older to the newest picture of the pair; the first moving pair also tells the vectors' orientation. */
    private void estimateMotion() {
        int error = nativeEstimateMotion(luma[1 - newest], luma[newest], motion);
        if (error != 0) throw new IllegalStateException("glTexEstimateMotionQCOM error 0x" + Integer.toHexString(error));
        motionReady = true;
        if (!oriented) orient();
    }

    /**
     * Which way the vectors point: the older picture warped a full step along them must look like the newest one. Tried for both
     * signs of x and y on a pair that moved; the closest wins.
     */
    private void orient() {
        int moved = changedCells(ring[1 - newest], ring[newest]);
        if (moved < MOVING_PAIR_CELLS) return;
        int best = Integer.MAX_VALUE;
        float bestX = 1f, bestY = 1f;
        for (float sx = -1f; sx <= 1f; sx += 2f) {
            for (float sy = -1f; sy <= 1f; sy += 2f) {
                signX = sx;
                signY = sy;
                drawWarp(ring[1 - newest], 1f);
                int left = changedCells(output, ring[newest]);
                if (left < best) { best = left; bestX = sx; bestY = sy; }
            }
        }
        signX = bestX;
        signY = bestY;
        oriented = true;
        LimeLog.info("Frame generation: motion vectors oriented " + bestX + "," + bestY + " (" + best + " of " + moved + " cells left)");
    }

    /** [source] warped [t] pair intervals along the motion into the output texture. */
    private void drawWarp(int source, float t) {
        if (warpProgram == 0) warpProgram = SgsrRenderer.program(FULLSCREEN, WARP);
        bindTarget(output, width, height);
        GLES31.glUseProgram(warpProgram);
        GLES31.glActiveTexture(GLES31.GL_TEXTURE0);
        GLES31.glBindTexture(GLES31.GL_TEXTURE_2D, source);
        GLES31.glUniform1i(GLES31.glGetUniformLocation(warpProgram, "src"), 0);
        GLES31.glActiveTexture(GLES31.GL_TEXTURE1);
        GLES31.glBindTexture(GLES31.GL_TEXTURE_2D, motion);
        GLES31.glUniform1i(GLES31.glGetUniformLocation(warpProgram, "motion"), 1);
        GLES31.glUniform2f(GLES31.glGetUniformLocation(warpProgram, "outSize"), width, height);
        GLES31.glUniform2f(GLES31.glGetUniformLocation(warpProgram, "scale"), signX / lumaWidth, signY / lumaHeight);
        GLES31.glUniform1f(GLES31.glGetUniformLocation(warpProgram, "t"), t);
        GLES31.glDrawArrays(GLES31.GL_TRIANGLE_STRIP, 0, 4);
        GLES31.glActiveTexture(GLES31.GL_TEXTURE0);
    }

    /**
     * How many cells of a 64 × 36 grid differ between [a] and [b]: a small grid of differences drawn on the GPU and read back (about
     * 9 KB, one short wait for this pass).
     */
    private int changedCells(int a, int b) {
        if (diffProgram == 0) {
            diffProgram = SgsrRenderer.program(FULLSCREEN, DIFF);
            diffTexture = texture(GLES31.GL_RGBA8, CELLS_X, CELLS_Y);
        }
        bindTarget(diffTexture, CELLS_X, CELLS_Y);
        GLES31.glUseProgram(diffProgram);
        GLES31.glActiveTexture(GLES31.GL_TEXTURE0);
        GLES31.glBindTexture(GLES31.GL_TEXTURE_2D, a);
        GLES31.glUniform1i(GLES31.glGetUniformLocation(diffProgram, "a"), 0);
        GLES31.glActiveTexture(GLES31.GL_TEXTURE1);
        GLES31.glBindTexture(GLES31.GL_TEXTURE_2D, b);
        GLES31.glUniform1i(GLES31.glGetUniformLocation(diffProgram, "b"), 1);
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

    /** Draws into [texture] ([w] × [h]) through the generator's own framebuffer. */
    private void bindTarget(int texture, int w, int h) {
        if (framebuffer == 0) {
            int[] id = new int[1];
            GLES31.glGenFramebuffers(1, id, 0);
            framebuffer = id[0];
        }
        GLES31.glBindFramebuffer(GLES31.GL_FRAMEBUFFER, framebuffer);
        GLES31.glFramebufferTexture2D(GLES31.GL_FRAMEBUFFER, GLES31.GL_COLOR_ATTACHMENT0, GLES31.GL_TEXTURE_2D, texture, 0);
        GLES31.glViewport(0, 0, w, h);
    }

    private static int texture(int format, int w, int h) {
        int[] id = new int[1];
        GLES31.glGenTextures(1, id, 0);
        GLES31.glBindTexture(GLES31.GL_TEXTURE_2D, id[0]);
        GLES31.glTexStorage2D(GLES31.GL_TEXTURE_2D, 1, format, w, h);
        GLES31.glTexParameteri(GLES31.GL_TEXTURE_2D, GLES31.GL_TEXTURE_MIN_FILTER, GLES31.GL_LINEAR);
        GLES31.glTexParameteri(GLES31.GL_TEXTURE_2D, GLES31.GL_TEXTURE_MAG_FILTER, GLES31.GL_LINEAR);
        GLES31.glTexParameteri(GLES31.GL_TEXTURE_2D, GLES31.GL_TEXTURE_WRAP_S, GLES31.GL_CLAMP_TO_EDGE);
        GLES31.glTexParameteri(GLES31.GL_TEXTURE_2D, GLES31.GL_TEXTURE_WRAP_T, GLES31.GL_CLAMP_TO_EDGE);
        return id[0];
    }

    private void allocate(int w, int h, int format) {
        deleteFrames();
        ring[0] = texture(format, w, h);
        ring[1] = texture(format, w, h);
        output = texture(format, w, h);
        width = w;
        height = h;
        internalFormat = format;
        reset();
    }

    /** The luma pair at half the picture size, rounded down to whole search blocks, and the motion texture (one texel per block). */
    private void allocateWarp() {
        GLES31.glGetIntegerv(GL_MOTION_ESTIMATION_SEARCH_BLOCK_X_QCOM, query, 0);
        int blockX = Math.max(1, query[0]);
        GLES31.glGetIntegerv(GL_MOTION_ESTIMATION_SEARCH_BLOCK_Y_QCOM, query, 0);
        int blockY = Math.max(1, query[0]);
        lumaWidth = Math.max(blockX, width / 2 / blockX * blockX);
        lumaHeight = Math.max(blockY, height / 2 / blockY * blockY);
        luma[0] = texture(GLES30.GL_R8, lumaWidth, lumaHeight);
        luma[1] = texture(GLES30.GL_R8, lumaWidth, lumaHeight);
        motion = texture(GLES30.GL_RGBA16F, lumaWidth / blockX, lumaHeight / blockY);
        LimeLog.info("Frame generation warp: luma " + lumaWidth + "x" + lumaHeight + ", search block " + blockX + "x" + blockY);
    }

    private void deleteFrames() {
        if (ring[0] != 0) GLES31.glDeleteTextures(3, new int[]{ring[0], ring[1], output}, 0);
        ring[0] = ring[1] = output = 0;
        if (luma[0] != 0) GLES31.glDeleteTextures(3, new int[]{luma[0], luma[1], motion}, 0);
        luma[0] = luma[1] = motion = 0;
    }

    @Override
    public void close() {
        deleteFrames();
        for (int program : new int[]{diffProgram, lumaProgram, warpProgram}) if (program != 0) GLES31.glDeleteProgram(program);
        if (diffTexture != 0) GLES31.glDeleteTextures(1, new int[]{diffTexture}, 0);
        if (framebuffer != 0) GLES31.glDeleteFramebuffers(1, new int[]{framebuffer}, 0);
        diffProgram = lumaProgram = warpProgram = diffTexture = framebuffer = 0;
    }

    private static native int nativeInit();
    private static native int nativeExtrapolate(int older, int newer, int output, float scale);
    private static native int nativeEstimateMotion(int ref, int target, int output);
}
