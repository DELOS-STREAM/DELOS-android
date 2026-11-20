package com.limelight.shell.screens

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
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import com.limelight.R
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
import kotlinx.coroutines.launch
import kotlin.math.abs
import kotlin.math.min

/** The viewer opened on item [index], zooming out of the thumbnail at [from] (root pixels; null: it fades in); [closing]: Back was pressed. */
internal data class ViewerRequest(val index: Int, val from: Rect?, val closing: Boolean = false)

/** Where the viewer's picture sits on a stage of [width] × [height]: as large as fits 280 × 300 px inside, 16:9, a little above the middle. */
internal fun viewerFit(width: Float, height: Float): Rect {
    var w = width - 280f
    var h = w * 9f / 16f
    if (h > height - 300f) {
        h = height - 300f
        w = h * 16f / 9f
    }
    val x = (width - w) / 2f
    val y = (height - h) / 2f - 10f
    return Rect(x, y, x + w, y + h)
}

/**
 * The full-screen screenshot viewer (the prototype's `Viewer`): it zooms out of the thumbnail and back into it (or fades when the
 * thumbnail is scrolled away), crossfades with parallax between items, shows a filmstrip and the count, and blurs the page behind it
 * for real ([haze]). Left and right browse (a nudge at the ends), A on a video opens YouTube, a finger swipes sideways or down.
 */
@Composable
internal fun OverviewViewer(
    media: List<OverviewModel.Media>,
    request: ViewerRequest,
    haze: HazeState,
    reduced: Boolean,
    thumbRect: (Int) -> Rect?,
    onIndex: (Int) -> Unit,
    onWatch: (String) -> Unit,
    onClosed: () -> Unit,
    onCloseRequest: () -> Unit,
) {
    val colors = LocalDelosColors.current
    val stage = LocalStage.current
    val feedback = LocalFeedback.current
    val k = stageUnit()
    val scope = rememberCoroutineScope()
    val fit = remember(stage.stageWidth, stage.stageHeight) { viewerFit(stage.stageWidth, stage.stageHeight) }
    val kind = ControllerGlyphs.kind
    var root by remember { mutableStateOf<LayoutCoordinates?>(null) }

    // 0: at the thumbnail (or invisible), 1: in place
    val zoom = remember { Animatable(0f) }
    val scrim = remember { Animatable(0f) }
    val ui = remember { Animatable(0f) }
    var zoomFrom by remember { mutableStateOf(request.from ?: thumbRect(request.index)) }
    var fading by remember { mutableStateOf(zoomFrom == null || reduced) }
    // The item shown and the one leaving, the direction of the change and its progress
    var shown by remember { mutableIntStateOf(request.index) }
    var leaving by remember { mutableStateOf<Int?>(null) }
    var direction by remember { mutableIntStateOf(1) }
    val change = remember { Animatable(1f) }
    val nudge = remember { Animatable(0f) }
    var drag by remember { mutableStateOf(Offset.Zero) }
    val currentRequest by rememberUpdatedState(request)

    LaunchedEffect(Unit) {
        launch { scrim.animateTo(1f, tween(Motion.SCRIM, easing = Motion.Ease)) }
        launch { kotlinx.coroutines.delay(if (reduced) 0 else 300); ui.animateTo(1f, tween(300, easing = Motion.Ease)) }
        zoom.animateTo(1f, if (fading) tween(Motion.FADE, easing = Motion.Ease) else tween(560, easing = OverviewMotion.Flight))
    }
    // Browsing: the new picture slides in from the side of the key, the old one leaves the other way
    LaunchedEffect(request.index) {
        if (request.index == shown) return@LaunchedEffect
        direction = if (request.index > shown) 1 else -1
        leaving = shown
        shown = request.index
        change.snapTo(0f)
        change.animateTo(1f, tween(if (reduced) Motion.FADE else 560, easing = EaseOut))
        leaving = null
    }
    // Closing: back into the thumbnail of the item shown, or a fade when it is scrolled away
    LaunchedEffect(request.closing) {
        if (!request.closing) return@LaunchedEffect
        feedback.play(Cue.BACK)
        launch { ui.animateTo(0f, tween(200)) }
        launch { scrim.animateTo(0f, tween(Motion.SCRIM, easing = Motion.Ease)) }
        val target = if (reduced) null else thumbRect(shown)
        zoomFrom = target
        fading = target == null
        zoom.animateTo(0f, if (fading) tween(Motion.EXIT, easing = Motion.In) else tween(440, easing = EaseOut))
        onClosed()
    }

    fun go(index: Int) {
        if (currentRequest.closing) return
        if (index < 0 || index > media.lastIndex) {
            if (!reduced) scope.launch {
                nudge.snapTo(0f)
                nudge.animateTo(1f, tween(320, easing = EaseOut))
                nudge.snapTo(0f)
            }
            direction = if (index < 0) -1 else 1
            return
        }
        feedback.play(Cue.MOVE)
        onIndex(index)
    }

    val requester = remember { FocusRequester() }
    LaunchedEffect(Unit) { runCatching { requester.requestFocus() } }

    Box(
        Modifier
            .fillMaxSize()
            .onGloballyPositioned { root = it }
            .focusRequester(requester)
            .focusable()
            .onKeyEvent { event ->
                if (event.type != KeyEventType.KeyDown) return@onKeyEvent event.key in VIEWER_KEYS
                when (event.key) {
                    Key.DirectionLeft -> go(shown - 1)
                    Key.DirectionRight -> go(shown + 1)
                    Key.ButtonA, Key.Enter, Key.NumPadEnter, Key.DirectionCenter -> media.getOrNull(shown)?.takeIf { it.kind == OverviewModel.MediaKind.VIDEO }?.let { onWatch(it.id) }
                    Key.DirectionUp, Key.DirectionDown -> Unit
                    else -> return@onKeyEvent false
                }
                true
            }
            .pointerInput(Unit) {
                // A finger: sideways browses, down closes; the picture follows it meanwhile
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
                ) { change, amount ->
                    change.consume()
                    drag += amount
                }
            },
    ) {
        // The page behind, blurred (`.vw-scrim`: blur 40 px, rgba(4, 8, 16, .55))
        Box(Modifier.fillMaxSize().graphicsLayer {
            val pulled = if (drag.y > 0f && abs(drag.y) > abs(drag.x)) 1f - min(drag.y / k, 400f) / 500f else 1f
            alpha = scrim.value * pulled
        }.backdropBlur(haze, 40f, Color(4, 8, 16).copy(alpha = .55f)))

        // The pictures: the one leaving under the one shown
        listOfNotNull(leaving, shown).forEach { index ->
            val item = media.getOrNull(index) ?: return@forEach
            val isLeaving = index == leaving
            Box(
                Modifier
                    .offset(fit.left.px, fit.top.px)
                    .size(fit.width.px, fit.height.px)
                    .graphicsLayer {
                        transformOrigin = TransformOrigin(0f, 0f)
                        val c = change.value
                        if (isLeaving) {
                            val p = min(1f, c * 560f / 420f)
                            alpha = 1f - p
                            if (!reduced) {
                                transformOrigin = TransformOrigin.Center
                                translationX = -direction * 90f * k * p
                                val s = Motion.lerp(1f, .94f, p)
                                scaleX = s
                                scaleY = s
                            }
                            return@graphicsLayer
                        }
                        // Entering after a change
                        var a = if (leaving != null) c else 1f
                        if (leaving != null && !reduced) {
                            transformOrigin = TransformOrigin.Center
                            translationX = direction * 160f * k * (1f - c)
                            val s = Motion.lerp(.96f, 1f, c)
                            scaleX = s
                            scaleY = s
                        }
                        // The zoom out of the thumbnail (or the fade)
                        val z = zoom.value
                        val from = zoomFrom
                        val origin = root
                        if (fading || from == null || origin == null) {
                            a *= z
                            if (!reduced && currentRequest.closing) {
                                transformOrigin = TransformOrigin.Center
                                val s = Motion.lerp(.94f, 1f, z)
                                scaleX = s
                                scaleY = s
                            }
                        } else if (z < 1f) {
                            val fx = fit.left * k
                            val fy = fit.top * k
                            val sx = from.width / (fit.width * k)
                            val sy = from.height / (fit.height * k)
                            translationX = (from.left - fx) * (1f - z)
                            translationY = (from.top - fy) * (1f - z)
                            scaleX = Motion.lerp(sx, 1f, z)
                            scaleY = Motion.lerp(sy, 1f, z)
                        }
                        // A finger dragging, the nudge at the ends
                        val d = drag
                        if (abs(d.y) > abs(d.x) && d.y > 0f) {
                            transformOrigin = TransformOrigin.Center
                            translationY += d.y
                            val s = 1f - min(d.y / k, 400f) / 1600f
                            scaleX *= s
                            scaleY *= s
                        } else translationX += d.x * .6f
                        val n = nudge.value
                        if (n > 0f) translationX += direction * 14f * k * (if (n < .5f) n * 2f else (1f - n) * 2f)
                        alpha = a
                        val corner = if (fading || from == null) 18f else Motion.lerp(40f, 18f, z)
                        shape = RoundedCornerShape(corner * k)
                        clip = true
                        shadowElevation = 30f * k
                    }
                    .background(colors.surface2),
            ) {
                val thumb by rememberIgdbImage(item.thumb, (569f * k).toInt())
                val full by rememberIgdbImage(item.full, (fit.width * k).toInt())
                // The thumbnail stays until the full picture is decoded
                (full ?: thumb)?.let { Image(it, null, Modifier.fillMaxSize(), contentScale = ContentScale.Crop) }
                if (item.kind == OverviewModel.MediaKind.VIDEO) {
                    Box(Modifier.align(Alignment.Center)) { Symbol(Symbols.VIDEO_PLAY, 96.tpx, Color.White, filled = true) }
                    BasicText(
                        stringResource(R.string.overview_video_youtube, item.name ?: stringResource(R.string.overview_video)),
                        Modifier.align(Alignment.BottomStart).padding(start = 30.px, bottom = 26.px),
                        style = TextStyle(color = Color.White, fontFamily = Figtree, fontWeight = FontWeight.Bold, fontSize = 26.tpx, shadow = Shadow(Color.Black.copy(alpha = .7f), Offset(0f, 2f * k), 10f * k)),
                    )
                }
            }
        }

        // The count, the filmstrip and the pointer's controls
        Box(Modifier.fillMaxSize().graphicsLayer { alpha = ui.value }) {
            val item = media.getOrNull(shown)
            Row(Modifier.offset(72.px, 40.px), verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(6.px)) {
                BasicText((shown + 1).toString(), style = TextStyle(color = colors.text, fontFamily = Unbounded, fontWeight = FontWeight.Bold, fontSize = 26.tpx))
                val name = when {
                    item == null -> ""
                    item.kind == OverviewModel.MediaKind.VIDEO -> item.name ?: stringResource(R.string.overview_video)
                    else -> stringResource(R.string.overview_screenshot, item.number)
                }
                BasicText(
                    stringResource(R.string.overview_viewer_count, media.size, name),
                    style = TextStyle(color = colors.text2, fontFamily = Figtree, fontWeight = FontWeight.Bold, fontSize = 22.tpx),
                )
            }
            Row(
                Modifier.align(Alignment.BottomCenter).padding(bottom = 112.px),
                horizontalArrangement = Arrangement.spacedBy(12.px),
            ) {
                media.forEachIndexed { index, strip ->
                    val on = index == shown
                    val scale by animateFloatAsState(if (on) 1.18f else 1f, tween(420, easing = com.limelight.shell.ui.EaseSpring), label = "strip")
                    val alpha by animateFloatAsState(if (on) 1f else .45f, tween(300), label = "stripAlpha")
                    val thumb by rememberIgdbImage(strip.thumb, (96f * 1.18f * k).toInt())
                    Box(
                        Modifier
                            .size(96.px, 54.px)
                            .graphicsLayer { scaleX = scale; scaleY = scale; this.alpha = alpha }
                            .drawWithContent {
                                drawContent()
                                if (on) drawRoundRect(colors.accent, topLeft = Offset(-3f * k / 2f, -3f * k / 2f), size = size.copy(size.width + 3f * k, size.height + 3f * k),
                                    cornerRadius = androidx.compose.ui.geometry.CornerRadius(9.5f * k), style = androidx.compose.ui.graphics.drawscope.Stroke(3f * k))
                            }
                            .clip(RoundedCornerShape(8.px))
                            .background(colors.surface2)
                            .pointerInput(index) { detectTapGestures { go(index) } },
                    ) { thumb?.let { Image(it, null, Modifier.fillMaxSize(), contentScale = ContentScale.Crop) } }
                }
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

private val VIEWER_KEYS = setOf(Key.DirectionLeft, Key.DirectionRight, Key.DirectionUp, Key.DirectionDown, Key.ButtonA, Key.Enter, Key.NumPadEnter, Key.DirectionCenter)

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
