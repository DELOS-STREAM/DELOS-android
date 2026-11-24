package com.limelight.shell.screens

import android.os.Build
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.focusable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.em
import com.limelight.R
import com.limelight.shell.ui.EaseOut
import com.limelight.shell.ui.EaseSpring
import com.limelight.shell.ui.Figtree
import com.limelight.shell.ui.Glyph
import com.limelight.shell.ui.GlyphButton
import com.limelight.shell.ui.HyperspaceTunnel
import com.limelight.shell.ui.InitialFocus
import com.limelight.shell.ui.LaunchHandoff
import com.limelight.shell.ui.LocalDelosColors
import com.limelight.shell.ui.LocalReducedMotion
import com.limelight.shell.ui.LocalStage
import com.limelight.shell.ui.PillButton
import com.limelight.shell.ui.Symbols
import com.limelight.shell.ui.Unbounded
import com.limelight.shell.ui.compact
import com.limelight.shell.ui.px
import com.limelight.shell.ui.stagePx
import com.limelight.shell.ui.tpx
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin

/**
 * The intro of the first launch and of a replayed tutorial (owner, 2026-11-24): an opening "film" over the whole window.
 * Black fades into the DELOS colours, dust streams past as the launch tunnel speeds up, the tunnel opens in a flash, the logo is drawn
 * last (the ring, the island, the moon), then "Welcome to", the wordmark letter by letter, the tagline and the prompt. A or a tap during
 * the film jumps to its end; after it they begin. Reduced motion: the end state fades in.
 */
@Composable
internal fun WelcomeIntro(onBegin: () -> Unit, onSkip: (() -> Unit)?) {
    val colors = LocalDelosColors.current
    val stage = LocalStage.current
    val reduced = LocalReducedMotion.current
    // Milliseconds of the film; starts past the end with reduced motion
    var time by remember { mutableFloatStateOf(if (reduced) IntroTimeline.END else 0f) }
    val tunnel = !reduced && Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU
    LaunchedEffect(Unit) {
        LaunchHandoff.travel = 0f
        LaunchHandoff.speedProgress = 0f
        var last = -1L
        while (true) {
            androidx.compose.runtime.withFrameNanos { now ->
                if (last >= 0) time += (now - last) / 1_000_000f
                last = now
            }
        }
    }
    // Read when pressed: the tap handler outlives this composition
    val press by androidx.compose.runtime.rememberUpdatedState<() -> Unit>({ if (time >= IntroTimeline.PROMPT) onBegin() else time = IntroTimeline.PROMPT })
    val focus = remember { FocusRequester() }
    InitialFocus(focus)
    Box(
        Modifier
            .fillMaxSize()
            .focusRequester(focus)
            .onKeyEvent { event ->
                val activate = event.key == Key.ButtonA || event.key == Key.Enter || event.key == Key.NumPadEnter || event.key == Key.DirectionCenter || event.key == Key.Spacebar
                if (activate && event.type == KeyEventType.KeyUp) press()
                activate
            }
            .focusable()
            .pointerInput(Unit) { detectTapGestures { press() } },
    ) {
        val t = time
        // The DELOS background behind everything: the colours the film fades into
        Canvas(Modifier.fillMaxSize()) {
            drawRect(colors.bg)
            drawRect(Brush.radialGradient(listOf(colors.accent3.copy(alpha = .22f), Color.Transparent), center = Offset(size.width * .5f, size.height * .42f), radius = size.maxDimension * .6f))
        }
        if (tunnel && t < IntroTimeline.TUNNEL_GONE) {
            val clockStart = remember { System.nanoTime() }
            Box(Modifier.fillMaxSize().graphicsLayer { alpha = IntroTimeline.tunnelAlpha(time) }) {
                HyperspaceTunnel(
                    progress = { IntroTimeline.tunnelSpeed(time) },
                    opening = { IntroTimeline.tunnelOpening(time) },
                    clock = { (System.nanoTime() - clockStart) / 1e9f },
                    modifier = Modifier.fillMaxSize(),
                )
            }
        }
        // Dust: streaks rushing past while the tunnel runs, then a slow drift around the island
        val dust = remember { IntroDust.make(160) }
        Canvas(Modifier.fillMaxSize()) {
            val warp = IntroTimeline.warp(time)
            val centre = Offset(size.width / 2f, size.height * .42f)
            val reach = size.maxDimension * .75f
            val seconds = time / 1000f
            dust.forEach { p ->
                val depth = (p.depth + seconds * (.05f + warp * .9f) * p.speed) % 1f
                val r = depth * depth * reach
                val a = p.angle + seconds * .03f * (1f - warp)
                val pos = centre + Offset(cos(a) * r, sin(a) * r * .72f)
                val fade = min(1f, depth * 3f) * IntroTimeline.dustAlpha(time)
                val color = if (p.cyan) colors.accent2 else colors.accent
                if (warp > .05f) {
                    val tail = r * (.04f + warp * .22f)
                    val from = centre + Offset(cos(a) * (r - tail), sin(a) * (r - tail) * .72f)
                    drawLine(color.copy(alpha = .75f * fade), from, pos, strokeWidth = stagePx(2f + p.size * 2f, stage), cap = StrokeCap.Round)
                } else {
                    drawCircle(color.copy(alpha = .55f * fade), radius = stagePx(1.5f + p.size * 2.5f, stage), center = pos)
                }
            }
            // The flash as the tunnel opens
            val flash = IntroTimeline.flash(time)
            if (flash > 0f) drawRect(Brush.radialGradient(listOf(colors.text.copy(alpha = .55f * flash), colors.accent.copy(alpha = .2f * flash), Color.Transparent), center = centre, radius = size.maxDimension * .7f))
        }
        Column(Modifier.align(Alignment.Center).padding(bottom = 30.px), horizontalAlignment = Alignment.CenterHorizontally) {
            AnimatedLogo({ time }, if (stage.compact) .8f else 1f)
            BasicText(
                stringResource(R.string.tutorial_welcome).uppercase(),
                Modifier.padding(top = 36.px).graphicsLayer { IntroTimeline.rise(this, time, IntroTimeline.KICKER, 24f) },
                style = TextStyle(color = colors.accent, fontFamily = Figtree, fontWeight = FontWeight.SemiBold, fontSize = 26.tpx, letterSpacing = .32.em),
            )
            Wordmark({ time }, if (stage.compact) 104 else 150)
            BasicText(
                stringResource(R.string.firstrun_boot_tagline),
                Modifier.padding(top = 10.px).graphicsLayer { IntroTimeline.rise(this, time, IntroTimeline.TAGLINE, 18f) },
                style = TextStyle(color = colors.text2, fontFamily = Figtree, fontSize = (if (stage.compact) 32 else 40).tpx, textAlign = TextAlign.Center),
            )
            Row(
                Modifier.padding(top = (if (stage.compact) 44 else 80).px).graphicsLayer {
                    IntroTimeline.rise(this, time, IntroTimeline.PROMPT, 14f)
                    // Breathes once it is there
                    val breathe = if (time > IntroTimeline.PROMPT + 600f) .82f + .18f * cos(((time - IntroTimeline.PROMPT) / 1400f) * 2f * PI.toFloat()) else 1f
                    alpha *= breathe
                },
                horizontalArrangement = Arrangement.spacedBy(14.px),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                val prompt = TextStyle(color = colors.text2, fontFamily = Figtree, fontWeight = FontWeight.Medium, fontSize = 28.tpx)
                GlyphButton(Glyph.A, 46)
                BasicText(stringResource(R.string.tutorial_press_a), style = prompt)
                GlyphButton(Glyph.TAP, 46)
                BasicText(stringResource(R.string.tutorial_tap_screen), style = prompt)
            }
            if (onSkip != null) Box(Modifier.padding(top = 28.px).graphicsLayer { IntroTimeline.rise(this, time, IntroTimeline.PROMPT + 200f, 10f) }) {
                PillButton(stringResource(R.string.firstrun_boot_skip), Symbols.FAST_FORWARD, onSkip, compact = true)
            }
        }
        // The film opens from black over the whole window
        val black = IntroTimeline.black(t)
        if (black > 0f) Canvas(Modifier.fillMaxSize()) { drawRect(Color.Black.copy(alpha = black)) }
    }
}

/** The DELOS ring drawn around, the island rising inside it, the moon arriving on its orbit (`BootLogo` animated). */
@Composable
private fun AnimatedLogo(time: () -> Float, scale: Float) {
    val colors = LocalDelosColors.current
    val stage = LocalStage.current
    Canvas(Modifier.size((260f * scale).px)) {
        val t = time()
        val k = stage.dpPerPx * density * scale
        val ring = IntroTimeline.progress(t, IntroTimeline.RING, 1000f)
        val isle = IntroTimeline.spring(t, IntroTimeline.ISLAND, 800f)
        val moon = IntroTimeline.progress(t, IntroTimeline.MOON, 700f)
        val bloom = IntroTimeline.progress(t, IntroTimeline.RING + 400f, 1200f)
        if (bloom > 0f) {
            val glow = 170f * k * (.7f + .3f * bloom)
            drawCircle(Brush.radialGradient(0f to colors.glow.copy(alpha = colors.glow.alpha * bloom), .66f to colors.glow.copy(alpha = colors.glow.alpha * .4f * bloom), 1f to Color.Transparent, center = center, radius = glow), radius = glow)
        }
        if (ring > 0f) drawArc(colors.accent, -90f, 360f * ring, false, topLeft = center - Offset(101f * k, 101f * k),
            size = androidx.compose.ui.geometry.Size(202f * k, 202f * k), style = Stroke(8f * k, cap = StrokeCap.Round))
        if (isle > 0f) {
            val r = 37f * k * isle
            val light = Offset(center.x - r * .3f, center.y - r * .4f)
            drawCircle(Brush.radialGradient(0f to colors.text, 1f to colors.accent3, center = light, radius = max(1f, r * 1.6f)), radius = r, alpha = .92f * min(1f, isle))
        }
        if (moon > 0f) {
            // Swings in along the orbit to its place at the top right, then keeps circling (7 s a turn, as on the boot logo)
            val settle = (1f - moon) * 140f
            val turn = max(0f, t - IntroTimeline.MOON - 700f) / 7000f * 360f
            val angle = Math.toRadians((-48.7 - settle + turn).toDouble())
            val orbit = 132f * k
            val pos = center + Offset((cos(angle) * orbit).toFloat(), (sin(angle) * orbit).toFloat())
            drawCircle(Brush.radialGradient(0f to colors.accent2.copy(alpha = .6f * moon), 1f to Color.Transparent, center = pos, radius = 44f * k), radius = 44f * k, center = pos)
            drawCircle(colors.accent2.copy(alpha = moon), radius = 14f * k, center = pos)
        }
    }
}

/** "DELOS", each letter dropping in after the one before. */
@Composable
private fun Wordmark(time: () -> Float, size: Int) {
    val colors = LocalDelosColors.current
    // l10n-ignore: the product wordmark
    Row(Modifier.padding(start = (size * .24f).px)) {
        "DELOS".forEachIndexed { i, letter -> // l10n-ignore
            BasicText(
                letter.toString(),
                Modifier.graphicsLayer {
                    val start = IntroTimeline.WORDMARK + i * 90f
                    val p = IntroTimeline.progress(time(), start, 620f)
                    alpha = p
                    translationY = (1f - p) * 60f * density
                    val s = .7f + .3f * IntroTimeline.spring(time(), start, 620f)
                    scaleX = s; scaleY = s
                },
                style = TextStyle(color = colors.text, fontFamily = Unbounded, fontWeight = FontWeight.SemiBold, fontSize = size.tpx, letterSpacing = .24.em),
            )
        }
    }
}

private class Mote(val angle: Float, val depth: Float, val speed: Float, val size: Float, val cyan: Boolean)

private object IntroDust {
    fun make(count: Int): List<Mote> {
        val random = java.util.Random(7)
        return List(count) {
            Mote(random.nextFloat() * 2f * PI.toFloat(), random.nextFloat(), .6f + random.nextFloat() * .8f, random.nextFloat(), random.nextFloat() < .28f)
        }
    }
}

/** The film's timeline in milliseconds and its curves. */
internal object IntroTimeline {
    const val TUNNEL_IN = 250f
    const val TUNNEL_OPEN = 2900f
    const val TUNNEL_GONE = 3900f
    const val RING = 3300f
    const val ISLAND = 3750f
    const val MOON = 4150f
    const val KICKER = 4500f
    const val WORDMARK = 4750f
    const val TAGLINE = 5500f
    const val PROMPT = 6100f
    const val END = 7400f

    fun progress(t: Float, start: Float, length: Float): Float = EaseOut.transform(((t - start) / length).coerceIn(0f, 1f))
    fun spring(t: Float, start: Float, length: Float): Float = EaseSpring.transform(((t - start) / length).coerceIn(0f, 1f))

    fun black(t: Float) = 1f - progress(t, 0f, 1100f)
    fun tunnelAlpha(t: Float) = progress(t, TUNNEL_IN, 900f)
    fun tunnelSpeed(t: Float) = progress(t, TUNNEL_IN, 2400f)
    fun tunnelOpening(t: Float) = progress(t, TUNNEL_OPEN, 900f)
    fun warp(t: Float) = min(progress(t, 400f, 1800f), 1f - progress(t, TUNNEL_OPEN, 1100f))
    fun dustAlpha(t: Float) = progress(t, 150f, 900f) * (1f - .45f * progress(t, TUNNEL_OPEN, 1400f))
    fun flash(t: Float): Float = if (t < TUNNEL_OPEN) 0f else {
        val x = (t - TUNNEL_OPEN) / 700f
        if (x >= 1f) 0f else sin(x * PI.toFloat()) * (1f - x * .4f)
    }

    /** Fades [layer] in from [rise] stage-ish pixels below, starting at [start]. */
    fun rise(layer: androidx.compose.ui.graphics.GraphicsLayerScope, t: Float, start: Float, rise: Float) {
        val p = progress(t, start, 700f)
        layer.alpha = p
        layer.translationY = (1f - p) * rise * layer.density
    }
}
