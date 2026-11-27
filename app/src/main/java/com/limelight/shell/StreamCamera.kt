package com.limelight.shell

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.key.*
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import com.limelight.R
import com.limelight.shell.ui.*
import com.limelight.shell.StreamMenuMotion as M

/**
 * The stream menu's camera (docs/gui/STREAM_MENU.md §2, prototype screenshot-A): a round tile of the dock's glass, 96 px, at the
 * top right with the tile row's inset (109 px from the top and from the right). It enters and leaves with the dock's head on the
 * [enter] and [exit] clocks, takes the app's focus ring, glow, lift and press like every focusable element, and shows
 * "A Screenshot" under it while focused. Down returns to the tile it came from; left, right and up stay on it.
 */
@Composable
internal fun StreamCamera(
    requester: FocusRequester,
    enter: () -> Float,
    exit: () -> Float,
    modifier: Modifier,
    onDown: () -> Boolean,
    capture: () -> Unit,
) {
    val colors = LocalDelosColors.current
    val rm = LocalReducedMotion.current
    var focused by remember { mutableStateOf(false) }
    val focusFill by animateFloatAsState(if (focused) 1f else 0f, tween(Motion.FADE, easing = Motion.Ease), label = "cameraFill")
    val hint by animateFloatAsState(if (focused) 1f else 0f, tween(Motion.FADE, easing = Motion.Ease), label = "cameraHint")
    val label = stringResource(R.string.stream_menu_screenshot)
    val side = 96f
    val k = stageScale()
    Box(
        modifier.graphicsLayer {
            val p = Motion.progress(enter(), M.HEAD_DELAY, Motion.ENTER, EaseOut)
            alpha = p.coerceIn(0f, 1f) * (1f - Motion.linear(exit(), 0, Motion.EXIT))
            translationY = if (rm) 0f else -24f * (1f - p) * k
            compositingStrategy = CompositingStrategy.ModulateAlpha
        },
        contentAlignment = Alignment.TopEnd,
    ) {
        Box(
            Modifier
                .size(side.px)
                .focusRequester(requester)
                .onPreviewKeyEvent {
                    when (it.key) {
                        Key.DirectionDown -> if (it.type == KeyEventType.KeyDown) onDown() else true
                        Key.DirectionLeft, Key.DirectionRight, Key.DirectionUp -> true
                        else -> false
                    }
                }
                .focusEffects(capture, corner = side / 2f, focusScale = 1.04f, origin = TransformOrigin(1f, 0f),
                    onFocusChange = { focused = it })
                .semantics { contentDescription = label }
                .drawBehind {
                    val corner = CornerRadius(size.minDimension / 2f)
                    drawRoundRect(Color(0xFF05070F).copy(alpha = StreamMenuLayout.TILE_BACKING_ALPHA), cornerRadius = corner)
                    drawTileFill(size.minDimension / 2f)
                    drawTileFocusFill(size.minDimension / 2f, focusFill)
                },
            contentAlignment = Alignment.Center,
        ) {
            Symbol(Symbols.STREAM_SCREENSHOT, 48.tpx, colors.text, filled = focused, fadeMillis = Motion.FADE)
        }
        Row(
            Modifier.offset(y = (side + 20f).px).graphicsLayer { alpha = hint },
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.px),
        ) {
            GlyphButton(Glyph.A, 44)
            BasicText(label, style = TextStyle(color = colors.text, fontFamily = Figtree, fontWeight = FontWeight.Medium, fontSize = 24.tpx,
                shadow = menuTextShadow(k, 2f, 12f, .6f)))
        }
    }
}

/**
 * The shutter: a white flash over the whole screen, the menu included (the picture is already copied from the video surface,
 * so the flash is never in it): up to 70 % in 40 ms, then out in 220 ms. Not with reduced motion.
 */
@Composable
internal fun CameraFlash(shot: Int) {
    if (LocalReducedMotion.current) return
    val alpha = remember { Animatable(0f) }
    LaunchedEffect(shot) {
        if (shot == 0) return@LaunchedEffect
        alpha.snapTo(0f)
        alpha.animateTo(.7f, tween(40, easing = Motion.Ease))
        alpha.animateTo(0f, tween(220, easing = EaseOut))
    }
    if (alpha.value > 0f) Canvas(Modifier.fillMaxSize()) { drawRect(Color.White.copy(alpha = alpha.value)) }
}
