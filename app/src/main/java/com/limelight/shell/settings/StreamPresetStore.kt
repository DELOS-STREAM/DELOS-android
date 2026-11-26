package com.limelight.shell.settings

import android.content.SharedPreferences
import org.json.JSONArray
import org.json.JSONObject
import java.util.UUID

/**
 * Named stream presets (Home, Balanced, TV, Travel, plus user-saved custom presets) that capture
 * and apply the core stream quality settings in one action.
 */
data class StreamPreset(
    val id: String,
    val name: String,
    val builtIn: Boolean,
    val resolution: String,
    val fps: String,
    val bitrateKbps: Int,
    val videoFormat: String,
    val framePacing: String,
)

object StreamPresetStore {
    const val KEY_CUSTOM_PRESETS = "ui_stream_presets"

    val BUILT_IN: List<StreamPreset> = listOf(
        StreamPreset(
            id = "builtin_home_1440_120",
            name = "Home 1440p 120",
            builtIn = true,
            resolution = "2560x1440",
            fps = "120",
            bitrateKbps = 80000,
            videoFormat = "auto",
            framePacing = "fast-vsync",
        ),
        StreamPreset(
            id = "builtin_balanced_1080_60",
            name = "Balanced 1080p 60",
            builtIn = true,
            resolution = "1920x1080",
            fps = "60",
            bitrateKbps = 30000,
            videoFormat = "auto",
            framePacing = "fast-vsync",
        ),
        StreamPreset(
            id = "builtin_tv_1080_60",
            name = "TV / Low Power 1080p 60",
            builtIn = true,
            resolution = "1920x1080",
            fps = "60",
            bitrateKbps = 25000,
            videoFormat = "forceh265",
            framePacing = "latency",
        ),
        StreamPreset(
            id = "builtin_travel_720_60",
            name = "Travel 720p 60",
            builtIn = true,
            resolution = "1280x720",
            fps = "60",
            bitrateKbps = 12000,
            videoFormat = "forceh265",
            framePacing = "latency",
        ),
    )

    fun all(prefs: SharedPreferences): List<StreamPreset> = BUILT_IN + loadCustom(prefs)

    fun loadCustom(prefs: SharedPreferences): List<StreamPreset> {
        val raw = prefs.getString(KEY_CUSTOM_PRESETS, null) ?: return emptyList()
        return runCatching {
            val array = JSONArray(raw)
            (0 until array.length()).mapNotNull { i ->
                val obj = array.optJSONObject(i) ?: return@mapNotNull null
                val id = obj.optString("id").takeIf { it.isNotBlank() } ?: return@mapNotNull null
                val name = obj.optString("name").takeIf { it.isNotBlank() } ?: return@mapNotNull null
                StreamPreset(
                    id = id,
                    name = name,
                    builtIn = false,
                    resolution = obj.optString("resolution", "1920x1080"),
                    fps = obj.optString("fps", "60"),
                    bitrateKbps = obj.optInt("bitrateKbps", 30000).coerceIn(500, 500000),
                    videoFormat = obj.optString("videoFormat", "auto"),
                    framePacing = obj.optString("framePacing", "latency"),
                )
            }
        }.getOrElse { emptyList() }
    }

    fun apply(prefs: SharedPreferences, preset: StreamPreset) {
        prefs.edit()
            .putString("list_resolution", preset.resolution)
            .putString("list_fps", preset.fps)
            .putInt("seekbar_bitrate_kbps", preset.bitrateKbps)
            .putString("list_video_format", preset.videoFormat)
            .putString("frame_pacing", preset.framePacing)
            .apply()
    }

    fun matchingPreset(prefs: SharedPreferences): StreamPreset? {
        val res = prefs.getString("list_resolution", null) ?: return null
        val fps = prefs.getString("list_fps", null) ?: return null
        val bitrate = prefs.getInt("seekbar_bitrate_kbps", -1)
        val format = prefs.getString("list_video_format", null) ?: return null
        val pacing = prefs.getString("frame_pacing", null) ?: return null
        return all(prefs).firstOrNull {
            it.resolution == res &&
                it.fps == fps &&
                it.bitrateKbps == bitrate &&
                it.videoFormat == format &&
                it.framePacing == pacing
        }
    }

    fun saveCustom(prefs: SharedPreferences, name: String): StreamPreset {
        val cleanName = name.trim()
        require(cleanName.isNotEmpty()) { "Preset name cannot be blank" }
        val preset = StreamPreset(
            id = "custom_${UUID.randomUUID()}",
            name = cleanName,
            builtIn = false,
            resolution = prefs.getString("list_resolution", "1920x1080") ?: "1920x1080",
            fps = prefs.getString("list_fps", "60") ?: "60",
            bitrateKbps = prefs.getInt("seekbar_bitrate_kbps", 30000),
            videoFormat = prefs.getString("list_video_format", "auto") ?: "auto",
            framePacing = prefs.getString("frame_pacing", "latency") ?: "latency",
        )
        val updated = loadCustom(prefs).filterNot { it.name.equals(cleanName, ignoreCase = true) } + preset
        writeCustom(prefs, updated)
        return preset
    }

    fun deleteCustom(prefs: SharedPreferences, id: String) {
        val updated = loadCustom(prefs).filterNot { it.id == id }
        writeCustom(prefs, updated)
    }

    private fun writeCustom(prefs: SharedPreferences, presets: List<StreamPreset>) {
        val array = JSONArray()
        presets.forEach { p ->
            array.put(
                JSONObject()
                    .put("id", p.id)
                    .put("name", p.name)
                    .put("resolution", p.resolution)
                    .put("fps", p.fps)
                    .put("bitrateKbps", p.bitrateKbps)
                    .put("videoFormat", p.videoFormat)
                    .put("framePacing", p.framePacing)
            )
        }
        prefs.edit().putString(KEY_CUSTOM_PRESETS, array.toString()).apply()
    }
}
