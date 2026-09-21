// Copyright 2026, Radetski
// SPDX-License-Identifier: GPL-3.0

package features.subscription

import features.proxy.server.model.Trojan
import features.proxy.server.model.VLESS
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class SubscriptionServerReconciliationTest {
    @Test
    fun preservesIdsForEquivalentServersEvenWhenTheProviderReordersThem() {
        val first = vless("first.example", "first")
        val second = vless("second.example", "second")

        val result = reconcileSubscriptionServers(
            previous = listOf(
                SubscriptionServerCandidate(id = 10, server = first),
                SubscriptionServerCandidate(id = 20, server = second),
            ),
            incoming = listOf(second.copy(remarks = "renamed"), first.copy(remarks = "renamed")),
            firstNewServerId = 100,
        )

        assertEquals(listOf(20, 10), result.servers.map(ReconciledSubscriptionServer::id))
        assertEquals(mapOf(20 to 20, 10 to 10), result.oldIdToNewId)
        assertEquals(100, result.nextServerId)
    }

    @Test
    fun remapsAnUnmatchedOldReferenceWhenTheReplacementCannotKeepItsId() {
        val result = reconcileSubscriptionServers(
            previous = listOf(SubscriptionServerCandidate(id = 10, server = vless("old.example", "old"))),
            incoming = listOf(Trojan(remarks = "new", server = "new.example", port = "443", password = "secret")),
            firstNewServerId = 100,
        )

        assertEquals(100, result.servers.single().id)
        assertNull(result.servers.single().matchedPreviousId)
        assertEquals(mapOf(10 to 100), result.oldIdToNewId)
    }

    @Test
    fun desktopPolicyDeduplicatesWithoutEndpointOrPositionFallback() {
        val incoming = vless("new.example", "new")
        val result = reconcileSubscriptionServers(
            previous = listOf(SubscriptionServerCandidate(id = 10, server = vless("old.example", "old"))),
            incoming = listOf(incoming, incoming.copy(remarks = "duplicate")),
            firstNewServerId = 100,
            policy = SubscriptionServerReconciliationPolicy(
                deduplicateIncomingByFingerprint = true,
                matchByEndpoint = false,
                matchByPositionWhenSameType = false,
                remapFirstUnmatchedCandidate = false,
            ),
        )

        assertEquals(listOf(100), result.servers.map(ReconciledSubscriptionServer::id))
        assertEquals(emptyMap(), result.oldIdToNewId)
    }

    private fun vless(host: String, remarks: String): VLESS = VLESS(
        remarks = remarks,
        id = "8b4a2b20-c533-4d13-a3e0-bb0a8d7eb9c6",
        server = host,
        port = "443",
    )
}
