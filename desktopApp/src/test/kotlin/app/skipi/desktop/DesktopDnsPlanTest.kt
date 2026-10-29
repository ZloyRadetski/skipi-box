// Copyright 2026, Radetski
// SPDX-License-Identifier: GPL-3.0

package app.skipi.desktop

import app.skipi.app.model.RoutingConfigRecord
import features.config.ConfigProfileLibrary
import features.proxy.server.model.ProxyServer
import features.proxy.server.model.Socks
import features.proxy.server.model.encodePersistedProxyServer
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/** Android's shared DNS plan (servers, bootstrap, hosts, upstream routing) on both Desktop config paths. */
class DesktopDnsPlanTest {
    private fun generated(): JsonObject {
        val server = Socks(remarks = "Proxy", server = "proxy.example", port = "1080")
        return Json.parseToJsonElement(
            assertNotNull(
                buildDesktopSelectedServerConfig(
                    serverLibrary = DesktopServerLibrary(
                        selectedServerId = 1,
                        servers = listOf(DesktopStoredProxyServer(1, server.encodePersistedProxyServer())),
                    ),
                    configLibrary = ConfigProfileLibrary(),
                    routing = RoutingConfigRecord(),
                    options = DesktopAppSettings().toLocalProxyXrayConfigOptions(),
                    trafficConfigContentById = emptyMap(),
                    subscriptionPingUrl = "http://127.0.0.1:18086/probe",
                    latencyByServerId = emptyMap(),
                ),
            ).getOrThrow(),
        ).jsonObject
    }

    private fun profile(content: String): JsonObject {
        val library = DesktopServerLibraries.select(
            DesktopServerLibraries.add(
                DesktopServerLibrary(),
                ProxyServer.parse("vless://8b4a2b20-c533-4d13-a3e0-bb0a8d7eb9c6@alpha.example:443#Alpha"),
            ),
            serverId = 1,
        )
        return Json.parseToJsonElement(
            DesktopTrafficProfileXrayConfigFactory.build(
                profile = DesktopStoredConfig(id = 1, name = "P", content = content),
                serverLibrary = library,
                options = DesktopAppSettings().toLocalProxyXrayConfigOptions(),
            ),
        ).jsonObject
    }

    private fun JsonObject.dnsServers() = getValue("dns").jsonObject.getValue("servers").jsonArray
    private fun JsonObject.inboundTagRules() = getValue("routing").jsonObject.getValue("rules").jsonArray
        .map { it.jsonObject }
        .filter { it["inboundTag"] != null }
        .associate { it.getValue("inboundTag").jsonArray.single().jsonPrimitive.content to it.getValue("outboundTag").jsonPrimitive.content }

    @Test
    fun generatedConfigUsesAndroidDefaultDnsAndBootstrap() {
        val root = generated()
        val dns = root.getValue("dns").jsonObject

        assertEquals("UseIPv4", dns.getValue("queryStrategy").jsonPrimitive.content)
        assertEquals("dns-proxy", dns.getValue("tag").jsonPrimitive.content)
        val servers = root.dnsServers()
        assertTrue(servers.any { it.toString() == "\"https://8.8.8.8/dns-query\"" })
        val direct = servers.filter { it is JsonObject && it.jsonObject["tag"]?.jsonPrimitive?.content == "dns-direct" }
        assertEquals(
            setOf("https://1.1.1.1/dns-query", "https://8.8.8.8/dns-query"),
            direct.map { it.jsonObject.getValue("address").jsonPrimitive.content }.toSet(),
        )
        assertEquals(
            "domain:proxy.example",
            direct.first().jsonObject.getValue("domains").jsonArray.single().jsonPrimitive.content,
        )
        assertTrue(dns.getValue("hosts").jsonObject.containsKey("dns.google"))
    }

    @Test
    fun generatedConfigRoutesDnsUpstreamQueries() {
        val rules = generated().inboundTagRules()

        assertEquals("direct", rules["dns-direct"])
        assertEquals("proxy", rules["dns-proxy"])
    }

    @Test
    fun profileSkipiDnsSettingsReachTheDnsSection() {
        val root = profile(
            """
            [SKIPI]
            proxy-dns = https://9.9.9.9/dns-query
            direct-dns = https://223.5.5.5/dns-query
            direct-dns-domains = domain:corp.example
            dns-hosts = my.example:203.0.113.5

            [Rule]
            FINAL,PROXY
            """.trimIndent() + "\n",
        )

        val servers = root.dnsServers()
        assertTrue(servers.any { it.toString() == "\"https://9.9.9.9/dns-query\"" })
        val direct = servers.first { it is JsonObject && it.jsonObject["address"]?.jsonPrimitive?.content == "https://223.5.5.5/dns-query" }
        assertTrue(direct.jsonObject.getValue("domains").jsonArray.any { it.jsonPrimitive.content == "domain:corp.example" })
        assertEquals(
            "203.0.113.5",
            root.getValue("dns").jsonObject.getValue("hosts").jsonObject.getValue("my.example").jsonPrimitive.content,
        )
        assertEquals("direct", root.inboundTagRules()["dns-direct"])
    }

    @Test
    fun profileFakeDnsIsNotAppliedWithoutLocalDns() {
        val root = profile("[SKIPI]\nfake-dns = true\n\n[Rule]\nFINAL,PROXY\n")

        assertFalse(root.containsKey("fakeDns"))
        assertFalse(root.dnsServers().any { it.toString() == "\"fakedns\"" })
    }
}
