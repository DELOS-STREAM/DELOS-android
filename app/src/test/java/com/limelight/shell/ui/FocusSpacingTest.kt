package com.limelight.shell.ui

import com.limelight.shell.StreamMenuLayout
import com.limelight.shell.keyboard.KeyboardMetrics
import com.limelight.shell.screens.ArtGridLayout
import com.limelight.shell.screens.ArtworkLayout
import com.limelight.shell.screens.CREATE_PROFILE
import com.limelight.shell.screens.HomeCompact
import com.limelight.shell.screens.ChipFocus
import com.limelight.shell.screens.LibraryFilterLayout
import com.limelight.shell.screens.LetterPicker
import com.limelight.shell.screens.OverviewGaps
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The focus spacing rule (docs/GUI.md, "Focus spacing"): every group of focusable siblings is at least twice its focus look's reach
 * plus 8 px apart, so a focused element's ring and lift never touch its neighbour.
 */
class FocusSpacingTest {
    /** The groups with a fixed gap, by name. */
    private val groups = mapOf(
        "home covers" to HomeCompact.COVER,
        "top nav" to StatusBarLayout.NAV,
        "top nav, compact" to StatusBarLayout.NAV_COMPACT,
        "circles" to CircleRowLayout.GAP,
        "create profile" to CREATE_PROFILE,
        "dialog fields" to DIALOG_FIELDS,
        "dialog buttons" to DIALOG_BUTTONS,
        "stream result details" to RESULT_DETAILS,
        "problem card rows" to PROBLEM_ROWS,
        "settings rows" to SETTING_ROWS,
        "context menu rows" to ContextMenuLayout.ROWS,
        "row picker options" to PickerLayout.OPTIONS,
        "letter picker keys" to LetterPicker.KEYS,
        "letter picker keys, compact" to LetterPicker.COMPACT_KEYS,
        "search field to keys" to LetterPicker.FIELD_TO_KEYS,
        "search field to keys, compact" to LetterPicker.COMPACT_FIELD_TO_KEYS,
        "keys to pills" to LetterPicker.KEYS_TO_PILLS,
        "search circles, compact" to LetterPicker.COMPACT_CIRCLES,
        "keys to results, compact" to LetterPicker.COMPACT_KEYS_TO_RESULTS,
        "artwork sheet rows" to ArtworkLayout.SHEET_ROWS,
        "artwork sheet header" to ArtworkLayout.SHEET_HEADER,
        "artwork toolbar to strip" to ArtworkLayout.WIDE_STRIP_GAP,
        "overview about" to OverviewGaps.ABOUT,
        "overview media" to OverviewGaps.MEDIA,
        "overview similar" to OverviewGaps.SIMILAR,
        "overview links across" to OverviewGaps.LINKS_ACROSS,
        "overview links down" to OverviewGaps.LINKS_DOWN,
        "stream menu tiles" to StreamMenuLayout.TILES,
        "keyboard keys" to KeyboardMetrics.KEYS,
    )

    @Test
    fun everyGroupKeepsTwiceItsReachPlusTheClearSpaceBetweenItsElements() {
        for ((name, group) in groups) {
            assertTrue("$name: gap ${group.gap} < ${group.minGap}", group.gap >= group.minGap - EPSILON)
        }
    }

    @Test
    fun theReachIsTheGrowthAtTheEdgePlusTheRing() {
        // A 300 px cover at 1.055 with its 5 px band 5 px out, both inside the scaled layer: 8.25 + 10.55
        assertEquals(18.8f, FocusSpacing.TILE.reach(300f), EPSILON)
        assertEquals(45.6f, FocusSpacing.TILE.minGap(300f), EPSILON)
        // The overview's ring is drawn around the scaled bounds: 15.6 growth + 6 + 4
        assertEquals(25.6f, FocusSpacing.overview(1.06f, 6f).reach(520f), EPSILON)
        // No ring: only the growth
        assertEquals(3.36f, FocusLook(1.12f).reach(56f), EPSILON)
        // A ring inside the element reaches inward: a 4 px band 1 px in
        assertEquals(-1f, FocusLook(1f, outset = -5f, band = 4f).reach(60f), EPSILON)
    }

    @Test
    fun theComputedGapsFollowTheRuleAtEveryScale() {
        var s = .3f
        while (s <= 1f + EPSILON) {
            assertTrue("cover gap at $s", HomeCompact.coverGap(s) >= FocusSpacing.TILE.minGap(HomeCompact.TILE * s) - EPSILON)
            if (s >= .62f) {
                assertTrue("circle gap at $s", CircleRowLayout.tightGap(s) >= CircleRowLayout.LOOK.minGap(CircleRowLayout.BIG_CIRCLE * s) - EPSILON)
            }
            s += .01f
        }
        assertEquals(46f, HomeCompact.coverGap(1f), EPSILON)
        var tile = 120f
        while (tile <= 700f) {
            assertTrue("artwork gap at $tile", ArtGridLayout.gap(tile) >= ArtGridLayout.LOOK.minGap(tile) - EPSILON)
            assertTrue("pill toolbar gap at $tile", ArtworkLayout.toolbarGap(tile) >= FocusSpacing.PILL.minGap(tile) - EPSILON)
            tile += 10f
        }
    }

    @Test
    fun wrappedRowsKeepTheirFocusClearanceVertically() {
        val links = OverviewGaps.LINKS_DOWN
        assertTrue(links.gap >= links.look.minGap(OverviewGaps.LINK_H) - EPSILON)
        assertTrue(ArtworkLayout.SHEET_ROWS.gap >= ChipFocus.BASE_GAP)
        assertTrue(ArtworkLayout.SHEET_ROWS.gap >= ChipFocus.LOOK.minGap(ChipFocus.HEIGHT) - EPSILON)
        for (scale in listOf(.65f, .82f, 1f)) {
            val clearance = (LibraryFilterLayout.SECTION_GAP + LibraryFilterLayout.HEADING_HEIGHT + LibraryFilterLayout.HEADING_GAP) * scale + 2f * ChipFocus.ROW_ROOM
            assertTrue("library rows at $scale", clearance >= ChipFocus.BASE_GAP)
            assertTrue("library focus at $scale", clearance >= ChipFocus.LOOK.minGap(ChipFocus.HEIGHT) - EPSILON)
        }
        // Tags and age badges are labels inside a focusable group, not independently growing controls.
        assertTrue(OverviewGaps.TAG_GAP >= FocusSpacing.CLEAR)
        assertTrue(OverviewGaps.BADGE_GAP >= FocusSpacing.CLEAR)
    }

    private companion object {
        const val EPSILON = .01f
    }
}
