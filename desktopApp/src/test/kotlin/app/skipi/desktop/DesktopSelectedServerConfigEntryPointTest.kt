// Copyright 2026, Radetski
// SPDX-License-Identifier: GPL-3.0
package app.skipi.desktop

import features.config.ConfigProfileLibrary
import features.proxy.server.model.ChainProxy
import features.proxy.server.model.Custom
import features.proxy.server.model.Socks
import features.proxy.server.model.encodePersistedProxyServer
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue
import platform.LocalProxyXrayConfigOptions

/** Pins the active-profile, raw-custom, and generated selected-server branches. */
class DesktopSelectedServerConfigEntryPointTest {
    @Test
    fun selectedActiveConfStillOwnsItsRulesEvenWhenAStoredServerIsSelected() {
        val server = Socks(remarks = "Stored selection", server = "stored.example", port = "1080")
        val library = DesktopServerLibrary(
            selectedServerId = 1,
            servers = listOf(
                DesktopStoredProxyServer(1, server.encodePersistedProxyServer()),
            ),
        )
        val profile = DesktopStoredConfig(
            id = 7,
            name = "Active routing",
            content = "[Rule]\nDOMAIN,only-in-conf.example,DIRECT\nFINAL,BLOCK\n",
        )
        val configLibrary = ConfigProfileLibrary(selectedConfigId = 7, configs = listOf(profile))

        val result = buildDesktopSelectedServerConfig(
            serverLibrary = library,
            configLibrary = configLibrary,
            options = LocalProxyXrayConfigOptions(socksPort = 19_081),
            trafficConfigContentById = mapOf(7 to profile.content),
            subscriptionPingUrl = "http://127.0.0.1:18086/probe",
            latencyByServerId = emptyMap(),
        )

        val root = Json.parseToJsonElement(assertNotNull(result).getOrThrow()).jsonObject
        val rules = root.getValue("routing").jsonObject.getValue("rules").jsonArray.map { it.jsonObject }
        assertTrue(rules.any { rule ->
            rule["domain"]?.jsonArray?.any { it.jsonPrimitive.content == "full:only-in-conf.example" } == true &&
                rule["outboundTag"]?.jsonPrimitive?.content == "direct"
        })
    }

    @Test
    fun selectedRawCustomStillUsesItsRawConfigAndDedicatedDesktopAdapter() {
        val custom = Custom(
            remarks = "Raw custom",
            configJson = """
                {
                  "outbounds": [
                    { "tag": "raw-custom-out", "protocol": "freedom" }
                  ],
                  "routing": {
                    "rules": [
                      { "type": "field", "domain": ["raw-only.example"], "outboundTag": "raw-custom-out" }
                    ]
                  }
                }
            """.trimIndent(),
        )
        val library = DesktopServerLibrary(
            selectedServerId = 8,
            servers = listOf(
                DesktopStoredProxyServer(8, custom.encodePersistedProxyServer()),
            ),
        )

        val result = buildDesktopSelectedServerConfig(
            serverLibrary = library,
            configLibrary = ConfigProfileLibrary(),
            options = LocalProxyXrayConfigOptions(socksPort = 19_082),
            trafficConfigContentById = emptyMap(),
            subscriptionPingUrl = "http://127.0.0.1:18086/probe",
            latencyByServerId = emptyMap(),
        )

        val root = Json.parseToJsonElement(assertNotNull(result).getOrThrow()).jsonObject
        assertEquals("raw-custom-out", root.getValue("outbounds").jsonArray.single().jsonObject
            .getValue("tag").jsonPrimitive.content)
        assertEquals("raw-only.example", root.getValue("routing").jsonObject
            .getValue("rules").jsonArray.single().jsonObject.getValue("domain").jsonArray.single().jsonPrimitive.content)
        assertEquals("skipi-socks", root.getValue("inbounds").jsonArray.single().jsonObject
            .getValue("tag").jsonPrimitive.content)
    }

    @Test
    fun selectedCompositeWithoutActiveProfileUsesGeneratedPlan() {
        val library = DesktopServerLibrary(
            selectedServerId = 9,
            servers = listOf(
                DesktopStoredProxyServer(
                    id = 1,
                    serverJson = Socks(remarks = "First hop", server = "first.example", port = "1080")
                        .encodePersistedProxyServer(),
                ),
                DesktopStoredProxyServer(
                    id = 2,
                    serverJson = Socks(remarks = "Second hop", server = "second.example", port = "1080")
                        .encodePersistedProxyServer(),
                ),
                DesktopStoredProxyServer(
                    id = 9,
                    serverJson = ChainProxy(remarks = "Selected chain", proxyServerIds = listOf(1, 2))
                        .encodePersistedProxyServer(),
                ),
            ),
        )

        val result = buildDesktopSelectedServerConfig(
            serverLibrary = library,
            configLibrary = ConfigProfileLibrary(),
            options = LocalProxyXrayConfigOptions(socksPort = 19_083),
            trafficConfigContentById = emptyMap(),
            subscriptionPingUrl = "http://127.0.0.1:18086/probe",
            latencyByServerId = emptyMap(),
        )

        val root = Json.parseToJsonElement(assertNotNull(result).getOrThrow()).jsonObject
        val outbounds = root.getValue("outbounds").jsonArray.map { it.jsonObject }
        assertEquals(listOf("proxy", "proxy-chain-1", "direct", "block"), outbounds.map {
            it.getValue("tag").jsonPrimitive.content
        })
        assertEquals(
            "second.example",
            outbounds.first().getValue("settings").jsonObject.getValue("address").jsonPrimitive.content,
        )
    }
}
