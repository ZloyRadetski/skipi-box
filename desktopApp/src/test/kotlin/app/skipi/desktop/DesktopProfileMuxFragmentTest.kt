// Copyright 2026, Radetski
// SPDX-License-Identifier: GPL-3.0

package app.skipi.desktop

import engine.xray.XrayMuxOutboundOptions
import features.proxy.server.model.ProxyServer
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import platform.LocalProxyXrayConfigOptions
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse

/** Active profile [SKIPI] Mux/Fragment values replace the global ones, as Android's withActiveTrafficConfigApplied does. */
class DesktopProfileMuxFragmentTest {
    private val server = ProxyServer.parse(
        "vless://8b4a2b20-c533-4d13-a3e0-bb0a8d7eb9c6@alpha.example:443#Alpha",
    )
    private val library = DesktopServerLibraries.select(
        DesktopServerLibraries.add(DesktopServerLibrary(), server),
        serverId = 1,
    )

    private fun build(content: String, options: LocalProxyXrayConfigOptions = LocalProxyXrayConfigOptions()): JsonObject =
        Json.parseToJsonElement(
            DesktopTrafficProfileXrayConfigFactory.build(
                profile = DesktopStoredConfig(id = 1, name = "P", content = content),
                serverLibrary = library,
                options = options,
            ),
        ).jsonObject

    @Test
    fun profileSkipiMuxAndFragmentAreAppliedToProxyOutbounds() {
        val root = build(
            """
            [General]
            ipv6 = true

            [SKIPI]
            mux = true
            mux-concurrency = 7
            mux-xudp-concurrency = 2
            mux-udp-443 = 1
            fragment = true
            fragment-packets = tlshello
            fragment-length = 5-10
            fragment-interval = 2-3

            [Rule]
            FINAL,PROXY
            """.trimIndent() + "\n",
        )

        val outbounds = root.getValue("outbounds").jsonArray.map { it.jsonObject }
        val proxy = outbounds.first { it["mux"] != null }
        assertEquals(
            Json.parseToJsonElement("""{"enabled": true, "concurrency": 7, "xudpConcurrency": 2, "xudpProxyUDP443": "allow"}"""),
            proxy.getValue("mux"),
        )
        assertEquals(
            "fragment",
            proxy.getValue("streamSettings").jsonObject.getValue("sockopt").jsonObject
                .getValue("dialerProxy").jsonPrimitive.content,
        )
        val fragment = outbounds.single { it["tag"]?.jsonPrimitive?.content == "fragment" }
        assertEquals("UseIP", fragment.getValue("settings").jsonObject.getValue("domainStrategy").jsonPrimitive.content)
        assertEquals(
            Json.parseToJsonElement("""{"packets": "tlshello", "length": "5-10", "interval": "2-3"}"""),
            fragment.getValue("settings").jsonObject.getValue("fragment"),
        )
    }

    @Test
    fun profileValuesReplaceEnabledGlobalSettings() {
        val root = build(
            "[Rule]\nFINAL,PROXY\n",
            LocalProxyXrayConfigOptions(
                mux = XrayMuxOutboundOptions("7", "2", 1),
            ),
        )

        assertFalse(root.toString().contains("\"mux\""))
        assertFalse(root.toString().contains("\"fragment\""))
    }
}
