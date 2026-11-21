package com.limelight.shell.ui

import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.gestures.animateScrollBy
import androidx.compose.foundation.gestures.scrollBy
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier

/**
 * The dashboard cover row's scrolling, shared by every horizontal row that behaves like it (the dashboard, the overview's carousel and
 * similar games): each focus step glides the focused item to the row's anchor, the start of its content padding, so the previous item
 * peeks in through the start fade; at the end the row stops by itself with the last item resting before the end padding.
 */
object AnchorRow {
    /** The prototype's library scroll: easeOutCubic over 200 ms. */
    val Glide = tween<Float>(200, easing = CubicBezierEasing(.33f, 1f, .68f, 1f))

    /** The start fade's solid end (stage pixels): where the previous item peeks out. */
    const val START_SOLID = 120f
    const val END_SOLID = 260f
    const val END_CLEAR = 20f

    sealed interface Step {
        /** The item is laid out: scroll by [delta] pixels to bring it to the anchor. */
        data class Glide(val delta: Float) : Step
        /** The item is not laid out: jump to it. */
        data object Jump : Step
        /** The item already rests at the anchor. */
        data object Stay : Step
    }

    /** How to bring item [index] to the anchor, given the laid-out items' offsets from it ([offsetOf]: null when not laid out). */
    fun step(index: Int, offsetOf: (Int) -> Int?): Step {
        val offset = offsetOf(index) ?: return Step.Jump
        return if (kotlin.math.abs(offset) < 1) Step.Stay else Step.Glide(offset.toFloat())
    }
}

/** Brings item [index] to the row's anchor ([AnchorRow]); with [reducedMotion] at once. */
suspend fun LazyListState.anchor(index: Int, reducedMotion: Boolean) {
    when (val step = AnchorRow.step(index) { i -> layoutInfo.visibleItemsInfo.firstOrNull { it.index == i }?.offset }) {
        AnchorRow.Step.Jump -> scrollToItem(index)
        AnchorRow.Step.Stay -> Unit
        is AnchorRow.Step.Glide -> if (reducedMotion) scrollBy(step.delta) else animateScrollBy(step.delta, AnchorRow.Glide)
    }
}

/**
 * The dashboard row's edge fades on [state]'s row: each end fades only while the row goes on beyond it, so the first item resting at the
 * anchor and the last one at the end are never dimmed.
 */
@Composable
fun Modifier.anchorRowFade(stage: StageMetrics, state: LazyListState, endSolid: Float = AnchorRow.END_SOLID, endClear: Float = AnchorRow.END_CLEAR): Modifier {
    val endFade by animateFloatAsState(if (state.canScrollForward) 1f else 0f, tween(300), label = "rowEndFade")
    val startFade by animateFloatAsState(if (state.canScrollBackward) 1f else 0f, tween(300), label = "rowStartFade")
    return fadeMask(stage, vertical = false, startClear = 0f, startSolid = AnchorRow.START_SOLID * startFade, endSolid = endSolid * endFade, endClear = endClear * endFade)
}
