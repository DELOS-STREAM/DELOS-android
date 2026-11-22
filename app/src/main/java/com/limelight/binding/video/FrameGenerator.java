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
    /*
     * The own warp: the newest picture moved t pair intervals along its motion. Per pixel the motion is the candidate that best
     * explains the pair (the older picture moved by it matches the newest, on two taps): the half-size block field (median-
     * filtered), the quarter-size one (twice the search range, for fast turns), the camera's fitted motion, or none (a still
     * HUD, or nothing fits: the pixel holds). The chosen kind is then looked up again where the pixel comes from (one fixed-point
     * step, q = p − t·v(q)). Vectors are in half-size luma pixels (lumaSize), older → newest.
     */
    private static final String WARP = "#version 310 es\n" +
            "precision highp float; uniform sampler2D src; uniform sampler2D older; uniform sampler2D fine; uniform sampler2D coarse;\n" +
            "uniform vec2 outSize; uniform vec2 lumaSize; uniform vec4 camera; uniform vec2 cameraShift; uniform vec2 cameraCenter;\n" +
            "uniform float useCamera; uniform float t; out vec4 color;\n" +
            "vec2 cam(vec2 uv){ vec2 p=uv*lumaSize-cameraCenter; return vec2(camera.x*p.x+camera.y*p.y,camera.z*p.x+camera.w*p.y)+cameraShift; }\n" +
            "vec2 motionOf(int k, vec2 uv){ return k==0 ? texture(fine,uv).rg : k==1 ? texture(coarse,uv).rg : k==2 ? cam(uv) : vec2(0.0); }\n" +
            "float misfit(vec2 uv, vec2 v){ vec2 d=v/lumaSize; vec2 o=1.5/outSize; float e=0.0;\n" +
            " vec3 a=abs(texture(older,uv-o-d).rgb-texture(src,uv-o).rgb); e+=max(a.r,max(a.g,a.b));\n" +
            " vec3 b=abs(texture(older,uv+o-d).rgb-texture(src,uv+o).rgb); e+=max(b.r,max(b.g,b.b)); return e; }\n" +
            "void main(){ vec2 uv=gl_FragCoord.xy/outSize; int best=3; float bestErr=misfit(uv,vec2(0.0));\n" +
            " for(int k=0;k<3;k++){ if(k==2 && useCamera<0.5) continue; vec2 v=motionOf(k,uv); float e=misfit(uv,v)*(k==2?0.85:1.0);\n" +
            "  if(e<bestErr){ bestErr=e; best=k; } }\n" +
            " vec2 q=uv-t*motionOf(best,uv)/lumaSize; color=texture(src,uv-t*motionOf(best,q)/lumaSize); }";

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
    // Whether the previous decoded frame repeated or refined its picture
    private boolean lastWasRepeat;
    // The way that generates: the warp (else the driver's extrapolation), and the two ways' smoothed errors relative to holding
    private boolean useWarp;
    private float driverScore = -1f, warpScore = -1f;
    private long evaluations, switches;
    private boolean warpBroken;
    // For the log
    private long generated, repeats, refinements;
    private final int[] recentChanged = new int[12];
    private int recentCount;
    private final int[] intervalRefreshes = new int[7];
    private int framebuffer, diffProgram, diffTexture;
    private final ByteBuffer diffPixels = ByteBuffer.allocateDirect(CELLS_X * CELLS_Y * 4).order(ByteOrder.nativeOrder());
    // The warp: luma copies per ring slot at half and quarter size, the motion between the pair at both, the cleaned-up fields the
    // shader reads (half: median-filtered; quarter: on the half grid, in half-size pixels) and the camera's fitted motion
    private final int[] luma = new int[2], lumaQuarter = new int[2];
    private int lumaWidth, lumaHeight, quarterWidth, quarterHeight, blockX, blockY, motion, motionQuarter, fieldFine, fieldCoarse;
    private int lumaProgram, warpProgram;
    private boolean motionReady;
    private final MotionField.Affine cameraMotion = new MotionField.Affine();
    private boolean cameraFitted;
    // The previous pair's camera shift (half-size pixels, NaN when none) and this pair's damping of t: a camera that changed speed
    // or direction sharply is extrapolated less far, so a stop or a turn does not overshoot and snap back
    private float previousShiftX = Float.NaN, previousShiftY = Float.NaN, damping = 1f;
    private long dampedPairs;
    private java.nio.FloatBuffer readback;
    private float[] fineRaw, fine, coarseRaw, coarse;
    // For the log: the latest field's longest vectors (half-size pixels), the share near the half-size maximum, the camera fit
    private float maxFine, maxCoarse, shareNearMax, cameraInliers = -1f;

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
            // Every other moving picture: which way would have predicted it better from the pair before it
            if (frames >= 2 && changed >= MOVING_PAIR_CELLS && offered == (OFFERS_EXTRAPOLATION | OFFERS_MOTION_ESTIMATION) && !warpBroken
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
        final float warp;
        try {
            ensureMotion();
            drawWarp(ring[newest], 1f);
            diff(output, next);
            warp = diffError / (float) holdError;
        } catch (RuntimeException e) {
            breakWarp(e);
            return;
        }
        driverScore = driverScore < 0 ? driver : driverScore * .8f + driver * .2f;
        warpScore = warpScore < 0 ? warp : warpScore * .8f + warp * .2f;
        boolean warpBetter = useWarp ? !(driverScore * 100 < warpScore * SWITCH_PERCENT) : warpScore * 100 < driverScore * SWITCH_PERCENT;
        if (warpBetter != useWarp) switches++;
        useWarp = warpBetter;
    }

    /** The own warp failed on this device (a shader, a read-back): the driver's extrapolation alone generates from now on. */
    private void breakWarp(RuntimeException e) {
        LimeLog.warning("Frame generation: own warp off: " + e);
        warpBroken = true;
        useWarp = false;
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
        if (useWarp) {
            try {
                ensureMotion();
                drawWarp(ring[newest], t * damping);
                generated++;
                return output;
            } catch (RuntimeException e) {
                // The driver's extrapolation goes on where it exists; without it generation stops (the caller logs)
                if ((offered & OFFERS_EXTRAPOLATION) == 0) throw e;
                breakWarp(e);
            }
        }
        int error = nativeExtrapolate(ring[1 - newest], ring[newest], output, t);
        if (error != 0) throw new IllegalStateException("glExtrapolateTex2DQCOM error 0x" + Integer.toHexString(error));
        generated++;
        return output;
    }

    /** For the log: the way in use, both ways' errors relative to holding the picture, and the game's frame intervals in refreshes. */
    String generatedChanged() {
        StringBuilder out = new StringBuilder().append(useWarp ? "warp" : "driver").append(" (errors vs hold: driver ")
                .append(Math.round(driverScore * 100) / 100f).append(", warp ").append(Math.round(warpScore * 100) / 100f)
                .append("; switches ").append(switches).append("), refinements ").append(refinements)
                .append(", motion: longest ").append(Math.round(maxFine)).append(" px half, ").append(Math.round(maxCoarse))
                .append(" px quarter, ").append(Math.round(shareNearMax * 100)).append(" % near the half-size longest, camera fit ")
                .append(cameraInliers < 0 ? "none" : Math.round(cameraInliers * 100) + " %").append(", damped pairs ").append(dampedPairs)
                .append(", intervals");
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

    /**
     * The motion of the current pair, estimated once per pair (or after a refinement replaced its newest picture): at half and quarter
     * size, read back, the half-size field median-filtered, the quarter-size one put on the half grid, and the camera fitted to it.
     */
    private void ensureMotion() {
        if (motionReady) return;
        drawLuma(1 - newest);
        drawLuma(newest);
        int error = nativeEstimateMotion(luma[1 - newest], luma[newest], motion);
        if (error == 0) error = nativeEstimateMotion(lumaQuarter[1 - newest], lumaQuarter[newest], motionQuarter);
        if (error != 0) throw new IllegalStateException("glTexEstimateMotionQCOM error 0x" + Integer.toHexString(error));
        final int w = lumaWidth / blockX, h = lumaHeight / blockY, cw = quarterWidth / blockX, ch = quarterHeight / blockY;
        readField(motion, w, h, fineRaw);
        readField(motionQuarter, cw, ch, coarseRaw);
        error = GLES31.glGetError();
        if (error != 0) throw new IllegalStateException("motion read-back error 0x" + Integer.toHexString(error));
        MotionField.median3x3(fineRaw, fine, w, h);
        MotionField.upsample(coarseRaw, cw, ch, lumaWidth / (float) quarterWidth, lumaHeight / (float) quarterHeight, coarse, w, h);
        cameraFitted = MotionField.fitAffine(coarse, w, h, blockX, blockY, 1f, 4f, cameraMotion);
        cameraInliers = cameraFitted ? cameraMotion.inliers / (float) cameraMotion.total : -1f;
        damping = 1f;
        if (cameraFitted) {
            if (!Float.isNaN(previousShiftX)) {
                double change = Math.hypot(cameraMotion.e - previousShiftX, cameraMotion.f - previousShiftY);
                double speed = Math.hypot(previousShiftX, previousShiftY);
                if (speed > 4 && change > speed * .5) { damping = .6f; dampedPairs++; }
            }
            previousShiftX = cameraMotion.e;
            previousShiftY = cameraMotion.f;
        } else {
            previousShiftX = previousShiftY = Float.NaN;
        }
        maxFine = MotionField.maxLength(fineRaw, w * h);
        maxCoarse = MotionField.maxLength(coarse, w * h);
        shareNearMax = maxFine > 0 ? MotionField.shareAtLeast(fineRaw, w * h, maxFine * .9f) : 0f;
        uploadField(fieldFine, fine, w, h);
        uploadField(fieldCoarse, coarse, w, h);
        motionReady = true;
    }

    /** A motion texture ([w] × [h], RGBA16F) read back into [out] (x, y per block). */
    private void readField(int texture, int w, int h, float[] out) {
        bindTarget(texture, w, h);
        readback.clear();
        GLES31.glReadPixels(0, 0, w, h, GLES31.GL_RGBA, GLES31.GL_FLOAT, readback);
        for (int i = 0; i < w * h; i++) {
            out[i * 2] = readback.get(i * 4);
            out[i * 2 + 1] = readback.get(i * 4 + 1);
        }
    }

    /** [field] (x, y per block) into [texture]. */
    private void uploadField(int texture, float[] field, int w, int h) {
        readback.clear();
        for (int i = 0; i < w * h; i++) {
            readback.put(field[i * 2]).put(field[i * 2 + 1]).put(0f).put(0f);
        }
        readback.flip();
        GLES31.glBindTexture(GLES31.GL_TEXTURE_2D, texture);
        GLES31.glTexSubImage2D(GLES31.GL_TEXTURE_2D, 0, 0, 0, w, h, GLES31.GL_RGBA, GLES31.GL_FLOAT, readback);
    }

    /** The luma of ring slot [slot] into its luma textures at half and quarter size (the motion estimation's input). */
    private void drawLuma(int slot) {
        if (lumaProgram == 0) lumaProgram = SgsrRenderer.program(FULLSCREEN, LUMA);
        if (luma[0] == 0) allocateWarp();
        GLES31.glUseProgram(lumaProgram);
        GLES31.glActiveTexture(GLES31.GL_TEXTURE0);
        GLES31.glBindTexture(GLES31.GL_TEXTURE_2D, ring[slot]);
        GLES31.glUniform1i(GLES31.glGetUniformLocation(lumaProgram, "src"), 0);
        bindTarget(luma[slot], lumaWidth, lumaHeight);
        GLES31.glUniform2f(GLES31.glGetUniformLocation(lumaProgram, "outSize"), lumaWidth, lumaHeight);
        GLES31.glDrawArrays(GLES31.GL_TRIANGLE_STRIP, 0, 4);
        bindTarget(lumaQuarter[slot], quarterWidth, quarterHeight);
        GLES31.glUniform2f(GLES31.glGetUniformLocation(lumaProgram, "outSize"), quarterWidth, quarterHeight);
        GLES31.glDrawArrays(GLES31.GL_TRIANGLE_STRIP, 0, 4);
    }

    /** [source] (the newest picture) warped [t] pair intervals along the pair's motion into the output texture. */
    private void drawWarp(int source, float t) {
        if (warpProgram == 0) warpProgram = SgsrRenderer.program(FULLSCREEN, WARP);
        bindTarget(output, width, height);
        GLES31.glUseProgram(warpProgram);
        final int[] units = {source, ring[1 - newest], fieldFine, fieldCoarse};
        final String[] names = {"src", "older", "fine", "coarse"};
        for (int i = 0; i < units.length; i++) {
            GLES31.glActiveTexture(GLES31.GL_TEXTURE0 + i);
            GLES31.glBindTexture(GLES31.GL_TEXTURE_2D, units[i]);
            GLES31.glUniform1i(GLES31.glGetUniformLocation(warpProgram, names[i]), i);
        }
        GLES31.glUniform2f(GLES31.glGetUniformLocation(warpProgram, "outSize"), width, height);
        GLES31.glUniform2f(GLES31.glGetUniformLocation(warpProgram, "lumaSize"), lumaWidth, lumaHeight);
        final MotionField.Affine m = cameraMotion;
        GLES31.glUniform4f(GLES31.glGetUniformLocation(warpProgram, "camera"), m.a, m.b, m.c, m.d);
        GLES31.glUniform2f(GLES31.glGetUniformLocation(warpProgram, "cameraShift"), m.e, m.f);
        GLES31.glUniform2f(GLES31.glGetUniformLocation(warpProgram, "cameraCenter"), m.cx, m.cy);
        GLES31.glUniform1f(GLES31.glGetUniformLocation(warpProgram, "useCamera"), cameraFitted ? 1f : 0f);
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

    /**
     * The luma pairs at half and quarter the picture size, rounded down to whole search blocks, the motion textures (one texel per
     * block) and the fields the warp reads (the half-size grid).
     */
    private void allocateWarp() {
        GLES31.glGetIntegerv(GL_MOTION_ESTIMATION_SEARCH_BLOCK_X_QCOM, query, 0);
        blockX = Math.max(1, query[0]);
        GLES31.glGetIntegerv(GL_MOTION_ESTIMATION_SEARCH_BLOCK_Y_QCOM, query, 0);
        blockY = Math.max(1, query[0]);
        lumaWidth = Math.max(blockX, width / 2 / blockX * blockX);
        lumaHeight = Math.max(blockY, height / 2 / blockY * blockY);
        quarterWidth = Math.max(blockX, width / 4 / blockX * blockX);
        quarterHeight = Math.max(blockY, height / 4 / blockY * blockY);
        for (int i = 0; i < 2; i++) {
            luma[i] = texture(GLES30.GL_R8, lumaWidth, lumaHeight);
            lumaQuarter[i] = texture(GLES30.GL_R8, quarterWidth, quarterHeight);
        }
        final int w = lumaWidth / blockX, h = lumaHeight / blockY;
        motion = texture(GLES30.GL_RGBA16F, w, h);
        motionQuarter = texture(GLES30.GL_RGBA16F, quarterWidth / blockX, quarterHeight / blockY);
        fieldFine = texture(GLES30.GL_RGBA16F, w, h);
        fieldCoarse = texture(GLES30.GL_RGBA16F, w, h);
        readback = ByteBuffer.allocateDirect(w * h * 4 * 4).order(ByteOrder.nativeOrder()).asFloatBuffer();
        fineRaw = new float[w * h * 2];
        fine = new float[w * h * 2];
        coarseRaw = new float[(quarterWidth / blockX) * (quarterHeight / blockY) * 2];
        coarse = new float[w * h * 2];
        LimeLog.info("Frame generation warp: luma " + lumaWidth + "x" + lumaHeight + " and " + quarterWidth + "x" + quarterHeight
                + ", search block " + blockX + "x" + blockY);
    }

    private void deleteFrames() {
        if (ring[0] != 0) GLES31.glDeleteTextures(3, new int[]{ring[0], ring[1], output}, 0);
        ring[0] = ring[1] = output = 0;
        if (luma[0] != 0) {
            GLES31.glDeleteTextures(8, new int[]{luma[0], luma[1], lumaQuarter[0], lumaQuarter[1], motion, motionQuarter, fieldFine,
                    fieldCoarse}, 0);
        }
        luma[0] = luma[1] = lumaQuarter[0] = lumaQuarter[1] = motion = motionQuarter = fieldFine = fieldCoarse = 0;
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
