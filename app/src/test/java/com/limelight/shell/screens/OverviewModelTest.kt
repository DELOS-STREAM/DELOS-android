package com.limelight.shell.screens

import com.limelight.shell.data.DetailAgeRating
import com.limelight.shell.data.DetailCompany
import com.limelight.shell.data.DetailExternalGame
import com.limelight.shell.data.DetailMultiplayer
import com.limelight.shell.data.DetailVideo
import com.limelight.shell.data.DetailWebsite
import com.limelight.shell.data.GameDetails
import com.limelight.shell.data.IgdbImage
import com.limelight.shell.data.SimilarGame
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** What the game overview shows from the IGDB details (docs/gui/GAME_INFO.md, "Game overview"). */
class OverviewModelTest {
    private fun details(
        summary: String? = "A salvage diver wakes aboard the Meridian.",
        companies: List<DetailCompany> = listOf(
            DetailCompany("Driftlight Studios", developer = true, publisher = false, porting = false, supporting = false),
            DetailCompany("Northvale Interactive", developer = false, publisher = true, porting = false, supporting = false),
        ),
        websites: List<DetailWebsite> = emptyList(),
        screenshots: List<IgdbImage> = listOf(IgdbImage("sc1", 1920, 1080), IgdbImage("sc2", null, null)),
        videos: List<DetailVideo> = listOf(DetailVideo("Launch Trailer", "dQw4w9WgXcQ")),
        similar: List<SimilarGame> = emptyList(),
        ageRatings: List<DetailAgeRating> = emptyList(),
        multiplayer: DetailMultiplayer? = null,
        externalGames: List<DetailExternalGame> = emptyList(),
        storyline: String? = null,
    ) = GameDetails(
        id = 214337, name = "Hollow Meridian", slug = null, summary = summary, storyline = storyline, firstReleaseDate = 1758153600,
        releaseDates = emptyList(), rating = 82.37, ratingCount = 1284, criticRating = 86.1, criticRatingCount = 42, totalRating = null,
        totalRatingCount = 0, genres = listOf("Shooter"), themes = emptyList(), modes = listOf("Single player"), perspectives = emptyList(),
        platforms = emptyList(), companies = companies, ageRatings = ageRatings, screenshots = screenshots, artworks = emptyList(),
        videos = videos, websites = websites, similarGames = similar, franchises = listOf("Meridian"), collections = emptyList(),
        engines = listOf("Unreal Engine 5"), keywords = emptyList(), languages = emptyList(), multiplayer = multiplayer,
        externalGames = externalGames,
    )

    @Test fun `videos come before the screenshots, with IGDB and YouTube URLs`() {
        val media = OverviewModel.media(details())
        assertEquals(listOf(OverviewModel.MediaKind.VIDEO, OverviewModel.MediaKind.SCREENSHOT, OverviewModel.MediaKind.SCREENSHOT), media.map { it.kind })
        assertEquals("https://i.ytimg.com/vi/dQw4w9WgXcQ/hqdefault.jpg", media[0].thumb)
        assertEquals("https://i.ytimg.com/vi/dQw4w9WgXcQ/maxresdefault.jpg", media[0].full)
        assertEquals("https://images.igdb.com/igdb/image/upload/t_screenshot_med/sc1.jpg", media[1].thumb)
        assertEquals("https://images.igdb.com/igdb/image/upload/t_screenshot_huge/sc1.jpg", media[1].full)
        assertEquals(2, media[2].number)
    }

    @Test fun `links put the official site first and name the others by IGDB's type`() {
        val links = OverviewModel.links(
            details(websites = listOf(
                DetailWebsite("https://store.steampowered.com/app/2741160", "Steam"),
                DetailWebsite("https://www.hollowmeridian.example/", "Official Website"),
                DetailWebsite("https://twitch.tv/x", "Twitch"),
                DetailWebsite("https://twitch.tv/x", "Twitch"),
                DetailWebsite("https://gog.com/game/x", "GOG"),
                DetailWebsite("ftp://example.org/x", "Other"),
            )),
            emptyList(),
        )
        assertEquals(listOf(OverviewModel.LinkKind.OFFICIAL, OverviewModel.LinkKind.STEAM, OverviewModel.LinkKind.VIDEO, OverviewModel.LinkKind.STORE), links.map { it.kind })
        assertNull(links[0].name)
        assertEquals("hollowmeridian.example", links[0].host)
        assertEquals("Twitch", links[2].name)
    }

    @Test fun `without websites the Steam page is the link`() {
        val fromApp = OverviewModel.links(details(), listOf(292030L))
        assertEquals("https://store.steampowered.com/app/292030/", fromApp.single().url)
        val fromIgdb = OverviewModel.links(details(externalGames = listOf(DetailExternalGame("1091500", 1))), emptyList())
        assertEquals("https://store.steampowered.com/app/1091500/", fromIgdb.single().url)
        assertTrue(OverviewModel.links(details(), emptyList()).isEmpty())
    }

    @Test fun `the age rating of the device's region comes first`() {
        val ratings = listOf(DetailAgeRating("ESRB", "M", emptyList()), DetailAgeRating("USK", "18", emptyList()), DetailAgeRating("PEGI", "18", emptyList()))
        assertEquals("PEGI", OverviewModel.ageRatings(ratings, "CZ").first().organization)
        assertEquals("ESRB", OverviewModel.ageRatings(ratings, "US").first().organization)
        assertEquals("USK", OverviewModel.ageRatings(ratings, "DE").first().organization)
        assertEquals(listOf("PEGI", "ESRB", "USK"), OverviewModel.ageRatings(ratings, "").map { it.organization })
    }

    @Test fun `the facts name developer, publisher, release, engine and series and see self-publishing`() {
        val facts = OverviewModel.facts(details())
        assertEquals(
            listOf(OverviewModel.Fact.DEVELOPER, OverviewModel.Fact.PUBLISHER, OverviewModel.Fact.RELEASED, OverviewModel.Fact.ENGINE, OverviewModel.Fact.SERIES),
            facts.map { it.fact },
        )
        assertEquals(1758153600L, facts[2].date)
        val self = OverviewModel.facts(details(companies = listOf(DetailCompany("Solo", developer = true, publisher = true, porting = false, supporting = false))))
        assertTrue(self.first { it.fact == OverviewModel.Fact.PUBLISHER }.selfPublished)
    }

    @Test fun `the share of HowLongToBeat is rounded and capped`() {
        assertEquals(90, OverviewModel.hltbShare((20.4 * 3600_000).toLong(), 81900))
        assertEquals(100, OverviewModel.hltbShare(100 * 3600_000L, 3600))
        assertNull(OverviewModel.hltbShare(1000, null))
        assertNull(OverviewModel.hltbShare(1000, 0))
    }

    @Test fun `empty sections do not exist and the sparse game keeps the streaming card`() {
        val sparse = details(summary = null, companies = emptyList(), screenshots = emptyList(), videos = emptyList())
            .copy(firstReleaseDate = null, engines = emptyList(), franchises = emptyList())
        assertEquals(
            listOf(OverviewModel.Section.DETAILS, OverviewModel.Section.STREAMING),
            OverviewModel.sections(sparse, emptyList(), OverviewModel.media(sparse)),
        )
        assertEquals(listOf(OverviewModel.Section.STREAMING), OverviewModel.sections(null, emptyList(), emptyList()))
        val full = details(similar = listOf(SimilarGame(1, "Ashfall Protocol", "co1")))
        val sections = OverviewModel.sections(full, OverviewModel.links(full, listOf(1L)), OverviewModel.media(full))
        assertEquals(OverviewModel.Section.entries.toList(), sections)
        assertEquals(
            listOf(OverviewModel.Section.ABOUT, OverviewModel.Section.MEDIA, OverviewModel.Section.DETAILS, OverviewModel.Section.SIMILAR),
            OverviewModel.cue(sections),
        )
    }

    @Test fun `a similar game is owned by its IGDB id or by its tolerant title`() {
        data class Game(val name: String, val igdb: Long?)
        val library = listOf(Game("Apex Circuit", 77), Game("The Quiet Orbit (Steam)", null))
        val byId = OverviewModel.owned(SimilarGame(77, "Apex Circuit: Turbo", null), library, { it.igdb }, ::fakeKey, { it.name })
        assertEquals("Apex Circuit", byId?.name)
        val byName = OverviewModel.owned(SimilarGame(5, "Quiet Orbit", null), library, { it.igdb }, ::fakeKey, { it.name })
        assertEquals("The Quiet Orbit (Steam)", byName?.name)
        assertNull(OverviewModel.owned(SimilarGame(9, "Deepwake", null), library, { it.igdb }, ::fakeKey, { it.name }))
    }

    private fun fakeKey(name: String) = com.limelight.shell.data.matchKey(name)

    @Test fun `multiplayer lists only what IGDB knows`() {
        assertNull(OverviewModel.coop(null))
        assertNull(OverviewModel.coop(DetailMultiplayer(0, 0, 0, 0, false, false, false, false, false, false)))
        val coop = OverviewModel.coop(DetailMultiplayer(8, 0, 4, 0, true, false, false, false, false, true))!!
        assertEquals(4, coop.onlineCoopMax)
        assertEquals(8, coop.onlineMax)
        assertTrue(coop.splitscreen)
    }

    @Test fun `the link kinds follow IGDB's website types`() {
        assertEquals(OverviewModel.LinkKind.OFFICIAL, OverviewModel.linkKind("Official Website"))
        assertEquals(OverviewModel.LinkKind.STEAM, OverviewModel.linkKind("Steam"))
        assertEquals(OverviewModel.LinkKind.STORE, OverviewModel.linkKind("Epic Games"))
        assertEquals(OverviewModel.LinkKind.WIKI, OverviewModel.linkKind("Wikipedia"))
        assertEquals(OverviewModel.LinkKind.VIDEO, OverviewModel.linkKind("YouTube"))
        assertEquals(OverviewModel.LinkKind.OTHER, OverviewModel.linkKind("Discord"))
        assertEquals(OverviewModel.LinkKind.OTHER, OverviewModel.linkKind(null))
    }
}
