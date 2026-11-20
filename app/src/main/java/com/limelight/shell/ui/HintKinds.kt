package com.limelight.shell.ui

/**
 * The glyph a hint written for a controller shows for [kind] (`ShellHints.adapt` of DELOS for Apple); null: the kind has no such button
 * and the hint is dropped.
 * - controller: unchanged;
 * - touch: only the primary action (A) stays, with the tap symbol: every other action has a control on the screen;
 * - pointer (mouse, trackpad): the primary action with the click, the right menu button (the context menu) with the right click;
 * - keyboard: every button with its key ([keyCap]: Return, Esc, Tab, O, F, [ and ], the arrows).
 */
fun Glyph.forKind(kind: InputKind): Glyph? = when (kind) {
    InputKind.CONTROLLER -> this
    InputKind.TOUCH -> if (this == Glyph.A) Glyph.TAP else null
    InputKind.POINTER -> when (this) {
        Glyph.A -> Glyph.CLICK
        Glyph.MENU_RIGHT -> Glyph.SECONDARY_CLICK
        else -> null
    }
    InputKind.KEYBOARD -> keyCap()?.let(Glyph::Cap)
}

/** The key of the shell's keyboard map ([com.limelight.shell.GlobalKeys.keyboardButton]) that does what this button does; null: no key. */
fun Glyph.keyCap(): InputCap? = when (this) {
    Glyph.A -> InputCap.RETURN
    Glyph.B -> InputCap.ESCAPE
    Glyph.X, Glyph.MENU_RIGHT -> InputCap.LETTER_O
    Glyph.Y -> InputCap.LETTER_F
    Glyph.MENU_LEFT -> InputCap.TAB
    Glyph.L1 -> InputCap.LEFT_BRACKET
    Glyph.R1 -> InputCap.RIGHT_BRACKET
    Glyph.ADJUST, Glyph.DPAD_HORIZONTAL -> InputCap.ARROWS
    Glyph.DPAD_DOWN -> InputCap.ARROW_DOWN
    is Glyph.Cap -> cap
    else -> null
}

/**
 * The hints (written for a controller) for [kind]: each with its glyph for the kind, the ones it has no button for left out ([forKind]).
 * A [Hint.tappable] hint stays as it is for touch and the pointer: the bar draws it as a pill to tap. The keyboard presses its key
 * instead, so there it is a hint with the key like the others; X and the right menu button are both O, so O shows once.
 */
fun List<Hint>.adaptedTo(kind: InputKind): List<Hint> = when (kind) {
    InputKind.CONTROLLER -> this
    InputKind.KEYBOARD -> {
        val shown = HashSet<InputCap>()
        mapNotNull { hint ->
            hint.glyph.keyCap()?.takeIf(shown::add)?.let { hint.copy(glyph = Glyph.Cap(it), tappable = false, pair = hint.pair?.keyCap()?.let(Glyph::Cap)) }
        }
    }
    else -> mapNotNull { hint -> if (hint.tappable) hint else hint.glyph.forKind(kind)?.let { hint.copy(glyph = it, pair = hint.pair?.forKind(kind)) } }
}
