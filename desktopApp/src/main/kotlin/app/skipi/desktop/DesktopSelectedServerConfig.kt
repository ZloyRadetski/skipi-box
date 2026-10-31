// Copyright 2026, Radetski
// SPDX-License-Identifier: GPL-3.0

package app.skipi.desktop

import platform.buildLogJson
import engine.xray.CustomXrayDnsRewriteRequest
import engine.xray.XrayResourceRoutingRuleValidator
import engine.xray.XraySniffingOptions
import engine.xray.XrayTags
import engine.xray.buildFreedomOutbound
import engine.xray.rewriteCustomXrayDnsConfig
import engine.xray.startupProxyServerHostDnsDomains
import features.proxy.server.model.customXrayConfigProxyServerHosts
import platform.withSniffing
import app.skipi.app.model.RoutingConfigRecord
import features.proxy.server.model.Custom
import features.proxy.server.model.formatCustomXrayConfigJson
import features.proxy.server.model.parseCustomXrayConfigJsonObject
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import platform.LocalProxyXrayConfigFactory
import platform.LocalProxyXrayConfigOptions
import engine.vpn.AmneziaWgBridgePlan
import engine.vpn.VpnDefaults
import java.nio.file.Path

/** A generated Core configuration and the AmneziaWG runner it needs started first, if any. */
internal data class DesktopGeneratedXrayConfig(
    val json: String,
    val amneziaWg: DesktopAmneziaWgBridge? = null,
)

/** Desktop entry point shared by preview, runtime start plans, and FullJson export. */
internal fun buildDesktopSelectedServerConfig(
    serverLibrary: DesktopServerLibrary,
    configLibrary: DesktopConfigLibrary,
    routing: RoutingConfigRecord,
    options: LocalProxyXrayConfigOptions,
    trafficConfigContentById: Map<Int, String>,
    subscriptionPingUrl: String,
    latencyByServerId: Map<Int, DesktopServerLatencyResult>,
    routingDataDirectory: Path? = null,
    /** Applies the strict full tunnel at start without rewriting stored routing; Custom configs cannot take it. */
    strictFullTunnel: Boolean = false,
): Result<String>? = buildDesktopSelectedServerConfigWithBridge(
    serverLibrary = serverLibrary,
    configLibrary = configLibrary,
    routing = routing,
    options = options,
    trafficConfigContentById = trafficConfigContentById,
    subscriptionPingUrl = subscriptionPingUrl,
    latencyByServerId = latencyByServerId,
    routingDataDirectory = routingDataDirectory,
    strictFullTunnel = strictFullTunnel,
)?.map { generated -> generated.json }

/** Like [buildDesktopSelectedServerConfig], keeping the AmneziaWG runner the configuration depends on. */
internal fun buildDesktopSelectedServerConfigWithBridge(
    serverLibrary: DesktopServerLibrary,
    configLibrary: DesktopConfigLibrary,
    routing: RoutingConfigRecord,
    options: LocalProxyXrayConfigOptions,
    trafficConfigContentById: Map<Int, String>,
    subscriptionPingUrl: String,
    latencyByServerId: Map<Int, DesktopServerLatencyResult>,
    routingDataDirectory: Path? = null,
    strictFullTunnel: Boolean = false,
    /** Raw resolvers of the runner's own netstack, as Android passes the TUN DNS. */
    amneziaWgDnsServers: List<String> = listOf(VpnDefaults.IPV4_DNS),
): Result<DesktopGeneratedXrayConfig>? {
    val selectedStored = serverLibrary.selectedServerId
        ?.let { selectedId -> serverLibrary.servers.firstOrNull { it.id == selectedId } }
    val selectedServerResult = selectedStored?.decode()
    val selectedServer = selectedServerResult?.getOrNull()
    if (selectedServer is Custom) {
        return checkNotNull(selectedServerResult).mapCatching {
            check(!strictFullTunnel) { "Strict full-tunnel mode cannot safely rewrite a raw Xray configuration" }
            DesktopGeneratedXrayConfig(buildDesktopCustomXrayConfig(selectedServer, options))
        }
    }

    val activeProfile = configLibrary.activeProfile
    if (activeProfile != null) {
        return runCatching {
            DesktopTrafficProfileXrayConfigFactory.buildGenerated(
                profile = activeProfile,
                serverLibrary = serverLibrary,
                options = options,
                configs = configLibrary.configs,
                amneziaWgDnsServers = amneziaWgDnsServers,
                strictFullTunnel = strictFullTunnel,
            )
        }
    }

    if (selectedStored == null) return null
    return checkNotNull(selectedServerResult).mapCatching {
        DesktopSelectedServerXrayConfigFactory.buildGenerated(
            serverLibrary = serverLibrary,
            routing = if (strictFullTunnel) routing.withStrictFullTunnelApplied() else routing,
            trafficConfigContentById = trafficConfigContentById,
            options = options,
            subscriptionPingUrl = subscriptionPingUrl,
            latencyByServerId = latencyByServerId,
            routingDataDirectory = routingDataDirectory,
            amneziaWgDnsServers = amneziaWgDnsServers,
        )?.getOrThrow()
            ?: error("Select a working server before connecting")
    }
}

/** Ports the runner's SOCKS bridge must not take: the listeners Core opens for the application. */
internal fun LocalProxyXrayConfigOptions.reservedLocalPorts(): Set<Int> = buildSet {
    add(socksPort)
    if (enableHttpProxy) add(httpProxyPort)
}

internal fun AmneziaWgBridgePlan.toDesktopBridge(): DesktopAmneziaWgBridge =
    DesktopAmneziaWgBridge(configJson = configJson, socksPort = socksPort)

/**
 * Raw Custom entries: the local listener always belongs to Desktop, logging to the application, and
 * with "override inbound and DNS" the application's DNS plan replaces the profile's, as on Android.
 */
internal fun buildDesktopCustomXrayConfig(
    server: Custom,
    options: LocalProxyXrayConfigOptions,
): String {
    val customJson = parseCustomXrayConfigJsonObject(server.configJson)
    val inbounds = buildList {
        add(
            buildJsonObject {
                LocalProxyXrayConfigFactory.buildSocksInbound(options)
                    .withSniffing(options.sniffing ?: XraySniffingOptions())
                    .forEach { (key, value) ->
                        put(key, value)
                    }
            },
        )
        if (options.enableHttpProxy) {
            add(
                buildJsonObject {
                    put("tag", LocalProxyXrayConfigFactory.HttpInboundTag)
                    put("listen", options.httpProxyListenAddress)
                    put("port", options.httpProxyPort)
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
    val config = if (server.overrideInboundAndDns) {
        val dnsPlan = planDesktopXrayDns(
            inputs = DesktopDnsInputs(),
            startupProxyServerDomains = customXrayConfigProxyServerHosts(server.configJson)
                .startupProxyServerHostDnsDomains(),
            ruleValidator = XrayResourceRoutingRuleValidator(hasResourceTag = null),
        )
        rewriteCustomXrayDnsConfig(
            CustomXrayDnsRewriteRequest(
                config = customJson,
                inbounds = inbounds,
                dnsPlan = dnsPlan,
                enableLocalDns = false,
                dnsHijackInboundTags = emptyList(),
                directOutbound = buildFreedomOutbound(tag = XrayTags.DIRECT, domainStrategy = "UseIP"),
            ),
        )
    } else {
        customJson
    }
    val cleanConfig = buildJsonObject {
        put("log", options.buildLogJson())
        put("inbounds", JsonArray(inbounds))
        config.forEach { (key, value) ->
            if (key != "inbounds" && key != "log") {
                put(key, value)
            }
        }
    }
    return formatCustomXrayConfigJson(cleanConfig)
}
