package com.limelight.shell.ui

import android.accessibilityservice.AccessibilityServiceInfo
import android.view.View
import android.view.accessibility.AccessibilityManager
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.ui.platform.LocalView
import java.lang.reflect.Field

/**
 * Keeps Compose from building its semantics tree for accessibility services that cannot read it.
 *
 * While any accessibility service is enabled, Compose rebuilds and compares the whole semantics tree several times a
 * second and on every scroll. Handheld game assistants (the AYN Odin's "Game Assistant") enable a service that only
 * listens for window changes and has no right to read a window's content, so that work serves nobody; on the Odin it
 * was the largest cost of the main thread after drawing (#113). While no enabled service can retrieve window content
 * and touch exploration is off, the Compose root's list of enabled services is set to empty, which turns its
 * accessibility work off. A screen reader, or any service that reads content, gets the full behaviour: Compose
 * clears that list itself on every accessibility state change and asks the system again. Window state events, which
 * such assistants listen for, come from the platform window and are not affected.
 *
 * The list is a private field of Compose (`proguard-rules.pro` keeps it); when the field is missing, nothing changes.
 */
@Composable
fun QuietAccessibility() {
    val view = LocalView.current
    DisposableEffect(view) {
        val manager = view.context.getSystemService(AccessibilityManager::class.java)
        val target = manager?.let { composeServiceList(view) }
        if (target == null) return@DisposableEffect onDispose { }
        val (delegate, field) = target
        val quiet = Runnable {
            if (manager.isEnabled && !manager.isTouchExplorationEnabled && !anyServiceReadsContent(manager)) {
                runCatching { field.set(delegate, emptyList<AccessibilityServiceInfo>()) }
            }
        }
        // Compose's own listeners clear the list on these changes; this runs after them
        val onState = AccessibilityManager.AccessibilityStateChangeListener { view.post(quiet) }
        val onTouch = AccessibilityManager.TouchExplorationStateChangeListener { view.post(quiet) }
        manager.addAccessibilityStateChangeListener(onState)
        manager.addTouchExplorationStateChangeListener(onTouch)
        view.post(quiet)
        onDispose {
            view.removeCallbacks(quiet)
            manager.removeAccessibilityStateChangeListener(onState)
            manager.removeTouchExplorationStateChangeListener(onTouch)
        }
    }
}

private fun anyServiceReadsContent(manager: AccessibilityManager): Boolean =
    manager.getEnabledAccessibilityServiceList(AccessibilityServiceInfo.FEEDBACK_ALL_MASK)
        .any { it.capabilities and AccessibilityServiceInfo.CAPABILITY_CAN_RETRIEVE_WINDOW_CONTENT != 0 }

/** The Compose root's accessibility delegate and its cached list of enabled services, or null when Compose changed. */
private fun composeServiceList(view: View): Pair<Any, Field>? = runCatching {
    val delegate = view.javaClass.getDeclaredField("composeAccessibilityDelegate").apply { isAccessible = true }.get(view)!!
    val field = delegate.javaClass.getDeclaredField("_enabledServices").apply { isAccessible = true }
    delegate to field
}.getOrNull()
