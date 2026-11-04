// Copyright 2026, Radetski
// SPDX-License-Identifier: GPL-3.0

package app.skipi.ui.home

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class FloatingToolbarVisibilityTest {
    @Test
    fun pingButtonDoesNotDependOnTheTunnelBeingRunning() {
        assertEquals(
            FloatingToolbarButtons(toggle = true, ping = true),
            floatingToolbarButtons(showToggleAction = true, showPingAction = true),
        )
    }

    @Test
    fun pingAloneKeepsTheToolbarVisibleEvenWithoutTheToggle() {
        val buttons = floatingToolbarButtons(showToggleAction = false, showPingAction = true)
        assertTrue(buttons.ping)
        assertFalse(buttons.toggle)
        assertTrue(buttons.anyVisible)
    }

    @Test
    fun nothingIsShownWhenBothActionsAreOff() {
        assertFalse(floatingToolbarButtons(showToggleAction = false, showPingAction = false).anyVisible)
    }
}
