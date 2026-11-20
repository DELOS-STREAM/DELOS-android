package com.limelight.shell.ui

import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.withFrameNanos
import kotlinx.coroutines.launch
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.focusable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.ColorMatrix
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.isPrimaryPressed
import androidx.compose.ui.input.pointer.isSecondaryPressed
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.graphics.shadow.Shadow
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalGraphicsContext
import androidx.compose.ui.unit.DpOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import com.limelight.shell.ContextMenu
import com.limelight.shell.LocalContextMenuOpener
import com.limelight.shell.LocalFocusTracker

/**
 * saturate(.86) brightness(.92): unfocused covers are slightly muted. Applied as the image's color
 * filter (cheap, drawn with the image) rather than as a layer effect on the whole tile.
 */
val IdleCoverFilter: ColorFilter = ColorFilter.colorMatrix(
    ColorMatrix().apply {
        setToSaturation(.86f)
        timesAssign(ColorMatrix(floatArrayOf(.92f, 0f, 0f, 0f, 0f, 0f, .92f, 0f, 0f, 0f, 0f, 0f, .92f, 0f, 0f, 0f, 0f, 0f, 1f, 0f)))
    }
)

/**
 * The covers behind an open context menu (the prototype's `.menu-open .gt:not(.sel)`: saturate(.6) brightness(.5)):
 * [IdleCoverFilter] when [open] is 0, the dimmed look at 1.
 */
fun menuDimCoverFilter(open: Float): ColorFilter {
    if (open <= 0f) return IdleCoverFilter
    val saturation = .86f + (.6f - .86f) * open
    val brightness = .92f + (.5f - .92f) * open
    return ColorFilter.colorMatrix(
        ColorMatrix().apply {
            setToSaturation(saturation)
            timesAssign(ColorMatrix(floatArrayOf(brightness, 0f, 0f, 0f, 0f, 0f, brightness, 0f, 0f, 0f, 0f, 0f, brightness, 0f, 0f, 0f, 0f, 0f, 1f, 0f)))
        }
    )
}

/**
 * The focus ring: an accent band of [width] stage pixels, [gap] pixels outside the element (the gap
 * is filled with the background color). Tiles use 5 / 5, list rows 3 / 0.
 */
@Immutable
data class FocusRing(val width: Float = 5f, val gap: Float = 5f)

/** The accent glow under a focused element: CSS box-shadow 0 [offsetY] [blur] [spread], stage pixels. */
@Immutable
data class FocusGlow(val offsetY: Float = 30f, val blur: Float = 80f, val spread: Float = -18f)

/**
 * The focus language of the prototype (`.f`, `.tile.is-focus`, `.row.is-focus`, `.sweep`): a spring
 * scale, a ring, an accent glow and optionally a sweep of light. Makes the element focusable (always,
 * also in touch mode); A, Enter, D-pad center and taps activate it. The focused element registers with
 * the [com.limelight.shell.FocusTracker], with its [contextMenu] and whether it is [adjustable] with
 * left/right.
 *
 * Every activation drops its focus lift and squeezes it ([pressLook]: pressed, it stands at [Motion.PRESS_SCALE] of its size at rest,
 * the glow blurred out to [Motion.PRESS_GLOW]); a tap
 * or a click squeezes from the finger's down and acts once the squeeze has landed. The pointer focuses it by moving over it and opens
 * its Options with a right click, both without scrolling it into view ([PointerFocus], docs/GUI.md, "Keyboard and pointer").
 *
 * Sizes are in stage pixels (see [px]).
 */
fun Modifier.focusEffects(
    onClick: () -> Unit,
    corner: Float,
    focusScale: Float = 1.055f,
    ring: FocusRing? = FocusRing(),
    glow: FocusGlow? = FocusGlow(),
    sweep: Boolean = false,
    /** Where the scale grows from: the wide first cover grows to the left, so it never covers its neighbour. */
    origin: TransformOrigin = TransformOrigin.Center,
    clickCue: Cue? = Cue.SELECT,
    contextMenu: ContextMenu? = null,
    adjustable: Boolean = false,
    controllerAdjust: ((direction: Int, repeatCount: Int) -> Unit)? = null,
    onFocusChange: ((Boolean) -> Unit)? = null,
    /**
     * Whether the element LOOKS focused: it has the focus, or its context menu is open ([com.limelight.shell.FocusTracker.hold]).
     * For what a caller draws itself (a cover's colors, a label, a pulse); [onFocusChange] stays the focus itself (sounds, hero, info).
     */
    onLookChange: ((Boolean) -> Unit)? = null,
    /** How long the focus spring of the scale takes ([Motion.FOCUS]). */
    focusMillis: Int = Motion.FOCUS,
    /** False for a focus target that does nothing when activated (a text panel that scrolls): no press. */
    press: Boolean = true,
    /** The element's press when its caller draws a focus lift of its own (the stream menu's tile): it drops that lift while pressed. */
    pressPulse: PressPulse? = null,
    /** Share the surrounding list's moving plate instead of drawing a separate ring and glow. */
    glide: Boolean = false,
    /** The element's focus requester when its caller needs it too (a part of the element that takes its own taps); otherwise its own. */
    focusRequester: FocusRequester? = null,
    /**
     * Touch only (issue #92): what a tap does instead of the A button's activation. Called after the tap has requested the focus,
     * with whether the element had the focus before the tap; returns whether the tap still activates like A. Keys and the
     * gamepad never reach it.
     */
    onTouchTap: ((wasFocused: Boolean) -> Boolean)? = null,
    /** What Options (the right menu button, Start) does while the element has the focus, instead of opening [contextMenu]; null: the menu. */
    onOptions: (() -> Unit)? = null,
): Modifier = composed {
    val colors = LocalDelosColors.current
    val stage = LocalStage.current
    val feedback = LocalFeedback.current
    val tracker = LocalFocusTracker.current
    val plate = if (glide) LocalRowGlide.current else null
    var focused by remember { mutableStateOf(false) }
    val ownRequester = remember { FocusRequester() }
    val requester = focusRequester ?: ownRequester
    // While its context menu is open the focus is in the menu, but the element keeps its focus look (ring, scale, glow, plate)
    val look = focused || tracker.holds(requester)
    val currentLookChange by rememberUpdatedState(onLookChange)
    LaunchedEffect(look) { currentLookChange?.invoke(look) }
    // A press drops the focus lift and pressLook below squeezes the rest: pressed, the element stands at Motion.PRESS_SCALE of its size at rest
    val ownPulse = rememberPressPulse()
    val pulse = pressPulse ?: ownPulse
    val scale by animateFocusScale(look, press && pulse.pressed, focusScale, focusMillis = if (plate != null) Motion.ROW_FOCUS else focusMillis)
    // Hosted rows never draw an independent ring or glow.
    val ringAlpha by animateFloatAsState(
        if (look) 1f else 0f, tween(if (look) 140 else 110, easing = EaseOut), label = "focusRing",
    )
    val k = stage.dpPerPx * LocalDensity.current.density
    val shape = RoundedCornerShape(corner.px)
    // The glow of a rounded box: CSS box-shadow 0 offsetY blur spread in the glow color (a round element and a row on a plate draw theirs differently)
    val shadows = LocalGraphicsContext.current.shadowContext
    val glowPainter = if (plate != null || glow == null || corner >= 500f) null else {
        val shadow = Shadow(radius = glow.blur.px, color = colors.glow, spread = glow.spread.px, offset = DpOffset(0.dp, glow.offsetY.px))
        remember(shadows, shape, shadow) { shadows.createDropShadowPainter(shape, shadow) }
    }
    // The glow of a press: the same shadow blurred Motion.PRESS_GLOW times as much, cross-faded in by the squeeze (never re-blurred per frame)
    val pressGlowPainter = if (glowPainter == null || glow == null) null else {
        val shadow = Shadow(radius = (glow.blur * Motion.PRESS_GLOW).px, color = colors.glow, spread = glow.spread.px, offset = DpOffset(0.dp, glow.offsetY.px))
        remember(shadows, shape, shadow) { shadows.createDropShadowPainter(shape, shadow) }
    }
    val sweepPos = remember { Animatable(0f) }
    LaunchedEffect(look) {
        if (look && sweep) {
            sweepPos.snapTo(0f)
            sweepPos.animateTo(1f, tween(1250, delayMillis = 80, easing = EaseOut))
        }
    }
    val interaction = remember { MutableInteractionSource() }
    val position = remember { arrayOfNulls<LayoutCoordinates>(1) }
    // A key or the pad activates at once; a tap or a click squeezes first and acts once the squeeze has landed
    val activate = { pointer: Boolean ->
        clickCue?.let(feedback::play)
        pulse.fire()
        if (pointer) pulse.afterSqueeze(onClick) else onClick()
    }
    val currentActivate by rememberUpdatedState(activate)
    val currentAdjust by rememberUpdatedState(controllerAdjust)
    val currentTouchTap by rememberUpdatedState(onTouchTap)
    val currentOptions by rememberUpdatedState(onOptions)
    val currentOpener by rememberUpdatedState(LocalContextMenuOpener.current)
    val currentMenu by rememberUpdatedState(contextMenu)
    val menuScope = rememberCoroutineScope()

    /** The pointer moved the focus here: where the element is (no focus scroller follows the pointer, PointerFocus), with a rarer cue. */
    fun pointerFocus() {
        if (focused) return
        if (!PointerFocus.cue(android.os.SystemClock.uptimeMillis())) feedback.silenceNextFocus()
        runCatching { requester.requestFocus() }
    }

    /** A right click or a two-finger click: the focus comes here and the element's Options run (its context menu), as Start does. */
    fun secondaryClick() {
        pointerFocus()
        currentOptions?.let { return it() }
        val menu = currentMenu ?: return
        currentOpener?.invoke(menu, position[0]?.takeIf { it.isAttached }?.boundsInRoot())
    }

    // The animated values (scale, glow and ring alpha) are only read while drawing, never while composing:
    // a focus change would otherwise rebuild both affected elements on every frame of the animation
    this
        .then(if (plate != null) Modifier.rowGlideTarget(look) else Modifier)
        // The focused element is drawn above its neighbours, so its ring and scale are never covered
        .zIndex(if (look) 1f else 0f)
        .then(if (press) Modifier.pressLook(pulse, origin) else Modifier)
        .graphicsLayer {
            scaleX = scale
            scaleY = scale
            transformOrigin = origin
        }
        .then(
            if (plate != null || glow == null) Modifier
            // A round element draws its glow itself, centered on it: the blurred shadow of dropShadow is cut off at the element's square
            else if (corner >= 500f) Modifier.drawBehind {
                val a = ringAlpha
                if (a > 0f) {
                    val inner = (size.minDimension / 2f + glow.spread * k).coerceAtLeast(1f)
                    // A press blurs the glow out to Motion.PRESS_GLOW times its reach
                    val outer = inner + glow.blur * k * Motion.lerp(1f, Motion.PRESS_GLOW, pulse.amount)
                    val c = colors.glow
                    drawCircle(
                        Brush.radialGradient(
                            0f to c.copy(alpha = c.alpha * a),
                            inner / outer to c.copy(alpha = c.alpha * a),
                            (inner / outer + 1f) / 2f to c.copy(alpha = c.alpha * a * .32f),
                            1f to Color.Transparent,
                            center = Offset(size.width / 2f, size.height / 2f), radius = outer,
                        ),
                        radius = outer, center = Offset(size.width / 2f, size.height / 2f),
                    )
                }
            }
            // The blurred shadow is rendered once per shape and size (the shadow context shares it between elements) and only
            // its opacity follows the focus: a shadow whose own alpha is animated is blurred again, in software, on every frame
            else if (glowPainter != null) Modifier.drawBehind {
                val a = ringAlpha
                val p = pulse.amount
                if (a > 0f) {
                    if (p < 1f) with(glowPainter) { draw(size, alpha = a * (1f - p)) }
                    if (p > 0f && pressGlowPainter != null) with(pressGlowPainter) { draw(size, alpha = a * p) }
                }
            } else Modifier
        )
        .drawWithContent {
            drawContent()
            val r = min(stagePx(corner, stage), size.minDimension / 2f)
            if (sweep && look) drawSweep(sweepPos.value, r)
            if (plate == null && ring != null && ringAlpha > 0f) drawFocusRing(ring, r, ringAlpha, colors, stage)
        }
        .onGloballyPositioned {
            position[0] = it
            if (focused) tracker.moved(requester, it)
        }
        .onFocusChanged {
            if (it.isFocused != focused) {
                focused = it.isFocused
                if (focused) {
                    feedback.focusMoved()
                    tracker.focused(
                        requester,
                        position[0],
                        contextMenu,
                        adjustable,
                        activate = { currentActivate(false) },
                        adjust = if (controllerAdjust == null) null else { direction, repeatCount ->
                            currentAdjust?.invoke(direction, repeatCount)
                        },
                        options = if (onOptions == null) null else { { currentOptions?.invoke() } },
                    )
                }
                onFocusChange?.invoke(focused)
            }
        }
        .onKeyEvent {
            // Handled on both edges so Android does not synthesize a fallback key (A → D-pad center)
            if (it.key !in ACTIVATE_KEYS) return@onKeyEvent false
            if (it.type == KeyEventType.KeyUp) activate(false)
            true
        }
        // Always focusable, also in touch mode: `clickable` would only be focusable after a key press,
        // but a console UI always shows the focus (and the game background that goes with it)
        .focusRequester(requester)
        .focusable(interactionSource = interaction)
        // The pointer (docs/GUI.md, "Keyboard and pointer"): moving over the element focuses it where it is, a right click (a trackpad's two-finger
        // click) also opens its Options. Watched before the taps, so a secondary click is taken here and never activates
        .pointerInput(requester) {
            awaitPointerEventScope {
                while (true) {
                    val event = awaitPointerEvent(PointerEventPass.Initial)
                    val change = event.changes.firstOrNull() ?: continue
                    when (event.type) {
                        PointerEventType.Enter, PointerEventType.Move -> if (!change.pressed && PointerFocus.hovers(change.type)) {
                            val at = position[0]?.takeIf { it.isAttached }?.localToRoot(change.position) ?: continue
                            if (PointerFocus.moved(at.x, at.y)) pointerFocus()
                        }
                        PointerEventType.Press -> if (event.buttons.isSecondaryPressed && !event.buttons.isPrimaryPressed) {
                            change.consume()
                            secondaryClick()
                        }
                    }
                }
            }
        }
        .pointerInput(requester) {
            detectTapGestures(onLongPress = {
                // Touch: a long press opens the element's context menu, as Start does (the tap's own handling is not reached after it)
                val open = currentOpener
                val menu = currentMenu
                if (menu != null && open != null) {
                    val hadFocus = focused
                    requester.requestFocus()
                    fun bounds() = position[0]?.takeIf { it.isAttached }?.boundsInRoot()
                    if (hadFocus) {
                        open(menu, bounds())
                    } else {
                        // The focus scrolls the element into view first (the library row glides the cover to its place): the menu opens
                        // where the element comes to rest, not where it was pressed (at most 0.9 s, 4 still frames in a row)
                        menuScope.launch {
                            var last = bounds()
                            var still = 0
                            val end = System.nanoTime() + 900_000_000L
                            while (still < 4 && System.nanoTime() < end) {
                                withFrameNanos { }
                                val now = bounds()
                                still = if (now == last) still + 1 else 0
                                last = now
                            }
                            open(menu, bounds())
                        }
                    }
                }
            }, onTap = {
                val wasFocused = focused
                requester.requestFocus()
                // The latest lambdas: this gesture handler outlives the composition it was created in
                if (currentTouchTap?.invoke(wasFocused) != false) currentActivate(true)
            })
        }
        .semantics {
            role = Role.Button
            onClick { activate(false); true }
        }
}

/** A on the gamepad, Enter, numpad Enter and D-pad center activate the focused element. */
private val ACTIVATE_KEYS = setOf(Key.ButtonA, Key.Enter, Key.NumPadEnter, Key.DirectionCenter)

private fun min(a: Float, b: Float) = if (a < b) a else b

/** A rounded stroke [offset] outside the element's bounds (box-shadow 0 0 0 spread). */
private fun DrawScope.drawRing(color: Color, radius: Float, offset: Float, width: Float, stroke: Stroke? = null) {
    drawRoundRect(
        color = color,
        topLeft = Offset(-offset, -offset),
        size = Size(size.width + offset * 2f, size.height + offset * 2f),
        cornerRadius = CornerRadius(radius + offset),
        style = stroke ?: Stroke(width),
    )
}

/** The focus ring (`box-shadow: 0 0 0 gap bg, 0 0 0 gap+width accent`) around an element with corner [radius]. */
fun DrawScope.drawFocusRing(
    ring: FocusRing, radius: Float, alpha: Float, colors: DelosColors, stage: StageMetrics,
    stroke: Stroke? = null, gapStroke: Stroke? = null,
) {
    val width = stagePx(ring.width, stage)
    val gap = stagePx(ring.gap, stage)
    drawRing(colors.accent.copy(alpha = alpha), radius, gap + width / 2f, width, stroke)
    if (gap > 0f) drawRing(colors.bg.copy(alpha = alpha), radius, gap / 2f, gap, gapStroke)
}

/**
 * linear-gradient(115deg, transparent 30%, white .28 48%, transparent 66%) at background-size 260%,
 * moving from background-position 130% to -40%.
 */
private fun DrawScope.drawSweep(progress: Float, radius: Float) {
    val imageWidth = size.width * 2.6f
    val position = 1.3f - 1.7f * progress
    val left = (size.width - imageWidth) * position
    val brush = cssLinearGradient(
        115f, Size(imageWidth, size.height),
        .3f to Color.White.copy(alpha = 0f), .48f to Color.White.copy(alpha = .28f), .66f to Color.White.copy(alpha = 0f),
        origin = Offset(left, 0f),
    )
    val clip = Path().apply { addRoundRect(RoundRect(0f, 0f, size.width, size.height, CornerRadius(radius))) }
    clipPath(clip) { drawRect(brush) }
}
