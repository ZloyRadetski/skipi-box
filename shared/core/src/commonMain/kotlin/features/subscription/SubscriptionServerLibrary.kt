// Copyright 2026, Radetski
// SPDX-License-Identifier: GPL-3.0

package features.subscription

import features.proxy.server.model.ProxyServer

/** Persistence-neutral server record used by subscription refresh adapters. */
data class SubscriptionServerRecord(
    val id: Int,
    val subscriptionId: Int? = null,
    val server: ProxyServer<*>? = null,
)

data class SubscriptionServerLibrary(
    val selectedServerId: Int? = null,
    val servers: List<SubscriptionServerRecord> = emptyList(),
)

/**
 * Replaces exactly one provider group while retaining manual and unrelated
 * records. Stable IDs are kept by the shared reconciliation policy.
 */
fun replaceSubscriptionServerGroup(
    library: SubscriptionServerLibrary,
    subscriptionId: Int,
    incoming: List<ProxyServer<*>>,
): SubscriptionServerLibrary {
    require(subscriptionId > 0) { "Subscription ID must be positive" }
    val previousGroup = library.servers.filter { it.subscriptionId == subscriptionId }
    val remaining = library.servers.filterNot { it.subscriptionId == subscriptionId }
    val reconciliation = reconcileSubscriptionServers(
        previous = previousGroup.mapNotNull { record ->
            record.server?.let { SubscriptionServerCandidate(id = record.id, server = it) }
        },
        incoming = incoming,
        firstNewServerId = (library.servers.maxOfOrNull(SubscriptionServerRecord::id) ?: 0) + 1,
        occupiedIds = library.servers.mapTo(mutableSetOf(), SubscriptionServerRecord::id),
        policy = SubscriptionServerReconciliationPolicy(
            deduplicateIncomingByFingerprint = true,
            matchByEndpoint = false,
            matchByPositionWhenSameType = false,
            remapFirstUnmatchedCandidate = false,
        ),
    )
    val replacements = reconciliation.servers.map { record ->
        SubscriptionServerRecord(record.id, subscriptionId, record.server)
    }
    val combined = remaining + replacements
    val selected = library.selectedServerId
        ?.takeIf { id -> combined.any { it.id == id } }
        ?: replacements.firstOrNull()?.id
        ?: remaining.firstOrNull()?.id
    return SubscriptionServerLibrary(selected, combined.distinctBy(SubscriptionServerRecord::id))
}
