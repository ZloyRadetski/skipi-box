// Copyright 2026, Radetski
// SPDX-License-Identifier: GPL-3.0

package features.subscription

import features.proxy.server.model.ProxyServer

/** A stored subscription server reduced to the fields needed for stable-ID reconciliation. */
data class SubscriptionServerCandidate(
    val id: Int,
    val server: ProxyServer<*>,
)

/** One incoming server after its stable ID has been resolved. */
data class ReconciledSubscriptionServer(
    val id: Int,
    val server: ProxyServer<*>,
    /** Existing record matched directly by fingerprint, endpoint, or position. */
    val matchedPreviousId: Int? = null,
)

data class SubscriptionServerReconciliation(
    val servers: List<ReconciledSubscriptionServer>,
    val nextServerId: Int,
    /** Old IDs remapped to their replacement, including stable identity mappings. */
    val oldIdToNewId: Map<Int, Int>,
)

/**
 * Platform policy for subscription refreshes.
 *
 * Android keeps composite proxy references alive by progressively matching old
 * entries. Desktop intentionally uses only fingerprint matching because its
 * persisted library has no composite-ID references yet.
 */
data class SubscriptionServerReconciliationPolicy(
    val deduplicateIncomingByFingerprint: Boolean = false,
    val matchByEndpoint: Boolean = true,
    val matchByPositionWhenSameType: Boolean = true,
    val remapFirstUnmatchedCandidate: Boolean = true,
)

/**
 * Assigns stable IDs to refreshed subscription servers without any storage,
 * UI, network, or platform dependencies.
 */
fun reconcileSubscriptionServers(
    previous: List<SubscriptionServerCandidate>,
    incoming: List<ProxyServer<*>>,
    firstNewServerId: Int,
    occupiedIds: Set<Int> = emptySet(),
    policy: SubscriptionServerReconciliationPolicy = SubscriptionServerReconciliationPolicy(),
): SubscriptionServerReconciliation {
    val inputs = if (policy.deduplicateIncomingByFingerprint) {
        incoming.distinctBy { server -> server.connectionFingerprint() }
    } else {
        incoming
    }
    val candidates = previous.map { candidate ->
        SubscriptionServerCandidateEntry(
            candidate = candidate,
            fingerprint = candidate.server.connectionFingerprint(),
            remarks = candidate.server.getInfo().remarks.trim(),
            endpointKey = candidate.server.subscriptionEndpointKey(),
        )
    }
    val candidatesByFingerprint = candidates.groupBy(SubscriptionServerCandidateEntry::fingerprint)
    val candidatesByEndpoint = candidates
        .mapNotNull { entry -> entry.endpointKey?.let { endpoint -> endpoint to entry } }
        .groupBy({ (endpoint, _) -> endpoint }, { (_, entry) -> entry })
    val consumedIds = mutableSetOf<Int>()
    val unavailableIds = (occupiedIds + previous.map(SubscriptionServerCandidate::id)).toMutableSet()
    val oldIdToNewId = linkedMapOf<Int, Int>()
    var nextServerId = firstNewServerId

    fun nextFreeId(): Int {
        while (nextServerId in unavailableIds) {
            check(nextServerId < Int.MAX_VALUE) { "No free subscription server ID remains" }
            nextServerId += 1
        }
        val id = nextServerId
        check(id < Int.MAX_VALUE) { "No free subscription server ID remains" }
        unavailableIds += id
        nextServerId += 1
        return id
    }

    val servers = inputs.mapIndexed { index, server ->
        val fingerprintMatches = candidatesByFingerprint[server.connectionFingerprint()]
            .orEmpty()
            .filterNot { entry -> entry.candidate.id in consumedIds }
        var matched = fingerprintMatches.bestMatchFor(server)

        if (matched == null && policy.matchByEndpoint) {
            val endpoint = server.subscriptionEndpointKey()
            val endpointMatches = endpoint
                ?.let(candidatesByEndpoint::get)
                .orEmpty()
                .filterNot { entry -> entry.candidate.id in consumedIds }
            matched = endpointMatches.bestMatchFor(server, remarksOnly = true)
        }
        if (matched == null && policy.matchByPositionWhenSameType) {
            val positional = candidates.getOrNull(index)
            if (positional != null &&
                positional.candidate.id !in consumedIds &&
                positional.candidate.server::class == server::class
            ) {
                matched = positional
            }
        }

        if (matched != null) {
            consumedIds += matched.candidate.id
            oldIdToNewId[matched.candidate.id] = matched.candidate.id
            ReconciledSubscriptionServer(
                id = matched.candidate.id,
                server = server,
                matchedPreviousId = matched.candidate.id,
            )
        } else {
            val id = nextFreeId()
            if (policy.remapFirstUnmatchedCandidate) {
                val displaced = candidates.getOrNull(index)?.takeIf { entry -> entry.candidate.id !in consumedIds }
                    ?: candidates.firstOrNull { entry -> entry.candidate.id !in consumedIds }
                if (displaced != null) {
                    consumedIds += displaced.candidate.id
                    oldIdToNewId[displaced.candidate.id] = id
                }
            }
            ReconciledSubscriptionServer(id = id, server = server)
        }
    }

    return SubscriptionServerReconciliation(
        servers = servers,
        nextServerId = nextServerId,
        oldIdToNewId = oldIdToNewId,
    )
}

private data class SubscriptionServerCandidateEntry(
    val candidate: SubscriptionServerCandidate,
    val fingerprint: String,
    val remarks: String,
    val endpointKey: String?,
)

private fun List<SubscriptionServerCandidateEntry>.bestMatchFor(
    server: ProxyServer<*>,
    remarksOnly: Boolean = false,
): SubscriptionServerCandidateEntry? {
    if (isEmpty()) return null
    if (size == 1) return first()
    val remarks = server.getInfo().remarks.trim()
    return firstOrNull { entry -> entry.remarks == remarks }
        ?: if (remarksOnly) first() else firstOrNull { entry ->
            entry.remarks.contains(remarks, ignoreCase = true) || remarks.contains(entry.remarks, ignoreCase = true)
        }
        ?: first()
}

private fun ProxyServer<*>.subscriptionEndpointKey(): String? {
    val info = getInfo()
    val address = info.address.trim().lowercase()
    if (address.isBlank() || address == ":0" || address == "0") return null
    return "${info.protocol.lowercase()}|$address"
}
