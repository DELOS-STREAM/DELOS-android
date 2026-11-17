package com.limelight.shell.ui

import androidx.compose.ui.geometry.Rect
import com.limelight.shell.StreamMenuLayout
import java.math.BigDecimal
import java.math.RoundingMode
import java.util.Locale

/*
 * The pure parts of the touch popups of settings rows (issue #92), unit tested in RowPickerLogicTest: what a slider does when stepped,
 * typed into or held, and where the popup card lies. The composables are in RowPicker.kt.
 */

/** The stepping, display and typed input of a [RowValue.Slider]. Values are in the slider's own unit (the bitrate in kbit/s), [RowValue.Slider.divisor] makes them readable (Mbit/s). */
internal object SliderEdit {
    /** [raw] moved to the nearest point of the slider's grid (`min + n * step`), then into `min..max`. */
    fun snap(slider: RowValue.Slider, raw: Int): Int {
        val step = slider.step.coerceAtLeast(1)
        val n = Math.round((raw.toLong() - slider.min).toDouble() / step)
        return (slider.min + n * step).coerceIn(slider.min.toLong(), slider.max.toLong()).toInt()
    }

    /** [steps] grid steps from [current] (negative: down), landing on the grid and inside the range. A value between two steps snaps to the grid. */
    fun step(slider: RowValue.Slider, current: Int, steps: Int): Int {
        val moved = current.toLong() + steps.toLong() * slider.step.coerceAtLeast(1)
        return snap(slider, moved.coerceIn(Int.MIN_VALUE.toLong(), Int.MAX_VALUE.toLong()).toInt())
    }

    /** The number of [value] as the player reads it: whole in the readable unit when it is, else one decimal. */
    fun numberText(slider: RowValue.Slider, value: Int, locale: Locale): String =
        if (value % slider.divisor == 0) (value / slider.divisor).toString() else String.format(locale, "%.1f", value.toDouble() / slider.divisor)

    /** The row's text: the number with its unit, or [RowValue.Slider.minText] at the lowest value. */
    fun label(slider: RowValue.Slider, value: Int, locale: Locale): String =
        if (value == slider.min && slider.minText != null) slider.minText else numberText(slider, value, locale) + slider.unit

    /**
     * What the player typed ([text], in the readable unit: Mbit/s for the bitrate, a comma or a point for decimals) as the slider's value: validated,
     * rounded to the grid and clamped to the range, so typing 400 on a slider that ends at 150 applies 150. Null for text that is no number (empty,
     * letters, a lone separator).
     */
    fun parse(slider: RowValue.Slider, text: String): Int? {
        val number = text.trim().replace(',', '.').toBigDecimalOrNull() ?: return null
        val raw = number.multiply(BigDecimal(slider.divisor)).setScale(0, RoundingMode.HALF_UP)
        val inRange = raw.max(BigDecimal(slider.min)).min(BigDecimal(slider.max)).toInt()
        return snap(slider, inRange)
    }

    /** True while [text] can still become a number by typing more: only digits and one decimal separator (the keyboard shows no other keys; pasted text is filtered by this). */
    fun typable(text: CharSequence): Boolean = text.count { it == '.' || it == ',' } <= 1 && text.all { it.isDigit() || it == '.' || it == ',' }
}

/** Holding a ◀ or ▶ button of the slider popup repeats it, faster the longer it is held (the same acceleration as a held key on the row). */
internal object HoldRepeat {
    /** The pause before the first repeat, and the pause between repeats, milliseconds. */
    const val FIRST_DELAY_MS = 380L
    const val INTERVAL_MS = 55L

    /** The steps of the [repeat]th repeat (counted from 1): [sliderAcceleration] of the key repeat with the same count. */
    fun steps(slider: RowValue.Slider, repeat: Int): Int = sliderAcceleration(slider, repeat)
}

/**
 * The card of a row's popup, stage pixels: its body ([x], [y], [width], [height]; the caret excluded) and its caret, which points sideways into the
 * row. The caret lies on the card's right edge when [caretRight], else on its left edge, [caretY] below the card's top, its tip at [tipX] on the stage
 * ([Float.NaN] caretY: no row to point at, no caret).
 */
internal data class PickerPlacement(
    val x: Float, val y: Float, val width: Float, val height: Float, val list: Float,
    val caretRight: Boolean = true, val caretY: Float = Float.NaN, val tipX: Float = Float.NaN,
) {
    val hasCaret: Boolean get() = !caretY.isNaN()
}

/**
 * The geometry of the contextual popover of a settings row, in stage pixels: the stream menu's popover card (glass, one outline with its caret) grown
 * out of the tapped row. The card is vertically centred on the row (kept inside the stage by margins) and lies beside the row's VALUE, on the side that
 * has the room: its caret points into the row at [VALUE_INSET] from the row's right edge, on the card's right edge when the card lies left of that point,
 * on its left edge when it lies right of it. A long options list scrolls.
 */
internal object PickerLayout {
    /** The options' card and the slider popup's: the slider's is about twice as wide, its buttons and number are for a finger. */
    const val OPTIONS_WIDTH = 560f
    const val VALUE_WIDTH = 1040f
    const val CORNER = StreamMenuLayout.POPOVER_RADIUS
    const val CARET_HEIGHT = StreamMenuLayout.CARET_HEIGHT
    const val CARET_WIDTH = StreamMenuLayout.CARET_WIDTH
    const val PAD = 20f
    const val TITLE = 36f
    const val OPTION = 64f
    const val GAP = 4f

    /** Where the caret's tip enters the row, from its right edge: on the row's value. */
    const val VALUE_INSET = 120f

    /** The card keeps this far from the stage's sides, and below the status bar / above the bottom edge by the margins the shell passes in. */
    const val SIDE_MARGIN = 24f
    const val TOP_MARGIN = 104f
    const val BOTTOM_MARGIN = 24f

    /** What the card has besides its options: padding above and below, the title and the gap under it. */
    const val CHROME = 2 * PAD + TITLE + GAP

    /** The room the focused option's 3 px ring needs above the first and below the last option, so the scrolling list does not cut it. */
    const val LIST_ROOM = 3f

    /**
     * The slider popup for a finger (stage pixels; 72 is 56 dp): padding, title, the number's field and the row of ◀ track ▶, with a gap between them.
     * The buttons are [VALUE_BUTTON] wide, the track is as high as the row of controls.
     */
    const val VALUE_FIELD = 88f
    const val VALUE_BUTTON = 96f
    const val VALUE_CONTROLS = 96f
    const val VALUE_GAP = 20f
    const val VALUE_SIDE = 36f
    const val VALUE_HEIGHT = 2 * PAD + TITLE + VALUE_FIELD + VALUE_CONTROLS + 2 * VALUE_GAP

    /** The height of the list of [count] options when nothing limits it. */
    fun listHeight(count: Int): Float = if (count <= 0) 0f else count * OPTION + (count - 1) * GAP + 2 * LIST_ROOM

    /** The card of an options list: [selected] lies over the row ([anchor], stage pixels; null: centred on the stage). */
    fun options(anchor: Rect?, count: Int, selected: Int, stageW: Float, stageH: Float, top: Float = TOP_MARGIN, bottom: Float = BOTTOM_MARGIN): PickerPlacement {
        val room = (stageH - top - bottom - CHROME).coerceAtLeast(OPTION)
        val list = listHeight(count).coerceAtMost(room)
        return card(anchor, CHROME + list, list, OPTIONS_WIDTH, stageW, stageH, top, bottom)
    }

    /** The card of the slider popup, its middle over the row. */
    fun value(anchor: Rect?, stageW: Float, stageH: Float, top: Float = TOP_MARGIN, bottom: Float = BOTTOM_MARGIN): PickerPlacement =
        card(anchor, VALUE_HEIGHT, 0f, VALUE_WIDTH, stageW, stageH, top, bottom)

    /**
     * A card of [height] centred vertically on the row, inside the margins, beside the point of the row's value on the side that has the room for
     * [wanted] (the left one when both have), narrowed to the room when neither has.
     */
    private fun card(anchor: Rect?, height: Float, list: Float, wanted: Float, stageW: Float, stageH: Float, top: Float, bottom: Float): PickerPlacement {
        val h = height.coerceAtMost((stageH - top - bottom).coerceAtLeast(OPTION))
        val limit = (stageW - 2 * SIDE_MARGIN).coerceAtLeast(OPTION)
        if (anchor == null) {
            val width = wanted.coerceAtMost(limit)
            return PickerPlacement((stageW - width) / 2f, ((stageH - h) / 2f).coerceIn(top, (stageH - bottom - h).coerceAtLeast(top)), width, h, list)
        }
        val point = anchor.right - VALUE_INSET
        val roomLeft = point - CARET_HEIGHT - SIDE_MARGIN
        val roomRight = stageW - SIDE_MARGIN - (point + CARET_HEIGHT)
        val onLeft = roomLeft >= wanted || (roomRight < wanted && roomLeft >= roomRight)
        val width = wanted.coerceAtMost(if (onLeft) roomLeft else roomRight).coerceAtLeast(OPTION)
        val x = (if (onLeft) point - CARET_HEIGHT - width else point + CARET_HEIGHT).coerceIn(SIDE_MARGIN, (stageW - SIDE_MARGIN - width).coerceAtLeast(SIDE_MARGIN))
        val y = (anchor.center.y - h / 2f).coerceIn(top, (stageH - bottom - h).coerceAtLeast(top))
        // The caret stays on the straight part of the edge, clear of the rounded corners
        val edge = (CORNER + CARET_WIDTH / 2f).coerceAtMost(h / 2f)
        val caretY = (anchor.center.y - y).coerceIn(edge, h - edge)
        return PickerPlacement(x, y, width, h, list, caretRight = onLeft, caretY = caretY, tipX = if (onLeft) x + width + CARET_HEIGHT else x - CARET_HEIGHT)
    }
}
