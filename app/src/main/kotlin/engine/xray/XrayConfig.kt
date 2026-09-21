// Copyright 2026, Radetski
// SPDX-License-Identifier: GPL-3.0

package engine.xray

import app.AppState
import app.ProxyServerState
import features.logs.AndroidAppLogger
import features.proxy.server.model.AmneziaWg
import features.proxy.server.model.Custom
import features.proxy.server.model.OlcRtc
import engine.vpn.buildLoopbackSocksOutbound
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

        val (generated, routingPlan) = buildGeneratedXrayConfig(resolvedRequest)
        val config = generated.toJsonObject()
        return BuiltXrayConfig(
            json = encodeRuntimeXrayConfig(config),
            unappliedRules = routingPlan.unappliedRules,
        )
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
        val activeOlcRtcBridge = SkipiCoreRuntime.activeOlcRtcBridge
        val activeAmneziaWgBridge = SkipiCoreRuntime.activeAmneziaWgBridge
        val outboundPlan = if (activeOlcRtcBridge != null || activeAmneziaWgBridge != null) {
            val updatedOutbounds = rawOutboundPlan.proxyOutbounds.map { item ->
                val olc = item.server as? OlcRtc
                val awg = item.server as? AmneziaWg
                when {
                    olc != null &&
                        activeOlcRtcBridge != null &&
                        item.customOutbound == null &&
                        olc.matchesRuntimeConfig(activeOlcRtcBridge.server) -> {
                        item.copy(
                            customOutbound = buildLoopbackSocksOutbound(
                                tag = item.tag,
                                port = activeOlcRtcBridge.socksPort,
                                user = activeOlcRtcBridge.socksUser,
                                pass = activeOlcRtcBridge.socksPass,
                            ),
                        )
                    }
                    awg != null &&
                        activeAmneziaWgBridge != null &&
                        item.customOutbound == null &&
                        awg.matchesRuntimeConfig(activeAmneziaWgBridge.server) -> {
                        item.copy(
                            customOutbound = buildLoopbackSocksOutbound(
                                tag = item.tag,
                                port = activeAmneziaWgBridge.socksPort,
                            ),
                        )
                    }
                    else -> item
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

private fun buildGeneratedXrayConfig(request: XrayConfigRequest): Pair<GeneratedXrayConfig, XrayRoutingPlan> {
    val outboundPlan = request.outboundPlan ?: request.appState.buildXrayOutboundPlan(request.selectedServer)
    val startupProxyServerDomains = outboundPlan.proxyOutbounds.startupProxyServerDnsDomains()
    val dnsPlan = request.buildXrayDnsPlan(startupProxyServerDomains)
    val routingPlan = request.appState.buildXrayRoutingPlan(
        routeTargets = outboundPlan.routeTargets,
        balancers = buildXrayBalancers(outboundPlan.balancers),
        routeProxyDns = dnsPlan.routingOptions.routeProxyDns,
        routeDirectDns = dnsPlan.routingOptions.routeDirectDns,
        dnsHijackInboundTags = request.dnsHijackInboundTags,
        dataDir = request.dataDir,
    )

    val generated = GeneratedXrayConfig(
        log = request.buildXrayLogConfig(),
        dns = buildXrayDnsConfig(dnsPlan),
        inbounds = request.inbounds.toJsonObjectArray(),
        outbounds = buildXrayOutbounds(
            appState = request.appState,
            proxyOutbounds = outboundPlan.proxyOutbounds,
            primaryOutboundTag = routingPlan.primaryOutboundTag,
            dnsPlan = dnsPlan,
            dnsProxyOutboundTag = outboundPlan.dnsDialerProxyTag(),
        ),
        routing = buildXrayRouting(routingPlan),
        fakeDns = dnsPlan.fakeDns,
        observatory = buildXrayObservatory(
            selectors = outboundPlan.observatorySelectors,
            probeUrl = outboundPlan.observatoryProbeUrl,
            probeInterval = outboundPlan.observatoryProbeInterval,
            probeTimeout = outboundPlan.observatoryProbeTimeout,
        ),
        burstObservatory = null,
        collectTrafficStats = request.collectTrafficStats,
    )
    return generated to routingPlan
}

private fun buildCustomXrayConfig(
    request: XrayConfigRequest,
    server: Custom,
): String {
    check(!request.appState.enableStrictFullTunnel) {
        "Strict full-tunnel mode cannot safely rewrite a raw Xray configuration"
    }
    // Logging belongs to the application rather than a pasted raw profile.
    // Otherwise a profile's "log": {"loglevel":"none"} silently disables
    // the access/error viewer and DNS diagnostics selected in Skipi.
    val config = CustomXrayConfigRewriter.rewrite(request, server)
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
private fun encodeRuntimeXrayConfig(config: JsonObject): String {
    val json = XrayConfigJson.encodeToString(config).withSingleTrailingLf()
    AndroidAppLogger.debug(
        LogTag,
        "Generated Xray config: chars=${json.length}, fingerprint=${json.hashCode().toUInt().toString(16)}",
    )
    return json
}

private const val LogTag = "XrayConfig"
