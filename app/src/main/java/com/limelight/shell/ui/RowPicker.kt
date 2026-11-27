package com.limelight.shell.ui

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import com.limelight.shell.paintPopoverGlass
import androidx.compose.ui.graphics.Outline
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection
import dev.chrisbanes.haze.HazeState
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.drag
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.focusable
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.input.InputTransformation
import androidx.compose.foundation.text.input.TextFieldLineLimits
import androidx.compose.foundation.text.input.rememberTextFieldState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.foundation.text.input.setTextAndSelectAll
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.IntOffset
import com.limelight.R
import com.limelight.shell.drawBoxShadow
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.roundToInt
import com.limelight.shell.ui.PickerLayout as P

/**
 * What a settings row opens on a tap (touch, issue #92): the list of a choice's options ([Options]) or a slider's popup ([Value]). Both are drawn by
 * [RowPickerLayer] at the row, in the look of the iPad's `ChoicePicker`.
 */
@Immutable
sealed interface RowPopup {
    val title: String

    /**
     * [options] (their labels) with the current one [selected] (-1: none); a pick calls [onPick] with its index. [onReset]: "Reset to profile value"
     * as the last item (a custom game setting).
     */
    data class Options(
        override val title: String, val options: List<String>, val selected: Int, val onPick: (Int) -> Unit, val onReset: (() -> Unit)? = null,
    ) : RowPopup {
        /** The list's items: the options and the reset. */
        val count get() = options.size + if (onReset != null) 1 else 0
    }

    /** The popup of [slider]: its value is changed through [RowValue.Slider.onChange]. */
    data class Value(override val title: String, val slider: RowValue.Slider) : RowPopup
}

/** A [popup] and the bounds of the row it belongs to (root pixels; null: no row to point at). */
@Immutable
data class RowPopupRequest(val popup: RowPopup, val anchor: Rect?)

/** Opens a row's popup at the focused row; null where no shell can show one (the row then steps on a tap, as A does). */
val LocalRowPicker = staticCompositionLocalOf<((RowPopup) -> Unit)?> { null }

/** The popup [value] opens on a touch tap or Options, or null for a row that has none (a toggle, an action, a choice without its options). [onReset]: a choice's reset item. */
internal fun rowPopupFor(title: String, value: RowValue, onReset: (() -> Unit)? = null): RowPopup? = when {
    value is RowValue.Choice && value.onPick != null && !value.options.isNullOrEmpty() -> RowPopup.Options(title, value.options, value.selected, value.onPick, onReset)
    value is RowValue.Slider -> RowPopup.Value(title, value)
    else -> null
}

/** The popup is closed by a tap beside the card; the slider popup commits a typed number first. */
private class CloseHook {
    var before: () -> Unit = {}
}

/**
 * The popup of a settings row over the screen: the stream menu's popover card, grown out of the tapped row ([PickerLayout]: vertically centred on the
 * row, its caret pointing into the row's value). Like the stream menu there is no dimming; a tap beside the card closes it.
 * [request] null: it shrinks back into the row. With a controller: up and down, A picks, B closes (the focus is trapped inside); the slider popup takes left and right.
 */
@Composable
fun RowPickerLayer(request: RowPopupRequest?, haze: HazeState?, onClose: () -> Unit) {
    // The last request stays while the card shrinks back
    var shown by remember { mutableStateOf(request) }
    if (request != null) shown = request
    // The anchor is the row's bounds in the window's root; the layer may start elsewhere (the shape simulator's box): relative to the layer
    var layerAt by remember { mutableStateOf(Offset.Zero) }
    Box(Modifier.fillMaxSize().onGloballyPositioned { layerAt = it.positionInRoot() }) {
        val r = shown ?: return@Box
        key(r) { PickerSheet(r, layerAt, haze, active = request != null, onClose) }
    }
}

@Composable
private fun PickerSheet(request: RowPopupRequest, layerAt: Offset, haze: HazeState?, active: Boolean, onClose: () -> Unit) {
    val k = stageUnit()
    val stage = LocalStage.current
    val colors = LocalDelosColors.current
    val reduced = LocalReducedMotion.current
    val popup = request.popup
    val anchor = request.anchor?.let { Rect((it.left - layerAt.x) / k, (it.top - layerAt.y) / k, (it.right - layerAt.x) / k, (it.bottom - layerAt.y) / k) }
    // Below the status bar and above the bottom edge; a compact stage's card ends above its hint bar
    val top = if (stage.compact) ContextMenuLayout.compactTopMargin(stage.textScale, stage.safe.top) else P.TOP_MARGIN + stage.safe.top
    val bottom = if (stage.compact) ContextMenuLayout.compactBottomMargin(stage.textScale, stage.safe.bottom) else P.BOTTOM_MARGIN + stage.safe.bottom
    val place = when (popup) {
        is RowPopup.Options -> P.options(anchor, popup.count, popup.selected, stage.stageWidth, stage.stageHeight, top, bottom)
        is RowPopup.Value -> P.value(anchor, stage.stageWidth, stage.stageHeight, top, bottom)
    }
    val hook = remember { CloseHook() }
    val feedback = LocalFeedback.current
    val sheen = colors.accent2
    // The card box holds the body and, on one side, the caret
    val caretLeft = place.hasCaret && !place.caretRight
    val caretRight = place.hasCaret && place.caretRight
    val d = if (place.hasCaret) P.CARET_HEIGHT else 0f
    val boxW = place.width + d
    // It grows from the caret's tip
    val origin = TransformOrigin(if (caretLeft) 0f else if (caretRight) 1f else .5f, if (place.hasCaret) place.caretY / place.height else .5f)
    Box(Modifier.fillMaxSize()) {
        // A tap beside the card closes it (nothing is dimmed: the stream menu's popover has no scrim either)
        if (active) Box(Modifier.fillMaxSize().pointerInput(Unit) { detectTapGestures { hook.before(); feedback.play(Cue.BACK); onClose() } })
        AnimatedVisibility(
            active,
            Modifier
                .offset { IntOffset(((place.x - if (caretLeft) d else 0f) * k).roundToInt(), (place.y * k).roundToInt()) }
                // The keyboard of the number field lifts the whole card (nothing else of the screen moves)
                .moveAboveIme(),
            enter = popEnter(origin, reduced), exit = popExit(origin, reduced),
        ) {
            Box(
                Modifier
                    .size(boxW.px, place.height.px)
                    // A tap on the card itself must not reach the layer behind
                    .pointerInput(Unit) { detectTapGestures { } },
            ) {
                PickerGlass(place, d, haze, sheen)
                Box(Modifier.padding(start = (if (caretLeft) d else 0f).px, end = (if (caretRight) d else 0f).px).fillMaxSize()) {
                    when (popup) {
                        is RowPopup.Options -> OptionsCard(popup, place.list, active, onClose)
                        is RowPopup.Value -> ValueCard(popup, active, hook, onClose)
                    }
                }
            }
        }
    }
}

/**
 * The popover's card behind the content, like the stream menu's popover (glass, outline and caret as one path, [cardCaretPath]) and its shadow. With a
 * [haze] (the shell: the screen behind is its source) the glass is the context menu's: the screen under it blurred under the card's navy tint, plus the
 * sheen; without one (the stream, which has no source) it is the stream popover's translucent glass.
 */
@Composable
private fun BoxScope.PickerGlass(place: PickerPlacement, caret: Float, haze: HazeState?, sheen: Color) {
    val k = stageUnit()
    val left = if (place.hasCaret && !place.caretRight) caret else 0f
    val shape = remember(place, caret, k) {
        object : Shape {
            override fun createOutline(size: Size, layoutDirection: LayoutDirection, density: Density): Outline =
                Outline.Generic(
                    cardCaretPath(
                        if (!place.hasCaret) CaretSide.None else if (place.caretRight) CaretSide.Right else CaretSide.Left,
                        left * k, 0f, size.width - caret * k, size.height, P.CORNER * k, if (place.hasCaret) place.caretY * k else 0f, k,
                    ),
                )
        }
    }
    Box(
        Modifier.matchParentSize().drawBehind {
            drawBoxShadow(Rect(left * k, 0f, size.width - (if (place.hasCaret && place.caretRight) caret * k else 0f), size.height), P.CORNER * k, 40f * k, 90f * k, -20f * k, Color.Black.copy(alpha = .62f))
        },
    )
    Box(
        Modifier
            .matchParentSize()
            .graphicsLayer { this.shape = shape; clip = true }
            .then(if (haze != null) Modifier.menuGlass(haze) else Modifier)
            .drawBehind {
                if (haze == null) paintPopoverGlass(1f, sheen, k)
                else drawRect(Brush.verticalGradient(0f to Color.White.copy(alpha = .13f), .52f to Color.Transparent, 1f to Color.Transparent))
            },
    )
}

@Composable
private fun CardTitle(text: String) {
    val colors = LocalDelosColors.current
    Box(Modifier.fillMaxWidth().height(P.TITLE.px).padding(horizontal = 36.px), contentAlignment = Alignment.TopStart) {
        BasicText(text, maxLines = 1, style = TextStyle(color = colors.text3, fontFamily = Figtree, fontWeight = FontWeight.SemiBold, fontSize = 20.tpx))
    }
}

/** The options of a choice, the current one checked and focused; a long list scrolls. A tap picks, which also closes the card. */
@Composable
private fun OptionsCard(popup: RowPopup.Options, list: Float, active: Boolean, onClose: () -> Unit) {
    val k = stageUnit()
    val scroll = rememberScrollState()
    val first = remember { FocusRequester() }
    InitialFocus(first)
    // The current option is in view from the first frame (a long list is scrolled to it)
    LaunchedEffect(Unit) {
        withFrameNanos { }
        scroll.scrollTo((popup.selected.coerceAtLeast(0) * (P.OPTION + P.OPTIONS.gap) * k).roundToInt())
    }
    Column(Modifier.fillMaxSize().focusTrap(active).padding(vertical = P.PAD.px), verticalArrangement = Arrangement.spacedBy(P.GAP.px)) {
        CardTitle(popup.title)
        Column(Modifier.fillMaxWidth().height(list.px).verticalScroll(scroll).padding(horizontal = 12.px, vertical = P.LIST_ROOM.px), verticalArrangement = Arrangement.spacedBy(P.OPTIONS.gap.px)) {
            popup.options.forEachIndexed { i, label ->
                OptionRow(label, current = i == popup.selected, requester = if (i == popup.selected.coerceAtLeast(0)) first else null) {
                    popup.onPick(i)
                    onClose()
                }
            }
            popup.onReset?.let { reset ->
                OptionRow(stringResource(R.string.settings_game_reset_menu), current = false, requester = null, icon = Symbols.RESET) {
                    onClose()
                    reset()
                }
            }
        }
    }
}

/** An option (`.menu-item` of the prototype): the focused one on a tint of the main color with a 3 px ring, the current one with a check. */
@Composable
private fun OptionRow(label: String, current: Boolean, requester: FocusRequester?, icon: String? = null, onClick: () -> Unit) {
    val colors = LocalDelosColors.current
    var focused by remember { mutableStateOf(false) }
    val fill by animateColorAsState(if (focused) colors.accent.copy(alpha = .17f) else Color.Transparent, tween(Motion.ROW_FOCUS, easing = Motion.Ease), label = "optionFill")
    Row(
        Modifier
            .fillMaxWidth()
            .height(P.OPTION.px)
            .focusEffects(
                onClick = onClick, corner = 28f, focusScale = P.OPTION_LOOK.scale, ring = P.OPTION_RING, glow = null, clickCue = Cue.SELECT,
                onFocusChange = { focused = it }, focusRequester = requester,
            )
            .clip(RoundedCornerShape(28.px))
            .background(fill)
            .padding(start = 24.px, end = 22.px),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(18.px),
    ) {
        if (icon != null) Symbol(icon, 30.tpx, if (focused) colors.accent else colors.text2)
        MarqueeText(
            label, active = focused, modifier = Modifier.weight(1f),
            style = TextStyle(color = colors.text, fontFamily = Figtree, fontWeight = FontWeight.SemiBold, fontSize = 23.tpx),
        )
        if (current) Symbol(Symbols.CHECK, 30.tpx, if (focused) colors.accent else colors.text2)
    }
}

/** Only digits and one decimal separator may be typed or pasted into the number field. */
private val NumberInput = InputTransformation {
    if (!SliderEdit.typable(asCharSequence())) revertAllChanges()
}

/**
 * The popup of a slider: its name, the value as a number to tap and type (the on-screen keyboard; clamped to the slider's range and validated), and a
 * row with a ◀ and a ▶ button (a hold repeats, faster the longer it is held) on each side of the slider itself. Every change applies at once, a drag
 * when the finger lifts. With a controller: left and right step it, A closes it.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun ValueCard(popup: RowPopup.Value, active: Boolean, hook: CloseHook, onClose: () -> Unit) {
    val colors = LocalDelosColors.current
    val feedback = LocalFeedback.current
    val keyboard = LocalSoftwareKeyboardController.current
    val locale = LocalConfiguration.current.locales[0]
    val slider = popup.slider
    var value by remember { mutableIntStateOf(slider.value) }
    var dragging by remember { mutableStateOf<Int?>(null) }
    var editing by remember { mutableStateOf(false) }
    var invalid by remember { mutableStateOf(false) }
    val text = rememberTextFieldState()
    val field = remember { FocusRequester() }
    val card = remember { FocusRequester() }
    InitialFocus(card)

    fun change(next: Int) {
        if (next == value) return
        value = next
        slider.onChange(next)
    }
    fun leaveEditing() {
        editing = false
        invalid = false
        keyboard?.hide()
        // The field had the focus: the controller goes on from the card
        runCatching { card.requestFocus() }
    }
    // The buttons, the track and the typed number all end the typing and set the value
    fun fromControls(next: Int) {
        if (editing) leaveEditing()
        change(next)
    }
    fun commit(): Boolean {
        val parsed = SliderEdit.parse(slider, text.text.toString())
        if (parsed == null) {
            invalid = true
            feedback.play(Cue.ERROR)
            return false
        }
        feedback.play(Cue.SELECT)
        change(parsed)
        leaveEditing()
        return true
    }
    // A tap beside the card applies a number that is typed and valid, and drops one that is not
    SideEffect { hook.before = { if (editing) SliderEdit.parse(slider, text.text.toString())?.let(::change) } }
    // B leaves the number field first, and closes the popup the second time
    BackHandler(enabled = editing && active) {
        feedback.play(Cue.BACK)
        leaveEditing()
    }
    LaunchedEffect(editing) {
        if (!editing) return@LaunchedEffect
        text.setTextAndSelectAll(SliderEdit.numberText(slider, value, locale))
        withFrameNanos { }
        runCatching { field.requestFocus() }
        keyboard?.show()
    }

    Column(
        Modifier
            .fillMaxSize()
            .focusTrap(active)
            .focusRequester(card)
            .focusable()
            .onPreviewKeyEvent { event ->
                val dir = when (event.key) {
                    Key.DirectionLeft -> -1
                    Key.DirectionRight -> 1
                    else -> 0
                }
                if (editing) return@onPreviewKeyEvent false
                when {
                    dir != 0 -> {
                        if (event.type == KeyEventType.KeyDown) {
                            val next = SliderEdit.step(slider, value, dir * sliderAcceleration(slider, event.nativeKeyEvent.repeatCount))
                            if (next != value) {
                                change(next)
                                feedback.play(Cue.STEP)
                            }
                        }
                        true
                    }
                    event.key == Key.ButtonA || event.key == Key.Enter || event.key == Key.NumPadEnter || event.key == Key.DirectionCenter -> {
                        if (event.type == KeyEventType.KeyUp) onClose()
                        true
                    }
                    else -> false
                }
            }
            .padding(vertical = P.PAD.px),
        verticalArrangement = Arrangement.spacedBy(P.VALUE_GAP.px),
    ) {
        CardTitle(popup.title)
        // The number: a tap turns it into a field
        val shape = CircleShape
        val line by animateColorAsState(if (invalid) MenuDanger else if (editing) colors.accent else colors.line, tween(Motion.FADE, easing = Motion.Ease), label = "valueLine")
        val numberStyle = TextStyle(color = if (invalid) MenuDanger else colors.text, fontFamily = Figtree, fontWeight = FontWeight.SemiBold, fontSize = 52.tpx, fontFeatureSettings = "tnum")
        val enterValue = stringResource(R.string.row_slider_enter_value)
        Box(
            Modifier
                .padding(horizontal = P.VALUE_SIDE.px)
                .fillMaxWidth()
                .height(P.VALUE_FIELD.px)
                .then(if (!editing) Modifier.pointerTap(999f, cue = Cue.OPEN) { editing = true } else Modifier)
                .clip(shape)
                .background(colors.surface2)
                .border((if (editing) 2 else 1).px, line, shape)
                .semantics { role = Role.Button; contentDescription = enterValue; onClick { editing = true; true } },
            contentAlignment = Alignment.Center,
        ) {
            if (editing) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.px)) {
                    BasicTextField(
                        state = text,
                        modifier = Modifier.width(320.px).focusRequester(field).keepFieldAboveIme(true),
                        inputTransformation = NumberInput,
                        textStyle = numberStyle.copy(textAlign = TextAlign.End),
                        keyboardOptions = KeyboardOptions(
                            keyboardType = if (slider.divisor > 1) KeyboardType.Decimal else KeyboardType.Number,
                            imeAction = ImeAction.Done, autoCorrectEnabled = false, showKeyboardOnFocus = true,
                        ),
                        onKeyboardAction = { commit() },
                        lineLimits = TextFieldLineLimits.SingleLine,
                        cursorBrush = SolidColor(colors.accent),
                    )
                    BasicText(slider.unit.trim(), maxLines = 1, style = TextStyle(color = colors.text2, fontFamily = Figtree, fontWeight = FontWeight.Medium, fontSize = 32.tpx))
                }
            } else {
                val shown = dragging ?: value
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.px)) {
                    if (shown == slider.min && slider.minText != null) {
                        BasicText(slider.minText, maxLines = 1, style = numberStyle)
                    } else {
                        BasicText(SliderEdit.numberText(slider, shown, locale), maxLines = 1, style = numberStyle)
                        BasicText(slider.unit.trim(), maxLines = 1, style = TextStyle(color = colors.text2, fontFamily = Figtree, fontWeight = FontWeight.Medium, fontSize = 32.tpx))
                    }
                }
                Symbol(Symbols.EDIT, 36.tpx, colors.text3, Modifier.align(Alignment.CenterEnd).padding(end = 32.px))
            }
        }
        Row(Modifier.padding(horizontal = P.VALUE_SIDE.px).fillMaxWidth().height(P.VALUE_CONTROLS.px), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(24.px)) {
            StepButton(-1, Symbols.CHEVRON_LEFT, stringResource(R.string.row_slider_decrease), enabled = value > slider.min, slider, { value }, feedback, ::fromControls)
            PopupTrack(slider, dragging ?: value, Modifier.weight(1f), onDrag = { dragging = it }, onCommit = ::fromControls)
            StepButton(1, Symbols.CHEVRON_RIGHT, stringResource(R.string.row_slider_increase), enabled = value < slider.max, slider, { value }, feedback, ::fromControls)
        }
    }
}

/** A round ◀ or ▶ button: the press steps once at once, a hold repeats after [HoldRepeat.FIRST_DELAY_MS] every [HoldRepeat.INTERVAL_MS] with growing steps. */
@Composable
private fun StepButton(
    direction: Int, icon: String, description: String, enabled: Boolean, slider: RowValue.Slider, current: () -> Int, feedback: Feedback, onValue: (Int) -> Unit,
) {
    val colors = LocalDelosColors.current
    var pressed by remember { mutableStateOf(false) }
    val fill by animateColorAsState(Color.White.copy(alpha = if (pressed) .22f else .10f), tween(Motion.PRESS, easing = Motion.Ease), label = "stepFill")
    // The shared hover and press looks; the step itself runs at the press (a hold repeats), so it is not deferred
    val pulse = rememberPressPulse()
    fun step(steps: Int) {
        if (!enabled) return
        val before = current()
        val next = SliderEdit.step(slider, before, steps)
        if (next != before) {
            onValue(next)
            feedback.play(Cue.STEP)
        }
    }
    Box(
        Modifier
            .size(P.VALUE_BUTTON.px)
            .hoverLook(999f, pulse)
            .pressLook(pulse)
            .alpha(if (enabled) 1f else .35f)
            .clip(CircleShape)
            .background(fill)
            .semantics { role = Role.Button; contentDescription = description; onClick { if (!enabled) false else { step(direction); true } } }
            .pointerInput(slider, direction) {
                detectTapGestures(onPress = {
                    pressed = true
                    coroutineScope {
                        val repeater = launch {
                            step(direction)
                            delay(HoldRepeat.FIRST_DELAY_MS)
                            var repeat = 1
                            while (true) {
                                step(direction * HoldRepeat.steps(slider, repeat))
                                repeat++
                                delay(HoldRepeat.INTERVAL_MS)
                            }
                        }
                        tryAwaitRelease()
                        repeater.cancel()
                    }
                    pressed = false
                })
            },
        contentAlignment = Alignment.Center,
    ) { Symbol(icon, 56.tpx, colors.text) }
}

/** The slider itself, [P.VALUE_CONTROLS] (96) px tall to be hit with a finger: a tap or a drag sets the value on the slider's grid when the finger lifts. */
@Composable
private fun PopupTrack(slider: RowValue.Slider, shown: Int, modifier: Modifier, onDrag: (Int?) -> Unit, onCommit: (Int) -> Unit) {
    val colors = LocalDelosColors.current
    val reduced = LocalReducedMotion.current
    val k = stageUnit()
    val fraction by animateFloatAsState(
        ((shown - slider.min) / (slider.max - slider.min).coerceAtLeast(1).toFloat()).coerceIn(0f, 1f),
        tween(if (reduced) 0 else Motion.SLIDER_FILL, easing = EaseOut), label = "popupSlider",
    )
    val reportDrag by androidx.compose.runtime.rememberUpdatedState(onDrag)
    val commitValue by androidx.compose.runtime.rememberUpdatedState(onCommit)
    Box(
        modifier
            .height(P.VALUE_CONTROLS.px)
            .pointerInput(slider) {
                awaitEachGesture {
                    // The knob's radius keeps the ends of the track reachable: the value maps to the track between the knob's extreme positions
                    fun at(x: Float): Int {
                        val inset = KNOB * k
                        val span = (size.width - 2 * inset).coerceAtLeast(1f)
                        val raw = slider.min + (slider.max - slider.min) * ((x - inset) / span).coerceIn(0f, 1f)
                        return SliderEdit.snap(slider, raw.roundToInt())
                    }
                    val down = awaitFirstDown()
                    down.consume()
                    var last = at(down.position.x)
                    reportDrag(last)
                    drag(down.id) { change -> change.consume(); last = at(change.position.x); reportDrag(last) }
                    reportDrag(null)
                    commitValue(last)
                }
            }
            .drawBehind {
                val inset = KNOB * k
                val span = size.width - 2 * inset
                val trackHeight = 14f * k
                val top = (size.height - trackHeight) / 2f
                drawRoundRect(colors.surface3, Offset(inset, top), Size(span, trackHeight), androidx.compose.ui.geometry.CornerRadius(trackHeight / 2f))
                if (fraction > 0f) drawRoundRect(
                    Brush.horizontalGradient(listOf(colors.accent, colors.accent2), startX = inset, endX = inset + span),
                    Offset(inset, top), Size(span * fraction, trackHeight), androidx.compose.ui.geometry.CornerRadius(trackHeight / 2f),
                )
                val center = Offset(inset + span * fraction, size.height / 2f)
                drawCircle(colors.accent3.copy(alpha = .40f), radius = (KNOB + 6f) * k, center = center)
                drawCircle(Color.Black.copy(alpha = .30f), radius = KNOB * k, center = center + Offset(0f, 3f * k))
                drawCircle(colors.text, radius = KNOB * k, center = center)
            },
    )
}

/** The slider popup's knob radius, stage pixels (a finger needs more than the row's 14). */
private const val KNOB = 30f
