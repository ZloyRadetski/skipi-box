// Copyright 2026, Radetski
// SPDX-License-Identifier: GPL-3.0

package features.proxy.server.model

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class V2RayTransportTest {
    @Test
    fun normalizesAliasesForPortableProfileParsing() {
        assertEquals(V2RayTransportRaw, "tcp".toCanonicalV2RayTransportType())
        assertEquals(V2RayTransportWebSocket, "ws".toCanonicalV2RayTransportType())
        assertEquals(V2RayTransportXhttp, "splithttp".toCanonicalV2RayTransportType())
        assertEquals("ws", V2RayTransportWebSocket.toV2RayTransportUrlType())
        assertEquals(V2RayTransportXdrive, "xdrive".toCanonicalV2RayTransportType())
    }

    @Test
    fun xdriveIsOfferedOnlyWhereTheCoreAllowsIt() {
        assertTrue(v2RayTransportOptionsFor(allowXdrive = true).any { it.value == V2RayTransportXdrive })
        assertFalse(v2RayTransportOptionsFor(allowXdrive = false).any { it.value == V2RayTransportXdrive })
        // the rest of the list is the same either way
        assertEquals(
            v2RayTransportOptionsFor(allowXdrive = true).filterNot { it.value == V2RayTransportXdrive },
            v2RayTransportOptionsFor(allowXdrive = false),
        )
    }

    @Test
    fun theOptionIndexIsLookedUpInTheOptionsTheEditorShows() {
        val withoutXdrive = v2RayTransportOptionsFor(allowXdrive = false)

        // a VMess profile that carries xdrive falls back to the first option instead of an index that is out of range
        assertEquals(0, v2RayTransportOptionIndex(V2RayTransportXdrive, withoutXdrive))
        assertEquals(
            withoutXdrive.indexOfFirst { it.value == V2RayTransportGrpc },
            v2RayTransportOptionIndex(V2RayTransportGrpc, withoutXdrive),
        )
    }
}
