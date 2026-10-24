// Copyright 2026, Radetski
// SPDX-License-Identifier: GPL-3.0

package features.proxy.app

import app.modes.ProxyAppListModeBlacklist
import app.modes.ProxyAppListModeGlobal
import app.modes.ProxyAppListModeWhitelist
import utils.toTrimmedNonEmptyDistinctList

data class ProxyAppVpnPolicy(
    val mode: Int = ProxyAppListModeGlobal,
    val packageNames: List<String> = emptyList(),
)

fun projectProxyAppVpnPolicy(
    mode: Int,
    selectedAppKeys: List<String>,
    currentUserId: Int,
    strictFullTunnel: Boolean,
): ProxyAppVpnPolicy {
    if (strictFullTunnel) {
        return ProxyAppVpnPolicy(mode = ProxyAppListModeGlobal)
    }
    val effectiveMode = when (mode) {
        ProxyAppListModeBlacklist,
        ProxyAppListModeWhitelist,
        ProxyAppListModeGlobal -> mode

        else -> ProxyAppListModeGlobal
    }
    if (effectiveMode == ProxyAppListModeGlobal) {
        return ProxyAppVpnPolicy(mode = effectiveMode)
    }
    return ProxyAppVpnPolicy(
        mode = effectiveMode,
        packageNames = selectedAppKeys.toVpnServicePackageNames(currentUserId),
    )
}

private fun List<String>.toVpnServicePackageNames(currentUserId: Int): List<String> =
    mapNotNull { key ->
        val separatorIndex = key.indexOf(':')
        if (separatorIndex < 0) {
            key
        } else {
            val userId = key.substring(0, separatorIndex).toIntOrNull()
            if (userId == currentUserId) {
                key.substring(separatorIndex + 1)
            } else {
                null
            }
        }
    }.toTrimmedNonEmptyDistinctList()
