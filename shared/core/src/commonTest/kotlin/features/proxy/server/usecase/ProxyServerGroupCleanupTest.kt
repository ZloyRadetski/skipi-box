// Copyright 2026, Radetski
// SPDX-License-Identifier: GPL-3.0

package features.proxy.server.usecase

import features.proxy.server.model.ChainProxy
import features.proxy.server.model.Custom
import features.proxy.server.model.HTTP
import features.proxy.server.model.ProxyServer
import features.proxy.server.model.StrategyGroup
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class ProxyServerGroupCleanupTest {
    @Test
    fun duplicateRemovalIsGroupScopedKeepsSelectedAndIgnoresUrlLessCompositeServers() {
        val duplicateUrl = "vless://8b4a2b20-c533-4d13-a3e0-bb0a8d7eb9c6@node.example:443#Node"
        val servers = listOf(
            record(1, ProxyServer.parse(duplicateUrl)),
            record(2, ProxyServer.parse(duplicateUrl)),
            record(3, ProxyServer.parse(duplicateUrl)),
            record(4, Custom(configJson = "{}")),
            record(5, StrategyGroup(proxyServerIds = listOf(1))),
        )

        assertEquals(
            setOf(1),
            ProxyServerGroupCleanup.duplicateServerIds(servers, currentGroupServerIds = setOf(1, 2, 4, 5), selectedServerId = 2),
        )
    }

    @Test
    fun invalidRemovalUsesModelValidationWithoutResolvingCompositeReferences() {
        val validChain = ChainProxy(remarks = "Valid chain", proxyServerIds = listOf(100, 101))
        assertTrue(validChain.validateFull().isEmpty())

        val servers = listOf(
            record(1, HTTP(port = "")),
            record(2, validChain),
            record(3, StrategyGroup(strategy = "unsupported", proxyServerIds = listOf(100))),
            record(4, HTTP(port = "")),
        )

        assertEquals(
            setOf(1, 3),
            ProxyServerGroupCleanup.invalidServerIds(servers, currentGroupServerIds = setOf(1, 2, 3)),
        )
    }

    private fun record(id: Int, server: ProxyServer<*>) = ProxyServerGroupCleanupRecord(id, server)
}
