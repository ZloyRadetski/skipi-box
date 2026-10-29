// Copyright 2026, Radetski
// SPDX-License-Identifier: GPL-3.0

package engine.xray

import app.effectiveLocalDnsEnabled
import engine.xray.XrayExportConfig
import engine.xray.XrayExportInput
import app.AppState
import app.ProxyServerState
import features.proxy.server.model.Custom
import features.proxy.server.model.formatCustomXrayConfigJson
import features.proxy.server.model.parseCustomXrayConfigJsonObject
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.add
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray
import platform.DefaultLocalHttpProxyPort
import platform.DefaultLocalSocksPort

internal object XrayExportConfigFactory {

    fun build(
        appState: AppState,
        selectedServer: ProxyServerState,
    ): String {
        val server = selectedServer.server
        if (server is Custom) {
            return XrayExportConfig.custom(
                configJson = server.configJson,
                overrideInboundAndDns = server.overrideInboundAndDns,
                localProxyPort = appState.localProxyPort,
            )
        }
        return XrayExportConfig.generated(
            XrayExportInput(
                outboundPlan = appState.buildXrayOutboundPlan(selectedServer),
                localProxyPort = appState.localProxyPort,
                proxyDnsServers = appState.proxyDns,
                enableIpv6 = appState.enableIpv6,
                mux = appState.xrayMuxOptions(),
                fragment = appState.xrayFragmentOptions(),
                transport = appState.xrayTransportOptions(),
                enableLocalDns = appState.effectiveLocalDnsEnabled,
            ),
        )
    }
}
