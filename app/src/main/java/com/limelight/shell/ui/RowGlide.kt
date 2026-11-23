package com.limelight.shell.ui

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.layout
import kotlin.math.roundToInt
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.graphics.ClipOp
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.asAndroidPath
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.inset
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.onGloballyPositioned
import com.limelight.shell.shadowRadius
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.min

/**
 * The one focus plate of a list: its optional tint, ring and shadow glide together instead of each
 * item fading its own layers. Like [GlideRing] it is told about the focused
 * row by [rowGlideTarget] and drawn behind the host's content by [RowGlideHost]; unlike it the plate is *not* animated
 * in window space. A list that scrolls while the plate glides would leave a window-space animation behind (and restart
 * it with every scrolled frame), so the plate is drawn at the focused row's bounds in this very frame plus a carry that
 * shrinks to nothing ([RowGlideMotion]): it travels with the row it heads for and at rest sits exactly on it. Everything
 * the draw phase needs is snapshot state read there (or plain arrays changed together with one), so a glide or scroll
 * frame redraws the plate without recomposing rows. The shadow's paint and clipping path are reused.
 */
@Stable
class RowGlide internal constructor(private val scope: CoroutineScope) {
    private var host: LayoutCoordinates? = null
    private var holder: Any? = null
    private var holderCoordinates: LayoutCoordinates? = null

    /** Reduced motion: the ring jumps from row to row. Kept up to date by the host. */
    internal var reduced = false

    /** The focused row's bounds in the host, pixels: left, top, right, bottom. */
    private val bounds = FloatArray(4)

    /** Whether [bounds] hold a row the ring has been on (it keeps them while it fades out, so a quick next focus glides from there). */
    private var placed = false

    /** What the ring still trails those bounds by (same order) until [progress] runs out. */
    private val carry = FloatArray(4)

    /** Bumped whenever [bounds] or [carry] change, so the draw phase redraws. */
    private var tick by mutableIntStateOf(0)

    /** The glide's linear progress, 0 when it starts and 1 once the ring has arrived. */
    private var progress by mutableFloatStateOf(1f)
    private val alpha = Animatable(0f)
    private var glideJob: Job? = null
    private var fadeJob: Job? = null

    private var glowClip: Path? = null
    private var glowPaint: android.graphics.Paint? = null
    private var shadowBlur = Float.NaN
    private var shadowOffset = Float.NaN
    private var ringStroke: Stroke? = null
    private var gapStroke: Stroke? = null
    private var shadowColor = 0

    internal fun attach(coordinates: LayoutCoordinates) {
        host = coordinates
        update()
    }

    /** The row [key], now at [coordinates], has the focus: the ring glides to it from where it is, or appears on it. */
    internal fun focus(key: Any, coordinates: LayoutCoordinates) {
        // Where the ring is drawn right now, before anything changes
        val glide = placed && RowGlideMotion.glides(alpha.value, reduced)
        val eased = Motion.Glide.transform(progress)
        val l = RowGlideMotion.edge(bounds[0], carry[0], eased)
        val t = RowGlideMotion.edge(bounds[1], carry[1], eased)
        val r = RowGlideMotion.edge(bounds[2], carry[2], eased)
        val b = RowGlideMotion.edge(bounds[3], carry[3], eased)
        holder = key
        holderCoordinates = coordinates
        fadeJob?.cancel()
        glideJob?.cancel()
        val found = update()
        if (glide && found) {
            carry[0] = RowGlideMotion.carry(l, bounds[0])
            carry[1] = RowGlideMotion.carry(t, bounds[1])
            carry[2] = RowGlideMotion.carry(r, bounds[2])
            carry[3] = RowGlideMotion.carry(b, bounds[3])
            progress = 0f
            glideJob = scope.launch {
                val start = withFrameNanos { it }
                while (progress < 1f) {
                    val now = withFrameNanos { it }
                    progress = ((now - start) / 1_000_000f / Motion.ROW_FOCUS).coerceIn(0f, 1f)
                }
            }
        } else {
            carry.fill(0f)
            progress = 1f
        }
        tick++
        fadeJob = scope.launch { alpha.animateTo(1f, tween(120, easing = Motion.Ease)) }
    }

    /** The focused row moved (scrolled, resized) or was laid out again: the ring follows. */
    internal fun moved(key: Any, coordinates: LayoutCoordinates) {
        if (holder != key) return
        holderCoordinates = coordinates
        update()
    }

    /** The row [key] lost the focus; the ring waits a moment for the next row (the focus passes through "nothing") before it fades. */
    internal fun blur(key: Any) {
        if (holder != key) return
        holder = null
        holderCoordinates = null
        fadeJob?.cancel()
        fadeJob = scope.launch {
            delay(70)
            alpha.animateTo(0f, tween(Motion.FADE, easing = Motion.Ease))
        }
    }

    /** The row [key] left the composition (another tab, a list that was replaced): the ring is gone with it, like a ring a row draws itself. */
    internal fun release(key: Any) {
        if (holder != key) return
        holder = null
        holderCoordinates = null
        glideJob?.cancel()
        fadeJob?.cancel()
        fadeJob = scope.launch { alpha.snapTo(0f) }
    }

    /** Re-reads the focused row's bounds in the host. Returns whether there were bounds to read. */
    private fun update(): Boolean {
        val h = host ?: return false
        val c = holderCoordinates ?: return false
        if (!h.isAttached || !c.isAttached) return false
        val origin = h.localPositionOf(c, Offset.Zero)
        val left = origin.x
        val top = origin.y
        val right = left + c.size.width
        val bottom = top + c.size.height
        placed = true
        if (left != bounds[0] || top != bounds[1] || right != bounds[2] || bottom != bounds[3]) {
            bounds[0] = left
            bounds[1] = top
            bounds[2] = right
            bounds[3] = bottom
            tick++
        }
        return true
    }

    /**
     * Draws the plate at the same moving bounds, [focusScale] times the focused row's size: under the rows the glow and the
     * [fill] (null when a frosted-glass plate carries it, see [RowGlideHost]), over that the [decoration] and the ring ([over]).
     */
    internal fun draw(
        drawScope: DrawScope, ring: FocusRing?, corner: Float, focusScale: Float,
        colors: DelosColors, stage: StageMetrics, fill: Color?, glow: FocusGlow?,
        decoration: (DrawScope.(Float) -> Unit)?, over: Boolean,
    ) {
        // Reading the tick is what redraws the ring when the row's bounds or the carry change
        if (tick == Int.MIN_VALUE) return
        val a = alpha.value
        if (a <= 0f) return
        val eased = Motion.Glide.transform(progress)
        val l = RowGlideMotion.edge(bounds[0], carry[0], eased)
        val t = RowGlideMotion.edge(bounds[1], carry[1], eased)
        val r = RowGlideMotion.edge(bounds[2], carry[2], eased)
        val b = RowGlideMotion.edge(bounds[3], carry[3], eased)
        if (r - l <= 0f || b - t <= 0f) return
        with(drawScope) {
            inset(
                RowGlideMotion.grownNear(l, r, focusScale), RowGlideMotion.grownNear(t, b, focusScale),
                size.width - RowGlideMotion.grownFar(l, r, focusScale), size.height - RowGlideMotion.grownFar(t, b, focusScale),
            ) {
                val radius = min(stagePx(corner, stage), this.size.minDimension / 2f)
                if (!over) {
                    if (glow != null) drawGlow(glow, radius, a, colors, stage, focusScale)
                    if (fill != null) drawRoundRect(fill.copy(alpha = fill.alpha * a), cornerRadius = CornerRadius(radius))
                    return@inset
                }
                decoration?.invoke(this, a)
                if (ring != null) {
                    val width = stagePx(ring.width, stage)
                    val gap = stagePx(ring.gap, stage)
                    val stroke = ringStroke?.takeIf { it.width == width } ?: Stroke(width).also { ringStroke = it }
                    val gapStyle = if (gap > 0f) {
                        gapStroke?.takeIf { it.width == gap } ?: Stroke(gap).also { gapStroke = it }
                    } else null
                    drawFocusRing(ring, radius, a, colors, stage, stroke, gapStyle)
                }
            }
        }
    }

    /** The plate's current rectangle in the host and its visibility, for the frosted-glass plate; null while hidden. */
    internal fun plate(focusScale: Float): Pair<androidx.compose.ui.geometry.Rect, Float>? {
        if (tick == Int.MIN_VALUE) return null
        val a = alpha.value
        if (a <= 0f) return null
        val eased = Motion.Glide.transform(progress)
        val l = RowGlideMotion.edge(bounds[0], carry[0], eased)
        val t = RowGlideMotion.edge(bounds[1], carry[1], eased)
        val r = RowGlideMotion.edge(bounds[2], carry[2], eased)
        val b = RowGlideMotion.edge(bounds[3], carry[3], eased)
        if (r - l <= 0f || b - t <= 0f) return null
        val rect = androidx.compose.ui.geometry.Rect(
            RowGlideMotion.grownNear(l, r, focusScale), RowGlideMotion.grownNear(t, b, focusScale),
            RowGlideMotion.grownFar(l, r, focusScale), RowGlideMotion.grownFar(t, b, focusScale),
        )
        return rect to a
    }

    /** The existing CSS-style shadow, clipped outside the plate so its translucent tint is not darkened. */
    private fun DrawScope.drawGlow(glow: FocusGlow, radius: Float, alpha: Float, colors: DelosColors, stage: StageMetrics, focusScale: Float) {
        val paint = glowPaint ?: android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply {
            color = android.graphics.Color.BLACK
            glowPaint = this
        }
        val clip = glowClip ?: Path().also { glowClip = it }
        val k = stagePx(1f, stage) * focusScale
        val blur = shadowRadius(glow.blur * k)
        val offset = glow.offsetY * k
        val color = colors.glow.copy(alpha = colors.glow.alpha * alpha).toArgb()
        if (blur != shadowBlur || offset != shadowOffset || color != shadowColor) {
            paint.setShadowLayer(blur, 0f, offset, color)
            shadowBlur = blur
            shadowOffset = offset
            shadowColor = color
        }
        val spread = (glow.spread * k).coerceAtLeast(-size.minDimension / 2f)
        val innerRadius = (radius + spread).coerceAtLeast(0f)
        clip.asAndroidPath().apply {
            rewind()
            addRoundRect(0f, 0f, size.width, size.height, radius, radius, android.graphics.Path.Direction.CW)
        }
        clipPath(clip, ClipOp.Difference) {
            drawIntoCanvas {
                it.nativeCanvas.drawRoundRect(
                    -spread, -spread, size.width + spread, size.height + spread,
                    innerRadius, innerRadius, paint,
                )
            }
        }
    }
}

/** The focus plate of the surrounding [RowGlideHost]; null outside one (a row then draws its own focus effects). */
val LocalRowGlide = staticCompositionLocalOf<RowGlide?> { null }

/**
 * One locally styled focus plate for a list or choice grid. [fill], [ring] and [glow] may each be
 * omitted; [decoration] draws context-specific accents inside the current moving bounds, receiving
 * the plate's visibility alpha. Settings keep the accepted default tint and glow. Targets register
 * through [rowGlideTarget] or `focusEffects(glide = true)` and draw no local focus plate.
 * Give the host the viewport's size, fade and clip modifiers. [focusScale] grows its bounds around
 * the target's center. First focus and reduced motion do not glide.
 */
@Composable
fun RowGlideHost(
    ring: FocusRing?,
    corner: Float,
    focusScale: Float,
    modifier: Modifier = Modifier,
    fill: Color? = LocalDelosColors.current.surface2,
    glow: FocusGlow? = FocusGlow(offsetY = 20f, blur = 50f, spread = -24f),
    decoration: (DrawScope.(alpha: Float) -> Unit)? = null,
    content: @Composable () -> Unit,
) {
    val colors = LocalDelosColors.current
    val stage = LocalStage.current
    val reduced = LocalReducedMotion.current
    val scope = rememberCoroutineScope()
    val state = remember { RowGlide(scope) }
    SideEffect { state.reduced = reduced }
    // Frosted glass under the focused row (owner, 2026-11-23): the plate's tint blurs the background behind it, like the top
    // bar, wherever the shell's background is there to blur; inside an overlay (no backdrop source) the tint is drawn as before.
    // The glass is a child placed at the plate's moving rectangle; the glow under it and the ring over it stay drawn.
    val glassy = fill != null && LocalBackdropHaze.current != null
    val radius = with(androidx.compose.ui.platform.LocalDensity.current) { corner.px.toPx() }
    CompositionLocalProvider(LocalRowGlide provides state) {
        Box(
            modifier
                .onGloballyPositioned { state.attach(it) }
                .drawBehind { state.draw(this, ring, corner, focusScale, colors, stage, if (glassy) null else fill, glow, decoration, over = false) },
        ) {
            if (glassy && fill != null) Box(
                Modifier.matchParentSize().layout { measurable, constraints ->
                    val plate = state.plate(focusScale)
                    layout(constraints.maxWidth, constraints.maxHeight) {
                        if (plate != null) {
                            val (rect, alpha) = plate
                            val placeable = measurable.measure(androidx.compose.ui.unit.Constraints.fixed(
                                rect.width.roundToInt().coerceAtLeast(1), rect.height.roundToInt().coerceAtLeast(1)))
                            placeable.placeWithLayer(rect.left.roundToInt(), rect.top.roundToInt()) { this.alpha = alpha }
                        }
                    }
                },
            ) {
                val shape = androidx.compose.foundation.shape.RoundedCornerShape(androidx.compose.foundation.shape.CornerSize(radius))
                Box(Modifier.fillMaxSize().clip(shape).glass(fill))
            }
            Box(Modifier.matchParentSize().drawBehind {
                state.draw(this, ring, corner, focusScale, colors, stage, null, null, decoration, over = true)
            })
            content()
        }
    }
}

/**
 * Marks a row of a [RowGlideHost]: while it is [focused] the plate glides to it and follows it as it scrolls. Put it before the
 * row's `focusEffects` (so its bounds are those of the layout, not of the focus scale). Does nothing outside a host.
 */
@Composable
fun Modifier.rowGlideTarget(focused: Boolean): Modifier {
    val ring = LocalRowGlide.current ?: return this
    val key = remember { Any() }
    val coordinates = remember { arrayOfNulls<LayoutCoordinates>(1) }
    LaunchedEffect(focused) {
        if (focused) coordinates[0]?.let { ring.focus(key, it) } else ring.blur(key)
    }
    DisposableEffect(ring) { onDispose { ring.release(key) } }
    return onGloballyPositioned {
        coordinates[0] = it
        if (focused) ring.moved(key, it)
    }
}
