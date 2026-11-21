package com.limelight.binding.video;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class SgsrDimensionsTest {
    @Test
    public void native1080pLevelOneUses1440pInternalTarget() {
        SgsrDimensions.Plan plan = SgsrDimensions.plan(1920, 1080, 1920, 1080, 8192, 1);
        assertTrue(plan.isSupersampling());
        assertEquals(2560, plan.internalWidth);
        assertEquals(1440, plan.internalHeight);
    }

    @Test
    public void native1080pLevelTwoUses4kInternalTarget() {
        SgsrDimensions.Plan plan = SgsrDimensions.plan(1920, 1080, 1920, 1080, 8192, 2);
        assertTrue(plan.isSupersampling());
        assertEquals(3840, plan.internalWidth);
        assertEquals(2160, plan.internalHeight);
    }

    @Test
    public void onePixelAspectFitDifferenceStillUsesSupersampling() {
        SgsrDimensions.Plan plan = SgsrDimensions.plan(1920, 1080, 1920, 1079, 8192, 2);
        assertTrue(plan.isSupersampling());
        assertEquals(3840, plan.internalWidth);
        assertEquals(2156, plan.internalHeight);
    }

    @Test
    public void lowerResolutionUsesDisplaySizedUpscaling() {
        SgsrDimensions.Plan plan = SgsrDimensions.plan(1280, 720, 1920, 1080, 8192, 2);
        assertFalse(plan.isSupersampling());
        assertEquals(1920, plan.internalWidth);
        assertEquals(1080, plan.internalHeight);
    }

    @Test
    public void native4kNeverAttempts8k() {
        SgsrDimensions.Plan plan = SgsrDimensions.plan(3840, 2160, 3840, 2160, 16384, 2);
        assertFalse(plan.isSupersampling());
        assertEquals(3840, plan.internalWidth);
        assertEquals(2160, plan.internalHeight);
    }

    @Test
    public void gpuTextureLimitDisablesSupersampling() {
        SgsrDimensions.Plan plan = SgsrDimensions.plan(1920, 1080, 1920, 1080, 2048, 2);
        assertFalse(plan.isSupersampling());
    }

    // The native PyroWave planner follows the same rules, with the same constants passed in at creation
    @Test
    public void streamLargerThanDisplayIsNeverSupersampled() {
        SgsrDimensions.Plan plan = SgsrDimensions.plan(2560, 1440, 1920, 1080, 8192, 1);
        assertFalse(plan.isSupersampling());
        assertEquals(1920, plan.internalWidth);
        assertEquals(1080, plan.internalHeight);
    }

    @Test
    public void nativeToleranceIsInclusive() {
        assertTrue(SgsrDimensions.isNativeInput(1920, 1080, 1920 + SgsrDimensions.NATIVE_TOLERANCE, 1080));
        assertFalse(SgsrDimensions.isNativeInput(1920, 1080, 1920 + SgsrDimensions.NATIVE_TOLERANCE + 1, 1080));
    }

    // The flow reads each level through its own size: these must be the sizes GLES gives the mip levels
    @Test
    public void pyramidLevelsHalveRoundedDown() {
        int[] expected = {1079, 539, 269, 134};
        for (int level = 0; level < expected.length; level++) {
            assertEquals(expected[level], SgsrDimensions.pyramidSize(1079, level));
        }
        assertEquals(1, SgsrDimensions.pyramidSize(4, 3));
    }

    @Test
    public void flowGridCoversEveryPixelOfItsLevel() {
        int[] grid = {135, 68, 34, 17};
        int[] groups = {17, 9, 5, 3};
        for (int level = 0; level < grid.length; level++) {
            assertEquals(grid[level], SgsrDimensions.flowGrid(1080, level));
            assertEquals(groups[level], SgsrDimensions.flowGroups(1080, level));
            assertTrue(SgsrDimensions.flowGrid(1080, level) * 8 >= SgsrDimensions.pyramidSize(1080, level));
            assertTrue(SgsrDimensions.flowGroups(1080, level) * 8 >= SgsrDimensions.flowGrid(1080, level));
        }
        assertEquals(240, SgsrDimensions.flowGrid(1920, 0));
        assertEquals(30, SgsrDimensions.flowGroups(1920, 0));
    }
}
