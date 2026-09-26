// Copyright 2026, Radetski
// SPDX-License-Identifier: GPL-3.0

package features.proxy.server.usecase

import features.proxy.server.model.ProxyServer
import features.proxy.server.model.getUrlOrNull

/** Persistence-neutral proxy server input record for group-scoped cleanup decisions. */
data class ProxyServerGroupCleanupRecord(
    val id: Int,
    val server: ProxyServer<*>,
)

/** Pure cleanup decisions. Callers apply the returned IDs to their own state/persistence models. */
object ProxyServerGroupCleanup {
    /**
     * Finds URL duplicates only among the supplied group members. URL-less
     * records, including composites, are left alone. If the selected record
     * duplicates an earlier record, the selected record is retained.
     */
    fun duplicateServerIds(
        servers: List<ProxyServerGroupCleanupRecord>,
        currentGroupServerIds: Set<Int>,
        selectedServerId: Int,
    ): Set<Int> {
        val keptServerIdsByUrl = mutableMapOf<String, Int>()
        val duplicateServerIds = mutableSetOf<Int>()
        servers.forEach { record ->
            val url = runCatching { record.server.getUrlOrNull() }.getOrNull()
            if (record.id in currentGroupServerIds && url != null) {
                val keptServerId = keptServerIdsByUrl[url]
                if (keptServerId == null) {
                    keptServerIdsByUrl[url] = record.id
                } else if (record.id == selectedServerId) {
                    duplicateServerIds += keptServerId
                    keptServerIdsByUrl[url] = record.id
                } else {
                    duplicateServerIds += record.id
                }
            }
        }
        return duplicateServerIds
    }

    /**
     * Finds records that fail their own full validation in the supplied
     * group. Composite references are not resolved here, matching model-level
     * validation; reference cleanup remains with the state owner after delete.
     */
    fun invalidServerIds(
        servers: List<ProxyServerGroupCleanupRecord>,
        currentGroupServerIds: Set<Int>,
    ): Set<Int> = servers.asSequence()
        .filter { record -> record.id in currentGroupServerIds }
        .filter { record -> record.server.validateFull().isNotEmpty() }
        .map(ProxyServerGroupCleanupRecord::id)
        .toSet()
}
