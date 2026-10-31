// Copyright 2026, Radetski
// SPDX-License-Identifier: GPL-3.0

package engine.xray

import app.AppState
import app.ProxyServerState
import features.proxy.server.model.ProxyServer
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Test
import kotlin.test.assertEquals

class TransportSockoptAndroidOracleTest {
    @Test
    fun keepAliveAndTheDirectDomainStrategyReachEveryProxyOutboundAndTheDirectOutbound() {
        val server = ProxyServerState(
            id = 1,
            groupId = 0,
            server = ProxyServer.parse("vless://8b4a2b20-c533-4d13-a3e0-bb0a8d7eb9c6@alpha.example:443?type=tcp&security=tls#Alpha"),
        )
        val state = AppState(
            proxyServers = listOf(server),
            selectedProxyServerId = 1,
            tunTcpKeepAliveInterval = "30",
            tunTcpUserTimeout = "20000",
        )
        val built = XrayConfigFactory.buildXrayConfigResult(
            XrayConfigRequest(
                appState = state,
                selectedServer = server,
                inbounds = emptyList(),
                coreLogPaths = XrayCoreLogPaths("/tmp/a", "/tmp/e"),
                collectTrafficStats = false,
            ),
        )
        val outbounds = Json.parseToJsonElement(built.json).jsonObject.getValue("outbounds").jsonArray
            .map { it.jsonObject }.associateBy { it.getValue("tag").jsonPrimitive.content }

        assertEquals(
            """{"domainStrategy":"UseIPv4","tcpKeepAliveInterval":30,"tcpKeepAliveIdle":30,"tcpUserTimeout":20000}""",
            outbounds.getValue("proxy").getValue("streamSettings").jsonObject.getValue("sockopt").toString(),
        )
        assertEquals(
            """{"tcpKeepAliveInterval":30,"tcpKeepAliveIdle":30,"tcpUserTimeout":20000}""",
            outbounds.getValue("direct").getValue("streamSettings").jsonObject.getValue("sockopt").toString(),
        )
        assertEquals("UseIPv4", outbounds.getValue("direct").getValue("settings").jsonObject.getValue("domainStrategy").jsonPrimitive.content)
    }
}
