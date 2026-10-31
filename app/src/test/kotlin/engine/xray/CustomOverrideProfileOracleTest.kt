// Copyright 2026, Radetski
// SPDX-License-Identifier: GPL-3.0

package engine.xray

import app.AppState
import app.ProxyServerState
import app.withActiveTrafficConfigApplied
import features.config.TrafficConfigState
import features.config.withSkipiSettingsReadFromRawConfig
import features.proxy.server.model.Custom
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Test
import kotlin.test.assertEquals

/**
 * Source oracle for Desktop's raw Custom path with an active profile: Android applies the profile to
 * the application state before building, so the profile's DNS, hosts and IPv6 reach a Custom entry
 * that overrides inbound and DNS.
 */
class CustomOverrideProfileOracleTest {
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
        val custom = ProxyServerState(
            id = 41,
            groupId = 0,
            server = Custom(remarks = "Raw", overrideInboundAndDns = true, configJson = raw),
        )
        var state = AppState(proxyServers = listOf(custom), selectedProxyServerId = custom.id)
        if (profile != null) {
            state = state.copy(
                trafficConfigs = listOf(
                    TrafficConfigState(id = 7, name = "P", rawConfig = profile).withSkipiSettingsReadFromRawConfig(),
                ),
                activeTrafficConfigId = 7,
            ).withActiveTrafficConfigApplied()
        }
        val built = XrayConfigFactory.buildXrayConfigResult(
            XrayConfigRequest(
                appState = state,
                selectedServer = custom,
                inbounds = emptyList(),
                coreLogPaths = XrayCoreLogPaths(accessLogPath = "/tmp/access.log", errorLogPath = "/tmp/error.log"),
                collectTrafficStats = false,
            ),
        )
        return Json.parseToJsonElement(built.json).jsonObject
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
