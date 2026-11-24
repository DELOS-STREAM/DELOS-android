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
                last = now
            }
        }
    }
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
    Canvas(Modifier.size((240f * scale).px)) {
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
        // The ring gathers from dust (owner): motes rise from the bottom of the screen, start circling round the logo's centre and
        // spiral in onto the ring, landing in the order and direction the ring is drawn; the solid stroke follows them
        val motes = RING_MOTES
        for (i in 0 until motes) {
            val along = i / motes.toFloat()
            val seed = (i * 7919) % 97 / 97f
            val arrive = IntroTimeline.RING + 200f + along * 1100f
            val p = ((t - (arrive - MOTE_FLIGHT)) / MOTE_FLIGHT).coerceIn(0f, 1f)
            if (p <= 0f) continue
            val targetDeg = start + 360f * along
            val pos: Offset
            if (p < .5f) {
                // Rising from below the screen to where it joins the circling, 1.6 rings out and a bit more than half a turn back
                val q = EaseOut.transform(p / .5f)
                val joinDeg = Math.toRadians((targetDeg - 210f).toDouble())
                val join = center + Offset((kotlin.math.cos(joinDeg) * radius * 1.6f).toFloat(), (kotlin.math.sin(joinDeg) * radius * 1.6f).toFloat())
                val from = center + Offset((seed - .5f) * radius * 6f, radius * (6.5f + 2f * seed))
                pos = from + (join - from) * q
            } else {
                // Circling in: the angle runs on to the mote's place while the orbit tightens to the ring
                val q = Motion.EaseInOut.transform((p - .5f) / .5f)
                val deg = Math.toRadians((targetDeg - 210f + 210f * q).toDouble())
                val r = radius * (1.6f - .6f * q)
                pos = center + Offset((kotlin.math.cos(deg) * r).toFloat(), (kotlin.math.sin(deg) * r).toFloat())
            }
            val fade = if (p < 1f) (p / .15f).coerceAtMost(1f) else (1f - ((t - arrive - 120f) / 500f).coerceIn(0f, 1f))
            if (fade > 0f) drawCircle(colors.accent.copy(alpha = fade), radius = stroke * (.5f + .3f * seed), center = pos)
        }
        val solid = IntroTimeline.drawn(t, IntroTimeline.RING + 320f, 1100f)
        if (solid > 0f) drawArc(colors.accent, start, 360f * solid, false, topLeft = center - Offset(radius, radius),
            size = androidx.compose.ui.geometry.Size(radius * 2f, radius * 2f), style = Stroke(stroke, cap = StrokeCap.Round))
        if (halo > 0f) drawCircle(colors.accent2.copy(alpha = .35f * halo.coerceAtMost(1f)), radius = 9f * u * halo, center = moon)
        if (dot > 0f) drawCircle(colors.accent2, radius = 5.5f * u * dot, center = moon)
    }
}

/** How long one mote flies, rising and circling in. */
private const val MOTE_FLIGHT = 1500f

/** How many motes gather into the ring. */
private const val RING_MOTES = 72

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
    const val MOON = 4350f
    const val HALO = 4600f
    const val KICKER = 5000f
    const val WORDMARK = 5200f
    const val TAGLINE = 6000f
    const val PROMPT = 6600f
    const val END = 7800f

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
