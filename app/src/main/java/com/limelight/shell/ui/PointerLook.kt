package com.limelight.shell.ui

import android.view.ViewConfiguration
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.AnimationState
import androidx.compose.animation.core.animateTo
import androidx.compose.animation.core.tween
import androidx.compose.foundation.MutatePriority
import androidx.compose.foundation.gestures.ScrollableState
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.PointerType
import androidx.compose.ui.input.pointer.isPrimaryPressed
import androidx.compose.ui.input.pointer.isSecondaryPressed
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChanged
import androidx.compose.ui.platform.LocalContext
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlin.math.abs

/**
 * The light hover look of the interactive elements that are no focus items (docs/GUI.md, "Keyboard and pointer"): the hint bar's pills, the back
 * arrow, the tab pills, a submenu's back chip, small buttons. A mouse or a trackpad over one lays the `text` colour at [HOVER_ALPHA] over
 * it in its shape and lifts it to [HOVER_SCALE], in [HOVER_IN] ms and out in [HOVER_OUT] ms (the focus ring's times). Focus items have no
 * hover look: the pointer gives them the focus ([PointerFocus]).
 */
object PointerLook {
    const val HOVER_SCALE = 1.03f
    const val HOVER_ALPHA = .08f
    const val HOVER_IN = 140
    const val HOVER_OUT = 110

    /** The move cue of a focus the pointer moved plays at most this often (sweeping over a row of covers is no burst of clicks). */
    const val CUE_GAP_MS = 90L

    /** The scale of an element [hover] (0..1) over it and [press] (0..1) pressed: the press squeezes whatever scale it has. */
    fun scale(hover: Float, press: Float): Float = Motion.lerp(1f, HOVER_SCALE, hover) * Motion.lerp(1f, Motion.PRESS_SCALE, press)
}

/**
 * The pointer (a mouse, a trackpad) and the focus (docs/GUI.md, "Keyboard and pointer"). While the pointer is the last input ([used]) no focus
 * scroller follows the focus ([followsFocus]): a hover, a click or a right click focuses an item where it is, and the wheel or the
 * trackpad is the only one that scrolls. Any other input (a key, a pad, a touch) hands the scrolling back to the focus.
 *
 * A hover focuses only when the pointer itself moved ([moved]): content scrolling under a pointer at rest is no hover.
 */
object PointerFocus {
    /** False while the pointer is the last input: the focus scrollers ([MarginBringIntoView], the library row) stay where they are. */
    var followsFocus = true
        private set

    private var lastX = Float.NaN
    private var lastY = Float.NaN
    private var lastCue = Long.MIN_VALUE / 2

    /** The player used [kind] on purpose ([ControllerGlyphs.note]). */
    fun used(kind: InputKind) {
        followsFocus = kind != InputKind.POINTER
    }

    /** The scroll a focus scroller may make for [requested] pixels: none while the pointer owns the scrolling. */
    fun scrollFor(requested: Float): Float = if (followsFocus) requested else 0f

    /** A pointer event of [type] is a hover that may move the focus: a mouse or a trackpad, not a finger or a pen. */
    fun hovers(type: PointerType): Boolean = type != PointerType.Touch && type != PointerType.Stylus && type != PointerType.Eraser

    /** The pointer is over an item at [x], [y] of the window: true when it moved there, false when the content moved under it. */
    fun moved(x: Float, y: Float): Boolean {
        if (abs(x - lastX) < .5f && abs(y - lastY) < .5f) return false
        lastX = x
        lastY = y
        return true
    }

    /** Whether a focus the pointer moved at [nowMs] plays the move cue (at most every [PointerLook.CUE_GAP_MS]). */
    fun cue(nowMs: Long): Boolean {
        if (nowMs - lastCue < PointerLook.CUE_GAP_MS) return false
        lastCue = nowMs
        return true
    }
}

/**
 * Follows a finger or the primary mouse button on the element for its [PressPulse]: down squeezes it, a release holds the squeeze
 * [Motion.PRESS_HOLD] more, a scroll that takes the gesture over lets go at once. It only watches, the element's own taps still come.
 */
fun Modifier.pressInput(pulse: PressPulse): Modifier = pointerInput(pulse) {
    awaitEachGesture {
        val down = awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial)
        if (down.type == PointerType.Mouse && !currentEvent.buttons.isPrimaryPressed) return@awaitEachGesture
        pulse.down()
        var tapped = false
        try {
            while (true) {
                val change = awaitPointerEvent(PointerEventPass.Final).changes.firstOrNull { it.id == down.id } ?: break
                if (!change.pressed) {
                    tapped = true
                    break
                }
                if (change.isConsumed && change.positionChanged()) break
            }
        } finally {
            pulse.up(tapped)
        }
    }
}

/**
 * The press of every interactive element (docs/GUI.md, "Motion"): squeezes it to [Motion.PRESS_SCALE] of its scale while [pulse] is
 * pressed, from [origin]. A finger or a mouse button squeezes it from its down ([pressInput]); an activation by key fires [pulse].
 */
fun Modifier.pressLook(pulse: PressPulse, origin: TransformOrigin = TransformOrigin.Center): Modifier =
    pressInput(pulse).graphicsLayer {
        val s = PointerLook.scale(0f, pulse.amount)
        scaleX = s
        scaleY = s
        transformOrigin = origin
    }

/**
 * The hover look ([PointerLook]) of an element of [corner] stage pixels (999: round) that is no focus item. Only a mouse or a trackpad
 * shows it; a finger has no hover.
 */
fun Modifier.hoverLook(corner: Float): Modifier = composed {
    val colors = LocalDelosColors.current
    val stage = LocalStage.current
    val reduced = LocalReducedMotion.current
    val scope = rememberCoroutineScope()
    val hover = remember { Animatable(0f) }
    this
        .pointerInput(Unit) {
            awaitPointerEventScope {
                while (true) {
                    val event = awaitPointerEvent(PointerEventPass.Initial)
                    val change = event.changes.firstOrNull() ?: continue
                    val on = when (event.type) {
                        PointerEventType.Enter -> PointerFocus.hovers(change.type)
                        PointerEventType.Exit -> false
                        else -> continue
                    }
                    scope.launch {
                        val target = if (on) 1f else 0f
                        if (reduced) hover.snapTo(target)
                        else hover.animateTo(target, tween(if (on) PointerLook.HOVER_IN else PointerLook.HOVER_OUT, easing = Motion.Ease))
                    }
                }
            }
        }
        .graphicsLayer {
            val s = PointerLook.scale(hover.value, 0f)
            scaleX = s
            scaleY = s
        }
        .drawWithContent {
            drawContent()
            val h = hover.value
            if (h > 0f) {
                val r = minOf(stagePx(corner, stage), size.minDimension / 2f)
                drawRoundRect(colors.text.copy(alpha = PointerLook.HOVER_ALPHA * h), cornerRadius = CornerRadius(r))
            }
        }
}

/**
 * A tap target that is no focus item (a hint pill, the back arrow, a tab pill): the [hoverLook], the [pressLook] and the tap, whose
 * [onTap] runs once the squeeze has landed ([PressPulse.afterSqueeze]); [cue] plays at the tap itself. [key] restarts the tap handler.
 */
fun Modifier.pointerTap(corner: Float, key: Any? = Unit, cue: Cue? = null, onTap: () -> Unit): Modifier = composed {
    val pulse = rememberPressPulse()
    val feedback = if (cue != null) LocalFeedback.current else null
    val tap by rememberUpdatedState(onTap)
    this
        .hoverLook(corner)
        .pressLook(pulse)
        .pointerInput(key) {
            detectTapGestures {
                if (cue != null) feedback?.play(cue)
                pulse.fire()
                pulse.afterSqueeze { tap() }
            }
        }
}

/**
 * A right click or a trackpad's two-finger click that no item took ([focusEffects] takes the ones on an item): [onClick] (the shell
 * closes an open layer with it).
 */
fun Modifier.secondaryClickOutside(onClick: () -> Unit): Modifier = composed {
    val click by rememberUpdatedState(onClick)
    pointerInput(Unit) {
        awaitPointerEventScope {
            while (true) {
                val event = awaitPointerEvent(PointerEventPass.Final)
                if (event.type == PointerEventType.Press && event.buttons.isSecondaryPressed && !event.buttons.isPrimaryPressed &&
                    event.changes.none { it.isConsumed }
                ) click()
            }
        }
    }
}

/**
 * A horizontal row ([state]) that a mouse's plain wheel scrolls too: a wheel turn with no sideways part (the row's own scrolling only
 * takes the sideways one, a trackpad's swipe) glides the row forward or back by the system's wheel step.
 */
fun Modifier.wheelScrollsRow(state: ScrollableState): Modifier = composed {
    val context = LocalContext.current
    val step = remember(context) { ViewConfiguration.get(context).scaledVerticalScrollFactor }
    val scope = rememberCoroutineScope()
    val glide = remember { arrayOfNulls<Job>(1) }
    val pending = remember { FloatArray(1) }
    pointerInput(state) {
        awaitPointerEventScope {
            while (true) {
                val event = awaitPointerEvent()
                if (event.type != PointerEventType.Scroll) continue
                val change = event.changes.firstOrNull() ?: continue
                if (change.isConsumed || change.scrollDelta.x != 0f || change.scrollDelta.y == 0f) continue
                change.consume()
                pending[0] += change.scrollDelta.y * step
                glide[0]?.cancel()
                glide[0] = scope.launch {
                    state.scroll(MutatePriority.UserInput) {
                        var done = 0f
                        AnimationState(0f).animateTo(pending[0], tween(WHEEL_GLIDE_MS, easing = Motion.EaseOutCss)) {
                            val delta = value - done
                            val moved = scrollBy(delta)
                            done = value
                            pending[0] -= moved
                            if (abs(moved - delta) > .5f) {
                                pending[0] = 0f
                                cancelAnimation()
                            }
                        }
                        pending[0] = 0f
                    }
                }
            }
        }
    }
}

/** A wheel step's glide on the library row. */
private const val WHEEL_GLIDE_MS = 160
