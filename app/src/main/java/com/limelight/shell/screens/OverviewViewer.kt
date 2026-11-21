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
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
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

/** The viewer opened on item [index], zooming out of the thumbnail at [from] (root pixels; null: it fades in); [closing]: Back was pressed. */
internal data class ViewerRequest(val index: Int, val from: Rect?, val closing: Boolean = false)

/** The entire viewport belongs to the image; Image's Fit preserves its natural aspect ratio inside it. */
internal fun viewerFit(width: Float, height: Float): Rect = Rect(0f, 0f, width, height)

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

    /** A tap on the picture hides the overlay at once. */
    fun hide() {
        controlsVisible = false
    }

    internal fun remaining(now: Long): Long = (3_000L - (now - lastInput)).coerceAtLeast(0L)

    internal fun hideIfIdle(now: Long) {
        if (remaining(now) == 0L) controlsVisible = false
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
    input: OverviewViewerInput? = null,
    onControlsVisibilityChanged: (Boolean) -> Unit = {},
) {
    val stage = LocalStage.current
    val feedback = LocalFeedback.current
    val k = stageUnit()
    val fit = remember(stage.stageWidth, stage.stageHeight) { viewerFit(stage.stageWidth, stage.stageHeight) }
    val shell = LocalShell.current
    val kind = ControllerGlyphs.kind
    val localInput = remember { OverviewViewerInput() }
    val controls = input ?: localInput
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
    var direction by remember { mutableIntStateOf(1) }
    val change = remember { Animatable(1f) }
    var drag by remember { mutableStateOf(Offset.Zero) }
    var opened by remember { mutableStateOf(false) }
    val currentRequest by rememberUpdatedState(request)
    val scope = rememberCoroutineScope()
    val dock = remember { DockState() }
    val player = remember(shown) {
        media.getOrNull(shown)?.takeIf { it.kind == OverviewModel.MediaKind.VIDEO }?.let { item ->
            item.stream?.let(::NativeVideoPlayer) ?: YouTubeVideoPlayer(item.id)
        }
    }
    val native = player as? NativeVideoPlayer
    val video = player?.takeIf { it.error == null }
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

    LaunchedEffect(Unit) {
        controls.onInput()
        launch { scrim.animateTo(1f, tween(if (reduced) 0 else Motion.SCRIM, easing = Motion.Ease)) }
        launch { delay(if (reduced) 0 else 300); ui.animateTo(1f, tween(if (reduced) 0 else 300, easing = Motion.Ease)) }
        zoom.animateTo(1f, if (fading) tween(Motion.FADE, easing = Motion.Ease) else tween(560, easing = OverviewMotion.Flight))
        opened = true
    }
    // Auto-hide: 3 s without input while a video plays or a screenshot shows; a paused or ended video and a dragged slider hold it
    val hold = (video != null && !video.playing) || dock.dragging
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
    // The focus starts on the timeline (a screenshot: Next) whenever the item changes or the overlay comes back
    LaunchedEffect(shown, video != null, overlayShown) { if (!overlayShown || dock.slot !in dockSlots(video != null)) dock.slot = dockDefault(video != null) }
    // The ring glides to the focused stop by a spring (snaps with reduced motion and on its first placement)
    LaunchedEffect(dock) {
        snapshotFlow { dock.bounds[dock.slot]?.let { ringRect(dock.slot, it, k) } }.collectLatest { target ->
            if (target == null) return@collectLatest
            if (reduced || !dock.ringPlaced) {
                dock.ringPlaced = true
                dock.ring.snapTo(target)
            } else dock.ring.animateTo(target, DockRingSpring)
        }
    }
    LaunchedEffect(request.index) {
        if (request.index == shown) return@LaunchedEffect
        controls.onInput()
        direction = if (request.index > shown) 1 else -1
        leaving = shown
        shown = request.index
        change.snapTo(0f)
        change.animateTo(1f, tween(if (reduced) Motion.FADE else 560, easing = EaseOut))
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
        if (currentRequest.closing || index !in media.indices || index == shown) return
        feedback.play(Cue.MOVE)
        onIndex(index)
    }
    fun seek(side: Int) {
        val v = video ?: return
        feedback.play(Cue.MOVE)
        v.seekBy(10 * side)
        scope.launch { dock.flash(side, reduced) }
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
    BackHandler { controls.onInput(); onCloseRequest() }
    val hiddenPointer = remember { PointerIcon(android.view.PointerIcon.TYPE_NULL) }
    Box(
        Modifier.fillMaxSize()
            .onGloballyPositioned { root = it; bounds = IntRect(it.positionInWindow().round(), it.size) }
            .onPreviewKeyEvent { event ->
                controls.onInput()
                if (event.type != KeyEventType.KeyDown) return@onPreviewKeyEvent event.key in VIEWER_KEYS
                when (event.key) {
                    Key.Back, Key.Escape, Key.ButtonB -> onCloseRequest()
                    Key.DirectionLeft -> step(-1, 0)
                    Key.DirectionRight -> step(1, 0)
                    Key.DirectionUp -> step(0, -1)
                    Key.DirectionDown -> step(0, 1)
                    Key.ButtonA, Key.Enter, Key.NumPadEnter, Key.DirectionCenter -> activate(dock.slot)
                    Key.ButtonY, Key.M -> video?.let { feedback.play(Cue.SELECT); it.toggleMute() }
                    // A keyboard's Space (when it is not mapped to A) plays or pauses wherever the focus is
                    Key.Spacebar -> video?.let { feedback.play(Cue.SELECT); it.togglePlay() }
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
                listOfNotNull(leaving, shown).forEach { index ->
                    val item = media.getOrNull(index) ?: return@forEach
                    val isLeaving = index == leaving
                    // A Steam trailer draws under its still here; the still fades, not the whole picture
                    val trailerHere = !isLeaving && native != null && !inWindow
                    Box(
                        Modifier.fillMaxSize().graphicsLayer {
                            transformOrigin = TransformOrigin(0f, 0f)
                            val c = change.value
                            if (isLeaving) {
                                val p = min(1f, c * 560f / 420f)
                                alpha = 1f - p
                                if (!reduced) translationX = -direction * 90f * k * p
                                return@graphicsLayer
                            }
                            var a = if (leaving != null) c else 1f
                            if (leaving != null && !reduced) translationX = direction * 160f * k * (1f - c)
                            val z = zoom.value
                            val from = zoomFrom
                            if (fading || from == null || root == null) a *= z
                            else if (z < 1f) {
                                val scale = min(from.width / (fit.width * k), from.height / (fit.height * k))
                                translationX += (from.center.x - fit.width * k * scale / 2f) * (1f - z)
                                translationY = (from.center.y - fit.height * k * scale / 2f) * (1f - z)
                                scaleX = Motion.lerp(scale, 1f, z)
                                scaleY = scaleX
                            }
                            if (item.kind != OverviewModel.MediaKind.VIDEO && !reduced) {
                                translationX += drag.x * .6f
                                translationY += drag.y.coerceAtLeast(0f)
                            }
                            alpha = a * if (item.kind == OverviewModel.MediaKind.VIDEO && !trailerHere) thumbnailAlpha else 1f
                        }.background(Color.Black),
                    ) {
                        // The trailer comes in once the opening or the browse has landed and stays through the closing
                        if (trailerHere && native != null && (settled || native.started)) NativeVideoSurface(native)
                        val thumb by rememberIgdbImage(item.thumb, (569f * k).toInt())
                        val full by rememberIgdbImage(item.full, (maxOf(fit.width, fit.height) * k).toInt())
                        // A trailer's still fills the stage like the video will (cropped), softly blurred until the video plays and
                        // crossfades over it; the full picture, else (missing or still loading) the thumbnail
                        val trailer = item.kind == OverviewModel.MediaKind.VIDEO
                        (full ?: thumb)?.let {
                            Image(it, null, Modifier.fillMaxSize().then(if (trailer) Modifier.blur(16.px) else Modifier)
                                .then(if (trailerHere) Modifier.graphicsLayer { alpha = thumbnailAlpha } else Modifier),
                                contentScale = if (trailer) ContentScale.Crop else ContentScale.Fit)
                        }
                    }
                }
            }
            if (settled && player?.error != null) VideoError(player.error!!, steam = native != null, Modifier.fillMaxSize())
            // Touch: a tap toggles the overlay, a double tap in the left or right third seeks 10 s, a swipe sideways browses and down closes.
            // Mouse: a click plays or pauses. While the overlay is hidden over a playing video, a touch is YouTube's (an ad's Skip): this
            // layer is not there, so the touch reaches the player's surface, and the touch itself brings the overlay back.
            val passThrough = inWindow && video != null && !overlayShown
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
                                ControllerGlyphs.kind == InputKind.POINTER -> { controls.onInput(); video?.let { feedback.play(Cue.SELECT); it.togglePlay() } }
                                revealTap -> controls.onInput()
                                else -> controls.hide()
                            }
                        }
                    }
                    .pointerInput(shown) {
                        detectDragGestures(
                            onDragEnd = {
                                val d = drag / k
                                drag = Offset.Zero
                                when {
                                    d.y > 120f && abs(d.y) > abs(d.x) -> onCloseRequest()
                                    d.x < -90f -> go(shown + 1)
                                    d.x > 90f -> go(shown - 1)
                                }
                            },
                            onDragCancel = { drag = Offset.Zero },
                        ) { change, amount -> change.consume(); drag += amount; controls.onInput() }
                    })
            }
            SeekFlash(dock, reduced)
            if (overlayShown || overlay > 0f) {
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
        if (settled && player is YouTubeVideoPlayer && player.error == null) {
            OverviewPlayerWindow(player, bounds, pointerHidden = !overlayShown, onPointer = { controls.onInput() }) {
                Box(Modifier.fillMaxSize()) {
                    aboveVideo(true)
                    // The window covers the shell's hint bar: the same hints again, over the video
                    LocalWindowHintBar.current?.invoke()
                }
            }
        }
    }
}

/** The stops of the dock for a video or a screenshot. */
private fun dockSlots(video: Boolean): List<DockSlot> = listOf(DockSlot.CLOSE) + (if (video) listOf(DockSlot.SEEK) else emptyList()) + dockRow(video)

private val VIEWER_KEYS = setOf(
    Key.DirectionLeft, Key.DirectionRight, Key.DirectionUp, Key.DirectionDown, Key.ButtonA, Key.Enter, Key.NumPadEnter, Key.DirectionCenter,
    Key.Back, Key.Escape, Key.ButtonB,
    Key.ButtonY, Key.M, Key.Spacebar,
)
