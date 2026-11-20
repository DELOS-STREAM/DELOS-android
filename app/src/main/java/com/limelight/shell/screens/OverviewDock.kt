package com.limelight.shell.screens

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Paint
import android.graphics.Typeface
import android.os.SystemClock
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.VectorConverter
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.dropShadow
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.core.content.res.ResourcesCompat
import com.limelight.R
import com.limelight.shell.ui.ControllerGlyphs
import com.limelight.shell.ui.Figtree
import com.limelight.shell.ui.InputKind
import com.limelight.shell.ui.LocalDelosColors
import com.limelight.shell.ui.LocalStage
import com.limelight.shell.ui.Symbols
import com.limelight.shell.ui.Unbounded
import com.limelight.shell.ui.backdropBlur
import com.limelight.shell.ui.hoverLook
import com.limelight.shell.ui.pressLook
import com.limelight.shell.ui.px
import com.limelight.shell.ui.rememberPressPulse
import com.limelight.shell.ui.stageUnit
import com.limelight.shell.ui.tpx
import dev.chrisbanes.haze.HazeState
import kotlin.math.PI
import androidx.compose.ui.layout.layout
import kotlin.math.roundToInt
import kotlin.math.sin

/** A stop of the viewer dock's focus (controller and keyboard); docs/gui/GAME_INFO.md §9.4. */
internal enum class DockSlot { CLOSE, SEEK, PREVIOUS, BACK, PLAY, FORWARD, NEXT, MUTE, VOLUME }

/** Where the focus starts: the timeline on a video, Next on a screenshot (L1/R1 browse; Left/Right walk previous and next). */
internal fun dockDefault(video: Boolean): DockSlot = if (video) DockSlot.SEEK else DockSlot.NEXT

/** The dock's lower row, left to right: the transport, then the sound. */
internal fun dockRow(video: Boolean): List<DockSlot> =
    if (video) listOf(DockSlot.PREVIOUS, DockSlot.BACK, DockSlot.PLAY, DockSlot.FORWARD, DockSlot.NEXT, DockSlot.MUTE, DockSlot.VOLUME)
    else listOf(DockSlot.PREVIOUS, DockSlot.NEXT)

/**
 * The stop a D-pad step ([dx] or [dy], −1 or 1) from [slot] reaches; null: the step acts on [slot] itself (Left/Right seek on the
 * timeline and change the volume on its slider). Up from the timeline reaches close, Down reaches play; the row is walked sideways.
 */
internal fun dockStep(slot: DockSlot, dx: Int, dy: Int, video: Boolean): DockSlot? {
    if (dy < 0) return when (slot) {
        DockSlot.CLOSE, DockSlot.SEEK -> DockSlot.CLOSE
        else -> if (video) DockSlot.SEEK else DockSlot.CLOSE
    }
    if (dy > 0) return when (slot) {
        DockSlot.CLOSE -> dockDefault(video)
        DockSlot.SEEK -> DockSlot.PLAY
        else -> slot
    }
    if (dx == 0 || slot == DockSlot.CLOSE) return slot
    if (slot == DockSlot.SEEK || slot == DockSlot.VOLUME) return null
    val row = dockRow(video)
    val at = row.indexOf(slot)
    return if (at < 0) dockDefault(video) else row[(at + dx).coerceIn(0, row.lastIndex)]
}

/** Repeated seeks in one direction within this long add up in the flash (10, 20…). */
internal const val SEEK_FLASH_CHAIN_MS = 900L

/** The running total of the seek flash: [previous] seconds shown on [previousSide], a new press [side] at [now] after one at [last]. */
internal fun seekFlashTotal(previous: Int, previousSide: Int, last: Long, side: Int, now: Long): Int =
    if (side == previousSide && now - last <= SEEK_FLASH_CHAIN_MS) previous + 10 else 10

/** The dock's focus, its controls' bounds (viewer pixels), a slider being dragged and the seek flash. */
@Stable
internal class DockState {
    var slot by mutableStateOf(DockSlot.SEEK)
    var dragging by mutableStateOf(false)
    val bounds = mutableStateMapOf<DockSlot, Rect>()
    var dock by mutableStateOf(Rect.Zero)
    var close by mutableStateOf(Rect.Zero)
    val ring = Animatable(Rect.Zero, Rect.VectorConverter)
    var ringPlaced = false

    var flashSide by mutableIntStateOf(0)
        private set
    var flashTotal by mutableIntStateOf(0)
        private set
    private var flashAt = 0L
    val flash = Animatable(1f)

    /** A seek of 10 s to [side] (−1 back, 1 forward): the arc on that side flashes and counts the chain up. */
    suspend fun flash(side: Int, reduced: Boolean) {
        val now = SystemClock.uptimeMillis()
        flashTotal = seekFlashTotal(flashTotal, flashSide, flashAt, side, now)
        flashSide = side
        flashAt = now
        flash.snapTo(0f)
        flash.animateTo(1f, tween(if (reduced) 600 else 700))
    }
}

/** Records a control's bounds in viewer pixels ([origin]: the viewer's position in this window). */
private fun Modifier.dockBounds(state: DockState, slot: DockSlot, origin: Offset): Modifier = onGloballyPositioned {
    val r = it.boundsInWindow().translate(-origin)
    if (state.bounds[slot] != r) state.bounds[slot] = r
}

/** The rectangle the focus ring hugs for [slot]: buttons grow with their 1.08 focus scale, the sliders keep a margin. */
internal fun ringRect(slot: DockSlot, bounds: Rect, k: Float): Rect = when (slot) {
    DockSlot.SEEK -> bounds.inflate(10f * k)
    DockSlot.VOLUME -> bounds.inflate(6f * k)
    else -> bounds.inflate(bounds.width * .04f + 4f * k)
}

/**
 * The viewer's overlay, variant A · Dock (build/player-proto/a.html): a tall translucent glass dock along the bottom with the timeline
 * (elapsed, buffered and played, remaining), then the count and title left, the transport centred and the sound right; on a screenshot
 * the transport is previous, a page indicator and next. A glass close button sits top right. [overlay] is the overlay's visibility
 * (0–1, read in draw and layer blocks only); [cover] how much a Compose picture lies under the dock (the thumbnail before the video
 * plays: its blur is Compose's, over the playing video it is the page's own, [OverviewVideoPlayer.glass]).
 */
@Composable
internal fun ViewerDock(
    state: DockState,
    media: List<OverviewModel.Media>,
    shown: Int,
    player: OverviewVideoPlayer?,
    haze: HazeState,
    inWindow: Boolean,
    origin: Offset,
    reduced: Boolean,
    overlay: () -> Float,
    cover: () -> Float,
    onGo: (Int) -> Unit,
    onClose: () -> Unit,
    onActivate: (DockSlot) -> Unit,
) {
    val colors = LocalDelosColors.current
    val stage = LocalStage.current
    val k = stageUnit()
    val kind = ControllerGlyphs.kind
    val ringOn = kind == InputKind.CONTROLLER || kind == InputKind.KEYBOARD
    fun focused(slot: DockSlot) = ringOn && state.slot == slot
    val item = media.getOrNull(shown)
    Box(Modifier.fillMaxSize()) {
        // Shades so the white text reads over a bright picture
        Box(Modifier.align(Alignment.BottomCenter).fillMaxWidth().height(520.px).graphicsLayer { alpha = overlay() }
            .background(Brush.verticalGradient(0f to Color.Transparent, .6f to Color.Black.copy(alpha = .18f), 1f to Color.Black.copy(alpha = .38f))))
        Box(Modifier.fillMaxWidth().height(220.px).graphicsLayer { alpha = overlay() }
            .background(Brush.verticalGradient(0f to Color.Black.copy(alpha = .35f), 1f to Color.Transparent)))
        // Close: a glass button top right
        Box(
            Modifier.align(Alignment.TopEnd).padding(top = 40.px, end = 56.px)
                .onGloballyPositioned { val r = it.boundsInWindow().translate(-origin); if (state.close != r) state.close = r }
                .graphicsLayer { if (!reduced) translationY = -(1f - overlay()) * 14f * k },
        ) {
            DockGlass(haze, inWindow, player, 38f, overlay, cover, Modifier.matchParentSize())
            DockButton(state, DockSlot.CLOSE, Symbols.CLOSE, 76f, stringResource(R.string.shell_hint_close), focused(DockSlot.CLOSE), origin, reduced,
                look = ButtonLook.GLASS, alpha = overlay, onClick = onClose)
        }
        val bottom = 92f + 28f + stage.safe.bottom
        Box(
            Modifier.align(Alignment.BottomCenter).padding(start = 64.px, end = 64.px, bottom = bottom.px).fillMaxWidth()
                .onGloballyPositioned { val r = it.boundsInWindow().translate(-origin); if (state.dock != r) state.dock = r }
                // A tap on the dock between its controls stays on the dock (it does not toggle the overlay)
                .pointerInput(Unit) { detectTapGestures { } },
        ) {
            DockGlass(haze, inWindow, player, 46f, overlay, cover, Modifier.matchParentSize())
            Column(
                Modifier.padding(start = 44.px, end = 44.px, top = 24.px, bottom = 30.px)
                    .graphicsLayer { alpha = overlay(); if (!reduced) translationY = (1f - overlay()) * 30f * k },
            ) {
                if (player != null) SeekRow(state, player, focused(DockSlot.SEEK), origin)
                Row(
                    Modifier.padding(top = if (player != null) 14.px else 0.px).fillMaxWidth().heightIn(min = (if (player != null) 128 else 96).px),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    // Equal weights either side keep the transport exactly in the middle of the dock
                    Box(Modifier.weight(1f).padding(end = 30.px)) {
                        Row(verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(10.px)) {
                            BasicText((shown + 1).toString(), Modifier.alignByBaseline(),
                                style = TextStyle(color = colors.accent, fontFamily = Unbounded, fontWeight = FontWeight.Bold, fontSize = 34.tpx))
                            val name = when {
                                item == null -> ""
                                item.kind == OverviewModel.MediaKind.VIDEO -> item.name ?: stringResource(R.string.overview_video)
                                else -> stringResource(R.string.overview_screenshot, item.number)
                            }
                            BasicText(stringResource(R.string.overview_viewer_count, media.size, name), Modifier.alignByBaseline(),
                                style = TextStyle(color = colors.text, fontFamily = Figtree, fontWeight = FontWeight.Bold, fontSize = 28.tpx),
                                maxLines = 1, overflow = TextOverflow.Ellipsis)
                        }
                    }
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(26.px)) {
                        DockButton(state, DockSlot.PREVIOUS, Symbols.PLAYER_PREVIOUS, 76f, stringResource(R.string.overview_previous),
                            focused(DockSlot.PREVIOUS), origin, reduced, enabled = shown > 0) { onGo(shown - 1) }
                        if (player != null) {
                            DockButton(state, DockSlot.BACK, Symbols.PLAYER_SEEK_BACK, 92f, stringResource(R.string.overview_video_back),
                                focused(DockSlot.BACK), origin, reduced, ten = true) { onActivate(DockSlot.BACK) }
                            DockButton(state, DockSlot.PLAY, if (player.playing) Symbols.PLAYER_PAUSE else Symbols.PLAYER_PLAY, 124f,
                                stringResource(if (player.playing) R.string.overview_hint_pause else R.string.overview_hint_play),
                                focused(DockSlot.PLAY), origin, reduced, look = ButtonLook.ACCENT) { onActivate(DockSlot.PLAY) }
                            DockButton(state, DockSlot.FORWARD, Symbols.PLAYER_SEEK_FORWARD, 92f, stringResource(R.string.overview_video_forward),
                                focused(DockSlot.FORWARD), origin, reduced, ten = true) { onActivate(DockSlot.FORWARD) }
                        } else PageDots(media, shown, reduced, onGo)
                        DockButton(state, DockSlot.NEXT, Symbols.PLAYER_NEXT, 76f, stringResource(R.string.overview_next),
                            focused(DockSlot.NEXT), origin, reduced, enabled = shown < media.lastIndex) { onGo(shown + 1) }
                    }
                    Box(Modifier.weight(1f).padding(start = 30.px), contentAlignment = Alignment.CenterEnd) {
                        if (player != null) Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.px)) {
                            val icon = when {
                                player.muted -> Symbols.PLAYER_MUTED
                                player.volume == 0 -> Symbols.PLAYER_VOLUME_NONE
                                player.volume < 50 -> Symbols.PLAYER_VOLUME_LOW
                                else -> Symbols.PLAYER_SOUND
                            }
                            DockButton(state, DockSlot.MUTE, icon, 72f,
                                stringResource(if (player.muted) R.string.overview_hint_unmute else R.string.overview_hint_mute),
                                focused(DockSlot.MUTE), origin, reduced) { onActivate(DockSlot.MUTE) }
                            VolumeSlider(state, player, focused(DockSlot.VOLUME), origin)
                        }
                    }
                }
            }
        }
    }
}

/**
 * The dock's glass: a tint of 0.20 over a real 40 px blur of the Compose picture under it (a screenshot, a video's thumbnail), a top
 * highlight and a hairline, each fading by its own opacity. In the player's window, once the thumbnail is gone, the page draws the
 * blurred glass over the video; a page that cannot blur gets a darker flat tint here instead.
 */
@Composable
private fun DockGlass(haze: HazeState, inWindow: Boolean, player: OverviewVideoPlayer?, corner: Float, overlay: () -> Float, cover: () -> Float, modifier: Modifier) {
    val shape = RoundedCornerShape(corner.px)
    val k = stageUnit()
    val tint = Color(10, 16, 32)
    Box(modifier.graphicsLayer { alpha = overlay() }.dropShadow(shape) {
        radius = 80f * k
        spread = -24f * k
        offset = Offset(0f, 30f * k)
        color = Color.Black
        alpha = .6f
    })
    Box(modifier.graphicsLayer { alpha = overlay() * cover() }.clip(shape).backdropBlur(haze, 40f, tint.copy(alpha = .2f)))
    if (inWindow && player?.glassSupported != true) Box(modifier.graphicsLayer { alpha = overlay() * (1f - cover()) }.clip(shape).background(tint.copy(alpha = .45f)))
    Box(
        modifier.graphicsLayer { alpha = overlay() }
            .background(Brush.verticalGradient(0f to Color.White.copy(alpha = .1f), .3f to Color.White.copy(alpha = .02f), 1f to Color.Transparent), shape)
            .border(1.px, Color.White.copy(alpha = .14f), shape),
    )
}

private enum class ButtonLook { PLAIN, ACCENT, GLASS }

/**
 * A round button of the dock, [size] stage pixels. Its glyph is centred by its ink, not its font box: the ink's bounds centre (the play
 * triangle: its ink centroid, the usual optical nudge to the right) sits on the button's centre ([CenteredGlyph]). [ten]: the ±10 s
 * arrows carry a drawn "10". Focused: 1.08 by a spring, a lighter fill, the Fill glyph.
 */
@Composable
private fun DockButton(
    state: DockState,
    slot: DockSlot,
    icon: String,
    size: Float,
    description: String,
    focused: Boolean,
    origin: Offset,
    reduced: Boolean,
    look: ButtonLook = ButtonLook.PLAIN,
    ten: Boolean = false,
    enabled: Boolean = true,
    alpha: () -> Float = { 1f },
    onClick: () -> Unit,
) {
    val colors = LocalDelosColors.current
    val press = rememberPressPulse()
    val scale by animateFloatAsState(if (focused && !reduced) 1.08f else 1f, spring(dampingRatio = .6f, stiffness = 220f), label = "dockButton")
    val fill = when (look) {
        ButtonLook.ACCENT -> colors.accent
        ButtonLook.GLASS -> if (focused) Color.White.copy(alpha = .26f) else Color.Transparent
        ButtonLook.PLAIN -> Color.White.copy(alpha = if (focused) .2f else .08f)
    }
    val ink = if (look == ButtonLook.ACCENT) colors.onAccent else colors.text
    Box(
        Modifier
            .dockBounds(state, slot, origin)
            .size(size.px)
            .graphicsLayer {
                this.alpha = alpha() * if (enabled) 1f else .35f
                scaleX = scale
                scaleY = scale
            }
            .then(if (look == ButtonLook.ACCENT) Modifier.dropShadow(CircleShape) {
                radius = 40f * size / 124f
                offset = Offset(0f, 14f)
                color = colors.accent
                this.alpha = .45f
            } else Modifier)
            .hoverLook(999f, press)
            .pressLook(press)
            .clip(CircleShape)
            .background(fill)
            .then(if (look == ButtonLook.PLAIN) Modifier.border(1.px, Color.White.copy(alpha = .12f), CircleShape) else Modifier)
            .semantics { contentDescription = description }
            .pointerInput(onClick, enabled) { detectTapGestures { if (enabled) press.afterSqueeze(onClick) } },
    ) {
        val glyph = size * if (ten) .62f else .44f
        CenteredGlyph(icon, glyph, ink, filled = focused, centroid = icon == Symbols.PLAYER_PLAY, modifier = Modifier.fillMaxSize())
        if (ten) CenteredText("10", size * .19f, ink, Modifier.fillMaxSize())
    }
}

/** The ink of a glyph drawn at the origin (baseline left): its bounds' centre and its alpha centroid, in pixels. */
internal class GlyphInk(val boundsX: Float, val boundsY: Float, val centroidX: Float, val centroidY: Float)

/** Measures glyphs once per text, typeface and size by drawing them into a bitmap (the font's own metrics do not centre an icon). */
internal object GlyphInks {
    private val cache = HashMap<Triple<String, Typeface, Int>, GlyphInk>()

    fun of(text: String, typeface: Typeface, size: Float): GlyphInk = cache.getOrPut(Triple(text, typeface, size.roundToInt())) {
        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply { this.typeface = typeface; textSize = size }
        val w = (paint.measureText(text) + size * 2f).toInt().coerceAtLeast(4)
        val h = (size * 3f).toInt().coerceAtLeast(4)
        val ox = size
        val oy = size * 2f
        val bitmap = Bitmap.createBitmap(w, h, Bitmap.Config.ALPHA_8)
        android.graphics.Canvas(bitmap).drawText(text, ox, oy, paint)
        val alpha = ByteArray(w * h)
        bitmap.copyPixelsToBuffer(java.nio.ByteBuffer.wrap(alpha))
        bitmap.recycle()
        var minX = w; var minY = h; var maxX = -1; var maxY = -1
        var sum = 0.0; var sx = 0.0; var sy = 0.0
        for (y in 0 until h) for (x in 0 until w) {
            val a = alpha[y * w + x].toInt() and 0xFF
            if (a == 0) continue
            if (x < minX) minX = x
            if (x > maxX) maxX = x
            if (y < minY) minY = y
            if (y > maxY) maxY = y
            sum += a; sx += a * (x + .5); sy += a * (y + .5)
        }
        if (maxX < 0) GlyphInk(0f, 0f, 0f, 0f)
        else GlyphInk((minX + maxX + 1) / 2f - ox, (minY + maxY + 1) / 2f - oy, (sx / sum).toFloat() - ox, (sy / sum).toFloat() - oy)
    }
}

private object DockFonts {
    private var regular: Typeface? = null
    private var fill: Typeface? = null
    private var figtree: Typeface? = null

    fun regular(context: Context) = regular ?: ResourcesCompat.getFont(context, R.font.phosphor_regular)!!.also { regular = it }
    fun fill(context: Context) = fill ?: ResourcesCompat.getFont(context, R.font.phosphor_fill)!!.also { fill = it }
    fun figtree(context: Context) = figtree ?: Typeface.create(ResourcesCompat.getFont(context, R.font.figtree)!!, 800, false).also { figtree = it }
}

/**
 * A Phosphor glyph centred by its ink in this box: the ink's bounds centre (or, [centroid], its alpha centroid) on the box's centre, so
 * every glyph sits optically in the middle of its button whatever its side bearings. [size] is in stage pixels (scales with the text);
 * [filled] crossfades Regular to Fill.
 */
@Composable
internal fun CenteredGlyph(icon: String, size: Float, color: Color, filled: Boolean, modifier: Modifier, centroid: Boolean = false) {
    val context = LocalContext.current
    val stage = LocalStage.current
    val k = stageUnit()
    val f by animateFloatAsState(if (filled) 1f else 0f, tween(350), label = "dockGlyphFill")
    val regular = DockFonts.regular(context)
    val fill = DockFonts.fill(context)
    val paint = remember { Paint(Paint.ANTI_ALIAS_FLAG) }
    Canvas(modifier) {
        val px = size * k * stage.textScale
        if (f < 1f) drawCentered(paint, icon, regular, px, color.copy(alpha = color.alpha * (1f - f)), centroid)
        if (f > 0f) drawCentered(paint, icon, fill, px, color.copy(alpha = color.alpha * f), centroid)
    }
}

/** [text] in Figtree ExtraBold, centred by its ink (the "10" inside the ±10 s arrows). */
@Composable
private fun CenteredText(text: String, size: Float, color: Color, modifier: Modifier) {
    val context = LocalContext.current
    val stage = LocalStage.current
    val k = stageUnit()
    val face = DockFonts.figtree(context)
    val paint = remember { Paint(Paint.ANTI_ALIAS_FLAG) }
    Canvas(modifier) { drawCentered(paint, text, face, size * k * stage.textScale, color, centroid = false) }
}

/** Draws [text] with its ink (bounds centre, or [centroid]) on [at] (default: the centre of this draw scope). */
private fun DrawScope.drawCentered(paint: Paint, text: String, face: Typeface, px: Float, color: Color, centroid: Boolean, at: Offset = center) {
    val ink = GlyphInks.of(text, face, px)
    paint.typeface = face
    paint.textSize = px
    paint.color = color.toArgb()
    val x = at.x - if (centroid) ink.centroidX else ink.boundsX
    val y = at.y - if (centroid) ink.centroidY else ink.boundsY
    drawIntoCanvas { it.nativeCanvas.drawText(text, x, y, paint) }
}

/** The timeline row: the elapsed time, the seek bar (buffered and played), the remaining time. */
@Composable
private fun SeekRow(state: DockState, player: OverviewVideoPlayer, focused: Boolean, origin: Offset) {
    val colors = LocalDelosColors.current
    val time = TextStyle(color = colors.text2, fontFamily = Figtree, fontWeight = FontWeight.Bold, fontSize = 22.tpx, fontFeatureSettings = "tnum")
    Row(Modifier.fillMaxWidth().height(52.px), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(22.px)) {
        BasicText(videoTime(player.time), Modifier.width(104.px), style = time.copy(textAlign = TextAlign.End))
        SeekBar(state, player, focused, origin, Modifier.weight(1f))
        BasicText("-" + videoTime((player.duration - player.time).coerceAtLeast(0f)), Modifier.width(104.px), style = time) // l10n-ignore
    }
}

/**
 * The seek bar: a tap seeks there, a drag scrubs (seeking on release), the mouse hovering it shows the time under the pointer. It
 * thickens with the focus, a drag or the hover, and shows its knob then (always for a finger).
 */
@Composable
private fun SeekBar(state: DockState, player: OverviewVideoPlayer, focused: Boolean, origin: Offset, modifier: Modifier) {
    val colors = LocalDelosColors.current
    val k = stageUnit()
    var drag by remember { mutableStateOf<Float?>(null) }
    var hover by remember { mutableStateOf<Float?>(null) }
    val touch = ControllerGlyphs.kind == InputKind.TOUCH
    val active = focused || drag != null || hover != null
    val thick by animateFloatAsState(if (active) 16f else 10f, tween(300), label = "seekThick")
    val knob by animateFloatAsState(if (active || touch) 1f else 0f, spring(dampingRatio = .55f, stiffness = 400f), label = "seekKnob")
    Box(
        modifier.height(44.px)
            .dockBounds(state, DockSlot.SEEK, origin)
            .semantics { contentDescription = videoTime(player.time) }
            .pointerInput(player) { detectTapGestures { o -> if (size.width > 0) player.seekTo(o.x / size.width) } }
            .pointerInput(player) {
                detectHorizontalDragGestures(
                    onDragStart = { o -> drag = (o.x / size.width).coerceIn(0f, 1f); state.dragging = true },
                    onDragEnd = { drag?.let(player::seekTo); drag = null; state.dragging = false },
                    onDragCancel = { drag = null; state.dragging = false },
                ) { change, amount ->
                    change.consume()
                    drag = ((drag ?: 0f) + amount / size.width).coerceIn(0f, 1f)
                }
            }
            .pointerInput(Unit) {
                awaitPointerEventScope {
                    while (true) {
                        val event = awaitPointerEvent()
                        val x = event.changes.firstOrNull()?.position?.x ?: continue
                        hover = when {
                            ControllerGlyphs.kind != InputKind.POINTER || event.type == PointerEventType.Exit -> null
                            else -> (x / size.width).coerceIn(0f, 1f)
                        }
                    }
                }
            },
        contentAlignment = Alignment.CenterStart,
    ) {
        Canvas(Modifier.fillMaxSize()) {
            val t = thick * k
            val y = size.height / 2f
            val r = CornerRadius(t / 2f)
            val d = player.duration
            val played = drag ?: if (d > 0f) (player.time / d).coerceIn(0f, 1f) else 0f
            drawRoundRect(Color.White.copy(alpha = .18f), Offset(0f, y - t / 2f), Size(size.width, t), r)
            if (player.loaded > 0f) drawRoundRect(Color.White.copy(alpha = .32f), Offset(0f, y - t / 2f), Size(size.width * player.loaded, t), r)
            if (played > 0f) drawRoundRect(colors.accent, Offset(0f, y - t / 2f), Size(size.width * played, t), r)
            if (knob > 0f) {
                val c = Offset(size.width * played, y)
                drawCircle(Color.Black.copy(alpha = .35f * knob), 14f * k * knob, c + Offset(0f, 2f * k))
                drawCircle(Color.White, 12f * k * knob, c)
            }
        }
        hover?.let { at ->
            val label = videoTime(at * player.duration)
            Box(
                Modifier.offset { IntOffset(0, (-46f * k).roundToInt()) }
                    .layoutCentredAt(at)
                    .clip(RoundedCornerShape(10.px))
                    .background(Color(10, 16, 32).copy(alpha = .8f))
                    .padding(horizontal = 12.px, vertical = 6.px),
            ) { BasicText(label, style = TextStyle(color = colors.text, fontFamily = Figtree, fontWeight = FontWeight.Bold, fontSize = 20.tpx, fontFeatureSettings = "tnum")) }
        }
    }
}

/** Places this child with its horizontal centre at [fraction] of the parent's width. */
private fun Modifier.layoutCentredAt(fraction: Float): Modifier = layout { measurable, constraints ->
    val p = measurable.measure(constraints.copy(minWidth = 0, minHeight = 0))
    layout(constraints.maxWidth, p.height) { p.place((constraints.maxWidth * fraction - p.width / 2f).roundToInt(), 0) }
}

/** The volume: a 170 px slider; a tap or a drag sets it, Left/Right on a controller step it by 10. */
@Composable
private fun VolumeSlider(state: DockState, player: OverviewVideoPlayer, focused: Boolean, origin: Offset) {
    val colors = LocalDelosColors.current
    val k = stageUnit()
    val knob by animateFloatAsState(if (focused) 1.25f else 1f, spring(dampingRatio = .55f, stiffness = 400f), label = "volumeKnob")
    val description = stringResource(R.string.overview_video_volume)
    Box(
        Modifier.size(170.px, 44.px)
            .dockBounds(state, DockSlot.VOLUME, origin)
            .semantics { contentDescription = description }
            .pointerInput(player) { detectTapGestures { o -> player.changeVolume((o.x / size.width * 100f).roundToInt()) } }
            .pointerInput(player) {
                detectHorizontalDragGestures(
                    onDragStart = { state.dragging = true },
                    onDragEnd = { state.dragging = false },
                    onDragCancel = { state.dragging = false },
                ) { change, _ ->
                    change.consume()
                    player.changeVolume((change.position.x / size.width * 100f).roundToInt())
                }
            },
    ) {
        Canvas(Modifier.fillMaxSize()) {
            val t = 8f * k
            val y = size.height / 2f
            val v = if (player.muted) 0f else player.volume / 100f
            drawRoundRect(Color.White.copy(alpha = .18f), Offset(0f, y - t / 2f), Size(size.width, t), CornerRadius(t / 2f))
            if (v > 0f) drawRoundRect(colors.text, Offset(0f, y - t / 2f), Size(size.width * v, t), CornerRadius(t / 2f))
            val c = Offset(size.width * v, y)
            drawCircle(Color.Black.copy(alpha = .35f), 12f * k * knob, c + Offset(0f, 2f * k))
            drawCircle(Color.White, 11f * k * knob, c)
        }
    }
}

/** A screenshot's page indicator: one dot per item (videos as rings), the current one a gold bar; a tap on a dot goes there. */
@Composable
private fun PageDots(media: List<OverviewModel.Media>, shown: Int, reduced: Boolean, onGo: (Int) -> Unit) {
    val colors = LocalDelosColors.current
    val shape = RoundedCornerShape(38.px)
    Row(
        Modifier.height(76.px).clip(shape).background(Color.White.copy(alpha = .08f)).border(1.px, Color.White.copy(alpha = .12f), shape)
            .padding(horizontal = 23.px),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        media.forEachIndexed { i, item ->
            val on = i == shown
            val width by animateFloatAsState(if (on) 40f else 12f, if (reduced) tween(0) else spring(dampingRatio = .6f, stiffness = 300f), label = "pageDot")
            Box(
                Modifier.height(76.px).padding(horizontal = 7.px).pointerInput(i) { detectTapGestures { onGo(i) } },
                contentAlignment = Alignment.Center,
            ) {
                val dot = Modifier.size(width.px, 12.px).clip(CircleShape)
                when {
                    on -> Box(dot.background(colors.accent))
                    item.kind == OverviewModel.MediaKind.VIDEO -> Box(dot.border(2.px, Color.White.copy(alpha = .55f), CircleShape))
                    else -> Box(dot.background(Color.White.copy(alpha = .4f)))
                }
            }
        }
    }
}

/** The gold focus ring of the controller and the keyboard: it glides between the dock's stops ([DockState.ring], a spring). */
@Composable
internal fun DockRing(state: DockState, visible: () -> Float) {
    val colors = LocalDelosColors.current
    val k = stageUnit()
    Canvas(Modifier.fillMaxSize()) {
        val a = visible()
        val r = state.ring.value
        if (a <= 0f || r.isEmpty) return@Canvas
        val corner = CornerRadius(r.height / 2f)
        drawRoundRect(colors.accent.copy(alpha = .28f * a), r.topLeft, r.size, corner, style = Stroke(14f * k))
        drawRoundRect(colors.accent.copy(alpha = a), r.topLeft, r.size, corner, style = Stroke(4f * k))
    }
}

/** The ring's spring: about 560 ms with a slight overshoot. */
internal val DockRingSpring = spring(dampingRatio = .72f, stiffness = 126f, visibilityThreshold = Rect(.5f, .5f, .5f, .5f))

/**
 * The seek flash (variant B's arcs): a glass wedge of a ring around the picture's centre on the side of the seek, flashing gold, with the
 * seek glyph and the running total (10, 20…). It shows whether or not the dock does.
 */
@Composable
internal fun SeekFlash(state: DockState, reduced: Boolean) {
    val colors = LocalDelosColors.current
    val context = LocalContext.current
    val stage = LocalStage.current
    val k = stageUnit()
    val paint = remember { Paint(Paint.ANTI_ALIAS_FLAG) }
    val regular = DockFonts.fill(context)
    val figtree = DockFonts.figtree(context)
    Canvas(Modifier.fillMaxSize()) {
        val t = state.flash.value
        if (t >= 1f || state.flashSide == 0) return@Canvas
        val a = when {
            t < .18f -> t / .18f
            t < .7f -> 1f
            else -> 1f - (t - .7f) / .3f
        }
        val side = state.flashSide
        val c = center
        val outer = 232f * k
        val inner = 128f * k
        val half = 44f
        val mid = if (side < 0) 180f else 0f
        val path = Path().apply {
            arcTo(Rect(c, outer), mid - half, half * 2f, forceMoveTo = true)
            arcTo(Rect(c, inner), mid + half, -half * 2f, forceMoveTo = false)
            close()
        }
        drawPath(path, Color(10, 16, 32).copy(alpha = .4f * a))
        drawPath(path, colors.accent.copy(alpha = .34f * a))
        drawPath(path, Color.White.copy(alpha = .14f * a), style = Stroke(1.5f * k))
        // The label nudges outward as it lands, unless motion is reduced
        val nudge = if (reduced) 0f else sin((t.coerceAtMost(.6f) / .6f) * PI.toFloat()) * 8f * k
        val lx = c.x + side * (180f * k + nudge)
        val icon = if (side < 0) Symbols.PLAYER_SEEK_BACK else Symbols.PLAYER_SEEK_FORWARD
        val glyph = 44f * k * stage.textScale
        drawCentered(paint, icon, regular, glyph, colors.accent.copy(alpha = a), centroid = false, at = Offset(lx, c.y - 18f * k))
        drawCentered(paint, state.flashTotal.toString(), figtree, 28f * k * stage.textScale, colors.text.copy(alpha = a), centroid = false, at = Offset(lx, c.y + 30f * k))
    }
}

