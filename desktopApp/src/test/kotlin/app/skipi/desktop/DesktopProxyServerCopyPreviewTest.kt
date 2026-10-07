// Copyright 2026, Radetski
// SPDX-License-Identifier: GPL-3.0

package app.skipi.desktop

import app.skipi.app.server.ProxyServerEditResult
import app.skipi.app.server.toProxyServerCopyTarget
import features.proxy.server.model.HTTP
import features.proxy.server.model.VLESS
import features.proxy.server.model.encodePersistedProxyServer
import kotlin.test.Test
import kotlin.test.assertEquals

class DesktopProxyServerCopyPreviewTest {
    @Test
    fun newDraftPreviewAppendsWithoutChangingLibraryCounterOrOpaqueRows() {
        val first = DesktopStoredProxyServer(
            id = 4,
            serverJson = HTTP(remarks = "First").encodePersistedProxyServer(),
        )
        val opaque = DesktopStoredProxyServer(
            id = 8,
            serverJson = "opaque payload that cannot be decoded",
            subscriptionId = 71,
        )
        val last = DesktopStoredProxyServer(
            id = 12,
            serverJson = VLESS(remarks = "Last").encodePersistedProxyServer(),
            subscriptionId = 91,
        )
        val library = DesktopServerLibrary(
            selectedServerId = 4,
            servers = listOf(first, opaque, last),
            nextServerId = 40,
        )
        val draft = HTTP(remarks = "Unsaved")
        val target = ProxyServerEditResult(
            serverId = null,
            server = draft,
        ).toProxyServerCopyTarget(defaultGroupId = DefaultManualGroupId)

        val preview = library.withProxyServerCopyTarget(target)

        assertEquals(TemporaryCopyServerId, preview.selectedServerId)
        assertEquals(library.nextServerId, preview.nextServerId)
        assertEquals(listOf(4, 8, 12, TemporaryCopyServerId), preview.servers.map(DesktopStoredProxyServer::id))
        assertEquals(opaque, preview.servers[1])
        assertEquals(
            DesktopStoredProxyServer(
                id = TemporaryCopyServerId,
                serverJson = draft.encodePersistedProxyServer(),
                subscriptionId = null,
            ),
            preview.servers.last(),
        )
        assertEquals(
            DesktopServerLibrary(
                selectedServerId = 4,
                servers = listOf(first, opaque, last),
                nextServerId = 40,
            ),
            library,
        )
    }

    @Test
    fun existingDraftPreviewReplacesInPlaceUsingItsCapturedGroup() {
        val first = DesktopStoredProxyServer(
            id = 4,
            serverJson = HTTP(remarks = "First").encodePersistedProxyServer(),
        )
        val original = DesktopStoredProxyServer(
            id = 8,
            serverJson = HTTP(remarks = "Stored").encodePersistedProxyServer(),
            subscriptionId = 11,
        )
        val opaque = DesktopStoredProxyServer(
            id = 12,
            serverJson = "opaque payload that cannot be decoded",
            subscriptionId = 71,
        )
        val last = DesktopStoredProxyServer(
            id = 20,
            serverJson = VLESS(remarks = "Last").encodePersistedProxyServer(),
            subscriptionId = 91,
        )
        val library = DesktopServerLibrary(
            selectedServerId = 4,
            servers = listOf(first, original, opaque, last),
            nextServerId = 40,
        )
        val draft = HTTP(remarks = "Edited")
        val target = ProxyServerEditResult(
            serverId = original.id,
            server = draft,
            groupId = 83,
            returnGroupId = 0,
        ).toProxyServerCopyTarget(defaultGroupId = DefaultManualGroupId)

        val preview = library.withProxyServerCopyTarget(target)

        assertEquals(original.id, preview.selectedServerId)
        assertEquals(library.nextServerId, preview.nextServerId)
        assertEquals(listOf(4, 8, 12, 20), preview.servers.map(DesktopStoredProxyServer::id))
        assertEquals(
            DesktopStoredProxyServer(
                id = original.id,
                serverJson = draft.encodePersistedProxyServer(),
                subscriptionId = 83,
            ),
            preview.servers[1],
        )
        assertEquals(opaque, preview.servers[2])
        assertEquals(
            DesktopServerLibrary(
                selectedServerId = 4,
                servers = listOf(first, original, opaque, last),
                nextServerId = 40,
            ),
            library,
        )
    }

    @Test
    fun removedExistingDraftPreviewAppendsTheCapturedIdWithoutAllocating() {
        val first = DesktopStoredProxyServer(
            id = 4,
            serverJson = HTTP(remarks = "First").encodePersistedProxyServer(),
        )
        val opaque = DesktopStoredProxyServer(
            id = 12,
            serverJson = "opaque payload that cannot be decoded",
            subscriptionId = 71,
        )
        val library = DesktopServerLibrary(
            selectedServerId = 4,
            servers = listOf(first, opaque),
            nextServerId = 40,
        )
        val draft = HTTP(remarks = "Restored")
        val target = ProxyServerEditResult(
            serverId = 8,
            server = draft,
            groupId = 55,
        ).toProxyServerCopyTarget(defaultGroupId = DefaultManualGroupId)

        val preview = library.withProxyServerCopyTarget(target)

        assertEquals(8, preview.selectedServerId)
        assertEquals(library.nextServerId, preview.nextServerId)
        assertEquals(listOf(4, 12, 8), preview.servers.map(DesktopStoredProxyServer::id))
        assertEquals(opaque, preview.servers[1])
        assertEquals(55, preview.servers.last().subscriptionId)
        assertEquals(draft.encodePersistedProxyServer(), preview.servers.last().serverJson)
        assertEquals(
            DesktopServerLibrary(
                selectedServerId = 4,
                servers = listOf(first, opaque),
                nextServerId = 40,
            ),
            library,
        )
    }

    private companion object {
        const val DefaultManualGroupId = 1
        const val TemporaryCopyServerId = -1
    }
}
