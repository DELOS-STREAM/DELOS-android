package com.limelight.shell.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** The Worker's NORMALIZED fixture of tools/metadata-worker/test/details.test.js, with another [id] and [name] when asked. */
internal fun detailsFixture(id: Long = 214337, name: String = "Hollow Meridian") = """
    {"id":$id,"name":"$name","slug":"hollow-meridian","summary":"A salvage diver wakes aboard the Meridian.","storyline":null,
     "firstReleaseDate":1758153600,
     "releaseDates":[{"platform":"PC","date":1758153600,"human":"Sep 18, 2025"},{"platform":"PS5","date":1760000000,"human":"Oct 09, 2025"}],
     "rating":82.37,"ratingCount":1284,"criticRating":86.1,"criticRatingCount":42,"totalRating":84.24,"totalRatingCount":1326,
     "genres":["Shooter","Adventure"],"themes":["Horror"],"modes":["Single player"],"perspectives":["Third person"],
     "platforms":[{"name":"PC (Microsoft Windows)","abbreviation":"PC"},{"name":"PlayStation 5","abbreviation":null}],
     "companies":[{"name":"Driftlight Studios","developer":true,"publisher":true,"porting":false,"supporting":false},
                  {"name":"Northvale Interactive","developer":false,"publisher":true,"porting":false,"supporting":false}],
     "ageRatings":[{"organization":"PEGI","rating":"18","descriptors":["Violence"]},{"organization":"ESRB","rating":"M","descriptors":[]}],
     "screenshots":[{"imageId":"sc6x1a","width":1920,"height":1080},{"imageId":"sc6x1b","width":null,"height":null}],
     "artworks":[{"imageId":"ar9q","width":3840,"height":2160}],
     "videos":[{"name":"Launch Trailer","videoId":"dQw4w9WgXcQ"}],
     "websites":[{"url":"https://hollowmeridian.example/","type":"Official Website"}],
     "similarGames":[{"id":11,"name":"Ashfall Protocol","coverImageId":"co1abc"},{"id":12,"name":"The Quiet Orbit","coverImageId":null}],
     "franchises":["Meridian"],"collections":["Meridian"],"engines":["Unreal Engine 5"],"keywords":["space station"],
     "languages":["English","German"],
     "multiplayer":{"onlineMax":8,"offlineMax":0,"onlineCoopMax":0,"offlineCoopMax":0,
                    "onlineCoop":true,"offlineCoop":false,"lanCoop":false,"campaignCoop":false,"dropIn":false,"splitscreen":true},
     "externalGames":[{"uid":"2741160","source":1}]}
""".trimIndent()

class GameDetailsParserTest {
    @Test fun theWorkersFullObjectIsReadCompletely() {
        val details = GameDetailsParser.parse(detailsFixture())
        assertEquals(
            GameDetails(
                id = 214337, name = "Hollow Meridian", slug = "hollow-meridian", summary = "A salvage diver wakes aboard the Meridian.",
                storyline = null, firstReleaseDate = 1758153600,
                releaseDates = listOf(DetailReleaseDate("PC", 1758153600, "Sep 18, 2025"), DetailReleaseDate("PS5", 1760000000, "Oct 09, 2025")),
                rating = 82.37, ratingCount = 1284, criticRating = 86.1, criticRatingCount = 42, totalRating = 84.24, totalRatingCount = 1326,
                genres = listOf("Shooter", "Adventure"), themes = listOf("Horror"), modes = listOf("Single player"), perspectives = listOf("Third person"),
                platforms = listOf(DetailPlatform("PC (Microsoft Windows)", "PC"), DetailPlatform("PlayStation 5", null)),
                companies = listOf(
                    DetailCompany("Driftlight Studios", developer = true, publisher = true, porting = false, supporting = false),
                    DetailCompany("Northvale Interactive", developer = false, publisher = true, porting = false, supporting = false),
                ),
                ageRatings = listOf(DetailAgeRating("PEGI", "18", listOf("Violence")), DetailAgeRating("ESRB", "M", emptyList())),
                screenshots = listOf(IgdbImage("sc6x1a", 1920, 1080), IgdbImage("sc6x1b", null, null)),
                artworks = listOf(IgdbImage("ar9q", 3840, 2160)),
                videos = listOf(DetailVideo("Launch Trailer", "dQw4w9WgXcQ")),
                websites = listOf(DetailWebsite("https://hollowmeridian.example/", "Official Website")),
                similarGames = listOf(SimilarGame(11, "Ashfall Protocol", "co1abc"), SimilarGame(12, "The Quiet Orbit", null)),
                franchises = listOf("Meridian"), collections = listOf("Meridian"), engines = listOf("Unreal Engine 5"),
                keywords = listOf("space station"), languages = listOf("English", "German"),
                multiplayer = DetailMultiplayer(8, 0, 0, 0, onlineCoop = true, offlineCoop = false, lanCoop = false, campaignCoop = false, dropIn = false, splitscreen = true),
                externalGames = listOf(DetailExternalGame("2741160", 1)),
            ),
            details,
        )
    }

    @Test fun missingKeysBecomeNullZeroOrEmpty() {
        val sparse = GameDetailsParser.parse("""{"id":7,"name":"Paper Lanterns","rating":71.2,"ratingCount":23}""")!!
        assertEquals(7L, sparse.id)
        assertEquals("Paper Lanterns", sparse.name)
        assertEquals(71.2, sparse.rating!!, 0.0)
        assertEquals(23, sparse.ratingCount)
        assertNull(sparse.slug)
        assertNull(sparse.summary)
        assertNull(sparse.firstReleaseDate)
        assertNull(sparse.criticRating)
        assertEquals(0, sparse.criticRatingCount)
        assertNull(sparse.multiplayer)
        assertNull(sparse.steam)
        for (list in listOf(sparse.releaseDates, sparse.genres, sparse.platforms, sparse.companies, sparse.ageRatings, sparse.screenshots,
            sparse.artworks, sparse.videos, sparse.websites, sparse.similarGames, sparse.keywords, sparse.languages, sparse.externalGames)) {
            assertTrue(list.isEmpty())
        }
    }

    @Test fun anUnusableIdentityOrMalformedJsonGivesNullWithoutThrowing() {
        for (json in listOf("", "not json", "[]", "null", "42", "{", """{"name":"No id"}""", """{"id":1}""", """{"id":0,"name":"Zero"}""",
            """{"id":-4,"name":"Negative"}""", """{"id":1.5,"name":"Fraction"}""", """{"id":"7","name":"Text id"}""", """{"id":7,"name":"  "}""",
            """{"id":7,"name":["List"]}""")) {
            assertNull(json, GameDetailsParser.parse(json))
        }
    }

    @Test fun mistypedFieldsAndEntriesAreDroppedNotFatal() {
        val details = GameDetailsParser.parse("""
            {"id":7,"name":" Game ","slug":5,"summary":"  ","rating":140,"criticRating":"high","ratingCount":-3,"totalRatingCount":"9",
             "genres":["RPG",3,null," ",{"name":"x"}],"platforms":"PC",
             "screenshots":[{"imageId":"ok_1-a","width":0,"height":-1},{"imageId":"bad/id"},{"width":5},"str"],
             "videos":[{"videoId":"<script>"},{"name":"Fine","videoId":"abc"}],
             "websites":[{"url":"javascript:alert(1)"},{"url":"http://plain.example"},{"type":"No url"}],
             "similarGames":[{"id":0,"name":"Zero"},{"id":3},{"id":4,"name":"Kept","coverImageId":"x y"}],
             "ageRatings":[{"organization":"PEGI"},{"organization":"USK","rating":"16","descriptors":"Gewalt"}],
             "companies":[{"developer":true},{"name":"Solo","developer":"yes"}],
             "externalGames":[{"uid":"1"},{"uid":"2","source":1}],"multiplayer":[1]}
        """.trimIndent())
        assertNotNull(details)
        details!!
        assertEquals("Game", details.name)
        assertNull(details.slug)
        assertNull(details.summary)
        assertNull(details.rating)
        assertNull(details.criticRating)
        assertEquals(0, details.ratingCount)
        assertEquals(0, details.totalRatingCount)
        assertEquals(listOf("RPG"), details.genres)
        assertTrue(details.platforms.isEmpty())
        assertEquals(listOf(IgdbImage("ok_1-a", null, null)), details.screenshots)
        assertEquals(listOf(DetailVideo("Fine", "abc")), details.videos)
        assertEquals(listOf(DetailWebsite("http://plain.example", null)), details.websites)
        assertEquals(listOf(SimilarGame(4, "Kept", null)), details.similarGames)
        assertEquals(listOf(DetailAgeRating("USK", "16", emptyList())), details.ageRatings)
        assertEquals(listOf(DetailCompany("Solo", developer = false, publisher = false, porting = false, supporting = false)), details.companies)
        assertEquals(listOf(DetailExternalGame("2", 1)), details.externalGames)
        assertNull(details.multiplayer)
    }

    @Test fun imageAndVideoUrlsFollowIgdbAndYoutube() {
        assertEquals("https://images.igdb.com/igdb/image/upload/t_screenshot_med/sc6x1a.jpg", IgdbImageUrls.image("sc6x1a", IgdbImageSize.SCREENSHOT_MED))
        assertEquals("https://images.igdb.com/igdb/image/upload/t_screenshot_big_2x/sc6x1a.jpg",
            IgdbImageUrls.image("sc6x1a", IgdbImageSize.SCREENSHOT_BIG, retina = true))
        assertEquals("https://images.igdb.com/igdb/image/upload/t_screenshot_huge/a.jpg", IgdbImageUrls.image("a", IgdbImageSize.SCREENSHOT_HUGE))
        assertEquals("https://images.igdb.com/igdb/image/upload/t_1080p/a.jpg", IgdbImageUrls.image("a", IgdbImageSize.HD_1080))
        assertEquals("https://images.igdb.com/igdb/image/upload/t_cover_big_2x/co1abc.jpg", IgdbImageUrls.image("co1abc", IgdbImageSize.COVER_BIG, true))
        assertEquals("https://i.ytimg.com/vi/dQw4w9WgXcQ/hqdefault.jpg", IgdbImageUrls.youtubeThumb("dQw4w9WgXcQ"))
        assertEquals("https://www.youtube.com/watch?v=dQw4w9WgXcQ", IgdbImageUrls.youtubeWatch("dQw4w9WgXcQ"))
    }

    @Test fun steamMediaIsReadWithHighlightTrailersFirst() {
        val json = detailsFixture().removeSuffix("}") + """,
            "steam":{"appId":1562430,
             "movies":[{"id":2,"name":"Teaser","thumbnail":"https://cdn.steamstatic.com/2/movie.293x165.jpg","mp4":"https://cdn.steamstatic.com/2/movie_max.mp4","webm":"https://cdn.steamstatic.com/2/movie_max.webm"},
                       {"id":1,"name":"Launch Trailer","thumbnail":"https://cdn.steamstatic.com/1/movie.293x165.jpg","hls":"https://cdn.steamstatic.com/1/hls_264_master.m3u8?t=1","dash":"https://cdn.steamstatic.com/1/dash_h264.mpd?t=1","highlight":true}],
             "screenshots":[{"id":0,"thumbnail":"https://cdn.steamstatic.com/ss_a.600x338.jpg","full":"https://cdn.steamstatic.com/ss_a.1920x1080.jpg"}]}}
        """.trimIndent()
        assertEquals(
            SteamMedia(
                1562430,
                listOf(
                    SteamMovie(1, "Launch Trailer", "https://cdn.steamstatic.com/1/movie.293x165.jpg", "https://cdn.steamstatic.com/1/hls_264_master.m3u8?t=1",
                        "https://cdn.steamstatic.com/1/dash_h264.mpd?t=1", null, null, highlight = true),
                    SteamMovie(2, "Teaser", "https://cdn.steamstatic.com/2/movie.293x165.jpg", null, null,
                        "https://cdn.steamstatic.com/2/movie_max.mp4", "https://cdn.steamstatic.com/2/movie_max.webm", highlight = false),
                ),
                listOf(SteamScreenshot(0, "https://cdn.steamstatic.com/ss_a.600x338.jpg", "https://cdn.steamstatic.com/ss_a.1920x1080.jpg")),
            ),
            GameDetailsParser.parse(json)!!.steam,
        )
    }

    @Test fun steamEntriesWithoutAPlayableHttpsStreamOrPictureAreDropped() {
        fun steam(body: String) = GameDetailsParser.parse("""{"id":7,"name":"Game","steam":$body}""")!!.steam
        val kept = steam("""
            {"appId":10,
             "movies":[{"id":1,"dash":"https://cdn.steamstatic.com/1.mpd"},{"id":2,"hls":"http://cdn.steamstatic.com/2.m3u8"},{"hls":"https://x/3.m3u8"},
                       {"id":4,"hls":"https://cdn.steamstatic.com/4.m3u8","thumbnail":"javascript:x"},{"id":4,"hls":"https://cdn.steamstatic.com/dup.m3u8"}],
             "screenshots":[{"id":0,"thumbnail":"https://cdn.steamstatic.com/s.jpg"},{"id":1,"full":"https://cdn.steamstatic.com/f.jpg"}]}
        """.trimIndent())!!
        assertEquals(listOf(SteamMovie(4, null, null, "https://cdn.steamstatic.com/4.m3u8", null, null, null, false)), kept.movies)
        assertEquals(listOf(SteamScreenshot(1, null, "https://cdn.steamstatic.com/f.jpg")), kept.screenshots)
        for (body in listOf("""{"appId":10,"movies":[],"screenshots":[]}""", """{"movies":[{"id":1,"hls":"https://a/b.m3u8"}]}""", "[]", "null")) {
            assertNull(body, steam(body))
        }
    }
}
