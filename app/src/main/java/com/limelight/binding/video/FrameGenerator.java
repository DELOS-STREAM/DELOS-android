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
 * newest picture along the block motion its motion estimation (GL_QCOM_motion_estimation) finds between the pair. Which one is
 * used follows how well each predicted the game's next picture: every other moving picture, both predict it from the pair before
 * it and the one with the smaller error (relative to simply holding the picture) generates the following steps. The driver tends
 * to follow a moving camera, the warp small objects on a still scene.
 * <p>
 * The pace is the game's, not the stream's: a decoded frame that repeats the previous one (a 30 fps game in a 60 fps stream), or
 * refines it on fast motion, is recognised and left out of the pair, and a generated frame takes its refresh. Every step comes from
 * the same pair, at t = offset / refreshes of the game's frame interval, spread evenly over its refreshes (at most the chosen
 * multiplier per game frame); a generated frame is never fed back in. GL thread only (SgsrRenderer's), with its context current.
 */
final class FrameGenerator implements AutoCloseable {
    private static final int OFFERS_EXTRAPOLATION = 1, OFFERS_MOTION_ESTIMATION = 2;
    private static final int GL_MOTION_ESTIMATION_SEARCH_BLOCK_X_QCOM = 0x8C90, GL_MOTION_ESTIMATION_SEARCH_BLOCK_Y_QCOM = 0x8C91;
    /** The game's frame interval is not trusted beyond this many expected intervals (a hitch, a pause): the next frame starts over. */
    private static final int MAX_GAP_INTERVALS = 3;
    /** The difference grid: cells of 4 × 4 samples each. */
    private static final int CELLS_X = 64, CELLS_Y = 36;
    /**
     * A cell changed when one of its samples moved by more than about 3 / 255 (the shader scales the largest difference by 8). A frame
     * repeats the previous picture when at most [MAX_CHANGED_CELLS] cells changed (re-encoding noise), or, right after a picture that
     * moved, when fewer than [REFINE_PERCENT] % of the cells that picture changed did: on fast motion the console's encoder sends the
     * repeat as a refinement of the same picture (about 15–20 % of the cells, observed on the Odin), not a new one.
     */
    private static final int CHANGED_LEVEL = 24, MAX_CHANGED_CELLS = CELLS_X * CELLS_Y / 200, REFINE_PERCENT = 35;
    /** A pair that changed at least this many cells moved enough to compare the two ways to predict and to orient the vectors. */
    private static final int MOVING_PAIR_CELLS = 60;
    /** The other way must predict this much better (in percent of the current one's error) before it takes over. */
    private static final int SWITCH_PERCENT = 90;
    /** The vector statistics' byte scales (luma px at 255), the change that counts as "the median changed it" (luma px), and how often a pair is measured. */
    private static final int STATS_LENGTH = 64, STATS_CHANGE = 16, STATS_CHANGED_PX = 2, STATS_EVERY = 8;

    private static final String FULLSCREEN = "#version 310 es\n" +
            "void main(){ vec2 p=vec2(float(gl_VertexID&1),float(gl_VertexID>>1)); gl_Position=vec4(p*2.0-1.0,0,1); }";
    // Per cell: R the largest difference (×8, the repeat test), G the mean difference (×4, the prediction error)
    private static final String DIFF = "#version 310 es\n" +
            "precision highp float; uniform sampler2D a; uniform sampler2D b; out vec4 color;\n" +
            "void main(){ vec2 cells=vec2(" + CELLS_X + ".0," + CELLS_Y + ".0); vec2 cell=floor(gl_FragCoord.xy); float d=0.0; float s=0.0;\n" +
            " for(int j=0;j<4;j++) for(int i=0;i<4;i++){ vec2 uv=(cell+(vec2(i,j)+0.5)/4.0)/cells;\n" +
            "  vec3 e=abs(texture(a,uv).rgb-texture(b,uv).rgb); float m=max(e.r,max(e.g,e.b)); d=max(d,m); s+=m; }\n" +
            " color=vec4(min(d*8.0,1.0),min(s/16.0*4.0,1.0),0.0,1.0); }";
    private static final String LUMA = "#version 310 es\n" +
            "precision highp float; uniform sampler2D src; uniform vec2 outSize; out vec4 color;\n" +
            "void main(){ vec3 c=texture(src,gl_FragCoord.xy/outSize).rgb; color=vec4(dot(c,vec3(0.299,0.587,0.114)),0.0,0.0,1.0); }";
    // The newest picture, sampled back along the motion: a pixel that moved by v over the pair moves by v·t over the step
    private static final String WARP = "#version 310 es\n" +
            "precision highp float; uniform sampler2D src; uniform sampler2D motion; uniform vec2 outSize; uniform vec2 scale;\n" +
            "uniform float t; out vec4 color;\n" +
            "void main(){ vec2 uv=gl_FragCoord.xy/outSize; vec2 v=texture(motion,uv).rg*scale; color=texture(src,uv-v*t); }";
    // The block vectors without their outliers: each block takes the vector median of its 3 × 3 neighbourhood, the neighbour's
    // vector (its own first) with the smallest summed distance to the other eight. The result is always a vector the driver
    // measured nearby, never a blend and never "no motion"
    private static final String MEDIAN = "#version 310 es\n" +
            "precision highp float; uniform sampler2D motion; out vec4 color;\n" +
            "void main(){ ivec2 p=ivec2(gl_FragCoord.xy); ivec2 last=textureSize(motion,0)-ivec2(1); vec2 n[9];\n" +
            " for(int j=0;j<3;j++) for(int i=0;i<3;i++) n[j*3+i]=texelFetch(motion,clamp(p+ivec2(i-1,j-1),ivec2(0),last),0).rg;\n" +
            " vec2 best=n[4]; float least=1e30;\n" +
            " for(int a=0;a<9;a++){ float s=0.0; for(int b=0;b<9;b++){ vec2 d=abs(n[a]-n[b]); s+=d.x+d.y; }\n" +
            "  if(s<least || (a==4 && s<=least)){ least=s; best=n[a]; } }\n" +
            " color=vec4(best,0.0,1.0); }";
    // For the log, per block: R the filtered vector's length (of [STATS_LENGTH] luma px), G how far the median moved it (of
    // [STATS_CHANGE] luma px), as bytes, so the read-back needs no float format
    private static final String STATS = "#version 310 es\n" +
            "precision highp float; uniform sampler2D raw; uniform sampler2D filtered; out vec4 color;\n" +
            "void main(){ ivec2 p=ivec2(gl_FragCoord.xy); vec2 f=texelFetch(filtered,p,0).rg; vec2 r=texelFetch(raw,p,0).rg;\n" +
            " color=vec4(min(length(f)/" + STATS_LENGTH + ".0,1.0),min(length(f-r)/" + STATS_CHANGE + ".0,1.0),0.0,1.0); }";

    /** [onRealFrame] results: a new picture of the game, an identical repeat, or the same picture with changed details. */
    static final int NEW_PICTURE = 0, REPEAT = 1, UPDATE = 2;

    private final int offered;
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
    // The latest difference test: changed cells and the summed mean difference
    private int diffChanged, diffError;
    private int lastPictureCells;
    // How far the last two pairs moved (summed difference) and the share of a step this pair extrapolates: a pair that moved
    // less than the one before (a camera slowing to a stop) is extrapolated less, so the stop does not overshoot and snap back
    private int pairError, previousPairError;
    private float damping = 1f;
    private long dampedPairs;
    // Whether the previous decoded frame repeated or refined its picture
    private boolean lastWasRepeat;
    // The way that generates: the warp (else the driver's extrapolation), and the two ways' smoothed errors relative to holding
    private boolean useWarp;
    private float driverScore = -1f, warpScore = -1f;
    private long evaluations, switches;
    // For the log
    // Volatile: the statistics read them from the decoder thread
    private volatile long generated, repeats, refinements, pictures;
    private final int[] recentChanged = new int[12];
    private int recentCount;
    private final int[] intervalRefreshes = new int[7];
    private int framebuffer, diffProgram, diffTexture;
    private final ByteBuffer diffPixels = ByteBuffer.allocateDirect(CELLS_X * CELLS_Y * 4).order(ByteOrder.nativeOrder());
    // The warp: a luma copy per ring slot, the motion between the pair, and the vectors' orientation (found on a moving pair)
    private final int[] luma = new int[2];
    private int lumaWidth, lumaHeight, motion, lumaProgram, warpProgram;
    private boolean motionReady;
    private float signX = 1f, signY = 1f;
    private boolean oriented;
    // The vector median (the field the warp samples) and, for the log, the vectors' lengths and what the median changed,
    // measured on every [STATS_EVERY]th pair since the last log line; and the steps each way generated since then
    private int motionFiltered, motionWidth, motionHeight, medianProgram, statsProgram, statsTexture;
    private boolean medianChecked, medianOff, statsOff;
    private ByteBuffer statsPixels;
    private long motionPairs, statPairs, statBlocks, statChanged, statLengthSum, stepsDriver, stepsWarp;
    private int statLengthMax;

    private FrameGenerator(int multiplier, int offered) {
        this.multiplier = multiplier;
        this.offered = offered;
        this.useWarp = (offered & OFFERS_EXTRAPOLATION) == 0;
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

    /** New pictures of the game so far (decoded frames that were neither repeats nor refinements). */
    long pictures() { return pictures; }

    /** Refreshes the game's frame interval spans (the plan of the newest pair). */
    int plannedRefreshes() { return refreshes; }

    /**
     * A decoded frame was drawn into [source] ([w] × [h]). A new picture becomes the newest of the pair and the steps until the next
     * one are planned from the interval to the previous picture and the display refresh ([periodNs]). A repeat changes no plan:
     * when it differs at all (a refinement on fast motion, a small HUD change on a still scene) it replaces the newest picture and
     * returns [UPDATE] (to be shown when no generated step takes its refresh), an identical one returns [REPEAT].
     */
    int onRealFrame(int source, int w, int h, long timestampNs, long periodNs) {
        GLES31.glBindTexture(GLES31.GL_TEXTURE_2D, source);
        GLES31.glGetTexLevelParameteriv(GLES31.GL_TEXTURE_2D, 0, GLES31.GL_TEXTURE_INTERNAL_FORMAT, query, 0);
        final int format = query[0];
        if (w != width || h != height || format != internalFormat || ring[0] == 0) allocate(w, h, format);
        if (frames > 0) {
            diff(source, ring[newest]);
            final int changed = diffChanged, holdError = diffError;
            recentChanged[recentCount++ % recentChanged.length] = changed;
            // A refinement directly follows its picture: after a repeat the next frame is the game's next picture, however little
            // it moved (a slowing camera, a small object on a still scene)
            boolean refinement = !lastWasRepeat && changed > MAX_CHANGED_CELLS && lastPictureCells >= MOVING_PAIR_CELLS
                    && changed * 100 < lastPictureCells * REFINE_PERCENT;
            if (changed <= MAX_CHANGED_CELLS || refinement) {
                repeats++;
                lastWasRepeat = true;
                if (changed == 0) return REPEAT;
                // The same picture with changed details: it replaces the newest of the pair (its motion is estimated again), so
                // a detail that changed is never held back until the next picture
                if (refinement) refinements++;
                GLES32.glCopyImageSubData(source, GLES31.GL_TEXTURE_2D, 0, 0, 0, 0, ring[newest], GLES31.GL_TEXTURE_2D, 0, 0, 0, 0, w, h, 1);
                motionReady = false;
                return UPDATE;
            }
            lastPictureCells = changed;
            previousPairError = pairError;
            pairError = holdError;
            // Only a clear slow-down (under 70 %): the difference sum varies a little from pair to pair at a steady speed
            damping = previousPairError > 0 && pairError * 10 < previousPairError * 7
                    ? Math.max(.3f, pairError / (float) previousPairError) : 1f;
            if (damping < 1f) dampedPairs++;
            // Every other moving picture: which way would have predicted it better from the pair before it
            if (frames >= 2 && changed >= MOVING_PAIR_CELLS && offered == (OFFERS_EXTRAPOLATION | OFFERS_MOTION_ESTIMATION)
                    && holdError > 0 && evaluations++ % 2 == 0) {
                evaluate(source, holdError);
            }
        }
        lastWasRepeat = false;
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
        pictures++;
        return NEW_PICTURE;
    }

    /**
     * Both ways predict [next] (the game's new picture) one full interval from the current pair; their errors, relative to holding
     * the newest picture ([holdError]), are smoothed, and the clearly better way generates from now on.
     */
    private void evaluate(int next, int holdError) {
        int error = nativeExtrapolate(ring[1 - newest], ring[newest], output, 1f);
        if (error != 0) throw new IllegalStateException("glExtrapolateTex2DQCOM error 0x" + Integer.toHexString(error));
        diff(output, next);
        final float driver = diffError / (float) holdError;
        ensureMotion();
        drawWarp(ring[newest], 1f);
        diff(output, next);
        final float warp = diffError / (float) holdError;
        driverScore = driverScore < 0 ? driver : driverScore * .8f + driver * .2f;
        warpScore = warpScore < 0 ? warp : warpScore * .8f + warp * .2f;
        boolean warpBetter = useWarp ? !(driverScore * 100 < warpScore * SWITCH_PERCENT) : warpScore * 100 < driverScore * SWITCH_PERCENT;
        if (warpBetter != useWarp) switches++;
        useWarp = warpBetter;
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
        float t = nextOffset() / (float) refreshes * damping;
        nextStep++;
        if (useWarp) {
            ensureMotion();
            drawWarp(ring[newest], t);
            stepsWarp++;
        } else {
            int error = nativeExtrapolate(ring[1 - newest], ring[newest], output, t);
            if (error != 0) throw new IllegalStateException("glExtrapolateTex2DQCOM error 0x" + Integer.toHexString(error));
            stepsDriver++;
        }
        generated++;
        return output;
    }

    /**
     * For the log: the way in use, both ways' errors relative to holding the picture, the game's frame intervals in refreshes, the
     * steps each way generated and the motion vectors (their mean and largest length in picture px, the share the median moved by
     * more than [STATS_CHANGED_PX] luma px) since the last call, which starts these counts over.
     */
    String generatedChanged() {
        StringBuilder out = new StringBuilder().append(useWarp ? "warp" : "driver").append(" (errors vs hold: driver ")
                .append(Math.round(driverScore * 100) / 100f).append(", warp ").append(Math.round(warpScore * 100) / 100f)
                .append("; switches ").append(switches).append("), refinements ").append(refinements).append(", damped pairs ")
                .append(dampedPairs).append(", intervals");
        for (int i = 1; i < intervalRefreshes.length; i++) out.append(' ').append(i).append(':').append(intervalRefreshes[i]);
        out.append(", format 0x").append(Integer.toHexString(internalFormat));
        out.append(", steps driver ").append(stepsDriver).append(" warp ").append(stepsWarp);
        if (medianOff) out.append(", vector median off");
        else if (statBlocks > 0) {
            // Byte values back to luma px, then to picture px (the luma is about half the picture's size)
            final float px = width / (float) Math.max(1, lumaWidth) * STATS_LENGTH / 255f;
            out.append(", vectors mean ").append(Math.round(statLengthSum * px / statBlocks * 10) / 10f).append(" px max ")
                    .append(Math.round(statLengthMax * px * 10) / 10f).append(" px, median changed ")
                    .append(Math.round(statChanged * 1000f / statBlocks) / 10f).append(" % (").append(statPairs).append(" pairs)");
        } else out.append(", vectors not measured");
        stepsDriver = stepsWarp = statPairs = statBlocks = statChanged = statLengthSum = 0;
        statLengthMax = 0;
        return out.toString();
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

    /** The motion of the current pair, estimated once per pair (or after a refinement replaced its newest picture). */
    private void ensureMotion() {
        if (motionReady) return;
        drawLuma(1 - newest);
        drawLuma(newest);
        int error = nativeEstimateMotion(luma[1 - newest], luma[newest], motion);
        if (error != 0) throw new IllegalStateException("glTexEstimateMotionQCOM error 0x" + Integer.toHexString(error));
        filterMotion();
        motionReady = true;
        if (!oriented) orient();
    }

    /**
     * The driver's vectors through the 3 × 3 vector median into the field the warp samples. A device that cannot run it (the
     * shader, or a half-float target) keeps the driver's own vectors: the warp goes on, the log says "vector median off".
     */
    private void filterMotion() {
        if (medianOff) return;
        try {
            if (medianProgram == 0) medianProgram = SgsrRenderer.program(FULLSCREEN, MEDIAN);
            bindTarget(motionFiltered, motionWidth, motionHeight);
            if (!medianChecked) {
                medianChecked = true;
                final int status = GLES31.glCheckFramebufferStatus(GLES31.GL_FRAMEBUFFER);
                if (status != GLES31.GL_FRAMEBUFFER_COMPLETE) throw new IllegalStateException("target 0x" + Integer.toHexString(status));
            }
        } catch (RuntimeException e) {
            medianOff = true;
            LimeLog.warning("Frame generation: vector median off: " + e);
            return;
        }
        GLES31.glUseProgram(medianProgram);
        GLES31.glActiveTexture(GLES31.GL_TEXTURE0);
        GLES31.glBindTexture(GLES31.GL_TEXTURE_2D, motion);
        GLES31.glUniform1i(GLES31.glGetUniformLocation(medianProgram, "motion"), 0);
        GLES31.glDrawArrays(GLES31.GL_TRIANGLE_STRIP, 0, 4);
        if (motionPairs++ % STATS_EVERY == 0) measureMotion();
    }

    /** One pair's vectors into the log's counts: a byte picture of lengths and changes, read back (one texel per block). */
    private void measureMotion() {
        if (statsOff) return;
        try {
            if (statsProgram == 0) statsProgram = SgsrRenderer.program(FULLSCREEN, STATS);
        } catch (RuntimeException e) {
            statsOff = true;
            LimeLog.warning("Frame generation: vector statistics off: " + e);
            return;
        }
        if (statsTexture == 0) {
            statsTexture = texture(GLES31.GL_RGBA8, motionWidth, motionHeight);
            statsPixels = ByteBuffer.allocateDirect(motionWidth * motionHeight * 4).order(ByteOrder.nativeOrder());
        }
        bindTarget(statsTexture, motionWidth, motionHeight);
        GLES31.glUseProgram(statsProgram);
        GLES31.glActiveTexture(GLES31.GL_TEXTURE0);
        GLES31.glBindTexture(GLES31.GL_TEXTURE_2D, motion);
        GLES31.glUniform1i(GLES31.glGetUniformLocation(statsProgram, "raw"), 0);
        GLES31.glActiveTexture(GLES31.GL_TEXTURE1);
        GLES31.glBindTexture(GLES31.GL_TEXTURE_2D, motionFiltered);
        GLES31.glUniform1i(GLES31.glGetUniformLocation(statsProgram, "filtered"), 1);
        GLES31.glDrawArrays(GLES31.GL_TRIANGLE_STRIP, 0, 4);
        GLES31.glActiveTexture(GLES31.GL_TEXTURE0);
        statsPixels.clear();
        GLES31.glReadPixels(0, 0, motionWidth, motionHeight, GLES31.GL_RGBA, GLES31.GL_UNSIGNED_BYTE, statsPixels);
        final int blocks = motionWidth * motionHeight;
        final int changedLevel = STATS_CHANGED_PX * 255 / STATS_CHANGE;
        for (int i = 0; i < blocks; i++) {
            final int length = statsPixels.get(i * 4) & 0xFF;
            statLengthSum += length;
            if (length > statLengthMax) statLengthMax = length;
            if ((statsPixels.get(i * 4 + 1) & 0xFF) > changedLevel) statChanged++;
        }
        statBlocks += blocks;
        statPairs++;
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

    /**
     * Which way the vectors point: the older picture warped a full step along them must look like the newest one. Tried for both
     * signs of x and y on a pair that moved; the smallest error wins.
     */
    private void orient() {
        diff(ring[1 - newest], ring[newest]);
        if (diffChanged < MOVING_PAIR_CELLS) return;
        final int moved = diffError;
        int best = Integer.MAX_VALUE;
        float bestX = 1f, bestY = 1f;
        for (float sx = -1f; sx <= 1f; sx += 2f) {
            for (float sy = -1f; sy <= 1f; sy += 2f) {
                signX = sx;
                signY = sy;
                drawWarp(ring[1 - newest], 1f);
                diff(output, ring[newest]);
                if (diffError < best) { best = diffError; bestX = sx; bestY = sy; }
            }
        }
        signX = bestX;
        signY = bestY;
        oriented = true;
        LimeLog.info("Frame generation: motion vectors oriented " + bestX + "," + bestY + " (error " + best + " of " + moved + ")");
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
        // The median's field; the driver's own where the median cannot run
        GLES31.glBindTexture(GLES31.GL_TEXTURE_2D, medianOff ? motion : motionFiltered);
        GLES31.glUniform1i(GLES31.glGetUniformLocation(warpProgram, "motion"), 1);
        GLES31.glUniform2f(GLES31.glGetUniformLocation(warpProgram, "outSize"), width, height);
        GLES31.glUniform2f(GLES31.glGetUniformLocation(warpProgram, "scale"), signX / lumaWidth, signY / lumaHeight);
        GLES31.glUniform1f(GLES31.glGetUniformLocation(warpProgram, "t"), t);
        GLES31.glDrawArrays(GLES31.GL_TRIANGLE_STRIP, 0, 4);
        GLES31.glActiveTexture(GLES31.GL_TEXTURE0);
    }

    /**
     * Compares [a] and [b] on a 64 × 36 grid drawn on the GPU and read back (about 9 KB, one short wait for this pass): [diffChanged]
     * the cells that changed, [diffError] the summed mean difference.
     */
    private void diff(int a, int b) {
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
        int changed = 0, error = 0;
        for (int i = 0; i < CELLS_X * CELLS_Y; i++) {
            if ((diffPixels.get(i * 4) & 0xFF) > CHANGED_LEVEL) changed++;
            error += diffPixels.get(i * 4 + 1) & 0xFF;
        }
        diffChanged = changed;
        diffError = error;
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
        motionWidth = lumaWidth / blockX;
        motionHeight = lumaHeight / blockY;
        motion = texture(GLES30.GL_RGBA16F, motionWidth, motionHeight);
        motionFiltered = texture(GLES30.GL_RGBA16F, motionWidth, motionHeight);
        LimeLog.info("Frame generation warp: luma " + lumaWidth + "x" + lumaHeight + ", search block " + blockX + "x" + blockY);
    }

    private void deleteFrames() {
        if (ring[0] != 0) GLES31.glDeleteTextures(3, new int[]{ring[0], ring[1], output}, 0);
        ring[0] = ring[1] = output = 0;
        if (luma[0] != 0) GLES31.glDeleteTextures(4, new int[]{luma[0], luma[1], motion, motionFiltered}, 0);
        luma[0] = luma[1] = motion = motionFiltered = 0;
        // The statistics picture has the motion field's size
        if (statsTexture != 0) GLES31.glDeleteTextures(1, new int[]{statsTexture}, 0);
        statsTexture = 0;
        statsPixels = null;
    }

    @Override
    public void close() {
        deleteFrames();
        for (int program : new int[]{diffProgram, lumaProgram, warpProgram, medianProgram, statsProgram}) if (program != 0) GLES31.glDeleteProgram(program);
        if (diffTexture != 0) GLES31.glDeleteTextures(1, new int[]{diffTexture}, 0);
        if (framebuffer != 0) GLES31.glDeleteFramebuffers(1, new int[]{framebuffer}, 0);
        diffProgram = lumaProgram = warpProgram = medianProgram = statsProgram = diffTexture = framebuffer = 0;
    }

    private static native int nativeInit();
    private static native int nativeExtrapolate(int older, int newer, int output, float scale);
    private static native int nativeEstimateMotion(int ref, int target, int output);
}
