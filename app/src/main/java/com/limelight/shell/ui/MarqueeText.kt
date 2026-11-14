package com.limelight.shell.ui

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.withFrameMillis
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.TextStyle
import kotlinx.coroutines.delay

private const val START_DELAY_MS = MarqueeMotion.START_DELAY_MS
private const val LOOP_DELAY_MS = MarqueeMotion.LOOP_DELAY_MS
private const val SPEED = MarqueeMotion.SPEED_PX_PER_S      // stage pixels per second
private const val GAP = 56f        // stage pixels between the end of the text and its repeat
private const val FADE_START = 28f // stage pixels of the left fade
private const val FADE_END = 36f   // stage pixels of the right fade

/**
 * One line of text that always stays readable, whatever its length (translations, large text scale). While it fits
 * nothing changes. When it is wider than the room it is given, its right end fades out, and while it is [active]
 * (usually: its row is focused) it scrolls around in a loop, with a pause before it starts and between laps.
 * The left end fades in only as the text moves away from its start (and out again as the lap ends), so a text at
 * rest always shows its first letter intact. Reduced motion keeps the text still. The caller limits the width with [modifier].
 */
@Composable
fun MarqueeText(text: String, style: TextStyle, modifier: Modifier = Modifier, active: Boolean = true) =
    MarqueeContent(text, modifier, active) { BasicText(text, style = style, maxLines = 1, softWrap = false) }

/** The motion of a [MarqueeContent] that goes there and back (`bounce`), as a pure function of time. */
object MarqueeMotion {
    /**
     * The offset (0 to [travel]) [elapsedMs] after the marquee started: still for [START_DELAY_MS], then to the end at [speed] (per second), a
     * pause of [LOOP_DELAY_MS], back to the start at the same speed, a pause, and again (the first pause only is [START_DELAY_MS]).
     */
    fun bounceOffset(elapsedMs: Long, travel: Float, speed: Float): Float {
        if (travel <= 0f || speed <= 0f || elapsedMs < START_DELAY_MS) return 0f
        val leg = travel / speed * 1000f
        val cycle = 2f * leg + 2f * LOOP_DELAY_MS
        val u = (elapsedMs - START_DELAY_MS) % cycle
        return when {
            u < leg -> travel * u / leg
            u < leg + LOOP_DELAY_MS -> travel
            u < 2f * leg + LOOP_DELAY_MS -> travel * (1f - (u - leg - LOOP_DELAY_MS) / leg)
            else -> 0f
        }
    }

    const val START_DELAY_MS = 700L
    const val LOOP_DELAY_MS = 900L
    const val SPEED_PX_PER_S = 56f
}

/**
 * [MarqueeText] for any single-line [content] (a stat block of several texts, a row of pills): the same fade, scroll and reduced-motion rules.
 * By default [content] is composed twice while it scrolls (the repeat) and loops one way; with [bounce] it goes to its end, pauses, goes back
 * to its start, pauses and repeats ([MarqueeMotion.bounceOffset], same speed and pauses), with no repeat: the left fade shows while content is
 * hidden at the left, the right one while it is hidden at the right. [key] restarts the scroll when the content changes.
 */
@Composable
fun MarqueeContent(key: Any?, modifier: Modifier = Modifier, active: Boolean = true, bounce: Boolean = false, content: @Composable () -> Unit) {
    val stage = LocalStage.current
    val rm = LocalReducedMotion.current
    val k = stage.dpPerPx * LocalDensity.current.density
    var boxWidth by remember { mutableIntStateOf(0) }
    var textWidth by remember { mutableIntStateOf(0) }
    val overflow = boxWidth > 0 && textWidth > boxWidth
    val scrolling = overflow && active && !rm
    // One lap is the content and the gap; at its end the repeat stands exactly where the content started
    val lap = textWidth + GAP * k
    val offset = remember { Animatable(0f) }
    val travel = (textWidth - boxWidth).coerceAtLeast(0).toFloat()
    LaunchedEffect(scrolling, key, lap, bounce, travel) {
        offset.snapTo(0f)
        if (!scrolling) return@LaunchedEffect
        if (bounce) {
            val begin = withFrameMillis { it }
            while (true) {
                val now = withFrameMillis { it }
                offset.snapTo(MarqueeMotion.bounceOffset(now - begin, travel, SPEED * k))
            }
        }
        delay(START_DELAY_MS)
        while (true) {
            offset.animateTo(lap, tween((lap / (SPEED * k) * 1000f).toInt().coerceAtLeast(1), easing = LinearEasing))
            offset.snapTo(0f)
            delay(LOOP_DELAY_MS)
        }
    }
    val ramp = FADE_START * k
    val leftFade = if (!scrolling) 0f else if (bounce) (offset.value / ramp).coerceIn(0f, 1f) else (minOf(offset.value, lap - offset.value) / ramp).coerceIn(0f, 1f)
    // A bouncing content has nothing hidden at the right once it has reached its end
    val rightFade = if (scrolling && bounce) ((travel - offset.value) / (FADE_END * k)).coerceIn(0f, 1f) else 1f
    Box(
        modifier
            .onSizeChanged { boxWidth = it.width }
            .clipToBounds()
            .then(if (overflow) Modifier.fadeMask(stage, vertical = false, startClear = 0f, startSolid = FADE_START * leftFade, endSolid = FADE_END * rightFade, endClear = 0f) else Modifier),
    ) {
        Row(
            Modifier
                .wrapContentWidth(Alignment.Start, unbounded = true)
                .graphicsLayer { translationX = -offset.value },
        ) {
            Box(Modifier.onSizeChanged { textWidth = it.width }) { content() }
            if (scrolling && !bounce) {
                Spacer(Modifier.width(GAP.px))
                content()
            }
        }
    }
}
