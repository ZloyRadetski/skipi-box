// Copyright 2026, Radetski
// SPDX-License-Identifier: GPL-3.0

package engine.xray

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
            return buildCustomExportConfig(server, appState)
        }
        return buildGeneratedExportConfig(appState, selectedServer)
    }

    private fun buildCustomExportConfig(
        server: Custom,
        appState: AppState,
    ): String {
        val customJson = parseCustomXrayConfigJsonObject(server.configJson)
        val inbounds = buildExportInbounds(appState)
        val cleanConfig = buildJsonObject {
            put("log", buildJsonObject { put("loglevel", "warning") })
            if (customJson["inbounds"] != null && !server.overrideInboundAndDns) {
                put("inbounds", customJson["inbounds"]!!)
            } else {
                put("inbounds", inbounds)
            }
            customJson.forEach { (key, value) ->
                if (key != "inbounds" && key != "log") {
                    put(key, value)
                }
            }
        }
        return formatCustomXrayConfigJson(cleanConfig)
    }

    private fun buildGeneratedExportConfig(
        appState: AppState,
        selectedServer: ProxyServerState,
    ): String {
        val outboundPlan = appState.buildXrayOutboundPlan(selectedServer)
        val primaryOutboundTag = outboundPlan.proxyOutbounds.firstOrNull()?.tag ?: XrayTags.PROXY

        val inbounds = buildExportInbounds(appState)
        val outbounds = buildXrayOutbounds(
            appState = appState,
            proxyOutbounds = outboundPlan.proxyOutbounds,
            primaryOutboundTag = primaryOutboundTag,
        )

        val dnsServers = (
            appState.proxyDns.filter { it.isNotBlank() && !it.startsWith("172.") } +
                listOf("1.1.1.1", "8.8.8.8")
        ).distinct()

        val dns = buildJsonObject {
            putJsonArray("servers") {
                dnsServers.forEach(::add)
            }
            put("queryStrategy", if (appState.enableIpv6) "UseIP" else "UseIPv4")
        }

        val balancer = outboundPlan.balancers.firstOrNull()
        val routing = buildJsonObject {
            put("domainStrategy", "IPIfNonMatch")
            putJsonArray("rules") {
                add(
                    buildJsonObject {
                        put("type", "field")
                        putJsonArray("ip") { add("geoip:private") }
                        put("outboundTag", XrayTags.DIRECT)
                    },
                )
                add(
                    buildJsonObject {
                        put("type", "field")
                        put("network", "tcp,udp")
                        if (balancer != null) {
                            put("balancerTag", balancer.tag)
                        } else {
                            put("outboundTag", primaryOutboundTag)
                        }
                    },
                )
            }
            if (outboundPlan.balancers.isNotEmpty()) {
                put("balancers", buildXrayBalancers(outboundPlan.balancers).toJsonObjectArray())
            }
        }

        val observatory = if (outboundPlan.observatorySelectors.isNotEmpty()) {
            buildXrayObservatory(
                selectors = outboundPlan.observatorySelectors,
                probeUrl = outboundPlan.observatoryProbeUrl,
                probeInterval = outboundPlan.observatoryProbeInterval,
                probeTimeout = outboundPlan.observatoryProbeTimeout,
            )
        } else {
            null
        }

        val config = buildJsonObject {
            put("log", buildJsonObject { put("loglevel", "warning") })
            put("dns", dns)
            put("inbounds", inbounds)
            put("outbounds", outbounds)
            put("routing", routing)
            if (observatory != null) {
                put("observatory", observatory)
            }
        }

        return XrayConfigJson.encodeToString(config) + "\n"
    }

    private fun buildExportInbounds(appState: AppState): JsonArray {
        val parsedSocksPort = appState.localProxyPort.toIntOrNull()
        val socksPort: Int = if (parsedSocksPort != null && parsedSocksPort in 1..65535) parsedSocksPort else DefaultLocalSocksPort
        val httpPort: Int = if (socksPort != DefaultLocalHttpProxyPort) DefaultLocalHttpProxyPort else 10810
        return buildJsonArray {
            add(
                buildJsonObject {
                    put("tag", "socks")
                    put("listen", "127.0.0.1")
                    put("port", socksPort)
                    put("protocol", "socks")
                    put(
                        "settings",
                        buildJsonObject {
                            put("auth", "noauth")
                            put("udp", true)
                        },
                    )
                },
            )
            add(
                buildJsonObject {
                    put("tag", "http")
                    put("listen", "127.0.0.1")
                    put("port", httpPort)
                    put("protocol", "http")
                    put(
                        "settings",
                        buildJsonObject {
                            put("allowTransparent", false)
                        },
                    )
                },
            )
        }
    }
}
