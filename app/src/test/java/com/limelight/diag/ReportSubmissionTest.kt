package com.limelight.diag

import kotlinx.coroutines.runBlocking
import okhttp3.OkHttpClient
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.net.URI
import java.net.URLDecoder
import java.util.concurrent.TimeUnit

/** What a report does: upload and link, or save to Downloads, and the address of the GitHub draft in every case. */
class ReportSubmissionTest {
    private val servers = mutableListOf<ReportServer>()
    private val id = "AbCdEfGhIjKlMnOpQrStUv"

    @After fun stop() = servers.forEach { it.close() }

    private val info = DeviceInfo(
        "0.1.0", "release", 100, "AYN", "Odin2 Portal", "odin2portal", "13", 33, "2026-09-05", "TKQ1", "QTI SM8550", "arm64-v8a",
        "Adreno (TM) 740", "1920x1080 at 120.0 Hz, 400 dpi", "Wi-Fi", "en-US", "UTC+01:00", listOf("Codec" to "hevc"),
    )
    private val problem = Problem("crash-1", ProblemKind.JAVA_CRASH, 1, "IllegalStateException: boom", "java.lang.IllegalStateException: boom\n\tat a.B.c(B.kt:1)")
    private val report = DiagnosticReport("Delos diagnostic report\n== Log (1 lines, oldest first) ==\nline\n", 1)
    private val scrubber = LogScrubber()
    private val prepared = ProblemReporter.Prepared(report, "delos-diagnostic-20261120-100000.txt") { attachment ->
        IssueUrl.build(ProblemIssue.draft(problem, info, attachment), scrub = scrubber::scrub)
    }

    private fun server(answer: (SeenRequest) -> ServerAnswer): ReportServer = ReportServer(answer).also { servers += it }

    private fun working(): ReportServer {
        lateinit var base: String
        val server = server { ServerAnswer(201, """{"id":"$id","url":"$base/r/$id","expires":"2026-12-20T10:00:00.000Z","rescrubbed":0}""") }
        base = server.url
        return server
    }

    private fun run(mode: ReportMode, uploader: ReportUploader, saved: MutableList<String>? = mutableListOf(), saves: Boolean = true) = runBlocking {
        ReportSubmission.run(prepared, mode, uploader) { r, name -> saved?.add(name + ":" + r.text.length); saves }
    }

    private fun what(url: String): String = URI(url).rawQuery.split('&').first { it.startsWith("what-happened=") }
        .substringAfter('=').let { URLDecoder.decode(it, "UTF-8") }

    @Test fun anUploadPutsTheLinkIntoTheDraftAndSavesNothing() {
        val server = working()
        val saved = mutableListOf<String>()
        val result = run(ReportMode.UPLOAD, ReportUploader(server.url), saved) as ReportResult.Uploaded
        assertEquals("${server.url}/r/$id", result.link)
        assertEquals("2026-12-20T10:00:00.000Z", result.expires)
        assertEquals(report.text, server.requests.single().text)
        assertTrue("nothing is saved to Downloads after an upload", saved.isEmpty())
        val what = what(result.issueUrl)
        assertTrue(what, what.startsWith("Diagnostic log: ${server.url}/r/$id\n\n"))
        assertTrue(what, what.contains("Delos stopped unexpectedly the last time."))
        assertTrue(what, what.contains("2026-12-20"))
        assertFalse("no file name when the log is a link", what.contains(".txt"))
        assertTrue(result.issueUrl.length.toString(), result.issueUrl.length <= IssueUrl.MAX_LENGTH)
        assertTrue(result.issueUrl.startsWith("https://github.com/DELOS-STREAM/DELOS-android/issues/new?template=app_report.yml"))
    }

    @Test fun aFailedUploadFallsBackToDownloadsAndSaysWhy() {
        for ((status, reason) in mapOf(429 to UploadFailure.RATE_LIMITED, 500 to UploadFailure.SERVICE_ERROR, 413 to UploadFailure.TOO_LARGE)) {
            val server = server { ServerAnswer(status, """{"error":"x"}""") }
            val saved = mutableListOf<String>()
            val result = run(ReportMode.UPLOAD, ReportUploader(server.url), saved) as ReportResult.Saved
            assertEquals(reason, result.uploadFailure)
            assertEquals(prepared.fileName, result.fileName)
            assertEquals(listOf("${prepared.fileName}:${report.text.length}"), saved)
            val what = what(result.issueUrl)
            assertTrue(what, what.contains("`${prepared.fileName}`"))
            assertFalse(what, what.contains("Diagnostic log:"))
        }
    }

    @Test fun aServiceThatDoesNotAnswerFallsBackToo() {
        val server = server { ServerAnswer(hang = true) }
        val quick = OkHttpClient.Builder().readTimeout(300, TimeUnit.MILLISECONDS).build()
        val result = run(ReportMode.UPLOAD, ReportUploader(server.url, quick)) as ReportResult.Saved
        assertEquals(UploadFailure.TIMEOUT, result.uploadFailure)
    }

    @Test fun choosingDownloadsUploadsNothing() {
        val server = working()
        val result = run(ReportMode.DOWNLOADS, ReportUploader(server.url)) as ReportResult.Saved
        assertEquals(null, result.uploadFailure)
        assertTrue("nothing reached the service", server.requests.isEmpty())
        assertTrue(what(result.issueUrl).contains("Downloads"))
    }

    @Test fun withoutAServiceAddressTheFlowIsTheOldOne() {
        val result = run(ReportMode.UPLOAD, ReportUploader("")) as ReportResult.Saved
        assertEquals(null, result.uploadFailure)
        assertEquals(prepared.fileName, result.fileName)
    }

    @Test fun whenNothingCanBeSavedTheDraftStillOpens() {
        val failing = server { ServerAnswer(500) }
        val afterFailure = run(ReportMode.UPLOAD, ReportUploader(failing.url), saves = false) as ReportResult.NotSaved
        assertEquals(UploadFailure.SERVICE_ERROR, afterFailure.uploadFailure)
        assertTrue(afterFailure.issueUrl.startsWith(IssueUrl.NEW_ISSUE))
        val byChoice = run(ReportMode.DOWNLOADS, ReportUploader(""), saves = false) as ReportResult.NotSaved
        assertEquals(null, byChoice.uploadFailure)
    }

    @Test fun aHugeReportIsCutToWhatTheServiceAcceptsBeforeTheUpload() {
        val server = working()
        val log = (1..40_000).joinToString("\n") { "2026-11-10 12:34:56.789 I/Stream [main] a log line of ordinary length number $it" }
        val head = "Delos diagnostic report\n== Device ==\nModel x\n== Problem ==\n${ProblemIssue.section(problem, "2026-11-10 12:34:56")}\n"
        val big = DiagnosticReport("$head== Log (40000 lines, oldest first) ==\n$log\n", 40_000)
        assertEquals(128 * 1024, ReportUploader.MAX_BYTES)
        assertTrue(big.bytes > ReportUploader.MAX_BYTES)
        val result = runBlocking {
            ReportSubmission.run(ProblemReporter.Prepared(big, "f.txt") { prepared.issueUrl(it) }, ReportMode.UPLOAD, ReportUploader(server.url)) { _, _ -> true }
        }
        assertTrue(result is ReportResult.Uploaded)
        val sent = server.requests.single().text
        assertTrue(sent.toByteArray().size <= ReportUploader.MAX_BYTES)
        assertTrue("the header, the device block and the problem stay whole", sent.startsWith(head))
        assertTrue(sent.contains("number 40000"))
        assertFalse(sent.contains("number 1\n"))
    }

    @Test fun theLogLinkOfAnotherHostNeverReachesTheDraft() {
        // A service that answers with a link elsewhere is a failed upload, not a draft that points there
        val server = server { ServerAnswer(201, """{"id":"$id","url":"https://evil.example/r/$id","expires":"x"}""") }
        val result = run(ReportMode.UPLOAD, ReportUploader(server.url)) as ReportResult.Saved
        assertEquals(UploadFailure.MALFORMED, result.uploadFailure)
        assertFalse(result.issueUrl.contains("evil.example"))
    }
}
