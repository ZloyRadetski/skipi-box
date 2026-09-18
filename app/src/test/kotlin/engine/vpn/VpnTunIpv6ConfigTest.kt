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
    fun buildVpnTunInbound_includesIpv6GatewayOnlyWhenEnableIpv6IsTrue() {
        val disabledState = AppState(enableIpv6 = false)
        val tunOptions = disabledState.toTunOptions()
        val disabledInbound = buildVpnTunInbound(disabledState, tunOptions)

        val disabledGateways = disabledInbound["settings"]?.jsonObject?.get("gateway")?.jsonArray
            ?.map { it.jsonPrimitive.content }.orEmpty()
        assertTrue("Expected IPv4 gateway in TUN settings", disabledGateways.any { it.startsWith("172.19.") })
        org.junit.Assert.assertFalse("Expected no IPv6 gateway in TUN settings when enableIpv6 is false", disabledGateways.any { it.contains(":") })

        val enabledState = AppState(enableIpv6 = true)
        val enabledInbound = buildVpnTunInbound(enabledState, tunOptions)
        val enabledGateways = enabledInbound["settings"]?.jsonObject?.get("gateway")?.jsonArray
            ?.map { it.jsonPrimitive.content }.orEmpty()
        assertTrue("Expected IPv6 gateway in TUN settings when enableIpv6 is true", enabledGateways.any { it.contains(":") })
    }

    @Test
    fun buildVpnHevSocks5TunnelConfig_includesIpv6AddressOnlyWhenEnableIpv6IsTrue() {
        val tunOptions = AppState().toTunOptions()
        val disabledConfig = buildVpnHevSocks5TunnelConfig(
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

        assertNotNull(disabledConfig)
        org.junit.Assert.assertNull(disabledConfig?.ipv6Address)

        val enabledConfig = buildVpnHevSocks5TunnelConfig(
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
            enableIpv6 = true,
            useHevTun = true,
        )

        assertNotNull(enabledConfig)
        assertEquals("fdfe:dcba:9876::1", enabledConfig?.ipv6Address)
    }
}
