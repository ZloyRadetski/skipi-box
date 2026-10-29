// Copyright 2026, Radetski
// SPDX-License-Identifier: GPL-3.0

package engine.vpn

import app.AppState
import app.modes.ProxyAppListModeGlobal
import features.proxy.app.projectProxyAppVpnPolicy

data class VpnApplicationPolicy(
    val mode: Int = ProxyAppListModeGlobal,
    val packageNames: List<String> = emptyList(),
)

fun AppState.toVpnApplicationPolicy(currentUserId: Int): VpnApplicationPolicy {
    val projection = projectProxyAppVpnPolicy(
        mode = proxyAppListMode,
        selectedAppKeys = proxyAppListSelectedApps,
        currentUserId = currentUserId,
        strictFullTunnel = enableStrictFullTunnel,
    )
    return VpnApplicationPolicy(
        mode = projection.mode,
        packageNames = projection.packageNames,
    )
}
