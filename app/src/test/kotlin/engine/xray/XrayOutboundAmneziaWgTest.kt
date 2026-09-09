// Copyright 2026, Radetski
// SPDX-License-Identifier: GPL-3.0

package engine.xray

import app.AppState
import app.ProxyServerState
import features.proxy.server.model.AmneziaWg
import features.proxy.server.model.ProxyServerConstants
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class XrayOutboundAmneziaWgTest {

    @Test
    fun testBuildXrayOutboundPlanForAmneziaWg() {
        val awg = AmneziaWg(
            remarks = "AWG Server",
            server = "192.0.2.1",
            port = "51820",
            secretKey = "aGVsbG8gd29ybGQgdGhpcyBpcyBhIHZhbGlkIGtleSE=",
            publicKey = "YW5vdGhlciB2YWxpZCBrZXkgZm9yIHRlc3Rpbmcgb2s=",
            address = "10.8.0.2/32",
            jc = "5",
            jmin = "30",
            jmax = "80",
            s1 = "20",
            s2 = "40",
            s3 = "60",
            s4 = "80",
            h1 = "11111",
            h2 = "22222",
            h3 = "33333",
            h4 = "44444",
        )
        val serverState = ProxyServerState(
            id = 1,
            server = awg,
            groupId = 0,
        )
        val appState = AppState(
            proxyServers = listOf(serverState),
            selectedProxyServerId = 1,
        )

        val plan = appState.buildXrayOutboundPlan(serverState)
        assertNotNull(plan)
        assertEquals(1, plan.proxyOutbounds.size)

        val proxyOutbound = plan.proxyOutbounds.first()
        assertEquals(XrayTags.PROXY, proxyOutbound.tag)

        val outboundObj = proxyOutbound.server?.toXrayOutbound(XrayTags.PROXY)
        assertNotNull(outboundObj)
        assertEquals(ProxyServerConstants.PROTOCOL_WIREGUARD, outboundObj.protocol)

        val settings = outboundObj.settings
        assertEquals(5, settings["jc"]?.toString()?.toIntOrNull())
        assertEquals(30, settings["jmin"]?.toString()?.toIntOrNull())
        assertEquals(80, settings["jmax"]?.toString()?.toIntOrNull())
        assertEquals(20, settings["s1"]?.toString()?.toIntOrNull())
        assertEquals(40, settings["s2"]?.toString()?.toIntOrNull())
        assertEquals(60, settings["s3"]?.toString()?.toIntOrNull())
        assertEquals(80, settings["s4"]?.toString()?.toIntOrNull())
        assertEquals(11111L, settings["h1"]?.toString()?.toLongOrNull())
        assertEquals(22222L, settings["h2"]?.toString()?.toLongOrNull())
        assertEquals(33333L, settings["h3"]?.toString()?.toLongOrNull())
        assertEquals(44444L, settings["h4"]?.toString()?.toLongOrNull())
    }

    @Test
    fun speedTestConfigInjectsOnlyMatchingActiveAmneziaWgBridge() {
        val active = AmneziaWg(
            remarks = "AWG Server",
            server = "192.0.2.1",
            port = "51820",
            secretKey = "aGVsbG8gd29ybGQgdGhpcyBpcyBhIHZhbGlkIGtleSE=",
            publicKey = "YW5vdGhlciB2YWxpZCBrZXkgZm9yIHRlc3Rpbmcgb2s=",
        )
        val sameRuntime = active.copy(remarks = "Other label")
        val serverState = ProxyServerState(id = 1, server = sameRuntime, groupId = 0)
        val bridge = engine.vpn.ActiveAmneziaWgBridge(socksPort = 23457, server = active)
        val bridgeField = engine.vpn.SkipiCoreRuntime::class.java.getDeclaredField("activeAmneziaWgBridge")
        bridgeField.isAccessible = true
        bridgeField.set(engine.vpn.SkipiCoreRuntime, bridge)

        try {
            val configJson = XraySpeedTestConfigFactory.buildXraySpeedTestConfig(
                XrayConfigRequest(
                    appState = AppState(proxyServers = listOf(serverState), selectedProxyServerId = 1),
                    selectedServer = serverState,
                    inbounds = emptyList(),
                    coreLogPaths = XrayCoreLogPaths(accessLogPath = "access.log", errorLogPath = "error.log"),
                ),
            )
            assertTrue(configJson.contains("23457"))
            assertTrue(configJson.contains("\"protocol\":\"socks\"") || configJson.contains("\"protocol\": \"socks\""))
        } finally {
            bridgeField.set(engine.vpn.SkipiCoreRuntime, null)
        }
    }
}
