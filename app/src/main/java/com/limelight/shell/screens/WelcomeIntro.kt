package com.limelight.shell.screens

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
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInWindow
import androidx.compose.runtime.DisposableEffect
import com.limelight.shell.ui.Shockwave
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
import com.limelight.shell.ui.InitialFocus
import com.limelight.shell.ui.LocalDelosColors
import com.limelight.shell.ui.Motion
import com.limelight.shell.ui.LocalReducedMotion
import com.limelight.shell.ui.LocalStage
import com.limelight.shell.ui.PillButton
import com.limelight.shell.ui.Symbols
import com.limelight.shell.ui.Unbounded
import com.limelight.shell.ui.compact
import com.limelight.shell.ui.px
import com.limelight.shell.ui.tpx
import kotlin.math.PI
import kotlin.math.cos

/**
 * The intro of the first launch and of a replayed tutorial (owner, 2026-11-24): one continuous opening over the whole window. Black
 * slowly turns into the DELOS colour, the shell's own animated background rises out of it, then the logo is put together piece by
 * piece (the ring drawn round, the moon dropping in at its end, the sharp halo opening round the moon), "Welcome to", the wordmark
 * letter by letter, the tagline and the prompt. It cannot be skipped: A or a tap begin once the prompt is there. Reduced motion: the end
 * state.
 */
@Composable
internal fun WelcomeIntro(onBegin: () -> Unit, onSkip: (() -> Unit)?) {
    val colors = LocalDelosColors.current
    val stage = LocalStage.current
    val reduced = LocalReducedMotion.current
    // Milliseconds of the film; starts past the end with reduced motion
    var time by remember { mutableFloatStateOf(if (reduced) IntroTimeline.END else 0f) }
    LaunchedEffect(Unit) {
        var last = -1L
        while (true) {
            androidx.compose.runtime.withFrameNanos { now ->
                if (last >= 0) time += (now - last) / 1_000_000f
                // The warp of the background follows the pulse (Shockwave): 0 to 1 over its run, off before and after
                val wave = (time - IntroTimeline.SHOCK) / SHOCK_MS
                Shockwave.progress = if (!reduced && wave in 0f..1f) wave else -1f
                last = now
            }
        }
    }
    DisposableEffect(Unit) { onDispose { Shockwave.progress = -1f } }
    // Read when pressed: the tap handler outlives this composition
    // The intro always plays to the end (owner): A or a tap only begin once the prompt is there
    val press by androidx.compose.runtime.rememberUpdatedState<() -> Unit>({ if (time >= IntroTimeline.PROMPT) onBegin() })
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
        // The shell's animated background lies behind every screen; a flat DELOS colour covers it first and melts away
        Canvas(Modifier.fillMaxSize()) {
            val cover = IntroTimeline.cover(time)
            if (cover > 0f) drawRect(colors.bg.copy(alpha = cover))
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
        // It all opens slowly from black
        Canvas(Modifier.fillMaxSize()) {
            val black = IntroTimeline.black(time)
            if (black > 0f) drawRect(Color.Black.copy(alpha = black))
        }
    }
}

/**
 * The DELOS mark (`LogoMark`: the ring, the moon at its top right and the moon's sharp halo) put together piece by piece: the ring is
 * drawn round from the moon's place, the moon drops in where the stroke ends, and its halo opens round it.
 */
@Composable
private fun AnimatedLogo(time: () -> Float, scale: Float) {
    val colors = LocalDelosColors.current
    val stage = LocalStage.current
    Canvas(Modifier.size((240f * scale).px).onGloballyPositioned { c ->
        // Where the shockwave starts (window pixels): the ring's centre and radius
        Shockwave.center = c.positionInWindow() + Offset(c.size.width / 2f, c.size.height / 2f)
        Shockwave.start = c.size.width / 40f * 18.5f
    }) {
        val t = time()
        // LogoMark's 40-unit geometry, scaled to the box
        val u = size.minDimension / 40f
        val stroke = 3f * u
        val radius = 20f * u - stroke / 2f
        val moon = center + Offset(18.5f * u, -12.5f * u)
        val start = Math.toDegrees(kotlin.math.atan2(-12.5, 18.5)).toFloat()
        val dot = IntroTimeline.spring(t, IntroTimeline.MOON, 420f)
        val halo = IntroTimeline.spring(t, IntroTimeline.HALO, 520f)
        // A soft light the ring gathers as it closes
        val bloom = IntroTimeline.progress(t, IntroTimeline.RING + 500f, 1400f)
        if (bloom > 0f) {
            val glow = radius * 1.9f
            drawCircle(Brush.radialGradient(0f to colors.glow.copy(alpha = colors.glow.alpha * .7f * bloom), .6f to colors.glow.copy(alpha = colors.glow.alpha * .25f * bloom), 1f to Color.Transparent, center = center, radius = glow), radius = glow)
        }
        // The ring gathers from dust (owner): motes in the PlayStation background's look (small sharp near-white points with a faint
        // halo, some warm, twinkling, adding light) float up from below the screen on wandering paths, start circling round the logo
        // and settle onto the ring, each at its own pace, in the direction the ring is drawn; the solid stroke fades in under them
        for (i in 0 until RING_MOTES) {
            val r1 = hash(i, 1); val r2 = hash(i, 2); val r3 = hash(i, 3); val r4 = hash(i, 4); val r5 = hash(i, 5)
            val along = i / RING_MOTES.toFloat()
            val land = IntroTimeline.RING + 250f + along * 1050f + (r1 - .5f) * 420f
            val flight = 1900f + r2 * 800f
            val p = (t - (land - flight)) / flight
            if (p <= 0f) continue
            val targetDeg = start + 360f * along
            val pos: Offset
            if (p < .55f) {
                // Floating up from below the screen, swaying, to where it joins the circling
                val q = Motion.EaseInOut.transform(p / .55f)
                val joinDeg = Math.toRadians((targetDeg - 170f - 160f * r5).toDouble())
                val joinR = radius * (1.5f + .9f * r4)
                val join = center + Offset((kotlin.math.cos(joinDeg) * joinR).toFloat(), (kotlin.math.sin(joinDeg) * joinR).toFloat())
                val from = center + Offset((r3 - .5f) * radius * 10f, radius * (6f + 3f * r4))
                val sway = kotlin.math.sin(q * 3.1416f * (1.5f + r2) + r1 * 6.28f) * radius * .45f * (1f - q)
                pos = from + (join - from) * q + Offset(sway, 0f)
            } else {
                // Circling in: on round the centre to its place while the orbit tightens, with a soft radial breathing
                val q = Motion.EaseInOut.transform(((p - .55f) / .45f).coerceAtMost(1f))
                val deg = Math.toRadians((targetDeg - 170f - 160f * r5 + (170f + 160f * r5) * q).toDouble())
                val r = radius * (1.5f + .9f * r4 - (.5f + .9f * r4) * q) + kotlin.math.sin(q * 6.28f + r1 * 6.28f) * radius * .06f * (1f - q)
                pos = center + Offset((kotlin.math.cos(deg) * r).toFloat(), (kotlin.math.sin(deg) * r).toFloat())
            }
            val twinkle = .75f + .25f * kotlin.math.sin(t * (.004f + .006f * r2) + r3 * 6.28f)
            val fade = (p / .12f).coerceAtMost(1f) * (1f - ((t - land - 150f) / 600f).coerceIn(0f, 1f)) * twinkle
            if (fade <= 0f) continue
            val warm = r4 < .35f
            val core = if (warm) colors.accent else androidx.compose.ui.graphics.lerp(colors.accent, Color.White, .75f)
            val size = stroke * (.16f + .2f * r5)
            drawCircle(core.copy(alpha = .22f * fade), radius = size * 3.2f, center = pos, blendMode = androidx.compose.ui.graphics.BlendMode.Plus)
            drawCircle(core.copy(alpha = fade), radius = size, center = pos, blendMode = androidx.compose.ui.graphics.BlendMode.Plus)
        }
        // The solid ring fades in under the settling dust, running round in the direction it is drawn
        val solid = IntroTimeline.drawn(t, IntroTimeline.RING + 400f, 1100f)
        val solidAlpha = IntroTimeline.progress(t, IntroTimeline.RING + 400f, 1300f)
        // The pulse when the ring closes: the logo bumps and a wave runs out (the backdrop's warp follows it: Shockwave)
        val pulse = ((t - IntroTimeline.SHOCK) / 700f).coerceIn(0f, 1f)
        val bump = if (pulse in 0f..1f) 1f + .07f * kotlin.math.sin(pulse * 3.1416f) else 1f
        if (pulse > 0f && pulse < 1f) {
            val wave = radius * (1f + 5f * EaseOut.transform(pulse))
            drawCircle(colors.accent.copy(alpha = .55f * (1f - pulse)), radius = wave, center = center, style = Stroke(stroke * (1.4f - pulse)))
        }
        if (solid > 0f) drawArc(colors.accent.copy(alpha = solidAlpha), start, 360f * solid, false, topLeft = center - Offset(radius * bump, radius * bump),
            size = androidx.compose.ui.geometry.Size(radius * 2f * bump, radius * 2f * bump), style = Stroke(stroke * bump, cap = StrokeCap.Round))
        if (halo > 0f) drawCircle(colors.accent2.copy(alpha = .35f * halo.coerceAtMost(1f)), radius = 9f * u * halo, center = moon)
        if (dot > 0f) drawCircle(colors.accent2, radius = 5.5f * u * dot, center = moon)
    }
}

/** How many motes gather into the ring. */
private const val RING_MOTES = 220

/** How long the shockwave runs out to the screen's edges. */
private const val SHOCK_MS = 1300f

/** A fixed pseudo-random value in 0..1 for mote [i] and channel [k]. */
private fun hash(i: Int, k: Int): Float {
    var x = i * 374761393 + k * 668265263
    x = (x xor (x ushr 13)) * 1274126177
    return ((x xor (x ushr 16)) and 0xFFFF) / 65535f
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


/** The film's timeline in milliseconds and its curves. */
internal object IntroTimeline {
    const val RING = 3200f
    const val SHOCK = 4700f
    const val MOON = 4900f
    const val HALO = 5150f
    const val KICKER = 5500f
    const val WORDMARK = 5700f
    const val TAGLINE = 6500f
    const val PROMPT = 7100f
    const val END = 8300f

    fun progress(t: Float, start: Float, length: Float): Float = EaseOut.transform(((t - start) / length).coerceIn(0f, 1f))
    fun spring(t: Float, start: Float, length: Float): Float = EaseSpring.transform(((t - start) / length).coerceIn(0f, 1f))
    /** The ring's stroke: even speed in the middle, easing in and out at its ends. */
    fun drawn(t: Float, start: Float, length: Float): Float = Motion.EaseInOut.transform(((t - start) / length).coerceIn(0f, 1f))

    /**
     * One continuous lift: black thins out over 1.6 s, and the flat DELOS colour under it already melts into the animated background
     * from 0.8 s to 3 s, so there is no step between the two.
     */
    fun black(t: Float) = 1f - Motion.EaseInOut.transform((t / 1600f).coerceIn(0f, 1f))
    fun cover(t: Float) = 1f - Motion.EaseInOut.transform(((t - 800f) / 2200f).coerceIn(0f, 1f))

    /** Fades [layer] in from [rise] stage-ish pixels below, starting at [start]. */
    fun rise(layer: androidx.compose.ui.graphics.GraphicsLayerScope, t: Float, start: Float, rise: Float) {
        val p = progress(t, start, 700f)
        layer.alpha = p
        layer.translationY = (1f - p) * rise * layer.density
    }
}
