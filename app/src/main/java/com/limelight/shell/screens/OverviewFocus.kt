package com.limelight.shell.screens

import android.graphics.BlurMaskFilter
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.onGloballyPositioned
import com.limelight.shell.ui.FocusGap
import com.limelight.shell.ui.FocusLook
import com.limelight.shell.ui.FocusSpacing
import com.limelight.shell.ui.DelosColors
import com.limelight.shell.ui.Motion
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlin.math.min

/** How the gold ring sits around a focusable part of the overview: [pad] stage pixels outside it, its corner (999: a pill). */
internal data class RingStyle(val pad: Float = 6f, val corner: Float = 999f)

/**
 * The overview's groups of focusable parts and their gaps (docs/GUI.md, "Focus spacing"): the shared ring is a 4 px band [RingStyle.pad]
 * outside a part's scaled bounds ([FocusSpacing.overview]); the size is the longest part along the axis.
 */
internal object OverviewGaps {
    /** Play and App settings (`FocusRow`): 1.06, and Play's sweep band at 7..11 px outside the pill, inside its scaled layer. */
    val BUTTONS = FocusLook(scale = 1.06f, outset = 7f, band = 4f)
    const val BUTTONS_GAP = 20f

    /** Static labels inside a focusable group: use the same clear gap between wrapped rows. */
    const val TAG_GAP = 10f
    const val BADGE_GAP = 28f

    /** The summary (ring 14 px out, no growth) above the "Read the story" button and beside the facts card. */
    val ABOUT = FocusGap(FocusSpacing.overview(1f, 14f), size = 0f, gap = 44f)

    /** The screenshots and videos: 520 × 292 thumbnails at 1.06, the ring 6 px out. */
    const val MEDIA_W = 520f
    const val MEDIA_H = 292f
    const val MEDIA_SCALE = 1.06f
    const val MEDIA_PAD = 6f
    /** A thumbnail's picture is enlarged evenly by this much (16:9 kept) to leave a rim for its parallax; the viewer's flight matches it. */
    const val MEDIA_OVERSCAN = 1.28f
    val MEDIA = FocusGap(FocusSpacing.overview(MEDIA_SCALE, MEDIA_PAD), size = MEDIA_W, gap = 60f)

    /** Similar games: 260 × 390 covers at 1.06, the ring 6 px out. */
    const val SIMILAR_W = 260f
    const val SIMILAR_H = 390f
    const val SIMILAR_SCALE = 1.06f
    const val SIMILAR_PAD = 6f
    val SIMILAR = FocusGap(FocusSpacing.overview(SIMILAR_SCALE, SIMILAR_PAD), size = SIMILAR_W, gap = 44f)

    /** The links: 64 px pills at 1.05, the ring 6 px out, wrapping; labels up to about 400 px wide side by side. */
    const val LINK_H = 64f
    const val LINK_SCALE = 1.05f
    val LINKS_ACROSS = FocusGap(FocusSpacing.overview(LINK_SCALE, 6f), size = 400f, gap = 48f)
    val LINKS_DOWN = FocusGap(FocusSpacing.overview(LINK_SCALE, 6f), size = LINK_H, gap = LINKS_ACROSS.gap)
}

/** A focusable part of the overview: its focus requester, where it is, its row ([NavTarget.group]) and its section. */
internal class OverviewTarget(
    val id: String, val requester: FocusRequester, val group: String?, val locked: Boolean, val ring: RingStyle, val section: String,
    val visualMotion: () -> Float,
) {
    var coordinates by mutableStateOf<LayoutCoordinates?>(null)
}

/**
 * The overview's focusable parts and its one gold ring (the prototype's `RingSet`): the parts register here ([overviewTarget]), the
 * D-pad moves between them with [OverviewNavigator], and the ring glides to the focused one ([Motion.RING] with [Motion.Glide]) in the
 * page's own coordinates, so it scrolls with the page. Like [com.limelight.shell.ui.GlideRing] it is drawn at the target's bounds of
 * this very frame plus a carry that shrinks to nothing, so a carousel that scrolls under it carries it along.
 */
@Stable
internal class OverviewFocus(private val scope: CoroutineScope) {
    val targets = LinkedHashMap<String, OverviewTarget>()
    val sections = HashMap<String, LayoutCoordinates>()
    val navigator = OverviewNavigator<String>()

    /** The focused part's id; null while nothing in the overview has the focus. */
    var focused by mutableStateOf<String?>(null)
        private set

    /** The page's content (the ring's host): coordinates that scroll with the page. */
    var host: LayoutCoordinates? = null

    private var carry = Rect.Zero
    private var carryCorner = 0f
    private val progress = Animatable(1f)
    private var job: Job? = null
    private var shownRect: Rect? = null
    private var shownCorner = 0f

    /** The ring's opacity (0 while the overview enters or leaves, with touch, or with no focus). */
    val alpha = Animatable(0f)

    fun focus(id: String, glide: Boolean, k: Float) {
        val target = targets[id] ?: return
        val now = bounds(target, k)
        val from = shownRect
        focused = id
        job?.cancel()
        if (glide && from != null && now != null && alpha.value > 0f) {
            carry = Rect(from.left - now.first.left, from.top - now.first.top, from.right - now.first.right, from.bottom - now.first.bottom)
            carryCorner = shownCorner - now.second
            job = scope.launch {
                progress.snapTo(0f)
                progress.animateTo(1f, tween(Motion.RING, easing = Motion.Glide))
            }
        } else {
            carry = Rect.Zero
            carryCorner = 0f
            scope.launch { progress.snapTo(1f) }
        }
    }

    fun blur(id: String) {
        if (focused == id) focused = null
    }

    /** The target's ring rectangle in the host's coordinates (pixels) and its corner radius, or null while it is not laid out. */
    private fun bounds(target: OverviewTarget, k: Float): Pair<Rect, Float>? {
        val h = host ?: return null
        val c = target.coordinates ?: return null
        if (!h.isAttached || !c.isAttached) return null
        target.visualMotion() // Observe the same focus/press clock that transforms the body.
        val r = h.localBoundingBoxOf(c, clipBounds = false)
        val pad = target.ring.pad * k
        val rect = Rect(r.left - pad, r.top - pad, r.right + pad, r.bottom + pad)
        val corner = if (target.ring.corner >= 999f) min(rect.width, rect.height) / 2f else target.ring.corner * k + pad
        return rect to corner
    }

    /** The parts' rectangles on screen for [OverviewNavigator], relative to [root]. */
    fun navTargets(root: LayoutCoordinates): Map<String, NavTarget> = buildMap {
        for ((id, target) in targets) {
            val c = target.coordinates ?: continue
            if (!c.isAttached || !root.isAttached) continue
            put(id, NavTarget(root.localBoundingBoxOf(c, clipBounds = false), target.group, target.locked))
        }
    }

    /** Draws the ring (call from the host's draw phase): a 4 px accent band and its soft glow (`box-shadow: 0 0 0 4px, 0 0 34px -4px`). */
    fun DrawScope.drawRing(colors: DelosColors, k: Float) {
        val a = alpha.value
        val target = focused?.let { targets[it] }
        val now = target?.let { bounds(it, k) }
        if (now == null) {
            if (a <= 0f) return
        }
        val rest = 1f - progress.value
        val (rect, corner) = if (now != null) {
            val r = now.first
            Rect(r.left + carry.left * rest, r.top + carry.top * rest, r.right + carry.right * rest, r.bottom + carry.bottom * rest) to
                (now.second + carryCorner * rest)
        } else (shownRect ?: return) to shownCorner
        shownRect = rect
        shownCorner = corner
        if (a <= 0f) return
        val width = 4f * k
        val glowPaint = glowPaint ?: android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG).also { glowPaint = it }
        glowPaint.style = android.graphics.Paint.Style.STROKE
        glowPaint.strokeWidth = width * 2f
        glowPaint.maskFilter = blurFilter(30f * k)
        glowPaint.color = colors.glow.copy(alpha = colors.glow.alpha * a).toArgb()
        drawIntoCanvas {
            it.nativeCanvas.drawRoundRect(
                rect.left - width, rect.top - width, rect.right + width, rect.bottom + width,
                corner + width, corner + width, glowPaint,
            )
        }
        drawRoundRect(
            colors.accent.copy(alpha = a),
            topLeft = Offset(rect.left - width / 2f, rect.top - width / 2f),
            size = Size(rect.width + width, rect.height + width),
            cornerRadius = CornerRadius(corner + width / 2f),
            style = Stroke(width),
        )
    }

    private var glowPaint: android.graphics.Paint? = null
    private var blur: Pair<Float, BlurMaskFilter>? = null

    private fun blurFilter(radius: Float): BlurMaskFilter {
        blur?.let { if (it.first == radius) return it.second }
        return BlurMaskFilter(radius.coerceAtLeast(1f), BlurMaskFilter.Blur.NORMAL).also { blur = radius to it }
    }
}

internal val LocalOverviewFocus = staticCompositionLocalOf<OverviewFocus?> { null }

/** Registers inside the visual transform: navigation and the ring share the rendered target bounds. */
@Composable
internal fun Modifier.overviewTarget(
    id: String, requester: FocusRequester, section: String, group: String? = null, locked: Boolean = false, ring: RingStyle = RingStyle(),
    visualMotion: () -> Float = { 0f },
): Modifier {
    val focus = LocalOverviewFocus.current ?: return this
    val motion by androidx.compose.runtime.rememberUpdatedState(visualMotion)
    val target = remember(focus, id) { OverviewTarget(id, requester, group, locked, ring, section) { motion() } }
    DisposableEffect(focus, id) {
        focus.targets[id] = target
        onDispose {
            if (focus.targets[id] === target) focus.targets.remove(id)
            focus.blur(id)
        }
    }
    return onGloballyPositioned { target.coordinates = it }
}

/** Registers a section of the page, the unit the page scrolls to. */
@Composable
internal fun Modifier.overviewSection(id: String): Modifier {
    val focus = LocalOverviewFocus.current ?: return this
    DisposableEffect(focus, id) { onDispose { focus.sections.remove(id) } }
    return onGloballyPositioned { focus.sections[id] = it }
}
