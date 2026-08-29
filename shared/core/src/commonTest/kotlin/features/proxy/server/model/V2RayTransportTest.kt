// Copyright 2026, Radetski
// SPDX-License-Identifier: GPL-3.0

package features.proxy.server.model

import kotlin.test.Test
import kotlin.test.assertEquals

class V2RayTransportTest {
    @Test
    fun normalizesAliasesForPortableProfileParsing() {
        assertEquals(V2RayTransportRaw, "tcp".toCanonicalV2RayTransportType())
        assertEquals(V2RayTransportWebSocket, "ws".toCanonicalV2RayTransportType())
        assertEquals(V2RayTransportXhttp, "splithttp".toCanonicalV2RayTransportType())
        assertEquals("ws", V2RayTransportWebSocket.toV2RayTransportUrlType())
    }
}
