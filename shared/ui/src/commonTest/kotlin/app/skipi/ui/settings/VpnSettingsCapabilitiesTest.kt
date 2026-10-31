// Copyright 2026, Radetski
// SPDX-License-Identifier: GPL-3.0

package app.skipi.ui.settings

import kotlin.test.Test
import kotlin.test.assertTrue

class VpnSettingsCapabilitiesTest {
    @Test
    fun everyRowIsAvailableByDefaultSoAndroidKeepsItsPage() {
        val capabilities = VpnSettingsCapabilities()

        assertTrue(
            listOf(
                capabilities.hevTun,
                capabilities.appendHttpProxy,
                capabilities.strictFullTunnel,
                capabilities.autoConnectOnBoot,
                capabilities.killSwitch,
                capabilities.systemVpnSettings,
                capabilities.batteryOptimization,
                capabilities.wakeLock,
                capabilities.seamlessSwitching,
            ).all { it },
        )
    }
}
