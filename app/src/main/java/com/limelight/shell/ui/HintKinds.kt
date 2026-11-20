package com.limelight.shell.ui

/**
 * The glyph a hint written for a controller shows for [kind] (`ShellHints.adapt` of DELOS for Apple); null: the kind has no such button
 * and the hint is dropped.
 * - controller: unchanged;
 * - touch: only the primary action (A) stays, with the tap symbol: every other action has a control on the screen;
 * - pointer (mouse, trackpad): the same, with the mouse symbol;
 * - keyboard: Enter for the primary action, Esc for back, the arrows for the adjust cue; the buttons with no key (tabs, menu buttons, X,
 *   Y) are dropped. A hint that already names a key ([Glyph.Cap], written for the keyboard) stays.
 */
fun Glyph.forKind(kind: InputKind): Glyph? = when (kind) {
    InputKind.CONTROLLER -> this
    InputKind.TOUCH -> if (this == Glyph.A) Glyph.TAP else null
    InputKind.POINTER -> if (this == Glyph.A) Glyph.CLICK else null
    InputKind.KEYBOARD -> when (this) {
        Glyph.A -> Glyph.Cap(InputCap.RETURN)
        Glyph.B -> Glyph.Cap(InputCap.ESCAPE)
        Glyph.ADJUST, Glyph.DPAD_HORIZONTAL -> Glyph.Cap(InputCap.ARROWS)
        Glyph.DPAD_DOWN -> Glyph.Cap(InputCap.ARROW_DOWN)
        is Glyph.Cap -> this
        else -> null
    }
}

/**
 * The hints (written for a controller) for [kind]: each with its glyph for the kind, the ones it has no button for left out ([forKind]).
 * A [Hint.tappable] hint stays as it is for every kind: the bar draws it as a pill to tap while no controller is the input.
 */
fun List<Hint>.adaptedTo(kind: InputKind): List<Hint> =
    if (kind == InputKind.CONTROLLER) this else mapNotNull { hint -> if (hint.tappable) hint else hint.glyph.forKind(kind)?.let { hint.copy(glyph = it) } }
