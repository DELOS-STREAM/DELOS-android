package com.limelight.shell.ps

import android.util.Log
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.unit.IntSize
import com.limelight.BuildConfig
import com.limelight.shell.ui.hsl
import kotlin.math.cos
import kotlin.math.exp
import kotlin.math.min
import kotlin.math.sin
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * The animated background of the PlayStation mode, in place of the user's background kind: deep navy with a wandering blue
 * and a cooler glow and two slow light ribbons, and over them the light dust of [PsDust]: motes carried along two curved
 * streams, large out-of-focus lights drifting past and giant soft ones far behind. It follows the approved prototype
 * (variant "Drift"): the base at .82 of its light, everything through the prototype's tone curve (see [lit] and [PsDust]).
 *
 * Drawn on the same 960 × 540 canvas as the other backgrounds (one scale, extended along the longer axis); the dust works
 * in the prototype's picture px, two to a canvas unit. Repainted at about 60 Hz at most; reduced motion slows it like the
 * others. The dust is made off the main thread when the size is known; until then only the base shows. A debug build logs
 * once a minute how many motes exist, how many elements a frame drew and the drawing's CPU time (average and worst).
 */
@Composable
fun PlayStationBackground(reducedMotion: Boolean, modifier: Modifier = Modifier) {
    val clock = remember { DoubleArray(1) }
    var tick by remember { mutableIntStateOf(0) }
    val speed by rememberUpdatedState(if (reducedMotion) .15 else 1.0)
    LaunchedEffect(Unit) {
        var last = 0L
        var drawn = 0L
        while (true) {
            withFrameNanos { now ->
                val dt = if (last == 0L) 0.0 else min((now - last) / 1e9, .1)
                last = now
                clock[0] += dt * speed
                if (now - drawn >= 15_000_000L) { drawn = now; tick++ }
            }
        }
    }
    var viewSize by remember { mutableStateOf(IntSize.Zero) }
    var dust by remember { mutableStateOf<PsDust?>(null) }
    LaunchedEffect(viewSize) {
        if (viewSize.width == 0 || viewSize.height == 0) return@LaunchedEffect
        val unit = min(viewSize.width / W, viewSize.height / H)
        dust = withContext(Dispatchers.Default) { PsDust(viewSize.width / unit * PICTURE, viewSize.height / unit * PICTURE, unit / PICTURE) }
    }
    val path = remember { Path() }
    val cost = remember { LongArray(4) }
    // Its own layer: the lights add up inside it, and the whole (opaque) picture covers what is under it at the layer's opacity
    Canvas(modifier.fillMaxSize().onSizeChanged { viewSize = it }.graphicsLayer { compositingStrategy = CompositingStrategy.Offscreen }) {
        @Suppress("UNUSED_VARIABLE")
        val redraw = tick // subscribes the drawing to the throttled clock
        val seconds = clock[0]
        val t = seconds.toFloat()
        val unit = min(size.width / W, size.height / H)
        val w = size.width / unit
        val h = size.height / unit
        scale(unit, unit, pivot = Offset.Zero) {
            drawRect(Brush.verticalGradient(0f to NAVY_TOP, 1f to NAVY_BOTTOM, endY = h), size = Size(w, h))
            drawGlow(w, h, t)
            drawRibbons(w, h, t, path)
            drawShapes(w, h, t)
            val motes = dust ?: return@scale
            val began = System.nanoTime()
            drawIntoCanvas { canvas ->
                val native = canvas.nativeCanvas
                native.save()
                native.scale(1f / PICTURE, 1f / PICTURE)
                motes.draw(native, seconds)
                native.restore()
            }
            if (BuildConfig.DEBUG) logCost(cost, motes, began, System.nanoTime())
        }
    }
}

private const val W = 960f
private const val H = 540f

/** Picture px (the prototype's 1920 × 1080) per canvas unit. */
private const val PICTURE = 2f

/** The share of the base's light under the dust. */
private const val BASE = .82f

/** [colour] at [strength] of its light, at the base's share, through the prototype's tone curve 1 − exp(−1.35 c). */
private fun lit(colour: Color, strength: Float = 1f): Color {
    val gain = -1.35f * BASE * strength
    return Color(1f - exp(gain * colour.red), 1f - exp(gain * colour.green), 1f - exp(gain * colour.blue))
}

private val NAVY_TOP = lit(hsl(222f, 82f, 7f))
private val NAVY_BOTTOM = lit(hsl(226f, 88f, 3f))
private val GLOW_BLUE = lit(hsl(214f, 100f, 50f), .24f)
private val GLOW_CYAN = lit(hsl(192f, 90f, 55f), .16f)
// The lines' colour as the prototype shows them (the line over its own glow, toned): a light teal blue, the second greener
private val RIBBON_LINE = arrayOf(lit(hsl(196f, 85f, 64f), .62f), lit(hsl(186f, 80f, 60f), .62f))
private val RIBBON_GLOW = arrayOf(lit(hsl(196f, 85f, 60f), .1f), lit(hsl(186f, 80f, 58f), .1f))

/** Adds one frame's drawing time to [cost] (sum, worst, frames, last log; ns) and logs the minute's figures when it is over. */
private fun logCost(cost: LongArray, dust: PsDust, began: Long, ended: Long) {
    val took = ended - began
    cost[0] += took
    if (took > cost[1]) cost[1] = took
    cost[2]++
    if (cost[3] == 0L) cost[3] = ended
    if (ended - cost[3] < 60_000_000_000L) return
    Log.d("PsBackground", "dust: ${dust.candidates} motes, frame drew ${dust.drawnPoints} points, ${dust.drawnSprites} sprites, " +
        "${dust.drawnGlints} glint lines; CPU ${"%.2f".format(cost[0] / 1e6 / cost[2])} ms average, ${"%.2f".format(cost[1] / 1e6)} ms worst over ${cost[2]} frames")
    cost[0] = 0; cost[1] = 0; cost[2] = 0; cost[3] = ended
}

/** A wide PlayStation-blue light that wanders slowly behind the content, a cooler one opposite it. */
private fun DrawScope.drawGlow(w: Float, h: Float, t: Float) {
    val a = Offset(w * (.62f + .14f * sin(t * .07f)), h * (.42f + .12f * cos(t * .05f)))
    drawRect(Brush.radialGradient(0f to GLOW_BLUE, 1f to GLOW_BLUE.copy(alpha = 0f), center = a, radius = w * .55f), size = Size(w, h), blendMode = BlendMode.Plus)
    val b = Offset(w * (.22f + .1f * cos(t * .06f)), h * (.8f + .08f * sin(t * .08f)))
    drawRect(Brush.radialGradient(0f to GLOW_CYAN, 1f to GLOW_CYAN.copy(alpha = 0f), center = b, radius = w * .42f), size = Size(w, h), blendMode = BlendMode.Plus)
}

/** Two thin lines of light that sweep across the screen in long waves (owner, 2026-11-23: the lines only, no wide blue band). */
private fun DrawScope.drawRibbons(w: Float, h: Float, t: Float, path: Path) {
    for (i in 0 until 2) {
        val y0 = h * (.58f + i * .16f)
        val sp = .16f + i * .07f
        path.rewind()
        var x = -40f
        path.moveTo(x, y0)
        while (x <= w + 40f) {
            path.lineTo(x, y0 + sin(x * .0052f + t * sp + i * 2.1f) * 52f + sin(x * .011f - t * sp * 1.4f) * 16f)
            x += 16f
        }
        val glow = RIBBON_GLOW[i]
        drawPath(path, Brush.horizontalGradient(0f to glow.copy(alpha = 0f), .5f to glow, 1f to glow.copy(alpha = 0f), startX = 0f, endX = w),
            style = Stroke(7f), blendMode = BlendMode.Plus)
        val line = RIBBON_LINE[i]
        drawPath(path, Brush.horizontalGradient(0f to line.copy(alpha = 0f), .5f to line, 1f to line.copy(alpha = 0f), startX = 0f, endX = w),
            style = Stroke(1.4f), blendMode = BlendMode.Plus)
    }
}

/**
 * The four PlayStation shapes ([PsShapes], owner 2026-11-24): [SHAPES] faint outlines at three depths, each with its own lane,
 * speed and slow turn, rising and wrapping from the top back to the bottom, under the dust.
 */
private fun DrawScope.drawShapes(w: Float, h: Float, t: Float) {
    for (i in 0 until SHAPES) {
        val depth = (i % 3) / 2f // 0 far, 1 near
        val size = 16f + depth * 24f
        val rise = 6f + depth * 10f
        val span = h + size * 4f
        val lane = fract(i * .618f + .11f)
        val x = w * lane + sin(t * .2f + i * 1.7f) * 24f
        val y = h + size * 2f - (fract(i * .37f) * span + t * rise) % span
        val turn = (t * (.05f + depth * .05f) * (if (i % 2 == 0) 1f else -1f) + i) * 57.29578f
        val colour = hsl(if (i % 4 == 1) 196f else 212f, 90f, 74f, .1f + depth * .16f)
        // The stroke in canvas units, divided by the scale the unit-box shape is drawn at
        val stroke = Stroke((1.1f + depth * .7f) / size, cap = StrokeCap.Round, join = StrokeJoin.Round)
        withTransform({
            translate(x, y)
            rotate(turn, pivot = Offset.Zero)
            scale(size, size, pivot = Offset.Zero)
        }) {
            drawPath(PsShapes.path(i % PsShapes.COUNT), colour, style = stroke, blendMode = BlendMode.Plus)
        }
    }
}

private const val SHAPES = 18

private fun fract(v: Float) = v - kotlin.math.floor(v)
