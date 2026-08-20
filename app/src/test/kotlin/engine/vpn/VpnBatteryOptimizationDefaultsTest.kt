// Copyright 2026, Radetski
// SPDX-License-Identifier: GPL-3.0

package engine.vpn

import app.AppState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test

class VpnBatteryOptimizationDefaultsTest {

    @Test
    fun appState_enableWakeLock_defaults_to_false() {
        val state = AppState()
        assertFalse("enableWakeLock should default to false to prevent battery drain", state.enableWakeLock)
    }

    @Test
    fun vpnDefaults_tcpKeepAliveInterval_is_optimized_for_mobile_sleep() {
        assertEquals("60", VpnDefaults.TCP_KEEP_ALIVE_INTERVAL)
        val state = AppState()
        assertEquals("60", state.tunTcpKeepAliveInterval)
    }
}
