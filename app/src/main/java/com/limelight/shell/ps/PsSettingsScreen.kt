package com.limelight.shell.ps

import android.content.SharedPreferences
import androidx.activity.compose.LocalActivity
import androidx.annotation.StringRes
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.LocalBringIntoViewSpec
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.em
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.limelight.R
import com.limelight.ps.PsPrefs
import com.limelight.shell.LocalShell
import com.limelight.shell.companion.CompanionHelp
import com.limelight.shell.companion.PublishCompanionHelp
import com.limelight.shell.companion.companionShowsHelp
import com.limelight.shell.companion.helpShort
import com.limelight.shell.companion.hidesDescription
import com.limelight.shell.data.ProfileStore
import com.limelight.shell.screens.HelpDock
import com.limelight.shell.screens.HelpDockLayout
import com.limelight.shell.screens.SettingsLayout
import com.limelight.shell.screens.SettingsPanelFrame
import com.limelight.shell.screens.ShoulderLabel
import com.limelight.shell.screens.TabPill
import com.limelight.shell.screens.TabRow
import com.limelight.shell.settings.*
import com.limelight.shell.ui.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext

/**
 * The tabs of the PlayStation settings. Each lists the classic stream keys it shows ([classic], stored in [PsPrefs.FILE] under their
 * usual names) followed by its PlayStation-only rows.
 */
private enum class PsSettingsTab(@StringRes val label: Int, val icon: String, val classic: List<String>) {
    STREAM(R.string.settings_tab_stream, Symbols.CAST,
        listOf("list_resolution", "list_fps", "seekbar_bitrate_kbps", "video_format", "checkbox_enable_hdr", "frame_pacing")),
    PICTURE(R.string.ps_settings_tab_picture, Symbols.MONITOR,
        listOf("checkbox_sgsr", "list_sgsr_quality", "seekbar_sgsr_sharpness", "list_sgsr_supersampling_level", "list_frame_generation", "list_video_scale_mode",
            "checkbox_enable_perf_overlay", "checkbox_enable_perf_overlay_lite")),
    NETWORK(R.string.ps_settings_tab_network, Symbols.NETWORK_CHECK,
        listOf("checkbox_prevent_packet_loss", "checkbox_enable_pip")),
    CONTROLLER(R.string.settings_tab_controls, Symbols.SPORTS_ESPORTS,
        listOf("checkbox_enable_rumble", "list_rumble_feel", "seekbar_deadzone", "checkbox_gamepad_motion_fallback", "checkbox_force_device_motion",
            "seekbar_gyro_smoothing", "checkbox_flip_face_buttons")),
    AUDIO(R.string.settings_tab_audio, Symbols.GRAPHIC_EQ, emptyList()),
}

/** The values the PlayStation offers of the classic choices: the console streams only these resolutions, rates and codecs. */
private val PS_CHOICES = mapOf(
    "list_resolution" to setOf("640x360", "1280x720", "1920x1080"),
    "list_fps" to setOf("30", "60"),
    "video_format" to setOf("neverh265", "forceh265"),
)

/** One row of the screen: a classic setting from the catalog or a PlayStation-only one. */
private class PsRow(
    val key: String,
    val icon: String,
    val label: String,
    val summary: String,
    val detail: String,
    val value: RowValue,
    val enabled: Boolean = true,
    val impact: SettingImpact? = null,
)

/**
 * The PlayStation settings (issue #25): every setting of a PlayStation stream, apart from the PC settings, in their own file
 * ([PsPrefs.FILE]). The layout is the settings screen's: the title, the L1 / R1 tab row, the list with the gliding plate and the
 * description panel (a dock on a compact stage; the companion window shows it on a dual-screen device). Every change is saved at once.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun PsSettingsScreen(profiles: ProfileStore, appearance: Appearance) {
    val shell = LocalShell.current
    val context = LocalContext.current
    val res = textResources()
    val colors = LocalDelosColors.current
    profiles.catalog.refresh(res)
    val feedback = LocalFeedback.current
    val activity = LocalActivity.current
    val prefs = remember { PsPrefs.prefs(context) }
    val stream = remember { StreamSettingsStore(profiles.catalog) { prefs } }
    DisposableEffect(stream) { onDispose { stream.close() } }
    val values by stream.values.collectAsStateWithLifecycle()
    var caps by remember(activity) { mutableStateOf<SettingsCapabilities?>(null) }
    LaunchedEffect(activity) {
        if (activity != null) caps = withContext(Dispatchers.Default) { SettingsCapabilities.read(activity) }
    }
    var tabIndex by rememberSaveable { mutableIntStateOf(0) }
    val tab = PsSettingsTab.entries[tabIndex]
    val rows = psRows(tab, profiles.catalog, stream, values, caps, prefs)
    var focusedKey by remember(tab) { mutableStateOf<String?>(null) }
    val focused = rows.firstOrNull { it.key == focusedKey }
    val transfer = rememberFocusTransfer()
    val window = LocalWindowInfo.current
    val scroll = remember(tab) { ScrollState(0) }
    val navigation = key(tab) { rememberFocusedListNavigation(rows.map { it.key }) }
    val stage = LocalStage.current
    val k = stage.dpPerPx * LocalDensity.current.density
    val compact = stage.shape == StageShape.COMPACT
    val bringIntoView = rememberMarginBringIntoView(scroll, before = 72f * k, after = (if (compact) SettingsLayout.COMPACT_LIST_AFTER else 110f) * k, stagePx = k)
    fun changeTab(next: Int) {
        if (next == tabIndex) return
        transfer.request()
        tabIndex = next
    }
    DisposableEffect(shell, tabIndex) {
        val handler: (Int) -> Unit = { dir ->
            changeTab(Math.floorMod(tabIndex + dir, PsSettingsTab.entries.size))
            feedback.play(Cue.MOVE)
        }
        shell.onShoulder = handler
        onDispose { if (shell.onShoulder === handler) shell.onShoulder = null }
    }
    LaunchedEffect(tab, navigation, caps) {
        if (caps == null) return@LaunchedEffect
        val first = rows.firstOrNull()?.key ?: return@LaunchedEffect
        snapshotFlow { window.isWindowFocused }.first { it }
        withFrameNanos { }
        withFrameNanos { }
        feedback.silenceNextFocus()
        navigation.requester(first).requestFocus()
    }
    val title = stringResource(R.string.ps_settings_title)
    val helpTitle = focused?.label ?: res.getString(tab.label)
    val helpBody = focused?.detail?.takeIf { it.isNotBlank() } ?: res.getString(R.string.ps_settings_note)
    val help = CompanionHelp(
        identity = focused?.key ?: "ps:${tab.name}",
        icon = focused?.icon ?: tab.icon,
        title = helpTitle,
        body = helpBody,
        short = helpShort(focused?.summary, helpBody, helpTitle),
        section = title,
        unavailable = focused?.enabled == false,
        impact = focused?.impact,
        savedNote = true,
    )
    PublishCompanionHelp(help)
    val hideDescription = hidesDescription(companionShowsHelp(), help)
    val margin = SettingsLayout.margin(stage)
    val bottom = SettingsLayout.bottom(appearance)
    val tabs: @Composable () -> Unit = {
        ShoulderLabel("L1")
        PsSettingsTab.entries.forEach { next ->
            TabPill(next.icon, res.getString(next.label), next == tab, Modifier.padding(horizontal = (if (compact) 3 else 5).px), collapse = compact) {
                changeTab(next.ordinal)
            }
        }
        ShoulderLabel("R1")
    }
    val list: @Composable (Modifier) -> Unit = { listModifier ->
        CompositionLocalProvider(LocalBringIntoViewSpec provides bringIntoView, LocalEntranceStagger provides rememberEntranceStagger(tab)) {
            key(tab) {
                // One ring glides between the rows; the host carries the list's viewport, so the ring fades with the rows
                SettingRowList(listModifier.settingListViewport(stage, scroll, if (compact) SettingsLayout.COMPACT_LIST_FADE_TOP else 14f, if (compact) SettingsLayout.COMPACT_LIST_FADE else 90f)) {
                    Column(Modifier.fillMaxSize().focusedListBoundary(navigation).verticalScroll(scroll)
                        .padding(start = (18f + SETTING_LIST_ROOM).px, end = (18f + SETTING_LIST_ROOM).px, top = 14.px, bottom = 140.px),
                        verticalArrangement = Arrangement.spacedBy(SETTING_ROWS.gap.px)) {
                        rows.forEach { row ->
                            key(row.key) {
                                SettingRow(row.icon, row.label, row.value, Modifier.focusedListRow(navigation, row.key),
                                    enabled = row.enabled, impact = row.impact, onFocused = { focusedKey = row.key })
                            }
                        }
                    }
                }
            }
        }
    }
    Box(Modifier.fillMaxSize()) {
        FocusTransferTarget(transfer)
        if (compact) {
            // No title on a compact stage: the tabs under the status bar, the list at full width and the description as a dock under it
            TabRow(SettingsLayout.COMPACT_TABS_Y, SettingsLayout.TABS_HEIGHT.px, left = margin, right = stage.stageWidth - margin) { tabs() }
            Column(Modifier.fillMaxSize().padding(start = margin.px, end = margin.px, top = SettingsLayout.COMPACT_LIST_TOP.px, bottom = bottom.px),
                verticalArrangement = Arrangement.spacedBy(HelpDockLayout.LIST_GAP.px)) {
                list(Modifier.weight(1f).fillMaxWidth())
                if (!hideDescription) {
                    val dockHeight = HelpDockLayout.height(stage.stageHeight, SettingsLayout.COMPACT_LIST_TOP + 14f, bottom, SettingsLayout.tallRow(stage))
                    PsSettingsPanel(tab, focused, Modifier.fillMaxWidth().height(dockHeight.px))
                }
            }
        } else {
            BasicText(title, style = TextStyle(color = colors.text, fontFamily = Unbounded, fontWeight = FontWeight.SemiBold, fontSize = 46.tpx),
                modifier = Modifier.offset(SettingsLayout.TITLE_X.px, SettingsLayout.TITLE_Y.px))
            TabRow(SettingsLayout.TABS_Y, SettingsLayout.TABS_HEIGHT.px, left = SettingsLayout.TITLE_X, right = stage.stageWidth - SettingsLayout.TITLE_X) { tabs() }
            Row(Modifier.fillMaxSize().padding(start = SettingsLayout.TITLE_X.px, end = SettingsLayout.TITLE_X.px, top = SettingsLayout.LIST_TOP.px, bottom = bottom.px),
                horizontalArrangement = Arrangement.spacedBy(56.px)) {
                list(Modifier.weight(1f))
                if (!hideDescription) PsSettingsPanel(tab, focused, Modifier.width(520.px).fillMaxHeight())
            }
        }
    }
}

/** The rows of [tab]: its classic settings (device-hidden ones left out, choices narrowed to what the console streams), then its own. */
@Composable
private fun psRows(
    tab: PsSettingsTab, catalog: SettingsCatalog, stream: StreamSettingsStore, values: Map<String, *>, caps: SettingsCapabilities?,
    prefs: SharedPreferences,
): List<PsRow> {
    val res = textResources()
    val auto = values[PsPrefs.AUTO_BITRATE] as? Boolean ?: true
    val unlocked = values[PsPrefs.UNLOCK_BITRATE] as? Boolean ?: false
    val maxBitrate = if (unlocked) PsPrefs.UNLOCKED_MAX_BITRATE_KBPS else PsPrefs.MAX_BITRATE_KBPS
    val height = (values["list_resolution"] as? String)?.substringAfter('x')?.toIntOrNull() ?: 1080
    val classic = tab.classic.mapNotNull { key -> catalog.all.firstOrNull { it.key == key } }
        .filter { it.key !in (caps?.hidden ?: emptySet()) }
        .flatMap { spec ->
            val current = values[spec.key] ?: spec.default
            val device = if (spec.kind == SettingKind.CHOICE) caps?.choices(res, spec, values) ?: spec.choices else spec.choices
            val choices = PS_CHOICES[spec.key]?.let { allowed -> device.filter { it.value in allowed } } ?: device
            val bitrate = spec.key == BITRATE_KEY
            val value = when (spec.kind) {
                SettingKind.TOGGLE -> RowValue.Toggle(current as? Boolean ?: false) { stream.write(spec, it) }
                SettingKind.CHOICE -> RowValue.Choice(spec.choiceLabel(res, current, choices), { dir ->
                    nextSettingChoice(current, choices, dir)?.let { stream.write(spec, it.value) }
                }, options = choices.map { it.label }, selected = choices.indexOfFirst { it.value == current },
                    onPick = { i -> choices.getOrNull(i)?.let { stream.write(spec, it.value) } })
                // The bitrate within what the console streams (PsPrefs.maxBitrateKbps), coloured by what this size is sure to get
                SettingKind.SLIDER -> if (bitrate) RowValue.Slider(((current as? Number)?.toInt() ?: PsPrefs.MIN_BITRATE_KBPS)
                    .coerceIn(PsPrefs.MIN_BITRATE_KBPS, maxBitrate), PsPrefs.MIN_BITRATE_KBPS, maxBitrate,
                    spec.step, spec.unit, spec.divisor, zones = listOf(
                        SliderZone(PsPrefs.idealBitrateKbps(height), OnlineGreen),
                        SliderZone(PsPrefs.autoCeilingKbps(height), WarnOrange),
                        SliderZone(PsPrefs.UNLOCKED_MAX_BITRATE_KBPS, LowRed),
                    )) { stream.write(spec, it) }
                    else RowValue.Slider((current as? Number)?.toInt() ?: spec.min, spec.min, spec.max, spec.step, spec.unit, spec.divisor) { stream.write(spec, it) }
                else -> RowValue.Info(current?.toString().orEmpty())
            }
            // The bitrate's help is its zones (the PC text points at the stream menu's bitrate, which a console stream lacks)
            val row = PsRow(spec.key, spec.icon, spec.label, spec.description,
                if (bitrate) res.getString(R.string.ps_settings_bitrate_zones) else SettingsHelp.details(res, spec, values, choices), value,
                enabled = caps != null && stream.enabled(spec, caps) && !(bitrate && auto), impact = impact(spec.key, values))
            // Automatic bitrate sits right above the bitrate it replaces, the unlock right below it
            if (bitrate) listOf(autoBitrateRow(res, auto, prefs), row, unlockBitrateRow(res, unlocked, !auto, prefs)) else listOf(row)
        }
    return classic + psOnlyRows(tab, values, prefs)
}

private const val BITRATE_KEY = "seekbar_bitrate_kbps"

/** The console picks its bitrate from the loss DELOS reports (PsPrefs.AUTO_BITRATE); the fixed bitrate row is off while it does. */
private fun autoBitrateRow(res: android.content.res.Resources, on: Boolean, prefs: SharedPreferences): PsRow {
    val text = res.getString(R.string.ps_settings_auto_bitrate_description)
    return PsRow(PsPrefs.AUTO_BITRATE, Symbols.NETWORK_CHECK, res.getString(R.string.ps_settings_auto_bitrate_title), text, text,
        RowValue.Toggle(on) { prefs.edit().putBoolean(PsPrefs.AUTO_BITRATE, it).apply() })
}

/** A fixed bitrate past what a PS5 is reported to send (PsPrefs.UNLOCK_BITRATE); locking it again brings a higher bitrate back down. */
private fun unlockBitrateRow(res: android.content.res.Resources, on: Boolean, enabled: Boolean, prefs: SharedPreferences): PsRow {
    val text = res.getString(R.string.ps_settings_unlock_bitrate_description)
    return PsRow(PsPrefs.UNLOCK_BITRATE, Symbols.MANUAL_BITRATE, res.getString(R.string.ps_settings_unlock_bitrate_title), text, text,
        RowValue.Toggle(on) { unlock ->
            val edit = prefs.edit().putBoolean(PsPrefs.UNLOCK_BITRATE, unlock)
            if (!unlock && prefs.getInt(BITRATE_KEY, 0) > PsPrefs.MAX_BITRATE_KBPS) edit.putInt(BITRATE_KEY, PsPrefs.MAX_BITRATE_KBPS)
            edit.apply()
        }, enabled = enabled)
}

/** The PlayStation-only rows of [tab], written straight to the [PsPrefs] keys; [values] makes them follow every change of the file. */
@Composable
private fun psOnlyRows(tab: PsSettingsTab, values: Map<String, *>, prefs: SharedPreferences): List<PsRow> {
    val res = textResources()
    fun toggle(key: String, icon: String, @StringRes label: Int, @StringRes description: Int, default: Boolean): PsRow {
        val on = values[key] as? Boolean ?: default
        return PsRow(key, icon, res.getString(label), res.getString(description), res.getString(description),
            RowValue.Toggle(on) { prefs.edit().putBoolean(key, it).apply() })
    }
    fun <T> choice(
        key: String, icon: String, @StringRes label: Int, @StringRes description: Int, options: List<T>, current: T, names: List<String>,
        write: (SharedPreferences.Editor, T) -> Unit,
    ): PsRow {
        val index = options.indexOf(current).coerceAtLeast(0)
        fun pick(i: Int) = prefs.edit().also { write(it, options[i]) }.apply()
        val text = res.getString(description)
        return PsRow(key, icon, res.getString(label), text, "${names[index]}\n\n$text",
            RowValue.Choice(names[index], { dir -> pick(Math.floorMod(index + dir, options.size)) }, options = names, selected = index, onPick = ::pick))
    }
    return when (tab) {
        PsSettingsTab.NETWORK -> listOf(
            toggle(PsPrefs.KEYFRAME_ON_LOSS, Symbols.REFRESH, R.string.ps_settings_keyframe_title, R.string.ps_settings_keyframe_description,
                false),
            choice(PsPrefs.VIDEO_QUEUE, Symbols.HOURGLASS_TOP, R.string.ps_settings_video_queue_title, R.string.ps_settings_video_queue_description,
                listOf("few", "normal", "many"), values[PsPrefs.VIDEO_QUEUE] as? String ?: "few",
                listOf(R.string.ps_settings_video_queue_few, R.string.ps_settings_video_queue_normal, R.string.ps_settings_video_queue_many).map { res.getString(it) },
            ) { editor, v -> editor.putString(PsPrefs.VIDEO_QUEUE, v) },
        )
        PsSettingsTab.AUDIO -> listOf(
            choice(PsPrefs.AUDIO_BUFFER, Symbols.TIMER, R.string.ps_settings_audio_buffer_title, R.string.ps_settings_audio_buffer_description,
                listOf("low", "normal", "high"), values[PsPrefs.AUDIO_BUFFER] as? String ?: "low",
                listOf(R.string.ps_settings_audio_buffer_low, R.string.ps_settings_audio_buffer_normal, R.string.ps_settings_audio_buffer_high).map { res.getString(it) },
            ) { editor, v -> editor.putString(PsPrefs.AUDIO_BUFFER, v) },
            toggle(PsPrefs.SWAP_CHANNELS, Symbols.SWAP_HORIZ, R.string.ps_settings_swap_channels_title, R.string.ps_settings_swap_channels_description,
                false),
        )
        else -> emptyList()
    }
}

/**
 * The description panel (a dock on a compact stage): the focused row's icon, name and help, or the tab and what this screen is for when
 * nothing is focused; the impact legend while the row has chips, and the autosave note.
 */
@Composable
private fun PsSettingsPanel(tab: PsSettingsTab, focused: PsRow?, modifier: Modifier) {
    val colors = LocalDelosColors.current
    val res = textResources()
    val compact = LocalStage.current.shape == StageShape.COMPACT
    val icon = focused?.icon ?: tab.icon
    val title = focused?.label ?: res.getString(tab.label)
    val detail = focused?.detail?.takeIf { it.isNotBlank() } ?: stringResource(R.string.ps_settings_note)
    val body: @Composable ColumnScope.() -> Unit = {
        BasicText(detail, style = TextStyle(color = colors.text2, fontFamily = Figtree, fontSize = (if (compact) 21 else 22).tpx, lineHeight = 1.45.em))
        if (focused?.enabled == false) BasicText(stringResource(R.string.settings_screen_unavailable),
            style = TextStyle(color = colors.text3, fontFamily = Figtree, fontSize = 18.tpx))
    }
    val savedNote: @Composable ColumnScope.() -> Unit = {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.px)) {
            Symbol(Symbols.CHECK_CIRCLE, 20.tpx, colors.text3)
            BasicText(stringResource(R.string.settings_game_saved_note), style = TextStyle(color = colors.text3, fontFamily = Figtree, fontSize = 17.tpx))
        }
    }
    if (compact) {
        HelpDock(modifier, icon, title, focused?.key to detail, focused?.impact != null, footer = savedNote, body = body)
        return
    }
    SettingsPanelFrame(modifier) {
        Box(Modifier.size(120.px).clip(CircleShape).background(colors.accent), contentAlignment = Alignment.Center) {
            Symbol(icon, 64.tpx, colors.onAccent, filled = true)
        }
        BasicText(title, style = TextStyle(color = colors.text, fontFamily = Unbounded, fontWeight = FontWeight.SemiBold, fontSize = 30.tpx),
            modifier = Modifier.padding(top = 8.px))
        AutoScrollingHelp(focused?.key to detail, Modifier.weight(1f), content = body)
        if (focused?.impact != null) ImpactLegend()
        savedNote()
    }
}
