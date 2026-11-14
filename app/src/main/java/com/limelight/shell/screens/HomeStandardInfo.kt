package com.limelight.shell.screens

import com.limelight.shell.ui.Compact

/**
 * Where the 16:9 home's title box and info row stand, stage pixels. The title box is centred between the status bar and the info row:
 * [titleTop] is [titleGap] under the bar, and the row starts [titleGap] under the box.
 */
internal class StandardInfo(val titleTop: Float, val titleBox: Float, val titleGap: Float, val infoHeight: Float)

/**
 * The 16:9 home (and the wide and tall shapes, which share its numbers), interface scale [scale] zooming the texts and the bars: the covers'
 * row at [ROW_TOP] (moving up with the interface scale), the info row (the badges, scrolling, and the streaming-time block at its right
 * end, [HomeCompact.INFO_ROW_HEIGHT] x scale high) [INFO_RING_GAP] above the focused cover's ring, and the title box of [TITLE_BOX]
 * (a logo of [LOGO_HEIGHT] with 10 px of air) centred in the room between the status bar and the row. There is no label.
 */
internal object HomeStandard {
    const val ROW_TOP = 430f

    /** The row's padding above the covers, and how far the row moves up per unit of interface scale above 1. */
    const val ROW_PAD = 90f
    const val ROW_SHIFT = 92f

    /** The title box: the prototype's 200 with a 180 high logo and 10 px of air above and below it. */
    const val TITLE_BOX = 200f
    const val LOGO_PAD = 10f
    const val LOGO_HEIGHT = TITLE_BOX - 2f * LOGO_PAD

    /** The air between the info row and the focused cover's ring (never under [HomeCompact.GAP_CHIPS]). */
    const val INFO_RING_GAP = 44f

    fun rowTop(scale: Float): Float = ROW_TOP - (scale - 1f) * ROW_SHIFT

    /** Where the focused cover's ring begins (its growth and the 10 px ring included). */
    fun ringTop(scale: Float): Float = rowTop(scale) + ROW_PAD - HomeCompact.ringReach(HomeCompact.COVER_HEIGHT)

    /** Where the info row begins. */
    fun infoTop(scale: Float): Float = ringTop(scale) - INFO_RING_GAP - HomeCompact.blockHeight(scale)

    /** The layout at [scale]; [safeTop] is the display cutout. */
    fun layout(scale: Float, safeTop: Float = 0f): StandardInfo {
        val statusBottom = safeTop + Compact.STATUS_BAR * scale
        val height = HomeCompact.blockHeight(scale)
        val room = infoTop(scale) - statusBottom
        // The box keeps its size unless the room is smaller than it and the two least gaps
        val box = minOf(TITLE_BOX, room - 2f * HomeCompact.GAP_STATUS).coerceAtLeast(0f)
        val gap = (room - box) / 2f
        return StandardInfo(statusBottom + gap, box, gap, height)
    }
}
