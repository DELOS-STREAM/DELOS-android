package com.limelight.shell.screens

import android.content.Context
import android.graphics.PixelFormat
import android.os.Build
import android.util.Log
import android.view.Gravity
import android.view.MotionEvent
import android.view.PointerIcon
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import android.webkit.WebView
import android.widget.FrameLayout
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionContext
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCompositionContext
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.unit.IntRect
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.findViewTreeLifecycleOwner
import androidx.lifecycle.findViewTreeViewModelStoreOwner
import androidx.lifecycle.setViewTreeLifecycleOwner
import androidx.lifecycle.setViewTreeViewModelStoreOwner
import androidx.savedstate.findViewTreeSavedStateRegistryOwner
import androidx.savedstate.setViewTreeSavedStateRegistryOwner
import com.limelight.diag.DiagLog
import com.limelight.shell.ui.ControllerGlyphs
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collectLatest

/** The log tag of the viewer's player (logcat and the diagnostic log). */
private const val TAG = "OverviewPlayer"

/**
 * A YouTube video of the viewer (the fallback for a game without Steam trailers, which play in the viewer's own tree) in a window of
 * its own: an application panel over the shell's window, placed on [bounds] (the viewer, in the
 * shell window's pixels). Its root is the player's web view, hardware accelerated, with no Compose layer, blur, alpha or transform of
 * any ancestor around it: a web view drawn inside the shell's Compose tree played the sound but showed a black picture. [content] (all
 * the viewer draws above the video: the thumbnail until it plays, the DELOS overlay, the hint bar) is composed over it in the same
 * window, as part of the viewer's composition. The window never takes the focus, so keys and Back stay with the shell; a touch or a
 * mouse in it tells [ControllerGlyphs] and [onPointer], as the shell's window does; while [pointerHidden] (the dock hidden) the mouse pointer
 * is hidden over it. It is in place only while the app is resumed:
 * pausing removes it (and pauses the video), resuming puts it back; leaving composition removes it and destroys the web view.
 */
@Composable
internal fun OverviewPlayerWindow(
    player: YouTubeVideoPlayer,
    bounds: IntRect,
    pointerHidden: Boolean,
    onPointer: () -> Unit,
    pictureAlpha: () -> Float = { 1f },
    pictureOffset: () -> Float = { 0f },
    content: @Composable () -> Unit,
) {
    val anchor = LocalView.current
    val parent = rememberCompositionContext()
    val currentContent by rememberUpdatedState(content)
    val currentPointer by rememberUpdatedState(onPointer)
    val currentAlpha by rememberUpdatedState(pictureAlpha)
    val currentOffset by rememberUpdatedState(pictureOffset)
    var window by remember(anchor, player) { mutableStateOf<PlayerWindow?>(null) }
    DisposableEffect(anchor, player) {
        val created = PlayerWindow(anchor, player, parent, { currentPointer() }) { currentContent() }
        window = created
        onDispose {
            created.destroy()
            window = null
        }
    }
    SideEffect {
        window?.place(bounds)
        window?.pointerHidden = pointerHidden
    }
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    DisposableEffect(lifecycle, window) {
        fun resumed(value: Boolean) {
            player.setActive(value)
            if (value) window?.show() else window?.hide()
        }
        resumed(lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED))
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_RESUME -> resumed(true)
                Lifecycle.Event.ON_PAUSE, Lifecycle.Event.ON_STOP, Lifecycle.Event.ON_DESTROY -> resumed(false)
                else -> Unit
            }
        }
        lifecycle.addObserver(observer)
        onDispose {
            lifecycle.removeObserver(observer)
            window?.hide()
        }
    }
    LaunchedEffect(player) {
        delay(LOAD_TIMEOUT_MS)
        if (!player.heard) player.fail(-1)
    }
    LaunchedEffect(window, player.started) { if (player.started) window?.logPlaying() }
    // View properties update RenderNodes, never the panel's layout or Compose content.
    LaunchedEffect(window) {
        snapshotFlow { currentAlpha() to currentOffset() }.collectLatest { (alpha, offset) ->
            window?.picture(alpha, offset)
        }
    }
}

private class PlayerWindow(
    private val anchor: View,
    private val player: YouTubeVideoPlayer,
    parent: CompositionContext,
    onPointer: () -> Unit,
    content: @Composable () -> Unit,
) {
    private val manager = anchor.context.getSystemService(WindowManager::class.java)
    private val web = playerView(anchor.context, player)
    private val above = ComposeView(anchor.context).apply {
        setParentCompositionContext(parent)
        setContent(content)
    }
    private val root = PlayerWindowRoot(anchor.context, onPointer).apply {
        addView(web, FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT))
        addView(above, FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT))
        // The composition above the video needs the shell's owners, as a Compose popup or dialog does
        setViewTreeLifecycleOwner(anchor.findViewTreeLifecycleOwner())
        setViewTreeViewModelStoreOwner(anchor.findViewTreeViewModelStoreOwner())
        setViewTreeSavedStateRegistryOwner(anchor.findViewTreeSavedStateRegistryOwner())
    }
    private val params = WindowManager.LayoutParams().apply {
        type = WindowManager.LayoutParams.TYPE_APPLICATION_PANEL
        flags = WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS or
            WindowManager.LayoutParams.FLAG_HARDWARE_ACCELERATED
        format = PixelFormat.OPAQUE
        gravity = Gravity.TOP or Gravity.START
        windowAnimations = 0
        layoutInDisplayCutoutMode = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_ALWAYS
            else WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) fitInsetsTypes = 0
        title = "Delos overview player" // l10n-ignore
    }

    /** The app is resumed: the window should be there (as soon as it has a place). */
    private var wanted = false
    private var shown = false
    private var destroyed = false

    var pointerHidden: Boolean
        get() = root.pointerHidden
        set(value) { root.pointerHidden = value }

    fun picture(alpha: Float, offset: Float) {
        web.alpha = alpha
        web.translationX = offset
    }
    fun place(bounds: IntRect) {
        if (params.x == bounds.left && params.y == bounds.top && params.width == bounds.width && params.height == bounds.height) return
        params.x = bounds.left
        params.y = bounds.top
        params.width = bounds.width
        params.height = bounds.height
        if (shown) manager.updateViewLayout(root, params) else if (wanted) show()
    }

    fun show() {
        wanted = true
        if (shown || destroyed || params.width <= 0 || params.height <= 0) return
        // A panel belongs to the window of the shell's view; without that window there is nothing to put it over
        params.token = anchor.windowToken ?: return
        manager.addView(root, params)
        shown = true
    }

    fun hide() {
        wanted = false
        if (!shown) return
        shown = false
        manager.removeViewImmediate(root)
    }

    fun destroy() {
        if (destroyed) return
        hide()
        destroyed = true
        above.disposeComposition()
        player.release()
        web.removeJavascriptInterface("DelosPlayer")
        web.stopLoading()
        web.destroy()
    }

    /** What the lead checks on a device (`adb logcat -s OverviewPlayer`): the player draws in a hardware window of its own, not in a layer. */
    fun logPlaying() {
        val window = web.rootView.layoutParams as? WindowManager.LayoutParams
        val message = "Playback started: webView.isHardwareAccelerated=${web.isHardwareAccelerated}, " + // l10n-ignore
            "layerType=${layerName(web.layerType)}, windowType=${window?.type ?: "none"}, " + // l10n-ignore
            "windowHardwareAccelerated=${window != null && window.flags and WindowManager.LayoutParams.FLAG_HARDWARE_ACCELERATED != 0}, " + // l10n-ignore
            "window=${window?.width}x${window?.height}" // l10n-ignore
        Log.d(TAG, message)
        DiagLog.d(TAG, message)
    }
}

private fun layerName(type: Int): String = when (type) {
    View.LAYER_TYPE_NONE -> "NONE"
    View.LAYER_TYPE_SOFTWARE -> "SOFTWARE"
    View.LAYER_TYPE_HARDWARE -> "HARDWARE"
    else -> type.toString()
}

/**
 * The window's root: a touch or a mouse in the player's window counts as input there, as [com.limelight.shell.ShellActivity] counts it in its
 * own. While [pointerHidden] no child (the web view's own pointer included) shows the mouse pointer.
 */
private class PlayerWindowRoot(context: Context, private val onPointer: () -> Unit) : FrameLayout(context) {
    var pointerHidden = false
        set(value) {
            if (field == value) return
            field = value
            // Setting the icon makes a pointer resting over the window take the change without moving
            pointerIcon = if (value) PointerIcon.getSystemIcon(context, PointerIcon.TYPE_NULL) else null
        }

    override fun onResolvePointerIcon(event: MotionEvent, pointerIndex: Int): PointerIcon? =
        if (pointerHidden) PointerIcon.getSystemIcon(context, PointerIcon.TYPE_NULL) else super.onResolvePointerIcon(event, pointerIndex)

    override fun dispatchTouchEvent(event: MotionEvent): Boolean {
        ControllerGlyphs.touch(event)
        onPointer()
        return super.dispatchTouchEvent(event)
    }

    override fun dispatchGenericMotionEvent(event: MotionEvent): Boolean {
        ControllerGlyphs.motion(event)
        onPointer()
        return super.dispatchGenericMotionEvent(event)
    }
}
