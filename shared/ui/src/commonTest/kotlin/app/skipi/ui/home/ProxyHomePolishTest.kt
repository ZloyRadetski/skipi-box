// Copyright 2026, Radetski
// SPDX-License-Identifier: GPL-3.0

package app.skipi.ui.home

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import app.skipi.app.home.ProxyHomeActionId
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class ProxyHomePolishTest {

    @Test
    fun emptyGroupDoesNotAllowLatencyTest() {
        val available = setOf(ProxyHomeActionId.TestGroup)
        val busy = emptySet<ProxyHomeActionId>()

        // Non-empty enabled group allows test
        assertTrue(
            canTestHomeGroup(
                groupEnabled = true,
                serverCount = 5,
                availableActions = available,
                busyActions = busy,
            ),
        )

        // Zero-server group MUST NOT allow test even if enabled
        assertFalse(
            canTestHomeGroup(
                groupEnabled = true,
                serverCount = 0,
                availableActions = available,
                busyActions = busy,
            ),
        )

        // Disabled group with servers does not allow test
        assertFalse(
            canTestHomeGroup(
                groupEnabled = false,
                serverCount = 3,
                availableActions = available,
                busyActions = busy,
            ),
        )

        // Busy test action blocks test
        assertFalse(
            canTestHomeGroup(
                groupEnabled = true,
                serverCount = 3,
                availableActions = available,
                busyActions = setOf(ProxyHomeActionId.TestGroup),
            ),
        )

        // Host latency state blocks a second test even when effect dispatch has finished.
        assertFalse(
            canTestHomeGroup(
                groupEnabled = true,
                serverCount = 3,
                availableActions = available,
                busyActions = busy,
                isTestingLatency = true,
            ),
        )
    }

    @Test
    fun powerActionDisabledDuringBusyOrConnectingStates() {
        val available = setOf(ProxyHomeActionId.ToggleTunnel)

        // Idle ready state allows toggle
        assertTrue(
            canToggleHomePower(
                canToggleTunnel = true,
                tunnelBusy = false,
                isConnecting = false,
                availableActions = available,
                busyActions = emptySet(),
            ),
        )

        // Tunnel busy suppresses toggle (double-tap protection)
        assertFalse(
            canToggleHomePower(
                canToggleTunnel = true,
                tunnelBusy = true,
                isConnecting = false,
                availableActions = available,
                busyActions = emptySet(),
            ),
        )

        // Connecting state suppresses toggle
        assertFalse(
            canToggleHomePower(
                canToggleTunnel = true,
                tunnelBusy = false,
                isConnecting = true,
                availableActions = available,
                busyActions = emptySet(),
            ),
        )

        // Busy actions set containing ToggleTunnel suppresses toggle
        assertFalse(
            canToggleHomePower(
                canToggleTunnel = true,
                tunnelBusy = false,
                isConnecting = false,
                availableActions = available,
                busyActions = setOf(ProxyHomeActionId.ToggleTunnel),
            ),
        )

        // When toggle capability is false
        assertFalse(
            canToggleHomePower(
                canToggleTunnel = false,
                tunnelBusy = false,
                isConnecting = false,
                availableActions = available,
                busyActions = emptySet(),
            ),
        )
    }

    @Test
    fun compactConnectedCardKeepsOnSurfaceTextReadableWithBrightAccent() {
        val surface = Color(0xFF0A0A0A)
        val brightAccent = Color(0xFFF1F1F3)
        val background = skipiProxyHeroCompactBackground(
            surface = surface,
            accent = brightAccent,
            connected = true,
        )

        assertTrue(contrastRatio(Color(0xFFF1F1F3), background) >= 4.5f)
        assertTrue(skipiProxyHeroCompactBackground(surface, brightAccent, connected = false) == surface)
    }

    private fun contrastRatio(foreground: Color, background: Color): Float {
        val foregroundLuminance = foreground.luminance()
        val backgroundLuminance = background.luminance()
        val lighter = maxOf(foregroundLuminance, backgroundLuminance)
        val darker = minOf(foregroundLuminance, backgroundLuminance)
        return (lighter + 0.05f) / (darker + 0.05f)
    }

}
