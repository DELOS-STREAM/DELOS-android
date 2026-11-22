package com.limelight.binding.video;

import java.util.Arrays;

/**
 * The CPU side of frame generation's own warp (FrameGenerator): block motion vectors read back from GL_QCOM_motion_estimation,
 * cleaned up and summarised. A field is [w] × [h] blocks, two floats (x, y) per block, row-major, in luma pixels of the level it
 * was estimated at. Pure Java, no GL.
 */
final class MotionField {
    private MotionField() {}

    /** [in] filtered by a 3 × 3 median per component into [out] (edges use the blocks that exist): isolated wrong vectors go. */
    static void median3x3(float[] in, float[] out, int w, int h) {
        final float[] window = new float[9];
        for (int y = 0; y < h; y++) {
            for (int x = 0; x < w; x++) {
                for (int c = 0; c < 2; c++) {
                    int n = 0;
                    for (int dy = -1; dy <= 1; dy++) {
                        int yy = y + dy;
                        if (yy < 0 || yy >= h) continue;
                        for (int dx = -1; dx <= 1; dx++) {
                            int xx = x + dx;
                            if (xx < 0 || xx >= w) continue;
                            window[n++] = in[(yy * w + xx) * 2 + c];
                        }
                    }
                    Arrays.sort(window, 0, n);
                    out[(y * w + x) * 2 + c] = window[n / 2];
                }
            }
        }
    }

    /**
     * [coarse] ([cw] × [ch] blocks, in pixels of a level [scaleX] / [scaleY] times smaller) on the [w] × [h] grid of the finer level,
     * in its pixels: each fine block takes the coarse block it lies in.
     */
    static void upsample(float[] coarse, int cw, int ch, float scaleX, float scaleY, float[] out, int w, int h) {
        for (int y = 0; y < h; y++) {
            int cy = Math.min(ch - 1, y * ch / h);
            for (int x = 0; x < w; x++) {
                int cx = Math.min(cw - 1, x * cw / w);
                out[(y * w + x) * 2] = coarse[(cy * cw + cx) * 2] * scaleX;
                out[(y * w + x) * 2 + 1] = coarse[(cy * cw + cx) * 2 + 1] * scaleY;
            }
        }
    }

    /** The largest vector length of a field. */
    static float maxLength(float[] v, int count) {
        float max = 0f;
        for (int i = 0; i < count; i++) max = Math.max(max, (float) Math.hypot(v[i * 2], v[i * 2 + 1]));
        return max;
    }

    /** The share of vectors at least [length] long. */
    static float shareAtLeast(float[] v, int count, float length) {
        if (count == 0) return 0f;
        int n = 0;
        for (int i = 0; i < count; i++) if (Math.hypot(v[i * 2], v[i * 2 + 1]) >= length) n++;
        return n / (float) count;
    }

    /**
     * The camera's motion as an affine map about the centre ([cx], [cy]): v(x, y) = (a·(x−cx) + b·(y−cy) + e, c·(x−cx) + d·(y−cy) + f).
     * [inliers] of [total] blocks follow it within the final threshold.
     */
    static final class Affine {
        float a, b, c, d, e, f, cx, cy;
        int inliers, total;

        float x(float px, float py) { return a * (px - cx) + b * (py - cy) + e; }
        float y(float px, float py) { return c * (px - cx) + d * (py - cy) + f; }
    }

    /**
     * Fits the camera's motion to a [w] × [h] field whose blocks are [blockW] × [blockH] pixels: least squares, then twice again on the
     * blocks within 2.5 × the median residual, kept between [minThreshold] and [maxThreshold] pixels, so moving objects and wrong
     * matches do not pull the camera along. Returns false when fewer than a quarter of the blocks follow it within [maxThreshold]
     * (nothing moves together) or the fit is degenerate.
     */
    static boolean fitAffine(float[] v, int w, int h, float blockW, float blockH, float minThreshold, float maxThreshold, Affine out) {
        final int count = w * h;
        if (count < 6) return false;
        final boolean[] use = new boolean[count];
        Arrays.fill(use, true);
        out.cx = w * blockW / 2f;
        out.cy = h * blockH / 2f;
        out.total = count;
        final float[] residual = new float[count];
        float threshold = Float.MAX_VALUE;
        for (int pass = 0; pass < 3; pass++) {
            if (pass > 0) {
                for (int i = 0; i < count; i++) use[i] = residual[i] <= threshold;
            }
            if (!solve(v, w, h, blockW, blockH, use, out)) return false;
            int n = 0;
            for (int y = 0; y < h; y++) {
                for (int x = 0; x < w; x++) {
                    int i = y * w + x;
                    float px = (x + .5f) * blockW, py = (y + .5f) * blockH;
                    residual[i] = (float) Math.hypot(out.x(px, py) - v[i * 2], out.y(px, py) - v[i * 2 + 1]);
                }
            }
            float[] sorted = residual.clone();
            Arrays.sort(sorted);
            threshold = Math.min(maxThreshold, Math.max(minThreshold, 2.5f * sorted[count / 2]));
            for (int i = 0; i < count; i++) if (residual[i] <= threshold) n++;
            out.inliers = n;
        }
        return out.inliers * 4 >= count;
    }

    /** Least squares over the blocks in [use]; false when fewer than six or the system is singular. */
    private static boolean solve(float[] v, int w, int h, float blockW, float blockH, boolean[] use, Affine out) {
        double sxx = 0, sxy = 0, syy = 0, sx = 0, sy = 0, n = 0;
        double sxu = 0, syu = 0, su = 0, sxv = 0, syv = 0, sv = 0;
        for (int y = 0; y < h; y++) {
            for (int x = 0; x < w; x++) {
                int i = y * w + x;
                if (!use[i]) continue;
                double px = (x + .5f) * blockW - out.cx, py = (y + .5f) * blockH - out.cy;
                double u = v[i * 2], vv = v[i * 2 + 1];
                sxx += px * px; sxy += px * py; syy += py * py; sx += px; sy += py; n++;
                sxu += px * u; syu += py * u; su += u;
                sxv += px * vv; syv += py * vv; sv += vv;
            }
        }
        if (n < 6) return false;
        // [sxx sxy sx; sxy syy sy; sx sy n] · (a, b, e) = (sxu, syu, su), the same matrix for (c, d, f)
        double det = sxx * (syy * n - sy * sy) - sxy * (sxy * n - sy * sx) + sx * (sxy * sy - syy * sx);
        if (Math.abs(det) < 1e-9) return false;
        double[] first = cramer(sxx, sxy, sx, sxy, syy, sy, sx, sy, n, sxu, syu, su, det);
        double[] second = cramer(sxx, sxy, sx, sxy, syy, sy, sx, sy, n, sxv, syv, sv, det);
        out.a = (float) first[0]; out.b = (float) first[1]; out.e = (float) first[2];
        out.c = (float) second[0]; out.d = (float) second[1]; out.f = (float) second[2];
        return true;
    }

    private static double[] cramer(double m00, double m01, double m02, double m10, double m11, double m12, double m20, double m21,
                                   double m22, double r0, double r1, double r2, double det) {
        double x = (r0 * (m11 * m22 - m12 * m21) - m01 * (r1 * m22 - m12 * r2) + m02 * (r1 * m21 - m11 * r2)) / det;
        double y = (m00 * (r1 * m22 - m12 * r2) - r0 * (m10 * m22 - m12 * m20) + m02 * (m10 * r2 - r1 * m20)) / det;
        double z = (m00 * (m11 * r2 - r1 * m21) - m01 * (m10 * r2 - r1 * m20) + r0 * (m10 * m21 - m11 * m20)) / det;
        return new double[]{x, y, z};
    }
}
