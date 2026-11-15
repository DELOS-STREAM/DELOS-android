package com.limelight.diag

import java.net.URLEncoder

/**
 * The fields of the repository's report form for the app that the app can fill in. [logs] is the one that gets cut when the
 * address would be too long; the full log is a file the user attaches.
 */
data class IssueDraft(
    val title: String,
    val whatHappened: String,
    val device: String,
    val build: String,
    val streamSettings: String,
    val logs: String,
    /** One of the form's Area options ([IssueUrl.AREA_OTHER], [IssueUrl.AREA_CONNECTION]), or null to leave it unset. */
    val area: String? = null,
)

/**
 * The GitHub new-issue address with the form made for the app (`.github/ISSUE_TEMPLATE/app_report.yml`) filled in through
 * query parameters named after the form's field ids. The file name and the ids are a contract with every installed app
 * (docs/DIAGNOSTICS.md, "Field-id contract of the issue forms"); `IssueFormContractTest` compares them with the form file.
 * Nothing is sent by this: the address is opened in the browser, where the user reads, edits and submits the issue. Every
 * free-text field passes [scrub] first and the whole address stays under [MAX_LENGTH] characters.
 *
 * The address carries no `labels` parameter: GitHub accepts it only from people who may label issues and documents a
 * "404 Not Found" page for everyone else. The form sets its labels (`bug`, `from-app`, `needs-triage`) itself, for everyone.
 */
object IssueUrl {
    const val NEW_ISSUE = "https://github.com/Meitoncz/Delos/issues/new"
    const val TEMPLATE = "app_report.yml"

    /** The two options of the form's Area dropdown that the app sends. */
    const val AREA_OTHER = "Other"
    const val AREA_CONNECTION = "Pairing/connection"

    /** Browsers and GitHub accept far longer addresses; this keeps clear of any proxy or intent limit. */
    const val MAX_LENGTH = 6000

    private const val CUT_NOTE = "\n… (cut; the full log is in the attached file)"

    fun build(draft: IssueDraft, scrub: (String) -> String = { it }, maxLength: Int = MAX_LENGTH): String {
        val title = scrub(draft.title).take(120)
        val what = scrub(draft.whatHappened)
        val device = scrub(draft.device).take(300)
        val build = scrub(draft.build).take(300)
        val settings = scrub(draft.streamSettings).take(300)
        val logs = scrub(draft.logs)

        fun url(logsText: String, whatText: String): String = buildString {
            append(NEW_ISSUE)
            append("?template=").append(encode(TEMPLATE))
            append("&title=").append(encode(title))
            append("&what-happened=").append(encode(whatText))
            draft.area?.let { append("&area=").append(encode(it)) }
            append("&device=").append(encode(device))
            append("&build=").append(encode(build))
            append("&stream-settings=").append(encode(settings))
            append("&logs=").append(encode(logsText))
        }

        var shownLogs = logs
        var result = url(shownLogs, what)
        // Cut the log first, a tenth at a time from the original (the cut note must not feed back into the length), then the
        // description; a cut is always in the raw text, never inside an escape
        var keep = logs.length
        while (result.length > maxLength && keep > 0) {
            keep = keep * 9 / 10
            shownLogs = if (keep < 40) "" else logs.take(keep).trimEnd() + CUT_NOTE
            result = url(shownLogs, what)
        }
        var shownWhat = what
        while (result.length > maxLength && shownWhat.isNotEmpty()) {
            shownWhat = shownWhat.take(shownWhat.length * 9 / 10).trimEnd()
            result = url(shownLogs, shownWhat)
        }
        return result
    }

    /** Percent-encoding with `%20` for a space, as the GitHub form expects. */
    internal fun encode(value: String): String = URLEncoder.encode(value, "UTF-8").replace("+", "%20")
}
