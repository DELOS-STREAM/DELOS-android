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
import kotlin.math.pow

/**
 * The intro's shockwave (owner, 2026-11-24): when the logo's ring closes, a pulse runs out from it and bends what lies under it (the
 * shell's animated background) like a lens. It leaves fast over its first fifth, then eases out along a smooth curve, slowing until it
 * reaches every corner of the screen, and fades on the way. The ring's dust rides it out (WelcomeIntro). [progress] is 0 to 1 while it
 * runs, below 0 otherwise; [center] and [start] (the ring's centre and radius) are window pixels. Android 13 and newer (RuntimeShader).
 */
object Shockwave {
    const val DURATION_MS = 3600f

    /** How far past the farthest corner the front runs before it ends, in pixels, so nothing is left at the edge. */
    const val OVERSHOOT = 160f

    /** The window pixels from [center] to its farthest corner plus [OVERSHOOT], for a window of [width] by [height]. */
    fun reach(width: Float, height: Float): Float =
        kotlin.math.hypot(maxOf(center.x, width - center.x), maxOf(center.y, height - center.y)) + OVERSHOOT

    /** The front's radius in window pixels at [p] (0..1) for a window of [width] by [height]. */
    fun radius(p: Float, width: Float, height: Float): Float = start + (reach(width, height) - start) * front(p)

    var progress by mutableFloatStateOf(-1f)
    var center by mutableStateOf(Offset.Zero)
    var start by mutableFloatStateOf(0f)

    /** The front's share of the way to the farthest corner at [p] (0..1): a fast start over the first fifth, then a smooth slowing. */
    fun front(p: Float): Float = if (p < .2f) .38f * (p / .2f) else .38f + .62f * (1f - (1f - (p - .2f) / .8f).pow(2.4f))

    private val shader by lazy {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) RuntimeShader(SHADER) else null
    }

    /** The warp on a full-window layer while the pulse runs. */
    fun Modifier.shockwave(): Modifier = graphicsLayer {
        val p = progress
        val s = shader
        renderEffect = if (p < 0f || p > 1f || s == null || Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) null else {
            s.setFloatUniform("center", center.x, center.y)
            s.setFloatUniform("radius", radius(p, size.width, size.height))
            val fade = (1f - p).pow(1.4f)
            s.setFloatUniform("width", 90f + 220f * p)
            s.setFloatUniform("strength", 90f * fade)
            s.setFloatUniform("light", .14f * fade)
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
            // A lens profile: pushes outward just ahead of the front, pulls inward behind it
            float bend = x * exp(-x * x) * 1.65;
            float2 dir = dist > 0.001 ? d / dist : float2(0.0);
            half4 c = content.eval(p - dir * bend * strength);
            c.rgb += half3(exp(-x * x * 2.0) * light);
            return c;
        }
    """
}
