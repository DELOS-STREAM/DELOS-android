package com.limelight.update

import androidx.preference.PreferenceManager
import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import okhttp3.Request
import okhttp3.Response
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File
import java.io.IOException
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

private class FakeInstaller : ApkInstaller {
    var allowed = true
    var opened = 0
    val installed = mutableListOf<ByteArray>()
    var failWith: IOException? = null
    override fun canInstall() = allowed
    override fun openPermissionSettings() { opened++ }
    override fun install(apk: File) {
        failWith?.let { throw it }
        installed += apk.readBytes()
    }
}

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class UpdateControllerTest {
    @get:Rule val tmp = TemporaryFolder()

    private val apk = ByteArray(3000) { (it * 7).toByte() }
    private val api = UpdatePolicy.LATEST_RELEASE_URL
    private val store = UpdateStore(PreferenceManager.getDefaultSharedPreferences(ApplicationProvider.getApplicationContext()).also { it.edit().clear().commit() })
    private val installer = FakeInstaller()
    private val logs = mutableListOf<String>()
    private var now = 1_800_000_000_000L
    private var latestTag = "v1.1.0"
    private var served = apk
    private var announcedSum = sha256Hex(apk)
    private lateinit var web: FakeWeb
    private lateinit var cache: File
    private lateinit var controller: UpdateController

    @Before fun setUp() {
        cache = tmp.newFolder("cache")
        web = FakeWeb { r -> answer(r) }
        build()
    }

    private fun answer(r: Request): Response = when {
        r.url.toString() == api -> FakeWeb.reply(r, 200, releaseJson(latestTag), "ETag" to "\"e$latestTag\"")
        r.url.encodedPath.endsWith("SHA256SUMS.txt") -> FakeWeb.reply(r, 200, "$announcedSum  delos-${latestTag.removePrefix("v")}-arm64-v8a.apk\n")
        r.url.encodedPath.endsWith(".apk") -> FakeWeb.replyBytes(r, served)
        else -> FakeWeb.reply(r, 404)
    }

    private fun build(installed: String = "1.0.0") {
        val policy = UpdatePolicy(false)
        val http = { UpdateHttp(web.client(), "Delos/$installed") }
        controller = UpdateController(
            store, { UpdateChecker(http(), store, policy, installed, listOf("arm64-v8a"), { now }, { logs += it }) }, http, installer, cache,
            CoroutineScope(Dispatchers.Unconfined), Dispatchers.Unconfined, { now }, { logs += it },
        )
    }

    private fun offered(): UpdateCandidate = (controller.state.value as UpdateState.Offer).candidate

    @Test fun startsIdleAndTheBackgroundCheckOffersANewerVersion() = runBlocking {
        assertEquals(UpdateState.Idle, controller.state.value)
        controller.checkInBackground().join()
        assertEquals("v1.1.0", offered().tag)
    }

    @Test fun nothingNewStaysIdleAndSilent() = runBlocking {
        build(installed = "1.1.0")
        controller.checkInBackground().join()
        assertEquals(UpdateState.Idle, controller.state.value)
    }

    @Test fun offIsSilentAndMakesNoRequest() = runBlocking {
        store.enabled = false
        controller.checkInBackground().join()
        assertEquals(UpdateState.Idle, controller.state.value)
        assertTrue(web.requests.isEmpty())
    }

    @Test fun aFailedBackgroundCheckIsSilentButLogged() = runBlocking {
        web.handler = { throw IOException("offline") }
        controller.checkInBackground().join()
        assertEquals(UpdateState.Idle, controller.state.value)
        assertTrue(logs.joinToString(), logs.any { "check failed" in it })
    }

    @Test fun laterHidesTheVersionForThreeDaysAndANewerOneShowsAtOnce() = runBlocking {
        controller.checkInBackground().join()
        controller.later()
        assertEquals(UpdateState.Idle, controller.state.value)

        // A day later the check runs again and finds the same version: still hidden
        now += 26 * 3_600_000L
        controller.checkInBackground().join()
        assertEquals(UpdateState.Idle, controller.state.value)

        // Another day later a newer version is out: it shows at once
        latestTag = "v1.2.0"
        now += 26 * 3_600_000L
        controller.checkInBackground().join()
        assertEquals("v1.2.0", offered().tag)
    }

    @Test fun laterEndsAfterThreeDays() = runBlocking {
        controller.checkInBackground().join()
        controller.later()
        now += UpdateSchedule.LATER_MS + 1
        controller.checkInBackground().join()
        assertEquals("v1.1.0", offered().tag)
    }

    @Test fun checkNowShowsTheOfferEvenAfterLater() = runBlocking {
        controller.checkInBackground().join()
        controller.later()
        val result = controller.checkNow()
        assertTrue(result is CheckResult.Available)
        assertEquals("v1.1.0", offered().tag)
    }

    @Test fun onlyAnOfferFromCheckNowIsRequested() = runBlocking {
        controller.checkInBackground().join()
        assertFalse(controller.requested.value)
        controller.checkNow()
        assertTrue(controller.requested.value)
        controller.later()
        assertFalse(controller.requested.value)
    }

    @Test fun updateDownloadsVerifiesAndOnlyThenInstalls() = runBlocking {
        controller.checkInBackground().join()
        controller.accept()!!.join()
        val verified = controller.state.value as UpdateState.Verified
        assertEquals(apk.toList(), verified.file.readBytes().toList())
        assertTrue(installer.installed.isEmpty())
        assertEquals("delos-1.1.0-arm64-v8a.apk", verified.file.name)
        assertEquals(File(cache, "updates"), verified.file.parentFile)

        controller.installVerified()!!.join()
        assertTrue(controller.state.value is UpdateState.Installing)
        assertEquals(listOf(apk.toList()), installer.installed.map { it.toList() })

        controller.onInstallResult(InstallOutcome.SUCCESS)
        assertEquals(UpdateState.Idle, controller.state.value)
        assertFalse(File(cache, "updates").exists())
    }

    @Test fun aChecksumMismatchDeletesTheFileAndNeverInstalls() = runBlocking {
        served = ByteArray(3000) { 1 }
        controller.checkInBackground().join()
        controller.accept()!!.join()
        val failed = controller.state.value as UpdateState.Failed
        assertEquals(UpdateFailure.CHECKSUM, failed.failure)
        assertFalse(File(cache, "updates/delos-1.1.0-arm64-v8a.apk").exists())
        assertNull(controller.installVerified())
        assertTrue(installer.installed.isEmpty())
        assertTrue(logs.joinToString(), logs.any { "MISMATCH" in it })
    }

    @Test fun withoutTheInstallPermissionTheUserIsSentToTheSystemPage() = runBlocking {
        installer.allowed = false
        controller.checkInBackground().join()
        assertNull(controller.accept())
        assertTrue(controller.state.value is UpdateState.NeedsPermission)
        assertTrue(web.requests.none { it.url.encodedPath.endsWith(".apk") })
        controller.openPermissionSettings()
        assertEquals(1, installer.opened)

        // Back in the app and still not allowed: nothing happens
        assertNull(controller.onResume())
        assertTrue(controller.state.value is UpdateState.NeedsPermission)

        // Allowed on the settings page: the update goes on by itself
        installer.allowed = true
        controller.onResume()!!.join()
        assertTrue(controller.state.value is UpdateState.Verified)
    }

    @Test fun cancelStopsTheDownloadDeletesTheFileAndReturnsToTheOffer() {
        val started = CountDownLatch(1)
        val release = CountDownLatch(1)
        web.handler = { r ->
            if (r.url.encodedPath.endsWith(".apk")) {
                started.countDown()
                release.await(10, TimeUnit.SECONDS)
                FakeWeb.replyBytes(r, apk)
            } else answer(r)
        }
        val scope = CoroutineScope(Dispatchers.Default)
        val policy = UpdatePolicy(false)
        val http = { UpdateHttp(web.client(), "Delos/1.0.0") }
        controller = UpdateController(store, { UpdateChecker(http(), store, policy, "1.0.0", listOf("arm64-v8a"), { now }, { logs += it }) }, http, installer, cache, scope, Dispatchers.IO, { now }, { logs += it })
        runBlocking { controller.checkInBackground().join() }
        val job = controller.accept()!!
        assertTrue(started.await(10, TimeUnit.SECONDS))
        assertTrue(controller.state.value is UpdateState.Downloading)
        controller.cancel()
        release.countDown()
        runBlocking { job.join() }
        assertTrue(controller.state.value.toString(), controller.state.value is UpdateState.Offer)
        assertFalse(File(cache, "updates/delos-1.1.0-arm64-v8a.apk").exists())
        assertTrue(installer.installed.isEmpty())
    }

    @Test fun aBrokenDownloadIsAFailureTheUserCanRetry() = runBlocking {
        web.handler = { r -> if (r.url.encodedPath.endsWith(".apk")) FakeWeb.reply(r, 500) else answer(r) }
        controller.checkInBackground().join()
        controller.accept()!!.join()
        assertEquals(UpdateFailure.DOWNLOAD, (controller.state.value as UpdateState.Failed).failure)
        web.handler = { r -> answer(r) }
        controller.accept()!!.join()
        assertTrue(controller.state.value is UpdateState.Verified)
    }

    @Test fun theSystemInstallerOutcomesMapToStates() = runBlocking {
        suspend fun reachInstalling() {
            build()
            controller.checkInBackground().join()
            controller.accept()!!.join()
            controller.installVerified()!!.join()
        }
        reachInstalling()
        controller.onInstallResult(InstallOutcome.ABORTED)
        assertEquals("v1.1.0", offered().tag)
        reachInstalling()
        controller.onInstallResult(InstallOutcome.SIGNATURE)
        assertEquals(UpdateFailure.SIGNATURE, (controller.state.value as UpdateState.Failed).failure)
        reachInstalling()
        controller.onInstallResult(InstallOutcome.FAILED)
        assertEquals(UpdateFailure.INSTALL, (controller.state.value as UpdateState.Failed).failure)
    }

    @Test fun anInstallResultOutsideAnInstallIsIgnored() {
        controller.onInstallResult(InstallOutcome.SUCCESS)
        assertEquals(UpdateState.Idle, controller.state.value)
    }

    @Test fun aSessionThatCannotBeCreatedFails() = runBlocking {
        installer.failWith = IOException("no session")
        controller.checkInBackground().join()
        controller.accept()!!.join()
        controller.installVerified()!!.join()
        assertEquals(UpdateFailure.INSTALL, (controller.state.value as UpdateState.Failed).failure)
        assertFalse(File(cache, "updates/delos-1.1.0-arm64-v8a.apk").exists())
    }

    @Test fun aCheckDoesNotInterruptADownloadInProgress() = runBlocking {
        controller.checkInBackground().join()
        controller.accept()!!.join()
        assertTrue(controller.state.value is UpdateState.Verified)
        controller.checkNow()
        assertTrue(controller.state.value is UpdateState.Verified)
        controller.later()
        assertTrue(controller.state.value is UpdateState.Verified)
    }

    @Test fun cleanCacheRemovesOnlyTheUpdatesFolder() {
        File(cache, "updates").mkdirs()
        File(cache, "updates/old.apk").writeText("x")
        File(cache, "artwork.png").writeText("y")
        controller.cleanCache()
        assertFalse(File(cache, "updates").exists())
        assertTrue(File(cache, "artwork.png").exists())
    }

    @Test fun installOutcomesFromTheSystemStatus() {
        assertEquals(InstallOutcome.SUCCESS, InstallOutcome.of(0, null))
        assertEquals(InstallOutcome.ABORTED, InstallOutcome.of(3, "INSTALL_FAILED_ABORTED: User rejected permissions"))
        assertEquals(InstallOutcome.SIGNATURE, InstallOutcome.of(6, "INSTALL_FAILED_UPDATE_INCOMPATIBLE: Existing package com.limelight signatures do not match newer version; ignoring!"))
        assertEquals(InstallOutcome.SIGNATURE, InstallOutcome.of(4, "INSTALL_FAILED_UPDATE_INCOMPATIBLE: x"))
        assertEquals(InstallOutcome.FAILED, InstallOutcome.of(4, "INSTALL_FAILED_INSUFFICIENT_STORAGE"))
        assertEquals(InstallOutcome.FAILED, InstallOutcome.of(7, "INSTALL_FAILED_OLDER_SDK"))
    }
}
