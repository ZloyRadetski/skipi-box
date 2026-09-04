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
        val yaml = olcrtc.toOlcRtcYamlConfig(10808)
        assertTrue(yaml.contains("provider: telemost"))
        assertTrue(yaml.contains("transport: vp8channel[mode=turbo]"))
        assertTrue(yaml.contains("room: https://telemost.yandex.ru/j/1234567890"))
        assertTrue(yaml.contains("socks5_listen: 127.0.0.1:10808"))
    }
}
