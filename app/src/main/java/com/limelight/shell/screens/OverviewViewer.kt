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
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntRect
import androidx.compose.ui.unit.round
import com.limelight.R
import com.limelight.shell.LocalShell
import com.limelight.shell.LocalWindowHintBar
import com.limelight.shell.OverviewViewerHints
import com.limelight.shell.data.rememberIgdbImage
import com.limelight.shell.ui.ControllerGlyphs
import com.limelight.shell.ui.Cue
import com.limelight.shell.ui.EaseOut
import com.limelight.shell.ui.Figtree
import com.limelight.shell.ui.InputKind
import com.limelight.shell.ui.LocalDelosColors
import com.limelight.shell.ui.LocalFeedback
import com.limelight.shell.ui.LocalStage
import com.limelight.shell.ui.Motion
import com.limelight.shell.ui.Symbol
import com.limelight.shell.ui.Symbols
import com.limelight.shell.ui.Unbounded
import com.limelight.shell.ui.backdropBlur
import com.limelight.shell.ui.px
import com.limelight.shell.ui.stageUnit
import com.limelight.shell.ui.tpx
import dev.chrisbanes.haze.HazeState
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.abs
import kotlin.math.min

/** The viewer opened on item [index], zooming out of the thumbnail at [from] (root pixels; null: it fades in); [closing]: Back was pressed. */
internal data class ViewerRequest(val index: Int, val from: Rect?, val closing: Boolean = false)

/** The entire viewport belongs to the image; Image's Fit preserves its natural aspect ratio inside it. */
internal fun viewerFit(width: Float, height: Float): Rect = Rect(0f, 0f, width, height)

/** Non-consuming input observation, also called before the shell consumes a global key or pointer event. */
@androidx.compose.runtime.Stable
internal class OverviewViewerInput(now: Long = SystemClock.uptimeMillis()) {
    var controlsVisible by mutableStateOf(true)
        private set
    private var lastInput = now

    fun onInput(now: Long = SystemClock.uptimeMillis()) {
        lastInput = now
        controlsVisible = true
    }

    internal fun remaining(now: Long): Long = (3_000L - (now - lastInput)).coerceAtLeast(0L)

    internal fun hideIfIdle(now: Long) {
        if (remaining(now) == 0L) controlsVisible = false
    }
}

/**
 * Full-viewport media. Only the images and overlays animate; a video plays in a window of its own over the shell's
 * ([OverviewPlayerWindow]), never inside the shell's Compose layers, with the thumbnail, the overlay and the hint bar composed over it there.
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
    val colors = LocalDelosColors.current
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
    val player = remember(shown) {
        media.getOrNull(shown)?.takeIf { it.kind == OverviewModel.MediaKind.VIDEO }?.let { OverviewVideoPlayer(it.id) }
    }
    val video = player?.takeIf { it.error == null }
    val settled = opened && leaving == null && !request.closing
    val thumbnailAlpha by animateFloatAsState(
        if (settled && player?.started == true && player.error == null) 0f else 1f,
        tween(if (reduced) 0 else Motion.FADE), label = "viewerThumbnail",
    )

    LaunchedEffect(Unit) {
        controls.onInput()
        launch { scrim.animateTo(1f, tween(if (reduced) 0 else Motion.SCRIM, easing = Motion.Ease)) }
        launch { delay(if (reduced) 0 else 300); ui.animateTo(1f, tween(if (reduced) 0 else 300, easing = Motion.Ease)) }
        zoom.animateTo(1f, if (fading) tween(Motion.FADE, easing = Motion.Ease) else tween(560, easing = OverviewMotion.Flight))
        opened = true
    }
    LaunchedEffect(controls, controls.controlsVisible) {
        visibilityChanged(controls.controlsVisible)
        while (controls.controlsVisible) {
            delay(controls.remaining(SystemClock.uptimeMillis()).coerceAtLeast(1L))
            controls.hideIfIdle(SystemClock.uptimeMillis())
        }
    }
    DisposableEffect(controls) { onDispose { visibilityChanged(true) } }
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
    val hints = OverviewViewerHints(video = video != null, playing = video?.playing == true, muted = video?.muted == true)
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
    val requester = remember { FocusRequester() }
    LaunchedEffect(Unit) { requester.requestFocus() }
    BackHandler { controls.onInput(); onCloseRequest() }
    Box(
        Modifier.fillMaxSize()
            .onGloballyPositioned { root = it; bounds = IntRect(it.positionInWindow().round(), it.size) }
            .onPreviewKeyEvent { event ->
                controls.onInput()
                if (event.type != KeyEventType.KeyDown) return@onPreviewKeyEvent event.key in VIEWER_KEYS
                when (event.key) {
                    Key.Back, Key.Escape, Key.ButtonB -> onCloseRequest()
                    Key.DirectionLeft -> if (video != null) video.seekBy(-10) else go(shown - 1)
                    Key.DirectionRight -> if (video != null) video.seekBy(10) else go(shown + 1)
                    Key.ButtonA, Key.Enter, Key.NumPadEnter, Key.DirectionCenter -> video?.togglePlay()
                    Key.ButtonY, Key.M -> video?.toggleMute()
                    Key.DirectionUp, Key.DirectionDown -> Unit
                    else -> return@onPreviewKeyEvent false
                }
                true
            }
            .focusRequester(requester)
            .focusable()
            .pointerInput(controls) {
                awaitPointerEventScope {
                    while (true) {
                        awaitPointerEvent(PointerEventPass.Initial)
                        controls.onInput()
                    }
                }
            },
    ) {
        // Effects belong to this backdrop sibling; the video itself plays in a window of its own over the shell's (OverviewPlayerWindow)
        Box(Modifier.fillMaxSize().graphicsLayer { alpha = scrim.value }
            .backdropBlur(haze, 40f, Color(4, 8, 16).copy(alpha = .55f)))
        // All the viewer draws above the video: here, and again over the video in the player's window while that is there
        val aboveVideo: @Composable () -> Unit = {
            // The thumbnail covers the video until state 1 actually reports playing.
            listOfNotNull(leaving, shown).forEach { index ->
                val item = media.getOrNull(index) ?: return@forEach
                val isLeaving = index == leaving
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
                        alpha = a * if (item.kind == OverviewModel.MediaKind.VIDEO) thumbnailAlpha else 1f
                    }.background(Color.Black),
                ) {
                    val thumb by rememberIgdbImage(item.thumb, (569f * k).toInt())
                    val full by rememberIgdbImage(item.full, (maxOf(fit.width, fit.height) * k).toInt())
                    (full ?: thumb)?.let { Image(it, null, Modifier.fillMaxSize(), contentScale = ContentScale.Fit) }
                }
            }
            if (settled && player?.error != null) VideoError(player.error!!, Modifier.fillMaxSize())
            // An input-only sibling keeps embedded web content out of focus.
            Box(Modifier.fillMaxSize()
                .pointerInput(shown) { detectTapGestures { controls.onInput() } }
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
            // The DELOS overlay (count and title, transport, buttons) fades out after 3 s without input and back on any input
            val overlayShown = controls.controlsVisible && !request.closing
            val overlay by animateFloatAsState(if (overlayShown) 1f else 0f, tween(if (reduced) 0 else Motion.FADE, easing = Motion.Ease), label = "viewerOverlay")
            if (overlayShown || overlay > 0f) {
                Box(Modifier.fillMaxSize().graphicsLayer { alpha = ui.value * overlay }) {
                    Box(Modifier.fillMaxSize().background(androidx.compose.ui.graphics.Brush.verticalGradient(
                        0f to Color.Black.copy(alpha = .65f), .2f to Color.Transparent,
                        .7f to Color.Transparent, 1f to Color.Black.copy(alpha = .8f),
                    )))
                    val item = media.getOrNull(shown)
                    Row(Modifier.offset(72.px, 40.px).padding(end = 180.px), verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(6.px)) {
                        BasicText((shown + 1).toString(), style = TextStyle(color = colors.accent, fontFamily = Unbounded, fontWeight = FontWeight.Bold, fontSize = 26.tpx))
                        val name = when {
                            item == null -> ""
                            item.kind == OverviewModel.MediaKind.VIDEO -> item.name ?: stringResource(R.string.overview_video)
                            else -> stringResource(R.string.overview_screenshot, item.number)
                        }
                        BasicText(stringResource(R.string.overview_viewer_count, media.size, name),
                            style = TextStyle(color = colors.text, fontFamily = Figtree, fontWeight = FontWeight.Bold, fontSize = 22.tpx))
                    }
                    if (video != null) {
                        VideoControls(video, Modifier.align(Alignment.BottomCenter).padding(start = 72.px, end = 72.px, bottom = 112.px)
                            .fillMaxWidth().height(72.px))
                    }
                    if (kind == InputKind.POINTER) {
                        ViewerButton(Symbols.CHEVRON_LEFT, stringResource(R.string.overview_previous), Modifier.align(Alignment.CenterStart).padding(start = 40.px)) { go(shown - 1) }
                        ViewerButton(Symbols.CHEVRON_RIGHT, stringResource(R.string.overview_next), Modifier.align(Alignment.CenterEnd).padding(end = 40.px)) { go(shown + 1) }
                    }
                    if (kind == InputKind.POINTER || kind == InputKind.TOUCH) {
                        ViewerButton(Symbols.CLOSE, stringResource(R.string.shell_hint_close), Modifier.align(Alignment.TopEnd).padding(top = 30.px, end = 56.px), size = 72f, onClick = onCloseRequest)
                    }
                }
            }
        }
        aboveVideo()
        if (settled && player != null && player.error == null) {
            OverviewPlayerWindow(player, bounds, onPointer = { controls.onInput() }) {
                Box(Modifier.fillMaxSize()) {
                    aboveVideo()
                    // The window covers the shell's hint bar: the same hints again, over the video
                    LocalWindowHintBar.current?.invoke()
                }
            }
        }
    }
}

private val VIEWER_KEYS = setOf(
    Key.DirectionLeft, Key.DirectionRight, Key.DirectionUp, Key.DirectionDown, Key.ButtonA, Key.Enter, Key.NumPadEnter, Key.DirectionCenter,
    Key.Back, Key.Escape, Key.ButtonB,
    Key.ButtonY, Key.M,
)

/** A round glass button of the viewer (previous, next, close). */
@Composable
private fun ViewerButton(icon: String, description: String, modifier: Modifier, size: Float = 84f, onClick: () -> Unit) {
    val colors = LocalDelosColors.current
    Box(
        modifier
            .size(size.px)
            .overviewGlass(null, size / 2f)
            .semantics { contentDescription = description }
            .pointerInput(Unit) { detectTapGestures { onClick() } },
        contentAlignment = Alignment.Center,
    ) { Symbol(icon, (size * .43f).tpx, colors.text) }
}
