package com.limelight.shell

import android.view.KeyEvent

/**
 * Gamepad buttons that work everywhere in the shell, independent of the focused element: the two top
 * menu buttons, X and the shoulder buttons. The activity feeds every key event through [dispatch]
 * before the normal (focus) dispatch.
 */
class GlobalKeys {
    /** Handles a key-up of one of [KEYS]. */
    var listener: ((keyCode: Int) -> Unit)? = null

    /** Y runs the action of the visible toast (Undo) first, wherever the shell is; nothing else reacts to Y then. */
    var canUndo: () -> Boolean = { false }
    var onUndo: (() -> Unit)? = null

    /** Y opens settings search only while the settings screen accepts input. */
    var canSearch: () -> Boolean = { false }
    var onSearch: (() -> Unit)? = null

    /** Y toggles the library filters on the home screen (and closes them while they are open). */
    var canFilter: () -> Boolean = { false }
    var onFilter: (() -> Unit)? = null

    /** While set, every key goes here first (recording the stream menu buttons); true consumes it. */
    var capture: ((KeyEvent) -> Boolean)? = null

    /** Returns whether the event was consumed: both edges are, so no fallback key is synthesized. */
    fun dispatch(event: KeyEvent): Boolean {
        capture?.let { if (it(event)) return true }
        if (event.keyCode == KeyEvent.KEYCODE_BUTTON_Y) {
            val undo = onUndo?.takeIf { canUndo() }
            val search = onSearch?.takeIf { undo == null && canSearch() }
            val filter = onFilter?.takeIf { undo == null && search == null && canFilter() }
            val action = undo ?: search ?: filter ?: return false
            if (event.action == KeyEvent.ACTION_UP && event.repeatCount == 0 && !event.isCanceled) action()
            return true
        }
        if (event.keyCode !in KEYS) return false
        val l = listener ?: return false
        if (event.action == KeyEvent.ACTION_UP && event.repeatCount == 0) l(event.keyCode)
        return true
    }

    companion object {
        val KEYS = setOf(
            KeyEvent.KEYCODE_BUTTON_SELECT, KeyEvent.KEYCODE_BUTTON_START, KeyEvent.KEYCODE_BUTTON_X,
            KeyEvent.KEYCODE_BUTTON_L1, KeyEvent.KEYCODE_BUTTON_R1,
        )

        /**
         * The shell's keyboard map (docs/GUI.md, "Keyboard and pointer"): the pad button a hardware keyboard's [keyCode] stands for, or
         * null for a key the shell leaves alone. The arrows are the D-pad already. Return, Enter and Space are A, Esc is B, Tab the left
         * menu button (the side menu), O and the menu key are X (the context menu, a screen's X action), F is Y (filter, search, Undo),
         * [ and ] are L1 and R1 (the tabs). While a text field is [typing] only Esc is mapped (letters, Space and Enter are the
         * field's); a key with Ctrl, Alt or Meta ([modified]) never is.
         */
        fun keyboardButton(keyCode: Int, typing: Boolean, modified: Boolean): Int? {
            if (modified) return null
            if (keyCode == KeyEvent.KEYCODE_ESCAPE) return KeyEvent.KEYCODE_BUTTON_B
            if (typing) return null
            return when (keyCode) {
                KeyEvent.KEYCODE_ENTER, KeyEvent.KEYCODE_NUMPAD_ENTER, KeyEvent.KEYCODE_SPACE -> KeyEvent.KEYCODE_BUTTON_A
                KeyEvent.KEYCODE_TAB -> KeyEvent.KEYCODE_BUTTON_SELECT
                KeyEvent.KEYCODE_O, KeyEvent.KEYCODE_MENU -> KeyEvent.KEYCODE_BUTTON_X
                KeyEvent.KEYCODE_F -> KeyEvent.KEYCODE_BUTTON_Y
                KeyEvent.KEYCODE_LEFT_BRACKET -> KeyEvent.KEYCODE_BUTTON_L1
                KeyEvent.KEYCODE_RIGHT_BRACKET -> KeyEvent.KEYCODE_BUTTON_R1
                else -> null
            }
        }

        /**
         * The shell's TV remote map: maps TV remote navigation and media keys to gamepad buttons.
         * Menu and Info map to X (context menu), Play/Pause to Start, Channel Up/Down and Page Up/Down
         * to shoulder buttons (L1/R1 for tab navigation), and color buttons to Y/X.
         */
        fun remoteButton(keyCode: Int): Int? = when (keyCode) {
            KeyEvent.KEYCODE_MENU, KeyEvent.KEYCODE_INFO -> KeyEvent.KEYCODE_BUTTON_X
            KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE -> KeyEvent.KEYCODE_BUTTON_START
            KeyEvent.KEYCODE_CHANNEL_UP, KeyEvent.KEYCODE_PAGE_UP -> KeyEvent.KEYCODE_BUTTON_R1
            KeyEvent.KEYCODE_CHANNEL_DOWN, KeyEvent.KEYCODE_PAGE_DOWN -> KeyEvent.KEYCODE_BUTTON_L1
            KeyEvent.KEYCODE_PROG_YELLOW -> KeyEvent.KEYCODE_BUTTON_Y
            KeyEvent.KEYCODE_PROG_BLUE -> KeyEvent.KEYCODE_BUTTON_X
            else -> null
        }
    }
}
