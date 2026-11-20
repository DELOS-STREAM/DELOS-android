package com.limelight.shell.screens

import android.content.Intent
import android.net.Uri
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloatAsState
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
import androidx.compose.foundation.gestures.animateScrollBy
import androidx.compose.foundation.gestures.scrollBy
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
import androidx.compose.ui.layout.LayoutCoordinates
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
import com.limelight.shell.data.LocalIgdbImages
import com.limelight.shell.data.NativeHdr
import com.limelight.shell.data.Pc
import com.limelight.shell.data.Playtime
import com.limelight.shell.data.PlaytimeFormat
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

/** The page's sides (`.a-page`: padding 0 150px), the header's top and its column gap, the cover's size, in stage pixels. */
private const val SIDE = 150f
private const val HEAD_TOP = 150f
private const val COVER_W = 340f
private const val COVER_H = 510f
private const val COVER_GAP = 72f
private const val TITLE_SIZE = 68f
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
 * [controller] runs the motion; [cover] is exactly the library card's image (the user's SteamGridDB choice, else the default card
 * art); [library] is every app of the PC, for similar games the user owns. [onClosed] runs once the card is back in its row.
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
    playtime: Playtime,
    resumes: Int,
    reducedMotion: Boolean,
    onPlay: () -> Unit,
    onSettings: () -> Unit,
    onJump: (App) -> Unit,
    onClosed: () -> Unit,
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
    val viewerHaze = remember { HazeState() }
    var viewer by remember { mutableStateOf<ViewerRequest?>(null) }
    var backdropShot by remember { mutableStateOf<String?>(null) }
    var storyOpen by remember(app.id) { mutableStateOf(false) }
    var hovered by remember { mutableStateOf(false) }
    var root by remember { mutableStateOf<LayoutCoordinates?>(null) }
    val kind = ControllerGlyphs.kind
    val reduced by rememberUpdatedState(reducedMotion)

    val screenshotSize = if (stage.stageHeight > 1200f) IgdbImageSize.HD_1080 else IgdbImageSize.SCREENSHOT_HUGE
    val media = remember(details, screenshotSize) { details?.let { OverviewModel.media(it, screenshotSize) }.orEmpty() }
    val links = remember(details, candidate) { details?.let { OverviewModel.links(it, candidate?.steamAppIds.orEmpty()) }.orEmpty() }
    val sections = remember(details, links, media) { OverviewModel.sections(details, links, media) }

    // The first two thumbnails and the first full screenshot load while the overview opens
    val images = LocalIgdbImages.current
    LaunchedEffect(media) {
        if (media.isNotEmpty()) images.prefetch(media.take(2).map { it.thumb } + listOfNotNull(media.firstOrNull { it.kind == OverviewModel.MediaKind.SCREENSHOT }?.full))
    }

    // Closing: the page scrolls back to its top first, then every motion reverses
    val close: () -> Unit = {
        if (!controller.leaving) {
            feedback.play(Cue.BACK)
            focus.alpha.let { a -> scope.launch { a.animateTo(0f, tween(200)) } }
            controller.close(
                prepare = { if (scroll.value > 1) scroll.animateScrollTo(0, tween(if (reduced) 0 else 420, easing = OverviewMotion.InOut)) },
                done = onClosed,
            )
        }
    }
    val currentClose by rememberUpdatedState(close)
    // Back while the overview is already leaving is swallowed: it must not reach the dashboard (and leave the app)
    BackHandler(enabled = controller.app != null) {
        if (viewer != null) viewer = viewer?.copy(closing = true) else currentClose()
    }
    LaunchedEffect(viewer != null, viewer?.index) {
        shell.overviewViewer = viewer?.let { media.getOrNull(it.index)?.kind == OverviewModel.MediaKind.VIDEO }
    }

    // The ring shows once the overview has settled, never for a finger
    LaunchedEffect(controller.settled, kind) {
        focus.alpha.animateTo(if (controller.settled && kind != InputKind.TOUCH) 1f else 0f, tween(200, easing = Motion.Ease))
    }

    // Ratings fill and count up 380 ms after the opening began (or as soon as the details arrive later)
    val ratings = remember(app.id) { Animatable(if (reducedMotion) 1f else 0f) }
    LaunchedEffect(details != null) {
        if (details == null || ratings.value >= 1f) return@LaunchedEffect
        snapshotFlow { controller.openMillis() >= OverviewMotion.RATINGS_DELAY }.first { it }
        ratings.animateTo(1f, tween(OverviewMotion.COUNT_UP, easing = LinearEasing))
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
        if (part != null && !hover) scope.launch { revealPart(focus, id, part, scroll, k, reduced) }
        if (part?.section == OverviewModel.Section.STREAMING.name && streaming.value < 1f) {
            scope.launch { delay(200); streaming.animateTo(1f, tween(OverviewMotion.COUNT_UP, easing = LinearEasing)) }
        }
        backdropShot = if (part?.section == OverviewModel.Section.MEDIA.name) media.getOrNull(part.index)?.thumb else null
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
        if (controller.closing) OverviewMotion.page(controller.clock.value, opening = false, reduced = controller.reduced)
        else if (controller.opening) OverviewMotion.page(controller.clock.value, opening = true, reduced = controller.reduced)
        else 0f to 1f
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
                val next = focus.navigator.move(from, direction, focus.navTargets(r))
                if (next != null) runCatching { focus.targets[next]?.requester?.requestFocus() }
                // Up from the header goes back to the dashboard, the natural reverse of Down
                else if (direction == NavDirection.UP && parts[from]?.section == HEAD) currentClose()
                true
            },
    ) {
        Box(Modifier.fillMaxSize().hazeSource(viewerHaze)) {
            OverviewBackdrop(controller, shell.hero, backdropShot, scroll::value, reducedMotion, pageHaze)
            CompositionLocalProvider(LocalOverviewFocus provides focus, LocalBackdropHaze provides pageHaze, LocalBringIntoViewSpec provides OverviewNoAutoScroll) {
                Box(
                    Modifier
                        .fillMaxSize()
                        // The page fades in under the status bar (`.a-view` mask: clear to 70 px, solid from 140 px, clear again at the bottom)
                        .graphicsLayer { compositingStrategy = CompositingStrategy.Offscreen }
                        .drawWithContent {
                            drawContent()
                            val h = size.height
                            drawRect(
                                Brush.verticalGradient(
                                    0f to Color.Transparent, 70f * k / h to Color.Transparent, 140f * k / h to Color.Black,
                                    1f - 60f * k / h to Color.Black, 1f to Color.Transparent,
                                ),
                                blendMode = BlendMode.DstIn,
                            )
                        }
                        .nestedScroll(pullConnection)
                        .verticalScroll(scroll),
                ) {
                    Column(
                        Modifier
                            .fillMaxWidth()
                            .onGloballyPositioned { focus.host = it }
                            .graphicsLayer { translationY = pull * .45f }
                            .drawWithContent {
                                drawContent()
                                // The carousels scroll under the ring: reading their state redraws it with them
                                track.firstVisibleItemScrollOffset; similarRow.firstVisibleItemScrollOffset
                                with(focus) { drawRing(colors, k) }
                            },
                    ) {
                        Header(
                            controller, pc, app, details, data?.hdr?.support, data?.completion?.seconds, candidate,
                            art, cover, playtime, resumes, sections, ratings::value, pageMotion, playRequester,
                            onPlay, onSettings, coverArt, pull = { pull },
                            register = { id -> parts[id] = PartFocus(HEAD) }, onFocused = onPartFocused, onHover = { hovered = true },
                        )
                        Column(Modifier.graphicsLayer { val (dy, a) = pageMotion(); translationY = dy * k; alpha = a }) {
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
                                        if (owned != null) onJump(owned)
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
                media, request, viewerHaze, reducedMotion,
                thumbRect = { index -> thumbRect(focus, index, root) },
                onIndex = { index ->
                    viewer = request.copy(index = index)
                    backdropShot = media.getOrNull(index)?.thumb
                    scope.launch { track.animateScrollToItem(index) }
                },
                onWatch = { videoId ->
                    runCatching { context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(IgdbImageUrls.youtubeWatch(videoId))).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
                },
                onClosed = {
                    val index = viewer?.index ?: request.index
                    viewer = null
                    focus.targets["shot$index"]?.requester?.let { runCatching { it.requestFocus() } }
                },
                onCloseRequest = { viewer = viewer?.copy(closing = true) },
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
    val track = part.track
    if (track != null) {
        val info = track.layoutInfo.visibleItemsInfo.firstOrNull { it.index == part.index }
        val width = track.layoutInfo.viewportSize.width.toFloat()
        if (info != null && width > 0f) {
            val margin = OverviewScroll.TRACK_MARGIN * k
            val delta = OverviewScroll.reveal(0f, info.offset.toFloat(), (info.offset + info.size).toFloat(), width, margin)
            if (abs(delta) > 1f) kotlinx.coroutines.coroutineScope {
                launch { if (reduced) track.scrollBy(delta) else track.animateScrollBy(delta, tween(560, easing = EaseOut)) }
            }
        }
    }
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
    candidate: com.limelight.shell.data.MetadataCandidate?, art: GameArt, cover: ImageBitmap?, playtime: Playtime, resumes: Int,
    sections: List<OverviewModel.Section>, ratings: () -> Float, pageMotion: () -> Pair<Float, Float>, playRequester: FocusRequester,
    onPlay: () -> Unit, onSettings: () -> Unit, coverArt: @Composable (App, Modifier) -> Unit, pull: () -> Float,
    register: (String) -> Unit, onFocused: (String) -> Unit, onHover: () -> Unit,
) {
    val colors = LocalDelosColors.current
    val stage = LocalStage.current
    val res = textResources()
    val k = stageUnit()
    val metadata = LocalGameMetadata.current.entry(pc, app).data
    val key = homeKey(pc, app)
    val hue = HeroBitmaps.hue(key) ?: art.hue2
    // A staggered part of the header (`.st`): it rises in after the page has begun to move, and fades out first when it closes
    fun Modifier.st(index: Int, delay: Int = OverviewMotion.HEADER_DELAY): Modifier = graphicsLayer {
        val (pageDy, pageAlpha) = pageMotion()
        val (dy, a) = when {
            controller.closing -> OverviewMotion.leave(controller.clock.value, controller.reduced)
            controller.opening -> OverviewMotion.rise(controller.clock.value, delay, index, controller.reduced)
            else -> 0f to 1f
        }
        translationY = (dy + pageDy) * k
        alpha = a * pageAlpha
    }
    Box(Modifier.fillMaxWidth().height((stage.stageHeight - 160f).px).overviewSection(HEAD)) {
        Row(Modifier.padding(start = SIDE.px, top = HEAD_TOP.px, end = SIDE.px)) {
            // The cover: the card's own image, flying in from the card and back ([OverviewMotion.cover])
            var coverCoordinates by remember { mutableStateOf<LayoutCoordinates?>(null) }
            Box(
                Modifier
                    .size(COVER_W.px, COVER_H.px)
                    .onGloballyPositioned { coverCoordinates = it }
                    .graphicsLayer {
                        transformOrigin = TransformOrigin(0f, 0f)
                        val pulled = pull()
                        val from = controller.from
                        val to = coverCoordinates?.takeIf { it.isAttached }?.let { c ->
                            c.rootRect()
                        }
                        val moving = controller.opening || controller.closing
                        if (controller.reduced) {
                            alpha = when {
                                controller.closing -> 1f - Motion.progress(controller.clock.value, 0, Motion.FADE, Motion.Ease)
                                controller.opening -> Motion.progress(controller.clock.value, 0, Motion.FADE, Motion.Ease)
                                else -> 1f
                            }
                        } else if (moving && from != null && to != null && to.width > 0f) {
                            val p = OverviewMotion.flight(controller.clock.value, opening = !controller.closing)
                            val frame = OverviewMotion.cover(p, from, to, OverviewMotion.ARC * k)
                            translationX = frame.offset.x
                            translationY = frame.offset.y
                            scaleX = frame.scaleX
                            scaleY = frame.scaleY
                            shape = RoundedCornerShape(frame.corner * k / 1f)
                        } else {
                            // Pulled down by a finger at the top, the cover shrinks a little
                            val s = 1f - min(pulled / k, 300f) / 2400f
                            scaleX = s
                            scaleY = s
                            shape = RoundedCornerShape(OverviewMotion.TO_CORNER * k)
                        }
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
                Box(Modifier.fillMaxSize().border(1.px, Color.White.copy(alpha = .08f), RoundedCornerShape(OverviewMotion.TO_CORNER.px)))
            }
            Spacer(Modifier.width(COVER_GAP.px))
            Column(Modifier.padding(top = 4.px), verticalArrangement = Arrangement.spacedBy(24.px)) {
                val year = details?.firstReleaseDate?.let(OverviewModel::year) ?: candidate?.year
                val developers = details?.let(OverviewModel::developers).orEmpty()
                val eyebrow = listOfNotNull(year?.toString(), developers.joinToString(", ").takeIf { it.isNotEmpty() })
                    .reduceOrNull { a, b -> res.getString(R.string.home_meta_join, a, b) }
                OverviewEyebrow(eyebrow ?: "\u00a0", colors.accent, Modifier.st(0))
                Title(controller, app.name, hue, pageMotion)
                // Up to two genres and two themes (as on the dashboard), then HDR
                val genres = remember(metadata) { topGenres(candidate?.genres.orEmpty(), metadata?.steamTags.orEmpty()) }
                val themes = remember(metadata) { topThemes(candidate?.themes.orEmpty(), metadata?.steamTags.orEmpty()) }
                FlowRow(Modifier.st(1), horizontalArrangement = Arrangement.spacedBy(10.px), verticalArrangement = Arrangement.spacedBy(10.px)) {
                    genres.forEach { OverviewTag(genreIcon(it.id), genreName(res, it), TagLook.GENRE, hue) }
                    themes.forEach { OverviewTag(themeIcon(it.id), themeName(res, it)) }
                    when (hdr) {
                        NativeHdr.HDR -> NativeHdrChip()
                        NativeHdr.SDR -> Chip(Symbols.SDR, "SDR")
                        NativeHdr.UNKNOWN -> Chip(Symbols.HDR_UNKNOWN, stringResource(R.string.metadata_hdr_unverified))
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
                        colors.accent, ratings,
                    )
                    RatingRing(
                        details?.rating, stringResource(R.string.overview_players),
                        when {
                            details?.rating == null -> stringResource(R.string.overview_not_rated_yet)
                            details.ratingCount < OverviewModel.FEW_RATINGS -> pluralStringResource(R.plurals.overview_ratings_few, details.ratingCount, count.format(details.ratingCount))
                            else -> pluralStringResource(R.plurals.overview_ratings, details.ratingCount, count.format(details.ratingCount))
                        },
                        colors.accent2, ratings,
                    )
                    if (details != null) {
                        val country = java.util.Locale.getDefault().country
                        AgeRatingView(
                            OverviewModel.ageRatings(details.ageRatings, country).firstOrNull(),
                            stringResource(R.string.overview_not_rated), stringResource(R.string.overview_no_age_rating), stringResource(R.string.overview_no_descriptors),
                        )
                    }
                }
                Row(Modifier.st(3), horizontalArrangement = Arrangement.spacedBy(20.px)) {
                    OverviewButton("play", Symbols.PLAY_ARROW, stringResource(R.string.overview_play), primary = true, playRequester, onPlay, register, onFocused, onHover)
                    OverviewButton("settings", Symbols.SETTINGS, stringResource(R.string.home_menu_settings), primary = false, remember { FocusRequester() }, onSettings, register, onFocused, onHover)
                }
                Row(Modifier.st(4), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(36.px)) {
                    val texts = rememberPlaytimeTexts(playtime, resumes)
                    StatRow(texts, PlaytimeFit.FULL, 45.px)
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

/** The title (68 px Unbounded in the game's colors), flying from the dashboard's title when that was text ([OverviewController.title]). */
@Composable
private fun Title(controller: OverviewController, name: String, hue: Float, pageMotion: () -> Pair<Float, Float>) {
    val colors = LocalDelosColors.current
    val k = stageUnit()
    var coordinates by remember { mutableStateOf<LayoutCoordinates?>(null) }
    BasicText(
        name,
        modifier = Modifier
            .widthIn(max = 1150.px)
            .onGloballyPositioned { coordinates = it }
            .graphicsLayer {
                transformOrigin = TransformOrigin(0f, 0f)
                val source = controller.title
                val moving = controller.opening || controller.closing
                val c = coordinates
                if (source != null && moving && c != null && c.isAttached && !controller.reduced) {
                    val to = c.rootRect()
                    val p = OverviewMotion.title(controller.clock.value, opening = !controller.closing)
                    val scale = source.fontSize / TITLE_SIZE
                    val dx = source.rect.left - to.left
                    val dy = source.rect.top - to.top + (source.rect.height - to.height * scale) / 2f
                    translationX = dx * (1f - p)
                    translationY = dy * (1f - p)
                    val s = Motion.lerp(scale, 1f, p)
                    scaleX = s
                    scaleY = s
                } else {
                    // A logo on the dashboard (or reduced motion): the title enters and leaves like the header's parts
                    val (pageDy, pageAlpha) = pageMotion()
                    val (dy, a) = when {
                        controller.closing -> OverviewMotion.leave(controller.clock.value, controller.reduced)
                        controller.opening && source == null -> OverviewMotion.rise(controller.clock.value, OverviewMotion.HEADER_DELAY, 0, controller.reduced)
                        controller.opening -> 0f to Motion.progress(controller.clock.value, 0, Motion.FADE, Motion.Ease)
                        else -> 0f to 1f
                    }
                    translationY = (dy + pageDy) * k
                    alpha = a * pageAlpha
                }
            },
        style = TextStyle(
            brush = Brush.verticalGradient(.3f to colors.text, 1f to hsl(hue, 70f, 68f)),
            fontFamily = Unbounded, fontWeight = FontWeight.Bold, fontSize = TITLE_SIZE.tpx, lineHeight = 1.06.em, letterSpacing = (-.01).em,
            lineBreak = LineBreak.Heading,
            shadow = Shadow(Color.Black.copy(alpha = .4f), Offset(0f, 6f * k), 28f * k),
        ),
    )
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

/** A header button (`.btn`): 76 px pill, Play in the accent. */
@Composable
private fun OverviewButton(
    id: String, icon: String, label: String, primary: Boolean, requester: FocusRequester, onClick: () -> Unit,
    register: (String) -> Unit, onFocused: (String) -> Unit, onHover: () -> Unit,
) {
    val colors = LocalDelosColors.current
    LaunchedEffect(id) { register(id) }
    var focused by remember { mutableStateOf(false) }
    Row(
        Modifier
            .overviewTarget(id, requester, HEAD, group = "buttons", locked = true)
            .hoverFocus(requester, onHover)
            .focusEffects(
                // A finger sees no focus look (focus first still applies): no ring, no growth
                onClick = onClick, corner = 999f, focusScale = if (ControllerGlyphs.kind == InputKind.TOUCH) 1f else 1.06f, ring = null, glow = null, focusRequester = requester,
                onFocusChange = { focused = it; if (it) onFocused(id) }, onTouchTap = { it },
            )
            .height(76.px)
            .clip(CircleShape)
            .background(if (primary) colors.accent else colors.surface)
            .then(if (primary) Modifier else Modifier.border(1.px, colors.line, CircleShape))
            .padding(horizontal = 38.px),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.px),
    ) {
        val ink = if (primary) colors.onAccent else colors.text
        Symbol(icon, 32.tpx, ink, filled = focused)
        BasicText(label, maxLines = 1, style = TextStyle(color = ink, fontFamily = Figtree, fontWeight = FontWeight.Bold, fontSize = 26.tpx))
    }
}

/** A focusable part of a section: registers, scales on focus and reports it. */
@Composable
private fun Modifier.part(
    id: String, section: OverviewModel.Section, parts: HashMap<String, PartFocus>, onFocused: (String) -> Unit, onHover: () -> Unit,
    onClick: () -> Unit, ring: RingStyle, corner: Float, focusScale: Float = 1f, group: String? = null, locked: Boolean = false,
    track: LazyListState? = null, index: Int = 0, onFocusChange: (Boolean) -> Unit = {},
): Modifier {
    val requester = remember(id) { FocusRequester() }
    LaunchedEffect(id, track, index) { parts[id] = PartFocus(section.name, track, index) }
    return this
        .overviewTarget(id, requester, section.name, group, locked, ring)
        .hoverFocus(requester, onHover)
        .focusEffects(
            onClick = onClick, corner = corner, focusScale = if (ControllerGlyphs.kind == InputKind.TOUCH) 1f else focusScale, ring = null, glow = null, focusRequester = requester,
            onFocusChange = { onFocusChange(it); if (it) onFocused(id) }, onTouchTap = { it },
        )
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
                    AnimatedVisibility(
                        storyOpen,
                        enter = if (reduced) fadeIn(tween(Motion.FADE)) else expandVertically(tween(600, easing = EaseOut), expandFrom = Alignment.Top),
                        exit = if (reduced) fadeOut(tween(Motion.FADE)) else shrinkVertically(tween(600, easing = EaseOut), shrinkTowards = Alignment.Top),
                    ) {
                        BasicText(
                            story, Modifier.padding(top = 22.px),
                            style = TextStyle(color = colors.text2, fontFamily = Figtree, fontSize = 25.tpx, lineHeight = 1.5.em),
                        )
                    }
                    val turn by animateFloatAsState(if (storyOpen) 180f else 0f, tween(500, easing = com.limelight.shell.ui.EaseSpring), label = "storyCaret")
                    var focused by remember { mutableStateOf(false) }
                    Row(
                        Modifier
                            .padding(top = 26.px)
                            .part("story", section, parts, onFocused, onHover, onClick = {
                                onStory(!storyOpen)
                                // The page settles on the button again once the text has opened or closed
                                scope.launch { delay(620); onFocused("story") }
                            }, ring = RingStyle(), corner = 999f, focusScale = 1.06f, onFocusChange = { focused = it })
                            .height(52.px).clip(CircleShape).background(Color.White.copy(alpha = .06f)).border(1.px, colors.line, CircleShape)
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
                Column(
                    Modifier
                        .width(560.px)
                        .part("facts", section, parts, onFocused, onHover, onClick = {}, ring = RingStyle(8f, 34f), corner = 34f)
                        .overviewGlass(LocalBackdropHaze.current, 34f)
                        .padding(horizontal = 40.px, vertical = 36.px),
                    verticalArrangement = Arrangement.spacedBy(16.px),
                ) {
                    facts.forEach { row ->
                        Row(verticalAlignment = Alignment.Top) {
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
                                colors.text3, Modifier.width(144.px).padding(top = 4.px), size = 17,
                            )
                            val value = row.date?.let { seconds ->
                                DateFormat.getDateInstance(DateFormat.LONG, locale).apply { timeZone = TimeZone.getTimeZone("UTC") }.format(Date(seconds * 1000))
                            } ?: row.value
                            val text = if (row.selfPublished) res.getString(R.string.home_meta_join, value, res.getString(R.string.overview_self_published)) else value
                            BasicText(text, style = TextStyle(color = colors.text, fontFamily = Figtree, fontWeight = FontWeight.SemiBold, fontSize = 21.tpx))
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
            modifier = Modifier.fillMaxWidth().padding(top = 24.px, bottom = 30.px),
            contentPadding = PaddingValues(start = SIDE.px, end = (SIDE + OverviewScroll.TRACK_MARGIN).px),
            horizontalArrangement = Arrangement.spacedBy(26.px),
        ) {
            itemsIndexed(media, key = { _, item -> item.kind.name + item.id }) { index, item ->
                Box(
                    Modifier
                        .part(
                            "shot$index", section, parts, onFocused, onHover,
                            onClick = { onOpen(index, null) },
                            ring = RingStyle(6f, 28f - 6f), corner = 22f, focusScale = 1.06f, group = "track", locked = true, track = track, index = index,
                        )
                        .size(520.px, 292.px)
                        .clip(RoundedCornerShape(22.px))
                        .background(colors.surface2),
                ) {
                    val image by rememberIgdbImage(item.thumb, (520f * 1.06f * k * 1.28f).toInt())
                    image?.let { bitmap ->
                        Image(
                            bitmap, null, contentScale = ContentScale.Crop,
                            modifier = Modifier
                                .requiredSize((520f * 1.28f).px, 292.px)
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
                            FlowRow(horizontalArrangement = Arrangement.spacedBy(if (label == R.string.overview_detail_age) 28.px else 10.px), verticalArrangement = Arrangement.spacedBy(10.px)) { content() }
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
        FlowRow(horizontalArrangement = Arrangement.spacedBy(18.px), verticalArrangement = Arrangement.spacedBy(18.px)) {
            links.forEachIndexed { index, link ->
                var focused by remember { mutableStateOf(false) }
                Row(
                    Modifier
                        .part("link$index", section, parts, onFocused, onHover, onClick = { onOpen(link.url) }, ring = RingStyle(), corner = 999f,
                            focusScale = 1.05f, group = "links", locked = true, onFocusChange = { focused = it })
                        .height(64.px).clip(CircleShape).background(Color.White.copy(alpha = .06f)).border(1.px, colors.line, CircleShape)
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
            modifier = Modifier.fillMaxWidth(),
            contentPadding = PaddingValues(start = SIDE.px, end = SIDE.px, top = 12.px, bottom = 24.px),
            horizontalArrangement = Arrangement.spacedBy(30.px),
        ) {
            itemsIndexed(details.similarGames, key = { _, game -> game.id }) { index, game ->
                val owned = remember(game, library) {
                    OverviewModel.owned(game, library, { metadata.entry(pc, it).data?.candidate?.id }, ::matchKey, { it.name })
                }
                Box(
                    Modifier
                        .part(
                            "similar$index", section, parts, onFocused, onHover, onClick = { onOpen(owned, game.name) },
                            ring = RingStyle(6f, 20f), corner = 20f, focusScale = 1.06f, group = "similar", locked = true, track = row, index = index,
                        )
                        .size(200.px, 300.px)
                        .clip(RoundedCornerShape(20.px))
                        .background(colors.surface2),
                ) {
                    if (owned != null) coverArt(owned, Modifier.fillMaxSize())
                    else {
                        val image by rememberIgdbImage(game.coverImageId?.let { IgdbImageUrls.image(it, IgdbImageSize.COVER_BIG) }, (200f * 1.06f * k).toInt())
                        val bitmap = image
                        if (bitmap != null) Image(bitmap, game.name, Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
                        else {
                            val hue = ((game.name.hashCode() and 0x7fffffff) % 360).toFloat()
                            PlaceholderArt(hue, (hue + 35f) % 360f, Symbols.SPORTS_ESPORTS, Modifier.fillMaxSize(), glyphSize = 220f, title = game.name, titleSize = 22f, titleInset = 16f, titleBottom = 16f, titleFit = true)
                        }
                    }
                    if (owned != null) {
                        BasicText(
                            stringResource(R.string.overview_in_library),
                            Modifier.padding(12.px).clip(CircleShape).background(Color(5, 10, 20).copy(alpha = .6f)).padding(horizontal = 10.px, vertical = 4.px),
                            style = TextStyle(color = colors.accent, fontFamily = Figtree, fontWeight = FontWeight.Bold, fontSize = 15.tpx),
                        )
                    }
                }
            }
        }
    }
}

/**
 * The page's backdrop: the dashboard's hero as a sharp and a deeply blurred layer, crossfaded by the scroll (opacity only, no animated
 * blur radius), drifting slower than the page and zooming a little; a focused screenshot crossfades in instead. It is the blur source
 * of the page's glass ([haze]).
 */
@Composable
private fun OverviewBackdrop(controller: OverviewController, hero: GameArt?, shot: String?, scroll: () -> Int, reduced: Boolean, haze: HazeState) {
    val colors = LocalDelosColors.current
    val k = stageUnit()
    val stage = LocalStage.current
    val area = remember(stage.stageWidth, stage.stageHeight) { HeroArea.of(stage) }
    val shotImage by rememberIgdbImage(shot, (569f * k).toInt())
    val image = (if (shot != null) shotImage else null) ?: hero?.image
    val sourceKey = if (shot != null && shotImage != null) "overview/$shot" else "overview/${hero?.id}"
    val bg = colors.bg
    val shadeBottom = remember(bg) { Brush.verticalGradient(0f to Color.Transparent, .42f to Color.Transparent, 1f to bg) }
    val shadeLeft = remember(bg) { Brush.horizontalGradient(0f to bg, .38f to Color.Transparent) }
    val depth = { BackdropDepth.at(scroll() / k, reduced) }
    val enter = {
        when {
            controller.closing -> OverviewMotion.backdrop(controller.clock.value, opening = false)
            controller.opening -> OverviewMotion.backdrop(controller.clock.value, opening = true)
            else -> 1f
        }
    }
    Box(Modifier.fillMaxSize().hazeSource(haze).graphicsLayer { alpha = enter() }.background(bg)) {
        Box(Modifier.fillMaxSize().graphicsLayer {
            val d = depth()
            translationY = d.offsetY * k
            scaleX = d.zoom
            scaleY = d.zoom
        }) {
            Crossfade(image to sourceKey, animationSpec = tween(800, easing = Motion.Ease), label = "overviewBackdrop") { (bitmap, key) ->
                if (bitmap != null) {
                    val deep by produceState(HeroBitmaps.cached(key, bitmap, DEEP_BLUR, area), key, bitmap) {
                        if (value == null) value = withContext(Dispatchers.Default) { runCatching { HeroBitmaps.render(key, bitmap, DEEP_BLUR, area) }.getOrNull() }
                    }
                    Box(Modifier.fillMaxSize()) {
                        Image(
                            bitmap, null, contentScale = ContentScale.Crop,
                            modifier = Modifier.requiredSize(area.width.px, area.height.px).align(Alignment.Center).graphicsLayer { alpha = depth().sharp },
                        )
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
        Box(Modifier.fillMaxSize().drawBehind {
            val a = depth().shade
            drawRect(shadeBottom, alpha = a)
            drawRect(shadeLeft, alpha = a)
            drawRect(Brush.radialGradient(.55f to Color.Transparent, 1f to Color(4, 8, 16).copy(alpha = .62f), center = Offset(size.width / 2f, size.height * .4f), radius = size.width * .6f))
        })
    }
}

/** The deep layer's blur (`blur(56px)`) and its saturate(1.35) brightness(.62). */
private const val DEEP_BLUR = 40
private val DeepFilter = ColorFilter.colorMatrix(ColorMatrix().apply {
    setToSaturation(1.35f)
    timesAssign(ColorMatrix(floatArrayOf(.62f, 0f, 0f, 0f, 0f, 0f, .62f, 0f, 0f, 0f, 0f, 0f, .62f, 0f, 0f, 0f, 0f, 0f, 1f, 0f)))
})
