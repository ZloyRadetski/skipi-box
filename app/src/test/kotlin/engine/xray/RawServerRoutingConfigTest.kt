// Copyright 2026, Radetski
// SPDX-License-Identifier: GPL-3.0

package engine.xray

import app.AppState
import app.ProxyServerState
import features.proxy.server.model.Custom
import features.proxy.server.model.VLESS
import features.routing.model.RouteRule
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import org.junit.Test

class RawServerRoutingConfigTest {
    private val raw = ProxyServerState(
        id = 1,
        groupId = 0,
        server = Custom(
            remarks = "Raw",
            configJson = """
                {"outbounds":[{"tag":"vpn","protocol":"vless","settings":{"vnext":[{"address":"p.example","port":443,"users":[{"id":"u","encryption":"none"}]}]}},
                              {"tag":"local","protocol":"freedom"}],
                 "routing":{"rules":[{"domain":["direct.example"],"outboundTag":"local"}]}}
            """.trimIndent(),
        ),
    )
    private val beta = ProxyServerState(
        id = 2,
        groupId = 0,
        server = VLESS(remarks = "Beta", id = "4219d973-8792-462f-8747-df766f70f137", server = "beta.example", port = "443"),
    )

    private fun build(appState: AppState) = Json.parseToJsonElement(
        XrayConfigFactory.buildXrayConfig(
            XrayConfigRequest(
                appState = appState,
                selectedServer = raw,
                inbounds = emptyList(),
                coreLogPaths = XrayCoreLogPaths("/tmp/access.log", "/tmp/error.log"),
            ),
        ),
    ).jsonObject

    @Test
    fun rulesNamingAnotherServerReachItAsARealOutbound() {
        val root = build(
            AppState(
                proxyServers = listOf(raw, beta),
                routeRules = listOf(RouteRule(id = 1, domain = listOf("domain:steam.example"), outboundTag = "2")),
            ),
        )
        val tags = root.getValue("outbounds").jsonArray.map { it.jsonObject.getValue("tag").jsonPrimitive.content }
        val rules = root.getValue("routing").jsonObject.getValue("rules").jsonArray.map { it.jsonObject }

        assertEquals("vpn", tags.first())
        assertTrue("2" in tags)
        assertEquals("2", rules.first { rule -> rule["domain"] != null }.getValue("outboundTag").jsonPrimitive.content)
        assertEquals("vpn", rules.last().getValue("outboundTag").jsonPrimitive.content)
    }

    @Test
    fun withoutRulesTheJsonKeepsItsOwnRouting() {
        val config = XrayConfigFactory.buildXrayConfig(
            XrayConfigRequest(
                appState = AppState(proxyServers = listOf(raw, beta)),
                selectedServer = raw,
                inbounds = emptyList(),
                coreLogPaths = XrayCoreLogPaths("/tmp/access.log", "/tmp/error.log"),
            ),
        )
        assertTrue(config.contains("direct.example"))
    }
}
