package com.limelight.shell.ps

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotateRad
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.graphicsLayer
import com.limelight.shell.ui.hsl
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.sin

/**
 * The animated background of the PlayStation mode, in place of the user's background kind: deep navy with two slow light
 * ribbons sweeping across it and the four face-button shapes (triangle, circle, cross, square) drifting upward at three depths,
 * outlined and faint. Drawn on the same 960 × 540 canvas as the other backgrounds (one scale, extended along the longer axis),
 * repainted at about 60 Hz at most; reduced motion slows it like the others.
 */
@Composable
fun PlayStationBackground(reducedMotion: Boolean, modifier: Modifier = Modifier) {
    var t by remember { mutableFloatStateOf(0f) }
    var tick by remember { mutableIntStateOf(0) }
    val speed by rememberUpdatedState(if (reducedMotion) .15f else 1f)
    LaunchedEffect(Unit) {
        var last = 0L
        var drawn = 0L
        while (true) {
            withFrameNanos { now ->
                val dt = if (last == 0L) 0f else min((now - last) / 1e9f, .1f)
                last = now
                t += dt * speed
                if (now - drawn >= 15_000_000L) { drawn = now; tick++ }
            }
        }
    }
    val path = remember { Path() }
    Canvas(modifier.fillMaxSize().graphicsLayer { compositingStrategy = CompositingStrategy.Offscreen; blendMode = BlendMode.Screen }) {
        @Suppress("UNUSED_VARIABLE")
        val redraw = tick // subscribes the drawing to the throttled clock
        val unit = min(size.width / W, size.height / H)
        val w = size.width / unit
        val h = size.height / unit
        scale(unit, unit, pivot = Offset.Zero) {
            drawRect(Brush.verticalGradient(0f to hsl(222f, 82f, 7f), 1f to hsl(226f, 88f, 3f), endY = h), size = Size(w, h))
            drawGlow(w, h, t)
            drawRibbons(w, h, t, path)
            drawShapes(w, h, t, path)
        }
    }
}

private const val W = 960f
private const val H = 540f

/** A wide PlayStation-blue light that wanders slowly behind the content, a cooler one opposite it. */
private fun DrawScope.drawGlow(w: Float, h: Float, t: Float) {
    val a = Offset(w * (.62f + .14f * sin(t * .07f)), h * (.42f + .12f * cos(t * .05f)))
    drawRect(Brush.radialGradient(0f to hsl(214f, 100f, 50f, .24f), 1f to hsl(214f, 100f, 50f, 0f), center = a, radius = w * .55f), size = Size(w, h), blendMode = BlendMode.Plus)
    val b = Offset(w * (.22f + .1f * cos(t * .06f)), h * (.8f + .08f * sin(t * .08f)))
    drawRect(Brush.radialGradient(0f to hsl(192f, 90f, 55f, .16f), 1f to hsl(192f, 90f, 55f, 0f), center = b, radius = w * .42f), size = Size(w, h), blendMode = BlendMode.Plus)
}

/** Two soft bands of light that sweep across the screen in long waves. */
private fun DrawScope.drawRibbons(w: Float, h: Float, t: Float, path: Path) {
    for (i in 0 until 2) {
        val y0 = h * (.58f + i * .16f)
        val sp = .16f + i * .07f
        val thick = 34f + i * 18f
        path.rewind()
        var x = -40f
        path.moveTo(x, y0)
        while (x <= w + 40f) {
            path.lineTo(x, y0 + sin(x * .0052f + t * sp + i * 2.1f) * 52f + sin(x * .011f - t * sp * 1.4f) * 16f)
            x += 16f
        }
        val hue = if (i == 0) 212f else 198f
        drawPath(path, Brush.horizontalGradient(0f to hsl(hue, 100f, 60f, 0f), .45f to hsl(hue, 100f, 62f, .2f), 1f to hsl(hue, 100f, 60f, 0f), startX = 0f, endX = w),
            style = Stroke(thick), blendMode = BlendMode.Plus)
        drawPath(path, Brush.horizontalGradient(0f to hsl(hue, 100f, 80f, 0f), .5f to hsl(hue, 100f, 82f, .32f), 1f to hsl(hue, 100f, 80f, 0f), startX = 0f, endX = w),
            style = Stroke(1.2f), blendMode = BlendMode.Plus)
    }
}

/** The face-button shapes: [SHAPES] of them, each with its own lane, depth, speed and turn; they wrap from the top back to the bottom. */
private fun DrawScope.drawShapes(w: Float, h: Float, t: Float, path: Path) {
    for (i in 0 until SHAPES) {
        val depth = (i % 3) / 2f // 0 far, 1 near
        val size = 14f + depth * 22f
        val rise = 6f + depth * 10f
        val span = h + size * 4f
        val lane = fract(i * .618f + .11f)
        val x = w * lane + sin(t * .2f + i * 1.7f) * 24f
        val y = h + size * 2f - (fract(i * .37f) * span + t * rise) % span
        val turn = t * (.05f + depth * .05f) * (if (i % 2 == 0) 1f else -1f) + i
        val alpha = .1f + depth * .16f
        val color = hsl(if (i % 4 == 1) 196f else 212f, 90f, 74f, alpha)
        val stroke = Stroke(1.2f + depth * .8f)
        translate(x, y) {
            rotateRad(turn, pivot = Offset.Zero) {
                when (i % 4) {
                    0 -> { // triangle
                        path.rewind()
                        for (k in 0 until 3) {
                            val a = -PI.toFloat() / 2f + k * 2f * PI.toFloat() / 3f
                            if (k == 0) path.moveTo(cos(a) * size, sin(a) * size) else path.lineTo(cos(a) * size, sin(a) * size)
                        }
                        path.close()
                        drawPath(path, color, style = stroke, blendMode = BlendMode.Plus)
                    }
                    1 -> drawCircle(color, radius = size * .86f, center = Offset.Zero, style = stroke, blendMode = BlendMode.Plus)
                    2 -> { // cross
                        val d = size * .72f
                        drawLine(color, Offset(-d, -d), Offset(d, d), stroke.width, blendMode = BlendMode.Plus)
                        drawLine(color, Offset(-d, d), Offset(d, -d), stroke.width, blendMode = BlendMode.Plus)
                    }
                    else -> { // square
                        val d = size * .72f
                        drawRect(color, Offset(-d, -d), Size(d * 2f, d * 2f), style = stroke, blendMode = BlendMode.Plus)
                    }
                }
            }
        }
    }
}

private const val SHAPES = 18

private fun fract(v: Float) = v - kotlin.math.floor(v)
