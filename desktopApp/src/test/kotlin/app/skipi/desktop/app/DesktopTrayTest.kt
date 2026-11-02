// Copyright 2026, Radetski
// SPDX-License-Identifier: GPL-3.0

package app.skipi.desktop

import kotlin.test.Test
import kotlin.test.assertEquals

class DesktopTrayTest {
    @Test
    fun theMenuOffersTheOppositeOfTheTunnelState() {
        assertEquals(DesktopTrayToggle.Connect, desktopTrayToggle(tunnelRunning = false))
        assertEquals(DesktopTrayToggle.Disconnect, desktopTrayToggle(tunnelRunning = true))
    }

    @Test
    fun aHiddenOrMinimizedWindowIsShownByTheTrayClick() {
        val quit = DesktopAppSettings(closeToBackground = false)
        assertEquals(DesktopTrayWindowStep.Show, desktopTrayWindowStep(windowShown = false, minimized = false, settings = quit))
        assertEquals(DesktopTrayWindowStep.Show, desktopTrayWindowStep(windowShown = true, minimized = true, settings = quit))
    }

    @Test
    fun aVisibleWindowIsHiddenToTheBackgroundOrMinimizedDependingOnTheCloseChoice() {
        assertEquals(
            DesktopTrayWindowStep.HideToBackground,
            desktopTrayWindowStep(true, false, DesktopAppSettings(closeToBackground = true)),
        )
        assertEquals(
            DesktopTrayWindowStep.Minimize,
            desktopTrayWindowStep(true, false, DesktopAppSettings(closeToBackground = false)),
        )
    }
}
