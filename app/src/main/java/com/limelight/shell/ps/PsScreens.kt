package com.limelight.shell.ps

import android.content.Intent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import com.limelight.shell.ui.Motion
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.em
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.limelight.R
import com.limelight.ps.PsAccountId
import com.limelight.ps.PsDiscovery
import com.limelight.ps.PsHost
import com.limelight.ps.PsRegistrationClient
import com.limelight.shell.ContextMenu
import com.limelight.shell.LocalShell
import com.limelight.shell.MenuItem
import com.limelight.shell.Screen
import com.limelight.shell.ui.CenterColumn
import com.limelight.shell.ui.CircleItem
import com.limelight.shell.ui.Circles
import com.limelight.shell.ui.Compact
import com.limelight.shell.ui.compact
import com.limelight.shell.ui.Cue
import com.limelight.shell.ui.Figtree
import com.limelight.shell.ui.FocusGap
import com.limelight.shell.ui.Heading
import com.limelight.shell.ui.InitialFocus
import com.limelight.shell.ui.LocalDelosColors
import com.limelight.shell.ui.LocalStage
import com.limelight.shell.ui.OnlineGreen
import com.limelight.shell.ui.PillButton
import com.limelight.shell.ui.Symbols
import com.limelight.shell.ui.Symbol
import com.limelight.shell.ui.focusEffects
import com.limelight.shell.screens.LetterPicker
import com.limelight.shell.ui.Unbounded
import com.limelight.shell.ui.glass
import com.limelight.shell.ui.px
import com.limelight.shell.ui.textResources
import com.limelight.shell.ui.tpx
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.net.InetAddress

/** The PlayStation pairing flow (prototype F, variant A's three steps). */
sealed interface PsPairStep {
    data object Find : PsPairStep
    data class SignIn(val host: PsHost) : PsPairStep
    data class Pin(val host: PsHost, val accountId: String) : PsPairStep
    data class Done(val name: String, val consoleId: String) : PsPairStep
}

/**
 * `PC | PlayStation` at the top of the start screen (prototype F, from variant B): the Settings tab row itself, with its L1 / R1 badges
 * at the sides and its gliding selection plate, centred on the stage at [y]. L1 / R1 switch too (the shell's shoulder hook).
 */
@Composable
fun PsModeSwitch(playStation: Boolean, onPick: (Boolean) -> Unit, y: Float) {
    val stage = com.limelight.shell.ui.LocalStage.current
    com.limelight.shell.screens.TabRow(y, com.limelight.shell.screens.SettingsLayout.TABS_HEIGHT.px, left = 0f, right = stage.stageWidth) {
        com.limelight.shell.screens.ShoulderLabel("L1")
        com.limelight.shell.screens.TabPill(Symbols.DESKTOP_WINDOWS, stringResource(R.string.ps_mode_pc), !playStation, Modifier.padding(horizontal = 5.px)) { onPick(false) }
        com.limelight.shell.screens.TabPill(Symbols.PAD_DUALSENSE_PS, stringResource(R.string.ps_mode_playstation), playStation, Modifier.padding(horizontal = 5.px)) { onPick(true) }
        com.limelight.shell.screens.ShoulderLabel("R1")
    }
}

/** The PlayStation world of the start screen: the paired consoles in circles and "Pair a PlayStation". */
@Composable
fun PsStartContent(profileName: String?) {
    val context = LocalContext.current
    val shell = LocalShell.current
    val res = textResources()
    val store = remember { PsConsoleStore.get(context) }
    val consoles by store.consoles.collectAsStateWithLifecycle()
    val first = remember { FocusRequester() }
    InitialFocus(first, consoles.size)
    CenterColumn(gap = 48f) {
        Heading(
            if (profileName != null) stringResource(R.string.devices_start_greeting, profileName) else stringResource(R.string.devices_start_welcome),
            stringResource(if (consoles.isEmpty()) R.string.ps_start_pair_note else R.string.ps_start_choose_note),
        )
        Circles {
            consoles.forEachIndexed { i, c ->
                CircleItem(
                    Symbols.CONSOLE_PS5, c.name,
                    onClick = { context.startActivity(Intent(context, PsStreamActivity::class.java).putExtra(PsStreamActivity.EXTRA_CONSOLE, c.id)) },
                    modifier = if (i == 0) Modifier.focusRequester(first) else Modifier,
                    meta = stringResource(R.string.ps_console_meta), big = true, selected = i == 0,
                    contextMenu = ContextMenu(c.name, below = true, items = listOf(
                        MenuItem(Symbols.CLOSE, res.getString(R.string.ps_console_remove)) {
                            store.remove(c.id)
                            shell.showToast(res.getString(R.string.ps_console_removed, c.name), Symbols.CHECK_CIRCLE)
                        },
                    )),
                )
            }
            CircleItem(
                Symbols.ADD_DEVICE, stringResource(R.string.ps_pair_new), { shell.navigate(Screen.PairPs(PsPairStep.Find, back = Screen.Start)) },
                modifier = if (consoles.isEmpty()) Modifier.focusRequester(first) else Modifier,
                meta = stringResource(R.string.ps_pair_new_meta), big = true,
            )
        }
    }
}

/** The three-step progress line of the pairing flow (variant A). */
@Composable
private fun Steps(current: Int) {
    val colors = LocalDelosColors.current
    Row(horizontalArrangement = Arrangement.spacedBy(28.px), verticalAlignment = Alignment.CenterVertically) {
        listOf(R.string.ps_step_find, R.string.ps_step_sign_in, R.string.ps_step_pin).forEachIndexed { i, label ->
            val n = i + 1
            val on = n == current
            val done = n < current
            Row(horizontalArrangement = Arrangement.spacedBy(12.px), verticalAlignment = Alignment.CenterVertically) {
                Box(
                    Modifier.size(42.px).clip(CircleShape).then(if (on) Modifier.glass(colors.accent) else Modifier).border(1.5.px, if (on) colors.accent else colors.line, CircleShape),
                    contentAlignment = Alignment.Center,
                ) {
                    BasicText(if (done) "✓" else n.toString(), style = TextStyle(color = if (on) colors.onAccent else if (done) colors.accent2 else colors.text3, fontFamily = Figtree, fontWeight = FontWeight.Bold, fontSize = 21.tpx))
                }
                BasicText(stringResource(label), style = TextStyle(color = if (on) colors.text else if (done) colors.accent2 else colors.text3, fontFamily = Figtree, fontSize = 23.tpx))
            }
        }
    }
}

/** Pairing a PlayStation: find the console, sign in to PSN once, enter the PIN from the TV. */
@Composable
fun PsPairScreen(screen: Screen.PairPs) {
    LaunchedEffect(Unit) { PsMode.set(true) }
    when (val step = screen.step) {
        PsPairStep.Find -> FindStep(screen)
        is PsPairStep.SignIn -> SignInStep(screen, step)
        is PsPairStep.Pin -> PinStep(screen, step)
        is PsPairStep.Done -> DoneStep(step)
    }
}

@Composable
private fun FindStep(screen: Screen.PairPs) {
    val shell = LocalShell.current
    val context = LocalContext.current
    var hosts by remember { mutableStateOf<List<PsHost>?>(null) }
    var round by remember { mutableStateOf(0) }
    LaunchedEffect(round) {
        hosts = null
        hosts = withContext(Dispatchers.IO) { runCatching { PsDiscovery.search(InetAddress.getByName("255.255.255.255"), 2500) }.getOrDefault(emptyList()) }
    }
    val first = remember { FocusRequester() }
    InitialFocus(first, hosts?.size)
    CenterColumn(gap = 44f) {
        Steps(1)
        Heading(
            stringResource(R.string.ps_find_title),
            stringResource(when { hosts == null -> R.string.ps_find_searching; hosts!!.isEmpty() -> R.string.ps_find_none; else -> R.string.ps_find_found }),
        )
        Circles {
            hosts.orEmpty().forEachIndexed { i, h ->
                CircleItem(
                    Symbols.CONSOLE_PS5, h.name ?: h.address.hostAddress.orEmpty(),
                    onClick = { shell.navigate(Screen.PairPs(PsPairStep.SignIn(h), back = screen)) },
                    modifier = if (i == 0) Modifier.focusRequester(first) else Modifier,
                    meta = stringResource(if (h.awake) R.string.ps_find_on else R.string.ps_find_rest),
                    metaDot = if (h.awake) OnlineGreen else null, big = true, pulse = h.awake,
                )
            }
            CircleItem(
                Symbols.REFRESH, stringResource(R.string.ps_search_again), { round++ },
                modifier = if (hosts.isNullOrEmpty()) Modifier.focusRequester(first) else Modifier,
                meta = stringResource(R.string.ps_search_meta), big = true,
            )
        }
    }
}

@Composable
private fun SignInStep(screen: Screen.PairPs, step: PsPairStep.SignIn) {
    val shell = LocalShell.current
    val context = LocalContext.current
    val res = textResources()
    val store = remember { PsConsoleStore.get(context) }
    var error by remember { mutableStateOf<String?>(null) }
    val saved = store.accountId
    val first = remember { FocusRequester() }
    InitialFocus(first)
    // Sony's page opens in its own full-screen activity; back there returns here
    val signIn = rememberLauncherForActivityResult(PsSignInActivity.Contract) { outcome ->
        when (outcome) {
            is PsSignInActivity.Outcome.Account -> {
                store.accountId = outcome.id
                shell.navigate(Screen.PairPs(PsPairStep.Pin(step.host, outcome.id), back = screen.back ?: Screen.Start))
            }
            is PsSignInActivity.Outcome.Failed -> error = res.getString(R.string.ps_sign_in_failed, outcome.message)
            null -> Unit
        }
    }
    CenterColumn(gap = 44f) {
        Steps(2)
        Heading(stringResource(R.string.ps_sign_in_title), error ?: stringResource(R.string.ps_sign_in_note))
        Row(horizontalArrangement = Arrangement.spacedBy(24.px)) {
            if (saved != null) PillButton(stringResource(R.string.ps_sign_in_keep), Symbols.CHECK, {
                shell.navigate(Screen.PairPs(PsPairStep.Pin(step.host, saved), back = screen.back ?: Screen.Start))
            }, Modifier.focusRequester(first), primary = true)
            PillButton(stringResource(R.string.ps_sign_in_button), Symbols.PAD_DUALSENSE_PS, { error = null; signIn.launch(Unit) },
                if (saved == null) Modifier.focusRequester(first) else Modifier, primary = saved == null)
        }
    }
}

@Composable
private fun PinStep(screen: Screen.PairPs, step: PsPairStep.Pin) {
    val shell = LocalShell.current
    val context = LocalContext.current
    val colors = LocalDelosColors.current
    val stage = LocalStage.current
    val res = textResources()
    var pin by remember { mutableStateOf("") }
    var busy by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    val first = remember { FocusRequester() }
    InitialFocus(first)
    val name = step.host.name ?: "PlayStation"
    fun type(digit: Char) { if (!busy && pin.length < 8) { error = null; pin += digit } }
    fun erase() { if (!busy) pin = pin.dropLast(1) }
    // The eighth digit pairs at once (prototype F); a wrong PIN empties the boxes and says why
    LaunchedEffect(pin) {
        if (pin.length != 8 || busy) return@LaunchedEffect
        busy = true
        val account = PsAccountId.fromBase64(step.accountId)
        val result = withContext(Dispatchers.IO) {
            runCatching { PsRegistrationClient.register(step.host.address, PS_PORT, step.host.target, account!!, pin.toInt()) }
        }
        busy = false
        result.onSuccess { c ->
            PsConsoleStore.get(context).add(c.nickname ?: name, step.host.address.hostAddress.orEmpty(), c)
            shell.navigate(Screen.PairPs(PsPairStep.Done(c.nickname ?: name, PsHexId.of(c.mac)), back = Screen.Start))
        }.onFailure { error = res.getString(R.string.ps_pin_failed, it.message ?: ""); pin = "" }
    }
    val side = if (stage.compact) Compact.SIDE else 120f
    Row(
        Modifier.fillMaxSize()
            .padding(start = (side + stage.safe.left).px, end = (side + stage.safe.right).px, top = (if (stage.compact) 120f else 150f).px, bottom = 110.px)
            // Digits typed on a keyboard go into the boxes too
            .onPreviewKeyEvent { e ->
                val code = e.nativeKeyEvent.keyCode
                when {
                    e.type != KeyEventType.KeyDown -> false
                    code in android.view.KeyEvent.KEYCODE_0..android.view.KeyEvent.KEYCODE_9 -> { type('0' + (code - android.view.KeyEvent.KEYCODE_0)); true }
                    code == android.view.KeyEvent.KEYCODE_DEL -> { erase(); true }
                    else -> false
                }
            },
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(80.px),
    ) {
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(26.px)) {
            Steps(3)
            BasicText(stringResource(R.string.ps_pin_eyebrow, name).uppercase(), style = TextStyle(color = colors.accent2, fontFamily = Figtree, fontWeight = FontWeight.Bold, fontSize = 20.tpx, letterSpacing = .08.em),
                modifier = Modifier.padding(top = 18.px))
            BasicText(stringResource(R.string.ps_pin_title), style = TextStyle(color = colors.text, fontFamily = Unbounded, fontWeight = FontWeight.SemiBold, fontSize = (if (stage.compact) 40 else 48).tpx, letterSpacing = (-.01).em))
            BasicText(stringResource(R.string.ps_pin_note), style = TextStyle(color = colors.text2, fontFamily = Figtree, fontSize = 26.tpx))
            // Eight boxes in two groups of four; the next one to fill is outlined in the accent
            Row(Modifier.padding(top = 10.px), horizontalArrangement = Arrangement.spacedBy(14.px), verticalAlignment = Alignment.CenterVertically) {
                for (i in 0 until 8) {
                    if (i == 4) Box(Modifier.width(14.px))
                    val shape = RoundedCornerShape(18.px)
                    val next = i == pin.length && !busy
                    Box(
                        Modifier.size(72.px, 92.px).clip(shape).glass(colors.surface2).border(if (next) 2.px else 1.px, if (next) colors.accent else colors.line, shape),
                        contentAlignment = Alignment.Center,
                    ) {
                        BasicText(pin.getOrNull(i)?.toString() ?: "–", style = TextStyle(color = if (i < pin.length) colors.text else colors.text3, fontFamily = Unbounded, fontWeight = FontWeight.SemiBold, fontSize = 40.tpx, textAlign = TextAlign.Center))
                    }
                }
            }
            BasicText(
                error ?: stringResource(if (busy) R.string.ps_pin_pairing else R.string.ps_pin_hint),
                style = TextStyle(color = if (error != null) colors.text else colors.text3, fontFamily = Figtree, fontSize = 22.tpx),
            )
        }
        PinPad(first, ::type, ::erase) { if (!busy) { error = null; pin = "" } }
    }
}

/**
 * The PIN pad (prototype F): 1–9, then Clear, 0 and erase, in a glass panel. Every key is a raised tile, lighter than the panel and
 * outlined, so the grid reads at a glance; the two function keys are a step darker. A focused key fills with the accent like the
 * prototype's and keeps the letter picker's inner ring (no growth: the grid is dense). The D-pad moves, A types.
 */
@Composable
private fun PinPad(first: FocusRequester, type: (Char) -> Unit, erase: () -> Unit, clear: () -> Unit) {
    val colors = LocalDelosColors.current
    val panel = RoundedCornerShape(36.px)
    Column(
        Modifier.clip(panel).glass(colors.surface).border(1.px, colors.line, panel).padding(PinKey.PAD.px),
        verticalArrangement = Arrangement.spacedBy(PinKey.KEYS.gap.px),
    ) {
        listOf("123", "456", "789").forEach { row ->
            Row(horizontalArrangement = Arrangement.spacedBy(PinKey.KEYS.gap.px)) {
                row.forEach { d -> PinKey(if (d == '1') Modifier.focusRequester(first) else Modifier, { type(d) }) { lit -> PinLabel(d.toString(), lit, 42) } }
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(PinKey.KEYS.gap.px)) {
            PinKey(Modifier, clear, Cue.BACK, function = true) { lit -> PinLabel(stringResource(R.string.ps_clear), lit, 26) }
            PinKey(Modifier, { type('0') }) { lit -> PinLabel("0", lit, 42) }
            PinKey(Modifier, erase, Cue.BACK, function = true) { lit -> Symbol(Symbols.BACKSPACE, 38.tpx, if (lit) colors.onAccent else colors.text, filled = lit) }
        }
    }
}

@Composable
private fun PinLabel(text: String, lit: Boolean, size: Int) {
    val colors = LocalDelosColors.current
    BasicText(text, maxLines = 1, style = TextStyle(color = if (lit) colors.onAccent else colors.text, fontFamily = Figtree, fontWeight = FontWeight.SemiBold, fontSize = size.tpx))
}

/** One key of the [PinPad]; [content] gets whether the key is focused. [function]: Clear and erase, a step darker than the digits. */
@Composable
private fun PinKey(modifier: Modifier, onClick: () -> Unit, cue: Cue = Cue.SELECT, function: Boolean = false, content: @Composable (Boolean) -> Unit) {
    val colors = LocalDelosColors.current
    var focused by remember { mutableStateOf(false) }
    val shape = RoundedCornerShape(PinKey.CORNER.px)
    val fill by animateColorAsState(if (focused) colors.accent else if (function) colors.surface2 else colors.surface3, tween(Motion.FADE), label = "pinKey")
    Box(
        modifier.size(PinKey.WIDTH.px, PinKey.HEIGHT.px)
            .focusEffects(onClick, corner = PinKey.CORNER, focusScale = LetterPicker.KEY_LOOK.scale, ring = LetterPicker.KEY_RING, glow = null, clickCue = cue, onFocusChange = { focused = it })
            .clip(shape).background(fill).border(1.px, colors.line, shape),
        contentAlignment = Alignment.Center,
    ) { content(focused) }
}

/** The [PinPad]'s keys and their gap (stage px); the gap follows the focus spacing rule for the letter picker's look. */
internal object PinKey {
    const val WIDTH = 136f
    const val HEIGHT = 92f
    const val CORNER = 18f
    const val PAD = 26f
    val KEYS = FocusGap(LetterPicker.KEY_LOOK, WIDTH, 18f)
}

@Composable
private fun DoneStep(step: PsPairStep.Done) {
    val shell = LocalShell.current
    val context = LocalContext.current
    val first = remember { FocusRequester() }
    InitialFocus(first)
    CenterColumn(gap = 48f) {
        Heading(stringResource(R.string.ps_paired_title, step.name), stringResource(R.string.ps_paired_note))
        Row(horizontalArrangement = Arrangement.spacedBy(18.px)) {
            PillButton(stringResource(R.string.ps_play_now), Symbols.PLAY_ARROW, {
                shell.navigate(Screen.Start)
                context.startActivity(Intent(context, PsStreamActivity::class.java).putExtra(PsStreamActivity.EXTRA_CONSOLE, step.consoleId))
            }, Modifier.focusRequester(first), primary = true)
            PillButton(stringResource(R.string.ps_back_to_start), Symbols.CHECK, { shell.navigate(Screen.Start) })
        }
    }
}

/** Registration, session init and ctrl always use 9295, whatever discovery's host-request-port says (spec 5.9 (3)). */
internal const val PS_PORT = 9295
