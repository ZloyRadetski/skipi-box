// Copyright 2026, Radetski
// SPDX-License-Identifier: GPL-3.0

package engine.xray

import app.AppState
import app.ProxyServerState
import features.proxy.server.model.Custom
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * Source oracle for Desktop's raw Custom path: with "override inbound and DNS" Android replaces the
 * profile's DNS by the application's plan and keeps its proxy outbound; without it the profile's
 * own DNS stays.
 */
class CustomOverrideDnsAndroidOracleTest {
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
        val custom = ProxyServerState(
            id = 41,
            groupId = 0,
            server = Custom(remarks = "Raw", overrideInboundAndDns = override, configJson = raw),
        )
        val built = XrayConfigFactory.buildXrayConfigResult(
            XrayConfigRequest(
                appState = AppState(proxyServers = listOf(custom), selectedProxyServerId = custom.id),
                selectedServer = custom,
                inbounds = emptyList(),
                coreLogPaths = XrayCoreLogPaths(accessLogPath = "/tmp/access.log", errorLogPath = "/tmp/error.log"),
                collectTrafficStats = false,
            ),
        )
        return Json.parseToJsonElement(built.json).jsonObject
    }

    @Test
    fun overrideReplacesTheProfileDnsWithTheApplicationPlanAndKeepsItsProxyOutbound() {
        val root = build(override = true)

        assertEquals("dns-proxy", root.getValue("dns").jsonObject.getValue("tag").jsonPrimitive.content)
        assertFalse(root.getValue("dns").jsonObject.getValue("servers").jsonArray.toString() == """["8.8.4.4"]""")
        val tags = root.getValue("outbounds").jsonArray.map { it.jsonObject.getValue("tag").jsonPrimitive.content }
        assertTrue("my-proxy" in tags)
        assertEquals(1, tags.count { it == "direct" })
    }

    @Test
    fun withoutOverrideTheProfileKeepsItsOwnDns() {
        val root = build(override = false)

        assertEquals("""["8.8.4.4"]""", root.getValue("dns").jsonObject.getValue("servers").jsonArray.toString())
        assertFalse("tag" in root.getValue("dns").jsonObject)
    }
}
