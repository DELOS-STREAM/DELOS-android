package com.limelight.shell.settings

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class StreamPresetStoreTest {
    private fun freshPrefs() = ApplicationProvider.getApplicationContext<Context>()
        .getSharedPreferences("stream-preset-test", Context.MODE_PRIVATE).also { it.edit().clear().commit() }

    @Test
    fun applyingBuiltInPresetWritesAndMatches() {
        val prefs = freshPrefs()
        val tv = StreamPresetStore.BUILT_IN.first { it.id == "builtin_tv_1080_60" }
        StreamPresetStore.apply(prefs, tv)

        assertEquals("1920x1080", prefs.getString("list_resolution", null))
        assertEquals("60", prefs.getString("list_fps", null))
        assertEquals(25000, prefs.getInt("seekbar_bitrate_kbps", -1))
        assertEquals("forceh265", prefs.getString("list_video_format", null))
        assertEquals("latency", prefs.getString("frame_pacing", null))
        assertEquals(tv.id, StreamPresetStore.matchingPreset(prefs)?.id)
    }

    @Test
    fun modifyingSettingBreaksPresetMatch() {
        val prefs = freshPrefs()
        val balanced = StreamPresetStore.BUILT_IN.first { it.id == "builtin_balanced_1080_60" }
        StreamPresetStore.apply(prefs, balanced)
        assertNotNull(StreamPresetStore.matchingPreset(prefs))

        prefs.edit().putInt("seekbar_bitrate_kbps", 42000).commit()
        assertNull(StreamPresetStore.matchingPreset(prefs))
    }

    @Test
    fun saveAndDeleteCustomPreset() {
        val prefs = freshPrefs()
        prefs.edit()
            .putString("list_resolution", "1920x1080")
            .putString("list_fps", "90")
            .putInt("seekbar_bitrate_kbps", 45000)
            .putString("list_video_format", "forceav1")
            .putString("frame_pacing", "fast-vsync")
            .commit()

        val saved = StreamPresetStore.saveCustom(prefs, "RayNeo 90Hz")
        assertFalse(saved.builtIn)
        assertEquals("RayNeo 90Hz", StreamPresetStore.matchingPreset(prefs)?.name)
        assertTrue(StreamPresetStore.loadCustom(prefs).any { it.id == saved.id })

        StreamPresetStore.deleteCustom(prefs, saved.id)
        assertNull(StreamPresetStore.matchingPreset(prefs))
        assertTrue(StreamPresetStore.loadCustom(prefs).isEmpty())
    }
}
