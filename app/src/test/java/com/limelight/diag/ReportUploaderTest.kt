package com.limelight.diag

import okhttp3.OkHttpClient
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.net.InetAddress
import java.net.ServerSocket
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger

/** The upload of a cleaned report against a real local HTTP server (the client and the service's contract, tools/report-worker). */
class ReportUploaderTest {
    private val servers = mutableListOf<ReportServer>()
    private val id = "AbCdEfGhIjKlMnOpQrStUv"
    private val report = "Delos diagnostic report\n== Log (1 lines, oldest first) ==\n2026-11-10 12:34:56.789 I/Stream [main] started\n"

    @After fun stop() = servers.forEach { it.close() }

    private fun server(answer: (SeenRequest) -> ServerAnswer): ReportServer = ReportServer(answer).also { servers += it }

    private fun created(base: String, linkId: String = id, extra: String = "") =
        ServerAnswer(201, """{"id":"$id","url":"$base/r/$linkId","expires":"2026-12-20T10:00:00.000Z","rescrubbed":2$extra}""")

    private fun uploader(server: ReportServer, http: OkHttpClient = OkHttpClient()) = ReportUploader(server.url, http)

    private fun failureOf(upload: ReportUpload): UploadFailure = (upload as ReportUpload.Failed).reason

    @Test fun aSuccessfulUploadSendsGzippedUtf8TextAndReturnsTheLink() {
        lateinit var base: String
        val server = server { created(base) }
        base = server.url
        val result = uploader(server).upload(report)
        assertEquals(ReportUpload.Uploaded("${server.url}/r/$id", "2026-12-20T10:00:00.000Z", 2), result)
        val seen = server.requests.single()
        assertEquals("POST", seen.method)
        assertEquals("/v1/reports", seen.path)
        assertEquals("text/plain; charset=utf-8", seen.headers["content-type"])
        assertEquals("gzip", seen.headers["content-encoding"])
        assertEquals(report, seen.text)
    }

    @Test fun anUnusualButValidAnswerIsAccepted() {
        lateinit var base: String
        val server = server { ServerAnswer(201, """{"id":"$id","url":"$base/r/$id","expires":"x","unknown":[1,2]}""") }
        base = server.url
        assertEquals(ReportUpload.Uploaded("${server.url}/r/$id", "x", 0), uploader(server).upload(report))
    }

    @Test fun theStatusOfTheAnswerSaysWhyItFailed() {
        val cases = mapOf(
            429 to UploadFailure.RATE_LIMITED,
            413 to UploadFailure.TOO_LARGE,
            500 to UploadFailure.SERVICE_ERROR,
            502 to UploadFailure.SERVICE_ERROR,
            504 to UploadFailure.SERVICE_ERROR,
            400 to UploadFailure.REJECTED,
            404 to UploadFailure.REJECTED,
            415 to UploadFailure.REJECTED,
            200 to UploadFailure.REJECTED,
        )
        for ((status, expected) in cases) {
            val server = server { ServerAnswer(status, """{"error":"x"}""") }
            assertEquals("status $status", expected, failureOf(uploader(server).upload(report)))
        }
    }

    @Test fun theDailyLimitIsToldApartFromOtherServiceErrors() {
        val daily = server { ServerAnswer(503, """{"error":"daily_limit"}""", mapOf("Retry-After" to "3600")) }
        assertEquals(UploadFailure.DAILY_LIMIT, failureOf(uploader(daily).upload(report)))
        for (body in listOf("""{"error":"storage_unavailable"}""", "<html>maintenance</html>", "")) {
            val other = server { ServerAnswer(503, body) }
            assertEquals(body, UploadFailure.SERVICE_ERROR, failureOf(uploader(other).upload(report)))
        }
    }

    @Test fun aRedirectIsNotFollowed() {
        val target = server { created("http://127.0.0.1") }
        val redirecting = server { ServerAnswer(307, "", mapOf("Location" to "${target.url}/v1/reports")) }
        assertEquals(UploadFailure.REJECTED, failureOf(uploader(redirecting).upload(report)))
        assertTrue("the redirect target was never asked", target.requests.isEmpty())
    }

    @Test fun anAnswerThatIsNotTheServicesIsMalformed() {
        lateinit var base: String
        val bad = listOf(
            "not json", "", "[]", "{}", "null", "<html>sign in to this network</html>",
            """{"id":"$id","expires":"x"}""",
            """{"id":"short","url":"BASE/r/short","expires":"x"}""",
            """{"id":"$id","url":"https://evil.example/r/$id","expires":"x"}""",
            """{"id":"$id","url":"BASE/r/other0000000000000000a","expires":"x"}""",
            """{"id":"$id","url":"BASE/r/$id?x=1","expires":"x"}""",
            """{"id":"$id","url":"BASE/r/$id#f","expires":"x"}""",
            """{"id":"$id","url":"BASE/elsewhere/$id","expires":"x"}""",
            """{"id":"$id","url":"javascript:alert(1)","expires":"x"}""",
            """{"id":"$id","url":"BASE/r/$id"}""",
            """{"id":5,"url":"BASE/r/$id","expires":"x"}""",
        )
        val next = AtomicInteger()
        val server = server { ServerAnswer(201, bad[next.getAndIncrement()].replace("BASE", base)) }
        base = server.url
        for (case in bad) assertEquals(case, UploadFailure.MALFORMED, failureOf(uploader(server).upload(report)))
        assertEquals(bad.size, server.requests.size)
    }

    @Test fun aServiceThatDoesNotAnswerIsATimeout() {
        val server = server { ServerAnswer(hang = true) }
        val quick = OkHttpClient.Builder().readTimeout(300, TimeUnit.MILLISECONDS).build()
        assertEquals(UploadFailure.TIMEOUT, failureOf(uploader(server, quick).upload(report)))
    }

    @Test fun noServiceAtTheAddressIsOffline() {
        val closed = ServerSocket(0, 1, InetAddress.getByName("127.0.0.1")).use { it.localPort }
        assertEquals(UploadFailure.OFFLINE, failureOf(ReportUploader("http://127.0.0.1:$closed").upload(report)))
    }

    @Test fun aReportOverTheLimitIsNeverSent() {
        val server = server { ServerAnswer(201) }
        val big = "x".repeat(ReportUploader.MAX_BYTES + 1)
        assertEquals(UploadFailure.TOO_LARGE, failureOf(uploader(server).upload(big)))
        assertTrue(server.requests.isEmpty())
    }

    @Test fun withoutAnAddressTheFeatureIsOff() {
        assertFalse(ReportUploader("").enabled)
        assertFalse(ReportUploader("   ").enabled)
        assertFalse(ReportUploader("not a url").enabled)
        // Cleartext to the internet is refused (rule S5); loopback is for tests
        assertFalse(ReportUploader("http://reports.example.com").enabled)
        assertTrue(ReportUploader("https://reports.example.com").enabled)
        assertTrue(ReportUploader("http://127.0.0.1:8787").enabled)
        assertEquals(UploadFailure.REJECTED, failureOf(ReportUploader("").upload(report)))
    }

    @Test fun neverSendsTheReportAnywhereElse() {
        val server = server { created(url(it)) }
        uploader(server).upload(report)
        assertEquals(listOf("/v1/reports"), server.requests.map { it.path })
    }

    private fun url(request: SeenRequest) = "http://" + request.headers["host"]

    @Test fun aReportThatFitsIsSentAsItIs() {
        assertEquals(report, ReportUploader.fit(report))
    }

    @Test fun aReportThatIsTooBigLosesItsOldestLogLinesAndKeepsTheHeader() {
        val lines = (1..200).map { "2026-11-10 12:34:56.789 I/Stream [main] line number $it" }
        val text = "Delos diagnostic report\n== Device ==\nModel x\n== Log (200 lines, oldest first) ==\n" + lines.joinToString("\n") + "\n"
        val fitted = ReportUploader.fit(text, maxBytes = 2000)
        assertTrue(fitted.toByteArray().size <= 2000)
        assertTrue(fitted.startsWith("Delos diagnostic report\n== Device ==\nModel x\n== Log (200 lines, oldest first) ==\n"))
        assertTrue(fitted.contains("older log lines were left out"))
        assertTrue("the newest line stays", fitted.contains("line number 200"))
        assertFalse("the oldest line goes", fitted.contains("line number 1\n"))
        // The kept lines are the newest ones, in order
        val kept = fitted.lines().filter { it.contains("line number") }.map { it.substringAfter("number ").toInt() }
        assertEquals(kept.sorted(), kept)
        assertEquals(200, kept.last())
        assertNull(kept.firstOrNull { it > 200 })
    }
}
