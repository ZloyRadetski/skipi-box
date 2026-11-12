// Copyright 2026, Radetski
// SPDX-License-Identifier: GPL-3.0

package engine.vpn

import app.AppState
import app.httpProxyInboundEnabled
import engine.proxy.LocalProxyLoopbackAddress
import engine.proxy.LocalProxyOptions
import engine.proxy.availablePort
import engine.xray.XrayProtocols
import engine.xray.XrayTags
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

import kotlinx.serialization.json.buildJsonArray

data class VpnAppendHttpProxyOptions(
    /** The HTTP inbound exists; Android's VPN then also advertises it as the system HTTP proxy. */
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

fun AppState.toVpnAppendHttpProxyOptions(
    localProxyOptions: LocalProxyOptions,
    excludedPorts: Set<Int> = emptySet(),
): VpnAppendHttpProxyOptions {
    if (!httpProxyInboundEnabled) {
        return VpnAppendHttpProxyOptions.Disabled
    }
    val listenAddress = localProxyOptions.listenAddress
    val reserved = setOf(localProxyOptions.port) + excludedPorts
    // The configured (or, with dynamic ports, already resolved) port wins; a free one only replaces a collision.
    val port = localProxyOptions.httpPort.takeIf { it !in reserved }
        ?: availablePort(listenAddress = listenAddress, excludedPorts = reserved)
        ?: fallbackAppendHttpProxyPort(localProxyOptions.port)
    return VpnAppendHttpProxyOptions(
        enabled = true,
        listenAddress = listenAddress,
        port = port,
        username = localProxyOptions.httpUsername,
        password = localProxyOptions.httpPassword,
    )
}

fun buildVpnAppendHttpInbound(options: VpnAppendHttpProxyOptions): JsonObject {
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

fun fallbackAppendHttpProxyPort(localProxyPort: Int): Int {
    return if (VpnDefaults.VPN_APPEND_HTTP_PROXY_FALLBACK_PORT == localProxyPort) {
        VpnDefaults.VPN_APPEND_HTTP_PROXY_FALLBACK_PORT + 1
    } else {
        VpnDefaults.VPN_APPEND_HTTP_PROXY_FALLBACK_PORT
    }
}
