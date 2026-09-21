// Copyright 2026, Radetski
// SPDX-License-Identifier: GPL-3.0

package features.proxy.server.usecase.importer

import kotlin.test.Test
import kotlin.test.assertEquals

class MihomoProxyNodeImportTest {
    @Test
    fun importsSupportedNodesAndClassifiesRejectedNodesWithoutPlatformLogging() {
        val result = listOf(
            mapOf(
                "type" to "socks5",
                "name" to "Valid node",
                "server" to "socks.example",
                "port" to 1080,
            ),
            mapOf(
                "type" to "tuic",
                "name" to "Unsupported node",
            ),
            mapOf(
                "name" to "Missing type",
            ),
        ).importMihomoProxyNodes(startIndex = 4)

        assertEquals(1, result.servers.size)
        assertEquals("Socks", result.servers.single().getInfo().protocol)
        assertEquals(2, result.rejectedCount)
        assertEquals(listOf(5, 6), result.failures.map(MihomoProxyNodeImportFailure::index))
        assertEquals(
            listOf("unsupported proxy type", "missing proxy type"),
            result.failures.map(MihomoProxyNodeImportFailure::reason),
        )
        assertEquals(listOf("Unsupported node", "Missing type"), result.failures.map(MihomoProxyNodeImportFailure::name))
    }

    @Test
    fun callerCanNarrowProtocolsWithoutDuplicatingValidation() {
        val result = listOf(
            mapOf(
                "type" to "socks5",
                "name" to "Desktop-compatible",
                "server" to "socks.example",
                "port" to 1080,
            ),
        ).importMihomoProxyNodes(isTypeSupported = { false })

        assertEquals(emptyList(), result.servers)
        assertEquals(1, result.rejectedCount)
        assertEquals("unsupported proxy type", result.failures.single().reason)
    }
}
