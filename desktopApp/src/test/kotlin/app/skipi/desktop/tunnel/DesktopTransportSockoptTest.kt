// Copyright 2026, Radetski
// SPDX-License-Identifier: GPL-3.0

package app.skipi.desktop

import app.skipi.app.model.RoutingConfigRecord
import engine.xray.XrayTcpSockopt
import features.proxy.server.model.ProxyServer
import features.proxy.server.model.encodePersistedProxyServer
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import platform.LocalProxyXrayConfigOptions
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * Same input and expectations as Android's `TransportSockoptAndroidOracleTest`: the TCP keep-alive
 * and user timeout, and the direct domain strategy, sit on every proxy outbound and on direct.
 */
class DesktopTransportSockoptTest {
    private val options = LocalProxyXrayConfigOptions(socksPort = 20_480, tcpSockopt = XrayTcpSockopt(30, 30, 20_000))

    private fun library() = DesktopServerLibrary(
        selectedServerId = 1,
        servers = listOf(
            DesktopStoredProxyServer(
                id = 1,
                serverJson = ProxyServer.parse(
                    "vless://8b4a2b20-c533-4d13-a3e0-bb0a8d7eb9c6@alpha.example:443?type=tcp&security=tls#Alpha",
                ).encodePersistedProxyServer(),
            ),
        ),
    )

    private fun outboundsOf(configs: DesktopConfigLibrary) = Json.parseToJsonElement(
        buildDesktopSelectedServerConfig(
            serverLibrary = library(),
            configLibrary = configs,
            routing = RoutingConfigRecord(),
            options = options,
            trafficConfigContentById = emptyMap(),
            subscriptionPingUrl = "",
            latencyByServerId = emptyMap(),
        )!!.getOrThrow(),
    ).jsonObject.getValue("outbounds").jsonArray.map { it.jsonObject }

    private fun assertTransport(outbounds: List<kotlinx.serialization.json.JsonObject>, proxyTag: String) {
        val byTag = outbounds.associateBy { it.getValue("tag").jsonPrimitive.content }
        assertEquals(
            """{"domainStrategy":"UseIPv4","tcpKeepAliveInterval":30,"tcpKeepAliveIdle":30,"tcpUserTimeout":20000}""",
            byTag.getValue(proxyTag).getValue("streamSettings").jsonObject.getValue("sockopt").toString(),
        )
        assertEquals(
            """{"tcpKeepAliveInterval":30,"tcpKeepAliveIdle":30,"tcpUserTimeout":20000}""",
            byTag.getValue("direct").getValue("streamSettings").jsonObject.getValue("sockopt").toString(),
        )
        assertEquals("UseIPv4", byTag.getValue("direct").getValue("settings").jsonObject.getValue("domainStrategy").jsonPrimitive.content)
    }

    @Test
    fun theGeneratedPathCarriesTheTransportSettings() {
        assertTransport(outboundsOf(DesktopConfigLibrary()), proxyTag = "proxy")
    }

    @Test
    fun theActiveProfilePathCarriesTheTransportSettings() {
        val profiles = DesktopConfigLibrary(
            selectedConfigId = 1,
            configs = listOf(DesktopStoredConfig(id = 1, name = "P", content = "[Rule]\nFINAL,PROXY\n")),
        )

        assertTransport(outboundsOf(profiles), proxyTag = "skipi-proxy")
    }
}
