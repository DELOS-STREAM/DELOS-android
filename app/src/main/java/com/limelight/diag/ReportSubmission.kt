package com.limelight.diag

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * The decision of a report, without Android: which of upload and save happens, in which order, and what the draft says.
 * [ProblemReporter.report] wires it to the build's uploader and to MediaStore; the tests wire it to a loopback server.
 */
object ReportSubmission {
    suspend fun run(
        prepared: ProblemReporter.Prepared,
        mode: ReportMode,
        uploader: ReportUploader,
        save: suspend (DiagnosticReport, String) -> Boolean,
    ): ReportResult {
        var failure: UploadFailure? = null
        if (mode == ReportMode.UPLOAD && uploader.enabled) {
            val text = ReportUploader.fit(prepared.report.text)
            when (val upload = withContext(Dispatchers.IO) { uploader.upload(text) }) {
                is ReportUpload.Uploaded -> return ReportResult.Uploaded(
                    prepared.issueUrl(ReportAttachment.Link(upload.link, upload.expires)), upload.link, upload.expires,
                )
                is ReportUpload.Failed -> failure = upload.reason
            }
        }
        val url = prepared.issueUrl(ReportAttachment.File(prepared.fileName))
        return if (save(prepared.report, prepared.fileName)) ReportResult.Saved(url, prepared.fileName, failure)
        else ReportResult.NotSaved(url, failure)
    }
}
