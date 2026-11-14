package com.limelight.shell.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.SubcomposeLayout
import androidx.compose.ui.unit.Constraints
import com.limelight.shell.data.Playtime
import com.limelight.shell.ui.LocalDelosColors
import com.limelight.shell.ui.LocalStage
import com.limelight.shell.ui.MarqueeContent
import com.limelight.shell.ui.px

/** What [InfoBar] does with the badges next to the block: they keep their natural width, or scroll in the room the block leaves. */
internal enum class BadgesFit {
    NATURAL, SCROLL;

    companion object {
        /** The badges [natural] wide stay as they are when they fit in [room] (the width beside the block and its gap), else they scroll. */
        fun pick(natural: Int, room: Int): BadgesFit = if (natural <= room) NATURAL else SCROLL
    }
}

/**
 * The info row under the title: the badges (the [facts], then a divider and the [tags]) and the streaming-time block at the right end,
 * right-aligned at the row's right edge and centred on the badges' line, in its full form. The badges are the flexible part: they never wrap
 * and never go under the block; when they do not fit in the room left of the block (with [HomeCompact.BLOCK_GAP_X] between) they
 * scroll in it with the edge fade of [MarqueeContent]. Only when the whole row is too narrow for that ([above], decided by
 * [HomeCompact.blockAbove] together with the layout's height) the block stands above the badges. If the block would still leave the
 * badges less than [HomeCompact.MIN_BADGES_WIDTH] (a longer text than the layout allows for), the block shortens ([PlaytimeFit]) as a safety net.
 * [height] is the row's height ([HomeCompact.infoHeight]).
 */
@Composable
internal fun InfoBar(
    playtime: Playtime,
    resumes: Int,
    above: Boolean,
    hasFacts: Boolean,
    hasTags: Boolean,
    tags: @Composable () -> Unit,
    modifier: Modifier = Modifier,
    facts: @Composable () -> Unit,
) {
    val scale = LocalStage.current.textScale
    val texts = rememberPlaytimeTexts(playtime, resumes)
    val height = HomeCompact.infoHeight(scale, above).px
    val blockHeight = HomeCompact.blockHeight(scale).px
    val gap = HomeCompact.BLOCK_GAP_X.px
    val minBadges = HomeCompact.MIN_BADGES_WIDTH.px
    val aboveGap = HomeCompact.BLOCK_ABOVE_GAP.px
    val hasBadges = hasFacts || hasTags
    val badges: @Composable () -> Unit = { Badges(hasFacts, hasTags, tags, facts) }
    SubcomposeLayout(modifier) { constraints ->
        val width = constraints.maxWidth
        val rowHeight = height.roundToPx()
        val natural = if (hasBadges) subcompose("badges", badges).first().measure(Constraints()) else null
        if (above) {
            val block = subcompose("block") { StreamingTimeBlock(playtime, resumes) }.first().measure(Constraints(maxWidth = width))
            val shown = natural?.let { placed ->
                if (BadgesFit.pick(placed.width, width) == BadgesFit.NATURAL) placed
                else subcompose("scroll") { MarqueeContent(Unit, Modifier, true, badges) }.first().measure(Constraints(minWidth = width, maxWidth = width))
            }
            layout(width, rowHeight) {
                block.place(0, 0)
                shown?.place(0, rowHeight - shown.height)
            }
        } else {
            val forms = listOf(PlaytimeFit.FULL, PlaytimeFit.STAT_ONLY, PlaytimeFit.SHORT).associateWith { fit ->
                subcompose(fit) { StatRow(texts, fit, blockHeight) }.first().measure(Constraints())
            }
            val gapPx = if (hasBadges) gap.roundToPx() else 0
            val leastBadges = if (hasBadges) minBadges.roundToPx() else 0
            // The block is full; it shortens only when even then the badges would keep less than their least room
            val fit = PlaytimeFit.pick(
                forms.getValue(PlaytimeFit.FULL).width, forms.getValue(PlaytimeFit.STAT_ONLY).width,
                forms.getValue(PlaytimeFit.SHORT).width, width - gapPx - leastBadges,
            ).let { if (it == PlaytimeFit.SCROLL) PlaytimeFit.SHORT else it }
            val block = forms.getValue(fit)
            val room = (width - block.width - gapPx).coerceAtLeast(0)
            val shown = natural?.let { placed ->
                if (BadgesFit.pick(placed.width, room) == BadgesFit.NATURAL) placed
                else subcompose("scroll") { MarqueeContent(Unit, Modifier, true, badges) }.first().measure(Constraints(minWidth = room, maxWidth = room))
            }
            layout(width, rowHeight) {
                // The block first, so that the badges (and their fade) are drawn over its scrim
                block.place(width - block.width, (rowHeight - block.height) / 2)
                shown?.place(0, (rowHeight - shown.height) / 2)
            }
        }
    }
}

/** The pills in one line: the facts, a thin divider and the tag pills, 12 px apart; the divider has 16 px each side. */
@Composable
private fun Badges(hasFacts: Boolean, hasTags: Boolean, tags: @Composable () -> Unit, facts: @Composable () -> Unit) {
    val colors = LocalDelosColors.current
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.px)) {
        facts()
        if (hasTags) {
            // Clearly visible but light: a short rounded bar in the dim text color
            if (hasFacts) Box(Modifier.padding(horizontal = 4.px).width(2.px).height(28.px).clip(CircleShape).background(colors.text3.copy(alpha = .55f)))
            Row(horizontalArrangement = Arrangement.spacedBy(12.px)) { tags() }
        }
    }
}
