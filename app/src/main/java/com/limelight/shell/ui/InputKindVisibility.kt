package com.limelight.shell.ui

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import kotlin.math.roundToInt

/** When a piece of chrome is shown, by the kind of input in use ([InputKind], `InputKindRule` of DELOS for Apple). */
enum class InputKindRule {
    /** For fingers, pointers and keyboards: hidden while a controller is the input, whose buttons do the same (the loader's Cancel pill). */
    WITHOUT_CONTROLLER,

    /** For a controller only (the button glyph on a pill that a finger presses anyway). */
    WITH_CONTROLLER;

    fun shows(kind: InputKind): Boolean = when (this) {
        WITHOUT_CONTROLLER -> kind != InputKind.CONTROLLER
        WITH_CONTROLLER -> kind == InputKind.CONTROLLER
    }
}

/** Where a piece of chrome shown by the input kind is: [shown] now, [progress] of its animation (0 hidden, 1 shown), [reduced] motion. */
@Immutable
private class KindReveal(val shown: Boolean, val progress: Float, val reduced: Boolean)

/**
 * The animation towards [shown]: [Motion.ROW_IN] with [EaseOut] (`Motion.out(rowIn)` of DELOS for Apple), with reduced motion a plain
 * [Motion.FADE].
 */
@Composable
private fun rememberKindReveal(shown: Boolean): KindReveal {
    val reduced = LocalReducedMotion.current
    val progress by animateFloatAsState(
        if (shown) 1f else 0f,
        if (reduced) tween(Motion.FADE, easing = Motion.EaseOutCss) else tween(Motion.ROW_IN, easing = EaseOut),
        label = "inputKind",
    )
    return KindReveal(shown, progress, reduced)
}

/**
 * Shows [content] by [rule] and the kind of input in use, live (`inputKindVisibility` of DELOS for Apple): the one component for chrome
 * that depends on the input kind; [visible] false hides it whatever the kind (the screen has no use for it now). It fades in or out
 * with a small scale ([Motion.POP_FROM_SCALE]) and slide ([Motion.CHEVRON_BUMP] twice): from its start edge in a row, rising from below
 * in a column or on its own; reduced motion: a plain fade. The layout around it follows: along [collapse] it takes its size, and the
 * gaps [before] and [after] it, only while shown, so the neighbours glide (null: it keeps its place, for chrome placed on its own). A
 * hidden one takes no touches and is hidden from accessibility; once the fade is over it is not composed at all, so it cannot hold the
 * focus either.
 */
@Composable
fun InputKindVisibility(
    rule: InputKindRule, modifier: Modifier = Modifier, visible: Boolean = true, before: Dp = 0.dp, after: Dp = 0.dp,
    collapse: Orientation? = Orientation.Horizontal, content: @Composable () -> Unit,
) = KindRevealBox(rememberKindReveal(visible && rule.shows(ControllerGlyphs.kind)), modifier, before, after, collapse, content)

/** [InputKindVisibility] driven by a [reveal] the caller already has. */
@Composable
private fun KindRevealBox(
    reveal: KindReveal, modifier: Modifier = Modifier, before: Dp = 0.dp, after: Dp = 0.dp, collapse: Orientation? = Orientation.Horizontal,
    content: @Composable () -> Unit,
) {
    if (!reveal.shown && reveal.progress == 0f) return
    val p = reveal.progress
    val slide = (Motion.CHEVRON_BUMP * 2).px
    val sideways = collapse == Orientation.Horizontal
    Layout(
        content = {
            Box(
                Modifier.graphicsLayer {
                    alpha = p
                    if (!reveal.reduced) {
                        val scale = Motion.lerp(Motion.POP_FROM_SCALE, 1f, p)
                        scaleX = scale
                        scaleY = scale
                        if (sideways) {
                            transformOrigin = TransformOrigin(0f, .5f)
                            translationX = -slide.toPx() * (1f - p)
                        } else {
                            translationY = slide.toPx() * (1f - p)
                        }
                    }
                },
            ) { content() }
        },
        modifier = modifier
            .then(if (reveal.shown) Modifier else Modifier.clearAndSetSemantics {})
            // Hidden (fading out): every touch on it stops here, before its content sees it
            .pointerInput(reveal.shown) {
                if (reveal.shown) return@pointerInput
                awaitPointerEventScope {
                    while (true) awaitPointerEvent(PointerEventPass.Initial).changes.forEach { it.consume() }
                }
            },
    ) { measurables, constraints ->
        val child = measurables.first().measure(constraints.copy(minWidth = 0, minHeight = 0))
        val start = before.roundToPx()
        val end = after.roundToPx()
        val horizontal = collapse == Orientation.Horizontal
        val vertical = collapse == Orientation.Vertical
        val width = if (horizontal) ((child.width + start + end) * p).roundToInt() else child.width + if (vertical) 0 else start + end
        val height = if (vertical) ((child.height + start + end) * p).roundToInt() else child.height
        layout(width, height) {
            if (vertical) child.placeRelative(0, start) else child.placeRelative(start, 0)
        }
    }
}

/**
 * A button glyph on a pill a finger can press ([PillButton]), [before] it the pill's gap: shown for the kinds that have the button
 * ([Glyph.forKind]: the controller's glyph, a key's cap), else it gives way the same way as [InputKindVisibility].
 */
@Composable
fun PillGlyph(glyph: Glyph, size: Int, before: Dp) {
    val shown = glyph.forKind(ControllerGlyphs.kind)
    // The glyph shown last stays while it fades out
    val last = remember { arrayOfNulls<Glyph>(1) }
    if (shown != null) last[0] = shown
    val reveal = rememberKindReveal(shown != null)
    val drawn = last[0] ?: return
    KindRevealBox(reveal, before = before) { GlyphButton(drawn, size) }
}
