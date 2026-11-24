// Copyright 2026, Radetski
// SPDX-License-Identifier: GPL-3.0

package engine.xray

import app.AppState
import app.ProxyServerState
import app.effectiveLocalDnsEnabled
import features.logs.AppLog
import features.proxy.server.model.AmneziaWg
import features.proxy.server.model.Custom
import engine.vpn.SkipiCoreRuntime
import engine.vpn.withStrictFullTunnelApplied
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

internal data class XrayConfigRequest(
    val appState: AppState,
    val selectedServer: ProxyServerState,
    val inbounds: List<JsonObject>,
    val coreLogPaths: XrayCoreLogPaths,
    val dataDir: String? = null,
    val proxyDnsServers: List<String> = appState.proxyDns,
    val directDnsServers: List<String> = appState.directDns,
    val directDnsDomains: List<String> = appState.directDnsDomains,
    val dnsHosts: List<String> = appState.dnsHosts,
    val dnsHijackInboundTags: List<String> = listOf(XrayTags.VPN_TUN_INBOUND),
    /** Keep in-process counters for notifications, widgets, and active-route UI. */
    val collectTrafficStats: Boolean = true,
    /**
     * The VPN startup path already needs this plan to derive direct-DNS hosts.
     * Reusing it here avoids walking every balancer member a second time.
     */
    val outboundPlan: XrayOutboundPlan? = null,
)

internal data class BuiltXrayConfig(
    val json: String,
    val unappliedRules: List<String> = emptyList(),
)

internal object XrayConfigFactory {
    fun buildXrayConfigResult(request: XrayConfigRequest): BuiltXrayConfig {
        // Keep this boundary safe for future callers that do not go through
        // VpnXrayConfigFactory (the standard VPN path applies it earlier too).
        val resolvedRequest = request.copy(
            appState = request.appState.withStrictFullTunnelApplied(),
        )
        val customServer = resolvedRequest.selectedServer.server as? Custom
        if (customServer != null) {
            return BuiltXrayConfig(json = buildCustomXrayConfig(resolvedRequest, customServer))
        }

        val generated = buildGeneratedXrayConfigResult(resolvedRequest)
        logGeneratedXrayConfig(generated.json)
        return BuiltXrayConfig(json = generated.json, unappliedRules = generated.unappliedRules)
    }

    fun buildXrayConfig(request: XrayConfigRequest): String {
        return buildXrayConfigResult(request).json
    }
}

internal object XraySpeedTestConfigFactory {
    fun buildXraySpeedTestConfig(request: XrayConfigRequest): String {
        val customServer = request.selectedServer.server as? Custom
        if (customServer != null) {
            val speedTestState = request.appState.copy(
                enableMux = false,
                enableFakeDns = false,
                enableStrictFullTunnel = false,
            )
            return buildCustomXrayConfig(
                request.copy(
                    appState = speedTestState,
                    inbounds = emptyList<JsonObject>(),
                ),
                customServer,
            )
        }

        val speedTestState = request.appState.copy(
            enableMux = false,
            enableFakeDns = false,
            enableDirectDnsForProxyServerDomains = true,
            enableStrictFullTunnel = false,
        )
        val rawOutboundPlan = request.outboundPlan ?: speedTestState.buildXrayOutboundPlan(request.selectedServer)
        val activeAmneziaWgBridge = SkipiCoreRuntime.activeAmneziaWgBridge
        val outboundPlan = if (activeAmneziaWgBridge != null) {
            val updatedOutbounds = rawOutboundPlan.proxyOutbounds.map { item ->
                val awg = item.server as? AmneziaWg
                if (
                    awg != null &&
                    item.customOutbound == null &&
                    awg.matchesRuntimeConfig(activeAmneziaWgBridge.server)
                ) {
                    item.copy(customOutbound = activeAmneziaWgBridge.loopbackOutbound(item.tag))
                } else {
                    item
                }
            }
            rawOutboundPlan.copy(proxyOutbounds = updatedOutbounds)
        } else {
            rawOutboundPlan
        }
        val primaryTag = outboundPlan.proxyOutbounds.firstOrNull()?.tag ?: XrayTags.PROXY
        val startupProxyServerDomains = outboundPlan.proxyOutbounds.startupProxyServerDnsDomains()

        val dnsPlan = request.copy(appState = speedTestState).buildXrayDnsPlan(startupProxyServerDomains)

        val routing = buildJsonObject {
            put("domainStrategy", speedTestState.routeDomainStrategy.toXrayRoutingDomainStrategy())
            put(
                "rules",
                buildJsonArray {
                    add(
                        buildJsonObject {
                            put("inboundTag", listOf(XrayTags.DIRECT_DNS).toJsonStringArray())
                            put("outboundTag", XrayTags.DIRECT)
                        },
                    )
                    add(
                        buildJsonObject {
                            put("network", "udp,tcp")
                            put("port", "53")
                            put("outboundTag", XrayTags.DIRECT)
                        },
                    )
                    if (dnsPlan.routingOptions.routeProxyDns) {
                        add(
                            buildJsonObject {
                                put("inboundTag", listOf(XrayTags.PROXY_DNS).toJsonStringArray())
                                put("outboundTag", primaryTag)
                            },
                        )
                    }
                    add(
                        buildJsonObject {
                            put("network", "tcp,udp")
                            put("outboundTag", primaryTag)
                        },
                    )
                },
            )
        }

        return GeneratedXrayConfig(
            log = request.copy(appState = speedTestState).buildXrayLogConfig(),
            dns = buildXrayDnsConfig(dnsPlan),
            inbounds = emptyList<JsonObject>().toJsonObjectArray(),
            outbounds = buildXrayOutbounds(
                appState = speedTestState,
                proxyOutbounds = outboundPlan.proxyOutbounds,
                primaryOutboundTag = primaryTag,
                dnsPlan = dnsPlan,
            ),
            routing = routing,
            fakeDns = null,
            observatory = null,
            burstObservatory = null,
            collectTrafficStats = false,
        ).encodeToJsonString()
    }
}

private fun buildGeneratedXrayConfigResult(request: XrayConfigRequest): XrayGeneratedConfigResult {
    val appState = request.appState
    val outboundPlan = request.outboundPlan ?: appState.buildXrayOutboundPlan(request.selectedServer)
    val dnsPlan = request.buildXrayDnsPlan(outboundPlan.proxyOutbounds.startupProxyServerDnsDomains())
    return buildGeneratedXrayConfig(
        XrayGeneratedConfigInput(
            outboundPlan = outboundPlan,
            dnsPlan = dnsPlan,
            log = request.buildXrayLogConfig(),
            inbounds = request.inbounds,
            routeDomainStrategy = appState.routeDomainStrategy,
            routeRules = appState.routeRules,
            defaultRouteOutboundTag = appState.defaultRouteOutboundTag,
            enableLocalDns = appState.effectiveLocalDnsEnabled,
            dnsHijackInboundTags = request.dnsHijackInboundTags,
            ruleValidator = xrayRoutingRuleValidator(request.dataDir),
            dnsProxyOutboundTag = outboundPlan.dnsDialerProxyTag(),
            transport = appState.xrayTransportOptions(),
            mux = appState.xrayMuxOptions(),
            fragment = appState.xrayFragmentOptions(),
            collectTrafficStats = request.collectTrafficStats,
        ),
    )
}

private fun buildCustomXrayConfig(
    request: XrayConfigRequest,
    server: Custom,
): String {
    // Logging belongs to the application rather than a pasted raw profile.
    // Otherwise a profile's "log": {"loglevel":"none"} silently disables
    // the access/error viewer and DNS diagnostics selected in Skipi.
    val config = CustomXrayConfigRewriter.rewrite(request, server)
        .let { rewritten ->
            if (request.appState.enableStrictFullTunnel) rewritten.withStrictFullTunnelRouting() else rewritten
        }
        .updated {
            put("log", request.buildXrayLogConfig())
        }
        .let { rewritten ->
            if (request.collectTrafficStats) rewritten.withXrayTrafficStatsConfig() else rewritten
        }
    return encodeRuntimeXrayConfig(config)
}

/**
 * Tunnel startup must not pretty-print and persist the whole generated config.
 * Besides being expensive for large balancers, that log contains credentials.
 * Keep a small fingerprint instead; explicit configuration export remains the
 * proper diagnostic path.
 */
private fun encodeRuntimeXrayConfig(config: JsonObject): String =
    XrayConfigJson.encodeToString(config).withSingleTrailingLf().also(::logGeneratedXrayConfig)

private fun logGeneratedXrayConfig(json: String) {
    AppLog.debug(
        LogTag,
        "Generated Xray config: chars=${json.length}, fingerprint=${json.hashCode().toUInt().toString(16)}",
    )
}

private const val LogTag = "XrayConfig"
