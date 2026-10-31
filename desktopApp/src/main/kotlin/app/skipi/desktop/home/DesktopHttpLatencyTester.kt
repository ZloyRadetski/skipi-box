// Copyright 2026, Radetski
// SPDX-License-Identifier: GPL-3.0

package app.skipi.desktop

import engine.vpn.VpnDefaults
import engine.vpn.buildLoopbackSocksOutbound
import engine.vpn.planAmneziaWgBridge
import features.proxy.server.model.AmneziaWg
import features.proxy.server.model.Custom
import features.proxy.server.model.ProxyServer
import features.proxy.server.model.StrategyGroup
import features.proxy.server.model.isCompositeProxyServer
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.add
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

/** A throwaway Core configuration with the one outbound to measure, and the runner it needs, if any. */
internal data class DesktopLatencyProbe(
    val configJson: String,
    val amneziaWg: DesktopAmneziaWgBridge? = null,
)

private const val ProbeOutboundTag = "probe"

/**
 * Builds the configuration for measuring [server] alone. The isolated Core instance dials the
 * first outbound, so no inbounds or routing are needed. An AmneziaWG server is reached through the
 * loopback SOCKS bridge of the native runner, exactly as in a real connection.
 */
internal fun buildDesktopLatencyProbe(
    server: ProxyServer<*>,
    amneziaWgDnsServers: List<String> = listOf(VpnDefaults.IPV4_DNS),
): DesktopLatencyProbe {
    require(server !is Custom && server !is StrategyGroup && !server.isCompositeProxyServer()) {
        "Only a server with a single outbound can be probed on its own"
    }
    val issues = server.validateFull()
    require(issues.isEmpty()) { "Server is invalid: ${issues.joinToString { issue -> issue.error.name }}" }

    val amneziaWgPlan = (server as? AmneziaWg)?.let { awg ->
        planAmneziaWgBridge(servers = listOf(awg), dnsServers = amneziaWgDnsServers, reservedPorts = emptySet())
    }
    val outbound: JsonObject = if (amneziaWgPlan != null) {
        buildLoopbackSocksOutbound(tag = ProbeOutboundTag, port = amneziaWgPlan.socksPort)
    } else {
        server.toXrayOutbound(ProbeOutboundTag).toJsonObject()
    }
    val config = buildJsonObject {
        put("log", buildJsonObject { put("loglevel", "none") })
        put("outbounds", buildJsonArray { add(outbound) })
    }
    return DesktopLatencyProbe(configJson = config.toString(), amneziaWg = amneziaWgPlan?.toDesktopBridge())
}

/**
 * HTTP ping mode: measures a server over its real transport instead of connecting to its port, which
 * says nothing for UDP protocols. [measure] runs one probe in SKIPI Core; the native call cannot be
 * interrupted, so a probe that outlasts the timeout is reported as a timeout and left to finish.
 */
internal class DesktopHttpLatencyTester(
    private val measure: (configJson: String, url: String, amneziaWg: DesktopAmneziaWgBridge?) -> Result<Long>,
    private val amneziaWgDnsServers: () -> List<String> = { listOf(VpnDefaults.IPV4_DNS) },
) {
    private val probes = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    suspend fun test(server: ProxyServer<*>, url: String, timeoutMillis: Int): DesktopServerLatencyResult {
        val probe = try {
            buildDesktopLatencyProbe(server, amneziaWgDnsServers())
        } catch (error: IllegalArgumentException) {
            return DesktopServerLatencyResult.Error(error.message.orEmpty().ifBlank { "Server cannot be probed" }, error)
        }
        val measurement = probes.async { measure(probe.configJson, url, probe.amneziaWg) }
        val outcome = withTimeoutOrNull(timeoutMillis.toLong()) { measurement.await() }
            ?: run {
                DesktopLogger.warn("Ping", "HTTP ping timed out after $timeoutMillis ms: $url")
                return DesktopServerLatencyResult.Timeout
            }
        return outcome.fold(
            onSuccess = { delay -> DesktopServerLatencyResult.Success(delay) },
            onFailure = { error ->
                DesktopLogger.warn("Ping", "HTTP ping failed: ${error.message.orEmpty().ifBlank { error.javaClass.simpleName }}")
                DesktopServerLatencyResult.Error(
                    message = error.message.orEmpty().ifBlank { error.javaClass.simpleName },
                    cause = error,
                )
            },
        )
    }
}
