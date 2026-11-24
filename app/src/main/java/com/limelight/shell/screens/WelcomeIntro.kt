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
import kotlin.math.pow

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
                val wave = (time - IntroTimeline.SHOCK) / Shockwave.DURATION_MS
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
        // The dust gathers on black (owner, for contrast); the burst's shockwave then opens the shell's animated background, black
        // left only beyond its front, until the front has passed every corner
        Canvas(Modifier.fillMaxSize()) {
            val wave = ((time - IntroTimeline.SHOCK) / Shockwave.DURATION_MS).coerceIn(0f, 1f)
            if (time < IntroTimeline.SHOCK) { drawRect(Color.Black); return@Canvas }
            if (wave >= 1f) return@Canvas
            val c = Shockwave.center
            val front = Shockwave.radius(wave, size.width, size.height)
            val edge = 150f
            val outer = front + edge
            drawRect(Brush.radialGradient(0f to Color.Transparent, ((front - edge).coerceAtLeast(0f) / outer) to Color.Transparent, 1f to Color.Black, center = c, radius = outer))
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
    val window = androidx.compose.ui.platform.LocalWindowInfo.current.containerSize
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
        // The ring fills from its lower left, clockwise (owner)
        val start = 135f
        val dot = IntroTimeline.spring(t, IntroTimeline.MOON, 420f)
        val halo = IntroTimeline.spring(t, IntroTimeline.HALO, 520f)
        // The ring gathers from dust (owner): motes in the PlayStation background's look (mostly fine light specks, a few larger ones
        // with a soft halo, some warm, twinkling, adding light) rise from the bottom middle, swing out to the logo's left and circle
        // it clockwise a few times, faster and faster as they close in, filling the ring from its lower left. They stay on the ring
        // until the pulse, then ride the shockwave out and fade; the solid ring, drawn as they land, stays
        val wave = ((t - IntroTimeline.SHOCK) / Shockwave.DURATION_MS).coerceIn(0f, 1f)
        val front = if (t < IntroTimeline.SHOCK) 0f else Shockwave.front(wave)
        // The pulse (owner): the logo winds back like a spring, shrinking just before the burst, then shoots out past its size and
        // bounces back as the shockwave leaves it
        val bump = when {
            t < IntroTimeline.SHOCK - WIND_MS -> 1f
            t < IntroTimeline.SHOCK -> 1f - .12f * Motion.EaseInOut.transform((t - IntroTimeline.SHOCK + WIND_MS) / WIND_MS)
            else -> {
                val k = ((t - IntroTimeline.SHOCK) / 1000f).coerceAtMost(1f)
                val w = 3f * 3.1416f * k
                1f + kotlin.math.exp(-3.5f * k) * (-.12f * kotlin.math.cos(w) + .14f * kotlin.math.sin(w)) * (1f - k * k * k)
            }
        }
        for (i in 0 until RING_MOTES) {
            val r1 = hash(i, 1); val r2 = hash(i, 2); val r3 = hash(i, 3); val r4 = hash(i, 4); val r5 = hash(i, 5)
            val along = i / RING_MOTES.toFloat()
            val land = IntroTimeline.RING + 300f + along * RING_FILL_MS + (r1 - .5f) * 300f
            val flight = 3200f + r2 * 1000f
            val p = ((t - (land - flight)) / flight).coerceAtMost(1f)
            if (p <= 0f) continue
            val targetDeg = start + 360f * along
            // One smooth spiral per mote, in polar terms round the logo's centre, both driven by the same clock so the path never
            // stops or kinks: the radius closes in from far below the screen first (a rise from the bottom middle), the angle
            // starts slowly and speeds up as the orbit tightens (out to the left, then one to three turns clockwise round the
            // logo), easing only in the last moment onto the mote's own place just off the ring's line
            val home = radius * (1f + (r3 - .5f) * .16f)
            val r0 = radius * (6.5f + 2.5f * r5)
            val fromDeg = 90f + (r3 - .5f) * 30f
            val laps = 360f * (1f + kotlin.math.floor(r2 * 2.99f))
            val turn = 1f - (1f - p.pow(2.8f)).pow(1.6f)
            val deg = Math.toRadians((fromDeg + (targetDeg + laps - fromDeg) * turn).toDouble())
            val wobble = kotlin.math.sin(p * 3.1416f * (2f + 2f * r1) + r4 * 6.28f) * radius * .16f * (1f - p).pow(1.5f)
            val r = home + (r0 - home) * (1f - p).pow(2.6f) + wobble
            // Landed dust winds back and springs out with the logo
            val rr = r * (if (p >= 1f) bump else 1f)
            var pos = center + Offset((kotlin.math.cos(deg) * rr).toFloat(), (kotlin.math.sin(deg) * rr).toFloat())
            var fade = (p / .12f).coerceAtMost(1f) * (.75f + .25f * kotlin.math.sin(t * (.004f + .006f * r2) + r3 * 6.28f))
            if (front > 0f) {
                // Carried out by the shockwave, each at its own share of the front's speed, fading on the way
                val dir = Math.toRadians(targetDeg.toDouble())
                val out = (Shockwave.radius(wave, window.width.toFloat(), window.height.toFloat()) - radius) * (.55f + .5f * r4)
                pos += Offset((kotlin.math.cos(dir) * out).toFloat(), (kotlin.math.sin(dir) * out).toFloat())
                fade *= 1f - ((t - IntroTimeline.SHOCK) / DUST_GONE_MS).coerceIn(0f, 1f)
            }
            if (fade <= 0f) continue
            // Mostly fine specks, a few larger and brighter with an orange glow (owner); the glow is a plain faint disc, not a
            // gradient, so there is no shader per mote per frame
            val grain = hash(i, 6)
            val warm = r4 < .3f
            val core = if (warm) colors.accent else androidx.compose.ui.graphics.lerp(colors.accent, Color.White, .6f + .35f * hash(i, 7))
            val dotR = stroke * (.035f + .17f * grain.pow(3f))
            val bright = fade * (.45f + .55f * hash(i, 8)) * (.6f + .4f * grain)
            if (grain > .45f) drawCircle(colors.accent.copy(alpha = .16f * bright), radius = dotR * 4.5f, center = pos, blendMode = androidx.compose.ui.graphics.BlendMode.Plus)
            drawCircle(core.copy(alpha = bright.coerceAtMost(1f)), radius = dotR, center = pos, blendMode = androidx.compose.ui.graphics.BlendMode.Plus)
        }
        // The solid ring, drawn round behind the landing dust, clockwise from the lower left; it stays after the pulse
        val solid = ((t - IntroTimeline.RING - 450f) / RING_FILL_MS).coerceIn(0f, 1f)
        val solidAlpha = IntroTimeline.progress(t, IntroTimeline.RING + 450f, 1200f)
        val ringTopLeft = center - Offset(radius * bump, radius * bump)
        val ringSize = androidx.compose.ui.geometry.Size(radius * 2f * bump, radius * 2f * bump)
        // The dust's orange light on the ring (owner): a glow hugging the stroke while the dust is on it, gone with the dust after the
        // burst, so the finished mark looks as it does everywhere else (no glow)
        val lit = solidAlpha * (1f - ((t - IntroTimeline.SHOCK) / DUST_GONE_MS).coerceIn(0f, 1f))
        if (solid > 0f && lit > 0f) for ((width, alpha) in RING_GLOW) drawArc(colors.accent.copy(alpha = alpha * lit), start, 360f * solid, false,
            topLeft = ringTopLeft, size = ringSize, style = Stroke(stroke * bump * width, cap = StrokeCap.Round))
        if (solid > 0f) drawArc(colors.accent.copy(alpha = solidAlpha), start, 360f * solid, false, topLeft = ringTopLeft,
            size = ringSize, style = Stroke(stroke * bump, cap = StrokeCap.Round))
        // Sparks (owner): at the burst some of the dust flies off the ring in every direction like sparks in the wind: fast at
        // first, braked by the air, drifting with a breeze up and to the right, flickering out
        val since = t - IntroTimeline.SHOCK
        if (since > 0f && since < SPARK_MAX_MS) for (j in 0 until SPARKS) {
            val s1 = hash(j, 11); val s2 = hash(j, 12); val s3 = hash(j, 13); val s4 = hash(j, 14); val s5 = hash(j, 15)
            val life = 900f + (SPARK_MAX_MS - 900f) * s1
            val age = since / life
            if (age >= 1f) continue
            val ang = s2 * 6.2832f
            val dir = Offset(kotlin.math.cos(ang), kotlin.math.sin(ang))
            val v0 = u * (.15f + .8f * s3.pow(1.6f))
            val decay = kotlin.math.exp(-SPARK_DRAG * since)
            val gust = since / 2000f
            val wind = Offset(40f * u * gust * gust, -26f * u * gust * gust)
            val sway = kotlin.math.sin(since * (.003f + .004f * s4) + s5 * 6.28f) * u * 4f * age
            val pos = center + dir * (radius * bump) + dir * (v0 / SPARK_DRAG * (1f - decay)) + wind + Offset(-dir.y * sway, dir.x * sway)
            val vel = dir * (v0 * decay) + Offset(80f * u * gust / 2000f, -52f * u * gust / 2000f)
            val flicker = .55f + .45f * kotlin.math.sin(since * (.02f + .03f * s4) + s5 * 40f)
            val a = (1f - age).pow(1.3f) * flicker
            val core = if (s5 < .55f) colors.accent else androidx.compose.ui.graphics.lerp(colors.accent, Color.White, .8f)
            drawLine(core.copy(alpha = a), pos - vel * 26f, pos, strokeWidth = u * (.2f + .35f * s3), cap = StrokeCap.Round,
                blendMode = androidx.compose.ui.graphics.BlendMode.Plus)
        }
        if (halo > 0f) drawCircle(colors.accent2.copy(alpha = .35f * halo.coerceAtMost(1f)), radius = 9f * u * halo, center = moon)
        if (dot > 0f) drawCircle(colors.accent2, radius = 5.5f * u * dot, center = moon)
    }
}

/** How many motes gather into the ring. */
private const val RING_MOTES = 460

/** How many sparks fly off at the burst, how long the longest lives, and the air's braking (per millisecond). */
private const val SPARKS = 240
private const val SPARK_MAX_MS = 2600f
private const val SPARK_DRAG = .0028f

/** How long after the burst the dust (riding the shockwave) and its light on the ring are gone. */
private const val DUST_GONE_MS = 1500f

/** The ring's glow while the dust lights it: wider strokes (times the ring's) at low alpha round the solid one. */
private val RING_GLOW = listOf(3.2f to .08f, 1.9f to .16f)

/** How long the dust takes to fill the ring, from its first mote landing to its last. */
private const val RING_FILL_MS = 2100f

/** How long the logo winds back before the burst. */
private const val WIND_MS = 420f

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
    const val RING = 3800f
    const val SHOCK = 6850f
    const val MOON = 7100f
    const val HALO = 7350f
    const val KICKER = 7750f
    const val WORDMARK = 7950f
    const val TAGLINE = 8750f
    const val PROMPT = 9350f
    const val END = 10600f

    fun progress(t: Float, start: Float, length: Float): Float = EaseOut.transform(((t - start) / length).coerceIn(0f, 1f))
    fun spring(t: Float, start: Float, length: Float): Float = EaseSpring.transform(((t - start) / length).coerceIn(0f, 1f))

    /** Fades [layer] in from [rise] stage-ish pixels below, starting at [start]. */
    fun rise(layer: androidx.compose.ui.graphics.GraphicsLayerScope, t: Float, start: Float, rise: Float) {
        val p = progress(t, start, 700f)
        layer.alpha = p
        layer.translationY = (1f - p) * rise * layer.density
    }
}
