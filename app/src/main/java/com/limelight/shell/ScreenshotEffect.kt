package com.limelight.shell

import android.graphics.Bitmap
import android.graphics.BitmapShader
import android.graphics.Rect
import android.graphics.RuntimeShader
import android.graphics.Shader
import android.os.Build
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ShaderBrush
import com.limelight.shell.ui.LocalReducedMotion
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.pow
import kotlin.math.sin

/**
 * The capture effect of the stream menu's camera (owner, 2026-11-27, prototype screenshot-fx A): a decent version of the intro's
 * shockwave. From the camera a lens wave runs over the game picture to every corner in 1.3 s, a bright core flashes at the button
 * with a faint veil over the screen, and 26 motes ride the front out and fade. Nothing is revealed: the bent picture is the copy of
 * the same frame the PNG is made from, drawn only inside the wave's band (outside it the live video shows), so an HDR stream keeps
 * its brightness everywhere else. Android 13+ bends the picture (RuntimeShader); older devices get the flash and the motes.
 * Reduced motion: none of it.
 */
internal class ScreenshotEffect {
    val progress = Animatable(-1f)
    var shot by mutableIntStateOf(0)
        private set
    /** The camera's centre in window pixels. */
    var origin by mutableStateOf(Offset.Zero)
    /** The copied frame and where the video sits in the window; null until the copy arrives. */
    var picture by mutableStateOf<Pair<Bitmap, Rect>?>(null)
        private set
    private var arrivedAt = 0f

    /** The copy arrived: it fades into the band over the next 6 % of the run, so it never pops in. */
    fun show(bitmap: Bitmap, where: Rect) {
        arrivedAt = progress.value.coerceAtLeast(0f)
        picture = bitmap to where
    }

    /** How far the copy has faded in at [p]. */
    fun appear(p: Float): Float = ((p - arrivedAt) / .06f).coerceIn(0f, 1f)

    /** Runs one capture effect; the copy follows through [show]. */
    suspend fun play() {
        shot++
        picture = null
        progress.snapTo(0f)
        progress.animateTo(1f, tween(DURATION_MS, easing = LinearEasing))
        progress.snapTo(-1f)
        picture = null
    }

    companion object {
        const val DURATION_MS = 1300
        // Prototype A, in stage pixels (1920 × 1080)
        const val START = 48f
        const val OVERSHOOT = 160f
        const val STRENGTH = 22f
        const val WIDTH_START = 60f
        const val WIDTH_END = 160f
        const val LIGHT = .07f
        const val MOTES = 26
        const val VEIL = .16f

        /** The intro's front curve (Shockwave.front): a fast start over the first fifth, then a smooth slowing. */
        fun front(p: Float): Float = if (p < .2f) .38f * (p / .2f) else .38f + .62f * (1f - (1f - (p - .2f) / .8f).pow(2.4f))
    }
}

/** The bent picture inside the wave's band; under the menu's gradient, over the live video. */
@Composable
internal fun ScreenshotWarp(fx: ScreenshotEffect) {
    if (LocalReducedMotion.current || Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return
    val k = stageScale()
    val shader = remember { RuntimeShader(WARP) }
    val p = fx.progress.value
    val shot = fx.picture ?: return
    if (p !in 0f..1f) return
    Canvas(Modifier.fillMaxSize()) {
        val (bitmap, rect) = shot
        val o = fx.origin
        val reach = hypot(maxOf(o.x, size.width - o.x), maxOf(o.y, size.height - o.y)) + ScreenshotEffect.OVERSHOOT * k
        val start = ScreenshotEffect.START * k
        val fade = (1f - p).pow(1.4f)
        shader.setInputShader("picture", BitmapShader(bitmap, Shader.TileMode.CLAMP, Shader.TileMode.CLAMP))
        shader.setFloatUniform("center", o.x, o.y)
        shader.setFloatUniform("radius", start + (reach - start) * ScreenshotEffect.front(p))
        shader.setFloatUniform("width", (ScreenshotEffect.WIDTH_START + (ScreenshotEffect.WIDTH_END - ScreenshotEffect.WIDTH_START) * p) * k)
        shader.setFloatUniform("strength", ScreenshotEffect.STRENGTH * k * fade)
        shader.setFloatUniform("light", ScreenshotEffect.LIGHT * fade)
        shader.setFloatUniform("video", rect.left.toFloat(), rect.top.toFloat(), rect.width().toFloat(), rect.height().toFloat())
        shader.setFloatUniform("pictureSize", bitmap.width.toFloat(), bitmap.height.toFloat())
        shader.setFloatUniform("appear", fx.appear(p))
        drawRect(ShaderBrush(shader))
    }
}

/** The flash at the camera and the motes riding the wave; over everything, the menu included. */
@Composable
internal fun ScreenshotBurst(fx: ScreenshotEffect) {
    if (LocalReducedMotion.current) return
    val k = stageScale()
    val p = fx.progress.value
    if (p !in 0f..1f) return
    val motes = remember(fx.shot) { motes(fx.shot) }
    Canvas(Modifier.fillMaxSize()) {
        val o = fx.origin
        val fade = (1f - p).pow(1.4f)
        val reach = hypot(maxOf(o.x, size.width - o.x), maxOf(o.y, size.height - o.y)) + ScreenshotEffect.OVERSHOOT * k
        val start = ScreenshotEffect.START * k
        val radius = start + (reach - start) * ScreenshotEffect.front(p)
        // Flash: in over 3 % of the run, then out on a cubic
        val f = if (p < .03f) p / .03f else (1f - (p - .03f) / .97f).pow(3f)
        drawRect(Color.White.copy(alpha = ScreenshotEffect.VEIL * f))
        drawRect(Brush.radialGradient(
            0f to Color(1f, .98f, .92f, .9f * f), (70f / 240f) to Color(1f, .94f, .82f, .45f * f), 1f to Color.Transparent,
            center = o, radius = 240f * k,
        ))
        // Motes: near ones faster, fading as the wave fades
        val appear = if (p < .05f) p / .05f else 1f
        for (m in motes) {
            val a = fade * m.alpha * appear
            if (a <= .01f) continue
            val r = start + (radius - start) * m.reach
            val at = Offset(o.x + cos(m.angle) * r, o.y + sin(m.angle) * r)
            val s = m.size * k * (1f - .5f * p)
            drawCircle(m.color.copy(alpha = a), s, at)
            if (m.size > 2.4f) drawCircle(m.color.copy(alpha = a * .25f), s * 3f, at)
        }
    }
}

private class Mote(val angle: Float, val reach: Float, val alpha: Float, val size: Float, val color: Color)

/** The burst fans out towards the screen (the camera sits at the top right): left and down mostly. */
private fun motes(seed: Int): List<Mote> {
    var s = 11L + seed
    fun r(): Float { s = (s * 16807L) % 2147483647L; return s / 2147483647f }
    return List(ScreenshotEffect.MOTES) {
        Mote(
            angle = (Math.PI * (.45 + r() * .65)).toFloat() + (r() - .5f) * .6f,
            reach = .35f + r() * .75f,
            alpha = .5f + r() * .5f,
            size = 1.2f + r() * 2.8f,
            color = if (r() > .35f) Color(1f, .925f, .784f) else Color(1f, .808f, .47f),
        )
    }
}

// The intro's lens profile (Shockwave.kt): pushes outward just ahead of the front, pulls inward behind it. The copy shows only in
// the band around the front (mask), only where the video is, and fades in when it arrives (appear).
private const val WARP = """
    uniform shader picture;
    uniform float2 center;
    uniform float radius;
    uniform float width;
    uniform float strength;
    uniform float light;
    uniform float4 video;
    uniform float2 pictureSize;
    uniform float appear;
    half4 main(float2 p) {
        float2 d = p - center;
        float dist = length(d);
        float x = (dist - radius) / width;
        float bend = x * exp(-x * x) * 1.65;
        float2 dir = dist > 0.001 ? d / dist : float2(0.0);
        float2 q = p - dir * bend * strength;
        float2 uv = (q - video.xy) / video.zw;
        if (uv.x < 0.0 || uv.y < 0.0 || uv.x > 1.0 || uv.y > 1.0) return half4(0.0);
        half4 c = picture.eval(uv * pictureSize);
        c.rgb += half3(exp(-x * x * 2.0) * light);
        float mask = exp(-x * x * 0.35) * appear;
        return half4(c.rgb * mask, mask);
    }
"""
