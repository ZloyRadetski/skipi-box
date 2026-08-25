// Copyright 2026, Radetski
// SPDX-License-Identifier: GPL-3.0

package features.proxy

import app.AppState
import app.modes.ConnectionDisplayModeClassic
import app.modes.ConnectionDisplayModeCompact
import org.junit.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class ConnectionDisplayModeTest {

    @Test
    fun testConnectionPanelUnpinnedByDefault() {
        assertFalse(AppState().pinConnectionPanelOnHome)
    }

    @Test
    fun testPinConnectionPanelToggle() {
        val defaultState = AppState()
        assertFalse(defaultState.pinConnectionPanelOnHome)
        val pinnedState = defaultState.copy(pinConnectionPanelOnHome = true)
        assertTrue(pinnedState.pinConnectionPanelOnHome)
        val unpinnedState = pinnedState.copy(pinConnectionPanelOnHome = false)
        assertFalse(unpinnedState.pinConnectionPanelOnHome)
    }

    private fun resolveUserScrollEnabled(
        pinConnectionPanelOnHome: Boolean,
        enableSubscriptionSwipe: Boolean,
    ): Boolean {
        return enableSubscriptionSwipe
    }

    @Test
    fun testSubscriptionSwipeResolution() {
        // Pinned with swipe enabled -> swipe active
        assertTrue(resolveUserScrollEnabled(pinConnectionPanelOnHome = true, enableSubscriptionSwipe = true))

        // Pinned with swipe disabled -> swipe disabled
        assertFalse(resolveUserScrollEnabled(pinConnectionPanelOnHome = true, enableSubscriptionSwipe = false))

        // Unpinned with swipe enabled -> swipe active
        assertTrue(resolveUserScrollEnabled(pinConnectionPanelOnHome = false, enableSubscriptionSwipe = true))

        // Unpinned with swipe disabled -> swipe disabled
        assertFalse(resolveUserScrollEnabled(pinConnectionPanelOnHome = false, enableSubscriptionSwipe = false))
    }

    private fun resolveFloatingToolbarVisibility(
        connectionDisplayMode: Int,
        classicShowFloatingPowerButton: Boolean,
        proxyRunning: Boolean,
    ): Pair<Boolean, Boolean> {
        val showFloatingPowerButton = connectionDisplayMode == ConnectionDisplayModeCompact ||
            classicShowFloatingPowerButton
        val showFloatingToolbar = showFloatingPowerButton ||
            (proxyRunning && connectionDisplayMode == ConnectionDisplayModeClassic)
        return Pair(showFloatingPowerButton, showFloatingToolbar)
    }

    @Test
    fun testCompactModeAlwaysShowsPowerButtonAndToolbar() {
        // Disconnected
        val (showPower1, showToolbar1) = resolveFloatingToolbarVisibility(
            connectionDisplayMode = ConnectionDisplayModeCompact,
            classicShowFloatingPowerButton = false,
            proxyRunning = false,
        )
        assertTrue(showPower1)
        assertTrue(showToolbar1)

        // Connected
        val (showPower2, showToolbar2) = resolveFloatingToolbarVisibility(
            connectionDisplayMode = ConnectionDisplayModeCompact,
            classicShowFloatingPowerButton = false,
            proxyRunning = true,
        )
        assertTrue(showPower2)
        assertTrue(showToolbar2)
    }

    @Test
    fun testClassicModeWithFloatingPowerButtonEnabled() {
        // Disconnected
        val (showPower1, showToolbar1) = resolveFloatingToolbarVisibility(
            connectionDisplayMode = ConnectionDisplayModeClassic,
            classicShowFloatingPowerButton = true,
            proxyRunning = false,
        )
        assertTrue(showPower1)
        assertTrue(showToolbar1)

        // Connected
        val (showPower2, showToolbar2) = resolveFloatingToolbarVisibility(
            connectionDisplayMode = ConnectionDisplayModeClassic,
            classicShowFloatingPowerButton = true,
            proxyRunning = true,
        )
        assertTrue(showPower2)
        assertTrue(showToolbar2)
    }

    @Test
    fun testClassicModeWithFloatingPowerButtonDisabled() {
        // Disconnected: toolbar is completely hidden
        val (showPower1, showToolbar1) = resolveFloatingToolbarVisibility(
            connectionDisplayMode = ConnectionDisplayModeClassic,
            classicShowFloatingPowerButton = false,
            proxyRunning = false,
        )
        assertFalse(showPower1)
        assertFalse(showToolbar1)

        // Connected: toolbar is shown for standalone ping button, but power button is disabled
        val (showPower2, showToolbar2) = resolveFloatingToolbarVisibility(
            connectionDisplayMode = ConnectionDisplayModeClassic,
            classicShowFloatingPowerButton = false,
            proxyRunning = true,
        )
        assertFalse(showPower2)
        assertTrue(showToolbar2)
    }
}
