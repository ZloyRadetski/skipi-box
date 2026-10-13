// Copyright 2026, Radetski
// SPDX-License-Identifier: GPL-3.0

package app.skipi.app.home

import app.modes.ProxyServerListSortDefault
import app.modes.ProxyServerListSortLatency
import app.modes.ProxyServerListSortName
import app.skipi.app.proxy.ProxyServerGroupingRequest
import app.skipi.app.proxy.ProxyServerGroupingResult

/** Membership used by Home summaries, distinct from the raw tab count used by the legacy tab adapter. */
data class ProxyHomeGroupMembership(
    val id: Int,
    val title: String,
    val serverIds: List<Int>,
) {
    val serverCount: Int get() = serverIds.size
}

/** Projects canonical grouping output into the membership and counts consumed by shared Home. */
fun proxyHomeGroupMemberships(
    request: ProxyServerGroupingRequest,
    grouping: ProxyServerGroupingResult,
): List<ProxyHomeGroupMembership> {
    val visibleIds = grouping.visibleServerIds.toSet()
    val visibleServers = request.servers.filter { server -> server.id in visibleIds }
    return grouping.groupTabs.map { tab ->
        val members = if (tab.id == request.allGroupId) {
            visibleServers.filter { server ->
                !server.isStrategyGroup || server.sourceTrafficConfigId == null
            }
        } else {
            visibleServers.filter { server -> server.groupId == tab.id }
        }
        ProxyHomeGroupMembership(
            id = tab.id,
            title = tab.name,
            serverIds = members.map { server -> server.id },
        )
    }
}

/** Keeps the Android Home search contract, including the server's resolved group title. */
fun proxyHomeServerSearchText(
    title: String,
    remarks: String,
    address: String,
    protocol: String,
    groupName: String,
): String = listOf(title, remarks, address, protocol, groupName).joinToString(" ")

/** Name sorting uses the canonical remarks, including any leading country flag. */
fun proxyHomeServerSortKey(remarks: String, title: String): String = remarks.ifBlank { title }

/** Persist the shared mode with the same numeric values already stored by Android. */
fun ProxyHomeSortMode.toAndroidValue(): Int = when (this) {
    ProxyHomeSortMode.Default -> ProxyServerListSortDefault
    ProxyHomeSortMode.Name -> ProxyServerListSortName
    ProxyHomeSortMode.Latency -> ProxyServerListSortLatency
}

/** Unknown persisted values follow Android's existing fallback to default order. */
fun proxyHomeSortModeFromAndroidValue(value: Int): ProxyHomeSortMode = when (value) {
    ProxyServerListSortName -> ProxyHomeSortMode.Name
    ProxyServerListSortLatency -> ProxyHomeSortMode.Latency
    else -> ProxyHomeSortMode.Default
}
