package com.limelight.shell.settings

import android.app.Application
import android.content.Context
import androidx.preference.PreferenceManager
import androidx.test.core.app.ApplicationProvider
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.util.Locale

/**
 * The app language on both platform paths: Android 10-12 (API 29: Delos stores the choice and applies
 * it itself) and Android 13+ (API 33 and 36: the system's per-app locale).
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [29, 33, 36], application = Application::class)
class AppLanguagePlatformTest {
    private lateinit var context: Context
    private lateinit var defaultLocale: Locale

    @Before
    fun setUp() {
        defaultLocale = Locale.getDefault()
        context = ApplicationProvider.getApplicationContext()
    }

    @After
    fun tearDown() {
        // The Android 10-12 path sets the process default locale.
        Locale.setDefault(defaultLocale)
    }

    @Test
    fun aFreshInstallFollowsTheSystem() {
        assertEquals(AppLanguage.SYSTEM, AppLanguage.selected(context))
    }

    @Test
    fun aChosenLanguageIsTheSelectedOne() {
        AppLanguage.choose(context, "de")
        assertEquals("de", AppLanguage.selected(context))

        AppLanguage.choose(context, "pt-BR")
        assertEquals("pt-BR", AppLanguage.selected(context))
    }

    @Test
    fun goingBackToTheSystemLanguageClearsTheChoice() {
        AppLanguage.choose(context, "ja")

        AppLanguage.choose(context, AppLanguage.SYSTEM)

        assertEquals(AppLanguage.SYSTEM, AppLanguage.selected(context))
    }

    @Test
    fun anUnsupportedChoiceFollowsTheSystem() {
        AppLanguage.choose(context, "cs")

        AppLanguage.choose(context, "tlh")

        assertEquals(AppLanguage.SYSTEM, AppLanguage.selected(context))
    }

    @Test
    fun theLanguageOfTheOldPreferenceIsAdoptedOnce() {
        val settings = PreferenceManager.getDefaultSharedPreferences(context)
        settings.edit().putString(AppLanguage.PREF_KEY, "fr").commit()

        AppLanguage.initApplication(context)
        assertEquals("fr", AppLanguage.selected(context))

        // Later the user chooses another language; the old preference must not win again at the next start.
        AppLanguage.choose(context, "ko")
        AppLanguage.initApplication(context)
        assertEquals("ko", AppLanguage.selected(context))
    }

    @Test
    fun withoutAnOldPreferenceNothingIsAdopted() {
        AppLanguage.initApplication(context)

        assertEquals(AppLanguage.SYSTEM, AppLanguage.selected(context))
    }

    @Test
    fun theLanguageRowMirrorsTheAppLanguageAndDrivesIt() {
        val settings = context.getSharedPreferences("profile", Context.MODE_PRIVATE)
        AppLanguage.choose(context, "uk")

        AppLanguage.mirror(context, settings)
        assertEquals("uk", settings.getString(AppLanguage.PREF_KEY, null))

        settings.edit().putString(AppLanguage.PREF_KEY, "es").commit()
        AppLanguage.onPreferenceChanged(context, settings, AppLanguage.PREF_KEY)
        assertEquals("es", AppLanguage.selected(context))

        // Another key of the same settings does not touch the language.
        settings.edit().putString(AppLanguage.PREF_KEY, "de").commit()
        AppLanguage.onPreferenceChanged(context, settings, "list_fps")
        assertEquals("es", AppLanguage.selected(context))
    }

    @Test
    @Config(sdk = [29])
    fun onAndroid10To12ActivitiesGetTheChosenLanguageThroughTheirBaseContext() {
        AppLanguage.choose(context, "de")

        val wrapped = AppLanguage.wrap(context)

        assertEquals("de", wrapped.resources.configuration.locales[0].language)
        assertEquals("de", Locale.getDefault().language)
    }

    @Test
    @Config(sdk = [29])
    fun onAndroid10To12NoChoiceLeavesTheBaseContextAlone() {
        assertSame(context, AppLanguage.wrap(context))
    }

    @Test
    @Config(sdk = [33, 36])
    fun onAndroid13AndLaterTheSystemAppliesTheLanguage() {
        AppLanguage.choose(context, "de")

        // Delos neither wraps the context nor changes the process locale: the platform does it.
        assertSame(context, AppLanguage.wrap(context))
        assertEquals(defaultLocale, Locale.getDefault())
    }
}
