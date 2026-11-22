package com.limelight.binding.video;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

/** The camera fit and the clean-up of frame generation's block motion: what decides where a fast turn moves the picture. */
public class MotionFieldTest {
    private static final int W = 40, H = 24;
    private static final float BLOCK = 8f;

    /** A camera turn (translation plus a slight zoom) over the whole field. */
    private static float[] cameraField(float tx, float ty, float zoom) {
        float[] v = new float[W * H * 2];
        float cx = W * BLOCK / 2f, cy = H * BLOCK / 2f;
        for (int y = 0; y < H; y++) for (int x = 0; x < W; x++) {
            float px = (x + .5f) * BLOCK - cx, py = (y + .5f) * BLOCK - cy;
            v[(y * W + x) * 2] = tx + zoom * px;
            v[(y * W + x) * 2 + 1] = ty + zoom * py;
        }
        return v;
    }

    @Test public void theCameraIsFoundUnderAMovingObjectAndNoise() {
        float[] v = cameraField(-30f, 4f, .02f);
        // A car crossing a quarter of the screen against the camera, and scattered wrong matches
        for (int y = 8; y < 16; y++) for (int x = 5; x < 15; x++) { v[(y * W + x) * 2] = 25f; v[(y * W + x) * 2 + 1] = 0f; }
        for (int i = 0; i < W * H; i += 37) { v[i * 2] = 90f; v[i * 2 + 1] = -70f; }
        MotionField.Affine fit = new MotionField.Affine();
        assertTrue(MotionField.fitAffine(v, W, H, BLOCK, BLOCK, 1f, 4f, fit));
        float cx = W * BLOCK / 2f, cy = H * BLOCK / 2f;
        assertEquals(-30f, fit.x(cx, cy), .5f);
        assertEquals(4f, fit.y(cx, cy), .5f);
        assertEquals(.02f, fit.a, .002f);
        assertEquals(.02f, fit.d, .002f);
        // The car's blocks are not the camera's
        assertTrue(fit.inliers < W * H - 80);
    }

    @Test public void noCameraIsFittedWhenNothingAgrees() {
        float[] v = new float[W * H * 2];
        java.util.Random random = new java.util.Random(7);
        for (int i = 0; i < v.length; i++) v[i] = random.nextFloat() * 200f - 100f;
        assertFalse(MotionField.fitAffine(v, W, H, BLOCK, BLOCK, 1f, 4f, new MotionField.Affine()));
    }

    @Test public void theMedianRemovesAnIsolatedWrongVectorAndKeepsAnEdge() {
        float[] v = cameraField(10f, 0f, 0f);
        v[(5 * W + 5) * 2] = 100f;
        // An object edge: the right half moves the other way
        for (int y = 0; y < H; y++) for (int x = W / 2; x < W; x++) v[(y * W + x) * 2] = -10f;
        float[] out = new float[v.length];
        MotionField.median3x3(v, out, W, H);
        assertEquals(10f, out[(5 * W + 5) * 2], 0f);
        assertEquals(10f, out[(10 * W + W / 2 - 1) * 2], 0f);
        assertEquals(-10f, out[(10 * W + W / 2) * 2], 0f);
    }

    @Test public void aCoarseFieldLandsOnTheFineGridInFinePixels() {
        float[] coarse = {1f, 2f, 3f, 4f};
        float[] fine = new float[4 * 2 * 2];
        MotionField.upsample(coarse, 2, 1, 2f, 2f, fine, 4, 2);
        assertEquals(2f, fine[0], 0f);
        assertEquals(4f, fine[1], 0f);
        assertEquals(6f, fine[(1 * 4 + 3) * 2], 0f);
        assertEquals(8f, fine[(1 * 4 + 3) * 2 + 1], 0f);
    }
}
