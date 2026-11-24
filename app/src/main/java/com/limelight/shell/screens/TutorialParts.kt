package com.limelight.shell.screens

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.GraphicsLayerScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.em
import com.limelight.shell.ui.glass
import com.limelight.shell.ui.EaseOut
import com.limelight.shell.ui.EaseSpring
import com.limelight.shell.ui.Figtree
import com.limelight.shell.ui.Glyph
import com.limelight.shell.ui.GlyphButton
import com.limelight.shell.ui.LocalDelosColors
import com.limelight.shell.ui.LocalReducedMotion
import com.limelight.shell.ui.LocalStage
import com.limelight.shell.ui.Motion
import com.limelight.shell.ui.Symbol
import com.limelight.shell.ui.Symbols
import com.limelight.shell.ui.Unbounded
import com.limelight.shell.ui.compact
import com.limelight.shell.ui.focusEffects
import com.limelight.shell.ui.PointerLook
import com.limelight.shell.ui.animateFocusScale
import com.limelight.shell.ui.pressInput
import com.limelight.shell.ui.rememberPressPulse
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import com.limelight.shell.ui.px
import com.limelight.shell.ui.stagePx
import com.limelight.shell.ui.tpx
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

/** The success green of the tutorial ("Nice!"), the same as the stream menu's good values. */
internal val TutorialGreen = Color(0xFF6EE7A0)

/**
 * A page's entrance: 0 to 1 over its first second. Each part of the page reads it with its own delay ([stagger]), so the kicker, the
 * title, the text, the try pill and the stage arrive one after another.
 */
@Composable
internal fun rememberEntrance(key: Any): () -> Float {
    val reduced = LocalReducedMotion.current
    val value = remember(key) { Animatable(if (reduced) 1f else 0f) }
    LaunchedEffect(key) { if (!reduced) value.animateTo(1f, tween(1100, easing = LinearEasing)) }
    return { value.value }
}

/** Fades and lifts a part of the page in, [delay] of the entrance's 1.1 s later than the page. */
internal fun GraphicsLayerScope.stagger(entrance: Float, delay: Float, rise: Float = 34f) {
    val p = EaseOut.transform(((entrance * 1100f - delay) / 620f).coerceIn(0f, 1f))
    alpha = p
    translationY = (1f - p) * rise * density
}

@Composable
internal fun TutorialKicker(text: String, modifier: Modifier = Modifier, color: Color = LocalDelosColors.current.accent) {
    BasicText(text.uppercase(LocalConfiguration.current.locales[0]), modifier,
        style = TextStyle(color = color, fontFamily = Figtree, fontWeight = FontWeight.SemiBold, fontSize = 22.tpx, letterSpacing = .24.em))
}

/**
 * One lesson: what it teaches at the left (kicker, title, two sentences, the try pill), the real element on its lit stage at the right.
 * A compact stage stacks them. [progress] is the dots of the chapter's lessons.
 */
@Composable
internal fun TutorialLessonPage(
    key: Any, kicker: String, title: String, body: String, action: String, glyphs: List<Glyph>, success: Boolean, successText: String,
    progress: @Composable () -> Unit, stageContent: @Composable BoxScope.() -> Unit,
) {
    val stage = LocalStage.current
    val colors = LocalDelosColors.current
    val entrance = rememberEntrance(key)
    val text: @Composable () -> Unit = {
        Column(verticalArrangement = Arrangement.spacedBy((if (stage.compact) 16 else 24).px)) {
            TutorialKicker(kicker, Modifier.graphicsLayer { stagger(entrance(), 0f) })
            BasicText(title, Modifier.graphicsLayer { stagger(entrance(), 70f) },
                style = TextStyle(color = colors.text, fontFamily = Unbounded, fontWeight = FontWeight.Bold, fontSize = (if (stage.compact) 50 else 68).tpx, lineHeight = 1.08.em))
            BasicText(body, Modifier.graphicsLayer { stagger(entrance(), 140f) },
                style = TextStyle(color = colors.text2, fontFamily = Figtree, fontSize = (if (stage.compact) 28 else 31).tpx, lineHeight = 1.45.em))
            Box(Modifier.padding(top = 18.px).graphicsLayer { stagger(entrance(), 220f) }) { TutorialTryPill(glyphs, action, success, successText) }
        }
    }
    val lit: @Composable (Modifier) -> Unit = { modifier ->
        TutorialStageCard(success, modifier.graphicsLayer {
            val p = EaseSpring.transform(((entrance() * 1100f - 120f) / 760f).coerceIn(0f, 1f))
            alpha = p.coerceIn(0f, 1f)
            val s = .9f + .1f * p
            scaleX = s; scaleY = s
        }, stageContent)
    }
    if (stage.compact) {
        Column(Modifier.fillMaxSize().padding(horizontal = 48.px, vertical = 110.px), verticalArrangement = Arrangement.spacedBy(36.px)) {
            text()
            lit(Modifier.fillMaxWidth().weight(1f))
            Box(Modifier.graphicsLayer { stagger(entrance(), 300f, 0f) }) { progress() }
        }
    } else Box(Modifier.fillMaxSize()) {
        Row(Modifier.fillMaxSize().padding(start = 140.px, end = 110.px, top = 120.px, bottom = 150.px), horizontalArrangement = Arrangement.spacedBy(70.px),
            verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.width(700.px)) { text() }
            lit(Modifier.weight(1f).height(720.px))
        }
        Box(Modifier.align(Alignment.BottomStart).padding(start = 140.px, bottom = 96.px).graphicsLayer { stagger(entrance(), 300f, 0f) }) { progress() }
    }
}

/**
 * The lit stage the lesson's element stands on: dark glass with a hairline, a slow breathing pool of the main colour under the
 * element, turning green for a moment when the lesson is done.
 */
@Composable
private fun TutorialStageCard(success: Boolean, modifier: Modifier, content: @Composable BoxScope.() -> Unit) {
    val colors = LocalDelosColors.current
    val reduced = LocalReducedMotion.current
    val breathe = if (reduced) .5f else rememberInfiniteTransition(label = "tutorialStage").animateFloat(0f, 1f,
        infiniteRepeatable(tween(Motion.BREATHE * 2, easing = Motion.EaseInOut), RepeatMode.Reverse), label = "tutorialStageBreathe").value
    val shape = RoundedCornerShape(44.px)
    Box(modifier.clip(shape).glass(colors.surface.copy(alpha = .55f)).border(1.px, colors.line, shape)) {
        Canvas(Modifier.fillMaxSize()) {
            val pool = if (success) TutorialGreen else colors.accent
            drawRect(Brush.radialGradient(listOf(pool.copy(alpha = .14f + .08f * breathe), Color.Transparent),
                center = Offset(size.width / 2f, size.height * .55f), radius = size.minDimension * (.62f + .06f * breathe)))
            drawRect(Brush.radialGradient(listOf(colors.accent2.copy(alpha = .07f), Color.Transparent),
                center = Offset(size.width * .85f, size.height * .1f), radius = size.minDimension * .55f))
        }
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center, content = content)
    }
}

/**
 * What to try now: the button(s) to press, pulsing, and the action. When the lesson is done it turns into a green "Nice!" with a check,
 * and a ring of sparks bursts from it.
 */
@Composable
internal fun TutorialTryPill(glyphs: List<Glyph>, action: String, success: Boolean, successText: String) {
    val colors = LocalDelosColors.current
    val reduced = LocalReducedMotion.current
    val shape = RoundedCornerShape(60.px)
    Box {
        AnimatedContent(success, transitionSpec = {
            (fadeIn(tween(260)) + scaleIn(tween(420, easing = EaseSpring), initialScale = .8f)) togetherWith fadeOut(tween(160))
        }, label = "tutorialTry") { done ->
            val tint = if (done) TutorialGreen else colors.accent
            Row(Modifier.widthIn(max = 700.px).clip(shape).glass(tint.copy(alpha = .12f)).border(1.5.px, tint.copy(alpha = .45f), shape)
                .padding(start = 20.px, end = 32.px, top = 16.px, bottom = 16.px),
                horizontalArrangement = Arrangement.spacedBy(16.px), verticalAlignment = Alignment.CenterVertically) {
                if (done) Symbol(Symbols.CHECK_CIRCLE, 56.tpx, tint, filled = true)
                else glyphs.forEachIndexed { i, glyph ->
                    if (i > 0) BasicText("/", style = TextStyle(color = colors.text3, fontFamily = Figtree, fontSize = 30.tpx))
                    PulsingGlyph(glyph, i * 300)
                }
                BasicText(if (done) successText else action, Modifier.weight(1f, fill = false),
                    style = TextStyle(color = if (done) tint else colors.text, fontFamily = Figtree, fontWeight = FontWeight.SemiBold, fontSize = 30.tpx))
            }
        }
        if (success && !reduced) Burst(Modifier.matchParentSize())
    }
}

/** A button glyph that breathes, as if pressed again and again. */
@Composable
private fun PulsingGlyph(glyph: Glyph, offset: Int) {
    val reduced = LocalReducedMotion.current
    val press = if (reduced) 0f else rememberInfiniteTransition(label = "tutorialPress").animateFloat(0f, 1f,
        infiniteRepeatable(tween(1400, offset, easing = LinearEasing)), label = "tutorialPressPhase").value
    // A quick press (down in 120 ms, back in 260 ms) once per cycle, then rest
    val down = when {
        press < .1f -> press / .1f
        press < .3f -> 1f - (press - .1f) / .2f
        else -> 0f
    }
    Box(Modifier.graphicsLayer { val s = 1f - .14f * down; scaleX = s; scaleY = s }) { GlyphButton(glyph, 48) }
}

/** Sparks flying off in a ring, once. */
@Composable
private fun Burst(modifier: Modifier) {
    val colors = LocalDelosColors.current
    val stage = LocalStage.current
    val t = remember { Animatable(0f) }
    LaunchedEffect(Unit) { t.animateTo(1f, tween(900, easing = EaseOut)) }
    Canvas(modifier) {
        val p = t.value
        if (p >= 1f) return@Canvas
        val c = Offset(stagePx(48f, stage), size.height / 2f)
        drawCircle(TutorialGreen.copy(alpha = (1f - p) * .7f), radius = stagePx(30f + 120f * p, stage), center = c, style = Stroke(stagePx(3f * (1f - p) + .5f, stage)))
        repeat(14) { i ->
            val a = i / 14f * 2f * PI.toFloat() + .2f
            val r = stagePx(36f + 150f * p, stage)
            val color = if (i % 3 == 0) colors.accent else TutorialGreen
            drawCircle(color.copy(alpha = 1f - p), radius = stagePx(5f * (1f - p) + 1f, stage), center = c + Offset(cos(a) * r, sin(a) * r))
        }
    }
}

/**
 * The end page's one button, the game overview's Play 1:1 (GameOverview.OverviewButton, primary): the 86 px gold capsule, its focus
 * lift and press squeeze, the overview's ring (a 4 px accent band 2 px out with its soft glow) and Play's living gold sweep.
 */
@Composable
internal fun TutorialStartButton(label: String, icon: String, onClick: () -> Unit, requester: FocusRequester) {
    val colors = LocalDelosColors.current
    val reduced = LocalReducedMotion.current
    val stage = LocalStage.current
    var focused by remember { mutableStateOf(false) }
    val pulse = rememberPressPulse()
    val scale = animateFocusScale(focused, pulse.pressed, 1.06f)
    // Room round the capsule for the ring, its glow, the halo and the sweep (they reach ~40 px out)
    Box(Modifier.padding(44.px)) {
        Box(
            Modifier
                .focusEffects(
                    onClick = onClick, corner = 999f, focusScale = 1f, press = false, pressPulse = pulse,
                    ring = null, glow = null, focusRequester = requester,
                    onFocusChange = { focused = it }, onTouchTap = { it },
                )
                .pressInput(pulse)
                .graphicsLayer {
                    val s = scale.value * PointerLook.scale(0f, pulse.amount)
                    scaleX = s
                    scaleY = s
                }
                .drawBehind {
                    if (!focused) return@drawBehind
                    // The overview's ring: RingStyle(2f, 999f), a 4 px band and its 30 px glow
                    val w = stagePx(4f, stage)
                    val pad = stagePx(2f, stage)
                    val r = size.height / 2f + pad
                    val glow = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply {
                        style = android.graphics.Paint.Style.STROKE
                        strokeWidth = w * 2f
                        maskFilter = android.graphics.BlurMaskFilter(stagePx(30f, stage), android.graphics.BlurMaskFilter.Blur.NORMAL)
                        color = colors.glow.toArgb()
                    }
                    drawContext.canvas.nativeCanvas.drawRoundRect(-pad - w, -pad - w, size.width + pad + w, size.height + pad + w, r + w, r + w, glow)
                    drawRoundRect(colors.accent, Offset(-pad - w / 2f, -pad - w / 2f),
                        androidx.compose.ui.geometry.Size(size.width + 2f * pad + w, size.height + 2f * pad + w),
                        androidx.compose.ui.geometry.CornerRadius(r + w / 2f), style = Stroke(w))
                },
        ) {
            PlayEffect(focused, focused, reduced)
            Row(
                Modifier.height(86.px).clip(CircleShape).background(colors.accent).padding(horizontal = 54.px),
                verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.px),
            ) {
                Symbol(icon, 32.tpx, colors.onAccent, filled = focused)
                BasicText(label, maxLines = 1, style = TextStyle(color = colors.onAccent, fontFamily = Figtree, fontWeight = FontWeight.Bold, fontSize = 26.tpx))
            }
        }
    }
}

/** The chapter's lessons as dots, the current one long. */
@Composable
internal fun TutorialProgress(index: Int, count: Int) {
    val colors = LocalDelosColors.current
    Row(horizontalArrangement = Arrangement.spacedBy(10.px), verticalAlignment = Alignment.CenterVertically) {
        repeat(count) { i ->
            val current = i == index
            Box(Modifier.size((if (current) 56 else 14).px, 8.px).clip(RoundedCornerShape(4.px))
                .background(when { current -> colors.accent; i < index -> colors.accent.copy(alpha = .55f); else -> colors.text.copy(alpha = .16f) }))
        }
    }
}

/** A chapter's title card: its number drawn big and hollow behind, the title, one line, and how many lessons it has. */
@Composable
internal fun TutorialChapterCard(key: Any, number: Int, label: String, title: String, note: String, lessons: String) {
    val colors = LocalDelosColors.current
    val stage = LocalStage.current
    val entrance = rememberEntrance(key)
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        BasicText(number.toString(), Modifier.graphicsLayer {
            val p = EaseOut.transform(entrance().coerceIn(0f, 1f))
            alpha = .09f * p
            val s = 1.25f - .25f * p
            scaleX = s; scaleY = s
        }, style = TextStyle(color = colors.accent, fontFamily = Unbounded, fontWeight = FontWeight.Bold, fontSize = (if (stage.compact) 420 else 620).tpx))
        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(22.px)) {
            TutorialKicker(label, Modifier.graphicsLayer { stagger(entrance(), 80f) })
            BasicText(title, Modifier.graphicsLayer { stagger(entrance(), 160f, 50f) },
                style = TextStyle(color = colors.text, fontFamily = Unbounded, fontWeight = FontWeight.Bold, fontSize = (if (stage.compact) 72 else 104).tpx, textAlign = TextAlign.Center))
            BasicText(note, Modifier.widthIn(max = 1100.px).graphicsLayer { stagger(entrance(), 260f) },
                style = TextStyle(color = colors.text2, fontFamily = Figtree, fontSize = 34.tpx, textAlign = TextAlign.Center, lineHeight = 1.4.em))
            BasicText(lessons, Modifier.padding(top = 18.px).graphicsLayer { stagger(entrance(), 360f) },
                style = TextStyle(color = colors.text3, fontFamily = Figtree, fontWeight = FontWeight.Medium, fontSize = 24.tpx))
        }
    }
}

/** A big choice card of "How do you play?": a large icon, the title and one line; the usual focus ring, glow and lift. */
@Composable
internal fun TutorialChoiceCard(title: String, note: String, icon: String, badge: String?, modifier: Modifier, requester: FocusRequester, onClick: () -> Unit) {
    val colors = LocalDelosColors.current
    val stage = LocalStage.current
    var focused by remember { mutableStateOf(false) }
    val shape = RoundedCornerShape(40.px)
    Box(modifier.width((if (stage.compact) 760 else 560).px).height((if (stage.compact) 300 else 440).px)
        .focusRequester(requester)
        .focusEffects(onClick, corner = 40f, focusScale = 1.05f, onLookChange = { focused = it })
        .clip(shape).glass(colors.surface).border(1.px, colors.line, shape)) {
        Canvas(Modifier.fillMaxSize()) {
            if (focused) drawRect(Brush.radialGradient(listOf(colors.accent.copy(alpha = .16f), Color.Transparent), center = Offset(size.width * .25f, size.height * .3f), radius = size.maxDimension * .7f))
        }
        Symbol(icon, (if (stage.compact) 110 else 150).tpx, if (focused) colors.accent else colors.text2, modifier = Modifier.padding(48.px), filled = focused)
        if (badge != null) Row(Modifier.align(Alignment.TopEnd).padding(44.px).clip(RoundedCornerShape(30.px)).background(TutorialGreen.copy(alpha = .14f))
            .padding(horizontal = 16.px, vertical = 8.px), horizontalArrangement = Arrangement.spacedBy(8.px), verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(10.px).clip(RoundedCornerShape(5.px)).background(TutorialGreen))
            BasicText(badge, style = TextStyle(color = TutorialGreen, fontFamily = Figtree, fontWeight = FontWeight.SemiBold, fontSize = 20.tpx))
        }
        Column(Modifier.align(Alignment.BottomStart).padding(48.px), verticalArrangement = Arrangement.spacedBy(12.px)) {
            BasicText(title, style = TextStyle(color = colors.text, fontFamily = Unbounded, fontWeight = FontWeight.Bold, fontSize = 48.tpx))
            BasicText(note, style = TextStyle(color = colors.text2, fontFamily = Figtree, fontSize = 27.tpx, lineHeight = 1.35.em))
        }
    }
}

/**
 * A finger showing the gesture before the player tries it: a soft disc moving along [path] (stage pixels inside the stage card's
 * content box, from its centre), with a ring that fills while it holds. Loops until the lesson is done.
 */
@Composable
internal fun GhostFinger(path: GhostPath, modifier: Modifier = Modifier) {
    val colors = LocalDelosColors.current
    val stage = LocalStage.current
    if (LocalReducedMotion.current) return
    val t by rememberInfiniteTransition(label = "ghost").animateFloat(0f, 1f, infiniteRepeatable(tween(path.cycleMs, easing = LinearEasing)), label = "ghostPhase")
    Canvas(modifier.fillMaxSize()) {
        val state = path.at(t)
        if (state.alpha <= 0f) return@Canvas
        val c = Offset(size.width / 2f + stagePx(state.x, stage), size.height / 2f + stagePx(state.y, stage))
        val r = stagePx(34f * (1f - .15f * state.press), stage)
        drawCircle(Brush.radialGradient(listOf(Color.White.copy(alpha = .30f * state.alpha), Color.Transparent), center = c, radius = r * 2.4f), radius = r * 2.4f, center = c)
        drawCircle(Color.White.copy(alpha = .38f * state.alpha), radius = r, center = c)
        drawCircle(Color.White.copy(alpha = .8f * state.alpha), radius = r, center = c, style = Stroke(stagePx(2.5f, stage)))
        if (state.hold > 0f) drawArc(colors.accent.copy(alpha = state.alpha), -90f, 360f * state.hold, false,
            topLeft = c - Offset(r * 1.5f, r * 1.5f), size = androidx.compose.ui.geometry.Size(r * 3f, r * 3f), style = Stroke(stagePx(5f, stage)))
    }
}

internal data class GhostState(val x: Float, val y: Float, val alpha: Float, val press: Float = 0f, val hold: Float = 0f)

/** The gestures the ghost finger can show, each a loop of [cycleMs]. */
internal enum class GhostPath(val cycleMs: Int) {
    SWIPE_LEFT(2200), DOUBLE_TAP(2200), PULL_UP(2200), HOLD(2600), EDGE_RIGHT(2400), SWIPE_UP_BOTTOM(2400), TAP(1800);

    fun at(t: Float): GhostState {
        fun ease(x: Float) = EaseOut.transform(x.coerceIn(0f, 1f))
        fun fade(t: Float, from: Float, to: Float) = when {
            t < from -> 0f
            t < from + .08f -> (t - from) / .08f
            t < to - .08f -> 1f
            t < to -> (to - t) / .08f
            else -> 0f
        }
        fun down(from: Float, to: Float) = if (t >= from && t <= to) 1f else 0f
        return when (this) {
            SWIPE_LEFT -> GhostState(220f - 440f * ease((t - .15f) / .5f), 40f, fade(t, .05f, .8f), press = down(.12f, .7f))
            DOUBLE_TAP -> GhostState(0f, 40f, fade(t, .05f, .8f), press = maxOf(down(.25f, .32f), down(.45f, .52f)))
            PULL_UP -> GhostState(0f, 120f - 240f * ease((t - .2f) / .45f), fade(t, .05f, .8f), press = down(.15f, .68f))
            HOLD -> GhostState(0f, 40f, fade(t, .05f, .85f), press = down(.15f, .75f), hold = if (t > .75f) 0f else ((t - .15f) / .45f).coerceIn(0f, 1f))
            EDGE_RIGHT -> GhostState(-400f + 380f * ease((t - .2f) / .45f), 0f, fade(t, .05f, .8f), press = down(.15f, .68f))
            SWIPE_UP_BOTTOM -> GhostState(0f, 300f - 260f * ease((t - .2f) / .45f), fade(t, .05f, .8f), press = down(.15f, .68f))
            TAP -> GhostState(0f, 0f, fade(t, .05f, .7f), press = down(.3f, .4f))
        }
    }
}

/** A stand-in for a streamed game: a dusk landscape in a rounded frame, for the lessons about the stream. */
@Composable
internal fun TutorialGameScreen(modifier: Modifier, label: String, content: @Composable BoxScope.() -> Unit = {}) {
    val colors = LocalDelosColors.current
    val shape = RoundedCornerShape(28.px)
    Box(modifier.clip(shape).border(1.px, colors.line, shape)) {
        Canvas(Modifier.fillMaxSize()) {
            drawRect(Brush.verticalGradient(listOf(Color(0xFF2B2459), Color(0xFF6E3C78), Color(0xFFE08A5B), Color(0xFF2C2340))))
            val w = size.width
            val h = size.height
            val sun = Offset(w * .62f, h * .58f)
            drawCircle(Brush.radialGradient(listOf(Color(0xFFFFD58A), Color(0x00FFD58A)), center = sun, radius = h * .3f), radius = h * .3f, center = sun)
            val hills = androidx.compose.ui.graphics.Path().apply {
                moveTo(0f, h * .72f); cubicTo(w * .25f, h * .6f, w * .45f, h * .78f, w * .7f, h * .66f); cubicTo(w * .85f, h * .6f, w * .95f, h * .68f, w, h * .66f)
                lineTo(w, h); lineTo(0f, h); close()
            }
            drawPath(hills, Color(0xFF3A2A55))
            val front = androidx.compose.ui.graphics.Path().apply {
                moveTo(0f, h * .84f); cubicTo(w * .3f, h * .76f, w * .6f, h * .9f, w, h * .8f); lineTo(w, h); lineTo(0f, h); close()
            }
            drawPath(front, Color(0xFF241B3A))
        }
        BasicText(label, Modifier.padding(22.px), style = TextStyle(color = Color.White.copy(alpha = .6f), fontFamily = Figtree, fontSize = 20.tpx))
        content()
    }
}
