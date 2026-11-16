package com.limelight.diag

/** Where the cleaned log of a report is, for the text of the draft. */
sealed interface ReportAttachment {
    /** Saved to the Downloads folder as [name]; the user attaches it in the browser. */
    data class File(val name: String) : ReportAttachment

    /** Uploaded to the report service: [link] is its address and [expires] the expiry (ISO 8601). */
    data class Link(val link: String, val expires: String) : ReportAttachment
}

/** Turns a [Problem] and the [DeviceInfo] into the prefilled report form: facts only, never a PC name or address. */
object ProblemIssue {
    fun draft(problem: Problem, info: DeviceInfo, attachment: ReportAttachment): IssueDraft {
        val title = when (problem.kind) {
            ProblemKind.JAVA_CRASH -> if (problem.viaExitRecord) titled("Crash", problem.subject) else "Crash: ${problem.subject}"
            ProblemKind.NATIVE_CRASH -> titled("Native crash", problem.subject)
            ProblemKind.ANR -> titled("Not responding", problem.subject)
            ProblemKind.UNCLEAN_EXIT -> "Delos stopped without closing normally"
            ProblemKind.STREAM_FAILED -> "Stream failed: ${problem.subject}"
        }
        val what = buildString {
            appendLine(problem.kind.issueSentence)
            appendLine()
            append("(Created from the problem report in Delos. Please add what you were doing just before.)")
        }
        return common(info, attachment, title, what, logsText(problem), if (problem.kind == ProblemKind.STREAM_FAILED) IssueUrl.AREA_CONNECTION else IssueUrl.AREA_OTHER)
    }

    /** The draft of a report the user started without a detected problem (Debug info): the form's own title and log summary stay empty. */
    fun manualDraft(info: DeviceInfo, attachment: ReportAttachment): IssueDraft {
        return common(info, attachment, "", "(Created from the problem report in Delos. Please describe what happened and what you were doing.)", "", null)
    }

    private fun common(info: DeviceInfo, attachment: ReportAttachment, title: String, what: String, logs: String, area: String?) = IssueDraft(
        title = title,
        whatHappened = what,
        device = "${info.manufacturer} ${info.model}, Android ${info.androidRelease} (API ${info.sdk})",
        build = "Delos ${info.delosVersion} (${info.buildType})",
        streamSettings = info.settings.joinToString(", ") { (label, value) -> "$label $value" },
        logs = logs,
        area = area,
        logLink = (attachment as? ReportAttachment.Link)?.link,
        attachmentNote = attachmentSentence(attachment),
    )

    private fun attachmentSentence(attachment: ReportAttachment): String = when (attachment) {
        is ReportAttachment.File -> "The diagnostic log was saved by Delos as `${attachment.name}` in the Downloads folder. I attach it to this issue."
        is ReportAttachment.Link ->
            "The cleaned diagnostic log was uploaded by Delos to its report service; the link above is readable by anyone who has it" +
                " and works until ${attachment.expires.take(10)}."
    }

    private fun titled(what: String, subject: String) = if (subject.isBlank()) what else "$what: $subject"

    /** The summary and the head of the stack trace (or the system's description): what fits an address; the file has the rest. */
    fun logsText(problem: Problem): String {
        val detail = problem.detail.lines().let { lines ->
            val head = lines.takeWhile { !it.trim().startsWith("at ") }
            val frames = lines.filter { it.trim().startsWith("at ") }.take(12)
            (head + frames).joinToString("\n")
        }
        return listOf(problem.summary, detail).filter { it.isNotBlank() }.distinct().joinToString("\n\n")
    }

    /** The report's "Problem" section: the same facts with the full stack trace. */
    fun section(problem: Problem, detectedAt: String): String = buildString {
        appendLine("Kind: ${problem.kind}")
        appendLine("Detected: $detectedAt")
        appendLine("Summary: ${problem.summary}")
        if (problem.detail.isNotBlank()) {
            appendLine()
            append(problem.detail)
        }
    }.trimEnd()
}
