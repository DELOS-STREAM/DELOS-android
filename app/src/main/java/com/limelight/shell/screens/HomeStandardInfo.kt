package com.limelight.shell.screens

import com.limelight.shell.ui.Compact

/** Where the info block of the 16:9 home (and the wide and tall shapes, which share its numbers) starts and how tall its title box is. */
internal class StandardInfo(val top: Float, val titleBox: Float, val logoPad: Float = HomeStandard.LOGO_PAD_STANDARD)

/**
 * The 16:9 home (stage pixels, interface scale [scale] zooms the texts and the bars): the info block starts at most [INFO_TOP] from
 * the stage's top with a [TITLE_BOX] tall title box (the prototype's 200 less the logo's air, which shrank from 10 to 4 px each side), and the covers' row
 * at [ROW_TOP] (moving up with the interface scale). The streaming-time block is always there: [TITLE_BLOCK_GAP], the block of
 * [HomeCompact.BLOCK_HEIGHT] and [HomeCompact.BLOCK_GAP] stand between the title box and the label. To keep the pills [HomeCompact.GAP_CHIPS]
 * (the compact stages' minimum) clear of the focused cover's ring the info block moves up toward the status bar and, when that is not
 * enough, the title box shrinks. Line heights are estimates: 1.2 times the font size, as Figtree lays out.
 */
internal object HomeStandard {
    const val INFO_TOP = 150f
    const val TITLE_BOX = 188f
    const val ROW_TOP = 430f

    /** The row's padding above the covers, and how far the row moves up per unit of interface scale above 1. */
    const val ROW_PAD = 90f
    const val ROW_SHIFT = 92f

    /** A compact stage's logo has 10 px of air above and below in its title box. */
    const val LOGO_PAD = 10f

    /** On this layout the logo has 4 px of air (it keeps its 180 px) and [TITLE_BLOCK_GAP] separates the block explicitly. */
    const val LOGO_PAD_STANDARD = 4f
    const val TITLE_BLOCK_GAP = 12f

    /** The title box never shrinks below this (the logo keeps 112 px, the 84 px title its line; the largest interface scale 1.3 needs all of it). */
    const val MIN_TITLE_BOX = 120f

    private const val LINE = 1.2f
    private const val LABEL_FONT = 17f
    private const val CHIPS_UNDER_LABEL = 8f
    private const val CHIPS_FONT = 19f
    private const val CHIP_FRAME = 14f

    fun rowTop(scale: Float): Float = ROW_TOP - (scale - 1f) * ROW_SHIFT

    /** Where the focused cover's ring begins (its growth and the 10 px ring included). */
    fun ringTop(scale: Float): Float = rowTop(scale) + ROW_PAD - HomeCompact.ringReach(HomeCompact.COVER_HEIGHT)

    /** Where the pills end with the info block at [top] and the title box [titleBox] tall: the label above them is shown (the tallest case). */
    fun chipsBottom(scale: Float, top: Float, titleBox: Float): Float {
        val label = LABEL_FONT * LINE * scale
        val chips = CHIPS_FONT * LINE * scale + CHIP_FRAME
        return top + titleBox + TITLE_BLOCK_GAP + HomeCompact.blockHeight(scale) + HomeCompact.BLOCK_GAP + label + CHIPS_UNDER_LABEL + chips
    }

    /** The info block's top and title box; [safeTop] is the display cutout. */
    fun layout(scale: Float, safeTop: Float = 0f): StandardInfo {
        val clearance = ringTop(scale) - chipsBottom(scale, INFO_TOP, TITLE_BOX)
        val need = (HomeCompact.GAP_CHIPS - clearance).coerceAtLeast(0f)
        // The logo's top keeps the compact stages' air from the status bar
        val up = minOf(need, (INFO_TOP - (safeTop + Compact.STATUS_BAR * scale + HomeCompact.GAP_STATUS - LOGO_PAD_STANDARD)).coerceAtLeast(0f))
        val shrink = minOf(need - up, TITLE_BOX - MIN_TITLE_BOX)
        return StandardInfo(INFO_TOP - up, TITLE_BOX - shrink)
    }
}
