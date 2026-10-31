// Copyright 2026, Radetski
// SPDX-License-Identifier: GPL-3.0

package app.skipi.desktop

import app.skipi.app.model.RoutingConfigRecord
import features.proxy.server.model.Custom
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

    private fun build(profile: String?): JsonObject {
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
            routing = RoutingConfigRecord(),
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

    private companion object {
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
