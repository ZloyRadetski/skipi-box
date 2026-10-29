// Copyright 2026, Radetski
// SPDX-License-Identifier: GPL-3.0

package app.skipi.desktop

import features.proxy.server.model.Custom
import features.proxy.server.model.Socks
import features.proxy.server.model.encodePersistedProxyServer
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** "Copy full JSON" on Desktop is the shared standalone export, not the runtime config. */
class DesktopFullJsonExportTest {
    private fun library(server: features.proxy.server.model.ProxyServer<*>) = DesktopServerLibrary(
        selectedServerId = 1,
        servers = listOf(DesktopStoredProxyServer(1, server.encodePersistedProxyServer())),
    )

    @Test
    fun exportIsTheStandaloneDocumentWithLoopbackInboundsAndPrivateDirectRule() {
        val settings = DesktopAppSettings(localProxyPort = 20_480, localProxyPortRaw = "20480")
        val json = buildDesktopFullJsonExport(
            serverLibrary = library(Socks(remarks = "Proxy", server = "proxy.example", port = "1080")),
            settings = settings,
            trafficConfigContentById = emptyMap(),
            latencyByServerId = emptyMap(),
        ).getOrThrow()

        val root = Json.parseToJsonElement(json).jsonObject
        val inbounds = root.getValue("inbounds").jsonArray.map { it.jsonObject }
        assertEquals(listOf("socks", "http"), inbounds.map { it.getValue("tag").jsonPrimitive.content })
        assertEquals(20_480, inbounds[0].getValue("port").jsonPrimitive.content.toInt())
        assertEquals("warning", root.getValue("log").jsonObject.getValue("loglevel").jsonPrimitive.content)
        val rules = root.getValue("routing").jsonObject.getValue("rules").jsonArray.map { it.jsonObject }
        assertEquals("geoip:private", rules[0].getValue("ip").jsonArray.single().jsonPrimitive.content)
        assertEquals("proxy", rules[1].getValue("outboundTag").jsonPrimitive.content)
    }

    @Test
    fun exportIgnoresTheActiveProfileAndLocalProxyAuthentication() {
        val settings = DesktopAppSettings(enableLocalProxyAuth = true, localProxyUsername = "u", localProxyPassword = "p")
        val json = buildDesktopFullJsonExport(
            serverLibrary = library(Socks(remarks = "Proxy", server = "proxy.example", port = "1080")),
            settings = settings,
            trafficConfigContentById = mapOf(1 to "[Rule]\nDOMAIN,example.com,DIRECT\nFINAL,REJECT\n"),
            latencyByServerId = emptyMap(),
        ).getOrThrow()

        assertTrue(!json.contains("example.com"))
        assertTrue(!json.contains("\"pass\""))
    }

    @Test
    fun globalMuxIsAppliedLikeOnAndroid() {
        val json = buildDesktopFullJsonExport(
            serverLibrary = library(Socks(remarks = "Proxy", server = "proxy.example", port = "1080")),
            settings = DesktopAppSettings(enableMux = true, muxConcurrency = "7"),
            trafficConfigContentById = emptyMap(),
            latencyByServerId = emptyMap(),
        ).getOrThrow()

        assertTrue(json.contains("\"mux\""))
    }

    @Test
    fun rawCustomServerIsExportedAsWritten() {
        val custom = Custom(
            remarks = "Raw",
            configJson = """{"inbounds": [{"tag": "mine"}], "outbounds": [{"tag": "o", "protocol": "freedom"}]}""",
            overrideInboundAndDns = false,
        )

        val root = Json.parseToJsonElement(
            buildDesktopFullJsonExport(library(custom), DesktopAppSettings(), emptyMap(), emptyMap()).getOrThrow(),
        ).jsonObject

        assertEquals("mine", root.getValue("inbounds").jsonArray.single().jsonObject.getValue("tag").jsonPrimitive.content)
    }

    @Test
    fun missingSelectionIsAnError() {
        val result = buildDesktopFullJsonExport(DesktopServerLibrary(), DesktopAppSettings(), emptyMap(), emptyMap())

        assertTrue(result.isFailure)
    }
}
