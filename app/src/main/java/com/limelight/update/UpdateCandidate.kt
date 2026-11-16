package com.limelight.update

import com.google.gson.JsonObject
import com.google.gson.JsonParser

/** A newer release that the updater can install: the [apkName] at [apkUrl] ([apkSize] bytes) whose SHA-256 is [sha256] (lower-case hex). */
data class UpdateCandidate(
    val tag: String,
    val version: SemVer,
    val notes: String,
    val apkName: String,
    val apkUrl: String,
    val apkSize: Long,
    val sha256: String,
)

/**
 * What the last answer of GitHub said, kept so that a `304 Not Modified` and the checks within the 24 hours need no request:
 * the [tag] of the latest release and, when it carries a usable download, its [candidate]. Whether the tag is newer than the
 * installed version is decided each time, because the installed version changes with an update.
 */
data class CachedLatest(val tag: String, val candidate: UpdateCandidate?, val resolved: Boolean = true) {
    /**
     * Whether this answer can stand in for a request when [installed] is the installed version. The assets and checksum of a release are
     * only looked up when it is newer than what was installed at the time ([resolved] is false when they were not), so an answer
     * that was not resolved is of no use once the installed version is older than the tag.
     */
    fun usableFor(installed: SemVer): Boolean = resolved || (SemVer.parseTag(tag)?.let { it <= installed } ?: true)

    fun toJson(): String = JsonObject().apply {
        addProperty("tag", tag)
        addProperty("resolved", resolved)
        candidate?.let {
            add("candidate", JsonObject().apply {
                addProperty("notes", it.notes)
                addProperty("apkName", it.apkName)
                addProperty("apkUrl", it.apkUrl)
                addProperty("apkSize", it.apkSize)
                addProperty("sha256", it.sha256)
            })
        }
    }.toString()

    companion object {
        /** The cache written by [toJson]; null for anything unreadable, which makes the next check ask GitHub again. */
        fun fromJson(json: String?): CachedLatest? {
            val root = json?.let { runCatching { JsonParser.parseString(it) }.getOrNull() }?.takeIf { it.isJsonObject }?.asJsonObject ?: return null
            val tag = root.get("tag")?.takeIf { it.isJsonPrimitive }?.asString ?: return null
            val version = SemVer.parseTag(tag) ?: return null
            val candidate = root.get("candidate")?.takeIf { it.isJsonObject }?.asJsonObject?.let { c ->
                fun text(name: String) = c.get(name)?.takeIf { it.isJsonPrimitive }?.asString
                UpdateCandidate(
                    tag, version, text("notes").orEmpty(), text("apkName") ?: return null, text("apkUrl") ?: return null,
                    text("apkSize")?.toLongOrNull() ?: return null, text("sha256") ?: return null,
                )
            }
            return CachedLatest(tag, candidate, root.get("resolved")?.takeIf { it.isJsonPrimitive }?.asBoolean ?: true)
        }
    }
}
