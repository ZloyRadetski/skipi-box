// Copyright 2026, Radetski
// SPDX-License-Identifier: GPL-3.0

package engine.vpn

import engine.network.findAvailableTcpPort
import engine.network.isPort
import engine.network.isTcpPortAvailable
import engine.xray.XrayOutboundPlan
import features.proxy.server.model.AmneziaWg
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.add
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

/**
 * The process-wide AmneziaWG runner a route needs: its private loopback SOCKS port and the
 * runtime config for the native core. Android's VpnService and the Desktop in-process Core
 * run the same plan.
 */
data class AmneziaWgBridgePlan(
    val socksPort: Int,
    val configJson: String,
    /** The server as configured; a hostname endpoint is resolved only inside [configJson]. */
    val server: AmneziaWg,
)

/**
 * Plans the single native AmneziaWG runtime for [servers]; null when there is none. Fails clearly if
 * a route asks the one runtime to represent two different tunnels.
 */
fun planAmneziaWgBridge(
    servers: List<AmneziaWg>,
    dnsServers: List<String>,
    reservedPorts: Set<Int>,
    resolveEndpoint: (AmneziaWg) -> AmneziaWg = AmneziaWg::withNativeRunnerEndpointResolved,
): AmneziaWgBridgePlan? {
    val server = servers.singleAmneziaWgRuntimeOrNull() ?: return null
    require(server.finalMask.isBlank()) {
        "AmneziaWG FinalMask is not supported by the native AmneziaWG runtime"
    }
    val port = findAvailableLocalPort(preferredPort = 10809, reservedPorts = reservedPorts)
    // Pass precisely this AWG outbound to skipi-core. Its generic JSON rewriter would also
    // rewrite ordinary WireGuard outbounds, which is unsafe in mixed profiles.
    val configJson = resolveEndpoint(server).toNativeRunnerConfigJson(
        tag = "skipi_awg_runtime",
        dnsServers = dnsServers,
    )
    return AmneziaWgBridgePlan(socksPort = port, configJson = configJson, server = server)
}

/** Points every outbound of the bridged tunnel at the runner's loopback SOCKS listener. */
fun XrayOutboundPlan.withAmneziaWgBridge(bridge: AmneziaWgBridgePlan): XrayOutboundPlan = copy(
    proxyOutbounds = proxyOutbounds.map { item ->
        val awg = item.server as? AmneziaWg
        if (awg != null && awg.matchesRuntimeConfig(bridge.server)) {
            item.copy(customOutbound = buildLoopbackSocksOutbound(tag = item.tag, port = bridge.socksPort))
        } else {
            item
        }
    },
)

/**
 * The native AmneziaWG runner uses its own netstack, so its resolver must be
 * supplied separately from the Xray outbound. Keep this runtime-only field out
 * of the Xray configuration: Xray's WireGuard outbound does not understand it.
 */
fun AmneziaWg.toNativeRunnerConfigJson(
    tag: String,
    dnsServers: List<String>,
): String {
    requireNativeRunnerCompatibility()
    val outbound = toXrayOutbound(tag).toJsonObject()
    val settings = outbound["settings"] as? JsonObject
        ?: error("AmneziaWG outbound has no settings")
    return buildJsonObject {
        outbound.forEach { (key, value) ->
            if (key != "settings") put(key, value)
        }
        put(
            "settings",
            buildJsonObject {
                settings.forEach { (key, value) -> put(key, value) }
                put("dnsServers", buildJsonArray {
                    dnsServers.forEach { server -> add(server) }
                })
            },
        )
    }.toString()
}

/**
 * The native AmneziaWG implementation cannot safely recover from every
 * malformed obfuscation setting: some values make its Go runtime abort the
 * whole Android process. Profiles can predate validation or arrive from an
 * import, so this boundary must be strict even when the editor showed a
 * warning earlier.
 */
private fun AmneziaWg.requireNativeRunnerCompatibility() {
    require(finalMask.isBlank()) {
        "AmneziaWG FinalMask is not supported by the native AmneziaWG runtime"
    }
    val issues = validateFull()
    require(issues.isEmpty()) {
        "AmneziaWG configuration is invalid for the native runtime: " +
            issues.joinToString { issue -> issue.error.name }
    }
}

internal fun List<AmneziaWg>.singleAmneziaWgRuntimeOrNull(): AmneziaWg? {
    val distinct = fold(mutableListOf<AmneziaWg>()) { runtimes, server ->
        if (runtimes.none { it.matchesRuntimeConfig(server) }) runtimes += server
        runtimes
    }
    require(distinct.size <= 1) {
        "The selected route contains multiple different AmneziaWG tunnels; only one native AmneziaWG runtime can run at a time"
    }
    return distinct.singleOrNull()
}

/** Builds a private loopback SOCKS outbound for a native bridge. */
fun buildLoopbackSocksOutbound(
    tag: String,
    port: Int,
    user: String = "",
    pass: String = "",
): JsonObject {
    return buildJsonObject {
        put("tag", tag)
        put("protocol", "socks")
        put("settings", buildJsonObject {
            put("servers", buildJsonArray {
                add(buildJsonObject {
                    put("address", "127.0.0.1")
                    put("port", port)
                    if (user.isNotBlank() || pass.isNotBlank()) {
                        put("users", buildJsonArray {
                            add(buildJsonObject {
                                put("user", user)
                                put("pass", pass)
                                put("level", 0)
                            })
                        })
                    }
                })
            })
        })
    }
}

fun findAvailableLocalPort(preferredPort: Int, reservedPorts: Set<Int>): Int {
    if (preferredPort.isPort() && preferredPort !in reservedPorts && isTcpPortAvailable("127.0.0.1", preferredPort)) {
        return preferredPort
    }
    val discovered = findAvailableTcpPort("127.0.0.1", reservedPorts)
    if (discovered != null && discovered.isPort()) {
        return discovered
    }
    for (port in 10809..65535) {
        if (port !in reservedPorts && isTcpPortAvailable("127.0.0.1", port)) {
            return port
        }
    }
    throw IllegalStateException("No free local TCP port is available for the native proxy bridge")
}
