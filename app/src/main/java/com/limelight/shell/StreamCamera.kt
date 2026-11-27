package com.limelight.shell

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.key.*
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.dp
import com.limelight.R
import com.limelight.shell.ui.*

@Composable
internal fun StreamCamera(requester: FocusRequester, modifier: Modifier, onDown: () -> Boolean, capture: () -> Unit) {
    var focused by remember { mutableStateOf(false) }
    val colors = LocalDelosColors.current
    val reduced = LocalReducedMotion.current
    val scale by animateFloatAsState(if (focused) 1.04f else 1f,
        if (reduced) snap() else tween(Motion.FOCUS, easing = EaseSpring), label = "camera focus")
    val label = stringResource(R.string.stream_menu_screenshot)
    val ring = 4.px
    val gap = 8.px
    val side = maxOf(96.px, 48.dp)
    Box(modifier, contentAlignment = Alignment.TopEnd) {
        Box(Modifier.size(side).graphicsLayer {
            transformOrigin = TransformOrigin(1f, 0f)
            scaleX = scale
            scaleY = scale
        }.drawBehind {
            if (focused) drawCircle(colors.accent, radius = size.minDimension / 2 + gap.toPx() + ring.toPx() / 2,
                style = Stroke(ring.toPx()))
        }.focusRequester(requester).onFocusChanged { focused = it.isFocused }
            .onPreviewKeyEvent {
                when (it.key) {
                    Key.DirectionDown -> if (it.type == KeyEventType.KeyDown) onDown() else true
                    Key.DirectionLeft, Key.DirectionRight, Key.DirectionUp -> true
                    Key.ButtonA, Key.Enter, Key.NumPadEnter, Key.DirectionCenter -> {
                        if (it.type == KeyEventType.KeyUp && !it.nativeKeyEvent.isCanceled) capture()
                        true
                    }
                    else -> false
                }
            }.shadow(if (focused) 14.px else 20.px, CircleShape, clip = false)
            .clip(CircleShape).glass(Color(0xC7161C30))
            .background(Brush.linearGradient(listOf(Color(0x12FFFFFF), Color(0x02FFFFFF))))
            .border(1.px, Color(0x26FFFFFF), CircleShape)
            .semantics { contentDescription = label }
            .clickable(role = Role.Button, onClickLabel = stringResource(R.string.stream_help_take_screenshot), onClick = capture),
            contentAlignment = Alignment.Center) {
            Symbol(Symbols.STREAM_SCREENSHOT, 48.tpx, Color(0xFFEEF3FB), filled = focused)
        }
        if (focused) Row(Modifier.offset(y = side + 20.px), verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.px)) {
            GlyphButton(Glyph.A, 44)
            BasicText(label, style = TextStyle(color = Color(0xFFEEF3FB), fontFamily = Figtree, fontSize = 24.tpx))
        }
    }
}

@Composable
internal fun CameraShutter() {
    val top = 8.px
    Canvas(Modifier.fillMaxSize()) {
        val edge = top.toPx()
        drawRect(Color(0xB3FFFFFF), size = Size(size.width, edge))
        drawRect(Color(0x50FFFFFF), Offset(0f, edge), Size(edge, size.height * .3f - edge))
        drawRect(Color(0x50FFFFFF), Offset(size.width - edge, edge), Size(edge, size.height * .3f - edge))
    }
}
