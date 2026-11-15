package com.limelight.shell.data

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.limelight.nvstream.http.ComputerDetails
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** Adding a PC by a typed address, and what the user is told when an action cannot happen. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33], application = android.app.Application::class)
class PcSetupTest {
    private lateinit var context: Context
    private lateinit var setup: PcSetup
    private lateinit var repo: PcRepository

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        val profiles = ProfileStore(context) { _, _ -> }
        repo = PcRepository(context, profiles::settings)
        setup = PcSetup(context, repo, profiles)
    }

    private fun pc() = Pc("PC-1", "Desk", "Desk", online = true, paired = false, runningGameId = 0, canWake = false,
        details = ComputerDetails().also { it.uuid = "PC-1"; it.name = "Desk" })

    // ---- the address the user typed ----

    @Test
    fun ipv4WithAndWithoutAPort() {
        assertEquals("192.168.1.20", setup.parseAddress("192.168.1.20")!!.host)
        assertEquals(-1, setup.parseAddress("192.168.1.20")!!.port)
        assertEquals("192.168.1.20", setup.parseAddress("192.168.1.20:48989")!!.host)
        assertEquals(48989, setup.parseAddress("192.168.1.20:48989")!!.port)
    }

    @Test
    fun hostNames() {
        assertEquals("desk.local", setup.parseAddress("desk.local")!!.host)
        assertEquals("pc.example.org", setup.parseAddress("pc.example.org:47989")!!.host)
        assertEquals(47989, setup.parseAddress("pc.example.org:47989")!!.port)
    }

    @Test
    fun ipv6InBrackets() {
        val withPort = setup.parseAddress("[2001:db8::1]:48989")!!
        assertEquals(48989, withPort.port)
        val withoutPort = setup.parseAddress("[2001:db8::1]")!!
        assertEquals(-1, withoutPort.port)
        // What is stored is the address without brackets.
        assertEquals("2001:db8::1", ComputerDetails.AddressTuple(withPort.host, 47989).address)
        assertEquals("2001:db8::1", ComputerDetails.AddressTuple(withoutPort.host, 47989).address)
    }

    @Test
    fun nothingTypedIsNotAnAddress() {
        assertNull(setup.parseAddress(""))
    }

    // ---- before the PC service is up, nothing is attempted ----

    @Test
    fun addingBeforeTheServiceIsReadySaysSo() = runBlocking {
        assertEquals(PcSetup.AddResult.Starting, setup.add("192.168.1.20"))
    }

    @Test
    fun pairingBeforeTheServiceIsReadySaysSo() = runBlocking {
        assertEquals(PcSetup.PairResult.Failed(PcSetup.PairFailure.STARTING), setup.pair(pc(), "1234", null))
    }

    @Test
    fun unpairingBeforeTheServiceIsReadySaysSo() = runBlocking {
        assertEquals(PcSetup.UnpairResult.STARTING, setup.unpair(pc()))
    }

    @Test
    fun wakingAPcWithoutAKnownMacAddressIsNotAttempted() = runBlocking {
        assertEquals(PcSetup.WakeResult.NO_MAC, setup.wake(pc()))
    }

    @Test
    fun aNewPinIsFourDigits() {
        assertTrue(setup.newPin().matches(Regex("[0-9]{4}")))
    }

    // ---- every outcome has its own sentence ----

    @Test
    fun everyPairingFailureHasItsOwnMessage() {
        val res = context.resources
        val texts = PcSetup.PairFailure.values().map { PcSetup.PairResult.Failed(it).text(res, "Desk") }

        assertTrue(texts.all { it.isNotBlank() })
        assertEquals(texts.size, texts.toSet().size)
    }

    @Test
    fun anUnexpectedPairingFailureShowsTheBackendsOwnMessage() {
        val res = context.resources

        assertEquals("handshake aborted",
            PcSetup.PairResult.Failed(PcSetup.PairFailure.FAILED, "handshake aborted").text(res, "Desk"))
        // A detail never replaces the message of a known reason.
        assertFalse(PcSetup.PairResult.Failed(PcSetup.PairFailure.PIN_WRONG, "x").text(res, "Desk") == "x")
    }

    @Test
    fun onlySuccessHasNoErrorText() {
        val res = context.resources

        assertNull(PcSetup.AddResult.Added("PC-1").errorText(res, "192.168.1.20"))
        val addErrors = listOf(PcSetup.AddResult.Starting, PcSetup.AddResult.NotAnAddress, PcSetup.AddResult.NoAnswer)
            .map { it.errorText(res, "192.168.1.20") }
        assertTrue(addErrors.all { !it.isNullOrBlank() })
        assertEquals(3, addErrors.toSet().size)

        assertNull(PcSetup.WakeResult.SENT.errorText(res, "Desk"))
        assertNotNull(PcSetup.WakeResult.NO_MAC.errorText(res, "Desk"))
        assertNotNull(PcSetup.WakeResult.FAILED.errorText(res, "Desk"))
    }

    @Test
    fun onlyUnpairedCountsAsSuccessAndEveryOutcomeHasItsOwnMessage() {
        val res = context.resources
        val outcomes = PcSetup.UnpairResult.values()

        assertEquals(listOf(PcSetup.UnpairResult.UNPAIRED), outcomes.filter { it.success })
        val texts = outcomes.map { it.text(res, "Desk") }
        assertTrue(texts.all { it.isNotBlank() })
        assertEquals(texts.size, texts.toSet().size)
    }

    @Test
    fun removingBeforeTheServiceIsReadyKeepsThePc() {
        repo.onComputerUpdated(pc().details.also { it.state = ComputerDetails.State.ONLINE })

        setup.remove(pc())

        assertEquals(1, repo.pcs.value.size)
    }
}
