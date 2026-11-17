package com.limelight.shell

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

class StreamMenuOutsideTapTest {
    @Test fun everyPageDecidesAndAgreesWithDismiss() {
        for (page in MenuPage.values()) {
            val expected = if (page == MenuPage.MAIN) OutsideTap.CLOSE_MENU else OutsideTap.CLOSE_POPOVER
            assertEquals(page.name, expected, outsideTapAction(page, closing = false))
            assertEquals(page.name, OutsideTap.NONE, outsideTapAction(page, closing = true))
            if (page == MenuPage.MAIN) assertNull(pageAfterDismiss(page)) else assertNotNull(pageAfterDismiss(page))
        }
    }
}
