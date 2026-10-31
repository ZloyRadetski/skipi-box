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
import java.nio.file.Path

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
): Result<String>? {
    val selectedStored = serverLibrary.selectedServerId
        ?.let { selectedId -> serverLibrary.servers.firstOrNull { it.id == selectedId } }
    val selectedServerResult = selectedStored?.decode()
    val selectedServer = selectedServerResult?.getOrNull()
    if (selectedServer is Custom) {
        return checkNotNull(selectedServerResult).mapCatching {
            check(!strictFullTunnel) { "Strict full-tunnel mode cannot safely rewrite a raw Xray configuration" }
            buildDesktopCustomXrayConfig(selectedServer, options)
        }
    }

    val activeProfile = configLibrary.activeProfile
    if (activeProfile != null) {
        return runCatching {
            DesktopTrafficProfileXrayConfigFactory.build(
                profile = activeProfile,
                serverLibrary = serverLibrary,
                options = options,
                configs = configLibrary.configs,
            )
        }
    }

    if (selectedStored == null) return null
    return checkNotNull(selectedServerResult).mapCatching {
        DesktopSelectedServerXrayConfigFactory.build(
            serverLibrary = serverLibrary,
            routing = if (strictFullTunnel) routing.withStrictFullTunnelApplied() else routing,
            trafficConfigContentById = trafficConfigContentById,
            options = options,
            subscriptionPingUrl = subscriptionPingUrl,
            latencyByServerId = latencyByServerId,
            routingDataDirectory = routingDataDirectory,
        )?.getOrThrow()
            ?: error("Select a working server before connecting")
    }
}

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
