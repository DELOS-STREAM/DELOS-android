package com.limelight.binding.video;

import android.opengl.GLES31;

import org.junit.Test;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

/** What can be checked without a GPU: the pyramid's filter and what the shaders and the renderer agree on. */
public class SgsrTemporalTest {
    private static String shader(String name) throws Exception {
        return new String(Files.readAllBytes(new File("src/main/assets/sgsr", name).toPath()), StandardCharsets.UTF_8);
    }

    // With GL_LINEAR, textureLod reads level 0 whatever level it names: the flow matched the wrong pictures
    @Test
    public void theLumaPyramidSelectsItsMipLevel() {
        assertEquals(GLES31.GL_LINEAR_MIPMAP_NEAREST, SgsrTemporal.minFilter(SgsrTemporal.LEVELS));
    }

    // A mipmap filter on a texture with one level would make it incomplete for sampling rules that expect a chain
    @Test
    public void aSingleLevelTextureStaysLinear() {
        assertEquals(GLES31.GL_LINEAR, SgsrTemporal.minFilter(1));
    }

    @Test
    public void theCutDetectorReadsThePyramidsCoarsestLevel() throws Exception {
        assertTrue(shader("scene_change.comp").contains("const float COARSEST=" + (SgsrTemporal.LEVELS - 1) + ".0;"));
    }

    // The flow writes motion only; a shader that still read its alpha as trust would reject every pixel's history
    @Test
    public void noShaderReadsATrustValueFromTheFlow() throws Exception {
        assertTrue(shader("video_flow.comp").contains("imageStore(flowOutput,cell,vec4(motion,0.0,0.0));"));
        for (String name : new String[]{"sgsr2_video.frag", "video_activate.comp"}) {
            String source = shader(name);
            assertFalse(name, source.contains("backward.w"));
            assertFalse(name, source.contains("forward.w"));
        }
    }
}
