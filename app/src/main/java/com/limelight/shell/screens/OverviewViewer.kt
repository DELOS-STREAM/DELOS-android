package com.limelight.shell.screens

import android.os.SystemClock
import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.focusable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.key
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.draw.blur
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.type
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInWindow
import androidx.compose.ui.unit.IntRect
import androidx.compose.ui.unit.round
import com.limelight.shell.LocalShell
import com.limelight.shell.LocalWindowHintBar
import com.limelight.shell.OverviewViewerHints
import com.limelight.shell.data.rememberIgdbImage
import com.limelight.shell.ui.ControllerGlyphs
import com.limelight.shell.ui.Cue
import com.limelight.shell.ui.EaseOut
import com.limelight.shell.ui.InputKind
import com.limelight.shell.ui.LocalFeedback
import com.limelight.shell.ui.LocalStage
import com.limelight.shell.ui.Motion
import com.limelight.shell.ui.backdropBlur
import com.limelight.shell.ui.px
import com.limelight.shell.ui.stageUnit
import dev.chrisbanes.haze.HazeState
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.input.pointer.PointerIcon
import androidx.compose.ui.input.pointer.pointerHoverIcon
import com.limelight.shell.ViewerHintTarget
import dev.chrisbanes.haze.hazeSource
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.abs
import kotlin.math.min
import kotlin.math.max

/** The viewer opened on item [index], zooming out of the thumbnail at [from] (root pixels; null: it fades in); [closing]: Back was pressed. */
internal data class ViewerRequest(val index: Int, val from: Rect?, val closing: Boolean = false)

/**
 * Non-consuming input observation, also called before the shell consumes a global key or pointer event. The overlay hides after 3 s
 * without input unless something holds it ([OverviewViewer]: a paused or ended video, a slider being dragged).
 */
@androidx.compose.runtime.Stable
internal class OverviewViewerInput(now: Long = SystemClock.uptimeMillis()) {
    var controlsVisible by mutableStateOf(true)
        private set
    private var lastInput = now

    /** When input last brought the hidden overlay back: the tap that did it only shows the overlay. */
    var revealedAt = Long.MIN_VALUE / 2
        private set

    fun onInput(now: Long = SystemClock.uptimeMillis()) {
        lastInput = now
        if (!controlsVisible) revealedAt = now
        controlsVisible = true
    }

    /** A tap on the picture hides the overlay unless playback or a slider holds it. */
    fun hide(held: Boolean) {
        if (OverviewViewerDecisions.canHide(held)) controlsVisible = false
    }

    internal fun remaining(now: Long): Long = (3_000L - (now - lastInput)).coerceAtLeast(0L)

    internal fun hideIfIdle(now: Long, held: Boolean = false) {
        if (remaining(now) == 0L) hide(held)
    }
}

/**
 * Full-viewport media. A Steam trailer plays natively inside this tree ([NativeVideoSurface]: the zoom, the browse slide and the dock's
 * glass apply to its moving picture); a YouTube video plays in a window of its own over the shell's ([OverviewPlayerWindow]), never
 * inside the shell's Compose layers, with the thumbnail, the dock and the hint bar composed over it there.
 */
@Composable
internal fun OverviewViewer(
    media: List<OverviewModel.Media>,
    request: ViewerRequest,
    haze: HazeState,
    reduced: Boolean,
    thumbRect: (Int) -> Rect?,
    onIndex: (Int) -> Unit,
    onClosed: () -> Unit,
    onCloseRequest: () -> Unit,
    input: OverviewViewerInput,
    onControlsVisibilityChanged: (Boolean) -> Unit,
) {
    val stage = LocalStage.current
    val feedback = LocalFeedback.current
    val k = stageUnit()
    val fit = remember(stage.stageWidth, stage.stageHeight) { Rect(0f, 0f, stage.stageWidth, stage.stageHeight) }
    // A horizontal scroll's step in pixels (a tilt-wheel notch; a trackpad sends fractions of it)
    val context = androidx.compose.ui.platform.LocalContext.current
    val scrollStep = remember(context) { android.view.ViewConfiguration.get(context).scaledHorizontalScrollFactor }
    val shell = LocalShell.current
    val kind = ControllerGlyphs.kind
    val controls = input
    val visibilityChanged by rememberUpdatedState(onControlsVisibilityChanged)
    var root by remember { mutableStateOf<LayoutCoordinates?>(null) }
    // Where the player's window goes: the viewer, in the shell window's pixels
    var bounds by remember { mutableStateOf(IntRect.Zero) }
    val zoom = remember { Animatable(0f) }
    val scrim = remember { Animatable(0f) }
    val ui = remember { Animatable(0f) }
    var zoomFrom by remember { mutableStateOf(request.from ?: thumbRect(request.index)) }
    var fading by remember { mutableStateOf(zoomFrom == null || reduced) }
    var shown by remember { mutableIntStateOf(request.index) }
    var leaving by remember { mutableStateOf<Int?>(null) }
    var leavingPlayer by remember { mutableStateOf<OverviewVideoPlayer?>(null) }
    var direction by remember { mutableIntStateOf(1) }
    val change = remember { Animatable(1f) }
    var drag by remember { mutableStateOf(Offset.Zero) }
    // A sideways swipe (a finger, a trackpad's two fingers) carries the item with it (pixels) and brings the one beside it in from
    // that side; [sliding] while it lasts, [slidIn] when a finished swipe brought the new item into place already
    val slide = remember { Animatable(0f) }
    var sliding by remember { mutableStateOf(false) }
    var slidIn by remember { mutableStateOf(false) }
    var opened by remember { mutableStateOf(false) }
    val currentRequest by rememberUpdatedState(request)
    val scope = rememberCoroutineScope()
    val dock = remember { DockState(media.getOrNull(shown)?.kind == OverviewModel.MediaKind.VIDEO) }
    val player = remember(shown) {
        media.getOrNull(shown)?.takeIf { it.kind == OverviewModel.MediaKind.VIDEO }?.let { item ->
            item.stream?.let(::NativeVideoPlayer) ?: YouTubeVideoPlayer(item.id)
        }
    }
    val native = player as? NativeVideoPlayer
    val video = player?.takeIf { it.error == null }
    LaunchedEffect(video != null, reduced) {
        dock.morph.animateTo(if (video != null) 1f else 0f, if (reduced) tween(200) else ViewerSpring)
        if (video == null) dock.lastPlayer = null
    }
    val settled = opened && leaving == null && !request.closing
    val thumbnailAlpha by animateFloatAsState(
        if (settled && player?.started == true && player.error == null) 0f else 1f,
        tween(if (reduced) 0 else Motion.FADE), label = "viewerThumbnail",
    )
    // The overlay (dock, close, shades) rises in 420 ms and falls away in 240 ms; with reduced motion it only fades
    val overlayShown = controls.controlsVisible && !request.closing
    val overlay by animateFloatAsState(
        if (overlayShown) 1f else 0f,
        if (overlayShown) tween(420, easing = EaseOut) else tween(Motion.EXIT, easing = Motion.In), label = "viewerOverlay",
    )
    val overlayAlpha = { ui.value * overlay }
    val overlayPresent by remember { derivedStateOf { controls.controlsVisible && !currentRequest.closing || overlay > 0f } }

    LaunchedEffect(Unit) {
        controls.onInput()
        launch { scrim.animateTo(1f, tween(if (reduced) 0 else Motion.SCRIM, easing = Motion.Ease)) }
        launch { delay(if (reduced) 0 else 300); ui.animateTo(1f, tween(if (reduced) 0 else 300, easing = Motion.Ease)) }
        zoom.animateTo(1f, if (fading) tween(Motion.FADE, easing = Motion.Ease) else tween(560, easing = OverviewMotion.Flight))
        opened = true
    }
    // Auto-hide: 3 s without input while a video plays or a screenshot shows; a paused or ended video and a dragged slider hold it
    val hold = (video != null && !video.playing) || dock.dragging
    val currentHold by rememberUpdatedState(hold)
    LaunchedEffect(controls, controls.controlsVisible, hold) {
        visibilityChanged(controls.controlsVisible)
        if (!controls.controlsVisible || hold) return@LaunchedEffect
        controls.onInput()
        while (controls.controlsVisible) {
            delay(controls.remaining(SystemClock.uptimeMillis()).coerceAtLeast(1L))
            controls.hideIfIdle(SystemClock.uptimeMillis())
        }
    }
    DisposableEffect(controls) { onDispose { visibilityChanged(true) } }
    // Preserve shared stops. A disappearing control hands focus to the nearest surviving
    // transport button rather than jumping to the screenshot's default every time.
    LaunchedEffect(shown, video != null, overlayShown) {
        if (!overlayShown) dock.slot = dockDefault(video != null)
        else dock.slot = dockSurviving(dock.slot, video != null)
    }
    // The ring sits on the focused stop at once: it does not glide between the dock's controls (owner, 2026-11-21)
    LaunchedEffect(dock) {
        snapshotFlow { dock.bounds[dock.slot]?.let { ringRect(dock.slot, it, k) } }.collectLatest { target ->
            if (target == null) return@collectLatest
            dock.ringPlaced = true
            dock.ring.snapTo(target)
        }
    }
    LaunchedEffect(request.index) {
        if (request.index == shown) return@LaunchedEffect
        controls.onInput()
        if (OverviewViewerDecisions.swapInPlace(slidIn, request.closing)) {
            // The swipe has the new item in place: the two swap where they are, with nothing more to animate
            direction = if (request.index > shown) 1 else -1
            player?.release()
            shown = request.index
            slide.snapTo(0f)
            change.snapTo(1f)
            slidIn = false
            sliding = false
            return@LaunchedEffect
        }
        direction = if (request.index > shown) 1 else -1
        leavingPlayer?.release()
        leavingPlayer = player
        player?.pause()
        leaving = shown
        change.snapTo(0f)
        shown = request.index
        change.animateTo(1f, if (reduced) tween(200) else ViewerSpring)
        leavingPlayer?.release()
        leavingPlayer = null
        leaving = null
    }
    LaunchedEffect(request.closing) {
        if (!request.closing) return@LaunchedEffect
        player?.pause()
        feedback.play(Cue.BACK)
        launch { ui.animateTo(0f, tween(if (reduced) 0 else 200)) }
        launch { scrim.animateTo(0f, tween(if (reduced) 0 else Motion.SCRIM, easing = Motion.Ease)) }
        zoomFrom = if (reduced) null else thumbRect(shown)
        fading = zoomFrom == null
        zoom.animateTo(0f, if (fading) tween(Motion.EXIT, easing = Motion.In) else tween(440, easing = EaseOut))
        onClosed()
    }
    fun go(index: Int) {
        controls.onInput()
        if (!OverviewViewerDecisions.canBrowse(index, shown, media.size, currentRequest.closing)) return
        feedback.play(Cue.MOVE)
        onIndex(index)
    }
    /** The item follows a sideways swipe of [x] pixels; past either end only a quarter of it, a rubber band. */
    fun follow(x: Float) {
        if (currentRequest.closing) return
        controls.onInput()
        sliding = true
        scope.launch { slide.snapTo(OverviewViewerDecisions.follow(shown, media.size, x)) }
    }
    /** A sideways swipe let go at [x]: past 90 px the item slides out and the one beside it in, then they swap; else it springs back. */
    fun endSlide(x: Float) {
        controls.onInput()
        val startedAt = shown
        val next = OverviewViewerDecisions.slideTarget(shown, media.size, x, k, currentRequest.closing)
        val width = fit.width * k
        scope.launch {
            if (next != null) {
                if (!reduced) slide.animateTo(-(if (x < 0f) 1f else -1f) * (width + SLIDE_GAP * k), tween(260, easing = EaseOut))
                if (!OverviewViewerDecisions.canCommit(startedAt, shown, currentRequest.closing)) return@launch
                slidIn = true
                feedback.play(Cue.MOVE)
                onIndex(next)
            } else {
                slide.animateTo(0f, if (reduced) tween(0) else ViewerSpring)
                sliding = false
            }
        }
    }
    fun seek(side: Int) {
        val v = video ?: return
        feedback.play(Cue.MOVE)
        v.seekBy(SEEK_STEP * side)
        scope.launch { dock.flash(side) }
    }
    fun activate(slot: DockSlot) {
        controls.onInput()
        when (slot) {
            DockSlot.CLOSE -> onCloseRequest()
            DockSlot.SEEK, DockSlot.PLAY -> video?.let { feedback.play(Cue.SELECT); it.togglePlay() }
            DockSlot.PREVIOUS -> go(shown - 1)
            DockSlot.NEXT -> go(shown + 1)
            DockSlot.BACK -> seek(-1)
            DockSlot.FORWARD -> seek(1)
            DockSlot.MUTE, DockSlot.VOLUME -> video?.let { feedback.play(Cue.SELECT); it.toggleMute() }
        }
    }
    fun step(dx: Int, dy: Int) {
        val next = dockStep(dock.slot, dx, dy, video != null)
        when {
            next == null && dock.slot == DockSlot.SEEK -> seek(dx)
            next == null && dock.slot == DockSlot.VOLUME -> video?.let { it.changeVolume((if (it.muted) 0 else it.volume) + 10 * dx) }
            next != null && next != dock.slot -> { feedback.play(Cue.MOVE); dock.slot = next }
        }
    }
    val hints = OverviewViewerHints(
        video = video != null, playing = video?.playing == true, muted = video?.muted == true,
        target = when (dock.slot) {
            DockSlot.SEEK -> ViewerHintTarget.SEEK
            DockSlot.PLAY -> ViewerHintTarget.PLAY
            DockSlot.VOLUME -> ViewerHintTarget.VOLUME
            else -> ViewerHintTarget.BUTTON
        },
    )
    LaunchedEffect(hints) { shell.overviewViewer = hints }
    val currentShown by rememberUpdatedState(shown)
    val currentGo by rememberUpdatedState(::go)
    DisposableEffect(shell) {
        val shoulder: (Int) -> Unit = { step -> controls.onInput(); currentGo(currentShown + step) }
        val previous = shell.onShoulder
        shell.onShoulder = shoulder
        onDispose {
            if (shell.onShoulder === shoulder) shell.onShoulder = previous
            shell.overviewViewer = null
        }
    }
    // Over the playing YouTube video the dock's blur is the page's own (OverviewPlayerWindow cannot blur a web view from Compose)
    LaunchedEffect(player, overlayShown, dock.dock, dock.close, settled) {
        val p = player as? YouTubeVideoPlayer ?: return@LaunchedEffect
        fun rect(r: Rect, corner: Float) = floatArrayOf(r.left, r.top, r.width, r.height, corner * k)
        p.glass(overlayShown && settled && !dock.dock.isEmpty, listOf(rect(dock.dock, 46f), rect(dock.close, 38f)), 40f * k)
    }
    val requester = remember { FocusRequester() }
    LaunchedEffect(Unit) { requester.requestFocus() }
    BackHandler(enabled = shell.layer == null && shell.picker == null) { controls.onInput(); onCloseRequest() }
    val hiddenPointer = remember { PointerIcon(android.view.PointerIcon.TYPE_NULL) }
    Box(
        Modifier.fillMaxSize()
            .onGloballyPositioned { root = it; bounds = IntRect(it.positionInWindow().round(), it.size) }
            .onPreviewKeyEvent { event ->
                controls.onInput()
                if (event.type != KeyEventType.KeyDown) return@onPreviewKeyEvent event.key in VIEWER_KEYS
                when (event.key) {
                    Key.Back, Key.ButtonB -> onCloseRequest()
                    Key.DirectionLeft -> step(-1, 0)
                    Key.DirectionRight -> step(1, 0)
                    Key.DirectionUp -> step(0, -1)
                    Key.DirectionDown -> step(0, 1)
                    Key.ButtonA, Key.DirectionCenter -> activate(dock.slot)
                    Key.ButtonY, Key.M -> video?.let { feedback.play(Cue.SELECT); it.toggleMute() }
                    else -> return@onPreviewKeyEvent false
                }
                true
            }
            .focusRequester(requester)
            .focusable()
            .pointerHoverIcon(if (overlayShown) PointerIcon.Default else hiddenPointer, overrideDescendants = !overlayShown)
            .pointerInput(controls) {
                awaitPointerEventScope {
                    while (true) {
                        awaitPointerEvent(PointerEventPass.Initial)
                        controls.onInput()
                    }
                }
            },
    ) {
        // Effects belong to this backdrop sibling; a YouTube video plays in a window of its own over the shell's (OverviewPlayerWindow)
        Box(Modifier.fillMaxSize().graphicsLayer { alpha = scrim.value }
            .backdropBlur(haze, 40f, Color(4, 8, 16).copy(alpha = .55f)))
        // All the viewer draws above the video: here, and again over the video in the player's window while that is there
        val aboveVideo: @Composable (inWindow: Boolean) -> Unit = { inWindow ->
            // The pictures are the dock's blur source; each composition (this window's, the player's) has its own
            val pictures = remember { HazeState() }
            val origin = if (inWindow) Offset.Zero else root?.positionInWindow() ?: Offset.Zero
            Box(Modifier.fillMaxSize().hazeSource(pictures)) {
                // The thumbnail covers the video until it plays (a YouTube video: state 1; a Steam trailer: its first frame).
                listOfNotNull(leaving, shown).forEach { index -> key(index) {
                    val item = media.getOrNull(index) ?: return@forEach
                    val isLeaving = index == leaving
                    // A Steam trailer draws under its still here; the still fades, not the whole picture
                    val surface = if (isLeaving) leavingPlayer as? NativeVideoPlayer else native
                    val trailerHere = surface != null && !inWindow
                    val leavingWeb = isLeaving && leavingPlayer is YouTubeVideoPlayer && inWindow
                    Box(
                        Modifier.fillMaxSize().graphicsLayer {
                            transformOrigin = TransformOrigin(0f, 0f)
                            val c = change.value
                            val opacity = c.coerceIn(0f, 1f)
                            if (isLeaving) {
                                alpha = if (leavingWeb) 0f else 1f - opacity
                                if (!reduced) translationX = -direction * size.width * .04f * c
                                return@graphicsLayer
                            }
                            var a = if (leaving != null) opacity else 1f
                            if (leaving != null && !reduced) translationX = direction * size.width * .04f * (1f - c)
                            val z = zoom.value
                            val from = zoomFrom
                            if (fading || from == null || root == null) a *= z
                            else if (z < 1f) {
                                // The tile shows its picture filling it and enlarged by the parallax rim (OverviewGaps.MEDIA_OVERSCAN): the
                                // flight starts and ends at that size, so nothing grows or shrinks as the tile takes over; the rim that
                                // overhangs the tile fades with the last quarter of the flight
                                val scale = max(from.width / (fit.width * k), from.height / (fit.height * k)) * OverviewGaps.MEDIA_OVERSCAN
                                translationX += (from.center.x - fit.width * k * scale / 2f) * (1f - z)
                                translationY = (from.center.y - fit.height * k * scale / 2f) * (1f - z)
                                scaleX = Motion.lerp(scale, 1f, z)
                                scaleY = scaleX
                                a *= (z * 4f).coerceIn(0f, 1f)
                            }
                            if (item.kind != OverviewModel.MediaKind.VIDEO && !reduced) translationY += drag.y.coerceAtLeast(0f)
                            translationX += slide.value
                            alpha = a * if (item.kind == OverviewModel.MediaKind.VIDEO && !trailerHere) thumbnailAlpha else 1f
                        }.background(Color.Black),
                    ) {
                        // The trailer comes in once the opening or the browse has landed and stays through the closing
                        if (trailerHere && surface != null && (isLeaving || settled || surface.started)) NativeVideoSurface(surface)
                        val thumb by rememberIgdbImage(item.thumb, (569f * k).toInt())
                        val full by rememberIgdbImage(item.full, (maxOf(fit.width, fit.height) * k).toInt())
                        val trailer = item.kind == OverviewModel.MediaKind.VIDEO
                        val still by com.limelight.shell.data.rememberTrailerStill(item.id, item.stream.takeIf { trailer }, (maxOf(fit.width, fit.height) * k).toInt())
                        // A trailer's still fills the stage like the video will (cropped), softly blurred until the video plays and
                        // crossfades over it: its own frame when made, else the full picture, else (missing or still loading) the thumbnail
                        (still.picture ?: full ?: thumb)?.let {
                            Image(it, null, Modifier.fillMaxSize()
                                .then(if (trailerHere) Modifier.graphicsLayer { alpha = if (isLeaving && surface?.started == true) 0f else thumbnailAlpha } else Modifier)
                                .then(if (trailer) Modifier.blur(16.px) else Modifier),
                                contentScale = if (trailer) ContentScale.Crop else ContentScale.Fit)
                        }
                    }
                } }
                // The items a sideways swipe brings in, one on each side of the current one
                if (sliding) for (side in listOf(-1, 1)) {
                    val item = media.getOrNull(shown + side) ?: continue
                    key(shown + side) {
                        Box(
                            Modifier.fillMaxSize().graphicsLayer {
                                translationX = slide.value + side * (size.width + SLIDE_GAP * k)
                            }.background(Color.Black),
                        ) { ViewerPicture(item, fit, k) }
                    }
                }
            }
            if (settled && player?.error != null) VideoError(player.error!!, steam = native != null, Modifier.fillMaxSize())
            // Touch: a tap toggles the overlay, a double tap in the left or right third seeks 5 s, a swipe sideways carries the item and
            // brings the next one in, a swipe down closes. Trackpad: a two-finger swipe sideways (a horizontal scroll) does the same.
            // Mouse: a click is a tap (Play is the dock's button; owner, 2026-11-21). While the overlay is hidden over a playing video, a touch is YouTube's (an ad's Skip): this
            // layer is not there, so the touch reaches the player's surface, and the touch itself brings the overlay back.
            val passThrough = inWindow && video != null && !overlayShown
            val browseScroll = remember { BrowseScroll() }
            if (!passThrough) {
                var revealTap by remember { mutableStateOf(false) }
                Box(Modifier.fillMaxSize()
                    .pointerInput(shown, video != null) {
                        detectTapGestures(
                            onPress = { revealTap = SystemClock.uptimeMillis() - controls.revealedAt < 250L },
                            onDoubleTap = if (video == null) null else { o ->
                                val third = when {
                                    o.x < size.width / 3f -> -1
                                    o.x > size.width * 2f / 3f -> 1
                                    else -> 0
                                }
                                controls.onInput()
                                if (third != 0) seek(third)
                            },
                        ) {
                            when {
                                revealTap -> controls.onInput()
                                else -> controls.hide(currentHold)
                            }
                        }
                    }
                    .pointerInput(shown) {
                        // The swipe's axis is decided by its first move: sideways it carries the item, downwards it pulls to close
                        var sideways: Boolean? = null
                        var travel = 0f
                        detectDragGestures(
                            onDragStart = { sideways = null; travel = 0f },
                            onDragEnd = {
                                val d = drag / k
                                drag = Offset.Zero
                                when {
                                    sideways == true -> endSlide(travel)
                                    OverviewViewerDecisions.closesDrag(d.x, d.y) -> onCloseRequest()
                                }
                            },
                            onDragCancel = {
                                drag = Offset.Zero
                                if (sideways == true) endSlide(0f)
                            },
                        ) { change, amount ->
                            change.consume()
                            if (sideways == null) sideways = abs(amount.x) > abs(amount.y)
                            if (sideways == true) {
                                travel += amount.x
                                follow(travel)
                            } else {
                                drag += amount
                                controls.onInput()
                            }
                        }
                    }
                    .pointerInput(shown) {
                        // A horizontal scroll (a trackpad's two-finger swipe, a mouse's tilt wheel) carries the item like a finger; a pause
                        // in the scrolling lets it go (kept across items: [browseScroll])
                        val s = browseScroll
                        awaitPointerEventScope {
                            while (true) {
                                val event = if (s.active) withTimeoutOrNull(BROWSE_SCROLL_PAUSE_MS) { awaitPointerEvent() } else awaitPointerEvent()
                                if (event == null) {
                                    s.active = false
                                    endSlide(-s.sum)
                                    s.sum = 0f
                                    continue
                                }
                                if (event.type != PointerEventType.Scroll) continue
                                val change = event.changes.firstOrNull() ?: continue
                                val d = change.scrollDelta
                                if (abs(d.x) <= abs(d.y)) continue
                                change.consume()
                                s.active = true
                                s.sum += d.x * scrollStep
                                follow(-s.sum)
                            }
                        }
                    })
            }
            SeekFlash(dock, reduced)
            if (overlayPresent) {
                ViewerDock(
                    dock, media, shown, video, pictures, inWindow, origin, reduced,
                    overlay = overlayAlpha,
                    cover = { if (inWindow) thumbnailAlpha else 1f },
                    onGo = ::go, onClose = onCloseRequest, onActivate = ::activate,
                )
                if (kind == InputKind.CONTROLLER || kind == InputKind.KEYBOARD) DockRing(dock, overlayAlpha)
            }
        }
        aboveVideo(false)
        val webPlayer = (leavingPlayer as? YouTubeVideoPlayer)
            ?: (player as? YouTubeVideoPlayer)?.takeIf { settled }
        if (webPlayer != null && webPlayer.error == null) {
            OverviewPlayerWindow(webPlayer, bounds, pointerHidden = !overlayShown, onPointer = { controls.onInput() },
                pictureAlpha = { if (webPlayer === leavingPlayer) 1f - change.value.coerceIn(0f, 1f) else 1f },
                pictureOffset = { if (!reduced && webPlayer === leavingPlayer) -direction * bounds.width * .04f * change.value else slide.value },
            ) {
                Box(Modifier.fillMaxSize()) {
                    aboveVideo(true)
                    // The window covers the shell's hint bar: the same hints again, over the video
                    LocalWindowHintBar.current?.invoke()
                }
            }
        }
    }
}

private val VIEWER_KEYS = setOf(
    Key.DirectionLeft, Key.DirectionRight, Key.DirectionUp, Key.DirectionDown, Key.ButtonA, Key.DirectionCenter,
    Key.Back, Key.ButtonB,
    Key.ButtonY, Key.M,
)

/** A horizontal scroll carrying the viewer's item: its sideways travel so far (pixels) and whether one is going on. */
private class BrowseScroll {
    var sum = 0f
    var active = false
}

/** A pause in the scrolling this long lets a trackpad's swipe go (milliseconds). */
private const val BROWSE_SCROLL_PAUSE_MS = 120L

/** The gap between the viewer's item and the one a swipe brings in beside it (stage pixels). */
private const val SLIDE_GAP = 40f

/** The picture of an item a swipe brings in beside the current one: a trailer's still (blurred, as before it plays) or the screenshot. */
@Composable
private fun ViewerPicture(item: OverviewModel.Media, fit: Rect, k: Float) {
    val trailer = item.kind == OverviewModel.MediaKind.VIDEO
    val thumb by rememberIgdbImage(item.thumb, (569f * k).toInt())
    val full by rememberIgdbImage(item.full, (maxOf(fit.width, fit.height) * k).toInt())
    val still by com.limelight.shell.data.rememberTrailerStill(item.id, item.stream.takeIf { trailer }, (maxOf(fit.width, fit.height) * k).toInt())
    (still.picture ?: full ?: thumb)?.let {
        Image(it, null, Modifier.fillMaxSize().then(if (trailer) Modifier.blur(16.px) else Modifier),
            contentScale = if (trailer) ContentScale.Crop else ContentScale.Fit)
    }
}
