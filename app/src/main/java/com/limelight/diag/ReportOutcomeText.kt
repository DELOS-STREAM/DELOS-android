package com.limelight.diag

import android.content.res.Resources
import com.limelight.R

/** The toasts of a report, in the language of the user: what happened to the log and, after a failed upload, why. */
object ReportOutcomeText {
    fun uploading(res: Resources): String = res.getString(R.string.report_uploading)

    fun text(res: Resources, result: ReportResult): String = when (result) {
        is ReportResult.Uploaded -> res.getString(R.string.report_uploaded)
        is ReportResult.Saved ->
            if (result.uploadFailure == null) res.getString(R.string.shell_report_saved, result.fileName)
            else res.getString(R.string.report_upload_failed_saved, reason(res, result.uploadFailure), result.fileName)
        is ReportResult.NotSaved ->
            if (result.uploadFailure == null) res.getString(R.string.shell_report_save_failed)
            else res.getString(R.string.report_upload_failed_not_saved, reason(res, result.uploadFailure))
    }

    /** True when the toast is good news: the log is on the service or in Downloads. */
    fun succeeded(result: ReportResult): Boolean = result !is ReportResult.NotSaved

    fun reason(res: Resources, failure: UploadFailure): String = res.getString(
        when (failure) {
            UploadFailure.OFFLINE -> R.string.report_reason_offline
            UploadFailure.TIMEOUT -> R.string.report_reason_timeout
            UploadFailure.RATE_LIMITED, UploadFailure.DAILY_LIMIT -> R.string.report_reason_busy
            UploadFailure.TOO_LARGE -> R.string.report_reason_too_large
            UploadFailure.SERVICE_ERROR, UploadFailure.REJECTED, UploadFailure.MALFORMED -> R.string.report_reason_service
        },
    )
}
