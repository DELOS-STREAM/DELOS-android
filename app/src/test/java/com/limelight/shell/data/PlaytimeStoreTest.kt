package com.limelight.shell.data

import android.app.Application
import android.content.Context
import android.content.res.Resources
import androidx.test.core.app.ApplicationProvider
import java.util.Locale
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class PlaytimeStoreTest {
    private val context = ApplicationProvider.getApplicationContext<Application>()
    private var wall = 1_000_000L
    private val store = PlaytimeStore({ id -> context.getSharedPreferences("test_${id ?: "default"}", Context.MODE_PRIVATE) }, { wall })
    private val a = PlaytimeKey("p1", "pc", 1)

    @Test fun nothingRecordedIsNone() {
        assertEquals(Playtime.NONE, store.playtime("p1", "pc", 1))
    }

    @Test fun addingStampsTheLastStreamedTime() {
        store.add(a, 30_000)
        assertEquals(Playtime(30_000, 1_000_000), store.playtime("p1", "pc", 1))
        wall = 2_500_000
        store.add(a, 15_000)
        assertEquals(Playtime(45_000, 2_500_000), store.playtime("p1", "pc", 1))
    }

    @Test fun zeroTimeChangesNothing() {
        store.add(a, 10_000)
        wall = 9_000_000
        store.add(a, 0)
        store.add(a, -5)
        assertEquals(Playtime(10_000, 1_000_000), store.playtime("p1", "pc", 1))
    }

    @Test fun profilesPcsAndAppsStaySeparate() {
        store.add(a, 10_000)
        wall = 3_000_000
        store.add(PlaytimeKey("p2", "pc", 1), 20_000)
        store.add(PlaytimeKey("p1", "other", 1), 30_000)
        store.add(PlaytimeKey("p1", "pc", 2), 40_000)
        assertEquals(Playtime(10_000, 1_000_000), store.playtime("p1", "pc", 1))
        assertEquals(Playtime(20_000, 3_000_000), store.playtime("p2", "pc", 1))
        assertEquals(30_000L, store.playtime("p1", "other", 1).totalMillis)
        assertEquals(40_000L, store.playtime("p1", "pc", 2).totalMillis)
    }

    @Test fun aProfilesTotalAddsEveryAppOnEveryPcAndKeepsTheLatestTime() {
        assertEquals(Playtime.NONE, store.total("t1"))
        wall = 2_000
        store.add(PlaytimeKey("t1", "pc", 1), 90_000)
        wall = 1_000
        store.add(PlaytimeKey("t1", "pc", 2), 30_000)
        store.add(PlaytimeKey("t1", "other", 1), 600_000)
        // Another profile stays apart
        wall = 9_000
        store.add(PlaytimeKey("t2", "pc", 1), 45_000)
        assertEquals(Playtime(720_000, 2_000), store.total("t1"))
        assertEquals(Playtime(45_000, 9_000), store.total("t2"))
    }

    @Test fun theTotalAndTheTimestampHaveTheirOwnKeys() {
        assertNotEquals(PlaytimeStore.totalKey("pc", 7), PlaytimeStore.lastKey("pc", 7))
        assertTrue(PlaytimeStore.lastKey("pc", 7).startsWith("ui_playtime_last_"))
        assertTrue(PlaytimeStore.lastKey("pc", 7).endsWith("_7"))
    }

    @Test fun observersAreToldOnEveryAddition() {
        val before = store.changes.value
        store.add(a, 1_000)
        assertEquals(before + 1, store.changes.value)
        store.add(a, 0)
        assertEquals(before + 1, store.changes.value)
    }

    @Test fun trackerSlicesReachTheStoreWithTheTimestampOfTheFlush() {
        var mono = 0L
        val tracker = PlaytimeTracker({ mono }, PlaytimeTracker.Sink(store::add))
        tracker.begin(a); tracker.setConnected(true)
        mono = 30_000; wall = 5_000_000; tracker.flush()
        mono = 50_000; wall = 5_020_000; tracker.end()
        assertEquals(Playtime(50_000, 5_020_000), store.playtime("p1", "pc", 1))
    }

    private fun resources(): Resources = context.resources

    @Test fun partsEnglish() {
        val min = 60_000L
        fun parts(millis: Long, short: Boolean = false) = PlaytimeFormat.parts(resources(), millis, short)
        assertEquals(PlaytimeFormat.Parts("0", "hours"), parts(0))
        assertEquals(PlaytimeFormat.Parts("0", "h"), parts(0, short = true))
        assertEquals(PlaytimeFormat.Parts("1", "minute"), parts(5_000))
        assertEquals(PlaytimeFormat.Parts("45", "minutes"), parts(45 * min))
        assertEquals(PlaytimeFormat.Parts("45", "min"), parts(45 * min, short = true))
        assertEquals(PlaytimeFormat.Parts("1", "hour"), parts(60 * min))
        assertEquals(PlaytimeFormat.Parts("20.4", "hours"), parts(20 * 60 * min + 24 * min))
        assertEquals(PlaytimeFormat.Parts("20.4", "h"), parts(20 * 60 * min + 24 * min, short = true))
        assertEquals(PlaytimeFormat.Parts("128.7", "hours"), parts((128.7 * 60 * min).toLong()))
    }

    @Test @Config(sdk = [33], qualifiers = "cs")
    fun theLocaleIsThatOfTheResourcesNotTheJvmDefault() {
        // The app's language is applied to its resources, not to Locale.getDefault(): the decimal comma and the text come from the same place
        val saved = Locale.getDefault()
        try {
            Locale.setDefault(Locale.US)
            assertEquals("cs", PlaytimeFormat.localeOf(resources()).language)
            assertEquals(PlaytimeFormat.Parts("20,4", "hodiny"), PlaytimeFormat.parts(resources(), 73_440_000))
            assertEquals("20,4", PlaytimeFormat.decimal(204, PlaytimeFormat.localeOf(resources())))
        } finally {
            Locale.setDefault(saved)
        }
    }

    @Test @Config(sdk = [33], qualifiers = "cs")
    fun partsCzech() {
        val min = 60_000L
        // The locale comes from the resources, as in the UI: no explicit locale
        fun parts(millis: Long) = PlaytimeFormat.parts(resources(), millis)
        assertEquals(PlaytimeFormat.Parts("0", "hodin"), parts(0))
        assertEquals(PlaytimeFormat.Parts("20,4", "hodiny"), parts(20 * 60 * min + 24 * min))
        assertEquals(PlaytimeFormat.Parts("3", "minuty"), parts(3 * min))
        assertEquals(PlaytimeFormat.Parts("45", "minut"), parts(45 * min))
        assertEquals(PlaytimeFormat.Parts("5", "hodin"), parts(5 * 60 * min))
    }

    @Test @Config(sdk = [33], qualifiers = "de")
    fun partsGermanZero() {
        assertEquals(PlaytimeFormat.Parts("0", "Stunden"), PlaytimeFormat.parts(resources(), 0))
        assertEquals(PlaytimeFormat.Parts("20,4", "Stunden"), PlaytimeFormat.parts(resources(), 73_440_000))
    }
}
