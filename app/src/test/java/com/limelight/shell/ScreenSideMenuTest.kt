package com.limelight.shell

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Where the left menu button opens the side menu. The shell's key listener is installed once, so it must ask the current
 * screen ([Screen.sideMenu]) on every press: a fresh install starts in the first launch, where there is no side menu, and
 * a value captured then kept the button dead on every later screen (found on the 1.0.0 release candidate).
 */
class ScreenSideMenuTest {
    @Test
    fun theMainScreensAndTheStartScreenHaveTheSideMenu() {
        for (screen in listOf(Screen.Home, Screen.Settings, Screen.Profile, Screen.Devices, Screen.Start)) {
            assertTrue("$screen", screen.sideMenu)
        }
    }

    @Test
    fun theFirstLaunchAndTheFullScreenFlowsHaveNone() {
        for (screen in listOf(Screen.Boot(), Screen.CreateProfile(editing = false, back = null), Screen.PairPc(back = null))) {
            assertFalse("$screen", screen.sideMenu)
        }
    }

    @Test
    fun leavingTheFirstLaunchTurnsTheSideMenuOn() {
        var screen: Screen = Screen.Boot()
        val listener = { screen.sideMenu }   // reads the screen at the press, like the shell's listener
        assertFalse(listener())
        screen = Screen.Home
        assertTrue(listener())
    }
}
