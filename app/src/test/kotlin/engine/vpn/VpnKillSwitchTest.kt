// Copyright 2026, Radetski
// SPDX-License-Identifier: GPL-3.0

package engine.vpn

import app.AppState
import app.withVpnSettingsReset
import engine.proxy.LocalProxyOptions
import engine.xray.XrayCoreLogPaths
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class VpnKillSwitchTest {

    @Test
    fun appState_enableKillSwitch_defaults_to_false() {
        val state = AppState()
        assertFalse("enableKillSwitch should default to false", state.enableKillSwitch)
    }

    @Test
    fun withVpnSettingsReset_restores_enableKillSwitch_to_false() {
        val state = AppState(enableKillSwitch = true)
        val resetState = state.withVpnSettingsReset()
        assertFalse("withVpnSettingsReset should restore enableKillSwitch to false", resetState.enableKillSwitch)
    }

    @Test
    fun vpnServiceStartConfig_killSwitch_field_is_retained() {
        val config = VpnServiceStartConfig(
            sessionName = "SKIPI",
            dnsServers = listOf(VpnDefaults.IPV4_DNS),
            xrayConfigJson = "{}",
            applicationPolicy = VpnApplicationPolicy(),
            localProxyOptions = LocalProxyOptions(
                listenAddress = "127.0.0.1",
                port = 10808,
                username = "u",
                password = "p",
            ),
            appendHttpProxyOptions = VpnAppendHttpProxyOptions.Disabled,
            coreLogPaths = XrayCoreLogPaths(
                accessLogPath = "/tmp/access.log",
                errorLogPath = "/tmp/error.log",
            ),
            enableKillSwitch = true,
        )
        assertTrue("VpnServiceStartConfig should store enableKillSwitch=true", config.enableKillSwitch)
    }
}
