// Copyright 2026, Radetski
// SPDX-License-Identifier: GPL-3.0

package engine.xray

import app.AppState
import app.ProxyServerState
import features.proxy.server.model.OlcRtc
import features.proxy.server.model.ProxyServerConstants
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class XrayOutboundOlcRtcTest {

    @Test
    fun testBuildXrayOutboundPlanForOlcRtc() {
        val olcrtc = OlcRtc(
            remarks = "Telemost Video Call",
            provider = "telemost",
            transport = "vp8channel",
            roomUrl = "https://telemost.yandex.ru/j/1234567890",
            encryptionKey = "0123456789abcdef0123456789abcdef0123456789abcdef0123456789abcdef",
            payload = "mode=turbo",
            localSocksPort = "10808",
        )
        val serverState = ProxyServerState(
            id = 2,
            server = olcrtc,
            groupId = 0,
        )
        val appState = AppState(
            proxyServers = listOf(serverState),
            selectedProxyServerId = 2,
        )

        val plan = appState.buildXrayOutboundPlan(serverState)
        assertNotNull(plan)
        assertEquals(1, plan.proxyOutbounds.size)

        val proxyOutbound = plan.proxyOutbounds.first()
        assertEquals(XrayTags.PROXY, proxyOutbound.tag)

        val outboundObj = proxyOutbound.server?.toXrayOutbound(XrayTags.PROXY)
        assertNotNull(outboundObj)
        assertEquals(ProxyServerConstants.PROTOCOL_SOCKS, outboundObj.protocol)

        val servers = outboundObj.settings["servers"]?.jsonArray
        assertNotNull(servers)
        assertEquals(1, servers.size)

        val firstServer = servers.first().jsonObject
        assertEquals("127.0.0.1", firstServer["address"]?.toString()?.removeSurrounding("\""))
        assertEquals(10808, firstServer["port"]?.toString()?.toIntOrNull())

        // Test YAML generation
        val yaml = olcrtc.toOlcRtcYamlConfig(10808, dnsServer = "1.1.1.1:53")
        assertTrue(yaml.contains("provider: telemost"))
        assertTrue(yaml.contains("transport: vp8channel"))
        assertTrue(yaml.contains("room: https://telemost.yandex.ru/j/1234567890"))
        assertTrue(yaml.contains("socks5_listen: 127.0.0.1:10808"))

        // Test YAML generation with credentials
        val authYaml = olcrtc.toOlcRtcYamlConfig(12345, "secret_user", "secret_pass", dnsServer = "1.1.1.1:53")
        assertTrue(authYaml.contains("socks5_listen: 127.0.0.1:12345"))
        assertTrue(authYaml.contains("socks5_user: secret_user"))
        assertTrue(authYaml.contains("socks5_pass: secret_pass"))

        // Test Outbound with custom port and auth
        val authOutbound = olcrtc.toXrayOutboundWithPortAndAuth(XrayTags.PROXY, 12345, "secret_user", "secret_pass")
        val authServers = authOutbound.settings["servers"]?.jsonArray
        assertNotNull(authServers)
        val authFirstServer = authServers.first().jsonObject
        assertEquals(12345, authFirstServer["port"]?.toString()?.toIntOrNull())
        val users = authFirstServer["users"]?.jsonArray
        assertNotNull(users)
        assertEquals(1, users.size)
        val firstUser = users.first().jsonObject
        assertEquals("secret_user", firstUser["user"]?.toString()?.removeSurrounding("\""))
        assertEquals("secret_pass", firstUser["pass"]?.toString()?.removeSurrounding("\""))
    }

    @Test
    fun testFindAvailableLocalPortAvoidsReservedPorts() {
        val reserved = setOf(10808, 10809)
        val allocated = engine.vpn.findAvailableLocalPort(10808, reserved)
        assertTrue(allocated !in reserved)
        assertTrue(allocated in 1024..65535)
    }

    @Test
    fun testXraySpeedTestConfigInjectsActiveBridge() {
        val olcrtc = OlcRtc(
            remarks = "Telemost Video Call",
            provider = "telemost",
            transport = "vp8channel",
            roomUrl = "https://telemost.yandex.ru/j/1234567890",
            encryptionKey = "0123456789abcdef0123456789abcdef0123456789abcdef0123456789abcdef",
            payload = "mode=turbo",
            localSocksPort = "10808",
        )
        val serverState = ProxyServerState(
            id = 1,
            server = olcrtc,
            groupId = 0,
        )

        val activeBridge = engine.vpn.ActiveOlcRtcBridge(
            socksPort = 23456,
            socksUser = "active_user",
            socksPass = "active_pass",
            server = olcrtc,
        )

        val bridgeField = engine.vpn.SkipiCoreRuntime::class.java.getDeclaredField("activeOlcRtcBridge")
        bridgeField.isAccessible = true
        bridgeField.set(engine.vpn.SkipiCoreRuntime, activeBridge)

        try {
            val appState = AppState(
                proxyServers = listOf(serverState),
                selectedProxyServerId = 1,
            )
            val configJson = XraySpeedTestConfigFactory.buildXraySpeedTestConfig(
                XrayConfigRequest(
                    appState = appState,
                    selectedServer = serverState,
                    inbounds = emptyList(),
                    coreLogPaths = XrayCoreLogPaths(
                        accessLogPath = "access.log",
                        errorLogPath = "error.log",
                    ),
                    dataDir = "",
                ),
            )

            assertTrue(configJson.contains("\"port\": 23456") || configJson.contains("\"port\":23456"))
            assertTrue(configJson.contains("active_user"))
            assertTrue(configJson.contains("active_pass"))
        } finally {
            bridgeField.set(engine.vpn.SkipiCoreRuntime, null)
        }
    }
}
