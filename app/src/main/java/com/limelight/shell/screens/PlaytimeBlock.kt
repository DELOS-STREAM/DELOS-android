package com.limelight.shell.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.SubcomposeLayout
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.LineHeightStyle
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.em
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import com.limelight.R
import com.limelight.shell.data.LastStreamed
import com.limelight.shell.data.Playtime
import com.limelight.shell.data.PlaytimeFormat
import com.limelight.shell.data.PlaytimeStore
import com.limelight.shell.data.ProfileStore
import com.limelight.shell.ui.Figtree
import com.limelight.shell.ui.LocalDelosColors
import com.limelight.shell.ui.LocalStage
import com.limelight.shell.ui.MarqueeContent
import com.limelight.shell.ui.px
import com.limelight.shell.ui.textResources
import com.limelight.shell.ui.tpx

/** How much of the stat block fits the width it is given, in the order it gives up text; [SCROLL] is the last resort. */
internal enum class PlaytimeFit {
    /** Streaming time, divider and last streamed. */
    FULL,

    /** Streaming time with the full unit ("20.4 hours") alone. */
    STAT_ONLY,

    /** Streaming time with the short unit ("20.4 h") alone. */
    SHORT,

    /** Not even that fits: the short form scrolls with a fade at its end. */
    SCROLL;

    companion object {
        /** The first form whose width ([full], [statOnly], [short], pixels) is at most [available]; the block never shrinks. */
        fun pick(full: Int, statOnly: Int, short: Int, available: Int): PlaytimeFit = when {
            full <= available -> FULL
            statOnly <= available -> STAT_ONLY
            short <= available -> SHORT
            else -> SCROLL
        }
    }
}

/** The texts of one stat block in the current language. */
internal class PlaytimeTexts(
    val eyebrowTime: String,
    val eyebrowLast: String,
    val full: PlaytimeFormat.Parts,
    val short: PlaytimeFormat.Parts,
    val last: String,
)

/** Reads the focused app's streaming time (docs/GUI.md): again whenever time is added, the profile changes or the shell resumes after a stream. */
@Composable
internal fun rememberPlaytime(profiles: ProfileStore, pcUuid: String, appId: Int, resumes: Int): Playtime {
    val context = LocalContext.current.applicationContext
    val store = remember(context) { PlaytimeStore(context) }
    val changes by store.changes.collectAsState()
    // The same profile the stream window files its time under (ProfileStore.currentId)
    val current by profiles.profiles.collectAsState()
    return remember(store, current, pcUuid, appId, changes, resumes) {
        store.playtime(ProfileStore.currentId(context), pcUuid, appId)
    }
}

/** Counts the shell's resumes: the stat block's relative day is worked out again (a stream ended, or a new day began). */
@Composable
internal fun rememberResumes(): Int {
    var resumes by remember { mutableIntStateOf(0) }
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { resumes++ }
    return resumes
}

/** The texts of the stat block for [playtime] in the current language; worked out again when it changes, on a resume and on a language change. */
@Composable
internal fun rememberPlaytimeTexts(playtime: Playtime, resumes: Int): PlaytimeTexts {
    // textResources() reads the configuration, so a language change recomposes this; the locale is that of the same resources the strings come from
    val config = LocalConfiguration.current
    val res = textResources()
    return remember(playtime, resumes, config) {
        val locale = PlaytimeFormat.localeOf(res)
        PlaytimeTexts(
            // Upper-cased in the locale (a Turkish i becomes İ)
            res.getString(R.string.playtime_eyebrow_time).uppercase(locale),
            res.getString(R.string.playtime_eyebrow_last).uppercase(locale),
            PlaytimeFormat.parts(res, playtime.totalMillis),
            PlaytimeFormat.parts(res, playtime.totalMillis, short = true),
            LastStreamed.format(res, playtime.lastStreamedMillis),
        )
    }
}

/**
 * The streaming-time stat block (prototype B of issue #85): the small caps eyebrow STREAMING TIME in the accent color over the
 * amount (big number, smaller unit), a thin divider, then LAST STREAMED over its relative day. Not focusable, no action. Always shown,
 * also for an app never streamed ("0 hours", "Never"). Its height is [HomeCompact.blockHeight] whatever is shown. This standalone form (the
 * block above the badges on a very narrow stage; the info row, [InfoBar], places the block itself) never shrinks: it gives up the
 * last-streamed column, then the unit's long form ([PlaytimeFit]), and only then scrolls ([MarqueeContent]).
 */
@Composable
internal fun StreamingTimeBlock(playtime: Playtime, resumes: Int, modifier: Modifier = Modifier) {
    val scale = LocalStage.current.textScale
    val texts = rememberPlaytimeTexts(playtime, resumes)
    val height = HomeCompact.blockHeight(scale).px
    val scrollKey = texts.full.number + texts.short.unit + texts.last
    SubcomposeLayout(modifier) { constraints ->
        val available = constraints.maxWidth
        val unbounded = Constraints()
        val measured = listOf(PlaytimeFit.FULL, PlaytimeFit.STAT_ONLY, PlaytimeFit.SHORT).associateWith { fit ->
            subcompose(fit) { StatRow(texts, fit, height) }.first().measure(unbounded)
        }
        val fit = PlaytimeFit.pick(
            measured.getValue(PlaytimeFit.FULL).width, measured.getValue(PlaytimeFit.STAT_ONLY).width,
            measured.getValue(PlaytimeFit.SHORT).width, available,
        )
        val shown = if (fit == PlaytimeFit.SCROLL) {
            subcompose(fit) { MarqueeContent(scrollKey, Modifier.height(height)) { StatRow(texts, PlaytimeFit.SHORT, height) } }
                .first().measure(Constraints(maxWidth = available))
        } else measured.getValue(fit)
        layout(shown.width, shown.height) { shown.place(0, 0) }
    }
}

@Composable
internal fun StatRow(texts: PlaytimeTexts, fit: PlaytimeFit, height: Dp) {
    val colors = LocalDelosColors.current
    val scale = LocalStage.current.textScale
    Row(
        Modifier.height(height),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(COLUMN_GAP.px),
    ) {
        val parts = if (fit == PlaytimeFit.SHORT) texts.short else texts.full
        Column(Modifier.height(height), verticalArrangement = Arrangement.Center) {
            Eyebrow(texts.eyebrowTime, colors.accent)
            Row(Modifier.height((VALUE_LINE * scale).px), horizontalArrangement = Arrangement.spacedBy(8.px)) {
                BasicText(
                    parts.number, Modifier.alignByBaseline(),
                    style = valueStyle(colors.text, 30, FontWeight.Bold),
                    maxLines = 1, softWrap = false,
                )
                BasicText(
                    parts.unit, Modifier.alignByBaseline(),
                    style = valueStyle(colors.text2, 20, FontWeight.SemiBold),
                    maxLines = 1, softWrap = false,
                )
            }
        }
        if (fit == PlaytimeFit.FULL) {
            StatDivider()
            Column(Modifier.height(height), verticalArrangement = Arrangement.Center) {
                Eyebrow(texts.eyebrowLast, colors.text3)
                Box(Modifier.height((VALUE_LINE * scale).px), contentAlignment = Alignment.CenterStart) {
                    BasicText(texts.last, style = valueStyle(colors.text2, 23, FontWeight.SemiBold), maxLines = 1, softWrap = false)
                }
            }
        }
    }
}

private const val EYEBROW_LINE = 14f
internal const val VALUE_LINE = 30f

/** The small caps label over a stat; [shadow] is the block's own over the art (none on a card, the side menu's). */
@Composable
internal fun Eyebrow(text: String, color: Color, shadow: Shadow? = TEXT_SHADOW) {
    BasicText(
        text,
        style = TextStyle(
            color = color, fontFamily = Figtree, fontWeight = FontWeight.Bold, fontSize = 13.tpx, letterSpacing = .16.em, shadow = shadow,
            lineHeight = EYEBROW_LINE.tpx, lineHeightStyle = LineHeightStyle(LineHeightStyle.Alignment.Center, LineHeightStyle.Trim.None),
        ),
        modifier = Modifier.padding(end = (EYEBROW_TRAIL * LocalStage.current.textScale).px),
        maxLines = 1, softWrap = false,
    )
}

@Composable
internal fun valueStyle(color: Color, size: Int, weight: FontWeight, shadow: Shadow? = TEXT_SHADOW) = TextStyle(
    color = color, fontFamily = Figtree, fontWeight = weight, fontSize = size.tpx, fontFeatureSettings = "tnum", shadow = shadow,
    lineHeight = VALUE_LINE.tpx,
    lineHeightStyle = LineHeightStyle(LineHeightStyle.Alignment.Center, LineHeightStyle.Trim.None),
)

/** A soft dark shadow under the block's texts: all the readability they get, no plate behind them. */
private val TEXT_SHADOW = Shadow(Color.Black.copy(alpha = .6f), Offset(0f, 2f), 8f)

/**
 * The thin bar between the block's two columns: 2 px wide, [BAR_HEIGHT] x scale high, the accent at 70 % in the middle and fading to
 * transparent over [BAR_FADE] of its length at the top and at the bottom. It is centred on the block's content by its row.
 */
@Composable
internal fun StatDivider() {
    val scale = LocalStage.current.textScale
    val solid = LocalDelosColors.current.accent.copy(alpha = .7f)
    val brush = Brush.verticalGradient(0f to Color.Transparent, BAR_FADE to solid, 1f - BAR_FADE to solid, 1f to Color.Transparent)
    Box(Modifier.width(2.px).height((BAR_HEIGHT * scale).px).clip(CircleShape).background(brush))
}

private const val BAR_HEIGHT = 38f
private const val BAR_FADE = .22f

/** The air between the block's columns and their divider. */
internal const val COLUMN_GAP = 22f

/** The letter spacing (0.16 em of 13) that Compose leaves out after the last letter of an eyebrow: the column's width includes it, so the air to the next element is the full gap. */
private const val EYEBROW_TRAIL = 2.1f
