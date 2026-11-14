package com.limelight.shell.screens

import com.limelight.shell.ui.Compact

/** Where the info block of the 16:9 home (and the wide and tall shapes, which share its numbers) starts and how tall its title box is. */
internal class StandardInfo(val top: Float, val titleBox: Float)

/**
 * The prototype's home layout (stage pixels, interface scale [scale] zooms the texts and the bars): the info block starts [INFO_TOP] from
 * the stage's top with a [TITLE_BOX] tall title box, and the covers' row at [ROW_TOP] (moving up with the interface scale). A game with
 * streaming time puts the block of [HomeCompact.BLOCK_HEIGHT] plus [HomeCompact.BLOCK_GAP] under the title box, which takes the room of the
 * chips' old padding and more. A game without time keeps the prototype's numbers exactly; with time the block moves up toward the
 * status bar and, when that is not enough, the title box shrinks, so that the pills still clear the focused cover's ring by
 * [HomeCompact.GAP_CHIPS] (the compact stages' minimum). Line heights are estimates: 1.2 times the font size, as Figtree lays out.
 */
internal object HomeStandard {
    const val INFO_TOP = 150f
    const val TITLE_BOX = 200f
    const val ROW_TOP = 430f

    /** The row's padding above the covers, and how far the row moves up per unit of interface scale above 1. */
    const val ROW_PAD = 90f
    const val ROW_SHIFT = 92f

    /** The box art or logo has 10 px of air above and below in the title box. */
    const val LOGO_PAD = 10f

    /** The title box never shrinks below this (the logo keeps 130 px, the 84 px title its line; the largest interface scale 1.3 needs all of it). */
    const val MIN_TITLE_BOX = 150f

    private const val LINE = 1.2f
    private const val LABEL_FONT = 17f
    private const val LABEL_PAD = 10f
    private const val CHIPS_UNDER_LABEL = 8f
    private const val CHIPS_FONT = 19f
    private const val CHIP_FRAME = 14f

    fun rowTop(scale: Float): Float = ROW_TOP - (scale - 1f) * ROW_SHIFT

    /** Where the focused cover's ring begins (its growth and the 10 px ring included). */
    fun ringTop(scale: Float): Float = rowTop(scale) + ROW_PAD - HomeCompact.ringReach(HomeCompact.COVER_HEIGHT)

    /** Where the pills end with the info block at [top] and the title box [titleBox] tall: the label above them is shown (the tallest case). */
    fun chipsBottom(scale: Float, top: Float, titleBox: Float, withBlock: Boolean): Float {
        val label = LABEL_FONT * LINE * scale
        val chips = CHIPS_FONT * LINE * scale + CHIP_FRAME
        val above = if (withBlock) HomeCompact.blockHeight(scale) + HomeCompact.BLOCK_GAP else LABEL_PAD
        return top + titleBox + above + label + CHIPS_UNDER_LABEL + chips
    }

    /** The info block's top and title box; [safeTop] is the display cutout. Without [withBlock] the prototype's own numbers. */
    fun layout(scale: Float, withBlock: Boolean, safeTop: Float = 0f): StandardInfo {
        if (!withBlock) return StandardInfo(INFO_TOP, TITLE_BOX)
        val clearance = ringTop(scale) - chipsBottom(scale, INFO_TOP, TITLE_BOX, withBlock = true)
        val need = (HomeCompact.GAP_CHIPS - clearance).coerceAtLeast(0f)
        // The logo's top keeps the compact stages' air from the status bar
        val up = minOf(need, (INFO_TOP - (safeTop + Compact.STATUS_BAR * scale + HomeCompact.GAP_STATUS - LOGO_PAD)).coerceAtLeast(0f))
        val shrink = minOf(need - up, TITLE_BOX - MIN_TITLE_BOX)
        return StandardInfo(INFO_TOP - up, TITLE_BOX - shrink)
    }
}
