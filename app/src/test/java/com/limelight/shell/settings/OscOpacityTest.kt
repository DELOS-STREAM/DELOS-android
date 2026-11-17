package com.limelight.shell.settings

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.limelight.preferences.PreferenceConfiguration
import com.limelight.shadows.ShadowGameManager
import com.limelight.shadows.ShadowMoonBridge
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33], shadows = [ShadowMoonBridge::class, ShadowGameManager::class])
class OscOpacityTest {
    private lateinit var context: Context
    private val prefs get() = context.getSharedPreferences("osc-opacity-test", Context.MODE_PRIVATE)

    @Before fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        prefs.edit().clear().commit()
    }

    @Test fun unsetReadsTheSameDefaultAsTheStream() {
        assertEquals(OscOpacity.DEFAULT, OscOpacity.read(prefs))
        assertEquals(PreferenceConfiguration.readPreferences(context, prefs).oscOpacity, OscOpacity.read(prefs))
    }

    @Test fun theWrittenValueIsWhatTheStreamReads() {
        assertEquals(35, OscOpacity.write(prefs, 35))
        assertEquals(35, OscOpacity.read(prefs))
        assertEquals(35, PreferenceConfiguration.readPreferences(context, prefs).oscOpacity)
    }

    @Test fun valuesOutsideTheRangeAreKeptInIt() {
        assertEquals(OscOpacity.MAX, OscOpacity.write(prefs, 140))
        assertEquals(OscOpacity.MAX, OscOpacity.read(prefs))
        assertEquals(OscOpacity.MIN, OscOpacity.write(prefs, -5))
        prefs.edit().putInt(OscOpacity.KEY, 400).commit()
        assertEquals(OscOpacity.MAX, OscOpacity.read(prefs))
    }

    @Test fun theSliderStepsLandOnTheDefault() {
        assertEquals(0, (OscOpacity.DEFAULT - OscOpacity.MIN) % OscOpacity.STEP)
        assertEquals(0, (OscOpacity.MAX - OscOpacity.MIN) % OscOpacity.STEP)
    }
}
