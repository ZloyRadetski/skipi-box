// Copyright 2026, Radetski
// SPDX-License-Identifier: GPL-3.0

package engine.stats

import app.AppState
import engine.network.findAvailableTcpPort
import engine.network.isTcpPortAvailable
import engine.network.toPortOrNull
import engine.vpn.VpnDefaults

const val XrayStatsApiListenAddress = "127.0.0.1"

fun AppState.xrayStatsApiExcludedPorts(): Set<Int> {
    return buildSet {
        add(localProxyPort.toPortOrNull() ?: VpnDefaults.LOCAL_PROXY_PORT)
        if (enableVpnAppendHttpProxy) {
            add(VpnDefaults.VPN_APPEND_HTTP_PROXY_FALLBACK_PORT)
            add(VpnDefaults.VPN_APPEND_HTTP_PROXY_FALLBACK_PORT + 1)
        }
    }
}

fun findAvailableXrayStatsApiPort(
    excludedPorts: Set<Int>,
): Int {
    return findAvailableTcpPort(
        listenAddress = XrayStatsApiListenAddress,
        excludedPorts = excludedPorts,
        attempts = AvailablePortAttempts,
    ) ?: error("No available Xray stats API port")
}

fun resolveXrayStatsApiPort(
    preferredPort: Int?,
    excludedPorts: Set<Int>,
): Int {
    val port = preferredPort?.takeIf { value ->
            value > 0 &&
            value !in excludedPorts &&
            isTcpPortAvailable(XrayStatsApiListenAddress, value)
    }
    return port ?: findAvailableXrayStatsApiPort(excludedPorts)
}

private const val AvailablePortAttempts = 32
