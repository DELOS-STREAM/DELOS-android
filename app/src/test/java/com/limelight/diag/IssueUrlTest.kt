package com.limelight.diag

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.net.URI
import java.net.URLDecoder

class IssueUrlTest {
    private val draft = IssueDraft(
        title = "Crash: IllegalStateException: boom",
        whatHappened = "Delos stopped unexpectedly the last time.\nIt was reported from the app.",
        device = "AYN Odin2 Portal, Android 13",
        build = "0.1.0 (release)",
        streamSettings = "hevc, 1920x1080, 60 fps, HDR off",
        logs = "java.lang.IllegalStateException: boom\n\tat com.limelight.Foo.bar(Foo.kt:1)",
        area = "Other",
    )

    private fun params(url: String): Map<String, String> = URI(url).rawQuery.split('&').associate {
        val (k, v) = it.split('=', limit = 2)
        k to URLDecoder.decode(v, "UTF-8")
    }

    @Test fun theAddressOpensTheFormMadeForTheApp() {
        val url = IssueUrl.build(draft)
        assertTrue(url, url.startsWith("https://github.com/DELOS-STREAM/DELOS-android/issues/new?template=app_report.yml&title="))
        assertEquals("app_report.yml", params(url)["template"])
    }

    @Test fun theAddressAsksForNoLabel() {
        // GitHub documents "404 Not Found" for a labels parameter from someone who may not label issues: the form sets them.
        assertFalse(params(IssueUrl.build(draft)).containsKey("labels"))
    }

    @Test fun everyFieldUsesTheIdOfTheFormAndSurvivesTheEscaping() {
        val p = params(IssueUrl.build(draft))
        assertEquals(draft.title, p["title"])
        assertEquals(draft.whatHappened, p["what-happened"])
        assertEquals("Other", p["area"])
        assertEquals(draft.device, p["device"])
        assertEquals(draft.build, p["build"])
        assertEquals(draft.streamSettings, p["stream-settings"])
        assertEquals(draft.logs, p["logs"])
    }

    @Test fun reservedCharactersNeverEndUpRawInAValue() {
        val url = IssueUrl.build(draft.copy(whatHappened = "a&b=c#d?e f+g%h\n\"q\" <x> é"))
        val query = url.substringAfter('?')
        assertEquals("eight parameters, so seven separators and no raw & inside a value", 7, query.count { it == '&' })
        assertFalse(query.contains(' ') || query.contains('#') || query.contains('<') || query.contains('"') || query.contains('\n'))
        assertEquals("a&b=c#d?e f+g%h\n\"q\" <x> é", params(url)["what-happened"])
    }

    @Test fun aHugeStackTraceIsCutToTheLimitAndSaysSo() {
        val trace = (1..2000).joinToString("\n") { "\tat com.limelight.Frame$it.run(Frame.kt:$it)" }
        val url = IssueUrl.build(draft.copy(logs = trace))
        assertTrue("${url.length}", url.length <= IssueUrl.MAX_LENGTH)
        val logs = params(url)["logs"]!!
        assertTrue(logs.startsWith("\tat com.limelight.Frame1.run"))
        assertTrue(logs.endsWith("the full log is in the attached file)"))
        assertEquals(draft.whatHappened, params(url)["what-happened"])
    }

    @Test fun aDescriptionTooLongForTheAddressIsCutAfterTheLog() {
        val long = "What I did before the crash. ".repeat(2000)
        val url = IssueUrl.build(draft.copy(whatHappened = long))
        assertTrue("${url.length}", url.length <= IssueUrl.MAX_LENGTH)
        val p = params(url)
        // The log goes first and entirely; what is left of the description is its beginning
        assertEquals("", p["logs"])
        assertTrue(p["what-happened"]!!.isNotEmpty())
        assertTrue(long.startsWith(p["what-happened"]!!))
        assertEquals(draft.device, p["device"])
        assertEquals(draft.title, p["title"])
    }

    @Test fun aSmallerLimitStillHoldsAndNeverCutsInsideAnEscape() {
        val url = IssueUrl.build(draft.copy(logs = "é".repeat(5000)), maxLength = 900)
        assertTrue(url.length <= 900)
        assertFalse(Regex("%[0-9A-F]?$").containsMatchIn(url))
        params(url)
    }

    @Test fun everyFreeTextFieldPassesTheScrubber() {
        val scrubber = LogScrubber(ScrubContext(pcNames = listOf("Meiton-PC")))
        val dirty = draft.copy(
            title = "Stream to Meiton-PC failed", whatHappened = "Could not reach Meiton-PC at 192.168.0.200",
            device = "Phone of Meiton-PC owner", streamSettings = "host 192.168.0.200", logs = "poll Meiton-PC (192.168.0.200:47989)",
        )
        val url = IssueUrl.build(dirty, scrub = scrubber::scrub)
        val text = params(url).values.joinToString("\n")
        assertFalse(text, text.contains("Meiton-PC") || text.contains("192.168.0.200"))
        assertTrue(text.contains("<pc-1>"))
    }

    @Test fun theAreaIsLeftOutWhenUnset() {
        assertFalse(IssueUrl.build(draft.copy(area = null)).contains("&area="))
    }

    @Test fun theTitleIsCapped() {
        assertEquals(120, params(IssueUrl.build(draft.copy(title = "x".repeat(500))))["title"]!!.length)
    }

    @Test fun theLogLinkIsTheFirstLineOfWhatHappenedAndIsNotScrubbed() {
        val link = "https://delos-reports.example.workers.dev/r/AbCdEfGhIjKlMnOpQrStUv"
        // The scrubber replaces an unknown host, as it must for text; the link of the report service is added after it
        val scrubber = LogScrubber()
        val url = IssueUrl.build(draft.copy(logLink = link), scrub = scrubber::scrub)
        assertEquals("Diagnostic log: $link\n\n" + draft.whatHappened, params(url)["what-happened"])
        assertTrue(scrubber.scrub(link).contains("<host>"))
    }

    @Test fun theLinkSurvivesTheLengthLimitWhileTheTextIsCut() {
        val link = "https://delos-reports.example.workers.dev/r/AbCdEfGhIjKlMnOpQrStUv"
        val long = draft.copy(logLink = link, whatHappened = "word ".repeat(3000), logs = "line\n".repeat(3000))
        val url = IssueUrl.build(long)
        assertTrue(url.length.toString(), url.length <= IssueUrl.MAX_LENGTH)
        assertTrue(params(url)["what-happened"]!!.startsWith("Diagnostic log: $link\n\n"))
        // Even when "What happened?" has to give up everything, the link stays
        val tiny = IssueUrl.build(long, maxLength = 700)
        assertTrue(params(tiny)["what-happened"]!!.startsWith("Diagnostic log: $link"))
    }

    @Test fun noLinkMeansNoLinkLine() {
        assertEquals(draft.whatHappened, params(IssueUrl.build(draft))["what-happened"])
    }

    @Test fun theNameOfTheSavedFileIsNotTakenForATokenByTheScrubber() {
        // "delos-diagnostic-20261110-120000" has 32 characters of letters and digits, which the scrubber's token rule removes
        val name = "delos-diagnostic-20261110-120000.txt"
        val scrubber = LogScrubber()
        assertTrue(scrubber.scrub(name).startsWith("<token>"))
        val info = DeviceInfo(
            "0.1.0", "release", 100, "AYN", "Odin2 Portal", "odin2portal", "13", 33, "2026-09-05", "TKQ1", "QTI SM8550", "arm64-v8a",
            "Adreno (TM) 740", "1920x1080 at 120.0 Hz, 400 dpi", "Wi-Fi", "en-US", "UTC+01:00", emptyList(),
        )
        val problem = Problem("c", ProblemKind.JAVA_CRASH, 1, "IllegalStateException: boom", "trace")
        val url = IssueUrl.build(ProblemIssue.draft(problem, info, ReportAttachment.File(name)), scrub = scrubber::scrub)
        assertTrue(params(url)["what-happened"]!!.contains("`$name`"))
    }

    @Test fun theAttachmentNoteComesLastAndIsNeverCut() {
        val note = "The diagnostic log was saved by Delos as `delos-diagnostic-20261110-120000.txt` in the Downloads folder."
        val url = IssueUrl.build(draft.copy(whatHappened = "word ".repeat(3000), attachmentNote = note))
        assertTrue(url.length <= IssueUrl.MAX_LENGTH)
        assertTrue(params(url)["what-happened"]!!.endsWith("\n\n$note"))
        assertEquals(draft.whatHappened, params(IssueUrl.build(draft))["what-happened"])
    }
}
