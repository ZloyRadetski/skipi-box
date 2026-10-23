// Copyright 2026, Radetski
// SPDX-License-Identifier: GPL-3.0

package app.skipi.desktop

import app.skipi.app.model.RoutingConfigRecord
import app.skipi.app.proxy.ProxyServerRecord
import app.skipi.app.proxy.XrayOutboundPlanner
import app.skipi.app.proxy.XrayOutboundPlanningInput
import engine.xray.XrayGeoRuleValidator
import engine.xray.XrayOutboundRenderingOptions
import engine.xray.XrayRoutingRequest
import engine.xray.XrayRoutingRuleValidator
import engine.xray.XrayTags
import engine.xray.buildXrayBalancers
import engine.xray.planXrayRouting
import features.proxy.server.model.AmneziaWg
import features.proxy.server.model.Custom
import features.proxy.server.model.OlcRtc
import features.proxy.server.model.ProxyServer
import features.proxy.server.model.stripLeadingCountryFlag
import platform.LocalProxyXrayConfigFactory
import platform.LocalProxyXrayConfigOptions

/** Builds a local listener config from the selected row and Android's shared proxy planner. */
object DesktopSelectedServerXrayConfigFactory {
    fun build(
        serverLibrary: DesktopServerLibrary,
        routing: RoutingConfigRecord,
        trafficConfigContentById: Map<Int, String>,
        options: LocalProxyXrayConfigOptions,
        subscriptionPingUrl: String,
        latencyByServerId: Map<Int, DesktopServerLatencyResult>,
    ): Result<String>? {
        val selectedId = serverLibrary.selectedServerId ?: return null
        val selectedStored = serverLibrary.servers.firstOrNull { stored -> stored.id == selectedId } ?: return null
        return selectedStored.decode().mapCatching { selectedServer ->
            LocalProxyXrayConfigFactory.validateOptions(options)
            require(selectedServer !is Custom) { "Raw custom Xray JSON needs a dedicated desktop adapter" }
            validateServer(selectedServer)

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
            val plan = XrayOutboundPlanner.plan(
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
            val routingPlan = planXrayRouting(
                request = XrayRoutingRequest(
                    routeDomainStrategy = routing.domainStrategy,
                    routeRules = routing.rules,
                    defaultRouteOutboundTag = routing.defaultOutboundTag,
                    routeTargets = plan.routeTargets,
                    balancers = buildXrayBalancers(plan.balancers),
                    enableLocalDns = false,
                    routeProxyDns = false,
                    routeDirectDns = false,
                    dnsHijackInboundTags = emptyList(),
                ),
                ruleValidator = DesktopNoResourceRoutingRuleValidator,
            )
            plan.proxyOutbounds.forEach { outbound ->
                outbound.server?.let { server ->
                    validateServer(server)
                    when (server) {
                        is OlcRtc -> error(
                            "Desktop has no OLCRTC native bridge for generated outbound '${outbound.tag}'",
                        )
                        is AmneziaWg -> error(
                            "Desktop has no AmneziaWG native bridge for generated outbound '${outbound.tag}'",
                        )
                        else -> Unit
                    }
                }
            }

            LocalProxyXrayConfigFactory.buildFromPlan(
                plan = plan,
                options = options,
                renderingOptions = XrayOutboundRenderingOptions(
                    primaryOutboundTag = plan.routeTargets[XrayTags.PROXY]?.tag ?: XrayTags.PROXY,
                    enableLocalDns = false,
                    dnsPlan = null,
                    dnsProxyOutboundTag = null,
                    transport = null,
                    mux = null,
                    fragment = null,
                ),
                routingPlan = routingPlan,
            )
        }
    }

    private fun validateServer(server: ProxyServer<*>) {
        val validationIssues = server.validateFull()
        require(validationIssues.isEmpty()) {
            "Selected server is invalid: ${validationIssues.joinToString { issue -> issue.error.name }}"
        }
    }

    private fun DesktopServerLatencyResult?.toPlannerLatency(): String = when (this) {
        is DesktopServerLatencyResult.Success -> "$milliseconds ms"
        DesktopServerLatencyResult.Timeout -> "Timeout"
        is DesktopServerLatencyResult.Error -> "Failed"
        null -> ""
    }
}

/**
 * Uses Android's null-dataDir compatibility behavior until the Desktop planner
 * receives the runtime asset directory. Core still loads global assets;
 * resource-aware filtering is a separate slice.
 */
private object DesktopNoResourceRoutingRuleValidator : XrayRoutingRuleValidator {
    override fun isDomainRuleValid(rule: String): Boolean = true

    override fun isIpRuleValid(rule: String): Boolean {
        val trimmed = rule.trim()
        if (trimmed.startsWith("geoip:", ignoreCase = true) || trimmed.startsWith("ext:", ignoreCase = true)) {
            return true
        }
        return XrayGeoRuleValidator.isIpRuleValid(trimmed) { _, _ -> false }
    }

    override fun filterValidDomainRules(rules: List<String>): List<String> = rules

    override fun filterValidIpRules(rules: List<String>): List<String> = rules
}
