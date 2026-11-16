package com.limelight.update

import java.io.IOException

/** Where the updater asks. A debug build never offers an update (the release APK is another app) unless [debugServer] points at a test server. */
class UpdatePolicy(val debugBuild: Boolean, val debugServer: String? = null) {
    val url: String get() = if (debugBuild && debugServer != null) debugServer else LATEST_RELEASE_URL
    val offersUpdates: Boolean get() = !debugBuild || debugServer != null

    /** Only a debug build with a test server may use plain HTTP, for that server. */
    val allowCleartext: Boolean get() = debugBuild && debugServer != null

    companion object {
        const val LATEST_RELEASE_URL = "https://api.github.com/repos/DELOS-STREAM/DELOS-android/releases/latest"
    }
}

/** The outcome of a check. */
sealed interface CheckResult {
    /** "Check for updates" is off: nothing was requested. */
    object Disabled : CheckResult

    /** A debug build without a test server: nothing was requested. */
    object DebugBuild : CheckResult

    /** The latest release is not newer than the installed version. */
    class UpToDate(val latest: String?) : CheckResult

    class Available(val candidate: UpdateCandidate) : CheckResult

    /** A newer version [tag] exists but has no download for this device or no checksum, so it is not offered. */
    class NoDownload(val tag: String) : CheckResult

    class Failed(val kind: UpdateErrorKind) : CheckResult
}

/**
 * The check (docs/UPDATES.md "Schedule"): at most one request a day unless [check] is forced, `If-None-Match` so an unchanged
 * answer costs no quota, and a newer release is only offered with its APK for this device and the SHA-256 from the same
 * release. Blocking; call it off the main thread. [log] receives developer-readable lines for `DiagLog`.
 */
class UpdateChecker(
    private val http: UpdateHttp,
    private val store: UpdateStore,
    private val policy: UpdatePolicy,
    private val installedVersion: String,
    private val abis: List<String>,
    private val clock: () -> Long,
    private val log: (String) -> Unit,
) {
    /** [force] skips the 24-hour wait (the Check now row); it never skips the setting. */
    fun check(force: Boolean): CheckResult {
        if (!store.enabled) return CheckResult.Disabled
        if (!policy.offersUpdates) return CheckResult.DebugBuild
        val installed = SemVer.parse(installedVersion) ?: return CheckResult.Failed(UpdateErrorKind.BAD_ANSWER).also { log("installed version '$installedVersion' is not a version") }
        val now = clock()
        val cached = store.cached?.takeIf { it.usableFor(installed) }
        if (!force && cached != null && !UpdateSchedule.checkDue(now, store.lastCheck)) return evaluate(cached, installed)
        return try {
            when (val answer = http.latest(policy.url, if (cached != null) store.etag else null)) {
                Latest.NotModified -> {
                    // A 304 is only sent for the ETag of the cached answer, so [cached] is there; the guard is for a cache wiped in between
                    val known = cached ?: return CheckResult.Failed(UpdateErrorKind.BAD_ANSWER)
                    store.saveAnswer(store.etag, known, now)
                    log("latest release unchanged (${known.tag})")
                    evaluate(known, installed)
                }
                is Latest.Body -> when (val parsed = ReleaseParser.parse(answer.text)) {
                    ParsedRelease.Malformed -> {
                        log("the answer is not a release")
                        CheckResult.Failed(UpdateErrorKind.BAD_ANSWER)
                    }
                    is ParsedRelease.Ignored -> {
                        log("latest release ignored: ${parsed.reason}")
                        CheckResult.UpToDate(null)
                    }
                    is ParsedRelease.Usable -> {
                        val latest = resolve(parsed.release, installed)
                        store.saveAnswer(answer.etag, latest, now)
                        log("latest release ${latest.tag}, installed $installedVersion")
                        evaluate(latest, installed)
                    }
                }
            }
        } catch (e: UpdateException) {
            log("check failed: ${e.kind} ${e.message}")
            CheckResult.Failed(e.kind)
        } catch (e: IOException) {
            log("check failed: ${e.javaClass.simpleName}")
            CheckResult.Failed(UpdateErrorKind.NETWORK)
        }
    }

    /** Fetches the checksum list only for a release that is newer and has the APK; everything else needs no second request. */
    private fun resolve(release: Release, installed: SemVer): CachedLatest {
        if (release.version <= installed) return CachedLatest(release.tag, null, resolved = false)
        val pick = ReleaseAssets.select(release, abis)
        if (pick is ReleaseAssets.Pick.Missing) {
            log("no offer: ${pick.reason}")
            return CachedLatest(release.tag, null)
        }
        pick as ReleaseAssets.Pick.Found
        if (pick.apk.size <= 0 || pick.apk.size > UpdateHttp.MAX_APK_BYTES) {
            log("no offer: ${pick.apk.name} is ${pick.apk.size} bytes")
            return CachedLatest(release.tag, null)
        }
        val sha = Checksums.parse(http.text(pick.checksums.url))[pick.apk.name]
        if (sha == null) {
            log("no offer: ${ReleaseAssets.CHECKSUMS} has no line for ${pick.apk.name}")
            return CachedLatest(release.tag, null)
        }
        return CachedLatest(release.tag, UpdateCandidate(release.tag, release.version, ReleaseNotes.summary(release.notes), pick.apk.name, pick.apk.url, pick.apk.size, sha))
    }

    private fun evaluate(latest: CachedLatest, installed: SemVer): CheckResult {
        val version = SemVer.parseTag(latest.tag) ?: return CheckResult.UpToDate(null)
        if (version <= installed) return CheckResult.UpToDate(latest.tag)
        return latest.candidate?.let { CheckResult.Available(it) } ?: CheckResult.NoDownload(latest.tag)
    }
}
