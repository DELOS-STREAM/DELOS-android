package com.limelight.shell.ps

import android.annotation.SuppressLint
import android.content.Intent
import android.webkit.CookieManager
import android.webkit.WebResourceRequest
import android.webkit.WebView
import android.webkit.WebViewClient
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.limelight.R
import com.limelight.ps.PsAccountId
import com.limelight.ps.PsDiscovery
import com.limelight.ps.PsHost
import com.limelight.ps.PsOAuth
import com.limelight.ps.PsRegistrationClient
import com.limelight.shell.ContextMenu
import com.limelight.shell.LocalShell
import com.limelight.shell.MenuItem
import com.limelight.shell.Screen
import com.limelight.shell.ui.CenterColumn
import com.limelight.shell.ui.CircleItem
import com.limelight.shell.ui.Circles
import com.limelight.shell.ui.Cue
import com.limelight.shell.ui.Figtree
import com.limelight.shell.ui.Heading
import com.limelight.shell.ui.InitialFocus
import com.limelight.shell.ui.LocalDelosColors
import com.limelight.shell.ui.OnlineGreen
import com.limelight.shell.ui.PillButton
import com.limelight.shell.ui.Symbols
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
                Symbols.ADD_LINK, stringResource(R.string.ps_pair_new), { shell.navigate(Screen.PairPs(PsPairStep.Find, back = Screen.Start)) },
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

@SuppressLint("SetJavaScriptEnabled")
@Composable
private fun SignInStep(screen: Screen.PairPs, step: PsPairStep.SignIn) {
    val shell = LocalShell.current
    val context = LocalContext.current
    val res = textResources()
    val store = remember { PsConsoleStore.get(context) }
    val scope = rememberCoroutineScope()
    var web by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    val saved = store.accountId
    val first = remember { FocusRequester() }
    InitialFocus(first)
    if (web) {
        // Sony's page fills the screen; the redirect with the code is caught and never loaded (spec 3.1)
        AndroidView(
            factory = { ctx ->
                CookieManager.getInstance().removeAllCookies(null)
                WebView(ctx).apply {
                    settings.javaScriptEnabled = true
                    settings.domStorageEnabled = true
                    webViewClient = object : WebViewClient() {
                        override fun shouldOverrideUrlLoading(view: WebView, request: WebResourceRequest): Boolean {
                            val code = PsOAuth.codeFrom(request.url.toString()) ?: return false
                            web = false
                            scope.launch {
                                runCatching { withContext(Dispatchers.IO) { PsAccountId.toBase64(PsOAuth.accountId(code)) } }
                                    .onSuccess { id ->
                                        store.accountId = id
                                        shell.navigate(Screen.PairPs(PsPairStep.Pin(step.host, id), back = screen.back ?: Screen.Start))
                                    }
                                    .onFailure { error = res.getString(R.string.ps_sign_in_failed, it.message ?: "") }
                            }
                            return true
                        }
                    }
                    loadUrl(PsOAuth.authorizeUrl())
                }
            },
            modifier = Modifier.fillMaxSize(),
        )
        return
    }
    CenterColumn(gap = 44f) {
        Steps(2)
        Heading(stringResource(R.string.ps_sign_in_title), error ?: stringResource(R.string.ps_sign_in_note))
        Row(horizontalArrangement = Arrangement.spacedBy(24.px)) {
            if (saved != null) PillButton(stringResource(R.string.ps_sign_in_keep), Symbols.CHECK, {
                shell.navigate(Screen.PairPs(PsPairStep.Pin(step.host, saved), back = screen.back ?: Screen.Start))
            }, Modifier.focusRequester(first), primary = true)
            PillButton(stringResource(R.string.ps_sign_in_button), Symbols.PAD_DUALSENSE_PS, { web = true },
                if (saved == null) Modifier.focusRequester(first) else Modifier, primary = saved == null)
        }
    }
}

@Composable
private fun PinStep(screen: Screen.PairPs, step: PsPairStep.Pin) {
    val shell = LocalShell.current
    val context = LocalContext.current
    val colors = LocalDelosColors.current
    val res = textResources()
    val scope = rememberCoroutineScope()
    var pin by remember { mutableStateOf("") }
    var busy by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    val first = remember { FocusRequester() }
    InitialFocus(first)
    val name = step.host.name ?: "PlayStation"
    fun pair() {
        if (pin.length != 8 || busy) return
        busy = true
        error = null
        scope.launch {
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
    }
    CenterColumn(gap = 36f) {
        Steps(3)
        Heading(stringResource(R.string.ps_pin_title), error ?: stringResource(R.string.ps_pin_note))
        Row(horizontalArrangement = Arrangement.spacedBy(12.px)) {
            for (i in 0 until 8) {
                val shape = RoundedCornerShape(18.px)
                Box(
                    Modifier.size(76.px, 96.px).clip(shape).glass(colors.surface2).border(1.5.px, if (i == pin.length) colors.accent else colors.line, shape),
                    contentAlignment = Alignment.Center,
                ) {
                    BasicText(pin.getOrNull(i)?.toString() ?: "", style = TextStyle(color = colors.text, fontFamily = Unbounded, fontWeight = FontWeight.SemiBold, fontSize = 44.tpx, textAlign = TextAlign.Center))
                }
            }
        }
        // A number pad for the controller: three rows of digits, then clear, 0, erase; the pair button under it
        Column(verticalArrangement = Arrangement.spacedBy(14.px), horizontalAlignment = Alignment.CenterHorizontally) {
            listOf(listOf("1", "2", "3"), listOf("4", "5", "6"), listOf("7", "8", "9")).forEachIndexed { r, row ->
                Row(horizontalArrangement = Arrangement.spacedBy(14.px)) {
                    row.forEachIndexed { c, d ->
                        Box(Modifier.width(120.px)) {
                            PillButton(d, null, { if (pin.length < 8) pin += d }, if (r == 0 && c == 0) Modifier.focusRequester(first) else Modifier, compact = true)
                        }
                    }
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(14.px)) {
                PillButton(stringResource(R.string.ps_clear), Symbols.CLOSE, { pin = "" }, compact = true, clickCue = Cue.BACK)
                Box(Modifier.width(120.px)) { PillButton("0", null, { if (pin.length < 8) pin += "0" }, compact = true) }
                PillButton("", Symbols.BACKSPACE, { pin = pin.dropLast(1) }, compact = true, clickCue = Cue.BACK)
            }
            PillButton(if (busy) stringResource(R.string.ps_pin_pairing) else stringResource(R.string.ps_pin_pair, name), Symbols.ADD_LINK, ::pair, primary = pin.length == 8)
        }
    }
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
