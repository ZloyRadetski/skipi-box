// Copyright 2026, Radetski
// SPDX-License-Identifier: GPL-3.0

package app.skipi.desktop

import app.skipi.app.model.RoutingConfigRecord
import features.proxy.server.model.AmneziaWg
import features.proxy.server.model.ChainProxy
import features.proxy.server.model.ProxyServer
import features.proxy.server.model.Socks
import features.proxy.server.model.StrategyGroup
import features.proxy.server.model.StrategyGroupConstants
import features.proxy.server.model.VLESS
import features.proxy.server.model.encodePersistedProxyServer
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import platform.LocalProxyXrayConfigOptions
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * An AmneziaWG server runs in SKIPI Core's native runner behind a private loopback SOCKS port;
 * Xray reaches it through an ordinary SOCKS outbound. Desktop plans that the way Android does.
 */
class DesktopAmneziaWgConfigTest {
    private val options = LocalProxyXrayConfigOptions(socksPort = 19_084)

    private fun awg() = AmneziaWg(
        remarks = "Native AmneziaWG",
        server = "198.51.100.10",
        port = "51820",
        secretKey = "aGVsbG8gd29ybGQgdGhpcyBpcyBhIHZhbGlkIGtleSE=",
        publicKey = "YW5vdGhlciB2YWxpZCBrZXkgZm9yIHRlc3Rpbmcgb2s=",
    )

    private fun stored(id: Int, server: ProxyServer<*>) =
        DesktopStoredProxyServer(id = id, serverJson = server.encodePersistedProxyServer())

    private fun library(selectedId: Int, vararg servers: DesktopStoredProxyServer) =
        DesktopServerLibrary(selectedServerId = selectedId, servers = servers.toList())

    private fun build(
        library: DesktopServerLibrary,
        configs: DesktopConfigLibrary = DesktopConfigLibrary(),
    ): DesktopGeneratedXrayConfig = assertNotNull(
        buildDesktopSelectedServerConfigWithBridge(
            serverLibrary = library,
            configLibrary = configs,
            routing = RoutingConfigRecord(),
            options = options,
            trafficConfigContentById = configs.configs.associate { it.id to it.content },
            subscriptionPingUrl = "",
            latencyByServerId = emptyMap(),
        ),
    ).getOrThrow()

    private fun JsonObject.outbounds() = getValue("outbounds").jsonArray.map { it.jsonObject }

    /** Ports of every SOCKS outbound, whether Xray's settings list servers or carry the address directly. */
    private fun JsonObject.socksPorts(): List<Int> = outbounds()
        .filter { it.getValue("protocol").jsonPrimitive.content == "socks" }
        .mapNotNull { outbound ->
            val settings = outbound.getValue("settings").jsonObject
            val target = settings["servers"]?.jsonArray?.firstOrNull()?.jsonObject ?: settings
            target["port"]?.jsonPrimitive?.content?.toIntOrNull()
        }

    @Test
    fun aSelectedAmneziaWgServerBecomesALoopbackSocksOutboundWithARunnerBridge() {
        val built = build(library(10, stored(10, awg())))

        val bridge = assertNotNull(built.amneziaWg)
        assertTrue(bridge.socksPort in 1024..65_535)
        assertTrue(bridge.socksPort != options.socksPort)
        assertTrue(bridge.configJson.contains("secretKey"))
        assertTrue(bridge.configJson.contains("dnsServers"))
        val root = Json.parseToJsonElement(built.json).jsonObject
        val protocols = root.outbounds().map { it.getValue("protocol").jsonPrimitive.content }
        assertTrue("wireguard" !in protocols, "Xray cannot run AmneziaWG itself")
        assertTrue(bridge.socksPort in root.socksPorts())
    }

    @Test
    fun withoutAnAmneziaWgServerThereIsNoBridge() {
        val built = build(library(10, stored(10, VLESS(remarks = "v", id = "u", server = "v.example", port = "443"))))

        assertNull(built.amneziaWg)
    }

    @Test
    fun aChainAndAnAutomaticGroupReachTheirAmneziaWgMemberThroughTheBridge() {
        val regular = Socks(remarks = "Regular", server = "hop.example", port = "1080")
        val chain = build(
            library(90, stored(10, regular), stored(20, awg()), stored(90, ChainProxy(remarks = "Chain", proxyServerIds = listOf(10, 20)))),
        )
        val group = build(
            library(
                90,
                stored(10, regular),
                stored(20, awg()),
                stored(
                    90,
                    StrategyGroup(
                        remarks = "Group",
                        strategy = StrategyGroupConstants.TYPE_LEAST_PING,
                        proxyServerIds = listOf(10, 20),
                    ),
                ),
            ),
        )

        for (built in listOf(chain, group)) {
            val bridge = assertNotNull(built.amneziaWg)
            val root = Json.parseToJsonElement(built.json).jsonObject
            assertTrue(bridge.socksPort in root.socksPorts())
            assertTrue(root.outbounds().none { it.getValue("protocol").jsonPrimitive.content == "wireguard" })
        }
    }

    @Test
    fun anActiveProfileReachesAnAmneziaWgServerThroughTheBridgeToo() {
        val profiles = DesktopConfigLibrary(
            selectedConfigId = 1,
            configs = listOf(DesktopStoredConfig(id = 1, name = "P", content = "[Rule]\nFINAL,PROXY\n")),
        )

        val built = build(library(10, stored(10, awg())), profiles)

        val bridge = assertNotNull(built.amneziaWg)
        val root = Json.parseToJsonElement(built.json).jsonObject
        assertTrue(root.outbounds().none { it.getValue("protocol").jsonPrimitive.content == "wireguard" })
        assertTrue(bridge.socksPort in root.socksPorts())
    }

    @Test
    fun theLegacyStringBuilderStillReturnsTheSameConfigJson() {
        val result = buildDesktopSelectedServerConfig(
            serverLibrary = library(10, stored(10, awg())),
            configLibrary = DesktopConfigLibrary(),
            routing = RoutingConfigRecord(),
            options = options,
            trafficConfigContentById = emptyMap(),
            subscriptionPingUrl = "",
            latencyByServerId = emptyMap(),
        )

        assertTrue(assertNotNull(result).isSuccess)
    }
}
