// Copyright 2026, Radetski
// SPDX-License-Identifier: GPL-3.0

package app.skipi.desktop

import app.skipi.app.model.RoutingConfigRecord
import engine.xray.XrayFragmentOutboundOptions
import engine.xray.XrayMuxOutboundOptions
import features.config.ConfigProfileLibrary
import features.proxy.server.model.Socks
import features.proxy.server.model.encodePersistedProxyServer
import java.nio.file.Files
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import platform.LocalProxyXrayConfigOptions

/** Android's Mux and Fragment global preferences, applied to Desktop's generated selected-server config. */
class DesktopMuxFragmentSettingsTest {
    @Test
    fun defaultsMatchAndroidAppState() {
        val settings = DesktopAppSettings()

        assertFalse(settings.enableMux)
        assertEquals("8", settings.muxConcurrency)
        assertEquals("16", settings.muxXudpConcurrency)
        assertEquals(0, settings.muxXudpProxyUdp443)
        assertFalse(settings.enableFragment)
        assertEquals("tlshello", settings.fragmentPackets)
        assertEquals("100-200", settings.fragmentLength)
        assertEquals("10-20", settings.fragmentInterval)
    }

    @Test
    fun valuesSurviveSaveAndReload() {
        val directory = Files.createTempDirectory("skipi-mux-fragment-")
        try {
            val path = directory.resolve("settings.json")
            val settings = DesktopAppSettings(
                enableMux = true,
                muxConcurrency = "7",
                muxXudpConcurrency = "2",
                muxXudpProxyUdp443 = 1,
                enableFragment = true,
                fragmentPackets = "1-3",
                fragmentLength = "5-10",
                fragmentInterval = "2-3",
            )

            DesktopSettingsLibraries.save(path, settings).getOrThrow()
            val loaded = DesktopSettingsLibraries.load(path).getOrThrow()

            assertEquals(settings.enableMux, loaded.enableMux)
            assertEquals("7", loaded.muxConcurrency)
            assertEquals("2", loaded.muxXudpConcurrency)
            assertEquals(1, loaded.muxXudpProxyUdp443)
            assertEquals(true, loaded.enableFragment)
            assertEquals("1-3", loaded.fragmentPackets)
            assertEquals("5-10", loaded.fragmentLength)
            assertEquals("2-3", loaded.fragmentInterval)
        } finally {
            directory.toFile().deleteRecursively()
        }
    }

    @Test
    fun disabledSettingsProduceNoRuntimeOverlay() {
        val options = DesktopAppSettings(muxConcurrency = "7", fragmentLength = "5-10")
            .toLocalProxyXrayConfigOptions()

        assertNull(options.mux)
        assertNull(options.fragment)
    }

    @Test
    fun enabledSettingsProduceRuntimeOverlayWithIpv4DirectStrategy() {
        val options = DesktopAppSettings(
            enableMux = true,
            muxConcurrency = "7",
            muxXudpConcurrency = "2",
            muxXudpProxyUdp443 = 1,
            enableFragment = true,
            fragmentPackets = "tlshello",
            fragmentLength = "5-10",
            fragmentInterval = "2-3",
        ).toLocalProxyXrayConfigOptions()

        assertEquals(XrayMuxOutboundOptions("7", "2", 1), options.mux)
        assertEquals(XrayFragmentOutboundOptions("UseIPv4", "tlshello", "5-10", "2-3"), options.fragment)
    }

    @Test
    fun generatedSelectedServerConfigCarriesMuxAndFragmentLikeAndroid() {
        val server = Socks(remarks = "Proxy", server = "proxy.example", port = "1080")
        val library = DesktopServerLibrary(
            selectedServerId = 1,
            servers = listOf(DesktopStoredProxyServer(1, server.encodePersistedProxyServer())),
        )

        val result = buildDesktopSelectedServerConfig(
            serverLibrary = library,
            configLibrary = ConfigProfileLibrary(),
            routing = RoutingConfigRecord(),
            options = DesktopAppSettings(
                enableMux = true,
                muxConcurrency = "7",
                muxXudpConcurrency = "2",
                muxXudpProxyUdp443 = 1,
                enableFragment = true,
                fragmentLength = "5-10",
                fragmentInterval = "2-3",
            ).toLocalProxyXrayConfigOptions().copy(socksPort = 19_090),
            trafficConfigContentById = emptyMap(),
            subscriptionPingUrl = "http://127.0.0.1:18086/probe",
            latencyByServerId = emptyMap(),
        )

        val outbounds = Json.parseToJsonElement(assertNotNull(result).getOrThrow())
            .jsonObject.getValue("outbounds").jsonArray.map(kotlinx.serialization.json.JsonElement::jsonObject)
        val proxy = outbounds.first { it.tag() == "skipi-proxy" || it.tag() == "proxy" }
        assertEquals(
            Json.parseToJsonElement(
                """{"enabled": true, "concurrency": 7, "xudpConcurrency": 2, "xudpProxyUDP443": "allow"}""",
            ),
            proxy.getValue("mux"),
        )
        assertEquals(
            "fragment",
            proxy.getValue("streamSettings").jsonObject.getValue("sockopt").jsonObject
                .getValue("dialerProxy").jsonPrimitive.content,
        )
        val fragment = outbounds.first { it.tag() == "fragment" }
        assertEquals(
            Json.parseToJsonElement("""{"packets": "tlshello", "length": "5-10", "interval": "2-3"}"""),
            fragment.getValue("settings").jsonObject.getValue("fragment"),
        )
    }

    @Test
    fun generatedSelectedServerConfigHasNoOverlayWhenDisabled() {
        val server = Socks(remarks = "Proxy", server = "proxy.example", port = "1080")
        val library = DesktopServerLibrary(
            selectedServerId = 1,
            servers = listOf(DesktopStoredProxyServer(1, server.encodePersistedProxyServer())),
        )

        val result = buildDesktopSelectedServerConfig(
            serverLibrary = library,
            configLibrary = ConfigProfileLibrary(),
            routing = RoutingConfigRecord(),
            options = LocalProxyXrayConfigOptions(socksPort = 19_091),
            trafficConfigContentById = emptyMap(),
            subscriptionPingUrl = "http://127.0.0.1:18086/probe",
            latencyByServerId = emptyMap(),
        )

        val text = assertNotNull(result).getOrThrow()
        assertFalse(text.contains("\"mux\""))
        assertFalse(text.contains("\"fragment\""))
    }

    private fun JsonObject.tag(): String? = get("tag")?.jsonPrimitive?.content
}
