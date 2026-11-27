package com.limelight.shell.screens

import android.view.KeyEvent as AndroidKey
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animate
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.focusable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.systemGestureExclusion
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.limelight.R
import com.limelight.shell.*
import com.limelight.shell.ps.PsModeSwitch
import com.limelight.shell.ui.*
import com.limelight.shell.data.ProfileStore
import com.limelight.shell.settings.rememberBackGameBarValue
import com.limelight.shell.settings.TutorialStore
import dev.chrisbanes.haze.hazeSource
import dev.chrisbanes.haze.rememberHazeState
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

/** The tutorial's three chapters (owner, 2026-11-24): getting around, playing, the PlayStation's own buttons. */
private enum class Chapter(val title: Int, val note: Int) {
    NAVIGATE(R.string.tutorial_chapter_navigate, R.string.tutorial_chapter_navigate_note),
    PLAY(R.string.tutorial_chapter_play, R.string.tutorial_chapter_play_note),
    PLAYSTATION(R.string.tutorial_chapter_ps, R.string.tutorial_chapter_ps_note),
}

/** What a lesson puts on its stage. */
private enum class Scene { ROW, COVER, SIDE, SWITCH, NAV, STREAM, HOLD, WINDOWS_BACK, LEAVE, TRACKPAD, OSC, PS_BUTTONS, PS_TOUCH }

/**
 * One thing to learn: its chapter, texts, the button(s) or gesture it asks for, and the scene. Controller lessons name pad glyphs,
 * touch lessons a ghost finger path.
 */
private enum class Lesson(
    val chapter: Chapter, val title: Int, val body: Int, val action: Int, val scene: Scene,
    val glyphs: List<Glyph> = listOf(Glyph.TAP), val ghost: GhostPath? = null,
) {
    MOVE(Chapter.NAVIGATE, R.string.tutorial_move_title, R.string.tutorial_move_body, R.string.tutorial_move_try, Scene.ROW, listOf(Glyph.DPAD_HORIZONTAL)),
    CHOOSE(Chapter.NAVIGATE, R.string.tutorial_choose_title, R.string.tutorial_choose_body, R.string.tutorial_choose_try, Scene.COVER, listOf(Glyph.A)),
    BACK(Chapter.NAVIGATE, R.string.tutorial_back_title, R.string.tutorial_back_body, R.string.tutorial_back_try, Scene.COVER, listOf(Glyph.B)),
    INFO(Chapter.NAVIGATE, R.string.tutorial_info_title, R.string.tutorial_info_body, R.string.tutorial_info_try, Scene.COVER, listOf(Glyph.DPAD_DOWN)),
    OPTIONS(Chapter.NAVIGATE, R.string.tutorial_options_title, R.string.tutorial_options_body, R.string.tutorial_options_try, Scene.COVER, listOf(Glyph.MENU_RIGHT)),
    SIDE(Chapter.NAVIGATE, R.string.tutorial_side_title, R.string.tutorial_side_body, R.string.tutorial_side_try, Scene.SIDE, listOf(Glyph.MENU_LEFT)),
    SWITCH(Chapter.NAVIGATE, R.string.tutorial_switch_title, R.string.tutorial_switch_body, R.string.tutorial_switch_try, Scene.SWITCH, listOf(Glyph.R1)),
    STREAM(Chapter.PLAY, R.string.tutorial_stream_title, R.string.tutorial_stream_body, R.string.tutorial_stream_try, Scene.STREAM, listOf(BACK_BUTTON, GUIDE)),
    HOLD_GUIDE(Chapter.PLAY, R.string.tutorial_hold_title, R.string.tutorial_hold_body, R.string.tutorial_hold_try, Scene.HOLD, listOf(BACK_BUTTON, GUIDE)),
    WINDOWS_BACK(Chapter.PLAY, R.string.tutorial_windows_back_title, R.string.tutorial_windows_back_body, R.string.tutorial_windows_back_try, Scene.WINDOWS_BACK, listOf(Glyph.A)),
    LEAVE(Chapter.PLAY, R.string.tutorial_leave_title, R.string.tutorial_leave_body, R.string.tutorial_leave_try, Scene.LEAVE, listOf(Glyph.A)),
    PS_BUTTONS(Chapter.PLAYSTATION, R.string.tutorial_ps_buttons_title, R.string.tutorial_ps_buttons_body, R.string.tutorial_ps_buttons_try, Scene.PS_BUTTONS, listOf(Glyph.MENU_LEFT)),

    SWIPE(Chapter.NAVIGATE, R.string.tutorial_swipe_title, R.string.tutorial_swipe_body, R.string.tutorial_swipe_try, Scene.ROW, ghost = GhostPath.SWIPE_LEFT),
    TAP(Chapter.NAVIGATE, R.string.tutorial_choose_title, R.string.tutorial_tap_body, R.string.tutorial_tap_try, Scene.COVER, ghost = GhostPath.DOUBLE_TAP),
    PULL(Chapter.NAVIGATE, R.string.tutorial_info_title, R.string.tutorial_pull_body, R.string.tutorial_pull_try, Scene.COVER, ghost = GhostPath.PULL_UP),
    HOLD(Chapter.NAVIGATE, R.string.tutorial_options_title, R.string.tutorial_hold_touch_body, R.string.tutorial_hold_touch_try, Scene.COVER, ghost = GhostPath.HOLD),
    EDGE(Chapter.NAVIGATE, R.string.tutorial_side_title, R.string.tutorial_edge_body, R.string.tutorial_edge_try, Scene.SIDE, ghost = GhostPath.EDGE_RIGHT),
    NAV(Chapter.NAVIGATE, R.string.tutorial_nav_title, R.string.tutorial_nav_body, R.string.tutorial_nav_try, Scene.NAV, ghost = GhostPath.TAP),
    STREAM_TOUCH(Chapter.PLAY, R.string.tutorial_stream_title, R.string.tutorial_stream_touch_body, R.string.tutorial_stream_touch_try, Scene.STREAM, ghost = GhostPath.SWIPE_UP_BOTTOM),
    TRACKPAD(Chapter.PLAY, R.string.tutorial_trackpad_title, R.string.tutorial_trackpad_body, R.string.tutorial_trackpad_try, Scene.TRACKPAD),
    OSC(Chapter.PLAY, R.string.tutorial_osc_title, R.string.tutorial_osc_body, R.string.tutorial_osc_try, Scene.OSC),
    LEAVE_TOUCH(Chapter.PLAY, R.string.tutorial_leave_title, R.string.tutorial_leave_body, R.string.tutorial_leave_touch_try, Scene.LEAVE),
    PS_TOUCH(Chapter.PLAYSTATION, R.string.tutorial_ps_touch_title, R.string.tutorial_ps_touch_body, R.string.tutorial_ps_touch_try, Scene.PS_TOUCH, ghost = GhostPath.TAP),
}

/** The device's Back button (the Odin's lower right one) and Guide: a tap opens the stream menu, a hold is Guide (GuideTapHold). */
private val BACK_BUTTON = Glyph.Icon(Symbols.ARROW_BACK)
private val GUIDE = Glyph.Pad(GlyphRole.GUIDE)

private val PAD_LESSONS = listOf(Lesson.MOVE, Lesson.CHOOSE, Lesson.BACK, Lesson.INFO, Lesson.OPTIONS, Lesson.SIDE, Lesson.SWITCH,
    Lesson.STREAM, Lesson.HOLD_GUIDE, Lesson.WINDOWS_BACK, Lesson.LEAVE, Lesson.PS_BUTTONS)
private val TOUCH_LESSONS = listOf(Lesson.SWIPE, Lesson.TAP, Lesson.PULL, Lesson.HOLD, Lesson.EDGE, Lesson.NAV,
    Lesson.STREAM_TOUCH, Lesson.TRACKPAD, Lesson.OSC, Lesson.LEAVE_TOUCH, Lesson.PS_TOUCH)

private sealed interface Page {
    data object Intro : Page
    data object Input : Page
    data class Opening(val chapter: Chapter) : Page
    data class Learn(val lesson: Lesson) : Page
    data object End : Page
}

private fun pages(touch: Boolean, replay: Boolean): List<Page> = buildList {
    if (replay) add(Page.Intro)
    add(Page.Input)
    var chapter: Chapter? = null
    (if (touch) TOUCH_LESSONS else PAD_LESSONS).forEach { lesson ->
        if (lesson.chapter != chapter) { chapter = lesson.chapter; add(Page.Opening(lesson.chapter)) }
        add(Page.Learn(lesson))
    }
    add(Page.End)
}

/** How long a Back or Guide press must last to be Guide (GuideTapHold.HOLD_MS). */
private const val HOLD_MS = 500L

/**
 * The tutorial (issue #129, owner 2026-11-24): after the intro and the profile, "How do you play?", then one lesson per page with the
 * app's real elements, each waiting for the real input, chapter cards between, and "Pair your first device" at the end. Everything it
 * changes is local except the explicitly labeled Windows Back preference. [onDone]: Let's start (first launch) or Done (replay).
 */
@Composable
fun TutorialScreen(screen: Screen.Tutorial, keys: GlobalKeys, onDone: () -> Unit) {
    val shell = LocalShell.current
    val feedback = LocalFeedback.current
    val res = textResources()
    val reduced = LocalReducedMotion.current
    val context = LocalContext.current
    val tutorialStore = remember(context) { TutorialStore(context) }
    val profileSettings = remember(context) { ProfileStore.currentSettings(context) }
    val windowsBack = rememberBackGameBarValue(profileSettings).on
    var touch by rememberSaveable { mutableStateOf(ControllerGlyphs.kind == InputKind.TOUCH || ControllerGlyphs.kind == InputKind.POINTER) }
    var index by rememberSaveable { mutableIntStateOf(0) }
    var forward by remember { mutableStateOf(true) }
    val list = remember(touch, screen.replay) { pages(touch, screen.replay) }
    val page = list[index.coerceIn(list.indices)]
    LaunchedEffect(page) { if (page is Page.End) tutorialStore.markSeen() }
    val go: (Int) -> Unit = { to -> forward = to > index; index = to.coerceIn(list.indices) }
    val next = { go(index + 1) }
    // Per page: whether the lesson is done, and the bits of state its scene shows
    var success by remember(index, touch) { mutableStateOf(false) }
    val lesson = (page as? Page.Learn)?.lesson
    val complete: () -> Unit = { if (!success) { feedback.play(Cue.COMPLETE); success = true } }
    LaunchedEffect(index, touch, success) { if (success) { delay(1600); next() } }
    // A chapter card stays for a moment, then the first lesson follows
    LaunchedEffect(index, touch) { if (page is Page.Opening) { delay(2600); if (list[index] == page) next() } }

    val scene = remember(index, touch) { SceneState(lesson?.scene == Scene.COVER && lesson == Lesson.BACK) }
    val skip: () -> Unit = {
        feedback.play(Cue.BACK)
        if (page is Page.Input) {
            tutorialStore.markSeen()
            go(list.lastIndex)
        } else next()
    }

    // The pad's global buttons are the tutorial's while it runs: Home's side menu, options and filters must not open behind it
    val handle by rememberUpdatedState<(AndroidKey) -> Boolean>({ event -> handleKey(event, page, lesson, scene, success, complete, skip, next, windowsBack) })
    DisposableEffect(keys, shell) {
        val listener = keys.listener
        val capture = keys.capture
        val shoulder = shell.onShoulder
        keys.capture = { handle(it) }
        keys.listener = { }
        shell.onShoulder = null
        onDispose { keys.listener = listener; keys.capture = capture; shell.onShoulder = shoulder }
    }
    BackHandler {
        when {
            shell.picker != null -> shell.closePicker()
            lesson == Lesson.BACK && scene.menu -> { scene.menu = false; feedback.play(Cue.BACK); complete() }
            lesson == Lesson.STREAM_TOUCH && !success -> { scene.stream = true; complete() }
            scene.menu -> scene.menu = false
            scene.side -> scene.side = false
            page is Page.Intro -> { tutorialStore.markSeen(); shell.navigate(screen.back ?: Screen.Settings) }
            index > (if (screen.replay) 1 else 0) -> { feedback.play(Cue.BACK); go(index - 1) }
            screen.replay -> { tutorialStore.markSeen(); shell.navigate(screen.back ?: Screen.Settings) }
        }
    }

    val haze = rememberHazeState()
    Box(Modifier.fillMaxSize()) {
        Box(Modifier.fillMaxSize().hazeSource(haze)) {
            AnimatedContent(
                index to touch,
                transitionSpec = {
                    if (reduced) fadeIn(tween(Motion.FADE)) togetherWith fadeOut(tween(Motion.FADE))
                    else {
                        val dir = if (forward) 1 else -1
                        (fadeIn(tween(520, 120)) + slideInHorizontally(tween(720, easing = EaseOut)) { dir * it / 6 }) togetherWith
                            (fadeOut(tween(260)) + slideOutHorizontally(tween(420, easing = Motion.In)) { -dir * it / 8 })
                    }
                },
                label = "tutorialPage",
            ) { (at, _) ->
                val shown = list[at.coerceIn(list.indices)]
                // Every page holds the focus somewhere (its own element asks after this), or the window would drop the pad's keys
                val pageFocus = remember { FocusRequester() }
                InitialFocus(pageFocus)
                Box(Modifier.fillMaxSize().focusRequester(pageFocus).focusable()) {
                when (shown) {
                    Page.Intro -> WelcomeIntro(onBegin = next, onSkip = null)
                    Page.Input -> InputPage(touch) { pickTouch -> touch = pickTouch; forward = true; index = list.indexOfFirst { it is Page.Opening }.let { if (it < 0) 0 else it } }
                    is Page.Opening -> {
                        val count = list.count { it is Page.Learn && it.lesson.chapter == shown.chapter }
                        TapToContinue(next) {
                            TutorialChapterCard(at, shown.chapter.ordinal + 1, stringResource(R.string.tutorial_chapter_label, shown.chapter.ordinal + 1),
                                stringResource(shown.chapter.title), stringResource(shown.chapter.note), res.getQuantityString(R.plurals.tutorial_chapter_lessons, count, count))
                        }
                    }
                    is Page.Learn -> {
                        val siblings = list.filterIsInstance<Page.Learn>().filter { it.lesson.chapter == shown.lesson.chapter }
                        val position = siblings.indexOfFirst { it.lesson == shown.lesson }
                        val guideOnly = windowsBack && shown.lesson in listOf(Lesson.STREAM, Lesson.HOLD_GUIDE)
                        val body = if (guideOnly) {
                            if (shown.lesson == Lesson.STREAM) R.string.tutorial_stream_guide_only_body else R.string.tutorial_hold_guide_only_body
                        } else shown.lesson.body
                        val action = if (guideOnly) {
                            if (shown.lesson == Lesson.STREAM) R.string.tutorial_stream_guide_only_try else R.string.tutorial_hold_guide_only_try
                        } else shown.lesson.action
                        TutorialLessonPage(
                            at, stringResource(R.string.tutorial_step, stringResource(shown.lesson.chapter.title), position + 1, siblings.size),
                            stringResource(shown.lesson.title), stringResource(body), stringResource(action),
                            if (guideOnly) listOf(GUIDE) else shown.lesson.glyphs, at == index && success, stringResource(R.string.tutorial_success),
                            progress = { TutorialProgress(position, siblings.size) },
                        ) {
                            LessonScene(shown.lesson, scene, at == index && success, complete, profileSettings, windowsBack)
                            val ghost = shown.lesson.ghost
                            if (ghost != null && !(at == index && success)) GhostFinger(ghost, Modifier.matchParentSize())
                        }
                    }
                    Page.End -> EndPage(at, screen.replay, onDone)
                }
                }
            }
        }
        // The real layers of the shell, opened by the lessons
        val anchor = scene.anchor
        val menu = if (scene.menu) ContextMenu(res.getString(R.string.tutorial_game), listOf(
            MenuItem(Symbols.PLAY_ARROW, res.getString(R.string.shell_hint_start)) { scene.menu = false },
            MenuItem(Symbols.IMAGE, res.getString(R.string.tutorial_menu_artwork)) { scene.menu = false },
            MenuItem(Symbols.TUNE, res.getString(R.string.tutorial_menu_settings)) { scene.menu = false },
            MenuItem(Symbols.VISIBILITY_OFF, res.getString(R.string.tutorial_menu_hide)) { scene.menu = false },
        ), MenuHeader(res.getString(R.string.tutorial_menu_status))) else null
        Scrim(menu != null || scene.side || scene.sideDrag.engaged, light = menu != null, haze = haze,
            progress = if (scene.sideDrag.engaged) ({ scene.sideDrag.progress }) else null,
            onDismiss = { if (lesson != Lesson.BACK) scene.menu = false; scene.side = false })
        ContextMenuLayer(menu, anchor, emptyList(), {}, haze) { if (lesson != Lesson.BACK) scene.menu = false }
        SideMenuLayer(scene.side || scene.sideDrag.engaged, haze, drag = if (scene.sideDrag.engaged) ({ scene.sideDrag.progress }) else null,
            header = { Heading(stringResource(R.string.tutorial_side_title), null) }) { first ->
            SettingRow(Symbols.PALETTE, stringResource(R.string.tutorial_side_theme), RowValue.Action { scene.side = false }, Modifier.focusRequester(first), compact = true)
            SettingRow(Symbols.SPEAKER, stringResource(R.string.tutorial_side_sounds), RowValue.Action { scene.side = false }, compact = true)
            SettingRow(Symbols.SETTINGS, stringResource(R.string.shell_nav_settings), RowValue.Action { scene.side = false }, compact = true)
        }
        TutorialHints(page, lesson, touch, skip, Modifier.align(Alignment.BottomCenter))
    }
}

/** What the scenes of one page share with the layers above them and with the keys. */
@Stable
private class SceneState(menuOpen: Boolean) {
    var menu by mutableStateOf(menuOpen)
    var side by mutableStateOf(false)
    var stream by mutableStateOf(false)
    var anchor by mutableStateOf<Rect?>(null)
    val sideDrag = SideMenuDrag()
    /** Back or Guide held since (uptime ms), and how far towards Guide (0 to 1). */
    var holdSince by mutableLongStateOf(0L)
    var hold by mutableFloatStateOf(0f)
    var guideSent by mutableStateOf(false)
    var selectTapped by mutableStateOf(false)
}

/** The tutorial's own key handling (keys.capture): true consumes the event. */
private fun handleKey(event: AndroidKey, page: Page, lesson: Lesson?, scene: SceneState, success: Boolean, complete: () -> Unit, skip: () -> Unit, next: () -> Unit, windowsBack: Boolean): Boolean {
    val up = event.action == AndroidKey.ACTION_UP && event.repeatCount == 0 && !event.isCanceled
    val down = event.action == AndroidKey.ACTION_DOWN && event.repeatCount == 0
    when (event.keyCode) {
        AndroidKey.KEYCODE_BUTTON_Y -> { if (up && page !is Page.Intro && page !is Page.End) skip(); return true }
        AndroidKey.KEYCODE_BUTTON_SELECT -> {
            if (up && !success) when (lesson) {
                Lesson.SIDE -> { scene.side = true; complete() }
                Lesson.PS_BUTTONS -> { scene.selectTapped = true; complete() }
                else -> Unit
            }
            return true
        }
        AndroidKey.KEYCODE_BUTTON_START, AndroidKey.KEYCODE_BUTTON_X -> {
            if (up && lesson == Lesson.OPTIONS && !success) { scene.menu = true; complete() }
            return true
        }
        AndroidKey.KEYCODE_BUTTON_L1, AndroidKey.KEYCODE_BUTTON_R1 -> {
            if (up && lesson == Lesson.SWITCH && !success && event.keyCode == AndroidKey.KEYCODE_BUTTON_R1) complete()
            return true
        }
        AndroidKey.KEYCODE_BACK, AndroidKey.KEYCODE_BUTTON_MODE -> {
            // The stream lessons take Back and Guide as the stream does: a tap opens the menu, a hold is Guide
            if (lesson != Lesson.STREAM && lesson != Lesson.HOLD_GUIDE) return false
            if (windowsBack && event.keyCode == AndroidKey.KEYCODE_BACK) return true
            if (down) scene.holdSince = event.downTime
            if (up) {
                val held = event.eventTime - event.downTime
                scene.holdSince = 0L
                scene.hold = 0f
                if (held < HOLD_MS && lesson == Lesson.STREAM && !success) { scene.stream = true; complete() }
            }
            return true
        }
        AndroidKey.KEYCODE_BUTTON_A, AndroidKey.KEYCODE_ENTER, AndroidKey.KEYCODE_DPAD_CENTER -> {
            if (up && page is Page.Opening) { next(); return true }
        }
    }
    return false
}

/** A page that goes on with A or a tap anywhere (the chapter cards). */
@Composable
private fun TapToContinue(onNext: () -> Unit, content: @Composable () -> Unit) {
    val focus = remember { FocusRequester() }
    InitialFocus(focus)
    Box(Modifier.fillMaxSize().focusRequester(focus).focusable().pointerInput(Unit) { detectTapGestures { onNext() } }) { content() }
}

@Composable
private fun InputPage(touch: Boolean, onPick: (Boolean) -> Unit) {
    val stage = LocalStage.current
    val colors = LocalDelosColors.current
    val entrance = rememberEntrance("input")
    val pad = remember { FocusRequester() }
    val finger = remember { FocusRequester() }
    InitialFocus(if (touch) finger else pad)
    val padConnected = ControllerGlyphs.kind == InputKind.CONTROLLER
    Column(Modifier.fillMaxSize().padding(top = 100.px), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
        TutorialKicker(stringResource(R.string.tutorial_input_kicker), Modifier.graphicsLayer { stagger(entrance(), 0f) })
        BasicText(stringResource(R.string.tutorial_input), Modifier.padding(top = 18.px).graphicsLayer { stagger(entrance(), 80f, 40f) },
            style = TextStyle(color = colors.text, fontFamily = Unbounded, fontWeight = FontWeight.Bold, fontSize = (if (stage.compact) 60 else 82).tpx))
        BasicText(stringResource(R.string.tutorial_input_note), Modifier.padding(top = 18.px).widthIn(max = 1100.px).graphicsLayer { stagger(entrance(), 160f) },
            style = TextStyle(color = colors.text2, fontFamily = Figtree, fontSize = 30.tpx, textAlign = androidx.compose.ui.text.style.TextAlign.Center))
        val cards: @Composable () -> Unit = {
            Box(Modifier.graphicsLayer { stagger(entrance(), 260f, 60f) }) {
                TutorialChoiceCard(stringResource(R.string.tutorial_controller), stringResource(R.string.tutorial_controller_note), Symbols.SPORTS_ESPORTS,
                    if (padConnected) stringResource(R.string.tutorial_controller_connected) else null, Modifier, pad) { onPick(false) }
            }
            Box(Modifier.graphicsLayer { stagger(entrance(), 360f, 60f) }) {
                TutorialChoiceCard(stringResource(R.string.tutorial_touch), stringResource(R.string.tutorial_touch_note), Symbols.TOUCH_APP, null, Modifier, finger) { onPick(true) }
            }
        }
        if (stage.compact) Column(Modifier.padding(top = 50.px), verticalArrangement = Arrangement.spacedBy(48.px)) { cards() }
        else Row(Modifier.padding(top = 70.px), horizontalArrangement = Arrangement.spacedBy(64.px)) { cards() }
    }
}

/** The lesson's element on its stage, wired to the real input. */
@Composable
private fun BoxScope.LessonScene(lesson: Lesson, scene: SceneState, success: Boolean, complete: () -> Unit, prefs: android.content.SharedPreferences, windowsBack: Boolean) {
    val stage = LocalStage.current
    val density = LocalDensity.current
    val k = stage.dpPerPx * density.density
    val scope = rememberCoroutineScope()
    val res = textResources()
    when (lesson.scene) {
        Scene.WINDOWS_BACK -> {
            val focus = remember { FocusRequester() }
            InitialFocus(focus)
            val toggle = rememberBackGameBarValue(prefs, complete)
            Column(Modifier.align(Alignment.Center).fillMaxWidth(.90f).widthIn(max = 1100.px),
                verticalArrangement = Arrangement.spacedBy(24.px)) {
                SettingRow(Symbols.DESKTOP_WINDOWS, stringResource(R.string.settings_back_game_bar_title),
                    toggle, Modifier.focusRequester(focus), description = stringResource(R.string.settings_back_game_bar_description))
                SettingRow(Symbols.CHECK_CIRCLE, stringResource(R.string.tutorial_windows_back_continue), RowValue.Action(complete))
            }
        }
        Scene.ROW, Scene.COVER -> CoverRow(lesson, scene, success, complete)
        Scene.SIDE -> {
            // A strip of covers, as on Home; the side menu itself is the shell's (above the page)
            Box(Modifier.fillMaxSize().then(if (lesson == Lesson.EDGE) Modifier.edgeSwipe(scene, complete, k) else Modifier), contentAlignment = Alignment.Center) {
                CoverStrip(dim = scene.side)
            }
        }
        Scene.SWITCH -> {
            var playStation by remember { mutableStateOf(false) }
            LaunchedEffect(success) { if (success) playStation = true }
            Box(Modifier.fillMaxWidth().height(140.px)) { PsModeSwitch(playStation, { if (it && !success) complete() }, 20f) }
        }
        Scene.NAV -> TopNav(listOf(
            NavButton(Symbols.SETTINGS, stringResource(R.string.shell_nav_settings), success) { complete() },
            NavButton(Symbols.ACCOUNT_CIRCLE, stringResource(R.string.shell_nav_profile), false) { complete() },
            NavButton(Symbols.DEVICES, stringResource(R.string.shell_nav_devices), false) { complete() },
        ), Modifier.graphicsLayer { scaleX = 1.7f; scaleY = 1.7f })
        Scene.STREAM -> TutorialGameScreen(Modifier.fillMaxWidth(.94f).aspectRatio(16f / 9f)
            .then(if (lesson == Lesson.STREAM_TOUCH) Modifier.bottomSwipe { scene.stream = true; complete() } else Modifier),
            if (scene.stream || success) "" else stringResource(R.string.tutorial_game)) {
            DockIn(scene.stream || success, Modifier.align(Alignment.BottomCenter))
        }
        Scene.HOLD -> TutorialGameScreen(Modifier.fillMaxWidth(.94f).aspectRatio(16f / 9f), stringResource(R.string.tutorial_game)) {
            // The press fills a ring; at half a second Guide reaches the PC
            LaunchedEffect(scene.holdSince) {
                val since = scene.holdSince
                if (since == 0L) return@LaunchedEffect
                while (scene.holdSince == since) {
                    val held = android.os.SystemClock.uptimeMillis() - since
                    scene.hold = (held / HOLD_MS.toFloat()).coerceIn(0f, 1f)
                    if (held >= HOLD_MS && !success) { scene.guideSent = true; complete() }
                    withFrameNanos { }
                }
            }
            HoldRing({ scene.hold }, Modifier.align(Alignment.Center), windowsBack)
            androidx.compose.animation.AnimatedVisibility(scene.guideSent || success, Modifier.align(Alignment.TopCenter).padding(top = 40.px),
                enter = fadeIn() + slideInVertically { -it }) {
                Chip(Symbols.CHECK_CIRCLE, stringResource(R.string.tutorial_guide_sent), TutorialGreen)
            }
        }
        Scene.LEAVE -> TutorialGameScreen(Modifier.fillMaxWidth(.94f).aspectRatio(16f / 9f), "") {
            var picked by remember { mutableStateOf<BarItem?>(null) }
            LeaveDock(Modifier.align(Alignment.BottomCenter)) { item ->
                picked = item
                if (item == BarItem.DISCONNECT) complete()
            }
            androidx.compose.animation.AnimatedVisibility(picked != null, Modifier.align(Alignment.TopCenter).padding(top = 34.px), enter = fadeIn() + slideInVertically { -it }) {
                if (picked == BarItem.QUIT) Chip(Symbols.POWER_SETTINGS_NEW, stringResource(R.string.tutorial_leave_quit), LocalDelosColors.current.text)
                else Chip(Symbols.CHECK_CIRCLE, stringResource(R.string.tutorial_leave_disconnect), TutorialGreen)
            }
        }
        Scene.TRACKPAD -> {
            val first = remember { FocusRequester() }
            InitialFocus(first)
            val choices = remember { res.getStringArray(R.array.mouse_mode_names).toList() }
            var mode by remember { mutableIntStateOf(0) }
            val choose: (Int) -> Unit = { mode = it; if (it == 2 || it == 3) complete() }
            Panel {
                MenuRow(Symbols.TOUCH_APP, stringResource(R.string.stream_menu_row_touchscreen_mode),
                    RowValue.Choice(choices[mode], { choose((mode + it + choices.size) % choices.size) }, options = choices, selected = mode, onPick = choose),
                    Modifier.fillMaxWidth().focusRequester(first))
            }
        }
        Scene.OSC -> {
            val first = remember { FocusRequester() }
            InitialFocus(first)
            var on by remember { mutableStateOf(false) }
            Panel {
                MenuRow(Symbols.SPORTS_ESPORTS, stringResource(R.string.stream_menu_row_onscreen_controls), RowValue.Toggle(on) { on = it; if (it) complete() },
                    Modifier.fillMaxWidth().focusRequester(first))
            }
        }
        Scene.PS_BUTTONS -> PsButtons(scene.selectTapped || success)
        Scene.PS_TOUCH -> PsTouchpad(complete)
    }
}

/** Covers at Home's look (placeholder art, the real focus ring, glow, scale and sweep), wired per lesson. */
@Composable
private fun CoverRow(lesson: Lesson, scene: SceneState, success: Boolean, complete: () -> Unit) {
    val stage = LocalStage.current
    val k = stage.dpPerPx * LocalDensity.current.density
    val scope = rememberCoroutineScope()
    val scale = .78f
    val tile = HomeCompact.TILE * scale
    val height = HomeCompact.COVER_HEIGHT * scale
    val row = rememberLazyListState()
    val focus = remember { List(COVERS.size) { FocusRequester() } }
    val start = if (lesson.scene == Scene.ROW) 0 else 1
    InitialFocus(focus[start])
    var selected by remember { mutableIntStateOf(start) }
    var launching by remember { mutableStateOf(false) }
    if (lesson == Lesson.SWIPE) LaunchedEffect(row) {
        snapshotFlow { row.firstVisibleItemIndex > 0 || row.firstVisibleItemScrollOffset > 90f * k }.first { it }
        complete()
    }
    val menu = ContextMenu("", emptyList())
    CompositionLocalProvider(LocalContextMenuOpener provides { _, _ -> if (lesson == Lesson.HOLD || lesson == Lesson.OPTIONS) { scene.menu = true; complete() } }) {
        Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
            // A lesson about one game shows three, centred; the row lessons scroll through all of them
            val gap = HomeCompact.coverGap(scale)
            val three = 3f * tile + 2f * gap + 2f * 46f
            LazyRow(
                state = row, userScrollEnabled = lesson.scene == Scene.ROW,
                contentPadding = PaddingValues(horizontal = (if (lesson.scene == Scene.ROW) 70 else 46).px, vertical = 90.px),
                horizontalArrangement = Arrangement.spacedBy(gap.px),
                modifier = if (lesson.scene == Scene.ROW) Modifier.fillMaxWidth() else Modifier.width(three.px),
            ) {
                itemsIndexed(COVERS) { i, hue ->
                    var drag by remember { mutableFloatStateOf(0f) }
                    var focused by remember { mutableStateOf(false) }
                    val motion = OverviewMotion.drag(drag, OverviewMotion.dragCap(90f, height))
                    val opens = lesson == Lesson.INFO || lesson == Lesson.PULL
                    Box(
                        Modifier.size(tile.px, height.px)
                            .onGloballyPositioned { if (i == selected) scene.anchor = it.boundsInRoot() }
                            .graphicsLayer {
                                translationY = motion.lift * k
                                if (launching && i == selected) { val s = 1.08f; scaleX = s; scaleY = s }
                            }
                            .focusRequester(focus[i])
                            .focusEffects(
                                onClick = {
                                    when (lesson) {
                                        Lesson.CHOOSE -> if (i == selected) { launching = true; complete() }
                                        Lesson.TAP -> if (i == selected) { launching = true; complete() }
                                        else -> Unit
                                    }
                                },
                                corner = 26f * scale, focusScale = HomeCompact.FOCUS_SCALE, sweep = true,
                                contextMenu = if (lesson == Lesson.HOLD || lesson == Lesson.OPTIONS) menu else null,
                                onFocusChange = {
                                    if (it) {
                                        selected = i
                                        if (lesson == Lesson.MOVE && i > 0) complete()
                                        scope.launch { row.animateScrollToItem(maxOf(0, i - 1)) }
                                    }
                                },
                                onLookChange = { focused = it },
                                // A finger: the first tap selects, a tap on the selected game starts it
                                onTouchTap = {
                                    if (lesson == Lesson.TAP) { if (i == selected) { launching = true; complete() } else selected = i; true } else false
                                },
                            )
                            .onPreviewKeyEvent { event ->
                                if (opens && event.type == KeyEventType.KeyUp && event.key == Key.DirectionDown) { if (!success) complete(); true }
                                else if (opens && event.key == Key.DirectionDown) true else false
                            }
                            .pointerInput(lesson) {
                                if (lesson == Lesson.PULL) detectVerticalDragGestures(
                                    onDragEnd = { if (drag <= -OverviewMotion.DRAG_OPENS && !success) complete(); drag = 0f },
                                    onDragCancel = { drag = 0f },
                                    onVerticalDrag = { change, amount -> change.consume(); drag = (drag + amount / k).coerceAtMost(0f) },
                                )
                            }
                            .clip(RoundedCornerShape((26f * scale).px)),
                    ) {
                        PlaceholderArt(hue, hue + 40f, Symbols.SPORTS_ESPORTS, Modifier.fillMaxSize(), title = stringResource(R.string.tutorial_game))
                        if (focused && opens && !success) GameInfoHint(true, { (-drag / OverviewMotion.DRAG_OPENS).coerceIn(0f, 1f) }, { complete() }, Modifier.align(Alignment.BottomCenter))
                    }
                }
            }
            // Game info: the overview's opening lines rise over the row
            if (lesson == Lesson.INFO || lesson == Lesson.PULL) androidx.compose.animation.AnimatedVisibility(success,
                enter = fadeIn(tween(300)) + slideInVertically(tween(520, easing = EaseOut)) { it / 3 }) { InfoCard() }
            if (launching) androidx.compose.animation.AnimatedVisibility(true, Modifier.align(Alignment.TopCenter).padding(top = 20.px), enter = fadeIn()) {
                Chip(Symbols.PLAY_ARROW, stringResource(R.string.tutorial_starting), LocalDelosColors.current.accent)
            }
        }
    }
}

private val COVERS = listOf(205f, 265f, 320f, 20f, 150f)

@Composable
private fun CoverStrip(dim: Boolean) {
    val scale = .62f
    Row(Modifier.graphicsLayer { alpha = if (dim) .5f else 1f }, horizontalArrangement = Arrangement.spacedBy(30.px)) {
        COVERS.take(4).forEach { hue ->
            Box(Modifier.size((HomeCompact.TILE * scale).px, (HomeCompact.COVER_HEIGHT * scale).px).clip(RoundedCornerShape(18.px))) {
                PlaceholderArt(hue, hue + 40f, Symbols.SPORTS_ESPORTS, Modifier.fillMaxSize(), title = stringResource(R.string.tutorial_game))
            }
        }
    }
}

/** The overview's head in small: eyebrow, the title, playtime and two tags. */
@Composable
private fun InfoCard() {
    val colors = LocalDelosColors.current
    val shape = RoundedCornerShape(30.px)
    Column(Modifier.width(640.px).clip(shape).glass(colors.surface2).border(1.px, colors.line, shape).padding(36.px), verticalArrangement = Arrangement.spacedBy(18.px)) {
        OverviewEyebrow(stringResource(R.string.tutorial_info_title).uppercase(), colors.accent)
        BasicText(stringResource(R.string.tutorial_game), style = TextStyle(color = colors.text, fontFamily = Unbounded, fontWeight = FontWeight.Bold, fontSize = 44.tpx))
        BasicText(stringResource(R.string.tutorial_info_played), style = TextStyle(color = colors.text2, fontFamily = Figtree, fontSize = 26.tpx))
        Row(horizontalArrangement = Arrangement.spacedBy(12.px)) {
            OverviewTag(Symbols.SPORTS_ESPORTS, stringResource(R.string.tutorial_info_tag_adventure))
            OverviewTag(Symbols.IMAGE, stringResource(R.string.tutorial_info_tag_media))
        }
    }
}

@Composable
private fun Chip(icon: String, text: String, tint: Color) {
    val colors = LocalDelosColors.current
    val shape = RoundedCornerShape(40.px)
    Row(Modifier.clip(shape).glass(colors.surface2).border(1.px, tint.copy(alpha = .5f), shape).padding(horizontal = 26.px, vertical = 14.px),
        horizontalArrangement = Arrangement.spacedBy(12.px), verticalAlignment = Alignment.CenterVertically) {
        Symbol(icon, 32.tpx, tint, filled = true)
        BasicText(text, style = TextStyle(color = colors.text, fontFamily = Figtree, fontWeight = FontWeight.SemiBold, fontSize = 24.tpx))
    }
}

@Composable
private fun Panel(content: @Composable ColumnScope.() -> Unit) {
    val colors = LocalDelosColors.current
    val shape = RoundedCornerShape(30.px)
    Column(Modifier.width(820.px).clip(shape).glass(colors.surface2).border(1.px, colors.line, shape).padding(24.px), content = content)
}

/** The ring that fills while Back or Guide is held, around the button. */
@Composable
private fun HoldRing(hold: () -> Float, modifier: Modifier, guideOnly: Boolean) {
    val colors = LocalDelosColors.current
    val stage = LocalStage.current
    Box(modifier.size(170.px), contentAlignment = Alignment.Center) {
        Canvas(Modifier.fillMaxSize()) {
            val w = stagePx(8f, stage)
            drawCircle(Color.Black.copy(alpha = .35f))
            drawCircle(Color.White.copy(alpha = .25f), style = Stroke(w), radius = size.minDimension / 2f - w)
            val p = hold()
            if (p > 0f) drawArc(colors.accent, -90f, 360f * p, false, topLeft = Offset(w, w), size = androidx.compose.ui.geometry.Size(size.width - 2 * w, size.height - 2 * w), style = Stroke(w))
        }
        Row(horizontalArrangement = Arrangement.spacedBy(6.px), verticalAlignment = Alignment.CenterVertically) {
            if (!guideOnly) GlyphButton(BACK_BUTTON, 44)
            GlyphButton(GUIDE, 44)
        }
    }
}

/** The stream's dock rising from the bottom of the game, as a tap on Back or Guide brings it. */
@Composable
private fun DockIn(visible: Boolean, modifier: Modifier) {
    androidx.compose.animation.AnimatedVisibility(visible, modifier.fillMaxWidth().padding(bottom = 18.px),
        enter = fadeIn(tween(260)) + slideInVertically(tween(560, easing = EaseOut)) { it / 2 }) {
        ScaledDock(focus = false, onPick = {})
    }
}

/** The dock with Disconnect and Quit to choose from; the focus starts on Disconnect. */
@Composable
private fun LeaveDock(modifier: Modifier, onPick: (BarItem) -> Unit) {
    Box(modifier.fillMaxWidth().padding(bottom = 18.px)) { ScaledDock(focus = true, start = BarItem.DISCONNECT, onPick = onPick) }
}

/** The stream's real dock, as wide as its tiles, scaled to fill the game frame's width and standing on its bottom edge. */
@Composable
private fun ScaledDock(focus: Boolean, start: BarItem = BarItem.RESUME, onPick: (BarItem) -> Unit) {
    val stage = LocalStage.current
    val items = BarItem.entries
    val geometry = remember(stage) {
        val tiles = DockGeometry(stage.stageWidth, 48f, false, stage.textScale, stage.stageHeight, items).tilesWidth
        DockGeometry(tiles + 2f * 56f, 48f, false, stage.textScale, stage.stageHeight, items)
    }
    val requesters = remember { items.associateWith { FocusRequester() } }
    var focused by remember { mutableStateOf(start) }
    val labels = listOf(R.string.stream_menu_tile_resume, R.string.stream_menu_tile_keyboard, R.string.stream_menu_tile_overlay, R.string.stream_menu_tile_bitrate,
        R.string.stream_menu_tile_upscaler, R.string.stream_menu_tile_frame_gen, R.string.stream_menu_tile_more, R.string.stream_menu_tile_disconnect, R.string.stream_menu_tile_quit)
    val icons = listOf(Symbols.RESUME, Symbols.KEYBOARD, Symbols.OVERLAY, Symbols.NETWORK_CHECK, Symbols.UPSCALER, Symbols.FRAME_GENERATION, Symbols.MORE_HORIZ,
        Symbols.LOGOUT, Symbols.POWER_SETTINGS_NEW)
    val circles = items.mapIndexed { i, item -> BarCircleSpec(item, icons[i], stringResource(labels[i])) { onPick(item) } }
    if (focus) InitialFocus(requesters.getValue(start))
    CompositionLocalProvider(LocalDockGeometry provides geometry) {
        androidx.compose.ui.layout.Layout({
            StreamMenuDock(stringResource(R.string.tutorial_game), circles, requesters, if (focus) focused else null, BarItem.RESUME, false,
                circles.first { it.item == focused }.label, { 2000f }, { 0f }, { 0f },
                { item, has -> if (has) focused = item }, rememberScrollState(), false, {}, {})
        }, Modifier.fillMaxWidth()) { measurables, constraints ->
            // Measured at its own size, laid out scaled down to the frame's width
            val dock = measurables.first().measure(androidx.compose.ui.unit.Constraints())
            val s = (constraints.maxWidth.toFloat() / dock.width).coerceAtMost(1f)
            val h = (dock.height * s).toInt()
            layout(constraints.maxWidth, h) {
                // The dock's own box keeps room under its tiles for the stream's hints: drop it by that much, so the tiles sit low in the frame
                dock.placeWithLayer((constraints.maxWidth - dock.width) / 2, (h - dock.height) / 2 + (h * .22f).toInt()) { scaleX = s; scaleY = s }
            }
        }
    }
}

/** A handheld's buttons on a PlayStation stream: Select (tap: touchpad, hold: Create) and Back (hold: PS). */
@Composable
private fun PsButtons(tapped: Boolean) {
    val colors = LocalDelosColors.current
    Column(verticalArrangement = Arrangement.spacedBy(26.px)) {
        PsButtonRow(Glyph.MENU_LEFT, stringResource(R.string.tutorial_ps_select_tap), Symbols.PAD_DUALSENSE_TOUCHPAD, tapped)
        PsButtonRow(Glyph.MENU_LEFT, stringResource(R.string.tutorial_ps_select_hold), Symbols.PAD_DUALSENSE_CREATE, false)
        PsButtonRow(BACK_BUTTON, stringResource(R.string.tutorial_ps_back_hold), Symbols.PAD_DUALSENSE_PS, false)
    }
}

@Composable
private fun PsButtonRow(glyph: Glyph, text: String, result: String, lit: Boolean) {
    val colors = LocalDelosColors.current
    val shape = RoundedCornerShape(26.px)
    val tint = if (lit) TutorialGreen else colors.line
    Row(Modifier.width(780.px).clip(shape).glass(colors.surface2).border(1.5.px, tint, shape).padding(horizontal = 30.px, vertical = 22.px),
        horizontalArrangement = Arrangement.spacedBy(22.px), verticalAlignment = Alignment.CenterVertically) {
        GlyphButton(glyph, 46)
        BasicText(text, Modifier.weight(1f), style = TextStyle(color = colors.text, fontFamily = Figtree, fontWeight = FontWeight.Medium, fontSize = 27.tpx))
        Symbol(Symbols.ARROW_FORWARD, 30.tpx, colors.text3)
        Symbol(result, 54.tpx, if (lit) TutorialGreen else colors.accent)
    }
}

/** The DualSense touchpad: the screen of a PlayStation stream; fingers leave glowing trails, a tap completes the lesson. */
@Composable
private fun PsTouchpad(complete: () -> Unit) {
    val colors = LocalDelosColors.current
    val stage = LocalStage.current
    val trail = remember { mutableStateListOf<Offset>() }
    val shape = RoundedCornerShape(40.px)
    Box(Modifier.width(800.px).height(440.px).clip(shape).glass(colors.surface2).border(2.px, colors.accent.copy(alpha = .4f), shape)
        .pointerInput(Unit) {
            awaitEachGesture {
                val down = awaitFirstDown()
                trail.clear(); trail.add(down.position)
                do {
                    val event = awaitPointerEvent()
                    event.changes.forEach { if (it.pressed) { trail.add(it.position); if (trail.size > 40) trail.removeAt(0) } }
                } while (event.changes.any { it.pressed })
                complete()
            }
        }) {
        Canvas(Modifier.fillMaxSize()) {
            trail.forEachIndexed { i, p ->
                val a = (i + 1f) / trail.size
                drawCircle(colors.accent.copy(alpha = .5f * a), radius = stagePx(10f + 18f * a, stage), center = p)
            }
        }
        Symbol(Symbols.PAD_DUALSENSE_TOUCHPAD, 120.tpx, colors.text.copy(alpha = .25f), modifier = Modifier.align(Alignment.Center))
    }
}

/** The left-edge swipe of Home (the shell's band and thresholds), opening the tutorial's side menu. */
private fun Modifier.edgeSwipe(scene: SceneState, complete: () -> Unit, k: Float): Modifier = this.sideMenuSwipe(
    canOpen = { true }, isOpen = { scene.side },
    onStart = { scene.sideDrag.engaged = true },
    onMove = { _, travel -> scene.sideDrag.progress = SideMenuSwipe.progress(0f, travel, 680f * k) },
    onEnd = { _, travel, _ ->
        val open = SideMenuSwipe.progress(0f, travel, 680f * k) > .4f
        if (open) { scene.side = true; complete() }
        scene.sideDrag.engaged = false
        scene.sideDrag.progress = 0f
    },
)

/** A swipe up from the bottom band of the frame, as the stream's own (StreamMenu: up more than sideways, from the bottom fifth). */
private fun Modifier.bottomSwipe(onOpen: () -> Unit): Modifier = pointerInput(Unit) {
    awaitEachGesture {
        val down = awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial)
        if (down.position.y < size.height * .72f) return@awaitEachGesture
        var end = down.position
        do {
            val event = awaitPointerEvent(PointerEventPass.Initial)
            event.changes.firstOrNull { it.id == down.id }?.let { end = it.position }
        } while (event.changes.any { it.pressed })
        val dy = down.position.y - end.y
        if (dy > size.height * .12f && dy > kotlin.math.abs(end.x - down.position.x)) onOpen()
    }
}

/** The hint bar of a page: the lesson's own button and Skip (Y; a tap for a finger). */
@Composable
private fun TutorialHints(page: Page, lesson: Lesson?, touch: Boolean, skip: () -> Unit, modifier: Modifier) {
    val hints = when (page) {
        Page.Intro, Page.End -> return
        Page.Input -> listOf(Hint(Glyph.A, stringResource(R.string.shell_hint_select)), Hint(Glyph.Y, stringResource(R.string.tutorial_skip_all), tappable = true))
        is Page.Opening -> listOf(Hint(Glyph.A, stringResource(R.string.tutorial_continue)), Hint(Glyph.Y, stringResource(R.string.tutorial_skip), tappable = true))
        is Page.Learn -> listOfNotNull(
            lesson?.takeIf { !touch }?.let { Hint(it.glyphs.first(), stringResource(it.action)) },
            Hint(Glyph.Y, stringResource(R.string.tutorial_skip), tappable = true),
        )
    }.adaptedTo(ControllerGlyphs.kind)
    HintBar(hints, null, modifier.padding(24.px), onTap = { skip() })
}

/** The end: a ring drawn round a check, sparks, "Pair your first device" and Let's start (a replay: "That's everything" and Done). */
@Composable
private fun EndPage(key: Any, replay: Boolean, onDone: () -> Unit) {
    val colors = LocalDelosColors.current
    val stage = LocalStage.current
    val entrance = rememberEntrance(key)
    val focus = remember { FocusRequester() }
    InitialFocus(focus)
    Column(Modifier.fillMaxSize(), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
        Canvas(Modifier.size(200.px)) {
            val p = EaseOut.transform((entrance() / .7f).coerceIn(0f, 1f))
            val w = stagePx(8f, stage)
            drawCircle(Brush.radialGradient(listOf(TutorialGreen.copy(alpha = .3f * p), Color.Transparent)), radius = size.minDimension * .9f)
            drawArc(TutorialGreen, -90f, 360f * p, false, topLeft = Offset(w, w), size = androidx.compose.ui.geometry.Size(size.width - 2 * w, size.height - 2 * w), style = Stroke(w, cap = androidx.compose.ui.graphics.StrokeCap.Round))
            val c = EaseOut.transform(((entrance() - .45f) / .35f).coerceIn(0f, 1f))
            if (c > 0f) {
                val a = Offset(size.width * .3f, size.height * .52f)
                val b = Offset(size.width * .45f, size.height * .66f)
                val d = Offset(size.width * .72f, size.height * .36f)
                val first = (c * 2f).coerceAtMost(1f)
                drawLine(TutorialGreen, a, a + (b - a) * first, w, cap = androidx.compose.ui.graphics.StrokeCap.Round)
                if (c > .5f) drawLine(TutorialGreen, b, b + (d - b) * ((c - .5f) * 2f), w, cap = androidx.compose.ui.graphics.StrokeCap.Round)
            }
            repeat(18) { i ->
                val q = ((entrance() - .5f) / .5f).coerceIn(0f, 1f)
                if (q > 0f && q < 1f) {
                    val ang = i / 18f * 2f * Math.PI.toFloat()
                    val r = size.minDimension * (.55f + .6f * q)
                    drawCircle((if (i % 2 == 0) colors.accent else TutorialGreen).copy(alpha = 1f - q), radius = w * (1f - q) + 2f,
                        center = center + Offset(kotlin.math.cos(ang) * r, kotlin.math.sin(ang) * r))
                }
            }
        }
        TutorialKicker(stringResource(if (replay) R.string.tutorial_end_replay_kicker else R.string.tutorial_end_kicker), Modifier.padding(top = 40.px).graphicsLayer { stagger(entrance(), 400f) }, TutorialGreen)
        BasicText(stringResource(if (replay) R.string.tutorial_end_replay else R.string.tutorial_end), Modifier.padding(top = 20.px).graphicsLayer { stagger(entrance(), 480f, 40f) },
            style = TextStyle(color = colors.text, fontFamily = Unbounded, fontWeight = FontWeight.Bold, fontSize = (if (stage.compact) 60 else 84).tpx, textAlign = androidx.compose.ui.text.style.TextAlign.Center))
        BasicText(stringResource(if (replay) R.string.tutorial_end_replay_note else R.string.tutorial_end_note), Modifier.padding(top = 22.px).widthIn(max = 1100.px).graphicsLayer { stagger(entrance(), 560f) },
            style = TextStyle(color = colors.text2, fontFamily = Figtree, fontSize = 30.tpx, textAlign = androidx.compose.ui.text.style.TextAlign.Center))
        Box(Modifier.padding(top = 60.px).graphicsLayer { stagger(entrance(), 680f, 30f) }) {
            TutorialStartButton(stringResource(if (replay) R.string.shell_hint_done else R.string.tutorial_start), if (replay) Symbols.CHECK else Symbols.PLAY_ARROW, onDone, focus)
        }
    }
}
