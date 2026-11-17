package com.limelight.shell

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** Touch: when a long press may open an element's context menu (the cover on the library, issue #92). */
class LongPressContextTest {
    private val menu = ContextMenu("Menu", emptyList())

    @Test fun aLongPressOpensTheMenuOfAnElementThatHasOne() {
        assertTrue(longPressOpensContext(menu, shellTakesIt = true, layerOpen = false, busy = false))
    }

    @Test fun anElementWithoutAMenuOrWithoutAShellDoesNothing() {
        assertFalse(longPressOpensContext(null, shellTakesIt = true, layerOpen = false, busy = false))
        assertFalse(longPressOpensContext(menu, shellTakesIt = false, layerOpen = false, busy = false))
    }

    @Test fun nothingOpensOverAnotherLayerOrAnOverlay() {
        assertFalse(longPressOpensContext(menu, shellTakesIt = true, layerOpen = true, busy = false))
        assertFalse(longPressOpensContext(menu, shellTakesIt = true, layerOpen = false, busy = true))
    }
}
