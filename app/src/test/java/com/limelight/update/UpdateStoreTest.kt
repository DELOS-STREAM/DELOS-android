package com.limelight.update

import androidx.preference.PreferenceManager
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class UpdateStoreTest {
    private val prefs = PreferenceManager.getDefaultSharedPreferences(ApplicationProvider.getApplicationContext()).also { it.edit().clear().commit() }
    private val candidate = UpdateCandidate("v1.1.0", SemVer.parse("1.1.0")!!, "Added\n• x", "delos-1.1.0-arm64-v8a.apk", "https://github.com/a.apk", 123, "ab".repeat(32))

    @Test fun checkingIsOnByDefaultAndTheChoiceIsKept() {
        val store = UpdateStore(prefs)
        assertTrue(store.enabled)
        store.enabled = false
        assertEquals(false, UpdateStore(prefs).enabled)
    }

    @Test fun theCachedAnswerSurvivesAndComesWithItsEtag() {
        val store = UpdateStore(prefs)
        assertNull(store.cached)
        assertNull(store.etag)
        store.saveAnswer("\"e\"", CachedLatest("v1.1.0", candidate), 42L)
        assertEquals(CachedLatest("v1.1.0", candidate), UpdateStore(prefs).cached)
        assertEquals("\"e\"", UpdateStore(prefs).etag)
        assertEquals(42L, UpdateStore(prefs).lastCheck)
    }

    @Test fun anEtagWithoutAnAnswerIsNotSent() {
        prefs.edit().putString("update_etag", "\"e\"").apply()
        assertNull(UpdateStore(prefs).etag)
        prefs.edit().putString("update_cached_latest", "not json").apply()
        assertNull(UpdateStore(prefs).etag)
    }

    @Test fun anAnswerWithoutADownloadIsCachedToo() {
        assertEquals(CachedLatest("v1.1.0", null), CachedLatest.fromJson(CachedLatest("v1.1.0", null).toJson()))
        assertNull(CachedLatest.fromJson("""{"tag":"nightly"}"""))
        assertNull(CachedLatest.fromJson("""{"tag":"v1.0.0","candidate":{"notes":"x"}}"""))
        assertNull(CachedLatest.fromJson(null))
    }

    @Test fun anAnswerThatWasNotResolvedServesOnlyAnInstalledVersionThatIsNotOlderThanTheTag() {
        val skipped = CachedLatest("v1.1.0", null, resolved = false)
        assertTrue(skipped.usableFor(SemVer.parse("1.1.0")!!))
        assertTrue(skipped.usableFor(SemVer.parse("1.2.0")!!))
        assertEquals(false, skipped.usableFor(SemVer.parse("1.1.0-rc.2")!!))
        assertEquals(false, skipped.usableFor(SemVer.parse("1.0.0")!!))
        assertTrue(CachedLatest("v1.1.0", null).usableFor(SemVer.parse("1.0.0")!!))
        assertEquals(skipped, CachedLatest.fromJson(skipped.toJson()))
    }

    @Test fun laterAndTheDebugServerAreStored() {
        val store = UpdateStore(prefs)
        store.later("v1.1.0", 99L)
        assertEquals("v1.1.0", UpdateStore(prefs).laterTag)
        assertEquals(99L, UpdateStore(prefs).laterUntil)
        store.debugServer = "  http://10.0.2.2:8080/latest "
        assertEquals("http://10.0.2.2:8080/latest", store.debugServer)
        store.debugServer = ""
        assertNull(store.debugServer)
    }
}
