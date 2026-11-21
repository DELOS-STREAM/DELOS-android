package com.limelight.shell

import android.content.Context
import android.content.SharedPreferences
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Shader
import com.limelight.nvstream.http.ComputerDetails
import com.limelight.nvstream.http.NvApp
import com.limelight.nvstream.http.PairingManager
import com.limelight.shell.data.App
import com.limelight.shell.data.ArtFilters
import com.limelight.shell.data.ArtKind
import com.limelight.shell.data.ArtworkStore
import com.limelight.shell.data.CompletionTime
import com.limelight.shell.data.GameGenre
import com.limelight.shell.data.HdrEvidence
import com.limelight.shell.data.MetadataCandidate
import com.limelight.shell.data.MetadataProviders
import com.limelight.shell.data.NativeHdr
import com.limelight.shell.data.Pc
import com.limelight.shell.data.PcRepository
import com.limelight.shell.data.Profile
import com.limelight.shell.data.Profiles
import com.limelight.shell.data.SgdbGame
import com.limelight.shell.data.SgdbImage
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.security.MessageDigest

/**
 * Debug builds only: the fixed fake world of the screen previews (`ScreenPreviews`): four PCs, one of them with a library of 14 games
 * (12 shown, 2 hidden, one running, one last played, a played order), three profiles and the metadata of every game. Nothing in it
 * is random or comes from the user's data; [NOW] is the clock of the metadata store.
 */
internal object PreviewFixtures {
    /** The PIN the pairing preview shows. */
    const val PIN = "4826"
    const val NOW = 1_700_000_000_000L

    /** The PC whose games Home shows (the repository's active PC and the start screen's default one). */
    const val ACTIVE_PC = "preview-pc-gaming"
    const val STUDY_PC = "preview-pc-study"
    private const val RUNNING_ID = 4
    private const val LAST_PLAYED_ID = 2
    private val HIDDEN_IDS = setOf(13, 14)

    /** Most recently started first; the games after these keep the PC's order. */
    private val PLAYED_IDS = listOf(2, 1, 7)

    class Game(val id: Int, val name: String, val year: Int, val hdr: Boolean, val genres: List<Int>, val themes: List<Int>, val hours: Int)

    /** A PC as the service reports it: [library] says whether it lists the [GAMES]. */
    class Host(
        val uuid: String, val name: String, val online: Boolean, val paired: Boolean, val mac: String?, val address: String?,
        val running: Int = 0, val library: Boolean = false,
    )

    val GENRES = mapOf(
        4 to "Fighting", 5 to "Shooter", 8 to "Platform", 9 to "Puzzle", 10 to "Racing", 12 to "Role-playing (RPG)", 13 to "Simulator",
        14 to "Sport", 25 to "Hack and slash/Beat 'em up", 31 to "Adventure", 32 to "Indie", 35 to "Card & Board Game",
    )
    val THEMES = mapOf(
        1 to "Action", 17 to "Fantasy", 18 to "Science fiction", 19 to "Horror", 21 to "Survival", 31 to "Drama", 38 to "Open world", 43 to "Mystery",
    )

    val GAMES = listOf(
        Game(1, "Cyberpunk 2077", 2020, true, listOf(12, 5), listOf(18, 38), 60),
        Game(2, "Hades", 2020, false, listOf(12, 25, 32), listOf(17, 1), 22),
        Game(3, "Hollow Knight", 2017, false, listOf(8, 31, 32), listOf(17, 1), 27),
        Game(RUNNING_ID, "Elden Ring", 2022, true, listOf(12, 31), listOf(17, 38), 60),
        Game(5, "Forza Horizon 5", 2021, true, listOf(10, 14), listOf(38), 25),
        Game(6, "Celeste", 2018, false, listOf(8, 32), listOf(31), 8),
        Game(7, "DOOM Eternal", 2020, true, listOf(5), listOf(18, 19, 1), 13),
        Game(8, "Stardew Valley", 2016, false, listOf(13, 12, 32), listOf(17), 53),
        Game(9, "The Witcher 3: Wild Hunt", 2015, true, listOf(12), listOf(17, 38), 100),
        Game(10, "Portal 2", 2011, false, listOf(9, 8), listOf(18), 9),
        Game(11, "Disco Elysium", 2019, false, listOf(12), listOf(43, 31), 24),
        Game(12, "Balatro", 2024, false, listOf(35, 32), listOf(1), 30),
        Game(13, "Resident Evil 4", 2023, true, listOf(5, 31), listOf(19, 21), 16),
        Game(14, "Dead Cells", 2018, false, listOf(8, 25, 32), listOf(1, 17), 12),
    )

    val HOSTS = listOf(
        Host(ACTIVE_PC, "GAMING-PC", online = true, paired = true, mac = "02:00:00:00:00:01", address = "192.0.2.11", running = RUNNING_ID, library = true),
        Host("preview-pc-living", "LIVING-ROOM-PC", online = false, paired = true, mac = "02:00:00:00:00:02", address = null),
        Host(
            "preview-pc-workstation", "Meiton's Workstation in the Upstairs Home Office with the Dual Monitor Desk Setup",
            online = true, paired = true, mac = null, address = "192.0.2.13",
        ),
        Host("preview-pc-attic", "ATTIC-PC", online = false, paired = true, mac = null, address = null),
        Host(STUDY_PC, "STUDY-LAPTOP", online = true, paired = false, mac = null, address = "192.0.2.14"),
    )

    /** More paired PCs than the three of [HOSTS], for the circle rows that scroll: [pairedPcs] in all (`--ei preview_pcs <n>`, [ScreenPreviews]); 0 leaves [HOSTS] alone. */
    var pairedPcs = 0

    private val MORE_NAMES = listOf("OFFICE-PC", "Bedroom", "STEAM-MACHINE", "Garage Workstation", "HTPC", "Dad's Laptop", "ROG-ALLY-DOCK", "Server", "Kids-PC")

    /** [HOSTS] and the extra paired PCs of [pairedPcs] (every second one asleep). */
    fun hosts(): List<Host> = HOSTS + List((pairedPcs - HOSTS.count { it.paired }).coerceAtLeast(0)) { i ->
        Host("preview-pc-more-$i", MORE_NAMES[i % MORE_NAMES.size] + if (i >= MORE_NAMES.size) " ${i / MORE_NAMES.size + 1}" else "", online = i % 2 == 0, paired = true,
            mac = "02:00:00:00:01:%02x".format(i), address = "192.0.2.${40 + i}")
    }

    fun host(uuid: String): Host = hosts().first { it.uuid == uuid }

    fun game(name: String): Game = GAMES.first { it.name == name }

    /** The service's [ComputerDetails] of [host]. */
    fun details(host: Host): ComputerDetails = ComputerDetails().also {
        it.uuid = host.uuid
        it.name = host.name
        it.state = if (host.online) ComputerDetails.State.ONLINE else ComputerDetails.State.OFFLINE
        // An offline PC has no pair state of its own (the service has none for a PC that has not answered); the repository remembers it
        it.pairState = if (!host.online) null else if (host.paired) PairingManager.PairState.PAIRED else PairingManager.PairState.NOT_PAIRED
        it.macAddress = host.mac
        it.runningGameId = host.running
        host.address?.let { address ->
            it.localAddress = ComputerDetails.AddressTuple(address, 47989)
            it.activeAddress = it.localAddress
        }
        it.httpsPort = 47984
        if (host.library) it.rawAppList = appList()
    }

    /** The `/applist` answer of the service: no whitespace between the tags, the parser reads it as text of the app before the first one. */
    private fun appList(): String = GAMES.joinToString(prefix = "<root status_code=\"200\">", postfix = "</root>", separator = "") {
        val name = it.name.replace("&", "&amp;").replace("<", "&lt;")
        "<App><AppTitle>$name</AppTitle><IsHdrSupported>${if (it.hdr) 1 else 0}</IsHdrSupported><ID>${it.id}</ID></App>"
    }

    fun pc(host: Host): Pc = details(host).let { Pc(host.uuid, host.name, host.name, host.online, host.paired, it.runningGameId, host.mac != null, it) }

    fun app(game: Game): App = App(game.id, game.name, game.hdr, game.id == RUNNING_ID, NvApp(game.name, "", game.id, game.hdr))

    fun profiles(): Profiles = Profiles(
        listOf(Profile("preview-profile-1", "Meiton", 2), Profile("preview-profile-2", "Ada", 5), Profile("preview-profile-3", "Nico", 9)),
        "preview-profile-1",
    )

    /**
     * Gives [repo] the PCs and the library (the service reports every PC repeatedly: the first report of the active PC makes the
     * repository drop the games it had and look for a cached list, the second one reads them) with what the profile keeps about the
     * active PC in [prefs]: the played order and last played game (`ui_played_apps_<pc>`, `ui_last_app_<pc>` of `PcRepository`), the
     * hidden games and the PC home shows.
     */
    fun seed(repo: PcRepository, prefs: SharedPreferences) {
        prefs.edit()
            .putString("ui_played_apps_$ACTIVE_PC", PLAYED_IDS.mapIndexed { i, id -> "$id:${NOW - i * 3_600_000L}" }.joinToString(","))
            .putInt("ui_last_app_$ACTIVE_PC", LAST_PLAYED_ID)
            .apply()
        repo.selectPc(ACTIVE_PC)
        HIDDEN_IDS.forEach { repo.setHidden(ACTIVE_PC, it, true) }
        // A paired PC that is offline was online once, which is where the repository learned that it is paired
        hosts().filter { !it.online && it.paired }.forEach { repo.onComputerUpdated(details(Host(it.uuid, it.name, true, true, it.mac, "192.0.2.99"))) }
        repeat(2) { hosts().forEach { repo.onComputerUpdated(details(it)) } }
    }

    fun candidate(game: Game) = MetadataCandidate(
        id = 5000L + game.id, name = game.name, year = game.year, steamAppIds = listOf(9000L + game.id),
        genres = game.genres.map { GameGenre(it, GENRES.getValue(it)) }, summary = "A fixed fake summary of ${game.name}.",
        platformIds = listOf(6), ratingCount = 100, themes = game.themes.map { GameGenre(it, THEMES.getValue(it)) },
    )
}

/** Debug builds only: the metadata providers of the preview, answering from [PreviewFixtures] (no network, no Worker). */
internal object PreviewMetadataProviders : MetadataProviders {
    override val proxyAvailable: Boolean = true

    private fun gameOf(candidate: MetadataCandidate) = PreviewFixtures.GAMES.first { 5000L + it.id == candidate.id }

    override fun search(query: String): List<MetadataCandidate> = PreviewFixtures.GAMES.filter { it.name == query }.map(PreviewFixtures::candidate)
    override fun game(id: Long): MetadataCandidate? = PreviewFixtures.GAMES.firstOrNull { 5000L + it.id == id }?.let(PreviewFixtures::candidate)
    override fun hdr(candidate: MetadataCandidate): HdrEvidence = HdrEvidence(if (gameOf(candidate).hdr) NativeHdr.HDR else NativeHdr.SDR)
    override fun completion(candidate: MetadataCandidate): CompletionTime =
        CompletionTime(candidate.id, candidate.name, gameOf(candidate).hours * 3600L, "https://preview.invalid/howlongtobeat/${candidate.id}")

    override fun steamTags(candidate: MetadataCandidate): List<String> =
        candidate.genres.map { it.name } + candidate.themes.map { it.name } + "Singleplayer"

    /** A full Worker game-details object for every preview game (fixed fake text; the image ids do not exist, so no picture loads). */
    override fun details(id: Long): String? {
        val game = PreviewFixtures.GAMES.firstOrNull { 5000L + it.id == id } ?: return null
        val released = java.util.Calendar.getInstance(java.util.TimeZone.getTimeZone("UTC")).apply {
            clear()
            set(game.year, java.util.Calendar.MARCH, 15)
        }.timeInMillis / 1000
        val slug = game.name.lowercase().replace("[^a-z0-9]+".toRegex(), "-").trim('-')
        val similar = PreviewFixtures.GAMES.filter { it.id != game.id }.let { others -> List(4) { others[(game.id + it * 3) % others.size] } }
        val details = mapOf(
            "id" to id, "name" to game.name, "slug" to slug,
            "summary" to "${game.name} is a fixed fake summary for the preview: a short paragraph about what the game is, where it " +
                "takes place and what the player does, long enough to wrap over a few lines.",
            "storyline" to "The first paragraph of a fake storyline sets the scene and introduces the hero.\n\n" +
                "A second paragraph raises the stakes: an old promise, a broken city and a road that leads further than planned.\n\n" +
                "The third paragraph stops before the ending, as storylines on IGDB usually do.",
            "firstReleaseDate" to released,
            "releaseDates" to listOf(
                mapOf("platform" to "PC", "date" to released, "human" to "Mar 15, ${game.year}"),
                mapOf("platform" to "PS5", "date" to released + 86_400L * 120, "human" to "Jul 13, ${game.year}"),
            ),
            "rating" to 70.0 + game.id * 2 % 25, "ratingCount" to 150 * game.id,
            "criticRating" to 75.5 + game.id % 20, "criticRatingCount" to 12 + game.id * 3,
            "totalRating" to 73.0 + game.id % 22, "totalRatingCount" to 162 * game.id,
            "genres" to game.genres.map { PreviewFixtures.GENRES.getValue(it) },
            "themes" to game.themes.map { PreviewFixtures.THEMES.getValue(it) },
            "modes" to listOf("Single player", "Co-operative"),
            "perspectives" to listOf("Third person"),
            "platforms" to listOf(
                mapOf("name" to "PC (Microsoft Windows)", "abbreviation" to "PC"),
                mapOf("name" to "PlayStation 5", "abbreviation" to "PS5"),
                mapOf("name" to "Xbox Series X|S"),
            ),
            "companies" to listOf(
                mapOf("name" to "Preview Studio ${game.id}", "developer" to true, "publisher" to false, "porting" to false, "supporting" to false),
                mapOf("name" to "Fixture Interactive", "developer" to false, "publisher" to true, "porting" to false, "supporting" to false),
                mapOf("name" to "Sample Ports", "developer" to false, "publisher" to false, "porting" to true, "supporting" to true),
            ),
            "ageRatings" to listOf(
                mapOf("organization" to "PEGI", "rating" to "16", "descriptors" to listOf("Violence", "Bad Language")),
                mapOf("organization" to "ESRB", "rating" to "M", "descriptors" to listOf("Blood and Gore", "Intense Violence")),
            ),
            "screenshots" to List(9) { mapOf("imageId" to "preview${game.id}sc$it", "width" to 1920, "height" to 1080) },
            "artworks" to List(2) { mapOf("imageId" to "preview${game.id}ar$it", "width" to 3840, "height" to 2160) },
            "videos" to listOf(
                mapOf("name" to "Launch Trailer", "videoId" to "preview${game.id}v0"),
                mapOf("name" to "Gameplay Overview", "videoId" to "preview${game.id}v1"),
            ),
            "websites" to listOf(
                mapOf("url" to "https://preview.invalid/$slug/", "type" to "Official Website"),
                mapOf("url" to "https://store.steampowered.com/app/${9000 + game.id}", "type" to "Steam"),
                mapOf("url" to "https://preview.invalid/wiki/$slug", "type" to "Community Wiki"),
            ),
            "similarGames" to similar.mapIndexed { index, other ->
                mapOf("id" to 5000L + other.id, "name" to other.name, "coverImageId" to if (index == 3) null else "preview${other.id}co")
            } + mapOf("id" to 4999L, "name" to "A Game Outside the Library", "coverImageId" to "previewoutsideco"),
            "franchises" to listOf("${game.name} Universe"), "collections" to listOf(game.name),
            "engines" to listOf("Preview Engine 4"),
            "keywords" to listOf("exploration", "boss fights", "crafting", "story rich", "atmospheric", "upgrades"),
            "languages" to listOf("English", "German", "French", "Spanish", "Japanese", "Czech"),
            "multiplayer" to mapOf(
                "onlineMax" to 4, "offlineMax" to 2, "onlineCoopMax" to 4, "offlineCoopMax" to 2,
                "onlineCoop" to true, "offlineCoop" to true, "lanCoop" to false, "campaignCoop" to true, "dropIn" to true, "splitscreen" to true,
            ),
            "externalGames" to listOf(mapOf("uid" to "${9000 + game.id}", "source" to 1)),
        )
        return com.google.gson.Gson().toJson(details)
    }
}

/**
 * Debug builds only: the SteamGridDB of the Artwork preview: the key is set (so the screen offers its choices), the game is matched,
 * the choices of each kind are fixed images whose thumbnails are drawn into the thumbnail cache of the sandbox (their addresses
 * never resolve), and nothing is looked up, downloaded or applied.
 */
internal class PreviewArtworkStore(private val sandbox: Context) : ArtworkStore(sandbox) {
    init {
        apiKey = "preview"
        automatic = false
    }

    override fun matched(pc: Pc, app: App): SgdbGame = SgdbGame(MATCH_ID, app.name, PreviewFixtures.GAMES.firstOrNull { it.id == app.id }?.year)

    override fun searched(pc: Pc, app: App): Boolean = true

    /** The second image of the kind is the one in use. */
    override fun chosen(pc: Pc, app: App, kind: ArtKind): Int? = images(kind)[1].id

    override suspend fun search(term: String): List<SgdbGame> = listOf(SgdbGame(MATCH_ID, term, 2020), SgdbGame(MATCH_ID + 1, "$term II", 2024))

    override suspend fun choices(pc: Pc, app: App, kind: ArtKind, filters: ArtFilters): List<SgdbImage> = withContext(Dispatchers.IO) {
        images(kind).also { seedThumbnails(kind, it, app.name) }
    }

    override suspend fun fetch(pc: Pc, app: App, refresh: Boolean): Boolean = false

    override suspend fun setGame(pc: Pc, app: App, game: SgdbGame) = Unit

    override suspend fun apply(pc: Pc, app: App, kind: ArtKind, image: SgdbImage?) = Unit

    private fun images(kind: ArtKind): List<SgdbImage> {
        val (width, height) = when (kind) {
            ArtKind.COVER -> 600 to 900
            ArtKind.WIDE -> 920 to 430
            ArtKind.HERO -> 1920 to 620
            ArtKind.LOGO -> 800 to 310
        }
        val count = when (kind) {
            ArtKind.COVER -> 15
            ArtKind.WIDE -> 12
            ArtKind.HERO -> 9
            ArtKind.LOGO -> 12
        }
        val styles = listOf("alternate", "blurred", "material", "white_logo", "no_logo")
        val authors = listOf("Ada", "Nico", "Mara", "Tomas")
        return (1..count).map { n ->
            val id = 100 * (kind.ordinal + 1) + n
            val url = "http://preview.invalid/${kind.file}/$id.png"
            SgdbImage(
                id, url, url, width, height, style = styles[n % styles.size], score = 40 - n, author = authors[n % authors.size],
                date = 1_650_000_000L + n * 86_400L, mime = "image/png",
            )
        }
    }

    /** Draws the thumbnails where the thumbnail cache looks for them (a file named by the SHA-1 of the address), so no download starts. */
    private fun seedThumbnails(kind: ArtKind, images: List<SgdbImage>, title: String) {
        val folder = File(sandbox.cacheDir, "sgdb-preview").apply { mkdirs() }
        val (w, h) = when (kind) {
            ArtKind.COVER -> 200 to 300
            ArtKind.WIDE -> 368 to 172
            ArtKind.HERO -> 480 to 155
            ArtKind.LOGO -> 320 to 124
        }
        images.forEach { image ->
            val file = File(folder, MessageDigest.getInstance("SHA-1").digest(image.previewUrl.toByteArray()).joinToString("") { b -> "%02x".format(b) })
            if (file.length() > 0L) return@forEach
            val bitmap = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
            val canvas = Canvas(bitmap)
            val paint = Paint(Paint.ANTI_ALIAS_FLAG)
            if (kind != ArtKind.LOGO) {
                val hue = (image.id * 47 % 360).toFloat()
                paint.shader = LinearGradient(
                    0f, 0f, w.toFloat(), h.toFloat(),
                    Color.HSVToColor(floatArrayOf(hue, .55f, .60f)), Color.HSVToColor(floatArrayOf((hue + 40f) % 360f, .65f, .22f)),
                    Shader.TileMode.CLAMP,
                )
                canvas.drawRect(0f, 0f, w.toFloat(), h.toFloat(), paint)
                paint.shader = null
                paint.color = Color.argb(46, 255, 255, 255)
                canvas.drawCircle(w * .78f, h * .3f, h * .32f, paint)
            }
            paint.color = Color.WHITE
            paint.textAlign = Paint.Align.CENTER
            paint.isFakeBoldText = true
            paint.textSize = if (kind == ArtKind.LOGO) h * .4f else h * .11f
            while (paint.measureText(title) > w * .86f && paint.textSize > 8f) paint.textSize -= 1f
            canvas.drawText(title, w / 2f, h * .55f, paint)
            file.outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 90, it) }
            bitmap.recycle()
        }
    }

    private companion object {
        const val MATCH_ID = 5_000
    }
}
