package com.limelight.shell

import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.util.VelocityTracker
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.Job

/** The side menu while a touch drag owns it ([SideMenuSwipe]): the panel and the scrim show [progress] instead of their transitions. */
@Stable
internal class SideMenuDrag {
    /** From the moment a drag takes the menu until the menu has settled (and, after a close, has left the screen). */
    var engaged by mutableStateOf(false)

    /** 0 = closed, 1 = fully open; a little more while the open panel is pulled further. */
    var progress by mutableFloatStateOf(0f)

    /** Where the current drag began, so a drag that catches a settling panel continues from where it is. */
    var start = 0f

    /** The release animation; a new drag stops it. */
    var settle: Job? = null
}

/**
 * Watches every touch of the shell before its children do, and takes a drag that belongs to the side menu: one that starts at the
 * left edge while [canOpen], or any drag to the left while [isOpen]. Taps and drags that turn out to be something else (a scroll,
 * a drag the other way) are left alone; once the drag is taken, its moves no longer reach the children. [onMove] and [onEnd] get
 * the travel to the right since the start in pixels; [onEnd] also the release speed in pixels per second (right positive).
 */
internal fun Modifier.sideMenuSwipe(
    canOpen: () -> Boolean,
    isOpen: () -> Boolean,
    onStart: (opening: Boolean) -> Unit,
    onMove: (opening: Boolean, travel: Float) -> Unit,
    onEnd: (opening: Boolean, travel: Float, velocity: Float) -> Unit,
): Modifier = pointerInput(Unit) {
    awaitEachGesture {
        val down = awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial)
        val opening = when {
            isOpen() -> false
            canOpen() && SideMenuSwipe.startsAtEdge(down.position.x, SideMenuSwipe.EDGE_DP.dp.toPx()) -> true
            else -> return@awaitEachGesture
        }
        val slop = viewConfiguration.touchSlop
        val velocity = VelocityTracker()
        velocity.addPosition(down.uptimeMillis, down.position)
        var claimed = false
        var travel = 0f
        while (true) {
            val change = awaitPointerEvent(PointerEventPass.Initial).changes.firstOrNull { it.id == down.id } ?: break
            travel = change.position.x - down.position.x
            if (!claimed) {
                when (SideMenuSwipe.claims(opening, travel, change.position.y - down.position.y, slop)) {
                    true -> {
                        claimed = true
                        onStart(opening)
                    }
                    false -> return@awaitEachGesture
                    null -> if (change.pressed) continue else return@awaitEachGesture
                }
            }
            velocity.addPosition(change.uptimeMillis, change.position)
            change.consume()
            if (!change.pressed) {
                onEnd(opening, travel, velocity.calculateVelocity().x)
                return@awaitEachGesture
            }
            onMove(opening, travel)
        }
        // The pointer went away without a release (the gesture was cancelled): the menu settles where the drag left it
        if (claimed) onEnd(opening, travel, 0f)
    }
}
