package com.limelight.shell.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.dropShadow
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.layout
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.layout.FirstBaseline
import androidx.compose.ui.layout.LastBaseline
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.em
import com.limelight.shell.data.DetailAgeRating
import com.limelight.shell.ui.ControllerGlyphs
import com.limelight.shell.ui.DelosColors
import com.limelight.shell.ui.Figtree
import com.limelight.shell.ui.InputKind
import com.limelight.shell.ui.LocalDelosColors
import com.limelight.shell.ui.LocalStage
import com.limelight.shell.ui.Symbol
import com.limelight.shell.ui.Unbounded
import com.limelight.shell.ui.backdropBlur
import com.limelight.shell.ui.hsl
import com.limelight.shell.ui.px
import com.limelight.shell.ui.stageUnit
import com.limelight.shell.ui.glass
import com.limelight.shell.ui.tpx
import dev.chrisbanes.haze.HazeState
import java.text.NumberFormat
import java.util.Locale
import kotlin.math.min

/** The small caps label over a value (`.eyebrow`): 15 px bold, tracked .16 em, in the accent color or dimmed. */
@Composable
internal fun OverviewEyebrow(text: String, color: Color, modifier: Modifier = Modifier, size: Int = 15) {
    BasicText(
        text.uppercase(Locale.getDefault()), modifier, maxLines = 1, overflow = TextOverflow.Ellipsis,
        style = TextStyle(color = color, fontFamily = Figtree, fontWeight = FontWeight.Bold, fontSize = size.tpx, letterSpacing = .16.em),
    )
}

/** A section's heading (`.h-sec`): its icon in the second accent, the title in Unbounded, an optional count beside it. */
@Composable
internal fun SectionHeading(icon: String, title: String, count: Int? = null) {
    val colors = LocalDelosColors.current
    Row(Modifier.padding(bottom = 28.px), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.px)) {
        Symbol(icon, 32.tpx, colors.accent2)
        BasicText(title, style = TextStyle(color = colors.text, fontFamily = Unbounded, fontWeight = FontWeight.SemiBold, fontSize = 30.tpx, letterSpacing = (-.005).em))
        if (count != null) OverviewEyebrow(count.toString(), colors.text3, Modifier.padding(start = 6.px))
    }
}

/** The look of a tag: a neutral one, or a genre in the game's hue (`.tag.g`). */
internal enum class TagLook { PLAIN, GENRE }

/** A tag (`.tag`): 40 px high, an optional icon, 19 px text. */
@Composable
internal fun OverviewTag(icon: String?, label: String, look: TagLook = TagLook.PLAIN, hue: Float = 200f) {
    val colors = LocalDelosColors.current
    val genre = look == TagLook.GENRE
    val fill = if (genre) hsl(hue, 70f, 34f, .3f) else Color.White.copy(alpha = .06f)
    val border = if (genre) hsl(hue, 70f, 55f, .5f) else colors.line
    val content = if (genre) hsl(hue, 80f, 86f) else colors.text2
    Row(
        Modifier.height(40.px).clip(CircleShape).glass(fill).border(1.px, border, CircleShape).padding(horizontal = 16.px),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.px),
    ) {
        if (icon != null) Symbol(icon, 22.tpx, if (genre) content else colors.accent2)
        BasicText(label, maxLines = 1, style = TextStyle(color = content, fontFamily = Figtree, fontSize = 19.tpx))
    }
}

/**
 * A rating ring (`.rating`): the arc fills and the number counts up on the ring's spring ([OverviewMotion.ring]) as [millis] (the
 * ratings' clock) runs, ring [index] of the row, read while drawing; the ring grows into place with them. [value] null draws the empty
 * track with a dash. The label and [sub] stand beside it.
 */
@Composable
internal fun RatingRing(
    value: Double?, label: String, sub: String, color: Color, millis: () -> Float, index: Int, reduced: Boolean, size: Float = 104f,
) {
    val colors = LocalDelosColors.current
    val measurer = rememberTextMeasurer()
    val k = stageUnit()
    val valueStyle = TextStyle(color = if (value == null) colors.text3 else colors.text, fontFamily = Unbounded, fontWeight = FontWeight.Bold, fontSize = (size * if (value == null) .2f else .3f).tpx)
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(18.px)) {
        Box(
            Modifier
                .size(size.px)
                .graphicsLayer {
                    val t = millis()
                    val s = OverviewMotion.ringScale(OverviewMotion.ring(t, index, reduced), reduced)
                    scaleX = s
                    scaleY = s
                    alpha = OverviewMotion.ringAlpha(t, reduced)
                }
                .drawBehind {
                    val stroke = 8f * k
                    val radius = size * k / 2f - 8f * k
                    val topLeft = Offset(this.size.width / 2f - radius, this.size.height / 2f - radius)
                    drawCircle(Color.White.copy(alpha = .1f), radius, style = Stroke(stroke))
                    val fill = OverviewMotion.ring(millis(), index, reduced)
                    if (value != null && fill > 0f) {
                        val sweep = min(360f, 360f * value.toFloat() / 100f * fill)
                        drawArc(color, -90f, sweep, false, topLeft, Size(radius * 2f, radius * 2f), style = Stroke(stroke, cap = StrokeCap.Round))
                    }
                    val text = if (value == null) "\u2014" else OverviewMotion.ringNumber(value, fill).toString()
                    val layout = measurer.measure(text, valueStyle)
                    drawText(layout, topLeft = Offset((this.size.width - layout.size.width) / 2f, (this.size.height - layout.size.height) / 2f))
                },
        )
        Column(verticalArrangement = Arrangement.spacedBy(4.px)) {
            BasicText(label, style = TextStyle(color = colors.text, fontFamily = Figtree, fontWeight = FontWeight.Bold, fontSize = 22.tpx))
            BasicText(sub, maxLines = 1, style = TextStyle(color = colors.text3, fontFamily = Figtree, fontSize = 18.tpx))
        }
    }
}

/** The badge colors of an age rating: PEGI's traffic lights, ESRB's white plate, a neutral plate for the others. */
private fun ageColors(rating: DetailAgeRating, colors: DelosColors): Pair<Color, Color> = when (rating.organization.uppercase(Locale.ROOT)) {
    "PEGI" -> when (rating.rating) {
        "3", "7" -> Color(0xFF74B72E)
        "12", "16" -> Color(0xFFF39200)
        else -> Color(0xFFE0322B)
    } to Color.White
    "ESRB" -> Color.White to Color(0xFF111111)
    else -> colors.surface3 to colors.text
}

/**
 * An age rating (`.age`): its badge, the organization and mark, the content descriptors under it. [rating] null: IGDB lists none
 * ([notRated] over [noRating]). [badgeScale] is read while drawing (the badge's pop).
 */
@Composable
internal fun AgeRatingView(rating: DetailAgeRating?, notRated: String, noRating: String, noDescriptors: String, badgeScale: () -> Float = { 1f }) {
    val colors = LocalDelosColors.current
    val pop = Modifier.graphicsLayer {
        val s = badgeScale()
        scaleX = s
        scaleY = s
    }
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.px)) {
        if (rating == null) {
            Box(pop.size(54.px).clip(RoundedCornerShape(12.px)).glass(Color.White.copy(alpha = .08f)), contentAlignment = Alignment.Center) {
                BasicText("\u2014", style = TextStyle(color = colors.text3, fontFamily = Figtree, fontSize = 18.tpx))
            }
            Column {
                BasicText(notRated, style = TextStyle(color = colors.text, fontFamily = Figtree, fontSize = 22.tpx))
                BasicText(noRating, style = TextStyle(color = colors.text3, fontFamily = Figtree, fontSize = 16.tpx, lineHeight = 1.3.em))
            }
            return@Row
        }
        val (fill, ink) = ageColors(rating, colors)
        val esrb = rating.organization.equals("ESRB", ignoreCase = true)
        Box(
            pop.height(54.px).widthIn(min = 54.px).clip(RoundedCornerShape((if (esrb) 6 else 12).px)).background(fill).padding(horizontal = 8.px),
            contentAlignment = Alignment.Center,
        ) {
            BasicText(rating.rating, maxLines = 1, style = TextStyle(color = ink, fontFamily = Unbounded, fontWeight = FontWeight.Bold, fontSize = 22.tpx))
        }
        Column(Modifier.widthIn(max = 520.px)) {
            BasicText("${rating.organization} ${rating.rating}", style = TextStyle(color = colors.text, fontFamily = Figtree, fontSize = 22.tpx))
            BasicText(
                rating.descriptors.joinToString(", ").ifEmpty { noDescriptors }, maxLines = 2, overflow = TextOverflow.Ellipsis,
                style = TextStyle(color = colors.text3, fontFamily = Figtree, fontSize = 16.tpx, lineHeight = 1.3.em),
            )
        }
    }
}

/**
 * A number that counts up to [final] as [progress] goes from 0 to 1, read while drawing (a count-up never recomposes): it takes the
 * final text's size and draws the current value, formatted with as many decimals as [final] has.
 */
@Composable
internal fun CountText(final: String, value: Double, style: TextStyle, progress: () -> Float, modifier: Modifier = Modifier) {
    val measurer = rememberTextMeasurer()
    val locale = Locale.getDefault()
    val decimals = remember(final) { final.reversed().takeWhile { it.isDigit() }.length.takeIf { final.any { c -> !c.isDigit() } } ?: 0 }
    val format = remember(locale, decimals) { NumberFormat.getNumberInstance(locale).apply { minimumFractionDigits = decimals; maximumFractionDigits = decimals } }
    val layout = remember(final, style) { measurer.measure(final, style) }
    Box(
        modifier
            .layout { measurable, constraints ->
                val placeable = measurable.measure(constraints)
                // The final text's baselines, so the count lines up with the unit beside it
                layout(
                    layout.size.width, layout.size.height,
                    mapOf(FirstBaseline to layout.firstBaseline.toInt(), LastBaseline to layout.lastBaseline.toInt()),
                ) { placeable.place(0, 0) }
            }
            .drawBehind {
                val p = progress()
                val text = if (p >= 1f) final else format.format(value * p)
                drawText(measurer.measure(text, style))
            },
    )
}

/**
 * The overview's glass (`.glass`): the content behind blurred for real ([backdropBlur] over the page's backdrop, [haze]), the
 * light gradient on top, a hairline and the deep shadow.
 */
@Composable
internal fun Modifier.overviewGlass(haze: HazeState?, corner: Float): Modifier {
    val colors = LocalDelosColors.current
    val shape = RoundedCornerShape(corner.px)
    val k = stageUnit()
    val tint = if (colors.dark) Color(22, 32, 60).copy(alpha = .46f) else colors.surface
    return this
        .dropShadow(shape) {
            radius = 90f * k
            spread = -20f * k
            offset = Offset(0f, 40f * k)
            color = Color.Black
            alpha = .55f
        }
        .clip(shape)
        .then(if (haze != null) Modifier.backdropBlur(haze, 30f, tint) else Modifier.background(tint))
        .background(Brush.verticalGradient(0f to Color.White.copy(alpha = .13f), .52f to Color.White.copy(alpha = .03f), 1f to Color.White.copy(alpha = .03f)))
        .border(1.px, Color.White.copy(alpha = .1f), shape)
}

/** With a mouse the pointer moving onto a part focuses it (without scrolling the page: [onHover] marks it). */
internal fun Modifier.hoverFocus(requester: FocusRequester, onHover: () -> Unit): Modifier = pointerInput(requester) {
    awaitPointerEventScope {
        while (true) {
            val event = awaitPointerEvent()
            if (event.type == PointerEventType.Enter && ControllerGlyphs.kind == InputKind.POINTER) {
                onHover()
                runCatching { requester.requestFocus() }
            }
        }
    }
}

/** The text shadow of the header's lines over the hero (`text-shadow: 0 2px 8px rgba(0,0,0,.6)`). */
@Composable
internal fun headerShadow(): Shadow {
    val k = stageUnit()
    return Shadow(Color.Black.copy(alpha = .6f), Offset(0f, 2f * k), 8f * k)
}
