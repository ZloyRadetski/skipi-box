// Copyright 2026, Radetski
// SPDX-License-Identifier: GPL-3.0

package features.proxy.server.model

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs

class ProxyServerPortableModelTest {
    @Test
    fun vlessUrlParsesAndGeneratesPortableXrayOutbound() {
        val server = assertIs<VLESS>(
            ProxyServer.parse(
                "vless://8b4a2b20-c533-4d13-a3e0-bb0a8d7eb9c6@example.com:443" +
                    "?type=ws&security=tls&path=%2Fedge&host=cdn.example.com#SKIPI",
            ),
        )

        assertEquals("SKIPI", server.remarks)
        assertEquals("example.com", server.server)
        assertEquals("443", server.port)
        assertEquals("websocket", server.parms.type)

        val outbound = server.toXrayOutbound("desktop-proxy")
        assertEquals("desktop-proxy", outbound.tag)
        assertEquals("vless", outbound.protocol)
        assertEquals("websocket", outbound.streamSettings?.get("network")?.toString()?.removeSurrounding("\""))
    }
}
