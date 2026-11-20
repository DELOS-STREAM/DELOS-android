package com.limelight.shell.ui

import android.content.res.Resources
import androidx.compose.ui.res.stringResource
import com.limelight.R
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.focusGroup
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.em
import com.limelight.diag.Problem
import com.limelight.diag.ProblemKind
import com.limelight.diag.ReportUploader
import dev.chrisbanes.haze.HazeState

@Composable
internal fun offerTitle(kind: ProblemKind): String = stringResource(if (kind == ProblemKind.STREAM_FAILED) R.string.shell_problem_title_stream else R.string.shell_problem_title_generic)

/** The sentence under the title: what happened the last time, by [kind]. */
internal fun problemSentence(res: Resources, kind: ProblemKind): String = res.getString(
    when (kind) {
        ProblemKind.JAVA_CRASH -> R.string.diag_problem_java_crash
        ProblemKind.NATIVE_CRASH -> R.string.diag_problem_native_crash
        ProblemKind.ANR -> R.string.diag_problem_anr
        ProblemKind.UNCLEAN_EXIT -> R.string.diag_problem_unclean_exit
        ProblemKind.STREAM_FAILED -> R.string.diag_problem_stream_failed
    },
)

/**
 * The line with the facts under the sentence: the kind and the technical subject (exception, signal, system description or
 * the title of the failed stream). The subject is data and stays as the system or the stream reported it.
 */
internal fun problemSummary(res: Resources, problem: Problem): String {
    val subject = problem.subject.take(Problem.SUMMARY_MAX)
    return when (problem.kind) {
        ProblemKind.JAVA_CRASH ->
            if (!problem.viaExitRecord) subject
            else if (subject.isBlank()) res.getString(R.string.diag_problem_summary_crash)
            else res.getString(R.string.diag_problem_summary_crash_with, subject)
        ProblemKind.NATIVE_CRASH ->
            if (subject.isBlank()) res.getString(R.string.diag_problem_summary_native_crash)
            else res.getString(R.string.diag_problem_summary_native_crash_with, subject)
        ProblemKind.ANR ->
            if (subject.isBlank()) res.getString(R.string.diag_problem_summary_anr)
            else res.getString(R.string.diag_problem_summary_anr_with, subject)
        ProblemKind.UNCLEAN_EXIT -> res.getString(R.string.diag_problem_summary_unclean_exit)
        ProblemKind.STREAM_FAILED -> subject
    }
}

/**
 * The offer after a problem (docs/DIAGNOSTICS.md, docs/GUI.md "Problem report card"): a glass card in the centre of the start screen
 * and the home screen, with two actions. It is not a dialog: nothing is dimmed, the rest of the screen stays usable, and it asks
 * nothing before leaving; only what lies under the card is blurred ([haze], the shell's screen). Focus starts on Report this problem;
 * A activates the focused button, left/right moves between the two, B is Dismiss (the shell's back handler). It is offered once per
 * problem. In a build with the report service ([ReportUploader.enabled]) the note says what Report uploads, and a second row offers
 * [onSaveToDownloads]: the same pill as Dismiss, without a glyph, for people who want no upload.
 */
@Composable
fun ProblemOfferCard(problem: Problem?, haze: HazeState, reducedMotion: Boolean, onReport: () -> Unit, onSaveToDownloads: () -> Unit, onDismiss: () -> Unit) {
    val colors = LocalDelosColors.current
    val uploads = remember { ReportUploader.shared.enabled }
    var shown by remember { mutableStateOf(problem) }
    if (problem != null) shown = problem
    val origin = TransformOrigin.Center
    // A compact stage centres the card between the status bar and the hint bar, 48 px from the sides, in the width that is left
    val stage = LocalStage.current
    val compact = stage.compact
    val bars = if (compact) Modifier.padding(
        top = (Compact.STATUS_BAR * stage.textScale + stage.safe.top).px,
        bottom = (Compact.HINT_BAR * stage.textScale + stage.safe.bottom).px,
    ) else Modifier
    Box(Modifier.fillMaxSize().then(bars), contentAlignment = Alignment.Center) {
        AnimatedVisibility(problem != null, enter = popEnter(origin, reducedMotion), exit = popExit(origin, reducedMotion)) {
            val p = shown ?: return@AnimatedVisibility
            val report = remember { FocusRequester() }
            InitialFocus(report, p.id)
            val shape = RoundedCornerShape(38.px)
            val res = textResources()
            Column(
                Modifier.width((if (compact) minOf(CARD_WIDTH, stage.stageWidth - 2f * 48f) else CARD_WIDTH).px).clip(shape)
                    .backdropBlur(haze, 24f, colors.surface).border(1.px, colors.line, shape).padding((if (compact) 38 else 48).px).focusGroup(),
                verticalArrangement = Arrangement.spacedBy(24.px),
            ) {
                Row(horizontalArrangement = Arrangement.spacedBy(28.px), verticalAlignment = Alignment.CenterVertically) {
                    Box(Modifier.size(84.px).clip(CircleShape).background(colors.accent), contentAlignment = Alignment.Center) {
                        Symbol(Symbols.DEBUG, 46.tpx, colors.onAccent, filled = true)
                    }
                    Column(verticalArrangement = Arrangement.spacedBy(10.px)) {
                        BasicText(offerTitle(p.kind), style = TextStyle(color = colors.text, fontFamily = Unbounded, fontWeight = FontWeight.SemiBold, fontSize = 28.tpx, lineHeight = 1.3.em))
                        BasicText(problemSentence(res, p.kind), style = TextStyle(color = colors.text2, fontFamily = Figtree, fontSize = 22.tpx, lineHeight = 1.45.em))
                    }
                }
                BasicText(
                    problemSummary(res, p),
                    style = TextStyle(color = colors.text3, fontFamily = Figtree, fontSize = 18.tpx, lineHeight = 1.45.em),
                    maxLines = 2, overflow = TextOverflow.Ellipsis,
                )
                BasicText(stringResource(if (uploads) R.string.shell_problem_note_upload else R.string.shell_problem_note), style = TextStyle(color = colors.text2, fontFamily = Figtree, fontSize = 20.tpx, lineHeight = 1.5.em))
                Row(Modifier.padding(top = 8.px), horizontalArrangement = Arrangement.spacedBy((if (compact) Compact.BUTTON_GAP else 18f).px)) {
                    PillButton(stringResource(R.string.shell_problem_report), Symbols.REPORT_ISSUE, onReport, Modifier.focusRequester(report), primary = true, compact = true,
                        glyph = Glyph.A, glyphSize = 34)
                    PillButton(stringResource(R.string.shell_problem_dismiss), Symbols.CLOSE, onDismiss, compact = true, clickCue = Cue.BACK, glyph = Glyph.B, glyphSize = 34)
                }
                if (uploads) PillButton(stringResource(R.string.report_save_downloads), Symbols.DOWNLOAD, onSaveToDownloads, compact = true)
            }
        }
    }
}

/** The card's width: 936 stage px, a fifth wider than the toast-sized card it was, so the note never reads cramped. */
private const val CARD_WIDTH = 936f
