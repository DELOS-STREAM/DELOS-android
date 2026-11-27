package com.limelight.shell

import android.content.Intent
import android.content.Context
import android.content.SharedPreferences
import android.annotation.SuppressLint
import android.os.Bundle
import android.view.KeyEvent
import android.view.MotionEvent
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.limelight.shell.ui.ControllerGlyphs
import com.limelight.shell.ui.InputKinds
import com.limelight.shell.ui.isTvDevice
import com.limelight.shell.companion.CompanionHost
import com.limelight.shell.companion.CompanionState
import com.limelight.shell.data.ArtworkStore
import com.limelight.shell.data.GameMetadataStore
import com.limelight.shell.data.LocalGameMetadata
import com.limelight.shell.data.IgdbImageCache
import com.limelight.shell.data.LocalIgdbImages
import com.limelight.shell.data.BoxArtCache
import com.limelight.shell.data.LocalArtwork
import com.limelight.shell.data.GameLauncher
import com.limelight.shell.data.LocalBoxArt
import com.limelight.shell.data.PcRepository
import com.limelight.shell.data.PcSetup
import com.limelight.shell.data.ProfileStore
import com.limelight.shell.settings.Appearance
import com.limelight.shell.settings.AppearanceStore
import com.limelight.shell.settings.AppLanguage
import com.limelight.shell.settings.SettingsTab
import com.limelight.shell.settings.StartupStore
import com.limelight.shell.ui.Feedback
import com.limelight.shell.ui.LocalDelosColors
import com.limelight.shell.ui.LocalReducedMotion
import com.limelight.shell.ui.LocalFeedback
import com.limelight.shell.ui.Stage
import com.limelight.shell.ui.animateDelosColors
import com.limelight.shell.ui.delosColors
import com.limelight.shell.ui.systemAccentHue
import com.limelight.shell.ui.rowFirstNavigation
import kotlinx.coroutines.flow.MutableStateFlow
import com.limelight.utils.DelosToast

/**
 * Entry point of the Delos UI (see docs/GUI.md): the launcher activity on phones, TVs and
 * in multi-window, and the target of art:// links and PC shortcuts.
 */
class ShellActivity : ComponentActivity() {
    private lateinit var feedback: Feedback
    private lateinit var appearance: AppearanceStore
    private var volumeHud: VolumeHud? = null
    private lateinit var repo: PcRepository
    private lateinit var boxArt: BoxArtCache
    private lateinit var artwork: ArtworkStore
    private lateinit var metadata: GameMetadataStore
    private lateinit var igdbImages: IgdbImageCache
    private lateinit var profiles: ProfileStore
    private val keys = GlobalKeys()
    /** The shell, for the input hooks that must see an event before anything consumes it (null until created). */
    private var shellState: ShellState? = null

    /** The second panel of a dual-screen device (docs/DUAL_SCREEN.md): shown from `onResume` to `onStop` while a companion display exists. */
    private var companion: CompanionHost? = null
    private var placement: ShellPlacement? = null

    /** This instance started the shell again on the main panel and ends: nothing in `onCreate` after its first lines was set up. */
    private var movedToMain = false

    /** The Language row's preference (the current profile's). The app locale is the truth; the row mirrors it ([AppLanguage]). */
    private var languageSettings: SharedPreferences? = null
    private val languageListener = SharedPreferences.OnSharedPreferenceChangeListener { prefs, key ->
        AppLanguage.onPreferenceChanged(this, prefs, key)
    }

    /** A link or shortcut the shell still has to handle (from the intent that started or reached it). */
    private val links = MutableStateFlow<ShellLink?>(null)

    /** The result of a stream that ended on its own, which the shell still has to show (once). */
    private val results = MutableStateFlow<StreamResult?>(null)

    /** The game controllers present while the shell is in front, so a removed one (no longer readable) is known as one. */
    private val pads = mutableSetOf<Int>()
    private val padListener = object : android.hardware.input.InputManager.InputDeviceListener {
        override fun onInputDeviceAdded(id: Int) {
            if (isPad(id) && pads.add(id)) feedback.play(com.limelight.shell.ui.Cue.PAD_CONNECT)
        }
        override fun onInputDeviceRemoved(id: Int) {
            if (pads.remove(id)) feedback.play(com.limelight.shell.ui.Cue.PAD_DISCONNECT)
        }
        override fun onInputDeviceChanged(id: Int) = Unit
    }

    /** A real game controller: its gamepad part only (a DualSense also brings a touchpad and motion sensors), not a virtual one. */
    private fun isPad(id: Int): Boolean {
        val device = android.view.InputDevice.getDevice(id) ?: return false
        return !device.isVirtual && device.supportsSource(android.view.InputDevice.SOURCE_GAMEPAD)
    }

    private fun watchPads(on: Boolean) {
        val input = getSystemService(android.hardware.input.InputManager::class.java) ?: return
        if (on) {
            // Controllers that came or went while the shell was away (in a stream) are taken as they are now, silently
            pads.clear()
            android.view.InputDevice.getDeviceIds().filterTo(pads, ::isPad)
            input.registerInputDeviceListener(padListener, null)
        } else {
            input.unregisterInputDeviceListener(padListener)
        }
    }

    // Android 10-12: puts the saved app language on this activity's resources (a no-op on Android 13+, where the system does it)
    override fun attachBaseContext(newBase: Context) {
        super.attachBaseContext(AppLanguage.wrap(newBase))
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        ControllerGlyphs.start(this)
        placement = ShellPlacement(this)
        if (savedInstanceState == null && placement?.moveOnCreate() == true) {
            movedToMain = true
            return
        }
        hideSystemBars()
        // No system snapshot of this window (Android 13+): coming back from a stream it would show the last frame from before, the launch
        // tunnel, for a moment
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU) setRecentsScreenshotEnabled(false)
        feedback = Feedback(this)
        // Everything below reads the current profile's settings
        profiles = ProfileStore(this)
        bindLanguage()
        appearance = AppearanceStore(profiles::settings)
        volumeHud = VolumeHud(this, { appearance.appearance.value }, {
            profiles.settings().getBoolean(com.limelight.preferences.PreferenceConfiguration.SOUND_ON_DEVICE_STRING, true)
        })
        repo = PcRepository(this, profiles::settings)
        val settingsAgain = intent.getBooleanExtra(ShellPlacement.EXTRA_RESTORE_SETTINGS, false) && profiles.profiles.value.all.isNotEmpty()
        val shell = ShellState(if (profiles.profiles.value.all.isEmpty()) Screen.Boot() else if (settingsAgain) Screen.Settings else Screen.Start)
        shellState = shell
        if (settingsAgain) shell.settingsTab = SettingsTab.DISPLAY
        DelosToast.register(this, show = { text, durationMs, kind -> shell.showToast(text, durationMs = durationMs, kind = kind) })
        shell.onToast = { kind ->
            feedback.play(when (kind) {
                com.limelight.utils.ToastKind.SUCCESS -> com.limelight.shell.ui.Cue.TOAST_SUCCESS
                com.limelight.utils.ToastKind.WARNING -> com.limelight.shell.ui.Cue.TOAST_WARNING
                com.limelight.utils.ToastKind.ERROR -> com.limelight.shell.ui.Cue.ERROR
                com.limelight.utils.ToastKind.INFO, com.limelight.utils.ToastKind.ACTION -> com.limelight.shell.ui.Cue.TOAST_INFO
            })
        }
        metadata = GameMetadataStore(this, profiles::settings)
        igdbImages = IgdbImageCache(this)
        val startup = StartupStore(profiles::settings)
        profiles.onSettingsChanged {
            appearance.reload()
            bindLanguage()
            startup.reload()
            metadata.reloadFilters()
            repo.profileChanged()
        }
        lifecycle.addObserver(repo)
        repo.bind()
        boxArt = BoxArtCache(this) { repo.binder?.uniqueId }
        artwork = ArtworkStore(this)
        if (com.limelight.BuildConfig.DEBUG && android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU) {
            com.limelight.shell.ui.BackgroundShaders.verify()
        }
        val launcher = GameLauncher(this, repo)
        val setup = PcSetup(this, repo, profiles)
        if (savedInstanceState == null) {
            links.value = ShellLink.from(this, intent)
            results.value = StreamResult.take(intent)
        }
        // Debug builds only: the stream menu, the Full keyboard, a toast or a screen with fake state instead of the shell (docs/GUI.md, "Stream menu preview", "Full keyboard", "Toasts", "Screen shapes")
        val preview = StreamMenuPreview.from(intent) ?: FullKeyboardPreview.from(intent) ?: ToastPreview.from(intent, shell) ?: SettingsTabsPreview.from(intent) ?: SettingsListPreview.from(intent) ?: GameSettingsPreview.from(intent) ?: TopMenuPreview.from(intent) ?: ContextMenuPreview.from(intent) ?: CompanionPreview.from(intent) ?: ScreenPreviews.from(intent, shell)
        // Debug builds only: draws all of it in a box of another panel's size and density (Debug info menu, or `preview_shape`)
        val simulator = ShapeSimulator.from(this, intent)
        setContent {
            com.limelight.shell.ui.QuietAccessibility()
            val screen: @Composable () -> Unit = {
                if (preview != null) ShellTheme(appearance, feedback) {
                    preview()
                    com.limelight.shell.ui.ToastHost(shell.toast, shell::hideToast, shell::runToastAction, shell.toastPress)
                }
                else ShellRoot(appearance, feedback, keys, repo, boxArt, artwork, metadata, igdbImages, launcher, profiles, startup, setup, links, results, shell)
            }
            if (simulator != null) simulator.Host(shell, appearance.appearance, screen) else screen()
        }
        feedback.view = window.decorView
        val fakeCompanion = CompanionPreview.fakeState(intent) // debug builds only: made-up state for the real second display
        // The window follows the appearance of the screen it shows (a screen preview runs its own: debug builds only)
        companion = CompanionHost(this, { CompanionPreview.liveAppearance ?: appearance.appearance }) {
            // A screen preview (debug builds) runs its own shell state: the window shows what that one publishes
            fakeCompanion ?: (CompanionPreview.liveShell ?: shell).companionState()
        }
    }

    // Diagnostics (owner, 2026-11-27: after a stream on a TV the library came back laid out wrongly): what changed and where
    override fun onConfigurationChanged(newConfig: android.content.res.Configuration) {
        super.onConfigurationChanged(newConfig)
        com.limelight.diag.DiagLog.i("Shell", "Configuration changed: display ${com.limelight.display.DualScreen.displayIdOf(this)}, " + // l10n-ignore
            "${newConfig.screenWidthDp}x${newConfig.screenHeightDp} dp, ${newConfig.densityDpi} dpi, colorMode 0x" + // l10n-ignore
            Integer.toHexString(newConfig.colorMode))
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        ShellLink.from(this, intent)?.let { links.value = it }
        StreamResult.take(intent)?.let { results.value = it }
    }

    // Activity's public key dispatcher must still run after the shell's global controller keys. Every event first tells the input kind
    // what was used (ControllerGlyphs: the hints follow the last input); a hardware keyboard's keys then act as the pad's buttons
    // (GlobalKeys.keyboardButton), except while a button is being recorded. On an Android TV a remote's keys act as pad buttons too
    // (GlobalKeys.remoteButton) and holding its OK key is X (RemoteHold); on every other device those keys pass through untouched
    @SuppressLint("RestrictedApi")
    override fun dispatchKeyEvent(event: KeyEvent): Boolean {
        if (volumeHud?.handle(event) == true) return true
        ControllerGlyphs.key(event)
        // Any key shows the fullscreen viewer's overlay again, before a global shortcut may consume it
        shellState?.overviewViewerInput?.invoke()
        if (keys.capture != null) return keys.dispatch(event) || super.dispatchKeyEvent(event)
        if (event.keyCode == KeyEvent.KEYCODE_DPAD_CENTER && tvRemote(event)) {
            when (remoteHold.step(event.action, event.repeatCount)) {
                RemoteHold.Step.FIRE -> {
                    // GlobalKeys acts on a button's release, so the release alone is the whole press of X
                    keys.dispatch(KeyEvent(event.downTime, event.eventTime, KeyEvent.ACTION_UP, KeyEvent.KEYCODE_BUTTON_X, 0, 0, event.deviceId, event.scanCode, event.flags, event.source))
                    return true
                }
                RemoteHold.Step.CONSUME -> return true
                RemoteHold.Step.PASS -> Unit
            }
        }
        val shellEvent = when {
            InputKinds.fromPad(event.source) -> event
            InputKinds.isKeyboard(event.device) -> keyboardAsPad(event)
            tvRemote(event) -> remoteAsPad(event)
            else -> event
        }
        return keys.dispatch(shellEvent) || super.dispatchKeyEvent(shellEvent)
    }

    private val tv by lazy { isTvDevice(this) }
    private val remoteHold = RemoteHold()

    /** A key of a TV remote on an Android TV ([InputKinds.isTvRemote]): the only keys the remote map and the OK hold apply to. */
    private fun tvRemote(event: KeyEvent): Boolean = tv && InputKinds.isTvRemote(event.device)

    /** A TV remote's key as the pad button it stands for ([GlobalKeys.remoteButton]); any other key as it is. */
    private fun remoteAsPad(event: KeyEvent): KeyEvent {
        val button = GlobalKeys.remoteButton(event.keyCode) ?: return event
        return KeyEvent(event.downTime, event.eventTime, event.action, button, event.repeatCount, 0, event.deviceId, event.scanCode, event.flags, event.source)
    }

    /** A hardware keyboard's key as the pad button it stands for ([GlobalKeys.keyboardButton]); any other event as it is. */
    private fun keyboardAsPad(event: KeyEvent): KeyEvent {
        if (InputKinds.fromPad(event.source) || !InputKinds.isKeyboard(event.device)) return event
        // In the filter, Return applies (FilterChip's own Return handler) where A toggles a chip: it must reach Compose as Return
        if (shellState?.layer == Layer.Filters && (event.keyCode == KeyEvent.KEYCODE_ENTER || event.keyCode == KeyEvent.KEYCODE_NUMPAD_ENTER)) return event
        val typing = currentFocus?.onCheckIsTextEditor() == true
        val modified = event.isCtrlPressed || event.isAltPressed || event.isMetaPressed
        val button = GlobalKeys.keyboardButton(event.keyCode, typing, modified) ?: return event
        return KeyEvent(event.downTime, event.eventTime, event.action, button, event.repeatCount, 0, event.deviceId, event.scanCode, event.flags, event.source)
    }

    override fun dispatchTouchEvent(event: MotionEvent): Boolean {
        ControllerGlyphs.touch(event)
        shellState?.overviewViewerInput?.invoke()
        return super.dispatchTouchEvent(event)
    }

    override fun dispatchGenericMotionEvent(event: MotionEvent): Boolean {
        ControllerGlyphs.motion(event)
        shellState?.overviewViewerInput?.invoke()
        return super.dispatchGenericMotionEvent(event)
    }

    /** Follows the current profile's settings for the Language row: it shows the app language, and choosing there sets it. */
    private fun bindLanguage() {
        languageSettings?.unregisterOnSharedPreferenceChangeListener(languageListener)
        val settings = profiles.settings()
        languageSettings = settings
        AppLanguage.mirror(this, settings)
        settings.registerOnSharedPreferenceChangeListener(languageListener)
    }

    override fun onDestroy() {
        companion?.stop()
        if (movedToMain) {
            super.onDestroy()
            return
        }
        languageSettings?.unregisterOnSharedPreferenceChangeListener(languageListener)
        profiles.streamProfiles.close()
        repo.unbind()
        feedback.release()
        super.onDestroy()
    }

    override fun onResume() {
        super.onResume()
        if (movedToMain) return
        com.limelight.binding.audio.VolumeController.get(this).keepSoundOnDevice(
            profiles.settings().getBoolean(com.limelight.preferences.PreferenceConfiguration.SOUND_ON_DEVICE_STRING, true))
        feedback.reroute()
        feedback.holdOutput(true)
        watchPads(true)
        languageSettings?.let { AppLanguage.mirror(this, it) }
        profiles.streamProfiles.resume()
        placement?.start()
        companion?.start()
        resumeParkedConsole()
    }

    /**
     * Coming back to DELOS after leaving a PlayStation stream for another app (the home screen, the screen off): the session was kept
     * (PsSessionPark), so its stream opens again at once. A stream the user left himself is never parked, so nothing opens then.
     */
    private fun resumeParkedConsole() {
        val id = com.limelight.ps.PsConnection.parkedConsole() ?: return
        val console = com.limelight.shell.ps.PsConsoleStore.get(this).consoles.value.firstOrNull { it.id == id } ?: return
        com.limelight.shell.ps.startPsStream(this, console.id, console.name, null, resumed = true)
    }

    override fun onStop() {
        placement?.stop()
        companion?.stop()
        if (::feedback.isInitialized) {
            feedback.stopIntro()
            feedback.holdOutput(false)
            watchPads(false)
        }
        super.onStop()
    }

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        // The system shows the bars again after the notification shade or a dialog
        if (hasFocus) hideSystemBars()
    }

    // Delos draws its own status bar; the system bars only appear temporarily after an edge swipe
    private fun hideSystemBars() {
        WindowCompat.getInsetsController(window, window.decorView).apply {
            systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
            hide(WindowInsetsCompat.Type.systemBars())
        }
    }
}

@Composable
private fun ShellRoot(
    store: AppearanceStore,
    feedback: Feedback,
    keys: GlobalKeys,
    repo: PcRepository,
    boxArt: BoxArtCache,
    artwork: ArtworkStore,
    metadata: GameMetadataStore,
    igdbImages: IgdbImageCache,
    launcher: GameLauncher,
    profiles: ProfileStore,
    startup: StartupStore,
    setup: PcSetup,
    links: MutableStateFlow<ShellLink?>,
    results: MutableStateFlow<StreamResult?>,
    shell: ShellState,
) {
    ShellTheme(store, feedback) { appearance ->
        CompositionLocalProvider(
            LocalBoxArt provides boxArt, LocalArtwork provides artwork, LocalGameMetadata provides metadata, LocalIgdbImages provides igdbImages,
        ) {
            // Up and Down that no element handled land on the first element of the next row (RowNav, docs/GUI.md)
            Box(Modifier.fillMaxSize().rowFirstNavigation(), propagateMinConstraints = true) {
                Shell(appearance, store::update, keys, repo, launcher, profiles, startup, setup, links, results, shell)
            }
        }
    }
}

/** The palette, feedback, reduced motion and the 1920 x 1080 stage every screen of Delos is drawn in. */
@Composable
private fun ShellTheme(store: AppearanceStore, feedback: Feedback, content: @Composable (Appearance) -> Unit) {
    val appearance by store.appearance.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val systemHue = remember { systemAccentHue(context) }
    // The PlayStation mode turns DELOS blue. That switch happens at once: a cross-fade of the palette recomposed the whole
    // interface every frame for 0.6 s and jerked the PC <-> PS switch (owner, 2026-11-24); the backgrounds still cross-fade
    val playStation by com.limelight.shell.ps.PsMode.active.collectAsStateWithLifecycle()
    var lastPlayStation by remember { mutableStateOf(playStation) }
    val modeSwitch = playStation != lastPlayStation
    SideEffect { lastPlayStation = playStation }
    val colors = animateDelosColors(if (playStation) com.limelight.shell.ps.playStationColors() else delosColors(appearance, systemHue), animate = !modeSwitch)
    SideEffect {
        feedback.soundsEnabled = appearance.sounds
        feedback.hapticsEnabled = appearance.haptics
    }
    CompositionLocalProvider(LocalDelosColors provides colors, LocalFeedback provides feedback, LocalReducedMotion provides appearance.reducedMotion) {
        Stage(textScale = appearance.uiScale / 100f) { content(appearance) }
    }
}
