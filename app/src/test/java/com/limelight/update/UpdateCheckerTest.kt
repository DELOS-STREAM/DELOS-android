package com.limelight.update

import androidx.preference.PreferenceManager
import androidx.test.core.app.ApplicationProvider
import okhttp3.Request
import okhttp3.Response
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class UpdateCheckerTest {
    private val apk = "apk-bytes".toByteArray()
    private val api = UpdatePolicy.LATEST_RELEASE_URL
    private val store = UpdateStore(PreferenceManager.getDefaultSharedPreferences(ApplicationProvider.getApplicationContext()).also { it.edit().clear().commit() })
    private val logs = mutableListOf<String>()
    private var now = 1_800_000_000_000L
    private var latestTag = "v1.1.0"
    private var etag = "\"abc\""
    private var sums: String? = null
    private lateinit var web: FakeWeb

    @Before fun setUp() {
        sums = "${sha256Hex(apk)}  delos-1.1.0-arm64-v8a.apk\n${"0".repeat(64)}  delos-1.1.0-armeabi-v7a.apk\n"
        web = FakeWeb { request -> answer(request) }
    }

    private fun answer(request: Request): Response = when {
        request.url.toString() == api ->
            if (request.header("If-None-Match") == etag) FakeWeb.reply(request, 304)
            else FakeWeb.reply(request, 200, releaseJson(latestTag), "ETag" to etag)
        request.url.encodedPath.endsWith("SHA256SUMS.txt") -> sums?.let { FakeWeb.reply(request, 200, it) } ?: FakeWeb.reply(request, 404)
        else -> FakeWeb.reply(request, 404)
    }

    private fun checker(installed: String = "1.0.0", debug: Boolean = false, server: String? = null, abis: List<String> = listOf("arm64-v8a", "armeabi-v7a")): UpdateChecker {
        val policy = UpdatePolicy(debug, server)
        return UpdateChecker(UpdateHttp(web.client(), "Delos/$installed", policy.allowCleartext), store, policy, installed, abis, { now }, { logs += it })
    }

    @Test fun theSettingIsOnByDefault() {
        assertTrue(store.enabled)
    }

    @Test fun offIsNoRequestAtAll() {
        store.enabled = false
        assertEquals(CheckResult.Disabled, checker().check(force = false))
        assertEquals(CheckResult.Disabled, checker().check(force = true))
        assertTrue(web.requests.isEmpty())
    }

    @Test fun aNewerReleaseIsOfferedWithItsApkAndChecksum() {
        val result = checker().check(force = false) as CheckResult.Available
        val c = result.candidate
        assertEquals("v1.1.0", c.tag)
        assertEquals("delos-1.1.0-arm64-v8a.apk", c.apkName)
        assertEquals("https://github.com/Meitoncz/Delos/releases/download/v1.1.0/delos-1.1.0-arm64-v8a.apk", c.apkUrl)
        assertEquals(sha256Hex(apk), c.sha256)
        assertEquals("Added\n• Something new", c.notes)
        // The request names the app and asks for the GitHub JSON, over HTTPS, without credentials
        val first = web.requests.first()
        assertEquals("Delos/1.0.0", first.header("User-Agent"))
        assertEquals("application/vnd.github+json", first.header("Accept"))
        assertTrue(first.url.isHttps)
        assertNull(first.header("Authorization"))
    }

    @Test fun theSameOrAnOlderVersionIsNotOffered() {
        assertTrue(checker(installed = "1.1.0").check(force = false) is CheckResult.UpToDate)
        assertTrue(checker(installed = "1.2.0").check(force = true) is CheckResult.UpToDate)
        // A release candidate of the installed version is older than it, so it is never offered over the final release
        latestTag = "v1.1.0"
        assertTrue(checker(installed = "1.1.0+g1234567").check(force = true) is CheckResult.UpToDate)
        // and the release is offered over its own release candidate
        assertTrue(checker(installed = "1.1.0-rc.2").check(force = true) is CheckResult.Available)
    }

    @Test fun theChecksumListIsFetchedOnlyForANewerRelease() {
        checker(installed = "1.1.0").check(force = false)
        assertEquals(1, web.requests.size)
    }

    @Test fun withinTwentyFourHoursNothingIsRequested() {
        assertTrue(checker().check(force = false) is CheckResult.Available)
        val requests = web.requests.size
        now += 23 * 3_600_000L
        assertTrue(checker().check(force = false) is CheckResult.Available)
        assertEquals(requests, web.requests.size)
        // The installed version is compared again each time, so an update done in between ends the offer
        assertTrue(checker(installed = "1.1.0").check(force = false) is CheckResult.UpToDate)
        assertEquals(requests, web.requests.size)
    }

    @Test fun afterADayTheAnswerIsRevalidatedWithTheEtag() {
        checker().check(force = false)
        now += 25 * 3_600_000L
        val before = web.requests.size
        assertTrue(checker().check(force = false) is CheckResult.Available)
        val sent = web.requests.drop(before)
        assertEquals(1, sent.size)
        assertEquals("\"abc\"", sent[0].header("If-None-Match"))
        assertEquals(now, store.lastCheck)
    }

    @Test fun aChangedAnswerReplacesTheCache() {
        checker().check(force = false)
        etag = "\"def\""
        latestTag = "v1.2.0"
        sums = "${sha256Hex(apk)}  delos-1.2.0-arm64-v8a.apk\n"
        now += 25 * 3_600_000L
        val c = (checker().check(force = false) as CheckResult.Available).candidate
        assertEquals("v1.2.0", c.tag)
        // The stale ETag was sent, did not match, and the new answer is cached with its own
        assertEquals("\"abc\"", web.requests.last { it.url.toString() == api }.header("If-None-Match"))
        assertEquals("\"def\"", store.etag)
    }

    @Test fun checkNowAsksAgainWhateverTheLastCheckWas() {
        checker().check(force = false)
        val before = web.requests.size
        checker().check(force = true)
        assertEquals(before + 1, web.requests.size)
    }

    @Test fun noChecksumLineMeansNoOffer() {
        sums = "${"1".repeat(64)}  delos-1.1.0-armeabi-v7a.apk\n"
        val result = checker().check(force = false)
        assertEquals("v1.1.0", (result as CheckResult.NoDownload).tag)
        assertTrue(logs.joinToString(), logs.any { "SHA256SUMS.txt has no line" in it })
    }

    @Test fun noChecksumFileMeansNoOffer() {
        sums = null
        assertTrue(checker().check(force = false) is CheckResult.Failed)
        // A failed lookup is not remembered: the next check asks again
        assertEquals(0L, store.lastCheck)
        assertNull(store.cached)
    }

    @Test fun noApkForTheDeviceMeansNoOffer() {
        val result = checker(abis = listOf("x86_64")).check(force = false)
        assertTrue(result is CheckResult.NoDownload)
        assertTrue(logs.joinToString(), logs.any { "no APK for x86_64" in it })
    }

    @Test fun anApkOverTheSizeCapIsNotOffered() {
        web.handler = { r ->
            if (r.url.toString() == api) FakeWeb.reply(r, 200, releaseJson("v1.1.0", assets = listOf(
                Triple("delos-1.1.0-arm64-v8a.apk", "https://x.test/a.apk", UpdateHttp.MAX_APK_BYTES + 1),
                Triple("SHA256SUMS.txt", "https://x.test/SHA256SUMS.txt", 10),
            ))) else answer(r)
        }
        assertTrue(checker().check(force = false) is CheckResult.NoDownload)
    }

    @Test fun aTagThatIsNotAVersionIsIgnored() {
        latestTag = "nightly"
        assertTrue(checker().check(force = false) is CheckResult.UpToDate)
        assertTrue(logs.joinToString(), logs.any { "ignored" in it })
    }

    @Test fun networkAndRateLimitErrorsAreReported() {
        web.handler = { throw java.io.IOException("offline") }
        assertEquals(UpdateErrorKind.NETWORK, (checker().check(force = false) as CheckResult.Failed).kind)
        web.handler = { r -> FakeWeb.reply(r, 403, "{}", "X-RateLimit-Remaining" to "0") }
        assertEquals(UpdateErrorKind.RATE_LIMITED, (checker().check(force = false) as CheckResult.Failed).kind)
        web.handler = { r -> FakeWeb.reply(r, 500) }
        assertEquals(UpdateErrorKind.HTTP_STATUS, (checker().check(force = false) as CheckResult.Failed).kind)
        web.handler = { r -> FakeWeb.reply(r, 200, "<html>") }
        assertEquals(UpdateErrorKind.BAD_ANSWER, (checker().check(force = false) as CheckResult.Failed).kind)
        assertEquals(0L, store.lastCheck)
    }

    @Test fun aDebugBuildOffersNothingAndAsksNothing() {
        assertEquals(CheckResult.DebugBuild, checker(debug = true).check(force = true))
        assertTrue(web.requests.isEmpty())
    }

    @Test fun aDebugBuildWithATestServerAsksOnlyThatServer() {
        val server = "http://10.0.2.2:8080/latest"
        web.handler = { r -> if (r.url.toString() == server) FakeWeb.reply(r, 200, releaseJson("v1.1.0")) else answer(r) }
        assertTrue(checker(debug = true, server = server).check(force = true) is CheckResult.Available)
        assertEquals(server, web.requests.first().url.toString())
        // A release build ignores a stored test server
        web.requests.clear()
        checker(debug = false, server = server).check(force = true)
        assertEquals(api, web.requests.first().url.toString())
    }

    @Test fun aCleartextTestServerIsRefusedWithoutTheDebugOverride() {
        val policy = UpdatePolicy(debugBuild = false, debugServer = "http://10.0.2.2:8080/latest")
        assertFalse(policy.allowCleartext)
        assertEquals(api, policy.url)
    }
}
