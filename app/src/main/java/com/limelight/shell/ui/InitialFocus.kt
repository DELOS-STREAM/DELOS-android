package com.limelight.shell.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.platform.LocalWindowInfo
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first

/**
 * Focuses [requester] silently when a screen or layer appears (like the prototype's showScreen and
 * openLayer). Waits until the window has focus: requests before that are lost.
 */
@Composable
fun InitialFocus(requester: FocusRequester, key: Any? = Unit) {
    val feedback = LocalFeedback.current
    val windowInfo = LocalWindowInfo.current
    LaunchedEffect(key) {
        snapshotFlow { windowInfo.isWindowFocused }.first { it }
        feedback.silenceNextFocus()
        runCatching { requester.requestFocus() }
    }
}

/**
 * Keeps the focus inside a card over the screen (the update and problem offers) while [active]: the D-pad cannot leave it,
 * and when the screen behind takes the focus back (a list restoring its row), [requester] gets it again silently.
 */
@Composable
fun Modifier.holdFocus(requester: FocusRequester, active: Boolean): Modifier {
    val feedback = LocalFeedback.current
    val windowInfo = LocalWindowInfo.current
    var inside by remember { mutableStateOf(true) }
    LaunchedEffect(inside, active) {
        if (!active || inside) return@LaunchedEffect
        snapshotFlow { windowInfo.isWindowFocused }.first { it }
        // One frame later: the screen behind has finished its own focus request
        delay(32)
        feedback.silenceNextFocus()
        runCatching { requester.requestFocus() }
    }
    return onFocusChanged { inside = it.hasFocus }.focusProperties { onExit = { if (active) cancelFocusChange() } }
}
