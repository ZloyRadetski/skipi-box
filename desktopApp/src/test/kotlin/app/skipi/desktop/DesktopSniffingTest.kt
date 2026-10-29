// Copyright 2026, Radetski
// SPDX-License-Identifier: GPL-3.0

package app.skipi.desktop

import app.skipi.app.model.RoutingConfigRecord
import features.config.ConfigProfileLibrary
import features.proxy.server.model.Custom
import features.proxy.server.model.ProxyServer
import features.proxy.server.model.Socks
import features.proxy.server.model.encodePersistedProxyServer
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull

/** Android's SOCKS inbound sniffing: enabled/routeOnly settings and the canonical destOverride list. */
class DesktopSniffingTest {
    private fun sniffing(json: String): JsonElement =
        Json.parseToJsonElement(json).jsonObject.getValue("inbounds").jsonArray.first().jsonObject.getValue("sniffing")

    private fun expected(enabled: Boolean, routeOnly: Boolean): JsonElement = Json.parseToJsonElement(
        """{"enabled": $enabled, "destOverride": ["http", "tls", "quic"], "routeOnly": $routeOnly}""",
    )

    private fun generated(settings: DesktopAppSettings): String {
        val server = Socks(remarks = "Proxy", server = "proxy.example", port = "1080")
        return assertNotNull(
            buildDesktopSelectedServerConfig(
                serverLibrary = DesktopServerLibrary(
                    selectedServerId = 1,
                    servers = listOf(DesktopStoredProxyServer(1, server.encodePersistedProxyServer())),
                ),
                configLibrary = ConfigProfileLibrary(),
                routing = RoutingConfigRecord(),
                options = settings.toLocalProxyXrayConfigOptions(),
                trafficConfigContentById = emptyMap(),
                subscriptionPingUrl = "http://127.0.0.1:18086/probe",
                latencyByServerId = emptyMap(),
            ),
        ).getOrThrow()
    }

    @Test
    fun defaultsMatchAndroid() {
        val settings = DesktopAppSettings()
        assertEquals(true, settings.enableSniffing)
        assertEquals(true, settings.enableSniffingRouteOnly)
    }

    @Test
    fun generatedConfigUsesAndroidSniffingDefaults() {
        assertEquals(expected(true, true), sniffing(generated(DesktopAppSettings())))
    }

    @Test
    fun generatedConfigFollowsGlobalSniffingSettings() {
        assertEquals(
            expected(false, false),
            sniffing(generated(DesktopAppSettings(enableSniffing = false, enableSniffingRouteOnly = false))),
        )
    }

    @Test
    fun activeProfileSniffingReplacesGlobalSettings() {
        val library = DesktopServerLibraries.select(
            DesktopServerLibraries.add(
                DesktopServerLibrary(),
                ProxyServer.parse("vless://8b4a2b20-c533-4d13-a3e0-bb0a8d7eb9c6@alpha.example:443#Alpha"),
            ),
            serverId = 1,
        )
        fun build(content: String) = DesktopTrafficProfileXrayConfigFactory.build(
            profile = DesktopStoredConfig(id = 1, name = "P", content = content),
            serverLibrary = library,
            options = DesktopAppSettings(enableSniffing = false).toLocalProxyXrayConfigOptions(),
        )

        assertEquals(expected(true, true), sniffing(build("[Rule]\nFINAL,PROXY\n")))
        assertEquals(
            expected(false, false),
            sniffing(build("[SKIPI]\nsniffing = false\nsniffing-route-only = false\n\n[Rule]\nFINAL,PROXY\n")),
        )
    }

    @Test
    fun rawCustomUsesCanonicalDestOverride() {
        val custom = Custom(
            remarks = "Raw",
            configJson = """{"outbounds": [{"tag": "o", "protocol": "freedom"}]}""",
        )
        val json = assertNotNull(
            buildDesktopSelectedServerConfig(
                serverLibrary = DesktopServerLibrary(
                    selectedServerId = 1,
                    servers = listOf(DesktopStoredProxyServer(1, custom.encodePersistedProxyServer())),
                ),
                configLibrary = ConfigProfileLibrary(),
                routing = RoutingConfigRecord(),
                options = DesktopAppSettings(enableSniffingRouteOnly = false).toLocalProxyXrayConfigOptions(),
                trafficConfigContentById = emptyMap(),
                subscriptionPingUrl = "http://127.0.0.1:18086/probe",
                latencyByServerId = emptyMap(),
            ),
        ).getOrThrow()

        assertEquals(expected(true, false), sniffing(json))
    }
}
