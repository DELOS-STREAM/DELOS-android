package com.limelight.shell.data

import androidx.compose.runtime.Immutable

@Immutable
data class GameGenre(val id: Int, val name: String)

/** IGDB's Windows/PC platform ID. */
private const val IGDB_PC_PLATFORM = 6

@Immutable
data class MetadataCandidate(
    val id: Long,
    val name: String,
    val year: Int?,
    /** Every distinct Steam UID IGDB lists for this game; several mean base game plus bundle/edition entries. */
    val steamAppIds: List<Long> = emptyList(),
    val genres: List<GameGenre> = emptyList(),
    val summary: String? = null,
    /** IGDB platform IDs; empty means IGDB did not list any. */
    val platformIds: List<Int> = emptyList(),
    /** IGDB's count of user and critic ratings; the only popularity signal used to break an otherwise exact tie. */
    val ratingCount: Int = 0,
    /** IGDB themes (Fantasy, Horror, ...), same shape as [genres]; empty when IGDB lists none. */
    val themes: List<GameGenre> = emptyList(),
) {
    val isPc: Boolean get() = IGDB_PC_PLATFORM in platformIds
}

enum class NativeHdr { HDR, SDR, UNKNOWN }

@Immutable
data class HdrEvidence(val support: NativeHdr, val page: String? = null, val notes: String? = null)

/** HLTB's comp_all ([seconds]), not Main Story or a detailed-table arithmetic average. */
@Immutable
data class CompletionTime(val id: Long, val name: String, val seconds: Long, val url: String)

@Immutable
data class GameMetadata(
    val candidate: MetadataCandidate? = null,
    val hdr: HdrEvidence = HdrEvidence(NativeHdr.UNKNOWN),
    val completion: CompletionTime? = null,
    /** Last time every provider answered (or, for an unmatched game, the last answered IGDB search); 0 after a failure. */
    val updatedAt: Long = 0,
    val manual: Boolean = false,
    /** When the provider last failed, 0 if its last request succeeded. Drives the per-provider retry backoff. */
    val igdbFailedAt: Long = 0,
    val pcgwFailedAt: Long = 0,
    val hltbFailedAt: Long = 0,
    val steamFailedAt: Long = 0,
    /** The game's Steam store user tags, most voted first; empty when it has no Steam id or the store listed none. */
    val steamTags: List<String> = emptyList(),
)

/** Why a game's information is missing or stale; the screens turn it into a sentence. */
enum class MetadataProblem { UNAVAILABLE, UPDATE_FAILED, PARTIAL }

@Immutable
data class GameMetadataState(
    val data: GameMetadata? = null,
    val loading: Boolean = false,
    val resolved: Boolean = false,
    val error: MetadataProblem? = null,
)

enum class HdrFilter { HDR, SDR, UNKNOWN }
enum class LengthFilter { UNDER_10, FROM_10_TO_30, FROM_30_TO_60, OVER_60, UNKNOWN }

/**
 * The library filter: each row (genres, themes, HDR support, length) keeps the values chosen in it. A game matches a row with any of
 * its values (an empty row matches every game) and the filter when it matches every row.
 */
@Immutable
data class MetadataFilters(
    val genres: Set<Int> = emptySet(),
    val themes: Set<Int> = emptySet(),
    val hdr: Set<HdrFilter> = emptySet(),
    val length: Set<LengthFilter> = emptySet(),
) {
    val active: Boolean get() = activeCount > 0

    /** How many values narrow the library, over all rows (two genres and HDR: 3). */
    val activeCount: Int get() = genres.size + themes.size + hdr.size + length.size

    fun toggleGenre(id: Int) = copy(genres = genres.toggle(id))
    fun toggleTheme(id: Int) = copy(themes = themes.toggle(id))
    fun toggleHdr(value: HdrFilter) = copy(hdr = hdr.toggle(value))
    fun toggleLength(value: LengthFilter) = copy(length = length.toggle(value))

    fun matches(app: App, metadata: GameMetadata?): Boolean {
        if (BundledArtwork.kind(app) != null) return !active
        // Any of the game's genres and themes counts, not only the main ones the home screen shows
        if (genres.isNotEmpty() && metadata?.candidate?.genres?.none { it.id in genres } != false) return false
        if (themes.isNotEmpty() && metadata?.candidate?.themes?.none { it.id in themes } != false) return false
        val support = metadata?.hdr?.support ?: NativeHdr.UNKNOWN
        if (hdr.isNotEmpty() && hdr.none { it.name == support.name }) return false
        val seconds = metadata?.completion?.seconds?.takeIf { it > 0 }
        return length.isEmpty() || length.any { it.matches(seconds) }
    }

    private fun LengthFilter.matches(seconds: Long?): Boolean = when (this) {
        LengthFilter.UNKNOWN -> seconds == null
        LengthFilter.UNDER_10 -> seconds != null && seconds < 10 * 3600L
        LengthFilter.FROM_10_TO_30 -> seconds != null && seconds >= 10 * 3600L && seconds < 30 * 3600L
        LengthFilter.FROM_30_TO_60 -> seconds != null && seconds >= 30 * 3600L && seconds < 60 * 3600L
        LengthFilter.OVER_60 -> seconds != null && seconds >= 60 * 3600L
    }

    private fun <T> Set<T>.toggle(value: T): Set<T> = if (value in this) this - value else this + value
}
