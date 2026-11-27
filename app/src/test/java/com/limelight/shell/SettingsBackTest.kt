package com.limelight.shell

import org.junit.Assert.assertEquals
import org.junit.Test

class SettingsBackTest {
    @Test
    fun settingsOpenedFromTheStartScreenGoBackThereThroughItsOwnPages() {
        val state = ShellState(Screen.Start)
        state.navigate(Screen.Settings)
        state.navigate(Screen.SettingsSearch)
        state.back()
        assertEquals(Screen.Settings, state.screen)
        state.back()
        assertEquals(Screen.Start, state.screen)
    }

    @Test
    fun settingsOpenedFromADashboardGoBackToIt() {
        val state = ShellState(Screen.Start)
        state.navigate(Screen.Settings)
        state.back()
        state.navigate(Screen.Home)
        state.navigate(Screen.Settings)
        state.back()
        assertEquals(Screen.Home, state.screen)
    }
}
