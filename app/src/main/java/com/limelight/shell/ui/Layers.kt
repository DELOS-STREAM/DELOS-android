package com.limelight.shell.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.offset
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.IntOffset
import com.limelight.shell.SideMenuSwipe
import androidx.compose.runtime.Stable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.layer.GraphicsLayer
import androidx.compose.ui.graphics.layer.drawLayer
import androidx.compose.ui.graphics.rememberGraphicsLayer
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.onGloballyPositioned
import kotlin.math.roundToInt
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.LocalBringIntoViewSpec
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.foundation.focusGroup
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import dev.chrisbanes.haze.HazeInput
import dev.chrisbanes.haze.HazeState
import dev.chrisbanes.haze.blur.HazeBlurStyle
import dev.chrisbanes.haze.blur.HazeColorEffect
import dev.chrisbanes.haze.blur.hazeBlur

/**
 * The animated background as a blur source, for glass that stays on (the focused settings row, the top
 * bar); overlays blur the whole screen through their own state instead.
 */
val LocalBackdropHaze = androidx.compose.runtime.staticCompositionLocalOf<HazeState?> { null }

/**
 * The animated background exactly as the shell draws it this frame (the hero at the user's blur, playing when animated, the
 * lights, the vignette), recorded once and drawn again by a page that covers the screen but continues the same background (the
 * game overview, [drawBackdropMirror]): the same render node drawn twice, no second hero, decoder or blur.
 */
@Stable
class BackdropMirror internal constructor(internal val layer: GraphicsLayer) {
    internal var coordinates: LayoutCoordinates? = null
}

val LocalBackdropMirror = staticCompositionLocalOf<BackdropMirror?> { null }

@Composable
fun rememberBackdropMirror(): BackdropMirror {
    val layer = rememberGraphicsLayer()
    return remember(layer) { BackdropMirror(layer) }
}

/** Records the background drawn by this element into [mirror] (and draws it as before). */
fun Modifier.backdropMirrorSource(mirror: BackdropMirror): Modifier = this
    .onGloballyPositioned { mirror.coordinates = it }
    .drawWithContent {
        mirror.layer.record { this@drawWithContent.drawContent() }
        drawLayer(mirror.layer)
    }

/**
 * Draws [mirror] where the shell's background lies on screen. [anchor] is an element that does not move with this one's own
 * transforms (a parallax may move and zoom the mirror like any picture drawn here).
 */
fun Modifier.drawBackdropMirror(mirror: BackdropMirror?, anchor: () -> LayoutCoordinates?): Modifier =
    if (mirror == null) this else drawBehind {
        val source = mirror.coordinates?.takeIf { it.isAttached }
        val here = anchor()?.takeIf { it.isAttached }
        val at = if (source != null && here != null) here.localPositionOf(source, Offset.Zero) else Offset.Zero
        translate(at.x, at.y) { drawLayer(mirror.layer) }
    }

/** Blur of the content behind an overlay, with a tint; the tint alone below Android 12. */
@Composable
fun Modifier.backdropBlur(haze: HazeState, radius: Float, tint: Color): Modifier {
    val r = radius.px
    return hazeBlur(
        HazeInput.Sources(haze),
        style = HazeBlurStyle {
            blurRadius(r)
            noiseFactor(0f)
            colorEffects(listOf(HazeColorEffect.tint(tint)))
            fallbackColorEffect(HazeColorEffect.tint(tint.copy(alpha = (tint.alpha + .35f).coerceAtMost(.92f))))
        },
    )
}

/**
 * Frosted glass over the animated background with [tint], like the top bar and the focused settings row;
 * just [tint] inside overlays, which turn the background source off ([LocalBackdropHaze] is null there).
 */
@Composable
fun Modifier.glass(tint: Color): Modifier =
    LocalBackdropHaze.current?.let { backdropBlur(it, 8f, tint) } ?: background(tint)

/**
 * Dims and slightly blurs the screen under a menu (`#scrim`); a tap outside closes the menu. The [light] scrim of the
 * context menu only dims (26 %, no blur): the tiles beside its card stay readable. While a touch drag moves the side menu,
 * [progress] is its opening progress: the scrim follows it instead of fading in and out.
 */
@Composable
fun Scrim(visible: Boolean, light: Boolean, haze: HazeState, progress: (() -> Float)? = null, onDismiss: () -> Unit) {
    val colors = LocalDelosColors.current
    // The scrim that fades out is the one that faded in
    var shownLight by remember { mutableStateOf(light) }
    if (visible) shownLight = light
    AnimatedVisibility(
        visible,
        enter = if (progress != null) EnterTransition.None else fadeIn(tween(Motion.SCRIM, easing = Motion.Ease)),
        exit = if (progress != null) ExitTransition.None else fadeOut(tween(Motion.EXIT, easing = Motion.Ease)),
    ) {
        Box(
            Modifier
                .fillMaxSize()
                .then(if (progress != null) Modifier.graphicsLayer { alpha = progress().coerceIn(0f, 1f) } else Modifier)
                .then(if (shownLight) Modifier.background(LightScrim) else Modifier.backdropBlur(haze, 6f, colors.scrim))
                .pointerInput(Unit) { detectTapGestures { onDismiss() } },
        )
    }
}

private val LightScrim = Color(4, 8, 16).copy(alpha = .26f)

/**
 * Keeps the focus inside an open layer: D-pad moves cannot leave it while [active]. Once the layer
 * closes (still animating out) the trap is released, so the focus can return to the screen.
 */
@Composable
internal fun Modifier.focusTrap(active: Boolean): Modifier {
    val current by rememberUpdatedState(active)
    return focusProperties { onExit = { if (current) cancelFocusChange() } }.focusGroup()
}

/**
 * Width of the side menu in stage pixels (the prototype's 620 plus room for the longest quick-setting row, Background
 * with Caustics, at the largest interface scale: the row's label and value need about 402 stage pixels at 130 % and
 * the panel leaves 680 - 248 = 432).
 */
private const val SIDE_MENU_WIDTH = 680f

/** The side menu's width in pixels of the window (what a touch drag of the menu measures its travel against). */
@Composable
fun sideMenuWidthPx(): Float = with(LocalDensity.current) { SIDE_MENU_WIDTH.px.toPx() }

/**
 * The side menu (left menu button), sliding in from the left: profile header and quick settings.
 * [header] and [content] are supplied by the shell; the first focusable of [content] should use
 * [firstFocus]. While a touch drag moves the menu, [drag] is its opening progress (0 closed, 1 open, a little more when
 * pulled further): the panel follows it instead of its enter and exit transitions.
 */
@Composable
fun SideMenuLayer(
    visible: Boolean,
    haze: HazeState,
    drag: (() -> Float)? = null,
    header: @Composable () -> Unit,
    content: @Composable ColumnScope.(firstFocus: FocusRequester) -> Unit,
) {
    val colors = LocalDelosColors.current
    val reduced = LocalReducedMotion.current
    AnimatedVisibility(
        visible,
        enter = if (drag != null) EnterTransition.None else sideEnter(reduced),
        exit = if (drag != null) ExitTransition.None else sideExit(reduced),
    ) {
        val first = remember { FocusRequester() }
        InitialFocus(first)
        // A compact stage's hints start left of the panel's right edge: the panel ends above the hint bar there, so nothing lies under the hints
        val stage = LocalStage.current
        val bottom = if (stage.compact) (Compact.HINT_BAR * stage.textScale + stage.safe.bottom).px else 0.px
        val width = SIDE_MENU_WIDTH.px
        // The panel reaches this far past the left edge, so a panel pulled beyond fully open shows more of itself, never a gap
        val overhang = (SIDE_MENU_WIDTH * SideMenuSwipe.RUBBER).px
        Box(
            Modifier
                .offset {
                    val p = drag?.invoke() ?: 1f
                    IntOffset(((p - 1f).coerceAtMost(SideMenuSwipe.RUBBER) * width.toPx() - overhang.toPx()).roundToInt(), 0)
                }
                .padding(bottom = bottom)
                .fillMaxHeight()
                .width(width + overhang),
        ) {
            Column(
                Modifier
                    .fillMaxSize()
                    .focusTrap(visible)
                    .backdropBlur(haze, 28f, colors.surface3)
                    .padding(start = overhang + 40.px, end = 40.px, top = 48.px, bottom = (if (stage.compact) 24 else 48).px),
                verticalArrangement = Arrangement.spacedBy(10.px),
            ) {
                EntranceScope {
                    header()
                    content(first)
                }
            }
            // border-right: 1px line
            Box(Modifier.align(Alignment.CenterEnd).fillMaxHeight().width(1.px).background(colors.line))
        }
    }
}

/**
 * The scrolling rows of the side menu. The scroll area and its fade layer are [GLOW_SIDE] / [GLOW_VERTICAL] larger than their place on each
 * side (overdraw, the content padded by the same), so the focus scale, ring and glow of the first, last and widest row are never cut by
 * the scroll container or the fade layer. The fade sits at the place's edges and only exists while there is more to scroll that way; the
 * focused row is scrolled clear of it.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun <K : Any> ColumnScope.SideMenuScroll(navigation: FocusedListNavigation<K>, content: @Composable ColumnScope.() -> Unit) {
    val stage = LocalStage.current
    val k = stage.dpPerPx * LocalDensity.current.density
    val sideScroll = rememberScrollState()
    val bringIntoView = rememberMarginBringIntoView(sideScroll, before = (40f + GLOW_VERTICAL) * k, after = (56f + GLOW_VERTICAL) * k, stagePx = k)
    val topFade by animateFloatAsState(if (sideScroll.canScrollBackward) 1f else 0f, tween(300), label = "sideTopFade")
    val bottomFade by animateFloatAsState(if (sideScroll.canScrollForward) 1f else 0f, tween(300), label = "sideBottomFade")
    CompositionLocalProvider(LocalBringIntoViewSpec provides bringIntoView) {
        SettingRowList(
            Modifier.weight(1f).overdraw(GLOW_SIDE.px, GLOW_VERTICAL.px).fadeMask(
                stage, vertical = true,
                startClear = GLOW_VERTICAL * topFade, startSolid = (GLOW_VERTICAL + 16f) * topFade,
                endSolid = (GLOW_VERTICAL + 40f) * bottomFade, endClear = GLOW_VERTICAL * bottomFade,
            ),
        ) {
            Column(
                Modifier
                    .fillMaxWidth()
                    .focusedListBoundary(navigation)
                    .verticalScroll(sideScroll)
                    .padding(horizontal = (14f + GLOW_SIDE).px, vertical = (18f + GLOW_VERTICAL).px),
                verticalArrangement = Arrangement.spacedBy(10.px),
                content = content,
            )
        }
    }
}

private const val GLOW_SIDE = 40f
private const val GLOW_VERTICAL = 56f

/** Profile header of the side menu (`.side-head`). */
@Composable
fun SideMenuHeader(avatar: @Composable BoxScope.() -> Unit, name: String, status: String) {
    val colors = LocalDelosColors.current
    Column(Modifier.staggerEntrance()) {
        Row(
            Modifier.padding(start = 12.px, end = 12.px, bottom = 24.px),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(20.px),
        ) {
            Box(Modifier.size(72.px).clip(CircleShape), contentAlignment = Alignment.Center, content = avatar)
            Column {
                BasicText(name, style = TextStyle(color = colors.text, fontFamily = Unbounded, fontWeight = FontWeight.SemiBold, fontSize = 28.tpx))
                BasicText(status, style = TextStyle(color = colors.text3, fontFamily = Figtree, fontSize = 20.tpx))
            }
        }
        Box(Modifier.fillMaxWidth().height(1.px).background(colors.line))
        Box(Modifier.height(14.px))
    }
}

/** Soft gradient used behind the side menu avatar placeholder. */
fun avatarBrush(hue: Float): Brush = Brush.linearGradient(listOf(hsl(hue, 80f, 60f), hsl(hue + 40f, 70f, 40f)))
