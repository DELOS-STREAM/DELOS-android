package com.limelight.shell.ui

import android.content.res.Resources
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.focusGroup
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
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
import com.limelight.R
import com.limelight.update.UpdateFailure
import com.limelight.update.UpdateState

/** The sentence under the title of a failed update. */
internal fun updateFailureText(res: Resources, failure: UpdateFailure): String = res.getString(
    when (failure) {
        UpdateFailure.DOWNLOAD -> R.string.update_card_failed_download
        UpdateFailure.TOO_LARGE -> R.string.update_card_failed_too_large
        UpdateFailure.STORAGE -> R.string.update_card_failed_storage
        UpdateFailure.CHECKSUM -> R.string.update_card_failed_checksum
        UpdateFailure.SIGNATURE -> R.string.update_card_failed_signature
        UpdateFailure.INSTALL -> R.string.update_card_failed_install
    },
)

/** The whole percent of a download for the card; null while the size is unknown. */
internal fun downloadPercent(received: Long, total: Long): Int? = if (total <= 0) null else (received * 100 / total).toInt().coerceIn(0, 100)

/** What the card's buttons do in [state]: the first is focused and bound to A, the second is B. */
internal enum class UpdateCardPrimary { UPDATE, ALLOW, CANCEL, RETRY, NONE }

internal fun updateCardPrimary(state: UpdateState): UpdateCardPrimary = when (state) {
    is UpdateState.Offer -> UpdateCardPrimary.UPDATE
    is UpdateState.NeedsPermission -> UpdateCardPrimary.ALLOW
    is UpdateState.Downloading -> UpdateCardPrimary.CANCEL
    is UpdateState.Failed -> if (state.failure == UpdateFailure.SIGNATURE) UpdateCardPrimary.NONE else UpdateCardPrimary.RETRY
    is UpdateState.Verified, is UpdateState.Installing, UpdateState.Idle -> UpdateCardPrimary.NONE
}

/**
 * The offer of an update (docs/UPDATES.md "Offer card"), built from the same parts as [ProblemOfferCard]: the glass card in the top
 * right corner, the round accent icon, the title and sentence, and the same pill buttons with the A and B glyphs. It is not a
 * dialog: nothing is dimmed and the rest of the screen stays usable. [state] is null while the card is hidden. Focus goes to the
 * first button whenever the state changes; A activates it, B is [onBack] (Later, or Cancel while downloading, set by the shell).
 */
@Composable
fun UpdateOfferCard(
    state: UpdateState?, reducedMotion: Boolean,
    onUpdate: () -> Unit, onAllow: () -> Unit, onCancel: () -> Unit, onLater: () -> Unit,
) {
    val colors = LocalDelosColors.current
    var shown by remember { mutableStateOf(state) }
    if (state != null) shown = state
    val origin = TransformOrigin(1f, 0f)
    val stage = LocalStage.current
    val compact = stage.compact
    val margin = if (compact) 48f else 64f
    Box(Modifier.fillMaxSize().padding(top = (if (compact) Compact.STATUS_BAR * stage.textScale + stage.safe.top else 230f).px, end = margin.px), contentAlignment = Alignment.TopEnd) {
        AnimatedVisibility(state != null, enter = popEnter(origin, reducedMotion), exit = popExit(origin, reducedMotion)) {
            val s = shown ?: return@AnimatedVisibility
            val candidate = when (s) {
                is UpdateState.Offer -> s.candidate
                is UpdateState.NeedsPermission -> s.candidate
                is UpdateState.Downloading -> s.candidate
                is UpdateState.Verified -> s.candidate
                is UpdateState.Installing -> s.candidate
                is UpdateState.Failed -> s.candidate
                UpdateState.Idle -> return@AnimatedVisibility
            }
            val primary = updateCardPrimary(s)
            val first = remember { FocusRequester() }
            InitialFocus(first, s::class to candidate.tag)
            val shape = RoundedCornerShape(34.px)
            val res = textResources()
            Column(
                Modifier.width((if (compact) minOf(780f, stage.stageWidth - 2f * 48f) else 780f).px).clip(shape).glass(colors.surface).border(1.px, colors.line, shape).padding((if (compact) 32 else 36).px).focusGroup(),
                verticalArrangement = Arrangement.spacedBy(18.px),
            ) {
                Row(horizontalArrangement = Arrangement.spacedBy(22.px), verticalAlignment = Alignment.CenterVertically) {
                    Box(Modifier.size(84.px).clip(CircleShape).background(colors.accent), contentAlignment = Alignment.Center) {
                        Symbol(Symbols.DOWNLOAD, 46.tpx, colors.onAccent, filled = true)
                    }
                    Column(verticalArrangement = Arrangement.spacedBy(6.px)) {
                        BasicText(res.getString(R.string.update_card_title, candidate.version.toString()), style = TextStyle(color = colors.text, fontFamily = Unbounded, fontWeight = FontWeight.SemiBold, fontSize = 28.tpx))
                        BasicText(
                            when (s) {
                                is UpdateState.Failed -> updateFailureText(res, s.failure)
                                is UpdateState.NeedsPermission -> res.getString(R.string.update_card_needs_permission)
                                is UpdateState.Downloading -> res.getString(R.string.update_card_downloading)
                                is UpdateState.Verified, is UpdateState.Installing -> res.getString(R.string.update_card_installing)
                                else -> res.getString(R.string.update_card_question)
                            },
                            style = TextStyle(color = colors.text2, fontFamily = Figtree, fontSize = 22.tpx, lineHeight = 1.35.em),
                        )
                    }
                }
                if (s is UpdateState.Offer && candidate.notes.isNotBlank()) {
                    BasicText(candidate.notes, style = TextStyle(color = colors.text3, fontFamily = Figtree, fontSize = 18.tpx, lineHeight = 1.35.em), maxLines = 3, overflow = TextOverflow.Ellipsis)
                }
                if (s is UpdateState.Downloading) {
                    val percent = downloadPercent(s.received, s.total)
                    Column(verticalArrangement = Arrangement.spacedBy(8.px)) {
                        Box(Modifier.fillMaxWidth().height(10.px).clip(CircleShape).background(colors.line)) {
                            if (percent != null) Box(Modifier.fillMaxWidth(percent / 100f).fillMaxHeight().background(colors.accent))
                        }
                        if (percent != null) BasicText(res.getString(R.string.update_card_percent, percent), style = TextStyle(color = colors.text3, fontFamily = Figtree, fontSize = 18.tpx))
                    }
                }
                if (primary != UpdateCardPrimary.NONE || s is UpdateState.Failed) {
                    Row(Modifier.padding(top = 6.px), horizontalArrangement = Arrangement.spacedBy((if (compact) Compact.BUTTON_GAP else 18f).px)) {
                        when (primary) {
                            UpdateCardPrimary.UPDATE -> PillButton(res.getString(R.string.update_card_update), Symbols.DOWNLOAD, onUpdate, Modifier.focusRequester(first), primary = true, compact = true, glyph = Glyph.A, glyphSize = 34)
                            UpdateCardPrimary.ALLOW -> PillButton(res.getString(R.string.update_card_allow), Symbols.SETTINGS, onAllow, Modifier.focusRequester(first), primary = true, compact = true, glyph = Glyph.A, glyphSize = 34)
                            UpdateCardPrimary.RETRY -> PillButton(res.getString(R.string.update_card_retry), Symbols.REFRESH, onUpdate, Modifier.focusRequester(first), primary = true, compact = true, glyph = Glyph.A, glyphSize = 34)
                            UpdateCardPrimary.CANCEL -> PillButton(res.getString(R.string.update_card_cancel), Symbols.CLOSE, onCancel, Modifier.focusRequester(first), compact = true, clickCue = Cue.BACK, glyph = Glyph.B, glyphSize = 34)
                            UpdateCardPrimary.NONE -> Unit
                        }
                        if (primary != UpdateCardPrimary.CANCEL) {
                            PillButton(
                                res.getString(R.string.update_card_later), Symbols.CLOSE, onLater,
                                if (primary == UpdateCardPrimary.NONE) Modifier.focusRequester(first) else Modifier,
                                compact = true, clickCue = Cue.BACK, glyph = Glyph.B, glyphSize = 34,
                            )
                        }
                    }
                }
            }
        }
    }
}
