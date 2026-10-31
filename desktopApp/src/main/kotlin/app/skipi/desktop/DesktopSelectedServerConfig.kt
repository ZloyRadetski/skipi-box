// Copyright 2026, Radetski
// SPDX-License-Identifier: GPL-3.0

package app.skipi.desktop

import platform.buildLogJson
import engine.xray.XraySniffingOptions
import platform.withSniffing
import app.skipi.app.model.RoutingConfigRecord
import features.proxy.server.model.Custom
import features.proxy.server.model.formatCustomXrayConfigJson
import features.proxy.server.model.parseCustomXrayConfigJsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonArray
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

/** Raw Custom entries retain their established Desktop-owned inbound adapter. */
internal fun buildDesktopCustomXrayConfig(
    server: Custom,
    options: LocalProxyXrayConfigOptions,
): String {
    val customJson = parseCustomXrayConfigJsonObject(server.configJson)
    val inbounds = buildJsonArray {
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
    val cleanConfig = buildJsonObject {
        put("log", options.buildLogJson())
        put("inbounds", inbounds)
        customJson.forEach { (key, value) ->
            if (key != "inbounds" && key != "log") {
                put(key, value)
            }
        }
    }
    return formatCustomXrayConfigJson(cleanConfig)
}
