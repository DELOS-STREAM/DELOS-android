package com.limelight.shell.screens

import com.limelight.shell.data.DetailAgeRating
import com.limelight.shell.data.DetailMultiplayer
import com.limelight.shell.data.GameDetails
import com.limelight.shell.data.IgdbImageSize
import com.limelight.shell.data.IgdbImageUrls
import com.limelight.shell.data.SimilarGame
import java.util.Locale
import kotlin.math.roundToInt

/**
 * What the game overview shows, worked out from the IGDB details and the app's own data with no Compose in it, so the rules are unit
 * tested (`OverviewModelTest`): the media of the carousel, the links, the facts, which age rating comes first, the share of
 * HowLongToBeat streamed, the ratings' captions and which sections exist (docs/gui/GAME_INFO.md, "Game overview").
 */
internal object OverviewModel {
    /** Fewer player ratings than this: the caption says the rating rests on few. */
    const val FEW_RATINGS = 50

    /** At most this many keywords in Details. */
    const val KEYWORDS = 5

    /** IGDB's external game source of Steam (the app's Steam ids come from it too). */
    private const val STEAM_SOURCE = 1

    enum class MediaKind { VIDEO, SCREENSHOT }

    /** One item of the carousel and the viewer: a video's YouTube id or a screenshot's IGDB image id, its [thumb] and its [full] picture. */
    data class Media(val kind: MediaKind, val id: String, val name: String?, val thumb: String, val full: String, val number: Int)

    /** Videos first, then the screenshots (the prototype's order); [screenshotSize] is the viewer's picture (`screenshot_huge` on a 1080p stage). */
    fun media(details: GameDetails, screenshotSize: IgdbImageSize = IgdbImageSize.SCREENSHOT_HUGE): List<Media> =
        details.videos.mapIndexed { i, video ->
            Media(MediaKind.VIDEO, video.videoId, video.name, IgdbImageUrls.youtubeThumb(video.videoId), IgdbImageUrls.youtubeStill(video.videoId), i + 1)
        } + details.screenshots.mapIndexed { i, shot ->
            Media(
                MediaKind.SCREENSHOT, shot.imageId, null,
                IgdbImageUrls.image(shot.imageId, IgdbImageSize.SCREENSHOT_MED), IgdbImageUrls.image(shot.imageId, screenshotSize), i + 1,
            )
        }

    enum class LinkKind { OFFICIAL, STEAM, STORE, WIKI, VIDEO, OTHER }

    /** A link button: its kind (icon), [name] (null: the translated "Official site") and where it opens. */
    data class Link(val kind: LinkKind, val name: String?, val url: String, val host: String)

    /** IGDB's website type names (`websites.type.type`) by kind. */
    private val STORES = setOf("gog", "epic games", "epicgames", "itch", "microsoft store", "xbox", "playstation", "nintendo", "apple app store", "google play", "humble bundle")
    private val WIKIS = setOf("wikipedia", "fandom", "wikia", "wiki")
    private val VIDEOS = setOf("twitch", "youtube")

    fun linkKind(type: String?): LinkKind {
        val t = type?.trim()?.lowercase(Locale.ROOT) ?: return LinkKind.OTHER
        return when {
            t == "official website" || t == "official" -> LinkKind.OFFICIAL
            t == "steam" -> LinkKind.STEAM
            t in STORES || STORES.any { t.startsWith(it) } -> LinkKind.STORE
            t in WIKIS -> LinkKind.WIKI
            t in VIDEOS -> LinkKind.VIDEO
            else -> LinkKind.OTHER
        }
    }

    fun host(url: String): String = url.substringAfter("://").substringBefore('/').removePrefix("www.")

    /**
     * The links of IGDB's websites (http and https only, one per address), official site first; a game IGDB lists no website for but
     * the app knows a Steam id of links its Steam page.
     */
    fun links(details: GameDetails, steamAppIds: List<Long>): List<Link> {
        val links = details.websites
            .filter { it.url.startsWith("https://") || it.url.startsWith("http://") }
            .distinctBy { it.url }
            .map { site ->
                val kind = linkKind(site.type)
                Link(kind, if (kind == LinkKind.OFFICIAL) null else site.type?.takeIf { it.isNotBlank() } ?: host(site.url), site.url, host(site.url))
            }
            .sortedBy { if (it.kind == LinkKind.OFFICIAL) 0 else 1 }
        if (links.isNotEmpty()) return links
        val steam = steamAppIds.firstOrNull()
            ?: details.externalGames.firstOrNull { it.source == STEAM_SOURCE }?.uid?.toLongOrNull()
            ?: return emptyList()
        val url = "https://store.steampowered.com/app/$steam/" // l10n-ignore
        return listOf(Link(LinkKind.STEAM, "Steam", url, host(url))) // l10n-ignore
    }

    /** The organization a device in [country] (ISO 3166 alpha-2) reads first: PEGI in Europe, ESRB in the Americas, the national ones elsewhere. */
    fun preferredOrganization(country: String): String = when (country.uppercase(Locale.ROOT)) {
        "US", "CA", "MX" -> "ESRB"
        "DE" -> "USK"
        "JP" -> "CERO"
        "KR" -> "GRAC"
        "BR" -> "ClassInd"
        "AU" -> "ACB"
        else -> "PEGI"
    }

    /** The age ratings with the one of the device's region first, then PEGI and ESRB, then the rest in IGDB's order. */
    fun ageRatings(ratings: List<DetailAgeRating>, country: String): List<DetailAgeRating> {
        val first = preferredOrganization(country)
        fun rank(rating: DetailAgeRating) = when {
            rating.organization.equals(first, ignoreCase = true) -> 0
            rating.organization.equals("PEGI", ignoreCase = true) -> 1
            rating.organization.equals("ESRB", ignoreCase = true) -> 2
            else -> 3
        }
        return ratings.withIndex().sortedWith(compareBy({ rank(it.value) }, { it.index })).map { it.value }
    }

    enum class Fact { DEVELOPER, PUBLISHER, RELEASED, PORTED, ENGINE, SERIES }

    /** One row of the facts card; [selfPublished]: the publisher is the developer. [date] is the release date's Unix seconds. */
    data class FactRow(val fact: Fact, val value: String, val selfPublished: Boolean = false, val date: Long? = null)

    fun developers(details: GameDetails) = details.companies.filter { it.developer }.map { it.name }

    fun facts(details: GameDetails): List<FactRow> = buildList {
        val developers = developers(details)
        val publishers = details.companies.filter { it.publisher }.map { it.name }
        val porters = details.companies.filter { it.porting }.map { it.name }
        if (developers.isNotEmpty()) add(FactRow(Fact.DEVELOPER, developers.joinToString(", ")))
        if (publishers.isNotEmpty()) add(FactRow(Fact.PUBLISHER, publishers.joinToString(", "), selfPublished = publishers == developers))
        details.firstReleaseDate?.let { add(FactRow(Fact.RELEASED, "", date = it)) }
        if (porters.isNotEmpty()) add(FactRow(Fact.PORTED, porters.joinToString(", ")))
        if (details.engines.isNotEmpty()) add(FactRow(Fact.ENGINE, details.engines.joinToString(", ")))
        (details.franchises.firstOrNull() ?: details.collections.firstOrNull())?.let { add(FactRow(Fact.SERIES, it)) }
    }

    /** The share of HowLongToBeat's time the player has streamed, 0 to 100; null without a time to beat. */
    fun hltbShare(streamedMillis: Long, hltbSeconds: Long?): Int? {
        if (hltbSeconds == null || hltbSeconds <= 0) return null
        return (streamedMillis / 1000.0 / hltbSeconds * 100).roundToInt().coerceIn(0, 100)
    }

    /** How the multiplayer facts read: the parts the Details card lists, in the prototype's order. */
    data class Coop(val onlineCoopMax: Int, val onlineMax: Int, val splitscreen: Boolean, val offlineCoopMax: Int, val lanCoop: Boolean)

    fun coop(multiplayer: DetailMultiplayer?): Coop? {
        val m = multiplayer ?: return null
        val coop = Coop(m.onlineCoopMax, m.onlineMax, m.splitscreen, m.offlineCoopMax, m.lanCoop)
        return coop.takeIf { it.onlineCoopMax > 0 || it.onlineMax > 0 || it.splitscreen || it.offlineCoopMax > 0 || it.lanCoop }
    }

    /** The sections of the page below the header, in order; a section without anything to show does not exist. */
    enum class Section { ABOUT, MEDIA, DETAILS, STREAMING, LINKS, SIMILAR }

    fun sections(details: GameDetails?, links: List<Link>, media: List<Media>): List<Section> = buildList {
        if (details != null && (details.summary != null || details.storyline != null || facts(details).isNotEmpty())) add(Section.ABOUT)
        if (media.isNotEmpty()) add(Section.MEDIA)
        if (details != null) add(Section.DETAILS)
        add(Section.STREAMING)
        if (links.isNotEmpty()) add(Section.LINKS)
        if (details != null && details.similarGames.isNotEmpty()) add(Section.SIMILAR)
    }

    /** The sections the cue under the header names: the ones a player looks for (the prototype's About · Media · Details · Similar games). */
    fun cue(sections: List<Section>): List<Section> = sections.filter { it == Section.ABOUT || it == Section.MEDIA || it == Section.DETAILS || it == Section.SIMILAR }

    /** A similar game the user owns: its library entry by IGDB id, else by the tolerant title ([key]). */
    fun <A> owned(similar: SimilarGame, library: List<A>, igdbId: (A) -> Long?, key: (String) -> String, name: (A) -> String): A? =
        library.firstOrNull { igdbId(it) == similar.id } ?: key(similar.name).takeIf { it.isNotEmpty() }?.let { wanted -> library.firstOrNull { key(name(it)) == wanted } }

    /** The release year of [seconds] (UTC). */
    fun year(seconds: Long): Int = java.util.Calendar.getInstance(java.util.TimeZone.getTimeZone("UTC")).apply { timeInMillis = seconds * 1000 }.get(java.util.Calendar.YEAR)
}
