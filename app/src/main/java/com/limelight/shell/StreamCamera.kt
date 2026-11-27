package com.limelight.shell

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.keyframes
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathMeasure
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.key.*
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInWindow
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import com.limelight.R
import com.limelight.shell.ui.*
import kotlinx.coroutines.delay
import com.limelight.shell.StreamMenuMotion as M

/**
 * The stream menu's camera (docs/gui/STREAM_MENU.md §2, prototype screenshot-B): a pill of the dock's glass with the camera and its
 * label, 96 px high, at the top right with the tile row's inset (109 px from the top and from the right). It enters and leaves with
 * the dock's head on the [enter] and [exit] clocks, takes the app's focus ring, glow, lift and press like every focusable element,
 * and shows the primary action's hint under it while focused, in the glyph of the input in use (A, tap, click, Return). Down returns
 * to the tile it came from; left, right and up stay on it.
 *
 * Capture (prototype screenshot-fx A, owner 2026-11-27): each [shot] pulses the pill (in, overshoot, settle) and [onCenter] tells
 * the effect ([ScreenshotEffect]) where the wave starts. Each [saved] turns the camera into a check drawn in the accent colour and the
 * label into "Saved" (the old word rises out blurred, the new one rises in), for about two seconds, then both return the same way.
 */
@Composable
internal fun StreamCamera(
    requester: FocusRequester,
    enter: () -> Float,
    exit: () -> Float,
    modifier: Modifier,
    shot: Int,
    saved: Int,
    onCenter: (Offset) -> Unit,
    onDown: () -> Boolean,
    capture: () -> Unit,
) {
    val colors = LocalDelosColors.current
    val rm = LocalReducedMotion.current
    var focused by remember { mutableStateOf(false) }
    val focusFill by animateFloatAsState(if (focused) 1f else 0f, tween(Motion.FADE, easing = Motion.Ease), label = "cameraFill")
    val hint by animateFloatAsState(if (focused) 1f else 0f, tween(Motion.FADE, easing = Motion.Ease), label = "cameraHint")
    val label = stringResource(R.string.stream_menu_screenshot)
    val savedLabel = stringResource(R.string.stream_menu_screenshot_saved)
    val side = 96f
    val k = stageScale()

    // The press pulse: down to .93, over to 1.07, back to 1 in 520 ms (prototype fxpulse)
    val pulse = remember { Animatable(1f) }
    LaunchedEffect(shot) {
        if (shot == 0 || rm) return@LaunchedEffect
        pulse.snapTo(1f)
        pulse.animateTo(1f, keyframes {
            durationMillis = 520
            .93f at 94 using EaseSpring
            1.07f at 286 using EaseSpring
            1f at 520 using EaseSpring
        })
    }
    // Saved: shown for about two seconds after the picture was written
    var showSaved by remember { mutableStateOf(false) }
    LaunchedEffect(saved) {
        if (saved == 0) return@LaunchedEffect
        showSaved = true
        delay(SAVED_MS)
        showSaved = false
    }
    val spec = { ms: Int, delayMs: Int -> if (rm) tween<Float>(0) else tween(ms, delayMs, easing = EaseSpring) }
    val camera by animateFloatAsState(if (showSaved) 0f else 1f, if (rm) tween(0) else tween(160), label = "cameraIcon")
    val cameraScale by animateFloatAsState(if (showSaved) .6f else 1f, spec(260, 0), label = "cameraScale")
    val check by animateFloatAsState(if (showSaved) 1f else 0f, if (rm) tween(0) else tween(160), label = "check")
    val checkScale by animateFloatAsState(if (showSaved) 1f else .6f, spec(320, 0), label = "checkScale")
    val checkDraw by animateFloatAsState(if (showSaved) 1f else 0f,
        if (rm) tween(0) else if (showSaved) tween(360, 120, easing = EaseOutQuad) else tween(160), label = "checkDraw")
    val wordMove by animateFloatAsState(if (showSaved) 1f else 0f, if (rm) tween(0) else tween(380, easing = WordEase), label = "wordMove")
    val wordFade by animateFloatAsState(if (showSaved) 1f else 0f, if (rm) tween(0) else tween(260, easing = EaseOutQuad), label = "wordFade")
    val nextMove by animateFloatAsState(if (showSaved) 1f else 0f,
        if (rm) tween(0) else tween(380, if (showSaved) 80 else 0, easing = WordEase), label = "nextMove")
    val nextFade by animateFloatAsState(if (showSaved) 1f else 0f,
        if (rm) tween(0) else tween(260, if (showSaved) 80 else 0, easing = EaseOutQuad), label = "nextFade")

    Box(
        modifier.graphicsLayer {
            val p = Motion.progress(enter(), M.HEAD_DELAY, Motion.ENTER, EaseOut)
            alpha = p.coerceIn(0f, 1f) * (1f - Motion.linear(exit(), 0, Motion.EXIT))
            translationY = if (rm) 0f else -24f * (1f - p) * k
            compositingStrategy = CompositingStrategy.ModulateAlpha
        },
        contentAlignment = Alignment.TopEnd,
    ) {
        // Prototype screenshot-B: a pill with the camera and its label always shown (owner, 2026-11-27: the bare circle came
        // out of nowhere); focus adds the primary action's hint under it
        Row(
            Modifier
                .height(side.px)
                .focusRequester(requester)
                .onPreviewKeyEvent {
                    when (it.key) {
                        Key.DirectionDown -> if (it.type == KeyEventType.KeyDown) onDown() else true
                        Key.DirectionLeft, Key.DirectionRight, Key.DirectionUp -> true
                        else -> false
                    }
                }
                .onGloballyPositioned { c ->
                    val at = c.positionInWindow()
                    // The wave starts at the camera symbol's centre: 30 px padding plus half the 44 px symbol
                    onCenter(Offset(at.x + (30f + 22f) * k, at.y + c.size.height / 2f))
                }
                .graphicsLayer {
                    scaleX = pulse.value
                    scaleY = pulse.value
                    transformOrigin = TransformOrigin(1f, 0f)
                }
                .focusEffects(capture, corner = side / 2f, focusScale = 1.04f, origin = TransformOrigin(1f, 0f),
                    onFocusChange = { focused = it })
                .semantics { contentDescription = if (showSaved) savedLabel else label }
                .drawBehind {
                    val corner = CornerRadius(size.height / 2f)
                    drawRoundRect(androidx.compose.ui.graphics.Color(0xFF05070F).copy(alpha = StreamMenuLayout.TILE_BACKING_ALPHA), cornerRadius = corner)
                    drawTileFill(size.height / 2f)
                    drawTileFocusFill(size.height / 2f, focusFill)
                }
                .padding(start = 30.px, end = 36.px),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(18.px),
        ) {
            // The camera and the check share one place
            Box(Modifier.size(44.px), contentAlignment = Alignment.Center) {
                Box(Modifier.graphicsLayer { alpha = camera; scaleX = cameraScale; scaleY = cameraScale }) {
                    Symbol(Symbols.STREAM_SCREENSHOT, 44.tpx, colors.text, filled = focused, fadeMillis = Motion.FADE)
                }
                if (check > 0f) Canvas(Modifier.size(44.px).graphicsLayer { alpha = check; scaleX = checkScale; scaleY = checkScale }) {
                    // The prototype's check (M10 25 L20 35 L38 14 in a 48 box), drawn along its length
                    val u = size.width / 48f
                    val full = Path().apply { moveTo(10f * u, 25f * u); lineTo(20f * u, 35f * u); lineTo(38f * u, 14f * u) }
                    val measure = PathMeasure().apply { setPath(full, false) }
                    val part = Path()
                    measure.getSegment(0f, measure.length * checkDraw, part, true)
                    drawPath(part, colors.accent, style = Stroke(width = 6f * u, cap = StrokeCap.Round, join = StrokeJoin.Round))
                }
            }
            // Both words in one cell: the pill keeps its width; the old word rises out blurred, the new one rises in
            val style = TextStyle(color = colors.text, fontFamily = Figtree, fontWeight = FontWeight.SemiBold, fontSize = 28.tpx,
                shadow = menuTextShadow(k, 2f, 12f, .6f))
            Box(contentAlignment = Alignment.CenterStart) {
                val rise = 40f * .7f * k
                BasicText(label, Modifier
                    .graphicsLayer { translationY = -rise * wordMove; alpha = 1f - wordFade }
                    .blur((4f * wordFade).px), style = style)
                BasicText(savedLabel, Modifier
                    .graphicsLayer { translationY = rise * (1f - nextMove); alpha = nextFade }
                    .blur((4f * (1f - nextFade)).px), style = style)
            }
        }
        val glyph = Glyph.A.forKind(ControllerGlyphs.kind)
        if (glyph != null) Row(
            Modifier.offset(y = (side + 20f).px).graphicsLayer { alpha = hint },
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.px),
        ) {
            GlyphButton(glyph, 44)
            BasicText(stringResource(R.string.stream_menu_hint_select), style = TextStyle(color = colors.text, fontFamily = Figtree,
                fontWeight = FontWeight.Medium, fontSize = 24.tpx, shadow = menuTextShadow(k, 2f, 12f, .6f)))
        }
    }
}

/** How long the camera shows "Saved" (the prototype's 2.2 s from the press, less the 0.26 s before it shows). */
private const val SAVED_MS = 1940L
/** The prototype's word motion, cubic-bezier(.22, 1, .36, 1). */
private val WordEase = CubicBezierEasing(.22f, 1f, .36f, 1f)
/** CSS ease-out. */
private val EaseOutQuad = CubicBezierEasing(0f, 0f, .58f, 1f)
