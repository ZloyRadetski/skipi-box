// Copyright 2026, Radetski
// SPDX-License-Identifier: GPL-3.0

package features.proxy.server.list

import app.ProxyServerLatencyTesting
import app.ProxyServerState
import app.modes.ProxyServerListLayoutDouble
import app.modes.ProxyServerListLayoutMultiple
import app.modes.ProxyServerListSortLatency
import app.modes.ProxyServerListSortName

fun Int.resolvedProxyServerListColumns(): Int {
    return when (this) {
        ProxyServerListLayoutDouble -> 2
        ProxyServerListLayoutMultiple -> 3
        else -> 1
    }
}

fun List<ProxyServerState>.sortedForProxyServerList(
    sort: Int,
): List<ProxyServerState> {
    return when (sort) {
        ProxyServerListSortName -> sortedWith(
            compareBy(String.CASE_INSENSITIVE_ORDER) { server -> server.displaySortTitle() },
        )
        ProxyServerListSortLatency -> sortedWith(
            compareBy<ProxyServerState> { server -> server.latency.proxyServerListLatencySortKey() }
                .thenBy(String.CASE_INSENSITIVE_ORDER) { server -> server.displaySortTitle() },
        )
        else -> this
    }
}

private fun ProxyServerState.displaySortTitle(): String {
    val info = server.getInfo()
    return info.remarks.ifBlank { info.protocol }
}

private fun String.proxyServerListLatencySortKey(): Int {
    val trimmed = trim()
    if (this == ProxyServerLatencyTesting) return Int.MAX_VALUE - 2
    if (trimmed.isBlank() || trimmed.startsWith("-") || trimmed.contains("Failed", ignoreCase = true) || trimmed.contains("Timeout", ignoreCase = true) || trimmed.contains("Error", ignoreCase = true)) {
        return Int.MAX_VALUE - 1
    }
    val number = latencyNumberRegex.find(trimmed)?.value?.toIntOrNull()
    return number ?: (Int.MAX_VALUE - 1)
}

private val latencyNumberRegex = Regex("""\d+""")
