package com.limelight.shell.data

import android.content.Context
import android.content.SharedPreferences
import androidx.test.core.app.ApplicationProvider
import com.limelight.nvstream.http.ComputerDetails
import com.limelight.nvstream.http.PairingManager.PairState
import com.limelight.nvstream.http.TestIdentity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * What the home screen and the Devices screen are told after sequences of poll results. The service
 * is not bound in these tests: the repository is fed through [PcRepository.onComputerUpdated], the
 * one entry the service uses.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33], application = android.app.Application::class)
class PcRepositoryTest {
    private lateinit var context: Context
    private lateinit var settings: SharedPreferences
    private lateinit var repo: PcRepository

    private val apps = "<root status_code=\"200\">" +
        "<App><AppTitle>Desktop</AppTitle><ID>1</ID><IsHdrSupported>0</IsHdrSupported></App>" +
        "<App><AppTitle>Steam</AppTitle><ID>2</ID><IsHdrSupported>1</IsHdrSupported></App>" +
        "<App><AppTitle>Hades</AppTitle><ID>3</ID><IsHdrSupported>0</IsHdrSupported></App>" +
        "</root>"

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        settings = context.getSharedPreferences("profile", Context.MODE_PRIVATE)
        settings.edit().clear().commit()
        context.getSharedPreferences("delos_pc_pairing", Context.MODE_PRIVATE).edit().clear().commit()
        repo = PcRepository(context) { settings }
    }

    private fun details(
        uuid: String = "PC-1",
        name: String = "Desk",
        state: ComputerDetails.State = ComputerDetails.State.ONLINE,
        pairState: PairState? = PairState.PAIRED,
        rawApps: String? = apps,
        running: Int = 0,
    ) = ComputerDetails().also {
        it.uuid = uuid
        it.name = name
        it.state = state
        it.pairState = pairState
        it.rawAppList = rawApps
        it.runningGameId = running
    }

    private fun ready() = repo.library.value as Library.Ready

    /**
     * A PC as the service reports it while polling: again and again. The first report makes a PC the
     * library's PC, the app list is taken from the reports after that.
     */
    private fun PcRepository.polled(details: ComputerDetails) {
        onComputerUpdated(details)
        onComputerUpdated(details)
    }

    @Test
    fun aPairedOnlinePcWithAnAppListIsTheLibrary() {
        repo.polled(details())

        val pc = repo.pcs.value.single()
        assertEquals("PC-1", pc.uuid)
        assertTrue(pc.online)
        assertTrue(pc.paired)
        assertEquals(listOf("Desktop", "Steam", "Hades"), ready().apps.map { it.name })
        assertEquals(listOf(false, true, false), ready().apps.map { it.hdr })
        assertEquals(pc, ready().pc)
    }

    @Test
    fun anUnpairedPcIsListedButIsNotTheLibrary() {
        repo.polled(details(pairState = PairState.NOT_PAIRED))

        assertFalse(repo.pcs.value.single().paired)
        assertFalse(repo.library.value is Library.Ready)
        assertFalse(repo.library.value is Library.Offline)
    }

    @Test
    fun aPairedPcWithoutAnAppListYetIsLoading() {
        repo.polled(details(rawApps = null))

        assertEquals(Library.Loading, repo.library.value)
    }

    @Test
    fun aPairedPcThatIsOffAndHasNoKnownAppsIsOffline() {
        // Paired earlier: the pinned certificate is all the database knows before the PC answers.
        val off = details(state = ComputerDetails.State.OFFLINE, pairState = null, rawApps = null)
        off.serverCert = TestIdentity.create("host").cert

        repo.polled(off)

        val library = repo.library.value
        assertTrue(library is Library.Offline)
        assertEquals("PC-1", (library as Library.Offline).pc.uuid)
    }

    @Test
    fun offlineThenOnlineKeepsApps() {
        repo.polled(details())

        repo.polled(details(state = ComputerDetails.State.OFFLINE, pairState = null, rawApps = null))

        // The games stay on screen while the PC is unreachable, and it does not look unpaired.
        assertEquals(3, ready().apps.size)
        assertFalse(ready().pc.online)
        assertTrue(ready().pc.paired)

        repo.polled(details())
        assertTrue(ready().pc.online)
        assertEquals(3, ready().apps.size)
    }

    @Test
    fun pairingIsRememberedAcrossAppStarts() {
        repo.polled(details())

        val restarted = PcRepository(context) { settings }
        restarted.polled(details(state = ComputerDetails.State.UNKNOWN, pairState = null, rawApps = null))

        assertTrue(restarted.pcs.value.single().paired)
    }

    @Test
    fun aServerThatSaysNotPairedWinsOverWhatWasRemembered() {
        repo.polled(details())

        repo.polled(details(pairState = PairState.NOT_PAIRED))

        assertFalse(repo.pcs.value.single().paired)
        val restarted = PcRepository(context) { settings }
        restarted.polled(details(state = ComputerDetails.State.OFFLINE, pairState = null, rawApps = null))
        assertFalse(restarted.pcs.value.single().paired)
    }

    @Test
    fun aFailedPairingAttemptDoesNotUnpairWhatWasPaired() {
        repo.polled(details())

        repo.polled(details(pairState = PairState.PIN_WRONG))

        assertTrue(repo.pcs.value.single().paired)
    }

    @Test
    fun aForgottenPcStaysForgotten() {
        repo.polled(details())
        repo.rename("PC-1", "My desk")

        repo.forget("PC-1")
        // Its polling thread may report once more.
        repo.polled(details())

        assertTrue(repo.pcs.value.isEmpty())
        assertFalse(repo.library.value is Library.Ready)

        // Added again by the user: shown again, as a new PC without the old name.
        repo.remember("PC-1")
        repo.polled(details(pairState = PairState.NOT_PAIRED))
        assertEquals("Desk", repo.pcs.value.single().name)
        assertFalse(repo.pcs.value.single().paired)
    }

    @Test
    fun renameSurvivesAPoll() {
        repo.polled(details())

        repo.rename("PC-1", "  Living room  ")
        repo.polled(details(name = "DESKTOP-ABC"))

        val pc = repo.pcs.value.single()
        assertEquals("Living room", pc.name)
        assertEquals("DESKTOP-ABC", pc.serverName)
        assertEquals("Living room", ready().pc.name)
    }

    @Test
    fun aBlankNameGoesBackToThePcsOwn() {
        repo.polled(details())
        repo.rename("PC-1", "Living room")

        repo.rename("PC-1", "   ")

        assertEquals("Desk", repo.pcs.value.single().name)
    }

    @Test
    fun hiddenAppsAreFiltered() {
        repo.polled(details())

        repo.setHidden("PC-1", 2, true)

        assertEquals(listOf("Desktop", "Hades"), ready().apps.map { it.name })
        assertEquals(setOf(2), ready().hidden)
        assertEquals(1, ready().hiddenCount)

        repo.setShowHidden(true)
        assertEquals(3, ready().apps.size)

        repo.setShowHidden(false)
        repo.setHidden("PC-1", 2, false)
        assertEquals(3, ready().apps.size)
        assertEquals(0, ready().hiddenCount)
    }

    @Test
    fun aHiddenGameThatNoLongerExistsIsNotCounted() {
        repo.polled(details())
        repo.setHidden("PC-1", 2, true)
        repo.setHidden("PC-1", 99, true)

        assertEquals(setOf(2), ready().hidden)
    }

    @Test
    fun hiddenGamesBelongToTheirPc() {
        repo.polled(details())
        repo.setHidden("PC-2", 2, true)

        assertEquals(3, ready().apps.size)
    }

    @Test
    fun theRunningGameComesFirstThenRecentlyPlayed() {
        repo.polled(details())
        repo.setLastPlayed("PC-1", 3)

        assertEquals(listOf("Hades", "Desktop", "Steam"), ready().apps.map { it.name })
        assertEquals(3, ready().lastPlayed)

        repo.polled(details(running = 2))
        assertEquals(listOf("Steam", "Hades", "Desktop"), ready().apps.map { it.name })
        assertEquals(listOf(true, false, false), ready().apps.map { it.running })
    }

    @Test
    fun theLastPlayedGameOfEarlierVersionsStillCountsAsRecent() {
        settings.edit().putInt("ui_last_app_PC-1", 2).commit()

        repo.polled(details())

        assertEquals("Steam", ready().apps.first().name)
    }

    @Test
    fun anUnreadableAppListKeepsThePreviousOne() {
        repo.polled(details())

        repo.polled(details(rawApps = "<root status_code=\"200\"><App><AppTitle>Desk"))

        assertEquals(listOf("Desktop", "Steam", "Hades"), ready().apps.map { it.name })
    }

    @Test
    fun aNewAppListReplacesTheOldOne() {
        repo.polled(details())

        repo.polled(details(rawApps = "<root status_code=\"200\"><App><AppTitle>Only</AppTitle><ID>7</ID></App></root>"))

        assertEquals(listOf("Only"), ready().apps.map { it.name })
    }

    @Test
    fun theOnlinePairedPcIsPreferredUntilTheUserChooses() {
        val cert = TestIdentity.create("host").cert
        repo.polled(details(uuid = "PC-OFF", state = ComputerDetails.State.OFFLINE, pairState = null, rawApps = null)
            .also { it.serverCert = cert })
        repo.polled(details(uuid = "PC-ON"))

        assertEquals("PC-ON", ready().pc.uuid)

        repo.selectPc("PC-OFF")
        assertEquals("PC-OFF", (repo.library.value as Library.Offline).pc.uuid)

        // The choice belongs to the profile and survives a restart.
        val restarted = PcRepository(context) { settings }
        restarted.polled(details(uuid = "PC-ON"))
        restarted.polled(details(uuid = "PC-OFF", state = ComputerDetails.State.OFFLINE, pairState = null, rawApps = null)
            .also { it.serverCert = cert })
        assertEquals("PC-OFF", (restarted.library.value as Library.Offline).pc.uuid)
    }

    @Test
    fun forgettingTheChosenPcFallsBackToAnother() {
        repo.polled(details(uuid = "PC-A"))
        repo.polled(details(uuid = "PC-B"))
        repo.selectPc("PC-B")
        repo.polled(details(uuid = "PC-B"))
        assertEquals("PC-B", ready().pc.uuid)

        repo.forget("PC-B")
        repo.polled(details(uuid = "PC-A"))

        assertEquals("PC-A", ready().pc.uuid)
        assertEquals(listOf("PC-A"), repo.pcs.value.map { it.uuid })
    }

    @Test
    fun anotherProfileHasItsOwnNamesAndHiddenGames() {
        repo.polled(details())
        repo.rename("PC-1", "Mine")
        repo.setHidden("PC-1", 2, true)

        val other = context.getSharedPreferences("profile_other", Context.MODE_PRIVATE)
        other.edit().clear().commit()
        settings = other
        repo.profileChanged()

        assertEquals("Desk", repo.pcs.value.single().name)
        assertEquals(3, ready().apps.size)
    }

    @Test
    fun aPcWithoutAnIdIsIgnored() {
        repo.polled(ComputerDetails().also { it.name = "Ghost" })

        assertTrue(repo.pcs.value.isEmpty())
    }

    @Test
    fun wakeOnLanNeedsAKnownMacAddress() {
        repo.polled(details())
        assertFalse(repo.pcs.value.single().canWake)

        repo.polled(details().also { it.macAddress = "a0:b1:c2:d3:e4:f5" })
        assertTrue(repo.pcs.value.single().canWake)
    }

    @Test
    fun withoutTheServiceThereIsNothingToRefresh() {
        assertNull(repo.binder)
        repo.refresh()
        repo.rescan()
    }
}
