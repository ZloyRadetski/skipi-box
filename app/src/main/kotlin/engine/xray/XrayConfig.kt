// Copyright 2026, Radetski
// SPDX-License-Identifier: GPL-3.0

package engine.xray

import app.AppState
import app.ProxyServerState
import features.logs.AndroidAppLogger
import features.proxy.server.model.Custom
import features.proxy.server.model.ProxyServer
import kotlinx.serialization.json.JsonObject

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
    val statsApiConfig: XrayStatsApiConfig? = null,
    /**
     * The VPN startup path already needs this plan to derive direct-DNS hosts.
     * Reusing it here avoids walking every balancer member a second time.
     */
    val outboundPlan: XrayOutboundPlan? = null,
)

internal data class XrayProxyOutboundServer(
    val tag: String,
    val server: ProxyServer<*>? = null,
    /** A primary outbound extracted from a simple raw Xray/JSON server. */
    val customOutbound: JsonObject? = null,
    val dialerProxyTag: String? = null,
    val allowFragment: Boolean = true,
)

internal object XrayConfigFactory {
    fun buildXrayConfig(request: XrayConfigRequest): String {
        val customServer = request.selectedServer.server as? Custom
        if (customServer != null) {
            return buildCustomXrayConfig(request, customServer)
        }

        val config = buildGeneratedXrayConfig(request).toJsonObject()
        return encodeRuntimeXrayConfig(config)
    }
}

internal object XraySpeedTestConfigFactory {
    fun buildXraySpeedTestConfig(request: XrayConfigRequest): String {
        val customServer = request.selectedServer.server as? Custom
        if (customServer != null) {
            return buildCustomXrayConfig(request, customServer)
        }

        val speedTestState = request.appState.copy(enableMux = false)
        val outboundPlan = speedTestState.buildXrayOutboundPlan(request.selectedServer)
        val startupProxyServerDomains = if (speedTestState.enableDirectDnsForProxyServerDomains) {
            outboundPlan.proxyOutbounds.startupProxyServerDnsDomains()
        } else {
            emptyList()
        }
        val dnsPlan = request.copy(appState = speedTestState).buildXrayDnsPlan(startupProxyServerDomains)
        val routingPlan = speedTestState.buildXrayRoutingPlan(
            routeTargets = outboundPlan.routeTargets,
            balancers = buildXrayBalancers(outboundPlan.balancers),
            routeProxyDns = dnsPlan.routingOptions.routeProxyDns,
            routeDirectDns = dnsPlan.routingOptions.routeDirectDns,
            dnsHijackInboundTags = emptyList(),
            dataDir = request.dataDir,
        )

        return GeneratedXrayConfig(
            log = request.copy(appState = speedTestState).buildXrayLogConfig(),
            dns = buildXrayDnsConfig(dnsPlan),
            inbounds = emptyList<JsonObject>().toJsonObjectArray(),
            outbounds = buildXrayOutbounds(
                appState = speedTestState,
                proxyOutbounds = outboundPlan.proxyOutbounds,
                primaryOutboundTag = routingPlan.primaryOutboundTag ?: XrayTags.PROXY,
            ),
            routing = buildXrayRouting(routingPlan),
            fakeDns = dnsPlan.fakeDns,
            observatory = null,
            burstObservatory = null,
            statsApiConfig = null,
        ).encodeToJsonString()
    }
}

private fun buildGeneratedXrayConfig(request: XrayConfigRequest): GeneratedXrayConfig {
    val outboundPlan = request.outboundPlan ?: request.appState.buildXrayOutboundPlan(request.selectedServer)
    val startupProxyServerDomains = if (request.appState.enableDirectDnsForProxyServerDomains) {
        outboundPlan.proxyOutbounds.startupProxyServerDnsDomains()
    } else {
        emptyList()
    }
    val dnsPlan = request.buildXrayDnsPlan(startupProxyServerDomains)
    val routingPlan = request.appState.buildXrayRoutingPlan(
        routeTargets = outboundPlan.routeTargets,
        balancers = buildXrayBalancers(outboundPlan.balancers),
        routeProxyDns = dnsPlan.routingOptions.routeProxyDns,
        routeDirectDns = dnsPlan.routingOptions.routeDirectDns,
        dnsHijackInboundTags = request.dnsHijackInboundTags,
        dataDir = request.dataDir,
    )

    return GeneratedXrayConfig(
        log = request.buildXrayLogConfig(),
        dns = buildXrayDnsConfig(dnsPlan),
        inbounds = request.inbounds.toJsonObjectArray(),
        outbounds = buildXrayOutbounds(
            appState = request.appState,
            proxyOutbounds = outboundPlan.proxyOutbounds,
            primaryOutboundTag = routingPlan.primaryOutboundTag,
        ),
        routing = buildXrayRouting(routingPlan),
        fakeDns = dnsPlan.fakeDns,
        observatory = buildXrayObservatory(
            selectors = outboundPlan.observatorySelectors,
            probeUrl = outboundPlan.observatoryProbeUrl,
            probeInterval = outboundPlan.observatoryProbeInterval,
        ),
        burstObservatory = null,
        statsApiConfig = request.statsApiConfig,
    )
}

private fun buildCustomXrayConfig(
    request: XrayConfigRequest,
    server: Custom,
): String {
    val config = CustomXrayConfigRewriter.rewrite(request, server)
        .withXrayStatsApiConfig(request.statsApiConfig)
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
