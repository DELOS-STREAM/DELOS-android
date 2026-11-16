package com.limelight.diag

import com.google.gson.GsonBuilder
import com.google.gson.JsonParser
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * The contract between [LogScrubber] and the JavaScript port in `tools/report-worker/src/scrub.js` that checks every report a
 * second time before it is stored (docs/DIAGNOSTICS.md, "The report service"). The lines below are scrubbed by the app and
 * written to `tools/report-worker/test/fixtures/app-scrub-contract.json`; the Worker's tests read that file and require that
 * its rules (a) leave the app's output unchanged and (b) produce the app's output from the raw line when the line needs no
 * knowledge of this install. This test fails when the app's scrubber changes without the file: run it with
 * `DELOS_UPDATE_SCRUB_CONTRACT=1` to rewrite the file, then run the Worker tests.
 */
class ScrubContractTest {
    private val context = ScrubContext(
        pcNames = listOf("Meiton-PC", "Living Room"),
        hostAddresses = listOf("meiton.example.org"),
        names = listOf("Meiton", "Odin Portal"),
        identifiers = listOf("0123456789abcdef"),
        secrets = listOf("sgdbSecretKey99"),
    )

    /** A raw line and whether the patterns alone (no known names) produce the app's result. */
    private class Case(val raw: String, val patternsOnly: Boolean)

    private val cases = listOf(
        Case("Starting parallel poll for Meiton-PC (192.168.0.200:47989, null, null, null)", false),
        Case("https://192.168.0.200:47984/applist?devicename=roth&uniqueid=0123456789ABCDEF&uuid=11111111-2222-3333-4444-555555555555", true),
        Case("http://10.0.0.5:47989/launch?appid=1234&mode=1920x1080x60&rikey=00112233445566778899aabbccddeeff", true),
        Case("GET https://user:hunter2@meiton.example.org/api/v1/status?token=abc123 failed", true),
        Case("GET https://www.steamgriddb.com/api/v2/grids/game/5254?types=static", true),
        Case("GET https://github.com/DELOS-STREAM/DELOS-android/issues/new?template=app_report.yml&title=x", true),
        Case("UID is now: fedcba9876543210", true),
        Case("192.168.0.200 -> 8.8.8.8 -> 192.168.0.200", true),
        Case("version 999.1.2.3 built", true),
        Case("addr fe80::1c2d:3eff:fe4f:5a6b%wlan0 used", true),
        Case("connect [2001:db8::1]:47989", true),
        Case("mapped 2001:db8:0:0:0:0:0:1", true),
        Case("mapped ::ffff:192.168.1.7", true),
        Case("seen fe80::1.", true),
        Case("2026-11-10 12:34:56.789 I/Lime [main] ratio 16:9 at 12:34:56 on port 47989 (Foo::bar)", true),
        Case("wake 00:1A:2B:3C:4D:5E and aa-bb-cc-dd-ee-ff and 00:1a:2b:3c:4d:5e", true),
        Case("api_key=abcDEF123", true),
        Case("Authorization: Bearer abc.def-ghi", true),
        Case("sent 9fB3kQ7xL2mZ8vC1nR5tY0uW4pH6jD3s done", true),
        Case("steamgriddb 0123456789abcdef0123456789abcdef", true),
        Case("/Users/meiton/Library/Caches/x", true),
        Case("C:\\Users\\Meiton\\AppData\\x", true),
        Case("/home/bob/.config", true),
        Case("/storage/1A2B-3C4D/Download/log.txt", true),
        Case("/storage/emulated/0/Download/log.txt", true),
        Case("connected to SSID: \"Home Wifi 5G\"", true),
        Case("BSSID=aa:bb:cc:dd:ee:ff", true),
        Case("serial: R5CT1234ABC", true),
        Case("from bob@example.com", true),
        Case("cannot reach desktop-4f2k.local", true),
        Case("java.net.UnknownHostException: gamingrig", true),
        Case("Unable to resolve host \"gamingrig\": No address", true),
        Case("2026-11-10 12:34:56.789 I/Stream [main] Negotiated HEVC Main10 4:2:0 HDR at 1920x1080@120, bitrate 80000 kbps", true),
        Case("Process started: Delos 0.1.0 (debug), pid 12345", true),
        Case("poll Meiton-PC (192.168.0.200:47989) https://192.168.0.200:47984/applist?uniqueid=0123456789abcdef mac 00:1A:2B:3C:4D:5E", false),
        Case("living room is online, MEITON-PC's games, homepage; hello meiton on Odin Portal", false),
        Case("resolving meiton.example.org failed; key sgdbSecretKey99 rejected; UID is now: 0123456789abcdef", false),
        Case("Sending WoL to 00:1A:2B:3C:4D:5E for Living Room via meiton.example.org; password=hunter2; SSID: \"Home Wifi\" owner meiton@example.com", false),
    )

    /** A whole report as the app builds it: the header, the device block, a problem section and a log with every kind of line. */
    private fun fullReport(): Case {
        val info = DeviceInfo(
            "0.15.0", "release", 1500, "AYN", "Odin2 Portal", "odin2portal", "13", 33, "2026-09-05", "TKQ1.240503.001", "QTI SM8550", "arm64-v8a",
            "Adreno (TM) 740", "1920x1080 at 120.0 Hz, 400 dpi", "Wi-Fi", "en-US", "UTC+01:00",
            listOf("Codec" to "hevc", "Resolution" to "1920x1080", "Frame rate" to "60", "HDR" to "off"),
        )
        val log = cases.joinToString("\n") { "2026-11-10 12:34:56.789 I/Lime [main] ${it.raw}" } + "\n" +
            "2026-11-10 12:34:57.001 W/Stream [Thread-9] Connection to https://www.steamgriddb.com/api/v2?x=1 took 5 s\n"
        val problem = ProblemIssue.section(
            Problem("p", ProblemKind.JAVA_CRASH, 1, "IllegalStateException: boom", "java.lang.IllegalStateException: boom\n\tat com.limelight.Foo.bar(Foo.kt:12)"),
            "2026-11-10 12:34:56.789 (device time)",
        )
        val report = DiagnosticReports.build(info, log, LogScrubber(context), "2026-11-10 12:34:56 (device time)", listOf("Problem" to problem))
        return Case(log, false).also { fullReportText = report.text }
    }

    private var fullReportText = ""

    private fun file(): File = File("../tools/report-worker/test/fixtures/app-scrub-contract.json")

    @Test
    fun theFileHoldsWhatTheAppScrubberProducesNow() {
        val gson = GsonBuilder().setPrettyPrinting().disableHtmlEscaping().create()
        val rows = cases.map { case ->
            mapOf("raw" to case.raw, "scrubbed" to LogScrubber(context).scrub(case.raw), "patternsOnly" to case.patternsOnly)
        } + fullReport().let { mapOf("raw" to "(a whole report)", "scrubbed" to fullReportText, "patternsOnly" to false) }
        val current = gson.toJson(mapOf("maxBytes" to ReportUploader.MAX_BYTES, "cases" to rows)) + "\n"
        if (System.getenv("DELOS_UPDATE_SCRUB_CONTRACT") == "1") file().writeText(current)
        assertTrue("${file().path} is missing: run this test with DELOS_UPDATE_SCRUB_CONTRACT=1", file().exists())
        assertEquals(
            "The app's scrubber and ${file().path} differ: run this test with DELOS_UPDATE_SCRUB_CONTRACT=1 and then the Worker tests",
            JsonParser.parseString(current), JsonParser.parseString(file().readText()),
        )
    }

    @Test
    fun theContextIndependentCasesNeedNoKnownNames() {
        for (case in cases.filter { it.patternsOnly }) {
            assertEquals(LogScrubber(context).scrub(case.raw), LogScrubber(ScrubContext()).scrub(case.raw))
        }
    }
}
