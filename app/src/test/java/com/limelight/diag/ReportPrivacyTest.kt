package com.limelight.diag

import android.app.Application
import android.content.Context
import androidx.preference.PreferenceManager
import androidx.test.core.app.ApplicationProvider
import com.limelight.computers.ComputerDatabaseManager
import com.limelight.nvstream.http.ComputerDetails
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.net.URLDecoder

/**
 * End to end: what the app knows about the user's PCs, profiles and keys is stored the way the app
 * stores it, then a diagnostic report and a prefilled issue address are built the way the UI builds
 * them. Nothing of what was stored may be in either, because users post both in public issues.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33], application = Application::class)
class ReportPrivacyTest {
    private lateinit var context: Context

    private val pcName = "Bedroom-Tower"
    private val pcNameInDelos = "Kids gaming box"
    private val pcUuid = "2A6F3E0B-11C4-4E7B-9E2D-5B6A7C8D9E0F"
    private val hostName = "tower.home.example"
    private val localIp = "192.168.77.20"
    private val remoteIp = "203.0.113.77"
    private val ipv6 = "2001:db8:77::20"
    private val mac = "a0:b1:c2:d3:e4:f5"
    private val profileName = "Marketa"
    private val profileId = "profile-7c1d0a55"
    private val clientId = "0123456789abcdef"
    private val apiKey = "sgdbKEY1234567890abcdef"

    private val secrets
        get() = listOf(pcName, pcNameInDelos, pcUuid, hostName, localIp, remoteIp, ipv6, mac, profileName, profileId, clientId, apiKey)

    /** Text as it turns up in logs and error messages, naming everything that was stored. */
    private val chatter
        get() = "Connecting to $pcName ($pcNameInDelos) at $hostName / $localIp / $remoteIp / [$ipv6]:47989, " +
            "mac $mac, pc $pcUuid, client $clientId, profile $profileName ($profileId), key $apiKey"

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        context.databaseList().forEach { context.deleteDatabase(it) }

        val database = ComputerDatabaseManager(context)
        database.updateComputer(ComputerDetails().also {
            it.uuid = pcUuid
            it.name = pcName
            it.localAddress = ComputerDetails.AddressTuple(localIp, 47989)
            it.remoteAddress = ComputerDetails.AddressTuple(remoteIp, 47989)
            it.manualAddress = ComputerDetails.AddressTuple(hostName, 47989)
            it.ipv6Address = ComputerDetails.AddressTuple(ipv6, 47989)
            it.macAddress = mac
        })
        database.close()

        PreferenceManager.getDefaultSharedPreferences(context).edit().clear()
            .putString("ui_profiles", "[{\"id\":\"$profileId\",\"name\":\"$profileName\",\"avatar\":0}]")
            .putString("sgdb_api_key", apiKey)
            .commit()
        context.getSharedPreferences("ui_profile_$profileId", Context.MODE_PRIVATE).edit().clear()
            .putString("ui_pc_name_$pcUuid", pcNameInDelos).commit()
        context.openFileOutput("uniqueid", Context.MODE_PRIVATE).use { it.write(clientId.toByteArray()) }
    }

    private fun assertNoSecrets(what: String, text: String) {
        secrets.forEach { secret ->
            assertFalse("$what contains '$secret'", text.contains(secret, ignoreCase = true))
        }
    }

    @Test
    fun theCollectorFindsEverythingThatWasStored() {
        val found = ScrubContextCollector.collect(context)

        assertTrue(pcName in found.pcNames)
        assertTrue(pcNameInDelos in found.pcNames)
        assertTrue(found.hostAddresses.containsAll(listOf(hostName, localIp, remoteIp, ipv6, mac)))
        assertTrue(profileName in found.names)
        assertTrue(found.identifiers.containsAll(listOf(pcUuid, profileId, clientId)))
        assertTrue(apiKey in found.secrets)
    }

    @Test
    fun aDiagnosticReportNeverContainsSavedPcNamesAddressesOrIds() = runBlocking {
        val report = DiagExport.report(context, sections = listOf("Last error" to chatter))

        // The section itself must have made it into the report, or this test proves nothing.
        assertTrue(report.text.contains("== Last error =="))
        assertTrue(report.text.contains("Connecting to"))
        assertNoSecrets("the report", report.text)
    }

    @Test
    fun aProblemReportAndItsIssueAddressNeverContainThem() = runBlocking {
        val problem = Problem("stream-1", ProblemKind.STREAM_FAILED, 1, "Lost $pcName at $localIp", chatter)

        val prepared = ProblemReporter.prepare(context, problem)

        assertTrue(prepared.report.text.contains("== Problem =="))
        assertTrue(prepared.report.text.contains("Connecting to"))
        assertNoSecrets("the saved report", prepared.report.text)
        assertNoSecrets("the issue address", prepared.url)
        assertNoSecrets("the decoded issue address", URLDecoder.decode(prepared.url, "UTF-8"))
        assertNoSecrets("the file name", prepared.fileName)
    }

    @Test
    fun theClipboardSummaryNeverContainsThem() = runBlocking {
        assertNoSecrets("the summary", DiagExport.summary(context).text)
    }

    @Test
    fun aPcRenamedInTheUnprofiledSettingsIsCoveredToo() = runBlocking {
        // Before the first profile exists the names given to PCs live in the default settings.
        PreferenceManager.getDefaultSharedPreferences(context).edit()
            .remove("ui_profiles").putString("ui_pc_name_$pcUuid", "Attic render node").commit()

        val report = DiagExport.report(context, sections = listOf("Last error" to "Lost Attic render node"))

        assertTrue(report.text.contains("== Last error =="))
        assertFalse(report.text.contains("Attic render node"))
    }
}
