package com.limelight.shell.screens

import android.content.Intent
import android.net.Uri
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.snap
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.BringIntoViewSpec
import androidx.compose.foundation.gestures.LocalBringIntoViewSpec
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.draw.dropShadow
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.ColorMatrix
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.zIndex
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.limelight.shell.ui.animateFocusScale
import com.limelight.shell.ui.FocusRow
import com.limelight.shell.ui.rememberPressPulse
import com.limelight.shell.ui.pressInput
import com.limelight.shell.ui.PointerLook
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.LineBreak
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Velocity
import androidx.compose.ui.unit.em
import com.limelight.R
import com.limelight.shell.LocalShell
import com.limelight.shell.data.App
import com.limelight.shell.data.GameDetails
import com.limelight.shell.data.IgdbImageSize
import com.limelight.shell.data.IgdbImageUrls
import com.limelight.shell.data.LastStreamed
import com.limelight.shell.data.LocalGameMetadata
import com.limelight.shell.data.NativeHdr
import com.limelight.shell.data.Pc
import com.limelight.shell.data.Playtime
import com.limelight.shell.data.PlaytimeFormat
import com.limelight.shell.data.YouTubeEmbeds
import com.limelight.shell.data.matchKey
import com.limelight.shell.data.rememberIgdbImage
import com.limelight.shell.ui.ControllerGlyphs
import com.limelight.shell.ui.Cue
import com.limelight.shell.ui.EaseOut
import com.limelight.shell.ui.Figtree
import com.limelight.shell.ui.GameArt
import com.limelight.shell.ui.HeroArea
import com.limelight.shell.ui.HeroBitmaps
import com.limelight.shell.ui.InputKind
import com.limelight.shell.ui.LocalBackdropHaze
import com.limelight.shell.ui.LocalDelosColors
import com.limelight.shell.ui.LocalFeedback
import com.limelight.shell.ui.LocalStage
import com.limelight.shell.ui.Motion
import com.limelight.shell.ui.backdropBlur
import com.limelight.shell.ui.glass
import com.limelight.shell.ui.PlaceholderArt
import com.limelight.shell.ui.Symbol
import com.limelight.shell.ui.Symbols
import com.limelight.shell.ui.Unbounded
import com.limelight.shell.ui.focusEffects
import com.limelight.shell.ui.genreIcon
import com.limelight.shell.ui.genreName
import com.limelight.shell.ui.hsl
import com.limelight.shell.ui.px
import com.limelight.shell.ui.stageUnit
import com.limelight.shell.ui.textResources
import com.limelight.shell.ui.themeIcon
import com.limelight.shell.ui.themeName
import com.limelight.shell.ui.topGenres
import com.limelight.shell.ui.topThemes
import com.limelight.shell.ui.tpx
import dev.chrisbanes.haze.HazeState
import dev.chrisbanes.haze.hazeSource
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.DateFormat
import java.text.NumberFormat
import java.util.Date
import java.util.TimeZone
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Outline
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.layer.GraphicsLayer
import androidx.compose.ui.graphics.layer.drawLayer
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection
import com.limelight.shell.data.ArtKind
import com.limelight.shell.data.LocalArtwork
import com.limelight.shell.data.rememberAnimatedArtwork
import com.limelight.shell.data.rememberArtworkResult
import com.limelight.shell.ui.AnimatedArtLayer
import com.limelight.shell.ui.LocalBackdropMirror
import com.limelight.shell.ui.drawBackdropMirror
import com.limelight.shell.ui.anchor
import com.limelight.shell.ui.anchorRowFade
import com.limelight.shell.ui.overdraw
import androidx.compose.ui.unit.dp

/** The page's sides (`.a-page`: padding 0 150px), the header's top and its column gap, the cover's size, in stage pixels. */
private const val SIDE = 150f
private const val HEAD_TOP = 150f
private const val COVER_W = 340f
private const val COVER_H = 510f
private const val COVER_GAP = 72f
private const val TITLE_SIZE = 68f

/** The header's logo box: as tall as two lines of the title, and of the dashboard's logo box proportions (900 × 180), so it flies at one scale. */
private const val LOGO_H = 140f
private const val LOGO_W = 700f
private const val SECTION_GAP = 70f

/** Section ids of [OverviewFocus] (the header and the [OverviewModel.Section]s). */
private const val HEAD = "head"

/** Lazy rows of the overview do not scroll by themselves when the focus moves: the overview reveals the focused item itself. */
@OptIn(ExperimentalFoundationApi::class)
private object OverviewNoAutoScroll : BringIntoViewSpec {
    override fun calculateScrollDistance(offset: Float, size: Float, containerSize: Float) = 0f
}

/** What a part does when it takes the focus: [header], its page section and whether it sits in a carousel. */
private class PartFocus(val section: String, val track: LazyListState? = null, val index: Int = 0)

/**
 * The game overview, variant A · Descent (docs/gui/GAME_INFO.md, "Game overview"; issue #107): one vertical page that continues
 * the dashboard below it. The card's cover rises into the header beside the title, the year and developer, the tags, the critics'
 * and players' rings, Play and App settings and the streaming stats; About, Screenshots and videos, Details, Your streaming, Links and
 * Similar games follow. The hero behind it is the dashboard's, deepening its blur as the page scrolls.
 *
 * [controller] runs the motion; [cover] is exactly the library card's image (the user's SteamGridDB choice,
 * else the default card art); [card] is the focused card as the row draws it, which the flying cover crossfades with; [library] is
 * every app of the PC, for similar games the user owns. [onLeaving] runs as the closing motion begins; the overview leaves composition
 * once the card is back in its row.
 */
@OptIn(ExperimentalLayoutApi::class, ExperimentalFoundationApi::class)
@Composable
internal fun GameOverview(
    controller: OverviewController,
    pc: Pc,
    app: App,
    library: List<App>,
    cover: ImageBitmap?,
    art: GameArt,
    card: GraphicsLayer?,
    playtime: Playtime,
    resumes: Int,
    reducedMotion: Boolean,
    /** The dashboard hero's blur (Appearance): the overview's deepening starts there and never ends sharper. */
    dashboardBlur: Int,
    onPlay: () -> Unit,
    onSettings: () -> Unit,
    onJump: (App) -> Unit,
    onLeaving: () -> Unit,
    coverArt: @Composable (App, Modifier) -> Unit,
) {
    val shell = LocalShell.current
    val colors = LocalDelosColors.current
    val stage = LocalStage.current
    val res = textResources()
    val feedback = LocalFeedback.current
    val metadataStore = LocalGameMetadata.current
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val k = stageUnit()
    val entry = metadataStore.entry(pc, app)
    val data = entry.data
    val details = data?.details
    val candidate = data?.candidate
    LaunchedEffect(pc.uuid, app.id) { metadataStore.ensureDetails(pc, app) }

    val focus = remember(app.id) { OverviewFocus(scope) }
    val scroll = rememberScrollState()
    val track = rememberLazyListState()
    val similarRow = rememberLazyListState()
    val pageHaze = remember { HazeState() }
    // The shell's background haze: this page is a source over the background in it (z 1), so the top bar and the screenshot viewer,
    // which float over the page, blur what lies under them now (the background, the page as it scrolls and moves), not a picture
    // of the dashboard
    val ownHaze = remember { HazeState() }
    val viewerHaze = LocalBackdropHaze.current ?: ownHaze
    var viewer by remember { mutableStateOf<ViewerRequest?>(null) }
    val viewerInput = remember { OverviewViewerInput() }
    DisposableEffect(viewer != null) {
        shell.overviewViewerInput = if (viewer != null) viewerInput::onInput else null
        if (viewer == null) shell.overviewViewerControlsVisible = true
        onDispose {
            shell.overviewViewerInput = null
            shell.overviewViewerControlsVisible = true
        }
    }
    var storyOpen by remember(app.id) { mutableStateOf(false) }
    var hovered by remember { mutableStateOf(false) }
    var root by remember { mutableStateOf<LayoutCoordinates?>(null) }
    val kind = ControllerGlyphs.kind
    val reduced by rememberUpdatedState(reducedMotion)

    val screenshotSize = if (stage.stageHeight > 1200f) IgdbImageSize.HD_1080 else IgdbImageSize.SCREENSHOT_HUGE
    // Videos play only in the app: one YouTube will not embed is left out (YouTubeEmbeds), the moment that is known
    val allMedia = remember(details, screenshotSize) { details?.let { OverviewModel.media(it, screenshotSize) }.orEmpty() }
    LaunchedEffect(allMedia) { YouTubeEmbeds.check(allMedia.filter { it.kind == OverviewModel.MediaKind.VIDEO }.map { it.id }) }
    val media by remember(allMedia) { derivedStateOf { allMedia.filter { it.kind != OverviewModel.MediaKind.VIDEO || YouTubeEmbeds.playable(it.id) } } }
    val links = remember(details, candidate) { details?.let { OverviewModel.links(it, candidate?.steamAppIds.orEmpty()) }.orEmpty() }
    val sections = remember(details, links, media) { OverviewModel.sections(details, links, media) }

    // The sections below the header compose once the opening has landed (until then only the top of the first one would peek in at
    // the bottom edge), so the flight's frames carry only the header; an overview shown again at once has them from its first frame.
    // They fade in.
    var sectionsReady by remember(app.id) { mutableStateOf(!controller.opening) }
    val sectionsIn = remember(app.id) { Animatable(if (sectionsReady) 1f else 0f) }
    LaunchedEffect(app.id) {
        if (sectionsReady) return@LaunchedEffect
        snapshotFlow { controller.settled }.first { it }
        sectionsReady = true
        sectionsIn.animateTo(1f, tween(Motion.FADE, easing = Motion.Ease))
    }

    // Closing: the page scrolls back to its top first, then every motion reverses. The card takes the focus back as the motion
    // begins: its focus look (scale, ring) settles while it is still hidden, so it is in place when the cover lands on it
    val close: () -> Unit = {
        if (!controller.leaving) {
            feedback.play(Cue.BACK)
            focus.alpha.let { a -> scope.launch { a.animateTo(0f, tween(200)) } }
            controller.close {
                onLeaving()
                if (scroll.value > 1) scroll.animateScrollTo(0, tween(if (reduced) 0 else 420, easing = OverviewMotion.InOut))
            }
        }
    }
    val currentClose by rememberUpdatedState(close)
    // Back while the overview is already leaving is swallowed: it must not reach the dashboard (and leave the app)
    BackHandler(enabled = controller.app != null) {
        if (viewer != null) viewer = viewer?.copy(closing = true) else currentClose()
    }
    // The viewer keeps the items it opened with, so its indices hold while a video drops out of the carousel; these map them back
    val viewerMedia = remember(viewer != null) { media }
    fun carouselIndex(index: Int): Int = viewerMedia.getOrNull(index)?.let { media.indexOf(it) } ?: -1

    // The ring shows once the overview has settled, for a finger too: a first tap only focuses (focus first), and the ring shows it did
    LaunchedEffect(controller.settled) {
        focus.alpha.animateTo(if (controller.settled) 1f else 0f, tween(200, easing = Motion.Ease))
    }

    // The rings fill on their springs and count up 285 ms after the opening began (or as soon as the details arrive later): this is
    // their clock in milliseconds, read while drawing ([OverviewMotion.ring])
    val ratings = remember(app.id) { Animatable(0f) }
    LaunchedEffect(details != null) {
        if (details == null || ratings.value > 0f) return@LaunchedEffect
        snapshotFlow { controller.openMillis() >= OverviewMotion.RATINGS_DELAY }.first { it }
        val total = if (reduced) Motion.FADE else OverviewMotion.RATINGS_TOTAL
        ratings.animateTo(total.toFloat(), tween(total, easing = LinearEasing))
    }
    // Your streaming counts up when its card is first focused
    val streaming = remember(app.id) { Animatable(if (reducedMotion) 1f else 0f) }

    // The parts take the focus: the page scrolls to the section, a carousel to its item, the backdrop follows the screenshots
    val parts = remember { HashMap<String, PartFocus>() }
    val onPartFocused: (String) -> Unit = { id ->
        val part = parts[id]
        val hover = hovered
        hovered = false
        focus.focus(id, glide = !reduced, k = k)
        if (part != null && !hover && ControllerGlyphs.kind != InputKind.POINTER && ControllerGlyphs.kind != InputKind.TOUCH) {
            scope.launch { revealPart(focus, id, part, scroll, k, reduced) }
        }
        if (part?.section == OverviewModel.Section.STREAMING.name && streaming.value < 1f) {
            scope.launch { delay(200); streaming.animateTo(1f, tween(OverviewMotion.COUNT_UP, easing = LinearEasing)) }
        }
    }

    // Play takes the focus from the overview's first frame
    val playRequester = remember { FocusRequester() }
    LaunchedEffect(app.id) {
        withFrameNanos { }
        feedback.silenceNextFocus()
        runCatching { playRequester.requestFocus() }
    }

    // Pulling the page down at its top follows the finger; far enough closes
    var pull by remember { mutableStateOf(0f) }
    val pullConnection = remember {
        object : NestedScrollConnection {
            override fun onPreScroll(available: Offset, source: NestedScrollSource): Offset {
                if (pull > 0f && available.y < 0f) {
                    val used = max(available.y, -pull)
                    pull += used
                    return Offset(0f, used)
                }
                return Offset.Zero
            }

            override fun onPostScroll(consumed: Offset, available: Offset, source: NestedScrollSource): Offset {
                if (source == NestedScrollSource.UserInput && available.y > 0f && scroll.value == 0) {
                    pull += available.y
                    return Offset(0f, available.y)
                }
                return Offset.Zero
            }

            override suspend fun onPreFling(available: Velocity): Velocity {
                if (pull <= 0f) return Velocity.Zero
                if (pull / k > OverviewMotion.PULL_CLOSES) currentClose()
                else Animatable(pull).animateTo(0f, tween(500, easing = com.limelight.shell.ui.EaseSpring)) { pull = value }
                if (!controller.leaving) pull = 0f
                return available
            }
        }
    }

    val pageMotion: () -> Pair<Float, Float> = {
        if (controller.closing) OverviewMotion.page(
            controller.clock.value, opening = false, reduced = controller.reduced,
            from = OverviewMotion.PAGE_RISE * (1f - OverviewMotion.settle(controller.interruptedAt)),
        )
        else if (controller.opening) OverviewMotion.page(controller.clock.value, opening = true, reduced = controller.reduced)
        else 0f to 1f
    }
    val playVisible by remember {
        derivedStateOf {
            scroll.value // Re-evaluate the measured visual bounds as the page scrolls.
            val r = root?.takeIf { it.isAttached }
            val c = focus.targets["play"]?.coordinates?.takeIf { it.isAttached }
            if (r == null || c == null) false else {
                val bounds = r.localBoundingBoxOf(c, clipBounds = false)
                bounds.bottom > 140f * k && bounds.top < r.size.height - 60f * k
            }
        }
    }

    Box(
        Modifier
            .fillMaxSize()
            .onGloballyPositioned { root = it }
            .onPreviewKeyEvent { event ->
                if (viewer != null) return@onPreviewKeyEvent false
                val direction = when (event.key) {
                    Key.DirectionUp -> NavDirection.UP
                    Key.DirectionDown -> NavDirection.DOWN
                    Key.DirectionLeft -> NavDirection.LEFT
                    Key.DirectionRight -> NavDirection.RIGHT
                    else -> return@onPreviewKeyEvent false
                }
                if (event.type != KeyEventType.KeyDown) return@onPreviewKeyEvent true
                val from = focus.focused ?: return@onPreviewKeyEvent true
                val r = root ?: return@onPreviewKeyEvent true
                val part = parts[from]
                val row = part?.track
                if (!direction.vertical && row != null) {
                    val nextIndex = part.index + if (direction == NavDirection.RIGHT) 1 else -1
                    val count = if (row === track) media.size else details?.similarGames?.size ?: 0
                    if (nextIndex in 0 until count) {
                        val nextId = if (row === track) "shot$nextIndex" else "similar$nextIndex"
                        val destination = focus.targets[nextId]
                        if (destination != null) runCatching { destination.requester.requestFocus() }
                        else scope.launch {
                            row.anchor(nextIndex, reduced)
                            withFrameNanos { }
                            runCatching { focus.targets[nextId]?.requester?.requestFocus() }
                        }
                    }
                    return@onPreviewKeyEvent true
                }
                val next = focus.navigator.move(from, direction, focus.navTargets(r))
                if (next != null) runCatching { focus.targets[next]?.requester?.requestFocus() }
                // Up from the header goes back to the dashboard, the natural reverse of Down
                else if (direction == NavDirection.UP && parts[from]?.section == HEAD) currentClose()
                true
            },
    ) {
        Box(Modifier.fillMaxSize().hazeSource(viewerHaze, zIndex = 1f)) {
            OverviewBackdrop(controller, shell.hero, scroll::value, reducedMotion, pageHaze, maxOf(DEEP_BLUR, dashboardBlur))
            CompositionLocalProvider(LocalOverviewFocus provides focus, LocalBackdropHaze provides pageHaze, LocalBringIntoViewSpec provides OverviewNoAutoScroll) {
                Box(
                    Modifier
                        .fillMaxSize()
                        // The page fades in under the status bar (`.a-view` mask: clear to 70 px, solid from 140 px, clear again at the
                        // bottom). The mask needs an offscreen copy of the page: while the sections are not there yet (the opening),
                        // nothing of the page reaches the masked bands (the header starts 150 px down), so the flight draws without it
                        .graphicsLayer { compositingStrategy = if (sectionsReady) CompositingStrategy.Offscreen else CompositingStrategy.Auto }
                        .drawWithCache {
                            val h = size.height
                            val mask = Brush.verticalGradient(
                                0f to Color.Transparent, 70f * k / h to Color.Transparent, 140f * k / h to Color.Black,
                                1f - 60f * k / h to Color.Black, 1f to Color.Transparent,
                            )
                            onDrawWithContent {
                                drawContent()
                                if (sectionsReady) drawRect(mask, blendMode = BlendMode.DstIn)
                            }
                        }
                        .nestedScroll(pullConnection)
                        .verticalScroll(scroll),
                ) {
                    // The ring draws in a layer of its own over the page: its glide and the carousels scrolling under it redraw
                    // only that layer, never re-record the whole page
                    Box(Modifier.fillMaxWidth().graphicsLayer { translationY = pull * .45f }) {
                    Column(
                        Modifier
                            .fillMaxWidth()
                            .onGloballyPositioned { focus.host = it },
                    ) {
                        Header(
                            controller, pc, app, details, data?.hdr?.support, data?.completion?.seconds, candidate,
                            art, cover, card, playtime, resumes, sections, ratings::value, pageMotion, playRequester,
                            onPlay, onSettings, coverArt, pull = { pull },
                            register = { id -> parts[id] = PartFocus(HEAD) }, onFocused = onPartFocused, onHover = { hovered = true },
                            playEffectActive = controller.settled && playVisible && viewer == null && shell.layer == null &&
                                shell.picker == null && shell.portal == null && shell.launch == null && shell.result == null,
                        )
                        // Fading out while it closes: its alpha applies to each of its parts as they draw (no offscreen copy of the page)
                        if (sectionsReady) Column(
                            Modifier.graphicsLayer {
                                val (dy, a) = pageMotion()
                                translationY = dy * k
                                alpha = a * sectionsIn.value
                                compositingStrategy = CompositingStrategy.ModulateAlpha
                            },
                        ) {
                            if (details == null) {
                                DetailsStatus(entry.detailsLoading || data?.detailsUpdatedAt == 0L && data.detailsFailedAt == 0L)
                            }
                            for (section in sections) {
                                Spacer(Modifier.height(SECTION_GAP.px))
                                when (section) {
                                    OverviewModel.Section.ABOUT -> AboutSection(details!!, storyOpen, { storyOpen = it }, parts, onPartFocused, { hovered = true })
                                    OverviewModel.Section.MEDIA -> MediaSection(media, track, parts, onPartFocused, { hovered = true }) { index, rect ->
                                        viewer = ViewerRequest(index, rect)
                                    }
                                    OverviewModel.Section.DETAILS -> DetailsSection(details!!, candidate, parts, onPartFocused, { hovered = true })
                                    OverviewModel.Section.STREAMING -> StreamingSection(app, pc, playtime, resumes, data?.completion?.seconds, streaming::value, parts, onPartFocused, { hovered = true })
                                    OverviewModel.Section.LINKS -> LinksSection(links, parts, onPartFocused, { hovered = true }) { url ->
                                        runCatching { context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
                                    }
                                    OverviewModel.Section.SIMILAR -> SimilarSection(details!!, pc, library, similarRow, parts, onPartFocused, { hovered = true }, coverArt) { owned, name ->
                                        if (owned != null) {
                                            currentClose()
                                            onJump(owned)
                                        }
                                        else shell.showToast(res.getString(R.string.overview_not_in_library, name), Symbols.INFO)
                                    }
                                }
                            }
                            Row(
                                Modifier.padding(start = SIDE.px, top = 80.px, end = SIDE.px),
                                verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.px),
                            ) {
                                Symbol(Symbols.INFO, 17.tpx, colors.text3)
                                BasicText(stringResource(R.string.overview_credit), style = TextStyle(color = colors.text3, fontFamily = Figtree, fontSize = 17.tpx))
                            }
                            Spacer(Modifier.height((220f + OverviewScroll.END_PAD).px))
                        }
                    }
                    Spacer(Modifier.matchParentSize().graphicsLayer().drawBehind {
                        // The carousels scroll under the ring: reading their state redraws it with them
                        track.firstVisibleItemScrollOffset; similarRow.firstVisibleItemScrollOffset
                        with(focus) { drawRing(colors, k) }
                    })
                    }
                }
            }
        }
        // A finger or a mouse gets a round back button instead of the hint bar's B
        if (kind == InputKind.TOUCH || kind == InputKind.POINTER) {
            val shown by animateFloatAsState(if (controller.leaving) 0f else 1f, tween(Motion.FADE), label = "overviewBack")
            Box(
                Modifier
                    .offset(56.px, 130.px)
                    .graphicsLayer { alpha = shown * pageMotion().second }
                    .size(72.px)
                    .overviewGlass(pageHaze, 36f)
                    .semantics { contentDescription = res.getString(R.string.shell_hint_back) }
                    .pointerInput(Unit) { detectTapGestures { currentClose() } },
                contentAlignment = Alignment.Center,
            ) { Symbol(Symbols.ARROW_BACK, 34.tpx, colors.text) }
        }
        viewer?.let { request ->
            OverviewViewer(
                viewerMedia, request, viewerHaze, reducedMotion,
                thumbRect = { index -> carouselIndex(index).takeIf { it >= 0 }?.let { thumbRect(focus, it, root) } },
                onIndex = { index ->
                    viewer = request.copy(index = index)
                    carouselIndex(index).takeIf { it >= 0 }?.let { scope.launch { track.anchor(it, reducedMotion) } }
                },
                onClosed = {
                    val index = carouselIndex(viewer?.index ?: request.index).coerceAtLeast(0)
                    viewer = null
                    focus.targets["shot$index"]?.requester?.let { runCatching { it.requestFocus() } }
                },
                onCloseRequest = { viewer = viewer?.copy(closing = true) },
                input = viewerInput,
                onControlsVisibilityChanged = { shell.overviewViewerControlsVisible = it },
            )
        }
    }
}

/** Thumbnail [index] of the carousel on screen (root pixels), or null while it is scrolled away (only the visible ones are composed). */
private fun thumbRect(focus: OverviewFocus, index: Int, root: LayoutCoordinates?): Rect? {
    val r = root?.takeIf { it.isAttached } ?: return null
    val c = focus.targets["shot$index"]?.coordinates?.takeIf { it.isAttached } ?: return null
    return r.localBoundingBoxOf(c, clipBounds = false)
}

/** Scrolls the page (and a carousel) to a part that took the focus ([OverviewScroll]). */
private suspend fun revealPart(focus: OverviewFocus, id: String, part: PartFocus, scroll: androidx.compose.foundation.ScrollState, k: Float, reduced: Boolean) {
    val host = focus.host ?: return
    val target = focus.targets[id]?.coordinates ?: return
    val section = focus.sections[part.section]
    if (!host.isAttached || !target.isAttached) return
    val view = (scroll.viewportSize.takeIf { it > 0 } ?: return) / k
    val element = host.localBoundingBoxOf(target, clipBounds = false)
    val sectionRect = section?.takeIf { it.isAttached }?.let { host.localBoundingBoxOf(it, clipBounds = false) } ?: element
    val to = OverviewScroll.page(
        scroll.value / k, part.section == HEAD, element.top / k, element.bottom / k, sectionRect.top / k, sectionRect.bottom / k,
        view, scroll.maxValue / k,
    )
    // A carousel scrolls like the dashboard's cover row: the focused item glides to the row's start
    part.track?.anchor(part.index, reduced)
    val pixels = (to * k).toInt()
    if (abs(pixels - scroll.value) > 1) {
        if (reduced) scroll.scrollTo(pixels) else scroll.animateScrollTo(pixels, tween(620, easing = EaseOut))
    }
}

/** While the IGDB details load (a spinner line) or when they could not be loaded (a muted line). */
@Composable
private fun DetailsStatus(loading: Boolean) {
    val colors = LocalDelosColors.current
    Row(Modifier.padding(start = SIDE.px, top = SECTION_GAP.px), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.px)) {
        if (loading) com.limelight.shell.ui.Spinner(28f) else Symbol(Symbols.INFO, 26.tpx, colors.text3)
        BasicText(
            stringResource(if (loading) R.string.overview_loading else R.string.overview_unavailable),
            style = TextStyle(color = colors.text3, fontFamily = Figtree, fontSize = 21.tpx),
        )
    }
}

/**
 * The header (`.a-head`, one screen minus 160 px high): the cover, then the year and developer, the title, the tags and HDR, the
 * rings and the age rating, Play and App settings, the streaming stats; the cue at its bottom names the sections below.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun Header(
    controller: OverviewController, pc: Pc, app: App, details: GameDetails?, hdr: NativeHdr?, hltbSeconds: Long?,
    candidate: com.limelight.shell.data.MetadataCandidate?, art: GameArt, cover: ImageBitmap?, card: GraphicsLayer?, playtime: Playtime, resumes: Int,
    sections: List<OverviewModel.Section>, ratings: () -> Float, pageMotion: () -> Pair<Float, Float>, playRequester: FocusRequester,
    onPlay: () -> Unit, onSettings: () -> Unit, coverArt: @Composable (App, Modifier) -> Unit, pull: () -> Float,
    register: (String) -> Unit, onFocused: (String) -> Unit, onHover: () -> Unit, playEffectActive: Boolean,
) {
    val colors = LocalDelosColors.current
    val stage = LocalStage.current
    val res = textResources()
    val k = stageUnit()
    val metadata = LocalGameMetadata.current.entry(pc, app).data
    val key = homeKey(pc, app)
    val hue = HeroBitmaps.hue(key) ?: art.hue2
    // A staggered part of the header (`.st`): it rises in after the page has begun to move, and fades out first when it closes. Its alpha
    // applies to each of its parts as they draw: an offscreen copy would be cut to the part's box (Play's halo reaches beyond it)
    fun Modifier.st(index: Int, delay: Int = OverviewMotion.HEADER_DELAY): Modifier = graphicsLayer {
        val (pageDy, pageAlpha) = pageMotion()
        val (dy, a) = when {
            controller.closing -> OverviewMotion.leave(
                controller.clock.value, controller.reduced,
                from = OverviewMotion.rise(controller.interruptedAt, delay, index, controller.reduced).second,
            )
            controller.opening -> OverviewMotion.rise(controller.clock.value, delay, index, controller.reduced)
            else -> 0f to 1f
        }
        translationY = (dy + pageDy) * k
        alpha = a * pageAlpha
        compositingStrategy = CompositingStrategy.ModulateAlpha
    }
    // A chip popping in ([OverviewMotion.chip]) [start] ms into the opening
    fun Modifier.pop(start: Int): Modifier = graphicsLayer {
        val s = OverviewMotion.chip(controller.openMillis() - start, controller.reduced)
        scaleX = s
        scaleY = s
    }
    Box(Modifier.fillMaxWidth().height((stage.stageHeight - 160f).px).overviewSection(HEAD)) {
        Row(Modifier.padding(start = SIDE.px, top = HEAD_TOP.px, end = SIDE.px)) {
            // The cover: a frame flying from the card's place and shape into the slot and back ([OverviewMotion.cover]). The picture
            // in it keeps its proportions (one scale, cropped to the frame), and the card as the row drew it ([card]: its own art,
            // a wide card's too, the current frame of its animation) crossfades into it on the way
            val pose = remember { CoverPose() }
            Box(
                Modifier
                    .size(COVER_W.px, COVER_H.px)
                    // Where the slot is on screen: it does not move while the cover flies (the page is at its top, nothing lays out).
                    // Known, the flight takes over from the dashboard's card
                    .onGloballyPositioned {
                        pose.slot = it.rootRect()
                        controller.placeCover()
                    }
                    // The flying ring redraws every frame: in a layer of its own, so the header around it is not recorded again
                    .graphicsLayer()
                    .drawWithCache {
                        val paint = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply {
                            style = android.graphics.Paint.Style.STROKE
                            strokeWidth = 4f * k
                        }
                        val glow = FlightGlow(k)
                        onDrawWithContent {
                            drawContent()
                            // The card's focus ring flies with it and fades out on the way (a finger sees the card's ring too)
                            if (controller.opening || controller.closing) {
                                val p = pose.update(controller, size.width, size.height, pull(), k)
                                if (p.alpha <= 0f) return@onDrawWithContent
                                val amount = (1f - controller.flight.value).coerceIn(0f, 1f)
                                if (amount <= 0f) return@onDrawWithContent
                                val left = p.x + p.frameLeft * p.scale
                                val top = p.y + p.frameTop * p.scale
                                val right = left + p.frameWidth * p.scale
                                val bottom = top + p.frameHeight * p.scale
                                val corner = Motion.lerp(OverviewMotion.TO_CORNER, OverviewMotion.FROM_CORNER, amount) * k
                                paint.color = colors.accent.copy(alpha = amount).toArgb()
                                val canvas = drawContext.canvas.nativeCanvas
                                glow.draw(canvas, left, top, right, bottom, colors.glow.copy(alpha = colors.glow.alpha * amount).toArgb())
                                canvas.drawRoundRect(left, top, right, bottom, corner, corner, paint)
                            }
                        }
                    }
                    .graphicsLayer {
                        val p = pose.update(controller, size.width, size.height, pull(), k)
                        transformOrigin = TransformOrigin(0f, 0f)
                        scaleX = p.scale
                        scaleY = p.scale
                        translationX = p.x
                        translationY = p.y
                        alpha = p.alpha
                        shape = p.shape
                        clip = true
                    }
                    .dropShadow(RoundedCornerShape(OverviewMotion.TO_CORNER.px)) {
                        radius = 120f * k
                        spread = -20f * k
                        offset = Offset(0f, 50f * k)
                        color = Color.Black
                        alpha = .7f
                    }
                    .background(colors.surface2),
            ) {
                if (cover != null) Image(cover, app.name, Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
                else coverArt(app, Modifier.fillMaxSize())
                // The chosen animated cover plays over its still first frame, as on the card (and stops with the overview)
                val artwork = LocalArtwork.current
                val animatedArt = LocalDashboardArt.current
                val animation by rememberAnimatedArtwork(artwork, pc, app, ArtKind.COVER)
                if (animatedArt != null && animation != null && cover != null) {
                    AnimatedArtLayer(
                        animation, animatedArt.motionOn, animatedArt.ticker, (COVER_W * k).roundToInt(), (COVER_H * k).roundToInt(),
                        Modifier.fillMaxSize(), stamp = artwork.version.intValue,
                    )
                }
                if (card != null) Box(
                    Modifier
                        .fillMaxSize()
                        .graphicsLayer { alpha = pose.update(controller, size.width, size.height, pull(), k).card }
                        .drawBehind {
                            val p = pose.update(controller, size.width, size.height, pull(), k)
                            val cw = card.size.width.toFloat()
                            val ch = card.size.height.toFloat()
                            if (p.card > 0f && cw > 0f && ch > 0f) {
                                // The card fills the frame at one scale, as the cover does
                                val u = OverviewMotion.fillScale(p.frameWidth, p.frameHeight, cw, ch)
                                translate(p.frameLeft + (p.frameWidth - cw * u) / 2f, p.frameTop + (p.frameHeight - ch * u) / 2f) {
                                    scale(u, u, pivot = Offset.Zero) { drawLayer(card) }
                                }
                            }
                        },
                )
                // The rim of the cover at rest (the frame in flight has the card's corner)
                Box(
                    Modifier
                        .fillMaxSize()
                        .graphicsLayer { alpha = if (controller.opening || controller.closing) 0f else 1f }
                        .border(1.px, Color.White.copy(alpha = .08f), RoundedCornerShape(OverviewMotion.TO_CORNER.px)),
                )
            }
            Spacer(Modifier.width(COVER_GAP.px))
            Column(Modifier.zIndex(1f).padding(top = 4.px), verticalArrangement = Arrangement.spacedBy(24.px)) {
                val year = details?.firstReleaseDate?.let(OverviewModel::year) ?: candidate?.year
                val developers = details?.let(OverviewModel::developers).orEmpty()
                val eyebrow = listOfNotNull(year?.toString(), developers.joinToString(", ").takeIf { it.isNotEmpty() })
                    .reduceOrNull { a, b -> res.getString(R.string.home_meta_join, a, b) }
                OverviewEyebrow(eyebrow ?: "\u00a0", colors.accent, Modifier.st(0))
                Title(controller, pc, app, hue, pageMotion)
                // Up to two genres and two themes (as on the dashboard), then HDR
                val genres = remember(metadata) { topGenres(candidate?.genres.orEmpty(), metadata?.steamTags.orEmpty()) }
                val themes = remember(metadata) { topThemes(candidate?.themes.orEmpty(), metadata?.steamTags.orEmpty()) }
                FlowRow(Modifier.st(1), horizontalArrangement = Arrangement.spacedBy(OverviewGaps.TAG_GAP.px), verticalArrangement = Arrangement.spacedBy(OverviewGaps.TAG_GAP.px)) {
                    // Each chip pops in as its row rises, one after another
                    val tagsStart = OverviewMotion.HEADER_DELAY + OverviewMotion.HEADER_STAGGER
                    var chip = 0
                    fun next() = Modifier.pop(tagsStart + chip++ * OverviewMotion.HEADER_STAGGER)
                    genres.forEach { Box(next()) { OverviewTag(genreIcon(it.id), genreName(res, it), TagLook.GENRE, hue) } }
                    themes.forEach { Box(next()) { OverviewTag(themeIcon(it.id), themeName(res, it)) } }
                    when (hdr) {
                        NativeHdr.HDR -> Box(next()) { NativeHdrChip() }
                        NativeHdr.SDR -> Box(next()) { Chip(Symbols.SDR, "SDR") }
                        NativeHdr.UNKNOWN -> Box(next()) { Chip(Symbols.HDR_UNKNOWN, stringResource(R.string.metadata_hdr_unverified)) }
                        null -> Unit
                    }
                }
                Row(Modifier.st(2), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(46.px)) {
                    val locale = PlaytimeFormat.localeOf(res)
                    val count = NumberFormat.getIntegerInstance(locale)
                    RatingRing(
                        details?.criticRating, stringResource(R.string.overview_critics),
                        if (details?.criticRating == null) stringResource(R.string.overview_no_critics)
                        else pluralStringResource(R.plurals.overview_reviews, details.criticRatingCount, count.format(details.criticRatingCount)),
                        colors.accent, ratings, index = 0, reduced = controller.reduced,
                    )
                    RatingRing(
                        details?.rating, stringResource(R.string.overview_players),
                        when {
                            details?.rating == null -> stringResource(R.string.overview_not_rated_yet)
                            details.ratingCount < OverviewModel.FEW_RATINGS -> pluralStringResource(R.plurals.overview_ratings_few, details.ratingCount, count.format(details.ratingCount))
                            else -> pluralStringResource(R.plurals.overview_ratings, details.ratingCount, count.format(details.ratingCount))
                        },
                        colors.accent2, ratings, index = 1, reduced = controller.reduced,
                    )
                    if (details != null) {
                        val country = java.util.Locale.getDefault().country
                        AgeRatingView(
                            OverviewModel.ageRatings(details.ageRatings, country).firstOrNull(),
                            stringResource(R.string.overview_not_rated), stringResource(R.string.overview_no_age_rating), stringResource(R.string.overview_no_descriptors),
                            // Its badge pops in after the two rings, on their clock
                            badgeScale = { OverviewMotion.chip(ratings() - 2 * OverviewMotion.RING_STAGGER, controller.reduced) },
                        )
                    }
                }
                // The gap grows with the wider button, so Play's sweep band never reaches App settings (FocusSpacing)
                FocusRow(OverviewGaps.BUTTONS, OverviewGaps.BUTTONS_GAP, Modifier.st(3)) {
                    OverviewButton("play", Symbols.PLAY_ARROW, stringResource(R.string.overview_play), primary = true, playRequester, onPlay, register, onFocused, onHover, playEffectActive)
                    OverviewButton("settings", Symbols.SETTINGS, stringResource(R.string.home_menu_settings), primary = false, remember { FocusRequester() }, onSettings, register, onFocused, onHover)
                }
                Row(Modifier.st(4), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(36.px)) {
                    val texts = rememberPlaytimeTexts(playtime, resumes)
                    // As tall as the home's info row at this interface scale: the texts grow with it
                    StatRow(texts, PlaytimeFit.FULL, HomeCompact.blockHeight(LocalStage.current.textScale).px)
                    if (hltbSeconds != null && hltbSeconds > 0) {
                        Box(Modifier.width(2.px).height(38.px).background(Color(133, 146, 168).copy(alpha = .4f)))
                        Column {
                            BasicText(
                                stringResource(R.string.overview_hltb).uppercase(PlaytimeFormat.localeOf(res)),
                                style = TextStyle(color = colors.text3, fontFamily = Figtree, fontWeight = FontWeight.Bold, fontSize = 13.tpx, letterSpacing = .16.em, shadow = headerShadow()),
                            )
                            BasicText(
                                completionText(res, hltbSeconds),
                                style = TextStyle(color = colors.text2, fontFamily = Figtree, fontWeight = FontWeight.SemiBold, fontSize = 23.tpx, shadow = headerShadow()),
                            )
                        }
                    }
                }
            }
        }
        // The cue: the sections below, with a bobbing caret
        val cue = OverviewModel.cue(sections)
        if (cue.isNotEmpty()) {
            val names = cue.map {
                stringResource(
                    when (it) {
                        OverviewModel.Section.ABOUT -> R.string.overview_section_about
                        OverviewModel.Section.MEDIA -> R.string.overview_cue_media
                        OverviewModel.Section.DETAILS -> R.string.overview_section_details
                        else -> R.string.overview_section_similar
                    },
                )
            }
            Row(
                Modifier.align(Alignment.BottomCenter).padding(bottom = 18.px).st(5, OverviewMotion.CUE_DELAY),
                verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.px),
            ) {
                Bob(down = true) { Symbol(Symbols.EXPAND_MORE, 24.tpx, colors.accent, filled = true) }
                BasicText(
                    names.reduce { a, b -> res.getString(R.string.home_meta_join, a, b) },
                    style = TextStyle(color = colors.text3, fontFamily = Figtree, fontWeight = FontWeight.SemiBold, fontSize = 19.tpx, letterSpacing = .04.em),
                )
            }
        }
    }
}

/**
 * The title: the game's logo when it has one, exactly the dashboard's (the user's SteamGridDB choice, else the default logo, its
 * animation playing over it), else its name in 68 px Unbounded in the game's colors; while the logo lookup is pending, the logo's room.
 * The name flies from the dashboard's name ([OverviewController.title]); a logo crossfades with the dashboard's logo, each at its own
 * place ([OverviewMotion.logoArriving]); otherwise it rises like the header's parts.
 */
@Composable
private fun Title(controller: OverviewController, pc: Pc, app: App, hue: Float, pageMotion: () -> Pair<Float, Float>) {
    val colors = LocalDelosColors.current
    val k = stageUnit()
    val artwork = LocalArtwork.current
    val logo by rememberArtworkResult(artwork, pc, app, ArtKind.LOGO)
    val logoImage = logo.image
    val isLogo = logoImage != null
    var coordinates by remember { mutableStateOf<LayoutCoordinates?>(null) }
    val flight = Modifier
        .onGloballyPositioned { coordinates = it }
        .graphicsLayer {
            transformOrigin = TransformOrigin(0f, 0f)
            val source = controller.title?.takeIf { (it.fontSize == null) == isLogo }
            val moving = controller.opening || controller.closing
            val c = coordinates
            val interrupted = controller.interruptedAt
            if (source != null && moving && !controller.reduced && source.fontSize == null) {
                // A logo does not move: it fades in here while the dashboard's fades out where it is, and the other way round
                alpha = if (controller.closing) {
                    OverviewMotion.logoLeaving(controller.clock.value, opening = false) * OverviewMotion.logoArriving(interrupted, opening = true)
                } else OverviewMotion.logoArriving(controller.clock.value, opening = true)
            } else if (source?.fontSize != null && moving && c != null && c.isAttached && !controller.reduced) {
                val to = c.rootRect()
                val p = if (controller.closing) OverviewMotion.title(controller.clock.value, opening = false, from = OverviewMotion.title(interrupted, opening = true))
                else OverviewMotion.title(controller.clock.value, opening = true)
                // The dashboard's name at its font size, centred on its box, grows into this one
                val scale = source.fontSize / TITLE_SIZE
                val dx = source.rect.left - to.left
                val dy = source.rect.top - to.top + (source.rect.height - to.height * scale) / 2f
                translationX = dx * (1f - p)
                translationY = dy * (1f - p)
                val s = Motion.lerp(scale, 1f, p)
                scaleX = s
                scaleY = s
            } else {
                // Nothing of its kind on the dashboard (or reduced motion): the title enters and leaves like the header's parts
                val (pageDy, pageAlpha) = pageMotion()
                val (dy, a) = when {
                    controller.closing -> OverviewMotion.leave(
                        controller.clock.value, controller.reduced,
                        from = if (source == null) OverviewMotion.rise(interrupted, OverviewMotion.HEADER_DELAY, 0, controller.reduced).second
                        else Motion.progress(interrupted, 0, Motion.FADE, Motion.Ease),
                    )
                    controller.opening && source == null -> OverviewMotion.rise(controller.clock.value, OverviewMotion.HEADER_DELAY, 0, controller.reduced)
                    controller.opening -> 0f to Motion.progress(controller.clock.value, 0, Motion.FADE, Motion.Ease)
                    else -> 0f to 1f
                }
                translationY = (dy + pageDy) * k
                alpha = a * pageAlpha
            }
        }
    if (logoImage != null) Box(flight) {
        Image(
            logoImage, contentDescription = app.name, contentScale = ContentScale.Fit, alignment = Alignment.CenterStart,
            modifier = Modifier.height(LOGO_H.px).widthIn(max = LOGO_W.px),
        )
        val animatedArt = LocalDashboardArt.current
        val animation by rememberAnimatedArtwork(artwork, pc, app, ArtKind.LOGO)
        if (animatedArt != null && animation != null) {
            AnimatedArtLayer(
                animation, animatedArt.motionOn, animatedArt.ticker, (LOGO_W * k).roundToInt(), (LOGO_H * k).roundToInt(),
                Modifier.matchParentSize(), fit = true, alignX = 0f, stamp = artwork.version.intValue,
            )
        }
    } else if (logo.resolved) BasicText(
        app.name,
        modifier = flight.widthIn(max = 1150.px),
        style = TextStyle(
            brush = Brush.verticalGradient(.3f to colors.text, 1f to hsl(hue, 70f, 68f)),
            fontFamily = Unbounded, fontWeight = FontWeight.Bold, fontSize = TITLE_SIZE.tpx, lineHeight = 1.06.em, letterSpacing = (-.01).em,
            lineBreak = LineBreak.Heading,
            shadow = Shadow(Color.Black.copy(alpha = .4f), Offset(0f, 6f * k), 28f * k),
        ),
    ) else Spacer(Modifier.height(LOGO_H.px))
}

/** Bobs its content by a few pixels (2.2 s, `@keyframes bob`), down or up; still with reduced motion. */
@Composable
internal fun Bob(down: Boolean, content: @Composable () -> Unit) {
    val reduced = com.limelight.shell.ui.LocalReducedMotion.current
    val k = stageUnit()
    val phase = remember { Animatable(0f) }
    LaunchedEffect(reduced) {
        if (reduced) return@LaunchedEffect
        while (true) {
            phase.snapTo(0f)
            phase.animateTo(1f, tween(2200, easing = LinearEasing))
        }
    }
    Box(Modifier.graphicsLayer {
        val t = phase.value
        // CSS `ease` keyframes 0 → 50 % → 100 %: there and back, eased each way
        val half = if (t < .5f) Motion.Ease.transform(t / .5f) else 1f - Motion.Ease.transform((t - .5f) / .5f)
        translationY = (if (down) 5f else -6f) * half * k
    }) { content() }
}

/** A header button (`.btn`): 86 px pill with 54 px at its sides, Play in the accent. */
@Composable
private fun OverviewButton(
    id: String, icon: String, label: String, primary: Boolean, requester: FocusRequester, onClick: () -> Unit,
    register: (String) -> Unit, onFocused: (String) -> Unit, onHover: () -> Unit, effectActive: Boolean = false,
) {
    val colors = LocalDelosColors.current
    LaunchedEffect(id) { register(id) }
    var focused by remember { mutableStateOf(false) }
    val pulse = rememberPressPulse()
    val scale = animateFocusScale(focused, pulse.pressed, OverviewGaps.BUTTONS.scale)
    val reduced = com.limelight.shell.ui.LocalReducedMotion.current
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    val window = LocalWindowInfo.current
    var foreground by remember(lifecycle) { mutableStateOf(lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED)) }
    DisposableEffect(lifecycle) {
        val observer = LifecycleEventObserver { _, _ -> foreground = lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED) }
        lifecycle.addObserver(observer)
        onDispose { lifecycle.removeObserver(observer) }
    }
    val effectShown = primary && focused && effectActive
    Box(
        Modifier
            .hoverFocus(requester, onHover)
            .focusEffects(
                // The growth and the shared ring are drawn below, for every input (a finger's first tap only focuses: focus first)
                onClick = onClick, corner = 999f, focusScale = 1f, press = false, pressPulse = pulse,
                ring = null, glow = null, focusRequester = requester,
                onFocusChange = { focused = it; if (it) onFocused(id) }, onTouchTap = { it },
            )
            // One focus/press transform for the body, the effect and the measured ring bounds
            .pressInput(pulse)
            .graphicsLayer {
                val s = scale.value * PointerLook.scale(0f, pulse.amount)
                scaleX = s
                scaleY = s
            }
            // The shared ring's 4 px band sits 2..6 px outside the pill (b.html `inset: -6px`), with the capsule's own radius
            .overviewTarget(id, requester, HEAD, group = "buttons", locked = true, ring = RingStyle(2f, 999f),
                visualMotion = { scale.value + pulse.amount }),
    ) {
        if (primary) PlayEffect(effectShown, effectShown && foreground && window.isWindowFocused, reduced)
        Row(
            Modifier
                .height(86.px)
                .clip(CircleShape)
                .then(if (primary) Modifier.background(colors.accent) else Modifier.glass(colors.surface))
                .then(if (primary) Modifier else Modifier.border(1.px, colors.line, CircleShape))
                .padding(horizontal = 54.px),
            verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.px),
        ) {
            val ink = if (primary) colors.onAccent else colors.text
            Symbol(icon, 32.tpx, ink, filled = focused)
            BasicText(label, maxLines = 1, style = TextStyle(color = ink, fontFamily = Figtree, fontWeight = FontWeight.Bold, fontSize = 26.tpx))
        }
    }
}

/**
 * Play's signature "living gold sweep" (build/play-effect-proto/b.html): a static gold halo under the pill
 * (`box-shadow: 0 0 26px 3px #f6b73c44, 0 0 56px 4px #f6b73c1c` at -12 px) and, outside the shared focus ring, a 4 px band
 * at -11..-7 px whose conic highlight turns once every [PLAY_SWEEP_MS]. Only the band's own layer redraws per frame
 * (a cached shader whose matrix turns); the pill, its text and the ring never rotate. Reduced motion keeps only the halo.
 */
@Composable
private fun BoxScope.PlayEffect(shown: Boolean, running: Boolean, reduced: Boolean) {
    val k = stageUnit()
    val accent = LocalDelosColors.current.accent
    val fade by animateFloatAsState(if (shown) 1f else 0f, if (reduced) snap() else tween(280, easing = Motion.Ease), label = "playFx")
    if (fade <= 0f && !shown) return
    val phase = remember { Animatable(0f) }
    LaunchedEffect(running, reduced) {
        if (!running || reduced) return@LaunchedEffect
        while (true) {
            phase.animateTo(1f, tween(PLAY_SWEEP_MS, easing = LinearEasing))
            phase.snapTo(0f)
        }
    }
    // The halo: recorded once, faded by its layer's alpha. The alpha modulates the paint (ModulateAlpha): an offscreen layer would be cut
    // to the pill's rectangle, and the halo, which reaches out beyond it, showed square corners until it had faded in
    Box(Modifier.matchParentSize().graphicsLayer { alpha = fade; compositingStrategy = CompositingStrategy.ModulateAlpha }.drawWithCache {
        fun halo(spread: Float, blur: Float, alpha: Float) = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply {
            color = accent.copy(alpha = alpha).toArgb()
            maskFilter = android.graphics.BlurMaskFilter(blur, android.graphics.BlurMaskFilter.Blur.NORMAL)
        } to spread
        val layers = listOf(halo((12f + 3f) * k, 13f * k, .27f), halo((12f + 4f) * k, 28f * k, .11f))
        onDrawBehind {
            val canvas = drawContext.canvas.nativeCanvas
            for ((paint, spread) in layers) {
                val r = size.height / 2f + spread
                canvas.drawRoundRect(-spread, -spread, size.width + spread, size.height + spread, r, r, paint)
            }
        }
    })
    if (reduced) return
    Box(Modifier.matchParentSize().graphicsLayer { alpha = fade; compositingStrategy = CompositingStrategy.ModulateAlpha }.drawWithCache {
        val band = 4f * k
        val inset = 9f * k // the band's centre line, between -11 and -7
        val cx = size.width / 2f
        val cy = size.height / 2f
        // conic-gradient(transparent 0–120°, accent/.27 190°, pale gold 235°, accent/.67 257°, transparent 290°)
        val shader = android.graphics.SweepGradient(
            cx, cy,
            intArrayOf(0, 0, accent.copy(alpha = .27f).toArgb(), androidx.compose.ui.graphics.lerp(accent, Color.White, .65f).toArgb(),
                accent.copy(alpha = .67f).toArgb(), 0, 0),
            floatArrayOf(0f, 120f / 360f, 190f / 360f, 235f / 360f, 257f / 360f, 290f / 360f, 1f),
        )
        val matrix = android.graphics.Matrix()
        val paint = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply {
            style = android.graphics.Paint.Style.STROKE
            strokeWidth = band
            this.shader = shader
        }
        val r = size.height / 2f + inset
        onDrawBehind {
            // CSS conic 0° is up, Android's sweep starts at 3 o'clock
            matrix.setRotate(phase.value * 360f - 90f, cx, cy)
            shader.setLocalMatrix(matrix)
            drawContext.canvas.nativeCanvas.drawRoundRect(-inset, -inset, size.width + inset, size.height + inset, r, r, paint)
        }
    })
}

private const val PLAY_SWEEP_MS = 5800

/**
 * A focusable part of a section: registers, scales on focus and reports it. The part draws its own focus lift and squeeze (like
 * [OverviewButton]) so the shared ring observes that clock: without it the ring is only redrawn while something else moves (a
 * carousel's anchor scroll), and a part the pointer focuses in place keeps a ring of a half-grown frame.
 */
@Composable
private fun Modifier.part(
    id: String, section: OverviewModel.Section, parts: HashMap<String, PartFocus>, onFocused: (String) -> Unit, onHover: () -> Unit,
    onClick: () -> Unit, ring: RingStyle, corner: Float, focusScale: Float = 1f, group: String? = null, locked: Boolean = false,
    track: LazyListState? = null, index: Int = 0, onFocusChange: (Boolean) -> Unit = {},
): Modifier {
    val requester = remember(id) { FocusRequester() }
    LaunchedEffect(id, track, index) { parts[id] = PartFocus(section.name, track, index) }
    var look by remember { mutableStateOf(false) }
    val pulse = rememberPressPulse()
    val scale = animateFocusScale(look, pulse.pressed, focusScale)
    return this
        .hoverFocus(requester, onHover)
        .focusEffects(
            onClick = onClick, corner = corner, focusScale = 1f, press = false, pressPulse = pulse, ring = null, glow = null,
            focusRequester = requester, onFocusChange = { onFocusChange(it); if (it) onFocused(id) }, onLookChange = { look = it },
            onTouchTap = { it },
        )
        .pressInput(pulse)
        .graphicsLayer {
            val s = scale.value * PointerLook.scale(0f, pulse.amount)
            scaleX = s
            scaleY = s
        }
        .overviewTarget(id, requester, section.name, group, locked, ring, visualMotion = { scale.value + pulse.amount })
}

/** About: the summary, the storyline expanding in place, the facts card. */
@Composable
private fun AboutSection(
    details: GameDetails, storyOpen: Boolean, onStory: (Boolean) -> Unit,
    parts: HashMap<String, PartFocus>, onFocused: (String) -> Unit, onHover: () -> Unit,
) {
    val colors = LocalDelosColors.current
    val res = textResources()
    val reduced = com.limelight.shell.ui.LocalReducedMotion.current
    val section = OverviewModel.Section.ABOUT
    val scope = rememberCoroutineScope()
    val k = stageUnit()
    Column(Modifier.padding(horizontal = SIDE.px).overviewSection(section.name)) {
        SectionHeading(Symbols.OVERVIEW_ABOUT, stringResource(R.string.overview_section_about))
        Row(horizontalArrangement = Arrangement.spacedBy(56.px)) {
            Column(Modifier.weight(1f)) {
                details.summary?.let { summary ->
                    BasicText(
                        summary,
                        Modifier.part("summary", section, parts, onFocused, onHover, onClick = {}, ring = RingStyle(14f, 18f), corner = 18f),
                        style = TextStyle(color = colors.text, fontFamily = Figtree, fontSize = 28.tpx, lineHeight = 1.5.em),
                    )
                }
                details.storyline?.let { story ->
                    // While the storyline opens or closes, the edge that cuts it (its bottom: the text continues below) fades out
                    // over up to STORY_FADE instead of a hard cut; the mask needs an offscreen copy only while it is cut
                    val storyFull = remember { IntArray(1) }
                    var storyCut by remember { mutableStateOf(false) }
                    Column(
                        Modifier
                            .onSizeChanged { storyCut = it.height > 0 && storyFull[0] - it.height > 1 }
                            .graphicsLayer { compositingStrategy = if (storyCut) CompositingStrategy.Offscreen else CompositingStrategy.Auto }
                            .drawWithContent {
                                drawContent()
                                val hidden = storyFull[0] - size.height
                                if (storyCut && hidden > 1f) {
                                    val fade = min(hidden, STORY_FADE * k)
                                    val top = size.height - fade
                                    drawRect(
                                        Brush.verticalGradient(0f to Color.Black, 1f to Color.Transparent, startY = top, endY = size.height),
                                        topLeft = Offset(0f, top), size = Size(size.width, fade), blendMode = BlendMode.DstIn,
                                    )
                                }
                            },
                    ) {
                        AnimatedVisibility(
                            storyOpen,
                            enter = if (reduced) fadeIn(tween(Motion.FADE)) else expandVertically(tween(600, easing = EaseOut), expandFrom = Alignment.Top),
                            exit = if (reduced) fadeOut(tween(Motion.FADE)) else shrinkVertically(tween(600, easing = EaseOut), shrinkTowards = Alignment.Top),
                        ) {
                            BasicText(
                                story, Modifier.onSizeChanged { storyFull[0] = it.height }.padding(top = 22.px),
                                style = TextStyle(color = colors.text2, fontFamily = Figtree, fontSize = 25.tpx, lineHeight = 1.5.em),
                            )
                        }
                    }
                    val turn by animateFloatAsState(if (storyOpen) 180f else 0f, tween(500, easing = com.limelight.shell.ui.EaseSpring), label = "storyCaret")
                    var focused by remember { mutableStateOf(false) }
                    Row(
                        Modifier
                            .padding(top = OverviewGaps.ABOUT.gap.px)
                            .part("story", section, parts, onFocused, onHover, onClick = {
                                onStory(!storyOpen)
                                // The page settles on the button again once the text has opened or closed
                                scope.launch { delay(620); onFocused("story") }
                            }, ring = RingStyle(), corner = 999f, focusScale = 1.06f, onFocusChange = { focused = it })
                            .height(52.px).clip(CircleShape).glass(Color.White.copy(alpha = .06f)).border(1.px, colors.line, CircleShape)
                            .padding(horizontal = 22.px),
                        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.px),
                    ) {
                        Box(Modifier.graphicsLayer { rotationZ = turn }) { Symbol(Symbols.EXPAND_MORE, 22.tpx, colors.text, filled = focused) }
                        BasicText(
                            stringResource(if (storyOpen) R.string.overview_show_less else R.string.overview_read_story),
                            style = TextStyle(color = colors.text, fontFamily = Figtree, fontWeight = FontWeight.Bold, fontSize = 20.tpx),
                        )
                    }
                }
            }
            val facts = remember(details) { OverviewModel.facts(details) }
            if (facts.isNotEmpty()) {
                val locale = PlaytimeFormat.localeOf(res)
                val labelGap = 28.px
                val factGap = 16.px
                // The labels' column is as wide as the longest label in the current language (`.factlist`, `auto 1fr`), 28 px from the values
                Layout(
                    content = {
                        facts.forEach { row ->
                            OverviewEyebrow(
                                stringResource(
                                    when (row.fact) {
                                        OverviewModel.Fact.DEVELOPER -> R.string.overview_fact_developer
                                        OverviewModel.Fact.PUBLISHER -> R.string.overview_fact_publisher
                                        OverviewModel.Fact.RELEASED -> R.string.overview_fact_released
                                        OverviewModel.Fact.PORTED -> R.string.overview_fact_ported
                                        OverviewModel.Fact.ENGINE -> R.string.overview_fact_engine
                                        OverviewModel.Fact.SERIES -> R.string.overview_fact_series
                                    },
                                ),
                                colors.text3, Modifier.padding(top = 4.px), size = 17,
                            )
                            val value = row.date?.let { seconds ->
                                DateFormat.getDateInstance(DateFormat.LONG, locale).apply { timeZone = TimeZone.getTimeZone("UTC") }.format(Date(seconds * 1000))
                            } ?: row.value
                            val text = if (row.selfPublished) res.getString(R.string.home_meta_join, value, res.getString(R.string.overview_self_published)) else value
                            BasicText(text, style = TextStyle(color = colors.text, fontFamily = Figtree, fontWeight = FontWeight.SemiBold, fontSize = 21.tpx))
                        }
                    },
                    modifier = Modifier
                        .width(560.px)
                        .part("facts", section, parts, onFocused, onHover, onClick = {}, ring = RingStyle(8f, 34f), corner = 34f)
                        .overviewGlass(LocalBackdropHaze.current, 34f)
                        .padding(horizontal = 40.px, vertical = 36.px),
                ) { measurables, constraints ->
                    val gap = labelGap.roundToPx()
                    val rowGap = factGap.roundToPx()
                    val labels = measurables.filterIndexed { i, _ -> i % 2 == 0 }.map { it.measure(Constraints(maxWidth = constraints.maxWidth / 2)) }
                    val labelWidth = labels.maxOf { it.width }
                    val valueWidth = (constraints.maxWidth - labelWidth - gap).coerceAtLeast(0)
                    val values = measurables.filterIndexed { i, _ -> i % 2 == 1 }.map { it.measure(Constraints(maxWidth = valueWidth)) }
                    val heights = labels.indices.map { maxOf(labels[it].height, values[it].height) }
                    val height = heights.sum() + rowGap * (heights.size - 1)
                    layout(constraints.maxWidth, height) {
                        var y = 0
                        labels.indices.forEach {
                            labels[it].place(0, y)
                            values[it].place(labelWidth + gap, y)
                            y += heights[it] + rowGap
                        }
                    }
                }
            }
        }
    }
}

/** Screenshots and videos: the carousel, full width, its pictures moving a little against it. */
@Composable
private fun MediaSection(
    media: List<OverviewModel.Media>, track: LazyListState, parts: HashMap<String, PartFocus>,
    onFocused: (String) -> Unit, onHover: () -> Unit, onOpen: (Int, Rect?) -> Unit,
) {
    val colors = LocalDelosColors.current
    val section = OverviewModel.Section.MEDIA
    val k = stageUnit()
    Column(Modifier.overviewSection(section.name)) {
        Box(Modifier.padding(horizontal = SIDE.px)) { SectionHeading(Symbols.OVERVIEW_MEDIA, stringResource(R.string.overview_section_media), media.size) }
        LazyRow(
            state = track,
            // Room above and below inside the edge fade's layer for the focus growth and ring
            modifier = Modifier.fillMaxWidth().padding(top = 24.px, bottom = 30.px).overdraw(0.dp, TRACK_ROOM.px).anchorRowFade(LocalStage.current, track),
            contentPadding = PaddingValues(start = SIDE.px, end = SIDE.px, top = TRACK_ROOM.px, bottom = TRACK_ROOM.px),
            horizontalArrangement = Arrangement.spacedBy(OverviewGaps.MEDIA.gap.px),
        ) {
            itemsIndexed(media, key = { _, item -> item.kind.name + item.id }) { index, item ->
                Box(
                    Modifier
                        .part(
                            "shot$index", section, parts, onFocused, onHover,
                            onClick = { onOpen(index, null) },
                            ring = RingStyle(OverviewGaps.MEDIA_PAD, 28f - OverviewGaps.MEDIA_PAD), corner = 22f, focusScale = OverviewGaps.MEDIA_SCALE,
                            group = "track", locked = true, track = track, index = index,
                        )
                        .size(OverviewGaps.MEDIA_W.px, OverviewGaps.MEDIA_H.px)
                        .clip(RoundedCornerShape(22.px))
                        .background(colors.surface2),
                ) {
                    val image by rememberIgdbImage(item.thumb, (OverviewGaps.MEDIA_W * OverviewGaps.MEDIA_SCALE * k * 1.28f).toInt())
                    image?.let { bitmap ->
                        Image(
                            bitmap, null, contentScale = ContentScale.Crop,
                            modifier = Modifier
                                .requiredSize((OverviewGaps.MEDIA_W * 1.28f).px, OverviewGaps.MEDIA_H.px)
                                .graphicsLayer {
                                    // Parallax: the picture lags behind the carousel by 7 % of its distance from the middle
                                    val info = track.layoutInfo.visibleItemsInfo.firstOrNull { it.index == index }
                                    val width = track.layoutInfo.viewportSize.width
                                    if (info != null && width > 0) {
                                        val c = info.offset + info.size / 2f - width / 2f
                                        translationX = -c * .07f
                                    }
                                },
                        )
                    }
                    Box(Modifier.fillMaxSize().border(1.px, Color.White.copy(alpha = .1f), RoundedCornerShape(22.px)))
                    if (item.kind == OverviewModel.MediaKind.VIDEO) {
                        Box(Modifier.align(Alignment.Center)) { Symbol(Symbols.VIDEO_PLAY, 64.tpx, Color.White, filled = true) }
                        BasicText(
                            item.name ?: stringResource(R.string.overview_video), Modifier.align(Alignment.BottomStart).padding(start = 16.px, bottom = 14.px),
                            maxLines = 1, overflow = TextOverflow.Ellipsis,
                            style = TextStyle(color = Color.White, fontFamily = Figtree, fontWeight = FontWeight.Bold, fontSize = 18.tpx, shadow = Shadow(Color.Black.copy(alpha = .7f), Offset(0f, 2f * k), 10f * k)),
                        )
                    }
                }
            }
        }
    }
}

/** Details: genres, themes, modes, perspective, platforms, age ratings, multiplayer, languages and keywords in a glass card. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun DetailsSection(
    details: GameDetails, candidate: com.limelight.shell.data.MetadataCandidate?,
    parts: HashMap<String, PartFocus>, onFocused: (String) -> Unit, onHover: () -> Unit,
) {
    val colors = LocalDelosColors.current
    val res = textResources()
    val section = OverviewModel.Section.DETAILS
    val hue = colors.accentHue
    val items = buildList<Pair<Int, @Composable () -> Unit>> {
        val genres = candidate?.genres.orEmpty()
        if (genres.isNotEmpty()) add(R.string.overview_detail_genres to { genres.forEach { OverviewTag(genreIcon(it.id), genreName(res, it), TagLook.GENRE, hue) } })
        else if (details.genres.isNotEmpty()) add(R.string.overview_detail_genres to { details.genres.forEach { OverviewTag(null, it, TagLook.GENRE, hue) } })
        val themes = candidate?.themes.orEmpty()
        if (themes.isNotEmpty()) add(R.string.overview_detail_themes to { themes.forEach { OverviewTag(themeIcon(it.id), themeName(res, it)) } })
        else if (details.themes.isNotEmpty()) add(R.string.overview_detail_themes to { details.themes.forEach { OverviewTag(null, it) } })
        if (details.modes.isNotEmpty()) add(R.string.overview_detail_modes to {
            details.modes.forEach { OverviewTag(if (it.startsWith("Single", ignoreCase = true)) Symbols.MODE_SINGLE else Symbols.MODE_MULTI, it) } // l10n-ignore
        })
        if (details.perspectives.isNotEmpty()) add(R.string.overview_detail_perspective to { details.perspectives.forEach { OverviewTag(Symbols.PERSPECTIVE, it) } })
        if (details.platforms.isNotEmpty()) add(R.string.overview_detail_platforms to {
            details.platforms.forEach { platform ->
                val name = platform.abbreviation ?: platform.name
                val icon = when {
                    name.equals("PC", true) || platform.name.contains("Windows") || name.equals("Linux", true) -> Symbols.DESKTOP_WINDOWS // l10n-ignore
                    name.equals("Mac", true) || platform.name.contains("Mac") -> Symbols.PLATFORM_MAC // l10n-ignore
                    else -> Symbols.PLATFORM_CONSOLE
                }
                OverviewTag(icon, name)
            }
        })
        val country = java.util.Locale.getDefault().country
        add(R.string.overview_detail_age to {
            val ratings = OverviewModel.ageRatings(details.ageRatings, country)
            if (ratings.isEmpty()) AgeRatingView(null, stringResource(R.string.overview_not_rated), stringResource(R.string.overview_no_age_rating), stringResource(R.string.overview_no_descriptors))
            ratings.forEach { AgeRatingView(it, "", "", stringResource(R.string.overview_no_descriptors)) }
        })
        OverviewModel.coop(details.multiplayer)?.let { coop ->
            // One tag per fact, so a long list wraps instead of being cut
            val facts = listOfNotNull(
                coop.onlineCoopMax.takeIf { it > 0 }?.let { res.getString(R.string.overview_online_coop, it) },
                coop.onlineMax.takeIf { it > 0 }?.let { res.getString(R.string.overview_online_max, it) },
                coop.offlineCoopMax.takeIf { it > 0 }?.let { res.getString(R.string.overview_couch_coop, it) },
                res.getString(R.string.overview_lan_coop).takeIf { coop.lanCoop },
                res.getString(R.string.overview_split_screen).takeIf { coop.splitscreen },
            )
            add(R.string.overview_detail_multiplayer to { facts.forEach { OverviewTag(Symbols.GROUP, it) } })
        }
        if (details.languages.isNotEmpty()) add(R.string.overview_detail_languages to {
            OverviewTag(Symbols.LANGUAGE, res.getQuantityString(R.plurals.overview_languages, details.languages.size, details.languages.size))
        })
        if (details.keywords.isNotEmpty()) add(R.string.overview_detail_keywords to { details.keywords.take(OverviewModel.KEYWORDS).forEach { OverviewTag(null, it) } })
    }
    Column(Modifier.padding(horizontal = SIDE.px).overviewSection(section.name)) {
        SectionHeading(Symbols.OVERVIEW_DETAILS, stringResource(R.string.overview_section_details))
        Column(
            Modifier
                .fillMaxWidth()
                .part("details", section, parts, onFocused, onHover, onClick = {}, ring = RingStyle(8f, 34f), corner = 34f)
                .overviewGlass(LocalBackdropHaze.current, 34f)
                .padding(horizontal = 40.px, vertical = 36.px),
            verticalArrangement = Arrangement.spacedBy(30.px),
        ) {
            items.chunked(2).forEach { pair ->
                Row(horizontalArrangement = Arrangement.spacedBy(64.px)) {
                    pair.forEach { (label, content) ->
                        Column(Modifier.weight(1f)) {
                            OverviewEyebrow(stringResource(label), colors.text3, Modifier.padding(bottom = 12.px))
                            val gap = if (label == R.string.overview_detail_age) OverviewGaps.BADGE_GAP else OverviewGaps.TAG_GAP
                            FlowRow(horizontalArrangement = Arrangement.spacedBy(gap.px), verticalArrangement = Arrangement.spacedBy(gap.px)) { content() }
                        }
                    }
                    if (pair.size == 1) Spacer(Modifier.weight(1f))
                }
            }
        }
    }
}

/** Your streaming: the time counting up, last streamed, HowLongToBeat with the share streamed. */
@Composable
private fun StreamingSection(
    app: App, pc: Pc, playtime: Playtime, resumes: Int, hltbSeconds: Long?, progress: () -> Float,
    parts: HashMap<String, PartFocus>, onFocused: (String) -> Unit, onHover: () -> Unit,
) {
    val colors = LocalDelosColors.current
    val res = textResources()
    val k = stageUnit()
    val section = OverviewModel.Section.STREAMING
    val texts = rememberPlaytimeTexts(playtime, resumes)
    val bigStyle = TextStyle(color = colors.text, fontFamily = Unbounded, fontWeight = FontWeight.Bold, fontSize = 54.tpx)
    val noteStyle = TextStyle(color = colors.text3, fontFamily = Figtree, fontSize = 19.tpx)
    Column(Modifier.padding(horizontal = SIDE.px).overviewSection(section.name)) {
        SectionHeading(Symbols.OVERVIEW_STREAMING, stringResource(R.string.overview_section_streaming))
        Row(
            Modifier
                .fillMaxWidth()
                .part("streaming", section, parts, onFocused, onHover, onClick = {}, ring = RingStyle(8f, 34f), corner = 34f)
                .overviewGlass(LocalBackdropHaze.current, 34f)
                .padding(horizontal = 40.px, vertical = 36.px),
            verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(48.px),
        ) {
            Column(Modifier.weight(1f)) {
                OverviewEyebrow(texts.eyebrowTime, colors.accent)
                Row(verticalAlignment = Alignment.Bottom) {
                    val amount = PlaytimeFormat.amount(playtime.totalMillis)
                    val value = if (amount.unit == PlaytimeFormat.Amount.Unit.HOURS) amount.tenths / 10.0 else amount.tenths.toDouble()
                    CountText(texts.full.number, value, bigStyle, progress, Modifier.alignByBaseline())
                    BasicText(
                        texts.full.unit, Modifier.padding(start = 10.px).alignByBaseline(),
                        style = TextStyle(color = colors.text2, fontFamily = Figtree, fontWeight = FontWeight.SemiBold, fontSize = 24.tpx),
                    )
                }
                BasicText(stringResource(R.string.overview_streaming_note), Modifier.padding(top = 14.px), style = noteStyle)
            }
            Column(Modifier.weight(1f)) {
                OverviewEyebrow(texts.eyebrowLast, colors.text3)
                BasicText(texts.last, maxLines = 1, style = bigStyle.copy(fontSize = 40.tpx))
                BasicText(if (app.running) stringResource(R.string.overview_running_on, pc.name) else "\u00a0", Modifier.padding(top = 14.px), style = noteStyle)
            }
            if (hltbSeconds != null && hltbSeconds > 0) Column(Modifier.weight(1f)) {
                OverviewEyebrow(stringResource(R.string.overview_hltb_all), colors.text3)
                BasicText(completionText(res, hltbSeconds), maxLines = 1, style = bigStyle.copy(fontSize = 40.tpx))
                val share = OverviewModel.hltbShare(playtime.totalMillis, hltbSeconds) ?: 0
                val accent = colors.accent
                val accent2 = colors.accent2
                Box(
                    Modifier.padding(top = 18.px).fillMaxWidth().height(10.px).clip(RoundedCornerShape(5.px)).background(Color.White.copy(alpha = .1f))
                        .drawBehind {
                            val w = size.width * share / 100f * progress()
                            if (w > 0f) drawRoundRect(
                                Brush.horizontalGradient(listOf(accent, accent2), endX = size.width),
                                size = size.copy(width = w), cornerRadius = androidx.compose.ui.geometry.CornerRadius(5f * k),
                            )
                        },
                )
                BasicText(
                    stringResource(R.string.overview_hltb_share, "${texts.full.number} ${texts.full.unit}", share),
                    Modifier.padding(top = 14.px), style = noteStyle,
                )
            }
        }
    }
}

/** Links: IGDB's websites (or the Steam page), each opening in the browser. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun LinksSection(
    links: List<OverviewModel.Link>, parts: HashMap<String, PartFocus>, onFocused: (String) -> Unit, onHover: () -> Unit, onOpen: (String) -> Unit,
) {
    val colors = LocalDelosColors.current
    val section = OverviewModel.Section.LINKS
    Column(Modifier.padding(horizontal = SIDE.px).overviewSection(section.name)) {
        SectionHeading(Symbols.OVERVIEW_LINKS, stringResource(R.string.overview_section_links))
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(OverviewGaps.LINKS_ACROSS.gap.px),
            verticalArrangement = Arrangement.spacedBy(OverviewGaps.LINKS_DOWN.gap.px),
        ) {
            links.forEachIndexed { index, link ->
                var focused by remember { mutableStateOf(false) }
                Row(
                    Modifier
                        .part("link$index", section, parts, onFocused, onHover, onClick = { onOpen(link.url) }, ring = RingStyle(), corner = 999f,
                            focusScale = OverviewGaps.LINK_SCALE, group = "links", locked = true, onFocusChange = { focused = it })
                        .height(OverviewGaps.LINK_H.px).clip(CircleShape).glass(Color.White.copy(alpha = .06f)).border(1.px, colors.line, CircleShape)
                        .padding(horizontal = 24.px),
                    verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.px),
                ) {
                    val icon = when (link.kind) {
                        OverviewModel.LinkKind.OFFICIAL -> Symbols.WEBSITE_OFFICIAL
                        OverviewModel.LinkKind.STEAM -> Symbols.STEAM
                        OverviewModel.LinkKind.STORE -> Symbols.WEBSITE_STORE
                        OverviewModel.LinkKind.WIKI -> Symbols.WEBSITE_WIKI
                        OverviewModel.LinkKind.VIDEO -> Symbols.WEBSITE_VIDEO
                        OverviewModel.LinkKind.OTHER -> Symbols.OVERVIEW_LINKS
                    }
                    Symbol(icon, 28.tpx, colors.accent2, filled = focused)
                    BasicText(link.name ?: stringResource(R.string.overview_official_site), maxLines = 1,
                        style = TextStyle(color = colors.text, fontFamily = Figtree, fontWeight = FontWeight.SemiBold, fontSize = 21.tpx))
                    Symbol(Symbols.LINK_OPEN_EXTERNAL, 20.tpx, colors.text3)
                }
            }
        }
    }
}

/** Similar games: the library card for one the user owns (it opens its own overview), IGDB's cover for the others. */
@Composable
private fun SimilarSection(
    details: GameDetails, pc: Pc, library: List<App>, row: LazyListState, parts: HashMap<String, PartFocus>,
    onFocused: (String) -> Unit, onHover: () -> Unit, coverArt: @Composable (App, Modifier) -> Unit, onOpen: (App?, String) -> Unit,
) {
    val colors = LocalDelosColors.current
    val metadata = LocalGameMetadata.current
    val section = OverviewModel.Section.SIMILAR
    val k = stageUnit()
    Column(Modifier.overviewSection(section.name)) {
        Box(Modifier.padding(horizontal = SIDE.px)) { SectionHeading(Symbols.OVERVIEW_SIMILAR, stringResource(R.string.overview_section_similar)) }
        LazyRow(
            state = row,
            // The edge fade's layer reaches 8 px further up and down: room for the focus growth (6 %) and the ring of the 260 × 390 covers
            modifier = Modifier.fillMaxWidth().overdraw(0.dp, 8.px).anchorRowFade(LocalStage.current, row),
            contentPadding = PaddingValues(start = SIDE.px, end = SIDE.px, top = 24.px, bottom = 38.px),
            horizontalArrangement = Arrangement.spacedBy(OverviewGaps.SIMILAR.gap.px),
        ) {
            itemsIndexed(details.similarGames, key = { _, game -> game.id }) { index, game ->
                val owned = remember(game, library) {
                    OverviewModel.owned(game, library, { metadata.entry(pc, it).data?.candidate?.id }, ::matchKey, { it.name })
                }
                Box(
                    Modifier
                        .part(
                            "similar$index", section, parts, onFocused, onHover, onClick = { onOpen(owned, game.name) },
                            ring = RingStyle(OverviewGaps.SIMILAR_PAD, SIMILAR_CORNER), corner = SIMILAR_CORNER, focusScale = OverviewGaps.SIMILAR_SCALE,
                            group = "similar", locked = true, track = row, index = index,
                        )
                        .size(OverviewGaps.SIMILAR_W.px, OverviewGaps.SIMILAR_H.px)
                        .clip(RoundedCornerShape(SIMILAR_CORNER.px))
                        .background(colors.surface2),
                ) {
                    // The picture is the blur source of the "In library" badge over it
                    val art = remember { HazeState() }
                    Box(Modifier.matchParentSize().hazeSource(art)) {
                        if (owned != null) coverArt(owned, Modifier.fillMaxSize())
                        else {
                            val image by rememberIgdbImage(game.coverImageId?.let { IgdbImageUrls.image(it, IgdbImageSize.COVER_BIG) }, (OverviewGaps.SIMILAR_W * OverviewGaps.SIMILAR_SCALE * k).toInt())
                            val bitmap = image
                            if (bitmap != null) Image(bitmap, game.name, Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
                            else {
                                val hue = ((game.name.hashCode() and 0x7fffffff) % 360).toFloat()
                                PlaceholderArt(hue, (hue + 35f) % 360f, Symbols.SPORTS_ESPORTS, Modifier.fillMaxSize(), glyphSize = 286f, title = game.name, titleSize = 26f, titleInset = 20f, titleBottom = 20f, titleFit = true)
                            }
                        }
                    }
                    if (owned != null) {
                        BasicText(
                            stringResource(R.string.overview_in_library),
                            Modifier.padding(12.px).clip(CircleShape).backdropBlur(art, 16f, Color(5, 10, 20).copy(alpha = .6f)).padding(horizontal = 10.px, vertical = 4.px),
                            style = TextStyle(color = colors.accent, fontFamily = Figtree, fontWeight = FontWeight.Bold, fontSize = 15.tpx),
                        )
                    }
                }
            }
        }
    }
}

/** A similar game's cover corner (its size: [OverviewGaps.SIMILAR_W] × [OverviewGaps.SIMILAR_H], 1.3 times the first design's 200 × 300). */
private const val SIMILAR_CORNER = 24f

/** Room above and below the carousel's pictures inside its edge fade's layer: their focus growth (6 %) and ring. */
private const val TRACK_ROOM = 24f

/**
 * The header cover this frame ([OverviewMotion.cover], [OverviewMotion.fillScale]): one scale and the translation of its top-left
 * (its own pixels), the frame it shows in its own unscaled pixels with the clip of that frame, its opacity (reduced motion fades it)
 * and how much of the row's card shows over it. Read while drawing; only a moving frame makes a new clip.
 */
private class CoverPose {
    var scale = 1f
        private set
    var x = 0f
        private set
    var y = 0f
        private set
    var alpha = 1f
        private set
    var card = 0f
        private set
    var frameLeft = 0f
        private set
    var frameTop = 0f
        private set
    var frameWidth = 0f
        private set
    var frameHeight = 0f
        private set
    var shape: Shape = RectangleShape
        private set
    private var rest: Shape? = null

    /**
     * The cover's slot on screen (root pixels), measured when it is placed. State: the layer that drew the overview's first frame before
     * the slot was known draws again with it.
     */
    var slot by mutableStateOf<Rect?>(null)

    /** The pose of a cover of [w] × [h] in its [slot], pulled down [pulled] pixels by a finger at the top of the page. */
    fun update(controller: OverviewController, w: Float, h: Float, pulled: Float, k: Float): CoverPose {
        scale = 1f
        x = 0f
        y = 0f
        alpha = 1f
        card = 0f
        frameLeft = 0f
        frameTop = 0f
        frameWidth = w
        frameHeight = h
        val from = controller.from
        val to = slot
        val moving = controller.opening || controller.closing
        if (controller.reduced) {
            alpha = when {
                controller.closing -> 1f - Motion.progress(controller.clock.value, 0, Motion.FADE, Motion.Ease)
                controller.opening -> Motion.progress(controller.clock.value, 0, Motion.FADE, Motion.Ease)
                else -> 1f
            }
        } else if (moving && from != null && to != null && to.width > 0f && w > 0f) {
            val p = controller.flight.value
            val f = OverviewMotion.cover(p, from, to, OverviewMotion.ARC * k)
            // Screen pixels in the cover's own (1 unless something above the page scales it)
            val r = w / to.width
            val fw = f.rect.width * r
            val fh = f.rect.height * r
            val s = OverviewMotion.fillScale(fw, fh, w, h)
            scale = s
            x = f.rect.left * r + (fw - w * s) / 2f
            y = f.rect.top * r + (fh - h * s) / 2f
            frameLeft = (f.rect.left * r - x) / s
            frameTop = (f.rect.top * r - y) / s
            frameWidth = fw / s
            frameHeight = fh / s
            card = OverviewMotion.cardShown(p)
            shape = FrameShape(frameLeft, frameTop, frameWidth, frameHeight, f.corner * k * r / s)
            return this
        } else if (moving && from != null) {
            // The slot is not known yet (the overview's first layout): the card on the dashboard stays until the flight can start from it
            alpha = 0f
        } else {
            // Pulled down by a finger at the top, the cover shrinks a little
            scale = 1f - min(pulled / k, 300f) / 2400f
        }
        shape = rest ?: RoundedCornerShape(OverviewMotion.TO_CORNER * k).also { rest = it }
        return this
    }
}

/**
 * The glow of the flying focus ring (an 8 px stroke blurred 30 px, [k] pixels per stage pixel), blurred once into a small nine-slice
 * picture and stretched to the frame on every frame: a blur filter on a shape that changes every frame would be rasterized again on
 * every one. Its corner is the header's (the card's is 2.6 px smaller, invisible under the blur).
 */
private class FlightGlow(k: Float) {
    /** Room outside and inside the stroke for the blur, the corner, and the corner slice that holds the corner and both rooms. */
    private val room = kotlin.math.ceil(2f * 30f * k + 4f * k).toInt()
    private val corner = OverviewMotion.TO_CORNER * k
    private val slice = 2 * room + kotlin.math.ceil(corner).toInt()
    private val side = 2 * slice + 1
    private val bitmap = android.graphics.Bitmap.createBitmap(side, side, android.graphics.Bitmap.Config.ALPHA_8).also {
        android.graphics.Canvas(it).drawRoundRect(
            room.toFloat(), room.toFloat(), (side - room).toFloat(), (side - room).toFloat(), corner, corner,
            android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply {
                style = android.graphics.Paint.Style.STROKE
                strokeWidth = 8f * k
                maskFilter = android.graphics.BlurMaskFilter(30f * k, android.graphics.BlurMaskFilter.Blur.NORMAL)
            },
        )
    }
    private val paint = android.graphics.Paint(android.graphics.Paint.FILTER_BITMAP_FLAG)
    private val src = android.graphics.Rect()
    private val dst = android.graphics.RectF()
    private val cuts = intArrayOf(0, slice, slice + 1, side)
    private val xs = FloatArray(4)
    private val ys = FloatArray(4)

    /** The glow around the frame [left]..[bottom] (the stroke's centre line) in [color]. */
    fun draw(canvas: android.graphics.Canvas, left: Float, top: Float, right: Float, bottom: Float, color: Int) {
        paint.color = color
        val l = left - room
        val t = top - room
        val r = right + room
        val b = bottom + room
        // A frame narrower than both corner slices would need them overlapping: none of the covers' frames is
        xs[0] = l; xs[1] = l + slice; xs[2] = max(l + slice, r - slice); xs[3] = r
        ys[0] = t; ys[1] = t + slice; ys[2] = max(t + slice, b - slice); ys[3] = b
        for (i in 0 until 3) for (j in 0 until 3) {
            src.set(cuts[i], cuts[j], cuts[i + 1], cuts[j + 1])
            dst.set(xs[i], ys[j], xs[i + 1], ys[j + 1])
            canvas.drawBitmap(bitmap, src, dst, paint)
        }
    }
}

/** A rounded frame at a place inside the cover (its own pixels): the clip of the cover in flight. */
private class FrameShape(private val left: Float, private val top: Float, private val width: Float, private val height: Float, private val corner: Float) : Shape {
    override fun createOutline(size: Size, layoutDirection: LayoutDirection, density: Density): Outline =
        Outline.Rounded(RoundRect(left, top, left + width, top + height, CornerRadius(corner)))
}

/**
 * The page's backdrop keeps the dashboard's hero ([BackdropMirror]), at the user's blur and playing when animated.
 * The scroll crossfades a deeply blurred layer of that hero over it (opacity only, no animated blur radius), drifting
 * slower than the page and zooming a little. Browsing media never changes this source. It supplies the page's glass ([haze]).
 */
@Composable
private fun OverviewBackdrop(controller: OverviewController, hero: GameArt?, scroll: () -> Int, reduced: Boolean, haze: HazeState, deepBlur: Int) {
    val colors = LocalDelosColors.current
    val k = stageUnit()
    val stage = LocalStage.current
    val mirror = LocalBackdropMirror.current
    val anchor = remember { arrayOfNulls<LayoutCoordinates>(1) }
    val area = remember(stage.stageWidth, stage.stageHeight) { HeroArea.of(stage) }
    val bg = colors.bg
    val shadeBottom = remember(bg) { Brush.verticalGradient(0f to Color.Transparent, .42f to Color.Transparent, 1f to bg) }
    val depth = { BackdropDepth.at(scroll() / k, reduced) }
    val enter = {
        when {
            controller.closing -> OverviewMotion.backdrop(controller.clock.value, opening = false)
            controller.opening -> OverviewMotion.backdrop(controller.clock.value, opening = true)
            else -> 1f
        }
    }
    Box(
        Modifier
            .fillMaxSize()
            .onGloballyPositioned { anchor[0] = it }
            .hazeSource(haze)
            // Its opacity applies to each layer as it draws: the background it fades over is the same picture, so no offscreen copy
            // of the whole screen is needed while it fades in and out
            .graphicsLayer { alpha = enter(); compositingStrategy = CompositingStrategy.ModulateAlpha }
            .then(if (mirror == null) Modifier.background(bg) else Modifier),
    ) {
        Box(
            Modifier
                .fillMaxSize()
                .graphicsLayer {
                    val d = depth()
                    translationY = d.offsetY * k
                    scaleX = d.zoom
                    scaleY = d.zoom
                }
                .drawBackdropMirror(mirror) { anchor[0] },
        ) {
            Crossfade(hero, animationSpec = tween(800, easing = Motion.Ease), label = "overviewBackdrop") { art ->
                val bitmap = art?.image
                if (bitmap != null) {
                    val key = remember(art?.id) { "overview/${art?.id}" }
                    val deep by produceState(HeroBitmaps.cached(key, bitmap, deepBlur, area), key, bitmap, deepBlur) {
                        if (value != null) return@produceState
                        // Rendered once the opening has landed: the deep layer is clear at the page's top, and blurring the hero
                        // would take the processor from the flight's frames
                        snapshotFlow { controller.settled }.first { it }
                        value = withContext(Dispatchers.Default) { runCatching { HeroBitmaps.render(key, bitmap, deepBlur, area) }.getOrNull() }
                    }
                    Box(Modifier.fillMaxSize()) {
                        // Without the dashboard mirror, draw this same hero below its deep layer.
                        if (mirror == null) {
                            Box(Modifier.fillMaxSize().background(bg))
                            Image(
                                bitmap, null, contentScale = ContentScale.Crop,
                                modifier = Modifier.requiredSize(area.width.px, area.height.px).align(Alignment.Center).graphicsLayer { alpha = depth().sharp },
                            )
                        }
                        deep?.let {
                            Image(
                                it, null, contentScale = ContentScale.Crop, colorFilter = DeepFilter,
                                modifier = Modifier.requiredSize(area.width.px, area.height.px).align(Alignment.Center).graphicsLayer { alpha = depth().deep },
                            )
                        }
                    }
                }
            }
        }
        // The shades under the page's text: the bottom one, and the left one under the header, which only begins below the top bar
        // (the bar, the brand and the PC keep the backdrop as it is: no band and no vignette behind them, owner 2026-11-20)
        Box(Modifier.fillMaxSize().drawWithCache {
            val left = android.graphics.LinearGradient(0f, 0f, size.width * .38f, 0f, bg.toArgb(), 0, android.graphics.Shader.TileMode.CLAMP)
            val below = android.graphics.LinearGradient(0f, SHADE_TOP * k, 0f, SHADE_SOLID * k, 0, android.graphics.Color.BLACK, android.graphics.Shader.TileMode.CLAMP)
            val paint = android.graphics.Paint().apply { shader = android.graphics.ComposeShader(left, below, android.graphics.PorterDuff.Mode.DST_IN) }
            onDrawBehind {
                val a = depth().shade * .7f
                drawRect(shadeBottom, alpha = a)
                paint.alpha = (a * 255f).roundToInt()
                drawContext.canvas.nativeCanvas.drawRect(0f, 0f, size.width, size.height, paint)
            }
        })
    }
}

/** The backdrop's left shade starts under the top bar at [SHADE_TOP] and is whole from [SHADE_SOLID] down (stage pixels). */
private const val SHADE_TOP = 140f
private const val SHADE_SOLID = 260f

/** How far up the storyline's cut edge fades out while it opens or closes (stage pixels, about two of its lines). */
private const val STORY_FADE = 72f

/** The deepest blur further down the page: 60 % of the former 40 px maximum, never below the dashboard's own blur. */
private const val DEEP_BLUR = 24
private val DeepFilter = ColorFilter.colorMatrix(ColorMatrix().apply {
    setToSaturation(1.35f)
    timesAssign(ColorMatrix(floatArrayOf(.62f, 0f, 0f, 0f, 0f, 0f, .62f, 0f, 0f, 0f, 0f, 0f, .62f, 0f, 0f, 0f, 0f, 0f, 1f, 0f)))
})
