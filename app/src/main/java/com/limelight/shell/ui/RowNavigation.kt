package com.limelight.shell.ui

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusDirection
import androidx.compose.ui.focus.FocusEventModifierNode
import androidx.compose.ui.focus.FocusManager
import androidx.compose.ui.focus.FocusProperties
import androidx.compose.ui.focus.FocusPropertiesModifierNode
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.FocusState
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.findRootCoordinates
import androidx.compose.ui.node.CompositionLocalConsumerModifierNode
import androidx.compose.ui.node.ModifierNodeElement
import androidx.compose.ui.node.TraversableNode
import androidx.compose.ui.node.currentValueOf
import androidx.compose.ui.node.requireLayoutCoordinates
import androidx.compose.ui.node.traverseAncestors
import androidx.compose.ui.platform.InspectorInfo
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalView

/**
 * The row-first rule ([RowFirst]) for Compose's focus: every [focusEffects] element (and a [rowNavItem]) is registered here while it is
 * attached. An Up or Down that no element handled itself reaches [rowFirstNavigation] at the shell's root, which picks the target among
 * the focused element's own layer ([rowNavScope]: a dialog, a menu, the side menu never let it out) and hands it to Compose as the
 * focused element's `up` / `down` for that one move. A caller's own `focusProperties { up / down }` on the element still wins (it is
 * applied after this one), focus traps and `canFocus = false` still refuse; a refused target is skipped and, at last, Compose's own
 * search moves as before. Main thread only; nothing runs per frame (the rectangles are read on the key press).
 */
object RowNav {
    private val items = ArrayList<RowNavItemNode>()
    private val candidates = RowCandidates()
    private val nodes = ArrayList<RowNavItemNode>()

    /** The registered element with the focus, if the focus is on one. */
    private var focused: RowNavItemNode? = null

    /** The element the last vertical move left, and whether going back to it is Down (true) or Up. */
    private var remembered: RowNavItemNode? = null
    private var rememberedDown = false

    /** During [moveFocus]: the source element and the target it hands to Compose. */
    private var redirectFrom: RowNavItemNode? = null
    private var redirect: FocusRequester? = null
    private var redirectDown = false

    private const val TRIES = 3

    /**
     * Moves the focus one row [down] or up with [focusManager] by the row-first rule; returns whether the focus moved. With the focus
     * on an element that is not registered it is Compose's move as before.
     */
    fun moveFocus(focusManager: FocusManager, down: Boolean): Boolean {
        val direction = if (down) FocusDirection.Down else FocusDirection.Up
        val source = focused?.takeIf { it.isAttached } ?: return focusManager.moveFocus(direction)
        var moved = false
        var tries = 0
        while (!moved && tries < TRIES) {
            val target = pick(source, down) ?: break
            redirectFrom = source
            redirect = target.requester
            redirectDown = down
            try {
                moved = focusManager.moveFocus(direction)
            } finally {
                redirectFrom = null
                redirect = null
            }
            // Refused (a trap, canFocus = false): the next one, then Compose's own search
            if (!moved) target.skip = true
            tries++
        }
        for (i in 0 until items.size) items[i].skip = false
        if (!moved) moved = focusManager.moveFocus(direction)
        if (moved) {
            remembered = source
            rememberedDown = !down
        }
        return moved
    }

    private fun pick(source: RowNavItemNode, down: Boolean): RowNavItemNode? {
        val from = source.coordinates() ?: return null
        val root = from.findRootCoordinates()
        val a = root.localBoundingBoxOf(from, clipBounds = false)
        candidates.clear()
        nodes.clear()
        var memo = -1
        for (i in 0 until items.size) {
            val item = items[i]
            if (item === source || item.skip || item.scope !== source.scope) continue
            val c = item.coordinates() ?: continue
            if (c.findRootCoordinates() !== root) continue
            val r = root.localBoundingBoxOf(c, clipBounds = false)
            if (item === remembered && rememberedDown == down) memo = candidates.size
            candidates.add(r.left, r.top, r.right, r.bottom, onScreen = leftEdgeShown(c, r.left, root), grid = item.grid)
            nodes.add(item)
        }
        val i = RowFirst.target(candidates, a.left, a.top, a.right, a.bottom, down, memo)
        val target = if (i >= 0) nodes[i] else null
        nodes.clear()
        return target
    }

    /** Whether the element's left edge is on screen: not cut off by a row it scrolls in, nor left of the window. */
    private fun leftEdgeShown(c: LayoutCoordinates, left: Float, root: LayoutCoordinates): Boolean {
        val shown = c.boundsInRoot()
        return if (shown.width > 0f && shown.height > 0f) shown.left <= left + 1f
        else left >= 0f && left < root.size.width
    }

    /** The element's layout coordinates, or null while it is not laid out. */
    private fun RowNavItemNode.coordinates(): LayoutCoordinates? {
        if (!isAttached) return null
        return try {
            requireLayoutCoordinates().takeIf { it.isAttached }
        } catch (_: IllegalStateException) {
            null
        }
    }

    internal fun attach(item: RowNavItemNode) {
        items.add(item)
    }

    internal fun detach(item: RowNavItemNode) {
        items.remove(item)
        if (focused === item) focused = null
        if (remembered === item) remembered = null
    }

    internal fun focusChanged(item: RowNavItemNode, isFocused: Boolean) {
        if (isFocused) focused = item else if (focused === item) focused = null
    }

    internal fun applyTo(item: RowNavItemNode, properties: FocusProperties) {
        if (redirectFrom !== item) return
        val target = redirect ?: return
        if (redirectDown) properties.down = target else properties.up = target
    }
}

/**
 * Registers the element with [RowNav] (it is a source and a target of row-first moves). Put it before the element's focus target,
 * after any `focusProperties` of the caller's that must win. [focusEffects] does it; [requester] must be attached to the element.
 */
fun Modifier.rowNavItem(requester: FocusRequester): Modifier = this then RowNavItemElement(requester)

/** The root of a layer whose focus is trapped (a dialog, a menu, the side menu, a sheet): row-first moves never leave it. */
fun Modifier.rowNavScope(): Modifier = this then RowNavScopeElement(grid = false)

/** A 2-D grid (artwork picker, avatars, letter picker): Up and Down keep the column inside it ([RowFirst]). */
fun Modifier.rowNavGrid(): Modifier = this then RowNavScopeElement(grid = true)

/** Up and Down that no element handled move by the row-first rule ([RowNav]); put it on the root of a window's content. */
@Composable
fun Modifier.rowFirstNavigation(): Modifier {
    val focusManager = LocalFocusManager.current
    return onKeyEvent {
        if (it.type != KeyEventType.KeyDown) return@onKeyEvent false
        when (it.key) {
            Key.DirectionDown -> RowNav.moveFocus(focusManager, down = true)
            Key.DirectionUp -> RowNav.moveFocus(focusManager, down = false)
            else -> false
        }
    }
}

private object RowNavKey

private class RowNavScopeNode(var grid: Boolean) : Modifier.Node(), TraversableNode {
    override val traverseKey: Any get() = RowNavKey
}

private data class RowNavScopeElement(val grid: Boolean) : ModifierNodeElement<RowNavScopeNode>() {
    override fun create() = RowNavScopeNode(grid)
    override fun update(node: RowNavScopeNode) {
        node.grid = grid
    }
    override fun InspectorInfo.inspectableProperties() {
        name = if (grid) "rowNavGrid" else "rowNavScope"
    }
}

internal class RowNavItemNode(var requester: FocusRequester) :
    Modifier.Node(), FocusPropertiesModifierNode, FocusEventModifierNode, CompositionLocalConsumerModifierNode {
    /** The layer the element belongs to (its nearest [rowNavScope], else its window) and whether it is in a [rowNavGrid]. */
    var scope: Any? = null
    var grid = false
    var skip = false
    private var isFocused = false

    // The window's view stays the same while the node is attached: reading it once here is the intent
    @android.annotation.SuppressLint("SuspiciousCompositionLocalModifierRead")
    override fun onAttach() {
        var inGrid = false
        var layer: Any? = null
        traverseAncestors(RowNavKey) { node ->
            if ((node as RowNavScopeNode).grid) {
                inGrid = true
                true
            } else {
                layer = node
                false
            }
        }
        grid = inGrid
        scope = layer ?: currentValueOf(LocalView)
        RowNav.attach(this)
    }

    override fun onDetach() {
        isFocused = false
        RowNav.detach(this)
    }

    override fun onFocusEvent(focusState: FocusState) {
        if (focusState.isFocused == isFocused) return
        isFocused = focusState.isFocused
        RowNav.focusChanged(this, isFocused)
    }

    override fun applyFocusProperties(focusProperties: FocusProperties) = RowNav.applyTo(this, focusProperties)
}

private data class RowNavItemElement(val requester: FocusRequester) : ModifierNodeElement<RowNavItemNode>() {
    override fun create() = RowNavItemNode(requester)
    override fun update(node: RowNavItemNode) {
        node.requester = requester
    }
    override fun InspectorInfo.inspectableProperties() {
        name = "rowNavItem"
    }
}
