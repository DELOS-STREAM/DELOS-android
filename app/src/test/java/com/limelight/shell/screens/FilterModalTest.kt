package com.limelight.shell.screens

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.limelight.R
import com.limelight.shell.data.GameGenre
import com.limelight.shell.data.GameMetadata
import com.limelight.shell.data.HdrFilter
import com.limelight.shell.data.LengthFilter
import com.limelight.shell.data.MetadataCandidate
import com.limelight.shell.data.MetadataFilters
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@Config(sdk = [33])
@RunWith(RobolectricTestRunner::class)
class FilterModalTest {
    private val res = ApplicationProvider.getApplicationContext<Context>().resources

    private fun game(vararg genres: Int) = GameMetadata(
        candidate = MetadataCandidate(1, "Game", 2020, genres = genres.map { GameGenre(it, "Genre $it") }),
    )

    @Test fun genreCountsCountGamesNotListings() {
        val counts = genreCounts(listOf(game(1, 2), game(2), game(2, 2), game(), null, GameMetadata()))
        assertEquals(mapOf(1 to 1, 2 to 3), counts)
    }

    @Test fun noGamesHaveNoCounts() {
        assertEquals(emptyMap<Int, Int>(), genreCounts(emptyList()))
        assertEquals(emptyMap<Int, Int>(), themeCounts(emptyList()))
    }

    @Test fun themeCountsCountGamesNotListingsAndKeepGenresApart() {
        fun themed(vararg themes: Int) = GameMetadata(
            candidate = MetadataCandidate(1, "Game", 2020, genres = listOf(GameGenre(7, "Genre 7")), themes = themes.map { GameGenre(it, "Theme $it") }),
        )
        val counts = themeCounts(listOf(themed(19, 21), themed(19), themed(19, 19), themed(), null, GameMetadata()))
        assertEquals(mapOf(19 to 3, 21 to 1), counts)
    }

    @Test fun resultLineShowsTheMatchesOfAnActiveFilterAndTheWholeLibraryOtherwise() {
        assertEquals("27 of 55 apps", filterResultLine(res, 27, 55, active = true))
        assertEquals("0 of 55 apps", filterResultLine(res, 0, 55, active = true))
        assertEquals("All 55 apps", filterResultLine(res, 55, 55, active = false))
        assertEquals("1 of 1 app", filterResultLine(res, 1, 1, active = true))
        assertEquals("All 1 app", filterResultLine(res, 1, 1, active = false))
    }

    @Test fun hintLabelShowsHowManyValuesNarrowTheLibrary() {
        assertEquals("Filter", filterHintLabel(res, MetadataFilters()))
        assertEquals("Filter • 1 active", filterHintLabel(res, MetadataFilters(hdr = setOf(HdrFilter.SDR))))
        assertEquals("Filter • 3 active", filterHintLabel(res, MetadataFilters(genres = setOf(2), hdr = setOf(HdrFilter.HDR), length = setOf(LengthFilter.OVER_60))))
        // Two genres, a theme and HDR
        assertEquals("Filter • 4 active", filterHintLabel(res, MetadataFilters(setOf(2, 5), setOf(19), setOf(HdrFilter.HDR))))
    }

    @Test fun theConfirmPillNamesTheMatchingApps() {
        assertEquals("Show 12 apps", filterShowLabel(res, 12))
        assertEquals("Show 1 app", filterShowLabel(res, 1))
        assertEquals("Show 0 apps", filterShowLabel(res, 0))
    }

    @Test fun theLabelsFollowTheRowsAndTheChipOrder() {
        // Ids IGDB does not have, so the chips show the names given
        val genres = listOf(GameGenre(9002, "Beta"), GameGenre(9001, "Alpha"))
        val filters = MetadataFilters(setOf(9001, 9002), emptySet(), setOf(HdrFilter.UNKNOWN, HdrFilter.HDR), setOf(LengthFilter.OVER_60, LengthFilter.UNDER_10))
        assertEquals(
            listOf("Beta", "Alpha", "Native HDR", "HDR/SDR unverified", "<10 h", "60+ h"),
            libraryFilterLabels(res, filters, genres, emptyList()),
        )
    }

    @Test fun theDraftAppliesOnlyOnConfirmAndClosingDropsIt() {
        var applied = MetadataFilters(genres = setOf(5))
        var applies = 0
        val draft = FilterDraft({ applied }) { applied = it; applies++ }
        draft.begin()
        assertEquals(applied, draft.value)
        draft.edit { it.toggleGenre(12).toggleHdr(HdrFilter.HDR) }
        // Choosing does not filter the library
        assertEquals(MetadataFilters(genres = setOf(5)), applied)
        // Closing (B) without confirming: the next opening starts from the applied filter
        draft.begin()
        assertEquals(MetadataFilters(genres = setOf(5)), draft.value)
        draft.edit { it.toggleGenre(12).toggleHdr(HdrFilter.HDR) }
        draft.confirm()
        assertEquals(MetadataFilters(genres = setOf(5, 12), hdr = setOf(HdrFilter.HDR)), applied)
        assertEquals(1, applies)
        // Reset clears the choices, which apply on confirm too; confirming nothing new writes nothing
        draft.reset()
        assertEquals(MetadataFilters(genres = setOf(5, 12), hdr = setOf(HdrFilter.HDR)), applied)
        draft.confirm()
        assertEquals(MetadataFilters(), applied)
        draft.confirm()
        assertEquals(2, applies)
    }

    @Test fun theUnverifiedHdrOptionUsesTheBadgeLabelAndEveryOptionHasItsOwnLabel() {
        assertEquals("HDR/SDR unverified", res.getString(hdrFilterLabel(HdrFilter.UNKNOWN)))
        assertEquals(R.string.metadata_hdr_unverified, hdrFilterLabel(HdrFilter.UNKNOWN))
        assertEquals(HdrFilter.entries.size, HdrFilter.entries.map { res.getString(hdrFilterLabel(it)) }.toSet().size)
        assertEquals(LengthFilter.entries.size, LengthFilter.entries.map { res.getString(lengthFilterLabel(it)) }.toSet().size)
        assertEquals(listOf("<10 h", "10–30 h", "30–60 h", "60+ h", "Unknown"), LengthFilter.entries.map { res.getString(lengthFilterLabel(it)) })
    }

    @Test fun theCompletionTimeUsesTheWebsitesRounding() {
        assertEquals("45 Mins", completionText(res, 2700))
        assertEquals("3½ Hours", completionText(res, 12772))
        assertEquals("22½ Hours", completionText(res, 80_713))
        assertEquals("66 Hours", completionText(res, 237_341))
        assertEquals("130 Hours", completionText(res, 469_657))
        assertEquals("1 Hour", completionText(res, 3600))
    }
}
