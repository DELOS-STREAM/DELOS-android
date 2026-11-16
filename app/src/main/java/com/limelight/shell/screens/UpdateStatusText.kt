package com.limelight.shell.screens

import android.content.res.Resources
import com.limelight.R
import com.limelight.update.CheckResult
import com.limelight.update.UpdateErrorKind

/** The value of the Check now row after a check: what the user wants to know in a few words. */
internal fun updateResultText(res: Resources, result: CheckResult): String = when (result) {
    CheckResult.Disabled -> res.getString(R.string.settings_native_update_result_disabled)
    CheckResult.DebugBuild -> res.getString(R.string.settings_native_update_result_debug)
    is CheckResult.UpToDate -> res.getString(R.string.settings_native_update_result_current)
    is CheckResult.Available -> res.getString(R.string.settings_native_update_result_available, result.candidate.version.toString())
    is CheckResult.NoDownload -> res.getString(R.string.settings_native_update_result_no_download, result.tag.removePrefix("v"))
    is CheckResult.Failed -> res.getString(
        when (result.kind) {
            UpdateErrorKind.NETWORK -> R.string.settings_native_update_result_network
            UpdateErrorKind.RATE_LIMITED -> R.string.settings_native_update_result_rate_limited
            else -> R.string.settings_native_update_result_failed
        },
    )
}
