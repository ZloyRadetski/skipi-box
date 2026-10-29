// Copyright 2026, Radetski
// SPDX-License-Identifier: GPL-3.0

package features.backup

import features.proxy.server.model.ChainProxy
import features.proxy.server.model.ProxyServer
import features.proxy.server.model.StrategyGroup

/** Problems a restore can only report, because the restored catalog is still usable. */
sealed interface AppBackupWarning {
    data class MissingChainProxyMembers(val count: Int) : AppBackupWarning

    data class MissingStrategyGroupMembers(val count: Int) : AppBackupWarning
}

/**
 * Counts chain/strategy members that point at servers missing from the restored catalog.
 * Strategy groups sourced from a profile resolve their members at runtime and are skipped.
 */
fun appBackupRestoreWarnings(servers: List<Pair<Int, ProxyServer<*>>>): List<AppBackupWarning> {
    val serverIds = servers.mapTo(mutableSetOf()) { (id, _) -> id }
    val missingChainMembers = servers.sumOf { (_, server) ->
        (server as? ChainProxy)?.proxyServerIds?.count { memberId -> memberId !in serverIds } ?: 0
    }
    val missingStrategyMembers = servers.sumOf { (_, server) ->
        val strategy = server as? StrategyGroup
        if (strategy?.sourceTrafficConfigId == null) {
            strategy?.proxyServerIds?.count { memberId -> memberId !in serverIds } ?: 0
        } else {
            0
        }
    }
    return buildList {
        if (missingChainMembers > 0) add(AppBackupWarning.MissingChainProxyMembers(missingChainMembers))
        if (missingStrategyMembers > 0) add(AppBackupWarning.MissingStrategyGroupMembers(missingStrategyMembers))
    }
}
