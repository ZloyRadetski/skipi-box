// Copyright 2026, Radetski
// SPDX-License-Identifier: GPL-3.0

package app.skipi.desktop

import features.proxy.server.model.HTTP
import features.proxy.server.model.encodePersistedProxyServer
import kotlin.test.Test
import kotlin.test.assertEquals

class DesktopProxyServerRepositoryTest {
    @Test
    fun selectingServerPreservesPersistedIdPayloadAndSubscriptionAssociation() {
        val payload = HTTP(remarks = "Stable ID", server = "proxy.example").encodePersistedProxyServer()
        var currentLibrary = DesktopServerLibrary(
            selectedServerId = null,
            servers = listOf(
                DesktopStoredProxyServer(
                    id = 42,
                    serverJson = payload,
                    subscriptionId = 9,
                ),
            ),
        )
        val repository = DesktopProxyServerRepository(
            initialLibrary = currentLibrary,
            readLibrary = { currentLibrary },
            saveLibrary = { library ->
                currentLibrary = library
                Result.success(Unit)
            },
            publishLibrary = { library -> currentLibrary = library },
        )

        repository.selectAndCommit(42)

        assertEquals(42, currentLibrary.selectedServerId)
        assertEquals(42, currentLibrary.servers.single().id)
        assertEquals(payload, currentLibrary.servers.single().serverJson)
        assertEquals(9, currentLibrary.servers.single().subscriptionId)
    }
}
