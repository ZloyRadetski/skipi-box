// Copyright 2026, Radetski
// SPDX-License-Identifier: GPL-3.0

package app.skipi.desktop

import app.skipi.app.model.RoutingConfigRecord
import features.proxy.server.model.Custom
import features.proxy.server.model.encodePersistedProxyServer
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import platform.LocalProxyXrayConfigOptions
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * Same raw profile and expectations as Android's `CustomOverrideDnsAndroidOracleTest`, through the
 * entry point the application uses to start Core. The local SOCKS listener stays Desktop's.
 */
class DesktopCustomOverrideDnsTest {
    private val raw = """
        {
          "dns": {"servers": ["8.8.4.4"]},
          "outbounds": [
            {"tag": "my-proxy", "protocol": "vless", "settings": {"vnext": [{"address": "edge.example", "port": 443,
              "users": [{"id": "00000000-0000-0000-0000-000000000000", "encryption": "none"}]}]}}
          ]
        }
    """.trimIndent()

    private fun build(override: Boolean): kotlinx.serialization.json.JsonObject {
        val library = DesktopServerLibrary(
            selectedServerId = 41,
            servers = listOf(
                DesktopStoredProxyServer(
                    id = 41,
                    serverJson = Custom(remarks = "Raw", overrideInboundAndDns = override, configJson = raw)
                        .encodePersistedProxyServer(),
                ),
            ),
        )
        val result = buildDesktopSelectedServerConfig(
            serverLibrary = library,
            configLibrary = DesktopConfigLibrary(),
            routing = RoutingConfigRecord(),
            options = LocalProxyXrayConfigOptions(socksPort = 20_480),
            trafficConfigContentById = emptyMap(),
            subscriptionPingUrl = "",
            latencyByServerId = emptyMap(),
        )
        return Json.parseToJsonElement(assertNotNull(result).getOrThrow()).jsonObject
    }

    @Test
    fun overrideReplacesTheProfileDnsWithTheApplicationPlanAndKeepsItsProxyOutbound() {
        val root = build(override = true)

        assertEquals("dns-proxy", root.getValue("dns").jsonObject.getValue("tag").jsonPrimitive.content)
        assertFalse(root.getValue("dns").jsonObject.getValue("servers").jsonArray.toString() == """["8.8.4.4"]""")
        val tags = root.getValue("outbounds").jsonArray.map { it.jsonObject.getValue("tag").jsonPrimitive.content }
        assertTrue("my-proxy" in tags)
        assertEquals(1, tags.count { it == "direct" })
        assertEquals("skipi-socks", root.getValue("inbounds").jsonArray.single().jsonObject.getValue("tag").jsonPrimitive.content)
    }

    @Test
    fun withoutOverrideTheProfileKeepsItsOwnDns() {
        val root = build(override = false)

        assertEquals("""["8.8.4.4"]""", root.getValue("dns").jsonObject.getValue("servers").jsonArray.toString())
        assertFalse("tag" in root.getValue("dns").jsonObject)
        assertEquals("skipi-socks", root.getValue("inbounds").jsonArray.single().jsonObject.getValue("tag").jsonPrimitive.content)
    }
}
