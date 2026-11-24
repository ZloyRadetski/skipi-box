// Copyright 2026, Radetski
// SPDX-License-Identifier: GPL-3.0

package app.skipi.desktop

import app.skipi.app.model.RoutingConfigRecord
import features.proxy.server.model.Custom
import features.proxy.server.model.ProxyServer
import features.routing.model.RouteRule
import features.proxy.server.model.encodePersistedProxyServer
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import platform.LocalProxyXrayConfigOptions
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull

/**
 * Same raw entry, profile and expectations as Android's `CustomOverrideProfileOracleTest`: the active
 * profile's DNS, hosts and IPv6 reach a Custom entry that overrides inbound and DNS.
 */
class DesktopCustomOverrideProfileTest {
    private val raw = """
        {
          "dns": {"servers": ["8.8.4.4"]},
          "outbounds": [
            {"tag": "my-proxy", "protocol": "vless", "settings": {"vnext": [{"address": "edge.example", "port": 443,
              "users": [{"id": "00000000-0000-0000-0000-000000000000", "encryption": "none"}]}]}}
          ]
        }
    """.trimIndent()

    private fun build(
        profile: String?,
        rawJson: String = raw,
        otherServers: List<DesktopStoredProxyServer> = emptyList(),
        routing: RoutingConfigRecord = RoutingConfigRecord(),
    ): JsonObject {
        val library = DesktopServerLibrary(
            selectedServerId = 41,
            servers = listOf(
                DesktopStoredProxyServer(
                    id = 41,
                    serverJson = Custom(remarks = "Raw", overrideInboundAndDns = true, configJson = rawJson)
                        .encodePersistedProxyServer(),
                ),
            ) + otherServers,
        )
        val configs = if (profile == null) {
            DesktopConfigLibrary()
        } else {
            DesktopConfigLibrary(
                selectedConfigId = 7,
                configs = listOf(DesktopStoredConfig(id = 7, name = "P", content = profile)),
            )
        }
        val result = buildDesktopSelectedServerConfig(
            serverLibrary = library,
            configLibrary = configs,
            routing = routing,
            options = LocalProxyXrayConfigOptions(socksPort = 20_480),
            trafficConfigContentById = emptyMap(),
            subscriptionPingUrl = "",
            latencyByServerId = emptyMap(),
        )
        return Json.parseToJsonElement(assertNotNull(result).getOrThrow()).jsonObject
    }

    private fun JsonObject.directStrategy(): String = getValue("outbounds").jsonArray
        .map { it.jsonObject }
        .first { it.getValue("tag").jsonPrimitive.content == "direct" }
        .getValue("settings").jsonObject.getValue("domainStrategy").jsonPrimitive.content

    @Test
    fun aProfileRuleDesktopCannotServeIsReportedForARawServerToo() {
        val library = DesktopServerLibrary(
            selectedServerId = 41,
            servers = listOf(
                DesktopStoredProxyServer(
                    id = 41,
                    serverJson = Custom(remarks = "Raw", overrideInboundAndDns = true, configJson = raw)
                        .encodePersistedProxyServer(),
                ),
            ),
        )
        val configs = DesktopConfigLibrary(
            selectedConfigId = 7,
            configs = listOf(
                DesktopStoredConfig(id = 7, name = "P", content = "[Rule]\nIP-ASN,13335,PROXY\nFINAL,PROXY\n"),
            ),
        )
        val generated = assertNotNull(
            buildDesktopSelectedServerConfigWithBridge(
                serverLibrary = library,
                configLibrary = configs,
                routing = RoutingConfigRecord(),
                options = LocalProxyXrayConfigOptions(socksPort = 20_480),
                trafficConfigContentById = emptyMap(),
                subscriptionPingUrl = "",
                latencyByServerId = emptyMap(),
            ),
        ).getOrThrow()

        assertEquals(listOf("geoip:as13335"), generated.unappliedRules)
        assertEquals(false, "geoip:as13335" in generated.json)
    }

    @Test
    fun theActiveProfilesDnsHostsAndIpv6ReachTheOverriddenCustomConfig() {
        val root = build(PROFILE)
        val dns = root.getValue("dns").jsonObject

        assertEquals("UseIP", dns.getValue("queryStrategy").jsonPrimitive.content)
        assertEquals(
            listOf("203.0.113.9", "203.0.113.10"),
            dns.getValue("hosts").jsonObject.getValue("profile.example").jsonArray.map { it.jsonPrimitive.content },
        )
        assertEquals(
            listOf("1.1.1.1"),
            dns.getValue("servers").jsonArray.filterIsInstance<JsonPrimitive>().map { it.content },
        )
        assertEquals("UseIP", root.directStrategy())
    }

    @Test
    fun withoutAProfileTheApplicationDefaultsStayIpv4() {
        val root = build(null)

        assertEquals("UseIPv4", root.getValue("dns").jsonObject.getValue("queryStrategy").jsonPrimitive.content)
        assertEquals("UseIPv4", root.directStrategy())
    }

    @Test
    fun theActiveProfilesRulesReplaceTheRoutingOfTheOverriddenCustomConfig() {
        val root = build(ROUTING_PROFILE, rawJson = RAW_WITH_OWN_ROUTING)
        val routing = root.getValue("routing").jsonObject
        val rules = routing.getValue("rules").jsonArray.map { it.jsonObject }

        // The rules written in the raw JSON are gone; the profile's decide.
        assertEquals(null, rules.firstOrNull { rule -> rule["domain"]?.jsonArray?.any { it.jsonPrimitive.content == "domain:ru" } == true })
        val discord = rules.first { rule -> rule["domain"]?.jsonArray?.any { it.jsonPrimitive.content == "geosite:discord" } == true }
        assertEquals("direct", discord.getValue("outboundTag").jsonPrimitive.content)
        val ads = rules.first { rule -> rule["domain"]?.jsonArray?.any { it.jsonPrimitive.content == "geosite:category-ads-all" } == true }
        assertEquals("block", ads.getValue("outboundTag").jsonPrimitive.content)
        // FINAL,PROXY goes to the raw server's own proxy outbound.
        assertEquals("my-proxy", rules.last().getValue("outboundTag").jsonPrimitive.content)
        assertEquals("AsIs", routing.getValue("domainStrategy").jsonPrimitive.content)
        val tags = root.getValue("outbounds").jsonArray.map { it.jsonObject.getValue("tag").jsonPrimitive.content }
        assertEquals(1, tags.count { it == "block" })
    }

    @Test
    fun aProfileGroupAndAnotherServerBecomeRealOutboundsOfTheRawConfig() {
        fun vless(id: Int, host: String, name: String) = DesktopStoredProxyServer(
            id = id,
            serverJson = ProxyServer.parse("vless://8b4a2b20-c533-4d13-a3e0-bb0a8d7eb9c6@$host:443#$name")
                .encodePersistedProxyServer(),
        )
        val root = build(
            GROUP_PROFILE,
            rawJson = RAW_WITH_OWN_ROUTING,
            otherServers = listOf(vless(1, "a.example", "Alpha"), vless(2, "b.example", "Beta")),
        )
        val routing = root.getValue("routing").jsonObject
        val rules = routing.getValue("rules").jsonArray.map { it.jsonObject }
        val tags = root.getValue("outbounds").jsonArray.map { it.jsonObject.getValue("tag").jsonPrimitive.content }

        // The raw server's own outbound stays the default (first); the group's members and Beta join it.
        assertEquals("my-proxy", tags.first())
        assertEquals(setOf("shadowrocket-group:Fast-policy-1", "shadowrocket-group:Fast-policy-2", "2"), tags.filter { it != "my-proxy" && it != "direct" && it != "block" }.toSet())
        assertEquals(listOf("shadowrocket-group:Fast"), routing.getValue("balancers").jsonArray.map { it.jsonObject.getValue("tag").jsonPrimitive.content })
        val google = rules.first { rule -> rule["domain"]?.jsonArray?.any { it.jsonPrimitive.content == "geosite:google" } == true }
        assertEquals("shadowrocket-group:Fast", google.getValue("balancerTag").jsonPrimitive.content)
        val steam = rules.first { rule -> rule["domain"]?.jsonArray?.any { it.jsonPrimitive.content == "geosite:steam" } == true }
        assertEquals("2", steam.getValue("outboundTag").jsonPrimitive.content)
        // The raw server itself, named by its remarks, and PROXY keep going to its own outbound.
        val raw = rules.first { rule -> rule["domain"]?.jsonArray?.any { it.jsonPrimitive.content == "geosite:claude" } == true }
        assertEquals("my-proxy", raw.getValue("outboundTag").jsonPrimitive.content)
        assertEquals("my-proxy", rules.last().getValue("outboundTag").jsonPrimitive.content)
        assertEquals(
            listOf("shadowrocket-group:Fast-policy-"),
            root.getValue("observatory").jsonObject.getValue("subjectSelector").jsonArray.map { it.jsonPrimitive.content },
        )
    }

    @Test
    fun aGroupOfRawJsonServersBecomesABalancerOfTheirOwnOutbounds() {
        fun rawServer(id: Int, host: String, name: String) = DesktopStoredProxyServer(
            id = id,
            serverJson = Custom(remarks = name, overrideInboundAndDns = true, configJson = raw.replace("edge.example", host))
                .encodePersistedProxyServer(),
        )
        val root = build(
            GROUP_PROFILE,
            rawJson = RAW_WITH_OWN_ROUTING,
            otherServers = listOf(rawServer(1, "a.example", "Alpha"), rawServer(2, "b.example", "Beta")),
        )
        val routing = root.getValue("routing").jsonObject
        val outbounds = root.getValue("outbounds").jsonArray.map { it.jsonObject }
        val rules = routing.getValue("rules").jsonArray.map { it.jsonObject }

        assertEquals(listOf("shadowrocket-group:Fast"), routing.getValue("balancers").jsonArray.map { it.jsonObject.getValue("tag").jsonPrimitive.content })
        val alpha = outbounds.first { it.getValue("tag").jsonPrimitive.content == "shadowrocket-group:Fast-policy-1" }
        assertEquals("vless", alpha.getValue("protocol").jsonPrimitive.content)
        assertEquals(
            "a.example",
            alpha.getValue("settings").jsonObject.getValue("vnext").jsonArray.first().jsonObject.getValue("address").jsonPrimitive.content,
        )
        val google = rules.first { rule -> rule["domain"]?.jsonArray?.any { it.jsonPrimitive.content == "geosite:google" } == true }
        assertEquals("shadowrocket-group:Fast", google.getValue("balancerTag").jsonPrimitive.content)
    }

    @Test
    fun globalRulesMayNameAnotherServerOfTheLibrary() {
        val beta = DesktopStoredProxyServer(
            id = 2,
            serverJson = ProxyServer.parse("vless://8b4a2b20-c533-4d13-a3e0-bb0a8d7eb9c6@b.example:443#Beta")
                .encodePersistedProxyServer(),
        )
        val root = build(
            profile = null,
            rawJson = RAW_WITH_OWN_ROUTING,
            otherServers = listOf(beta),
            routing = RoutingConfigRecord(
                rules = listOf(RouteRule(id = 1, domain = listOf("geosite:steam"), outboundTag = "2")),
            ),
        )
        val rules = root.getValue("routing").jsonObject.getValue("rules").jsonArray.map { it.jsonObject }
        val tags = root.getValue("outbounds").jsonArray.map { it.jsonObject.getValue("tag").jsonPrimitive.content }

        assertEquals("my-proxy", tags.first())
        assertEquals(true, "2" in tags)
        assertEquals("2", rules.first { rule -> rule["domain"] != null }.getValue("outboundTag").jsonPrimitive.content)
        assertEquals("my-proxy", rules.last().getValue("outboundTag").jsonPrimitive.content)
    }

    @Test
    fun withoutProfileRulesTheRawRoutingStaysWhenTheGlobalRoutingIsEmpty() {
        val root = build(null, rawJson = RAW_WITH_OWN_ROUTING)
        val rules = root.getValue("routing").jsonObject.getValue("rules").jsonArray.map { it.jsonObject }
        assertEquals(true, rules.any { rule -> rule["domain"]?.jsonArray?.any { it.jsonPrimitive.content == "domain:ru" } == true })
    }

    private companion object {
        val RAW_WITH_OWN_ROUTING = """
            {
              "outbounds": [
                {"tag": "my-proxy", "protocol": "vless", "settings": {"vnext": [{"address": "edge.example", "port": 443,
                  "users": [{"id": "00000000-0000-0000-0000-000000000000", "encryption": "none"}]}]}},
                {"tag": "direct", "protocol": "freedom"}
              ],
              "routing": {"domainStrategy": "IPIfNonMatch", "rules": [
                {"domain": ["domain:ru"], "outboundTag": "direct"}
              ]}
            }
        """.trimIndent()

        val GROUP_PROFILE = """
            [Proxy Group]
            Fast = url-test, Alpha, Beta, url=https://example.com/ping, interval=60

            [Rule]
            DOMAIN-SET,geosite:google,Fast
            DOMAIN-SET,geosite:steam,Beta
            DOMAIN-SET,geosite:claude,Raw
            FINAL,PROXY
        """.trimIndent()

        val ROUTING_PROFILE = """
            [Rule]
            DOMAIN-SET,geosite:category-ads-all,REJECT
            DOMAIN-SET,geosite:discord,DIRECT
            FINAL,PROXY

            [SKIPI]
            route-domain-strategy = AsIs
        """.trimIndent() + "\n"

        val PROFILE = """
            [General]
            dns-server = 1.1.1.1
            ipv6 = true

            [Host]
            profile.example = 203.0.113.9, 203.0.113.10

            [Rule]
            FINAL,PROXY
        """.trimIndent() + "\n"
    }
}
