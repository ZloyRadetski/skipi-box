// Copyright 2026, Radetski
// SPDX-License-Identifier: GPL-3.0

package engine.vpn

import app.AppState
import engine.proxy.LocalProxyLoopbackAddress
import engine.proxy.LocalProxyOptions
import engine.proxy.availablePort
import engine.xray.XrayProtocols
import engine.xray.XrayTags
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

import kotlinx.serialization.json.buildJsonArray

internal data class VpnAppendHttpProxyOptions(
    val enabled: Boolean,
    val listenAddress: String = LocalProxyLoopbackAddress,
    val port: Int,
    val username: String = "",
    val password: String = "",
) {
    companion object {
        val Disabled = VpnAppendHttpProxyOptions(
            enabled = false,
            listenAddress = LocalProxyLoopbackAddress,
            port = 0,
            username = "",
            password = "",
        )
    }
}

internal fun AppState.toVpnAppendHttpProxyOptions(
    localProxyOptions: LocalProxyOptions,
    excludedPorts: Set<Int> = emptySet(),
): VpnAppendHttpProxyOptions {
    if (!enableVpnAppendHttpProxy) {
        return VpnAppendHttpProxyOptions.Disabled
    }
    val listenAddress = localProxyOptions.listenAddress
    return VpnAppendHttpProxyOptions(
        enabled = true,
        listenAddress = listenAddress,
        port = availablePort(
            listenAddress = listenAddress,
            excludedPorts = setOf(localProxyOptions.port) + excludedPorts,
        ) ?: fallbackAppendHttpProxyPort(localProxyOptions.port),
        username = localProxyOptions.username,
        password = localProxyOptions.password,
    )
}

internal fun buildVpnAppendHttpInbound(options: VpnAppendHttpProxyOptions): JsonObject {
    return buildJsonObject {
        put("tag", XrayTags.VPN_APPEND_HTTP_INBOUND)
        put("listen", options.listenAddress)
        put("port", options.port)
        put("protocol", XrayProtocols.HTTP)
        put(
            "settings",
            buildJsonObject {
                put("allowTransparent", false)
                put("userLevel", 0)
                if (options.username.isNotBlank()) {
                    put(
                        "accounts",
                        buildJsonArray {
                            add(
                                buildJsonObject {
                                    put("user", options.username)
                                    put("pass", options.password)
                                }
                            )
                        }
                    )
                }
            },
        )
    }
}

internal fun fallbackAppendHttpProxyPort(localProxyPort: Int): Int {
    return if (VpnDefaults.VPN_APPEND_HTTP_PROXY_FALLBACK_PORT == localProxyPort) {
        VpnDefaults.VPN_APPEND_HTTP_PROXY_FALLBACK_PORT + 1
    } else {
        VpnDefaults.VPN_APPEND_HTTP_PROXY_FALLBACK_PORT
    }
}
