// Copyright 2026, Radetski
// SPDX-License-Identifier: GPL-3.0

package features.proxy.server.model

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class ProxyServerEndpointTest {
    @Test
    fun resolves_and_normalizes_direct_proxy_endpoints() {
        val server = HTTP(server = "  edge.example  ", port = "443")

        assertEquals(
            ProxyServerEndpoint(host = "edge.example", port = 443),
            server.connectionEndpointOrNull(),
        )
    }

    @Test
    fun resolves_special_and_custom_proxy_endpoints() {
        val olcRtc = OlcRtc(
            provider = "telemost",
            roomUrl = "https://telemost.yandex.ru/j/1234567890",
        )
        val custom = Custom(
            configJson = """
                {"outbounds":[{"protocol":"socks","settings":{"servers":[{"address":"custom.example","port":8443}]}}]}
            """.trimIndent(),
        )

        assertEquals(ProxyServerEndpoint("telemost.yandex.ru", 443), olcRtc.connectionEndpointOrNull())
        assertEquals(ProxyServerEndpoint("custom.example", 8443), custom.connectionEndpointOrNull())
    }

    @Test
    fun rejects_empty_or_invalid_direct_endpoints() {
        assertNull(HTTP(server = "", port = "443").connectionEndpointOrNull())
        assertNull(HTTP(server = "edge.example", port = "0").connectionEndpointOrNull())
        assertNull(HTTP(server = "edge.example", port = "65536").connectionEndpointOrNull())
    }
}
