package com.limelight.shell.screens

import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.platform.LocalContext
import com.limelight.R
import com.limelight.shell.data.Pc
import com.limelight.shell.data.Playtime
import com.limelight.shell.data.PlaytimeStore
import com.limelight.shell.data.ProfileStore
import com.limelight.shell.ui.Figtree
import com.limelight.shell.ui.LocalDelosColors
import com.limelight.shell.ui.LocalReducedMotion
import com.limelight.shell.ui.LocalStage
import com.limelight.shell.ui.OnlineGreen
import com.limelight.shell.ui.Unbounded
import com.limelight.shell.ui.px
import com.limelight.shell.ui.staggerEntrance
import com.limelight.shell.ui.textResources
import com.limelight.shell.ui.tpx

/** The live state of the PC whose library is open, as the side menu's header shows it: a dot and a word ([label]). */
enum class SidePcState(val label: Int) {
    ONLINE(R.string.devices_status_online),
    STREAMING(R.string.shell_side_pc_streaming),
    CONNECTING(R.string.shell_side_pc_connecting),
    ASLEEP(R.string.devices_status_asleep),
    OFFLINE(R.string.devices_status_offline);

    companion object {
        /** A PC being connected to ([connecting]: a game starting on it) connects; an online one streams while it runs a game; one not online sleeps when it can be woken. */
        fun of(online: Boolean, running: Boolean, connecting: Boolean, canWake: Boolean): SidePcState = when {
            connecting -> CONNECTING
            online -> if (running) STREAMING else ONLINE
            canWake -> ASLEEP
            else -> OFFLINE
        }

        fun of(pc: Pc, connecting: Boolean): SidePcState = of(pc.online, pc.runningGameId != 0, connecting, pc.canWake)
    }
}

/** Reads the current profile's whole streaming time for the side menu's header: again whenever time is added, the profile changes or the shell resumes. */
@Composable
fun rememberProfilePlaytime(profiles: ProfileStore): Playtime {
    val context = LocalContext.current.applicationContext
    val store = remember(context) { PlaytimeStore(context) }
    val changes by store.changes.collectAsState()
    val current by profiles.profiles.collectAsState()
    val resumes = rememberResumes()
    return remember(store, current, changes, resumes) { store.total(ProfileStore.currentId(context)) }
}

/** The PC in the side menu's header: its name and live state. */
data class SidePc(val name: String, val state: SidePcState)

/**
 * The side menu's header (prototype A, "Status card", `.a-head` / `.a-card`): the profile's [avatar] and [name], under the name the PC with
 * its live dot and state (or "Not connected"), then a card in the stat-block language of the home screen with the profile's whole
 * streaming time ([playtime]) and its last stream, and the rule above the rows.
 */
@Composable
fun SideMenuHeader(avatar: @Composable BoxScope.() -> Unit, name: String, pc: SidePc?, playtime: Playtime) {
    val colors = LocalDelosColors.current
    val scale = LocalStage.current.textScale
    val res = textResources()
    val texts = rememberPlaytimeTexts(playtime, rememberResumes())
    val pcText = pc?.let { "${it.name} · ${res.getString(it.state.label)}" } ?: res.getString(R.string.shell_side_not_connected)
    val spoken = "$name, $pcText, ${texts.eyebrowTime} ${texts.full.number} ${texts.full.unit}, ${texts.eyebrowLast} ${texts.last}"
    Column(Modifier.staggerEntrance().fillMaxWidth().clearAndSetSemantics { contentDescription = spoken }) {
        Row(
            Modifier.padding(horizontal = 12.px),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(20.px),
        ) {
            Box(Modifier.size(72.px).clip(CircleShape), contentAlignment = Alignment.Center, content = avatar)
            Column(verticalArrangement = Arrangement.spacedBy(6.px)) {
                BasicText(
                    name, style = TextStyle(color = colors.text, fontFamily = Unbounded, fontWeight = FontWeight.SemiBold, fontSize = 28.tpx),
                    maxLines = 1, overflow = TextOverflow.Ellipsis,
                )
                PcLine(pc)
            }
        }
        Row(
            Modifier
                .padding(start = 12.px, end = 12.px, top = 22.px, bottom = 24.px)
                .fillMaxWidth()
                .clip(RoundedCornerShape(24.px))
                .background(colors.surface)
                .border(1.px, colors.line, RoundedCornerShape(24.px))
                .padding(horizontal = 24.px, vertical = 18.px),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(28.px),
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(6.px)) {
                Eyebrow(texts.eyebrowTime, colors.accent, shadow = null)
                Row(Modifier.height((VALUE_LINE * scale).px), horizontalArrangement = Arrangement.spacedBy(8.px)) {
                    BasicText(texts.full.number, Modifier.alignByBaseline(), style = valueStyle(colors.text, 30, FontWeight.Bold, shadow = null), maxLines = 1, softWrap = false)
                    BasicText(texts.full.unit, Modifier.alignByBaseline(), style = valueStyle(colors.text2, 20, FontWeight.SemiBold, shadow = null), maxLines = 1, softWrap = false)
                }
            }
            StatDivider()
            Column(verticalArrangement = Arrangement.spacedBy(6.px)) {
                Eyebrow(texts.eyebrowLast, colors.text3, shadow = null)
                Box(Modifier.height((VALUE_LINE * scale).px), contentAlignment = Alignment.CenterStart) {
                    BasicText(
                        texts.last, style = valueStyle(colors.text2, 23, FontWeight.SemiBold, shadow = null),
                        maxLines = 1, overflow = TextOverflow.Ellipsis,
                    )
                }
            }
        }
        Box(Modifier.fillMaxWidth().height(1.px).background(colors.line))
        Box(Modifier.height(14.px))
    }
}

/** The PC's dot, name and state, or "Not connected" (`.a-pc`): 21 px, the name in the second text color, the state in the third. */
@Composable
private fun PcLine(pc: SidePc?) {
    val colors = LocalDelosColors.current
    val res = textResources()
    val style = TextStyle(color = colors.text3, fontFamily = Figtree, fontSize = 21.tpx)
    if (pc == null) {
        BasicText(res.getString(R.string.shell_side_not_connected), style = style, maxLines = 1, overflow = TextOverflow.Ellipsis)
        return
    }
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.px)) {
        SidePcDot(pc.state)
        BasicText(pc.name, Modifier.weight(1f, fill = false), style = style.copy(color = colors.text2), maxLines = 1, overflow = TextOverflow.Ellipsis)
        BasicText("·", style = style, maxLines = 1) // l10n-ignore
        BasicText(res.getString(pc.state.label), style = style, maxLines = 1, softWrap = false)
    }
}

/**
 * The 12 px dot of a PC's state: green with its glow while online, the main color with a slow pulse while it streams, the second accent
 * blinking while it connects, a ring while it sleeps, grey while offline. Reduced motion: no pulse, no blink.
 */
@Composable
private fun SidePcDot(state: SidePcState) {
    val colors = LocalDelosColors.current
    val moving = !LocalReducedMotion.current && (state == SidePcState.STREAMING || state == SidePcState.CONNECTING)
    val beat = if (moving) {
        val transition = rememberInfiniteTransition(label = "sidePcDot")
        val half = if (state == SidePcState.STREAMING) 800 else 500
        transition.animateFloat(0f, 1f, infiniteRepeatable(tween(half, easing = EASE_IN_OUT), RepeatMode.Reverse), label = "beat")
    } else null
    val ring = 2.px
    Box(
        Modifier.size(12.px).drawBehind {
            val b = beat?.value ?: 0f
            val r = size.minDimension / 2
            when (state) {
                SidePcState.ONLINE -> {
                    glow(OnlineGreen, r, r * 5f / 6f)
                    drawCircle(OnlineGreen, r)
                }
                SidePcState.STREAMING -> {
                    // 1 → 0.8 in size, the glow 6 → 1 px
                    val scaled = r * (1f - .2f * b)
                    glow(colors.accent, scaled, r * (1f - 5f / 6f * b))
                    drawCircle(colors.accent, scaled)
                }
                SidePcState.CONNECTING -> drawCircle(colors.accent2.copy(alpha = 1f - .75f * b), r)
                SidePcState.ASLEEP -> drawCircle(colors.text3, r - ring.toPx() / 2, style = Stroke(ring.toPx()))
                SidePcState.OFFLINE -> drawCircle(colors.text3, r)
            }
        },
    )
}

/** A soft glow of [color] reaching [spread] beyond a dot of radius [r] (the prototype's box-shadow). */
private fun androidx.compose.ui.graphics.drawscope.DrawScope.glow(color: Color, r: Float, spread: Float) {
    val outer = r + spread
    if (outer <= 0f) return
    drawCircle(Brush.radialGradient(0f to color.copy(alpha = .7f), r / outer to color.copy(alpha = .5f), 1f to Color.Transparent, center = center, radius = outer), outer)
}

private val EASE_IN_OUT = CubicBezierEasing(.42f, 0f, .58f, 1f)
