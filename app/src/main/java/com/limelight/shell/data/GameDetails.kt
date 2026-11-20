package com.limelight.shell.data

import androidx.compose.runtime.Immutable
import com.google.gson.JsonElement
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import com.google.gson.JsonPrimitive

@Immutable
data class DetailReleaseDate(val platform: String?, val date: Long?, val human: String?)

@Immutable
data class DetailPlatform(val name: String, val abbreviation: String?)

@Immutable
data class DetailCompany(val name: String, val developer: Boolean, val publisher: Boolean, val porting: Boolean, val supporting: Boolean)

/** One organization's mark as printed on its badge ("18", "M", "E10+"). */
@Immutable
data class DetailAgeRating(val organization: String, val rating: String, val descriptors: List<String>)

@Immutable
data class IgdbImage(val imageId: String, val width: Int?, val height: Int?)

/** [videoId] is a YouTube id. */
@Immutable
data class DetailVideo(val name: String?, val videoId: String)

@Immutable
data class DetailWebsite(val url: String, val type: String?)

@Immutable
data class SimilarGame(val id: Long, val name: String, val coverImageId: String?)

/** Every IGDB multiplayer mode of the game folded into one: the largest player counts, any co-op kind. */
@Immutable
data class DetailMultiplayer(
    val onlineMax: Int,
    val offlineMax: Int,
    val onlineCoopMax: Int,
    val offlineCoopMax: Int,
    val onlineCoop: Boolean,
    val offlineCoop: Boolean,
    val lanCoop: Boolean,
    val campaignCoop: Boolean,
    val dropIn: Boolean,
    val splitscreen: Boolean,
)

/** [source] is IGDB's external_game_source (1 = Steam). */
@Immutable
data class DetailExternalGame(val uid: String, val source: Int)

/** The game overview's IGDB data: the Worker's `v1/igdb/game-details` object (tools/metadata-worker/README.md, "Game details"). */
@Immutable
data class GameDetails(
    val id: Long,
    val name: String,
    val slug: String?,
    val summary: String?,
    /** Paragraphs separated by a blank line. */
    val storyline: String?,
    /** Unix seconds (UTC). */
    val firstReleaseDate: Long?,
    /** Oldest first. */
    val releaseDates: List<DetailReleaseDate>,
    /** Ratings are 0-100; the counts are 0 when nobody rated. */
    val rating: Double?,
    val ratingCount: Int,
    val criticRating: Double?,
    val criticRatingCount: Int,
    val totalRating: Double?,
    val totalRatingCount: Int,
    val genres: List<String>,
    val themes: List<String>,
    val modes: List<String>,
    val perspectives: List<String>,
    val platforms: List<DetailPlatform>,
    val companies: List<DetailCompany>,
    val ageRatings: List<DetailAgeRating>,
    val screenshots: List<IgdbImage>,
    val artworks: List<IgdbImage>,
    val videos: List<DetailVideo>,
    val websites: List<DetailWebsite>,
    val similarGames: List<SimilarGame>,
    val franchises: List<String>,
    val collections: List<String>,
    val engines: List<String>,
    val keywords: List<String>,
    val languages: List<String>,
    val multiplayer: DetailMultiplayer?,
    val externalGames: List<DetailExternalGame>,
)

/**
 * Reads the Worker's game-details JSON. A missing or mistyped scalar becomes null (0 / false for counts and flags), a missing list
 * empty, and a list entry lacking what identifies it is dropped. Image and video ids that could not be put in a URL and links that
 * are not http(s) are dropped too, because the app builds URLs from them and opens them.
 */
object GameDetailsParser {
    private val mediaId = "[A-Za-z0-9_-]+".toRegex()

    /** The details in [json], or null when it is not an object with a positive id and a name. Never throws. */
    fun parse(json: String): GameDetails? = try {
        JsonParser.parseString(json).takeIf { it.isJsonObject }?.asJsonObject?.let(::details)
    } catch (_: RuntimeException) {
        null
    }

    private fun details(game: JsonObject): GameDetails? {
        val id = game.long("id")?.takeIf { it > 0 } ?: return null
        val name = game.string("name") ?: return null
        return GameDetails(
            id = id,
            name = name,
            slug = game.string("slug"),
            summary = game.string("summary"),
            storyline = game.string("storyline"),
            firstReleaseDate = game.long("firstReleaseDate"),
            releaseDates = game.objects("releaseDates") { DetailReleaseDate(it.string("platform"), it.long("date"), it.string("human")) },
            rating = game.rating("rating"),
            ratingCount = game.count("ratingCount"),
            criticRating = game.rating("criticRating"),
            criticRatingCount = game.count("criticRatingCount"),
            totalRating = game.rating("totalRating"),
            totalRatingCount = game.count("totalRatingCount"),
            genres = game.strings("genres"),
            themes = game.strings("themes"),
            modes = game.strings("modes"),
            perspectives = game.strings("perspectives"),
            platforms = game.objects("platforms") { entry ->
                entry.string("name")?.let { DetailPlatform(it, entry.string("abbreviation")) }
            },
            companies = game.objects("companies") { entry ->
                entry.string("name")?.let {
                    DetailCompany(it, entry.flag("developer"), entry.flag("publisher"), entry.flag("porting"), entry.flag("supporting"))
                }
            },
            ageRatings = game.objects("ageRatings") { entry ->
                val organization = entry.string("organization") ?: return@objects null
                val rating = entry.string("rating") ?: return@objects null
                DetailAgeRating(organization, rating, entry.strings("descriptors"))
            },
            screenshots = game.images("screenshots"),
            artworks = game.images("artworks"),
            videos = game.objects("videos") { entry ->
                entry.mediaId("videoId")?.let { DetailVideo(entry.string("name"), it) }
            },
            websites = game.objects("websites") { entry ->
                entry.string("url")?.takeIf { it.startsWith("https://") || it.startsWith("http://") }?.let { DetailWebsite(it, entry.string("type")) }
            },
            similarGames = game.objects("similarGames") { entry ->
                val similarId = entry.long("id")?.takeIf { it > 0 } ?: return@objects null
                entry.string("name")?.let { SimilarGame(similarId, it, entry.mediaId("coverImageId")) }
            },
            franchises = game.strings("franchises"),
            collections = game.strings("collections"),
            engines = game.strings("engines"),
            keywords = game.strings("keywords"),
            languages = game.strings("languages"),
            multiplayer = game.member("multiplayer")?.takeIf { it.isJsonObject }?.asJsonObject?.let { mode ->
                DetailMultiplayer(
                    mode.count("onlineMax"), mode.count("offlineMax"), mode.count("onlineCoopMax"), mode.count("offlineCoopMax"),
                    mode.flag("onlineCoop"), mode.flag("offlineCoop"), mode.flag("lanCoop"), mode.flag("campaignCoop"),
                    mode.flag("dropIn"), mode.flag("splitscreen"),
                )
            },
            externalGames = game.objects("externalGames") { entry ->
                val uid = entry.string("uid") ?: return@objects null
                entry.int("source")?.let { DetailExternalGame(uid, it) }
            },
        )
    }

    private fun JsonObject.member(name: String): JsonElement? = get(name)?.takeUnless { it.isJsonNull }

    private fun JsonObject.primitive(name: String): JsonPrimitive? = member(name)?.takeIf { it.isJsonPrimitive }?.asJsonPrimitive

    private fun JsonObject.string(name: String): String? =
        primitive(name)?.takeIf { it.isString }?.asString?.trim()?.takeIf { it.isNotEmpty() }

    private fun JsonObject.long(name: String): Long? =
        primitive(name)?.takeIf { it.isNumber }?.let { runCatching { it.asBigDecimal.longValueExact() }.getOrNull() }

    private fun JsonObject.int(name: String): Int? =
        primitive(name)?.takeIf { it.isNumber }?.let { runCatching { it.asBigDecimal.intValueExact() }.getOrNull() }

    private fun JsonObject.count(name: String): Int = int(name)?.coerceAtLeast(0) ?: 0

    private fun JsonObject.rating(name: String): Double? =
        primitive(name)?.takeIf { it.isNumber }?.asDouble?.takeIf { it in 0.0..100.0 }

    private fun JsonObject.flag(name: String): Boolean = primitive(name)?.takeIf { it.isBoolean }?.asBoolean ?: false

    private fun JsonObject.mediaId(name: String): String? = string(name)?.takeIf { mediaId.matches(it) }

    private fun JsonObject.array(name: String): List<JsonElement> = member(name)?.takeIf { it.isJsonArray }?.asJsonArray?.toList().orEmpty()

    private fun JsonObject.strings(name: String): List<String> = array(name).mapNotNull { element ->
        element.takeIf { it.isJsonPrimitive && it.asJsonPrimitive.isString }?.asString?.trim()?.takeIf { it.isNotEmpty() }
    }

    private fun <T> JsonObject.objects(name: String, read: (JsonObject) -> T?): List<T> =
        array(name).mapNotNull { element -> element.takeIf { it.isJsonObject }?.asJsonObject?.let(read) }

    private fun JsonObject.images(name: String): List<IgdbImage> = objects(name) { entry ->
        entry.mediaId("imageId")?.let { IgdbImage(it, entry.int("width")?.takeIf { w -> w > 0 }, entry.int("height")?.takeIf { h -> h > 0 }) }
    }
}

/** IGDB's image sizes the overview uses (`t_<path>` in the image URL). */
enum class IgdbImageSize(val path: String) {
    SCREENSHOT_MED("screenshot_med"),
    SCREENSHOT_BIG("screenshot_big"),
    SCREENSHOT_HUGE("screenshot_huge"),
    HD_1080("1080p"),
    COVER_BIG("cover_big"),
}

/** URLs of IGDB's public image CDN and of YouTube; neither needs credentials, so they do not go through the Worker. */
object IgdbImageUrls {
    /** [retina] asks for the double-density version of [size]. */
    fun image(imageId: String, size: IgdbImageSize, retina: Boolean = false): String =
        "https://images.igdb.com/igdb/image/upload/t_${size.path}${if (retina) "_2x" else ""}/$imageId.jpg"

    fun youtubeThumb(videoId: String): String = "https://i.ytimg.com/vi/$videoId/hqdefault.jpg"

    /** The largest still YouTube keeps (1280 × 720); not every video has one, so the viewer keeps [youtubeThumb] until it loads. */
    fun youtubeStill(videoId: String): String = "https://i.ytimg.com/vi/$videoId/maxresdefault.jpg"

    fun youtubeWatch(videoId: String): String = "https://www.youtube.com/watch?v=$videoId"
}
