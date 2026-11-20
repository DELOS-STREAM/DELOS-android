package com.limelight.shell.data

import com.limelight.nvstream.http.NvApp
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class MetadataFiltersTest {
    private val game = App(1, "Game", false, false, NvApp())

    private fun metadata(hdr: NativeHdr = NativeHdr.UNKNOWN, seconds: Long? = null, genre: Int = 1, themes: IntArray = intArrayOf()) = GameMetadata(
        candidate = MetadataCandidate(
            10, "Game", 2020, listOf(20L), listOf(GameGenre(genre, "Genre")),
            themes = themes.map { GameGenre(it, "Theme $it") },
        ),
        hdr = HdrEvidence(hdr),
        completion = seconds?.let { CompletionTime(30, "Game", it, "https://howlongtobeat.com/game/30") },
    )

    @Test fun confirmedSdrDoesNotIncludeUnmatchedOrUnknownGames() {
        val filter = MetadataFilters(hdr = setOf(HdrFilter.SDR))
        assertTrue(filter.matches(game, metadata(NativeHdr.SDR)))
        assertFalse(filter.matches(game, metadata(NativeHdr.HDR)))
        assertFalse(filter.matches(game, metadata(NativeHdr.UNKNOWN)))
        assertFalse(filter.matches(game, null))
        assertTrue(MetadataFilters(hdr = setOf(HdrFilter.UNKNOWN)).matches(game, null))
    }

    @Test fun unverifiedHdrMatchesUnknownRecordsAndMissingMetadataButNeverConfirmedSupport() {
        val filter = MetadataFilters(hdr = setOf(HdrFilter.UNKNOWN))
        assertTrue(filter.matches(game, metadata(NativeHdr.UNKNOWN)))
        assertTrue(filter.matches(game, null))
        assertFalse(filter.matches(game, metadata(NativeHdr.HDR)))
        assertFalse(filter.matches(game, metadata(NativeHdr.SDR)))
    }

    @Test fun activeCountCountsEveryChosenValue() {
        assertEquals(0, MetadataFilters().activeCount)
        assertFalse(MetadataFilters().active)
        assertEquals(1, MetadataFilters(length = setOf(LengthFilter.UNKNOWN)).activeCount)
        assertEquals(4, MetadataFilters(setOf(5), setOf(19), setOf(HdrFilter.SDR), setOf(LengthFilter.OVER_60)).activeCount)
        assertEquals(4, MetadataFilters(genres = setOf(5, 12), themes = setOf(19), hdr = setOf(HdrFilter.HDR)).activeCount)
        assertTrue(MetadataFilters(genres = setOf(5)).active)
        assertTrue(MetadataFilters(themes = setOf(19)).active)
    }

    @Test fun togglingAddsAValueAndASecondToggleRemovesItLeavingTheOtherRows() {
        val chosen = MetadataFilters().toggleGenre(5).toggleGenre(12).toggleTheme(19).toggleHdr(HdrFilter.HDR).toggleLength(LengthFilter.UNDER_10)
        assertEquals(MetadataFilters(setOf(5, 12), setOf(19), setOf(HdrFilter.HDR), setOf(LengthFilter.UNDER_10)), chosen)
        assertEquals(MetadataFilters(setOf(12), setOf(19), setOf(HdrFilter.HDR), setOf(LengthFilter.UNDER_10)), chosen.toggleGenre(5))
        assertEquals(MetadataFilters(), chosen.toggleGenre(5).toggleGenre(12).toggleTheme(19).toggleHdr(HdrFilter.HDR).toggleLength(LengthFilter.UNDER_10))
    }

    @Test fun aThemeFilterMatchesAnyOfTheGamesThemesAndNeverGamesWithoutThemes() {
        val filter = MetadataFilters(themes = setOf(19))
        assertTrue(filter.matches(game, metadata(themes = intArrayOf(1, 19))))
        assertTrue(filter.matches(game, metadata(themes = intArrayOf(19))))
        assertFalse(filter.matches(game, metadata(themes = intArrayOf(1, 21))))
        assertFalse(filter.matches(game, metadata()))
        assertFalse(filter.matches(game, null))
    }

    @Test fun aGenreFilterMatchesAnyOfTheGamesGenresNotOnlyTheMainOne() {
        val twoGenres = GameMetadata(candidate = MetadataCandidate(10, "Game", 2020, genres = listOf(GameGenre(9, "Puzzle"), GameGenre(31, "Adventure"))))
        assertTrue(MetadataFilters(genres = setOf(9)).matches(game, twoGenres))
        assertTrue(MetadataFilters(genres = setOf(31)).matches(game, twoGenres))
        assertFalse(MetadataFilters(genres = setOf(5)).matches(game, twoGenres))
    }

    @Test fun valuesInOneRowCombineWithOr() {
        val genres = MetadataFilters(genres = setOf(5, 12))
        assertTrue(genres.matches(game, metadata(genre = 5)))
        assertTrue(genres.matches(game, metadata(genre = 12)))
        assertFalse(genres.matches(game, metadata(genre = 1)))
        val themes = MetadataFilters(themes = setOf(19, 21))
        assertTrue(themes.matches(game, metadata(themes = intArrayOf(21))))
        assertFalse(themes.matches(game, metadata(themes = intArrayOf(1))))
        val hdr = MetadataFilters(hdr = setOf(HdrFilter.HDR, HdrFilter.UNKNOWN))
        assertTrue(hdr.matches(game, metadata(NativeHdr.HDR)))
        assertTrue(hdr.matches(game, metadata(NativeHdr.UNKNOWN)))
        assertFalse(hdr.matches(game, metadata(NativeHdr.SDR)))
        val length = MetadataFilters(length = setOf(LengthFilter.UNDER_10, LengthFilter.UNKNOWN))
        assertTrue(length.matches(game, metadata(seconds = 20_000)))
        assertTrue(length.matches(game, metadata(seconds = null)))
        assertFalse(length.matches(game, metadata(seconds = 100_000)))
    }

    @Test fun rowsCombineWithAnd() {
        // Two genres + a theme + HDR: either genre, and the theme, and native HDR
        val filter = MetadataFilters(genres = setOf(5, 12), themes = setOf(19), hdr = setOf(HdrFilter.HDR))
        assertTrue(filter.matches(game, metadata(NativeHdr.HDR, genre = 5, themes = intArrayOf(19))))
        assertTrue(filter.matches(game, metadata(NativeHdr.HDR, genre = 12, themes = intArrayOf(1, 19))))
        assertFalse(filter.matches(game, metadata(NativeHdr.SDR, genre = 5, themes = intArrayOf(19))))
        assertFalse(filter.matches(game, metadata(NativeHdr.HDR, genre = 5, themes = intArrayOf(21))))
        assertFalse(filter.matches(game, metadata(NativeHdr.HDR, genre = 1, themes = intArrayOf(19))))
    }

    @Test fun genreAndThemeAreIndependentRequirements() {
        val filter = MetadataFilters(genres = setOf(12), themes = setOf(19))
        assertTrue(filter.matches(game, metadata(genre = 12, themes = intArrayOf(19))))
        assertFalse(filter.matches(game, metadata(genre = 12, themes = intArrayOf(21))))
        assertFalse(filter.matches(game, metadata(genre = 5, themes = intArrayOf(19))))
    }

    @Test fun durationRangesHaveUnambiguousBoundariesAndKeepMissingValuesSeparate() {
        val times = listOf(35_999L, 36_000L, 107_999L, 108_000L, 215_999L, 216_000L)
        val expected = mapOf(
            LengthFilter.UNDER_10 to listOf(35_999L),
            LengthFilter.FROM_10_TO_30 to listOf(36_000L, 107_999L),
            LengthFilter.FROM_30_TO_60 to listOf(108_000L, 215_999L),
            LengthFilter.OVER_60 to listOf(216_000L),
        )
        for ((range, values) in expected) {
            val filter = MetadataFilters(length = setOf(range))
            assertEquals(values, times.filter { filter.matches(game, metadata(seconds = it)) })
            assertFalse(filter.matches(game, metadata(seconds = null)))
            assertFalse(filter.matches(game, metadata(seconds = 0)))
        }
        val unknown = MetadataFilters(length = setOf(LengthFilter.UNKNOWN))
        assertTrue(unknown.matches(game, metadata(seconds = null)))
        assertTrue(unknown.matches(game, metadata(seconds = 0)))
        assertFalse(unknown.matches(game, metadata(seconds = 36_000)))
    }

    @Test fun genreHdrAndHeadlineLengthAreCombinedAsIndependentRequirements() {
        val filter = MetadataFilters(genres = setOf(12), hdr = setOf(HdrFilter.HDR), length = setOf(LengthFilter.UNDER_10))
        assertTrue(filter.matches(game, metadata(NativeHdr.HDR, 20_000, 12)))
        assertFalse(filter.matches(game, metadata(NativeHdr.HDR, 20_000, 1)))
        assertFalse(filter.matches(game, metadata(NativeHdr.SDR, 20_000, 12)))
        assertFalse(filter.matches(game, metadata(NativeHdr.HDR, 36_000, 12)))
        assertFalse(filter.matches(game, null))
    }

    @Test fun builtInsMatchOnlyInactiveFiltersEvenWithStaleGameMetadata() {
        val builtIns = listOf(
            " desktop " to null,
            "REMOTE INPUT" to null,
            " remote monitor " to null,
            "Steam Big Picture" to null,
            " steam bigpicture " to null,
            "Renamed Input" to "9A1C5A25-58FE-40E0-B9AA-7D3F00000006",
            "Renamed Monitor" to "9a1c5a25-58fe-40e0-b9aa-7d3f00000005",
        ).mapIndexed { index, (name, uuid) ->
            App(index + 10, name, false, false, NvApp().apply { setAppUUID(uuid) })
        }
        val activeFilters = listOf(MetadataFilters(genres = setOf(1)), MetadataFilters(themes = setOf(1))) +
            HdrFilter.entries.map { MetadataFilters(hdr = setOf(it)) } +
            LengthFilter.entries.map { MetadataFilters(length = setOf(it)) }
        for (builtIn in builtIns) {
            for (data in listOf(null, metadata(), metadata(NativeHdr.HDR, 20_000))) {
                assertTrue(builtIn.name, MetadataFilters().matches(builtIn, data))
                for (filter in activeFilters) {
                    assertFalse("${builtIn.name}: $filter", filter.matches(builtIn, data))
                }
            }
        }
    }

    @Test fun ordinaryGamesWithUnknownUuidsRetainMissingMetadataMatching() {
        val ordinary = App(2, "Ordinary Game", false, false, NvApp().apply {
            setAppUUID("9a1c5a25-58fe-40e0-b9aa-7d3f00000007")
        })
        assertTrue(MetadataFilters().matches(ordinary, null))
        assertTrue(MetadataFilters(hdr = setOf(HdrFilter.UNKNOWN)).matches(ordinary, null))
        assertTrue(MetadataFilters(length = setOf(LengthFilter.UNKNOWN)).matches(ordinary, null))
        assertTrue(MetadataFilters(hdr = setOf(HdrFilter.UNKNOWN), length = setOf(LengthFilter.UNKNOWN)).matches(ordinary, null))
        assertFalse(MetadataFilters(genres = setOf(1)).matches(ordinary, null))
    }
}
