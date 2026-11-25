package com.limelight.shell.ui

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.em
import com.limelight.binding.audio.VolumeRoute
import kotlin.math.roundToInt

/** The approved volume-C-final.html: 248 stage pixels, no route caption or focus/input target.
 * Its circular glass background is supplied by the compositor-backed host window.
 */
@Composable
internal fun VolumeDial(level: Int, initialLevel: Float, route: VolumeRoute, modifier: Modifier = Modifier) {
    // This overlay stays dark over either video or shell; use the approved prototype's foregrounds in light mode too.
    val text = Color(0xFFEEF3FB)
    val text2 = Color(0xFFB9C5D8)
    val accent = Color(0xFFF6B73C)
    val reduced = LocalReducedMotion.current
    val stage = LocalStage.current
    val target = level.coerceIn(0, 15) / 15f
    val fill = remember { Animatable(initialLevel.coerceIn(0f, 15f) / 15f) }
    LaunchedEffect(target, reduced) {
        if (reduced) fill.snapTo(target)
        else fill.animateTo(target, tween(Motion.FOCUS, easing = EaseSpring))
    }
    Box(modifier.size(248.px), contentAlignment = Alignment.Center) {
        Canvas(Modifier.fillMaxSize().drawBehind {
            val border = stagePx(1f, stage)
            drawCircle(Color(0xFFB9C5D8).copy(alpha = .18f), radius = size.minDimension / 2f - border / 2,
                style = Stroke(border))
        }.padding(4.px)) {
            val radius = stagePx(91f, stage)
            val stroke = Stroke(stagePx(8f, stage), cap = StrokeCap.Round)
            drawCircle(Color(0xFFEEF3FB).copy(alpha = .125f), radius = radius, style = stroke)
            val arc = fill.value.coerceIn(0f, 1f)
            if (level > 0 && arc > 0f) drawArc(
                accent, -90f, 360f * arc, false,
                topLeft = Offset(center.x - radius, center.y - radius),
                size = Size(radius * 2f, radius * 2f), style = stroke,
            )
        }
        Column(Modifier.padding(34.px), horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(8.px)) {
            Box(Modifier.size(46.px, 42.px), contentAlignment = Alignment.Center) {
                val icon = when {
                    level == 0 -> Symbols.PLAYER_MUTED
                    route == VolumeRoute.SPEAKER -> Symbols.SPEAKER
                    else -> Symbols.VOLUME_HEADPHONES
                }
                Symbol(icon, 42.tpx, if (level == 0) text2 else text)
                if (level > 0 && route == VolumeRoute.BLUETOOTH) {
                    Box(Modifier.align(Alignment.BottomEnd).offset(x = 10.px, y = 3.px)
                        .size(24.px, 28.px).drawBehind {
                            val spread = stagePx(3f, stage)
                            drawRoundRect(Color(0xFF172335), Offset(-spread, -spread),
                                Size(size.width + spread * 2, size.height + spread * 2),
                                CornerRadius(stagePx(11f, stage)))
                        }.background(Color(0xFF172335), RoundedCornerShape(8.px)),
                        contentAlignment = Alignment.Center) {
                        Symbol(Symbols.VOLUME_BLUETOOTH, 22.tpx, accent)
                    }
                }
            }
            Row(verticalAlignment = Alignment.Bottom) {
                BasicText((target * 100f).roundToInt().toString(), Modifier.alignByBaseline(), style = TextStyle(
                    color = text, fontFamily = Unbounded, fontWeight = FontWeight.Medium,
                    fontSize = 43.tpx, lineHeight = 49.45.tpx, letterSpacing = (-.05f).em,
                ))
                BasicText("%", Modifier.padding(start = 5.px).alignByBaseline(), style = TextStyle(
                    color = text2, fontFamily = Figtree, fontWeight = FontWeight.Medium, fontSize = 22.tpx,
                ))
            }
        }
    }
}
