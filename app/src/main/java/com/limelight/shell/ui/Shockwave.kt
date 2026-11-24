package com.limelight.shell.ui

import android.graphics.RenderEffect
import android.graphics.RuntimeShader
import android.os.Build
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.asComposeRenderEffect
import androidx.compose.ui.graphics.graphicsLayer

/**
 * The intro's shockwave (owner, 2026-11-24): when the logo's ring closes, a pulse runs out from it to the screen's edges and warps
 * what lies under it (the shell's animated background) like a lens, then fades. [progress] is 0 to 1 while it runs, below 0 otherwise;
 * [center] and [start] are in window pixels (the ring's centre and radius). Android 13 and newer (RuntimeShader); older ones skip it.
 */
object Shockwave {
    var progress by mutableFloatStateOf(-1f)
    var center by mutableStateOf(Offset.Zero)
    var start by mutableFloatStateOf(0f)

    private val shader by lazy {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) RuntimeShader(SHADER) else null
    }

    /** The warp on a full-window layer while the pulse runs. */
    fun Modifier.shockwave(): Modifier = graphicsLayer {
        val p = progress
        val s = shader
        renderEffect = if (p < 0f || p > 1f || s == null || Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) null else {
            val reach = kotlin.math.hypot(size.width, size.height)
            // The front eases out across the screen; the warp is strongest at the start and fades as it travels
            val eased = 1f - (1f - p) * (1f - p) * (1f - p)
            s.setFloatUniform("center", center.x, center.y)
            s.setFloatUniform("radius", start + (reach - start) * eased)
            s.setFloatUniform("width", 40f + 160f * p)
            s.setFloatUniform("strength", 26f * (1f - p) * (1f - p))
            s.setFloatUniform("light", .16f * (1f - p))
            RenderEffect.createRuntimeShaderEffect(s, "content").asComposeRenderEffect()
        }
    }

    private const val SHADER = """
        uniform shader content;
        uniform float2 center;
        uniform float radius;
        uniform float width;
        uniform float strength;
        uniform float light;
        half4 main(float2 p) {
            float2 d = p - center;
            float dist = length(d);
            float x = (dist - radius) / width;
            // A lens profile: pushes outward ahead of the front, pulls inward behind it
            float bend = x * exp(-x * x);
            float2 dir = dist > 0.001 ? d / dist : float2(0.0);
            half4 c = content.eval(p - dir * bend * strength);
            c.rgb += half3(exp(-x * x) * light);
            return c;
        }
    """
}
