package com.limelight.shell.settings

import android.content.Context
import android.content.pm.PackageManager
import android.content.res.Configuration
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

/** The look of a fresh install (owner's choice, 2026-11-16): what a profile without stored appearance keys reads. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class AppearanceDefaultsTest {
    private val firstRun = Appearance(
        dark = true, materialYou = false, palette = Palette.DELOS, background = BackgroundKind.CONTOUR,
        matchGameColors = true, heroBackground = true, heroBlur = 5, heroOpacity = 80, uiScale = 100,
    )

    private fun freshProfile() = ApplicationProvider.getApplicationContext<Context>()
        .getSharedPreferences("appearance-defaults-test", Context.MODE_PRIVATE).also { it.edit().clear().commit() }

    @Test
    fun aFreshProfileGetsTheFirstRunLook() {
        val prefs = freshProfile()
        assertEquals(firstRun, AppearanceStore { prefs }.appearance.value)
    }

    @Test
    @Config(qualifiers = "notnight")
    fun theFirstRunThemeIsDarkEvenWhenTheSystemIsLight() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        assertEquals(Configuration.UI_MODE_NIGHT_NO, context.resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK)
        val prefs = freshProfile()
        assertEquals(true, AppearanceStore { prefs }.appearance.value.dark)
    }

    @Test
    fun storedChoicesStillWinOverTheDefaults() {
        val prefs = freshProfile()
        // The stored keys of the profile settings (AppearanceStore)
        prefs.edit().putBoolean("ui_dark", false).putInt("ui_scale", 115).putString("ui_background", BackgroundKind.AURORA.name).commit()
        val read = AppearanceStore { prefs }.appearance.value
        assertEquals(false, read.dark)
        assertEquals(115, read.uiScale)
        assertEquals(BackgroundKind.AURORA, read.background)
    }

    @Test
    fun aProfileMadeOnAnAndroidTvStartsWithTheLighterLook() {
        val prefs = freshProfile()
        AppearanceStore.applyTvDefaults(prefs)
        assertEquals(firstRun.copy(background = BackgroundKind.STILL, heroBlur = 0, reducedMotion = true), AppearanceStore { prefs }.appearance.value)
    }

    @Test
    fun theTvLookKeepsWhatWasAlreadyChosen() {
        val prefs = freshProfile()
        prefs.edit().putString("ui_background", BackgroundKind.CONTOUR.name).putInt("ui_hero_blur", 5).putBoolean("ui_reduced_motion", false).commit()
        AppearanceStore.applyTvDefaults(prefs)
        assertEquals(firstRun, AppearanceStore { prefs }.appearance.value)
    }

    @Test
    fun anExistingProfileOnAnAndroidTvKeepsItsLook() {
        // The leanback feature alone changes nothing: only a profile made on the TV gets the lighter look written
        val context = ApplicationProvider.getApplicationContext<Context>()
        shadowOf(context.packageManager).setSystemFeature(PackageManager.FEATURE_LEANBACK, true)
        val prefs = freshProfile()
        assertEquals(firstRun, AppearanceStore { prefs }.appearance.value)
    }
}
