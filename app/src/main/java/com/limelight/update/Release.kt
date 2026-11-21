package com.limelight.update

import com.google.gson.JsonObject
import com.google.gson.JsonParser

/** One file attached to a GitHub release: its [name], the HTTPS download [url] and its [size] in bytes. */
data class ReleaseAsset(val name: String, val url: String, val size: Long)

/** The parts of a GitHub release the updater reads: the tag and its [version], the release notes (Markdown) and the assets. */
data class Release(val tag: String, val version: SemVer, val notes: String, val assets: List<ReleaseAsset>)

/** What [ReleaseParser] made of an answer. */
sealed interface ParsedRelease {
    class Usable(val release: Release) : ParsedRelease

    /** A valid answer that is not offered (a draft, a pre-release, a tag that is no version); [reason] is for the log. */
    class Ignored(val reason: String) : ParsedRelease

    /** The text is not the JSON object of a release. */
    object Malformed : ParsedRelease
}

/** Reads the answer of `GET /repos/{owner}/{repo}/releases/latest` (docs/UPDATES.md). */
object ReleaseParser {
    /**
     * The release in [json]. A draft or pre-release (the endpoint never returns them; this is a second check) and a tag that is not
     * `vMAJOR.MINOR.PATCH[-pre]` are [ParsedRelease.Ignored]. An asset without a name, an HTTPS URL or a size is left out.
     */
    fun parse(json: String): ParsedRelease {
        val root = runCatching { JsonParser.parseString(json) }.getOrNull()?.takeIf { it.isJsonObject }?.asJsonObject ?: return ParsedRelease.Malformed
        if (root.bool("draft")) return ParsedRelease.Ignored("the release is a draft")
        if (root.bool("prerelease")) return ParsedRelease.Ignored("the release is a pre-release")
        val tag = root.text("tag_name") ?: return ParsedRelease.Ignored("the release has no tag")
        val version = SemVer.parseTag(tag) ?: return ParsedRelease.Ignored("the tag '${tag.take(40)}' is not a version")
        val assets = root.get("assets")?.takeIf { it.isJsonArray }?.asJsonArray?.toList().orEmpty().mapNotNull { element ->
            val asset = element.takeIf { it.isJsonObject }?.asJsonObject ?: return@mapNotNull null
            val name = asset.text("name") ?: return@mapNotNull null
            val url = asset.text("browser_download_url")?.takeIf { it.startsWith("https://") } ?: return@mapNotNull null
            val size = asset.get("size")?.takeIf { it.isJsonPrimitive && it.asJsonPrimitive.isNumber }?.asLong ?: return@mapNotNull null
            ReleaseAsset(name, url, size)
        }
        return ParsedRelease.Usable(Release(tag, version, root.text("body").orEmpty(), assets))
    }

    private fun JsonObject.bool(name: String): Boolean = get(name)?.takeIf { it.isJsonPrimitive && it.asJsonPrimitive.isBoolean }?.asBoolean ?: false

    private fun JsonObject.text(name: String): String? = get(name)?.takeIf { it.isJsonPrimitive && it.asJsonPrimitive.isString }?.asString?.takeIf { it.isNotBlank() }
}

/** The first lines of release notes as plain text, for the offer card. */
object ReleaseNotes {
    const val MAX_LINES = 3
    const val MAX_LINE_CHARS = 160

    private val comment = Regex("<!--.*?-->", RegexOption.DOT_MATCHES_ALL)
    private val link = Regex("""!?\[([^\]]*)]\([^)]*\)""")
    private val heading = Regex("""^#{1,6}\s*""")
    private val bullet = Regex("""^[-*+]\s+""")
    private val emphasis = Regex("""(?<![\w*_])([*_])(?![\s*_])(.+?)(?<![\s*_])\1(?![\w*_])""")

    /** The first [MAX_LINES] non-empty lines of [markdown] without Markdown marks, each cut at [MAX_LINE_CHARS]; empty when there are none. */
    fun summary(markdown: String): String = markdown.replace(comment, "").lines().asSequence()
        .map { plain(it) }.filter { it.isNotEmpty() }.take(MAX_LINES)
        .map { if (it.length > MAX_LINE_CHARS) it.take(MAX_LINE_CHARS - 1).trimEnd() + "…" else it }
        .joinToString("\n")

    private fun plain(line: String): String = line.trim().replace(heading, "").replace(bullet, "• ")
        .replace(link, "$1").replace("**", "").replace("__", "").replace("`", "").replace(emphasis, "$2").trim()
}
