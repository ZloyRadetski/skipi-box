// Copyright 2026, Radetski
// SPDX-License-Identifier: GPL-3.0

// Draft intended for:
// shared/app/src/commonTest/kotlin/app/skipi/app/home/ProxyHomeDeletionCleanupOperationTest.kt
// Keep this file outside module source sets until the H01/S03 checkpoint is complete.
package app.skipi.app.home

import app.skipi.app.model.ProxyServerCatalog
import app.skipi.app.model.ProxyServerRecord
import app.skipi.app.proxy.deleteProxyServerRecords
import features.proxy.server.model.ChainProxy
import features.proxy.server.model.ProxyServer
import features.proxy.server.model.StrategyGroup
import features.proxy.server.usecase.ProxyServerGroupCleanup
import features.proxy.server.usecase.ProxyServerGroupCleanupRecord
import kotlin.test.Test
import kotlin.test.assertEquals

class ProxyHomeDeletionCleanupOperationTest {
    // Cleanup is dispatched directly by Android; no service stop is added for this tool.
    @Test
    fun duplicateCleanupThenCatalogDeletePreservesSelectedDuplicateOrderAndCompositeReferences() {
        val duplicateUrl = "vless://8b4a2b20-c533-4d13-a3e0-bb0a8d7eb9c6@node.example:443#Node"
        val catalog = ProxyServerCatalog(
            servers = listOf(
                ProxyServerRecord(
                    id = 30,
                    server = StrategyGroup(proxyServerIds = listOf(7, 8, 9), selectedMemberId = 7),
                    sourceSubscriptionId = 70,
                    enabled = false,
                ),
                ProxyServerRecord(id = 40, server = ChainProxy(proxyServerIds = listOf(7, 8, 9)), sourceSubscriptionId = 71),
                ProxyServerRecord(id = 7, server = ProxyServer.parse(duplicateUrl), sourceSubscriptionId = 72),
                ProxyServerRecord(id = 8, server = ProxyServer.parse(duplicateUrl), sourceSubscriptionId = 73),
                // Same URL outside the selected subscription group must remain untouched.
                ProxyServerRecord(id = 9, server = ProxyServer.parse(duplicateUrl), sourceSubscriptionId = 99, enabled = false),
                ProxyServerRecord(id = 10, server = ProxyServer.parse("vless://other@other.example:443#Other")),
            ),
            nextServerId = 80,
            selectedServerId = 8,
        )
        val cleanupRows = catalog.servers.map { ProxyServerGroupCleanupRecord(it.id, it.server) }

        val duplicateIds = ProxyServerGroupCleanup.duplicateServerIds(
            servers = cleanupRows,
            currentGroupServerIds = setOf(7, 8),
            selectedServerId = catalog.selectedServerId,
        )
        val updated = deleteProxyServerRecords(catalog, duplicateIds)

        assertEquals(setOf(7), duplicateIds)
        assertEquals(listOf(30, 40, 8, 9, 10), updated.servers.map { it.id })
        assertEquals(80, updated.nextServerId)
        assertEquals(8, updated.selectedServerId)
        assertEquals(listOf(70, 71, 73, 99, null), updated.servers.map { it.sourceSubscriptionId })
        assertEquals(listOf(false, true, true, false, true), updated.servers.map { it.enabled })
        assertEquals(listOf(8, 9), (updated.servers.single { it.id == 30 }.server as StrategyGroup).proxyServerIds)
        assertEquals(8, (updated.servers.single { it.id == 30 }.server as StrategyGroup).selectedMemberId)
        assertEquals(listOf(8, 9), (updated.servers.single { it.id == 40 }.server as ChainProxy).proxyServerIds)
    }
}
