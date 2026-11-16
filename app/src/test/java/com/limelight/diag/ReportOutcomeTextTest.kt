package com.limelight.diag

import android.app.Application
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File

/** The toasts of a report and the privacy statement of the card, in English and in the other nine languages. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33], application = Application::class)
class ReportOutcomeTextTest {
    private val res = ApplicationProvider.getApplicationContext<Application>().resources
    private val link = "https://r.example/r/AbCdEfGhIjKlMnOpQrStUv"

    @Test fun anUploadIsConfirmedWithTheExpiry() {
        val text = ReportOutcomeText.text(res, ReportResult.Uploaded("u", link, "2026-12-20T10:00:00Z"))
        assertEquals("Log uploaded; its link is in the draft. It is kept for 30 days.", text)
        assertEquals(ReportUploader.KEPT_DAYS, 30)
    }

    @Test fun aSaveByChoiceIsTheOldToast() {
        assertEquals("Log saved to Downloads as f.txt. Attach it to the issue.", ReportOutcomeText.text(res, ReportResult.Saved("u", "f.txt", null)))
        assertEquals("The log could not be saved. Share it from Settings → About → Debug info.", ReportOutcomeText.text(res, ReportResult.NotSaved("u", null)))
    }

    @Test fun aFailedUploadNamesTheReasonAndWhatWasDoneInstead() {
        val saved = ReportOutcomeText.text(res, ReportResult.Saved("u", "f.txt", UploadFailure.OFFLINE))
        assertEquals("The log was not uploaded (no connection). Saved to Downloads as f.txt. Attach it to the issue.", saved)
        val notSaved = ReportOutcomeText.text(res, ReportResult.NotSaved("u", UploadFailure.RATE_LIMITED))
        assertEquals("The log was not uploaded (the report service is busy, try again later) and could not be saved. Share it from Settings → About → Debug info.", notSaved)
    }

    @Test fun everyFailureHasAReasonInHumanWords() {
        val reasons = UploadFailure.values().associateWith { ReportOutcomeText.reason(res, it) }
        assertTrue(reasons.toString(), reasons.values.all { it.isNotBlank() && it == it.lowercase() })
        assertEquals(reasons[UploadFailure.RATE_LIMITED], reasons[UploadFailure.DAILY_LIMIT])
        assertEquals(5, reasons.values.toSet().size)
    }

    @Test fun onlyAFailedSaveIsBadNews() {
        assertTrue(ReportOutcomeText.succeeded(ReportResult.Uploaded("u", link, "x")))
        assertTrue(ReportOutcomeText.succeeded(ReportResult.Saved("u", "f.txt", UploadFailure.TIMEOUT)))
        assertFalse(ReportOutcomeText.succeeded(ReportResult.NotSaved("u", null)))
    }

    @Test fun theUploadingToastIsShort() {
        assertEquals("Uploading the cleaned log…", ReportOutcomeText.uploading(res))
    }

    /** Where `strings_reports.xml` of every language is, from the module directory (the Gradle test working directory). */
    private val folders = File("src/main/res").listFiles { f -> f.isDirectory && (f.name == "values" || f.name.startsWith("values-")) }!!
        .filter { File(it, "strings_reports.xml").isFile }

    private fun text(folder: File, key: String): String =
        Regex("""<string name="$key">(.*?)</string>""").find(File(folder, "strings_reports.xml").readText())!!.groupValues[1]

    @Test fun allTenLanguagesTellTheSameFacts() {
        val languages = folders.map { it.name }.toSet()
        assertEquals(setOf("values", "values-cs", "values-de", "values-es", "values-fr", "values-ja", "values-ko", "values-pt-rBR", "values-uk", "values-zh-rCN"), languages)
        for (folder in folders) {
            // The facts of the privacy statement: kept 30 days, readable by anyone with the link, GitHub, nothing before the choice
            val note = text(folder, "shell_problem_note_upload")
            assertTrue("${folder.name}: $note", note.contains("30") && note.contains("GitHub") && note.contains("Delos"))
            assertTrue("${folder.name}: the toast names the 30 days", text(folder, "report_uploaded").contains("30"))
        }
    }

    @Test fun theEnglishCardTextIsTheOneOfTheOwner() {
        assertEquals(
            "Report sends a cleaned log to the Delos report service (kept 30 days, readable by anyone who has the link) and opens a GitHub draft for you to check. Nothing is sent before you choose.",
            res.getString(com.limelight.R.string.shell_problem_note_upload),
        )
        assertEquals("Save the log to Downloads instead", res.getString(com.limelight.R.string.report_save_downloads))
    }
}
