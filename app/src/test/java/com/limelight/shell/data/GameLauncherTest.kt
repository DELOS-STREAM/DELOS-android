package com.limelight.shell.data

import android.app.Activity
import android.content.ComponentName
import androidx.preference.PreferenceManager
import com.limelight.DelosStreamActivity
import com.limelight.computers.ComputerManagerService
import com.limelight.nvstream.http.ComputerDetails
import com.limelight.nvstream.http.NvApp
import com.limelight.nvstream.http.TestIdentity
import com.limelight.profiles.ProfilesManagerReset
import com.limelight.utils.ServerHelper
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.Mockito.mock
import org.mockito.Mockito.`when`
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** The intent a game is started with: which PC, which app, which certificate, which display. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33], application = android.app.Application::class,
    shadows = [com.limelight.shadows.ShadowMoonBridge::class, com.limelight.shadows.ShadowGameManager::class])
class GameLauncherTest {
    private lateinit var activity: Activity
    private lateinit var binder: ComputerManagerService.ComputerManagerBinder
    private val cert = TestIdentity.create("host").cert

    @Before
    fun setUp() {
        ProfilesManagerReset.reset()
        activity = Robolectric.buildActivity(Activity::class.java).setup().get()
        PreferenceManager.getDefaultSharedPreferences(activity).edit().clear().commit()
        binder = mock(ComputerManagerService.ComputerManagerBinder::class.java)
        `when`(binder.uniqueId).thenReturn("0123456789ABCDEF")
    }

    private fun pc(virtualSupported: Boolean = false, driverReady: Boolean = false) = ComputerDetails().also {
        it.uuid = "PC-1"
        it.name = "Desk"
        it.state = ComputerDetails.State.ONLINE
        it.activeAddress = ComputerDetails.AddressTuple("192.168.1.20", 48989)
        it.httpsPort = 48984
        it.serverCert = cert
        it.vDisplaySupported = virtualSupported
        it.vDisplayDriverReady = driverReady
        it.serverCommands = arrayListOf("Sleep") // the type the serverinfo parser produces
    }

    private val app = NvApp("Hades", "APP-UUID", 42, true)

    private fun wantVirtualDisplay() {
        PreferenceManager.getDefaultSharedPreferences(activity).edit()
            .putBoolean("checkbox_use_virtual_display", true).commit()
    }

    @Test
    fun theIntentNamesThePcTheAppAndThePinnedCertificate() {
        val start = GameLauncher.startIntent(activity, binder, pc(), app)
        val intent = ServerHelper.getStreamIntent(start.intent)

        assertEquals(ComponentName(activity, DelosStreamActivity::class.java), intent.component)
        assertEquals("192.168.1.20", intent.getStringExtra(DelosStreamActivity.EXTRA_HOST))
        assertEquals(48989, intent.getIntExtra(DelosStreamActivity.EXTRA_PORT, 0))
        assertEquals(48984, intent.getIntExtra(DelosStreamActivity.EXTRA_HTTPS_PORT, 0))
        assertEquals(42, intent.getIntExtra(DelosStreamActivity.EXTRA_APP_ID, 0))
        assertEquals("Hades", intent.getStringExtra(DelosStreamActivity.EXTRA_APP_NAME))
        assertEquals("APP-UUID", intent.getStringExtra(DelosStreamActivity.EXTRA_APP_UUID))
        assertTrue(intent.getBooleanExtra(DelosStreamActivity.EXTRA_APP_HDR, false))
        assertEquals("PC-1", intent.getStringExtra(DelosStreamActivity.EXTRA_PC_UUID))
        assertEquals("0123456789ABCDEF", intent.getStringExtra(DelosStreamActivity.EXTRA_UNIQUEID))
        // The stream must be pinned to the same certificate as the pairing.
        assertArrayEquals(cert.encoded, intent.getByteArrayExtra(DelosStreamActivity.EXTRA_SERVER_CERT))
        assertEquals(arrayListOf("Sleep"), intent.getStringArrayListExtra(DelosStreamActivity.EXTRA_SERVER_COMMANDS))
    }

    @Test
    fun theServerCommandsMayBeAnyKindOfList() {
        // Regression: the list was cast to ArrayList, so any other List crashed the start of a game.
        val lists = listOf<List<String>>(
            java.util.Collections.unmodifiableList(arrayListOf("Sleep", "Restart")),
            java.util.LinkedList(listOf("Sleep", "Restart")),
            listOf("Sleep", "Restart"),
        )
        for (commands in lists) {
            val details = pc().also { it.serverCommands = commands }

            val intent = ServerHelper.getStreamIntent(GameLauncher.startIntent(activity, binder, details, app).intent)

            assertEquals(arrayListOf("Sleep", "Restart"), intent.getStringArrayListExtra(DelosStreamActivity.EXTRA_SERVER_COMMANDS))
        }
    }

    @Test
    fun aPcWithoutServerCommandsCarriesNone() {
        val details = pc().also { it.serverCommands = null }

        val intent = ServerHelper.getStreamIntent(GameLauncher.startIntent(activity, binder, details, app).intent)

        assertNull(intent.getStringArrayListExtra(DelosStreamActivity.EXTRA_SERVER_COMMANDS))
    }

    @Test
    fun anUnpairedPcCarriesNoCertificate() {
        val details = pc().also { it.serverCert = null }

        val intent = ServerHelper.getStreamIntent(GameLauncher.startIntent(activity, binder, details, app).intent)

        assertNull(intent.getByteArrayExtra(DelosStreamActivity.EXTRA_SERVER_CERT))
    }

    @Test
    fun theMainDisplayIsStreamedUnlessTheVirtualDisplayIsWanted() {
        val start = GameLauncher.startIntent(activity, binder, pc(virtualSupported = true, driverReady = true), app)

        assertFalse(ServerHelper.getStreamIntent(start.intent).getBooleanExtra(DelosStreamActivity.EXTRA_VDISPLAY, true))
        assertFalse(start.virtualDisplayUnavailable)
    }

    @Test
    fun aWantedAndWorkingVirtualDisplayIsUsed() {
        wantVirtualDisplay()

        val start = GameLauncher.startIntent(activity, binder, pc(virtualSupported = true, driverReady = true), app)

        assertTrue(ServerHelper.getStreamIntent(start.intent).getBooleanExtra(DelosStreamActivity.EXTRA_VDISPLAY, false))
        assertFalse(start.virtualDisplayUnavailable)
    }

    @Test
    fun aWantedVirtualDisplayWithoutADriverFallsBackToTheMainDisplayAndSaysSo() {
        wantVirtualDisplay()

        for (details in listOf(pc(virtualSupported = true, driverReady = false), pc(virtualSupported = false))) {
            val start = GameLauncher.startIntent(activity, binder, details, app)

            // No question is asked: the game starts on the main display and the caller is told.
            assertFalse(ServerHelper.getStreamIntent(start.intent).getBooleanExtra(DelosStreamActivity.EXTRA_VDISPLAY, true))
            assertTrue(start.virtualDisplayUnavailable)
        }
    }

    @Test
    fun aPerGameOverrideOfTheVirtualDisplayWinsOverTheProfile() {
        val settings = com.limelight.shell.settings.GameSettingsStore(ProfileStore.currentSettings(activity), "PC-1", 42)
        settings.preferences.edit().putBoolean("checkbox_use_virtual_display", true).commit()

        val forThisGame = GameLauncher.startIntent(activity, binder, pc(virtualSupported = true, driverReady = true), app)
        val forAnotherGame = GameLauncher.startIntent(activity, binder, pc(virtualSupported = true, driverReady = true),
            NvApp("Other", "OTHER-UUID", 43, false))

        assertTrue(ServerHelper.getStreamIntent(forThisGame.intent).getBooleanExtra(DelosStreamActivity.EXTRA_VDISPLAY, false))
        assertFalse(ServerHelper.getStreamIntent(forAnotherGame.intent).getBooleanExtra(DelosStreamActivity.EXTRA_VDISPLAY, true))
    }

    @Test
    fun withoutTheServiceNothingIsPrepared() {
        val profiles = ProfileStore(activity) { _, _ -> }
        val repo = PcRepository(activity, profiles::settings)
        val launcher = GameLauncher(activity, repo)
        val shown = Pc("PC-1", "Desk", "Desk", online = true, paired = true, runningGameId = 0, canWake = false, details = pc())

        assertNull(launcher.prepare(shown, App(42, "Hades", hdr = true, running = false, nvApp = app)))
    }

    @Test
    fun onlyQuitCountsAsSuccessAndEveryOutcomeHasItsOwnMessage() {
        val res = activity.resources
        val outcomes = GameLauncher.QuitOutcome.values()

        assertEquals(listOf(GameLauncher.QuitOutcome.QUIT), outcomes.filter { GameLauncher.QuitResult(it).success })
        val texts = outcomes.map { GameLauncher.QuitResult(it).text(res, "Desk", "Hades") }
        assertTrue(texts.all { it.isNotBlank() })
        assertEquals(texts.size, texts.toSet().size)
        // The PC's own reason is shown when it refused.
        assertEquals("in use", GameLauncher.QuitResult(GameLauncher.QuitOutcome.NOT_QUIT, "in use").text(res, "Desk", "Hades"))
    }
}
