// Copyright 2026, Radetski
// SPDX-License-Identifier: GPL-3.0

package engine.vpn

import app.AppState
import app.withVpnSettingsReset
import features.config.TrafficConfigAndroidSettings
import org.junit.Assert.assertTrue
import org.junit.Test

class HevTunDefaultsTest {

    @Test
    fun appState_enableVpnHevTun_defaults_to_true() {
        val state = AppState()
        assertTrue("enableVpnHevTun should default to true", state.enableVpnHevTun)
    }

    @Test
    fun trafficConfigAndroidSettings_enableVpnHevTun_defaults_to_true() {
        val settings = TrafficConfigAndroidSettings()
        assertTrue("TrafficConfigAndroidSettings.enableVpnHevTun should default to true", settings.enableVpnHevTun)
    }

    @Test
    fun withVpnSettingsReset_restores_enableVpnHevTun_to_true() {
        val state = AppState(enableVpnHevTun = false)
        val resetState = state.withVpnSettingsReset()
        assertTrue("withVpnSettingsReset should restore enableVpnHevTun to true", resetState.enableVpnHevTun)
    }
}
