// Copyright 2026, Radetski
// SPDX-License-Identifier: GPL-3.0

package app.skipi.desktop

import app.skipi.app.proxy.ProxyServerRecord
import app.skipi.app.proxy.XrayOutboundPlanner
import app.skipi.app.proxy.XrayOutboundPlanningInput
import engine.xray.XrayExportConfig
import engine.xray.XrayExportInput
import features.proxy.server.model.Custom
import features.proxy.server.model.stripLeadingCountryFlag

/**
 * "Copy full JSON" for the server selected in [serverLibrary]: the shared standalone export, so
 * it matches Android and does not depend on the active profile or the running tunnel.
 */
internal fun buildDesktopFullJsonExport(
    serverLibrary: DesktopServerLibrary,
    settings: DesktopAppSettings,
    trafficConfigContentById: Map<Int, String>,
    latencyByServerId: Map<Int, DesktopServerLatencyResult>,
): Result<String> = runCatching {
    val selectedId = serverLibrary.selectedServerId ?: error("Select a working server before exporting")
    val selectedStored = serverLibrary.servers.firstOrNull { it.id == selectedId }
        ?: error("Select a working server before exporting")
    val selectedServer = selectedStored.decode().getOrThrow()
    val localProxyPort = settings.localProxyPortRaw ?: settings.localProxyPort.toString()

    if (selectedServer is Custom) {
        return@runCatching XrayExportConfig.custom(
            configJson = selectedServer.configJson,
            overrideInboundAndDns = selectedServer.overrideInboundAndDns,
            localProxyPort = localProxyPort,
        )
    }

    val records = serverLibrary.servers.mapNotNull { stored ->
        val server = stored.decode().getOrNull() ?: return@mapNotNull null
        ProxyServerRecord(
            id = stored.id,
            groupId = stored.subscriptionId ?: DesktopProxyGroupIds.DefaultManualSubscriptionId,
            server = server,
            latency = latencyByServerId[stored.id].toPlannerLatency(),
        )
    }
    val routing = settings.toRoutingConfigRecord()
    val options = settings.toLocalProxyXrayConfigOptions()
    val plan = XrayOutboundPlanner.plan(
        XrayOutboundPlanningInput(
            selectedServer = records.first { it.id == selectedId },
            servers = records,
            routeRules = routing.rules,
            defaultRouteOutboundTag = routing.defaultOutboundTag,
            shadowrocketPolicyGroups = emptyList(),
            trafficConfigContentById = trafficConfigContentById,
            enableLocalDns = false,
            enableFragment = settings.enableFragment,
            subscriptionPingUrl = settings.subscriptionPingUrl,
            stripLeadingFlag = { value -> value.stripLeadingCountryFlag() },
        ),
    )
    XrayExportConfig.generated(
        XrayExportInput(
            outboundPlan = plan,
            localProxyPort = localProxyPort,
            proxyDnsServers = DesktopDnsInputs().proxyDns,
            enableIpv6 = false,
            mux = options.mux,
            fragment = options.fragment,
        ),
    )
}
