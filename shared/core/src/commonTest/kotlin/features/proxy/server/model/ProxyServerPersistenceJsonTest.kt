// Copyright 2026, Radetski
// SPDX-License-Identifier: GPL-3.0

package features.proxy.server.model

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs

class ProxyServerPersistenceJsonTest {
    @Test
    fun persistedServerRoundTripsItsProtocolAndFields() {
        val original = VLESS(
            remarks = "Desktop",
            id = "8b4a2b20-c533-4d13-a3e0-bb0a8d7eb9c6",
            server = "example.com",
            port = "443",
            parms = V2RayParameters(type = V2RayTransportWebSocket, security = "tls", path = "/edge"),
        )

        val restored = assertIs<VLESS>(original.encodePersistedProxyServer().decodePersistedProxyServer())

        assertEquals(original, restored)
    }
}
