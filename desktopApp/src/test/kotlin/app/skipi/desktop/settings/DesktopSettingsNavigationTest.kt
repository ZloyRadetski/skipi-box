// Copyright 2026, Radetski
// SPDX-License-Identifier: GPL-3.0

package app.skipi.desktop

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.ImageComposeScene
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.toArgb
import app.skipi.ui.settings.SkipiVpnSettingsScreen
import app.skipi.ui.settings.VpnSettingsState
import app.skipi.ui.settings.rememberVpnSettingsLabels
import app.skipi.ui.theme.ProvideSkipiTheme
import app.skipi.ui.theme.SkipiVisualProfile
import app.skipi.ui.theme.resolveBaseAppearanceColors
import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class DesktopSettingsNavigationTest {
    /** Pixels in the top-left corner, where the back arrow sits, that clearly differ from the page. */
    private fun arrowPixels(dark: Boolean): Int {
        val colors = resolveBaseAppearanceColors(isDark = dark)
        val background = colors.background.toArgb()
        val scene = ImageComposeScene(width = 900, height = 600)
        try {
            scene.setContent {
                ProvideSkipiTheme(
                    colors = colors,
                    fontFamily = null,
                    fontSizeMode = null,
                    fontWeightMode = null,
                    visualProfile = SkipiVisualProfile.Desktop,
                ) {
                    Box(Modifier.fillMaxSize().background(colors.background)) {
                        SkipiVpnSettingsScreen(
                            state = VpnSettingsState(
                                tunSummary = "", hevTunEnabled = true, appendHttpProxyEnabled = false,
                                strictFullTunnelEnabled = false, autoConnectOnAppOpen = false, autoConnectOnBoot = false,
                                killSwitchEnabled = false, batteryOptimizationUnrestricted = true, wakeLockEnabled = false,
                                seamlessNetworkSwitchingEnabled = false,
                            ),
                            labels = rememberVpnSettingsLabels(),
                            padding = PaddingValues(),
                            isWideScreen = true,
                            onBack = {},
                            onOpenTunSettings = {}, onHevTunChange = {}, onAppendHttpProxyChange = {},
                            onStrictFullTunnelChange = {}, onAutoConnectOnOpenChange = {}, onAutoConnectOnBootChange = {},
                            onKillSwitchChange = {}, onBatteryOptimizationChange = {}, onWakeLockChange = {},
                            onSeamlessSwitchingChange = {}, onOpenSystemVpnSettings = {}, onOpenNetworkAutomation = {},
                        )
                    }
                }
            }
            var frame = 0L
            repeat(40) { frame += 16_666_667L; scene.render(frame).close() }
            val image = scene.render(frame + 16_666_667L)
            try {
                val pixels = requireNotNull(image.peekPixels())
                try {
                    fun luma(argb: Int) = 0.299 * ((argb shr 16) and 0xFF) + 0.587 * ((argb shr 8) and 0xFF) + 0.114 * (argb and 0xFF)
                    var count = 0
                    for (y in 0 until 64) for (x in 0 until 72) {
                        if (abs(luma(pixels.getColor(x, y)) - luma(background)) > 90) count++
                    }
                    return count
                } finally {
                    pixels.close()
                }
            } finally {
                image.close()
            }
        } finally {
            scene.close()
        }
    }

    @Test
    fun theBackArrowIsVisibleOnTheDarkTheme() {
        assertTrue(arrowPixels(dark = true) >= 30, "no back arrow pixels in the top-left corner on the dark theme")
    }

    @Test
    fun theBackArrowIsVisibleOnTheLightTheme() {
        assertTrue(arrowPixels(dark = false) >= 30, "no back arrow pixels in the top-left corner on the light theme")
    }

    @Test
    fun escapeStepsBackToTheParentPageAndStopsAtTheOverview() {
        assertEquals(DesktopSettingsDestination.Overview, desktopSettingsParent(DesktopSettingsDestination.Vpn))
        assertEquals(DesktopSettingsDestination.Overview, desktopSettingsParent(DesktopSettingsDestination.LocalProxy))
        assertEquals(DesktopSettingsDestination.Subscriptions, desktopSettingsParent(DesktopSettingsDestination.SubscriptionPing))
        assertEquals(DesktopSettingsDestination.Subscriptions, desktopSettingsParent(DesktopSettingsDestination.SubscriptionUserAgents))
        assertEquals(DesktopSettingsDestination.General, desktopSettingsParent(DesktopSettingsDestination.UrlSchemes))
        assertEquals(null, desktopSettingsParent(DesktopSettingsDestination.Overview))
    }

    @Test
    fun onlyAKeyDownOfEscapeGoesBack() {
        assertTrue(isEscapeBack(androidx.compose.ui.input.key.Key.Escape, androidx.compose.ui.input.key.KeyEventType.KeyDown))
        assertEquals(false, isEscapeBack(androidx.compose.ui.input.key.Key.Escape, androidx.compose.ui.input.key.KeyEventType.KeyUp))
        assertEquals(false, isEscapeBack(androidx.compose.ui.input.key.Key.Enter, androidx.compose.ui.input.key.KeyEventType.KeyDown))
    }

    @Test
    fun theEscapeHandlerOnlyConsumesTheKeyWhileAPageIsRegistered() {
        val handler = DesktopEscapeHandler()
        var backs = 0

        assertEquals(false, handler.onEscape())

        handler.register { backs += 1 }
        assertTrue(handler.onEscape())
        assertEquals(1, backs)

        handler.register(null)
        assertEquals(false, handler.onEscape())
    }
}
