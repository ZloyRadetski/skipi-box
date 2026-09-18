// Copyright 2026, Radetski
// SPDX-License-Identifier: GPL-3.0

package engine.vpn

import app.AppState
import engine.proxy.LocalProxyOptions
import engine.xray.XrayCoreLogPaths
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class VpnTunIpv6ConfigTest {

    @Test
    fun vpnDefaults_haveGamingOptimizedMtuAndIpv6Constants() {
        assertEquals(1400, VpnDefaults.MTU)
        assertEquals("2001:4860:4860::8888", VpnDefaults.IPV6_DNS)
        assertEquals("::/0", VpnDefaults.IPV6_ALL_CIDR)
    }

    @Test
    fun appState_defaultTunMtuIsOptimized() {
        assertEquals("1400", AppState().tunMtu)
    }

    @Test
    fun buildVpnTunInbound_alwaysIncludesIpv6Gateway() {
        val appState = AppState(enableIpv6 = false)
        val tunOptions = appState.toTunOptions()
        val inbound = buildVpnTunInbound(appState, tunOptions)

        val settings = inbound["settings"]?.jsonObject
        assertNotNull(settings)
        val gatewayArray = settings?.get("gateway")?.jsonArray
        assertNotNull(gatewayArray)

        val gateways = gatewayArray?.map { it.jsonPrimitive.content }.orEmpty()
        assertTrue("Expected IPv4 gateway in TUN settings", gateways.any { it.startsWith("172.19.") })
        assertTrue("Expected IPv6 gateway in TUN settings even when enableIpv6 is false", gateways.any { it.contains(":") })
    }

    @Test
    fun buildVpnHevSocks5TunnelConfig_alwaysIncludesIpv6Address() {
        val appState = AppState(enableIpv6 = false)
        val tunOptions = appState.toTunOptions()
        val config = buildVpnHevSocks5TunnelConfig(
            dataDir = "/data/data/com.example/files",
            coreLogPaths = XrayCoreLogPaths(
                accessLogPath = "/data/data/com.example/files/logs/access.log",
                errorLogPath = "/data/data/com.example/files/logs/error.log",
            ),
            localProxyOptions = LocalProxyOptions(
                listenAddress = "127.0.0.1",
                port = 10808,
                username = "u",
                password = "p",
            ),
            tunOptions = tunOptions,
            enableIpv6 = false,
            useHevTun = true,
        )

        assertNotNull(config)
        assertEquals("fdfe:dcba:9876::1", config?.ipv6Address)
    }
}
