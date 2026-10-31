// Copyright 2026, Radetski
// SPDX-License-Identifier: GPL-3.0

package app.skipi.desktop

import engine.xray.XrayRouteTarget
import engine.xray.XrayRouteTargetKind
import engine.xray.startupProxyServerDnsDomains
import app.skipi.app.model.RoutingConfigRecord
import app.skipi.app.proxy.ProxyServerRecord
import app.skipi.app.proxy.XrayOutboundPlanner
import app.skipi.app.proxy.XrayOutboundPlanningInput
import engine.xray.XrayOutboundRenderingOptions
import engine.xray.XrayResourceRoutingRuleValidator
import engine.xray.XrayRoutingRequest
import engine.xray.XrayTags
import engine.xray.buildXrayBalancers
import engine.xray.planXrayRouting
import engine.xray.validateXrayExternalDomainResources
import features.routing.usecase.GeoDatParser
import engine.vpn.VpnDefaults
import engine.vpn.planAmneziaWgBridge
import engine.vpn.withAmneziaWgBridge
import features.proxy.server.model.AmneziaWg
import features.proxy.server.model.Custom
import features.proxy.server.model.ProxyServer
import features.proxy.server.model.stripLeadingCountryFlag
import platform.LocalProxyXrayConfigFactory
import platform.LocalProxyXrayConfigOptions
import java.nio.file.Path
import engine.xray.XrayConfigJson

/** Builds a local listener config from the selected row and Android's shared proxy planner. */
object DesktopSelectedServerXrayConfigFactory {
    fun build(
        serverLibrary: DesktopServerLibrary,
        routing: RoutingConfigRecord,
        trafficConfigContentById: Map<Int, String>,
        options: LocalProxyXrayConfigOptions,
        subscriptionPingUrl: String,
        latencyByServerId: Map<Int, DesktopServerLatencyResult>,
        routingDataDirectory: Path? = null,
    ): Result<String>? = buildGenerated(
        serverLibrary = serverLibrary,
        routing = routing,
        trafficConfigContentById = trafficConfigContentById,
        options = options,
        subscriptionPingUrl = subscriptionPingUrl,
        latencyByServerId = latencyByServerId,
        routingDataDirectory = routingDataDirectory,
    )?.map { generated -> generated.json }

    /** Builds the configuration and the AmneziaWG runner it needs started before Core, if any. */
    internal fun buildGenerated(
        serverLibrary: DesktopServerLibrary,
        routing: RoutingConfigRecord,
        trafficConfigContentById: Map<Int, String>,
        options: LocalProxyXrayConfigOptions,
        subscriptionPingUrl: String,
        latencyByServerId: Map<Int, DesktopServerLatencyResult>,
        routingDataDirectory: Path? = null,
        amneziaWgDnsServers: List<String> = listOf(VpnDefaults.IPV4_DNS),
    ): Result<DesktopGeneratedXrayConfig>? {
        val selectedId = serverLibrary.selectedServerId ?: return null
        val selectedStored = serverLibrary.servers.firstOrNull { stored -> stored.id == selectedId } ?: return null
        return selectedStored.decode().mapCatching { selectedServer ->
            LocalProxyXrayConfigFactory.validateOptions(options)
            validateServer(selectedServer)
            if (selectedServer is Custom) {
                return@mapCatching DesktopGeneratedXrayConfig(buildDesktopCustomXrayConfig(selectedServer, options))
            }
            routingDataDirectory?.let { dataDirectory ->
                validateXrayExternalDomainResources(routing.rules) { fileName ->
                    val file = dataDirectory.resolve(fileName).toFile()
                    file.isFile && file.length() > 0
                }
            }

            val hasResourceTag: ((String, String, String) -> Boolean)? = routingDataDirectory?.let { dataDirectory ->
                { fileName, tag, _ ->
                    val file = dataDirectory.resolve(fileName).toFile()
                    file.isFile && file.length() > 0 && GeoDatParser.parseTags(file)
                        .any { resourceTag -> resourceTag.equals(tag, ignoreCase = true) }
                }
            }

            val records = serverLibrary.servers.mapNotNull { stored ->
                val server = if (stored === selectedStored) {
                    selectedServer
                } else {
                    stored.decode().getOrNull()
                } ?: return@mapNotNull null
                ProxyServerRecord(
                    id = stored.id,
                    groupId = stored.subscriptionId ?: DesktopProxyGroupIds.DefaultManualSubscriptionId,
                    server = server,
                    latency = latencyByServerId[stored.id].toPlannerLatency(),
                )
            }
            val selectedRecord = records.first { record -> record.id == selectedId }
            val rawPlan = XrayOutboundPlanner.plan(
                XrayOutboundPlanningInput(
                    selectedServer = selectedRecord,
                    servers = records,
                    routeRules = routing.rules,
                    defaultRouteOutboundTag = routing.defaultOutboundTag,
                    shadowrocketPolicyGroups = emptyList(),
                    trafficConfigContentById = trafficConfigContentById,
                    enableLocalDns = false,
                    enableFragment = false,
                    subscriptionPingUrl = subscriptionPingUrl,
                    stripLeadingFlag = { value -> value.stripLeadingCountryFlag() },
                ),
            )
            // AmneziaWG runs in Core's native runner; Xray reaches it through a loopback SOCKS outbound.
            val amneziaWgPlan = planAmneziaWgBridge(
                servers = rawPlan.proxyOutbounds.mapNotNull { it.server as? AmneziaWg },
                dnsServers = amneziaWgDnsServers,
                reservedPorts = options.reservedLocalPorts(),
            )
            val plan = amneziaWgPlan?.let(rawPlan::withAmneziaWgBridge) ?: rawPlan
            val ruleValidator = XrayResourceRoutingRuleValidator(hasResourceTag = hasResourceTag)
            val dnsPlan = planDesktopXrayDns(
                inputs = DesktopDnsInputs(),
                startupProxyServerDomains = plan.proxyOutbounds.startupProxyServerDnsDomains(),
                ruleValidator = ruleValidator,
            )
            val routeTargets = plan.routeTargets +
                (XrayTags.DIRECT to XrayRouteTarget(XrayTags.DIRECT, XrayRouteTargetKind.Outbound))
            val routingPlan = planXrayRouting(
                request = XrayRoutingRequest(
                    routeDomainStrategy = routing.domainStrategy,
                    routeRules = routing.rules,
                    defaultRouteOutboundTag = routing.defaultOutboundTag,
                    routeTargets = routeTargets,
                    balancers = buildXrayBalancers(plan.balancers),
                    enableLocalDns = false,
                    routeProxyDns = dnsPlan.routingOptions.routeProxyDns,
                    routeDirectDns = dnsPlan.routingOptions.routeDirectDns,
                    dnsHijackInboundTags = emptyList(),
                ),
                ruleValidator = ruleValidator,
            )
            plan.proxyOutbounds.forEach { outbound -> outbound.server?.let(::validateServer) }

            val json = LocalProxyXrayConfigFactory.buildFromPlan(
                plan = plan,
                options = options,
                renderingOptions = XrayOutboundRenderingOptions(
                    primaryOutboundTag = plan.routeTargets[XrayTags.PROXY]?.tag ?: XrayTags.PROXY,
                    enableLocalDns = false,
                    dnsPlan = dnsPlan,
                    dnsProxyOutboundTag = null,
                    transport = null,
                    mux = null,
                    fragment = null,
                ),
                routingPlan = routingPlan,
            )
            DesktopGeneratedXrayConfig(json = json, amneziaWg = amneziaWgPlan?.toDesktopBridge())
        }
    }

    private fun validateServer(server: ProxyServer<*>) {
        val validationIssues = server.validateFull()
        require(validationIssues.isEmpty()) {
            "Selected server is invalid: ${validationIssues.joinToString { issue -> issue.error.name }}"
        }
    }

}

internal fun DesktopServerLatencyResult?.toPlannerLatency(): String = when (this) {
    is DesktopServerLatencyResult.Success -> "$milliseconds ms"
    DesktopServerLatencyResult.Timeout -> "Timeout"
    is DesktopServerLatencyResult.Error -> "Failed"
    null -> ""
}
