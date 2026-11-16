package com.limelight.diag

import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Environment
import android.provider.MediaStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import java.util.Locale

/** What the user chose to do with the log of a report. */
enum class ReportMode {
    /** Upload the cleaned log to the report service and put its link into the draft; save it to Downloads when that fails. */
    UPLOAD,
    /** Save the cleaned log to Downloads for the user to attach; nothing is uploaded. */
    DOWNLOADS,
}

/** What a report did, for the toast and for the browser: [issueUrl] is the prefilled GitHub draft in every case. */
sealed interface ReportResult {
    val issueUrl: String

    /** The log is on the report service; its [link] is in the draft and expires at [expires] (ISO 8601). */
    data class Uploaded(override val issueUrl: String, val link: String, val expires: String) : ReportResult

    /** The log is in Downloads as [fileName]. [uploadFailure] is why the upload did not happen; null when Downloads was chosen. */
    data class Saved(override val issueUrl: String, val fileName: String, val uploadFailure: UploadFailure?) : ReportResult

    /** Neither the upload nor the save worked (or only the save was asked for and failed): the draft opens without a log. */
    data class NotSaved(override val issueUrl: String, val uploadFailure: UploadFailure?) : ReportResult
}

/**
 * What "Report this problem" does, in the order the user sees it (docs/DIAGNOSTICS.md): the cleaned report is uploaded to the
 * Delos report service and the GitHub new-issue page opens in the browser with the form prefilled and the link in it. When
 * the upload is off in this build, fails, or the user chose to save the file instead, the whole cleaned log is saved to the
 * Downloads folder and the user attaches it. Nothing happens before the user starts it and the app holds no account or token.
 */
object ProblemReporter {
    class Prepared(val report: DiagnosticReport, val fileName: String, private val addressOf: (ReportAttachment) -> String) {
        /** The prefilled GitHub address for the log at [attachment]. */
        fun issueUrl(attachment: ReportAttachment): String = addressOf(attachment)
    }

    /**
     * Builds the scrubbed report (with the problem's section when there is a [problem]) and the way to the prefilled address,
     * off the main thread. A [problem] of null is a report the user started from Debug info.
     */
    suspend fun prepare(context: Context, problem: Problem?): Prepared = withContext(Dispatchers.IO) {
        val app = context.applicationContext
        val info = DeviceInfoCollector.collect(app)
        val scrubber = LogScrubber(ScrubContextCollector.collect(app))
        val fileName = "delos-diagnostic-${ZonedDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss", Locale.ROOT))}.txt"
        val report = DiagnosticReports.build(
            info, DiagLog.readAll(), scrubber, DiagExport.generated(),
            sections = if (problem == null) emptyList() else listOf("Problem" to ProblemIssue.section(problem, DiagExport.generated())),
        )
        Prepared(report, fileName) { attachment ->
            val draft = if (problem == null) ProblemIssue.manualDraft(info, attachment) else ProblemIssue.draft(problem, info, attachment)
            IssueUrl.build(draft, scrub = scrubber::scrub)
        }
    }

    /**
     * The whole report: [prepare], then [ReportSubmission.run] with the build's uploader and the MediaStore save. Returns what to
     * tell the user and which address to open.
     */
    suspend fun report(context: Context, problem: Problem?, mode: ReportMode): ReportResult {
        val prepared = prepare(context, problem)
        return ReportSubmission.run(prepared, mode, ReportUploader.shared) { report, name -> saveToDownloads(context, report, name) != null }
    }

    /**
     * Writes [report] to the Downloads folder through MediaStore (no permission needed on any supported Android version) and
     * returns its address, or null when that failed.
     */
    suspend fun saveToDownloads(context: Context, report: DiagnosticReport, fileName: String): Uri? = withContext(Dispatchers.IO) {
        val resolver = context.contentResolver
        val values = ContentValues().apply {
            put(MediaStore.Downloads.DISPLAY_NAME, fileName)
            put(MediaStore.Downloads.MIME_TYPE, "text/plain")
            put(MediaStore.Downloads.RELATIVE_PATH, Environment.DIRECTORY_DOWNLOADS)
            put(MediaStore.Downloads.IS_PENDING, 1)
        }
        val uri = runCatching { resolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, values) }.getOrNull() ?: return@withContext null
        val written = runCatching {
            resolver.openOutputStream(uri)?.use { it.write(report.text.toByteArray(Charsets.UTF_8)) } != null
        }.getOrDefault(false)
        if (!written) {
            runCatching { resolver.delete(uri, null, null) }
            return@withContext null
        }
        values.clear()
        values.put(MediaStore.Downloads.IS_PENDING, 0)
        runCatching { resolver.update(uri, values, null, null) }
        uri
    }

    /** The browser opening [url]; false when no app handles it. */
    fun openIssue(context: Context, url: String): Boolean =
        runCatching { context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }.isSuccess
}
