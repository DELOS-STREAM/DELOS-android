package com.limelight.shell.settings

import android.content.SharedPreferences
import androidx.compose.runtime.*
import com.limelight.preferences.PreferenceConfiguration
import com.limelight.shell.ui.RowValue

/** The same profile preference and toggle value in Controls and the controller tutorial. */
@Composable
fun rememberBackGameBarValue(prefs: SharedPreferences, onChanged: () -> Unit = {}): RowValue.Toggle {
    val key = PreferenceConfiguration.BACK_GAME_BAR_PREF_STRING
    var enabled by remember(prefs) { mutableStateOf(prefs.getBoolean(key, false)) }
    DisposableEffect(prefs) {
        val listener = SharedPreferences.OnSharedPreferenceChangeListener { source, changed ->
            if (changed == key) enabled = source.getBoolean(key, false)
        }
        prefs.registerOnSharedPreferenceChangeListener(listener)
        onDispose { prefs.unregisterOnSharedPreferenceChangeListener(listener) }
    }
    return RowValue.Toggle(enabled) { on ->
        enabled = on
        prefs.edit().putBoolean(key, on).apply()
        onChanged()
    }
}
