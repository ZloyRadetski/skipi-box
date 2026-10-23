// Copyright 2026, Radetski
// SPDX-License-Identifier: GPL-3.0

package app.skipi.desktop

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

/** Desktop entry point shared by preview, runtime start plans, and FullJson export. */
internal fun buildDesktopSelectedServerConfig(
    serverLibrary: DesktopServerLibrary,
    configLibrary: DesktopConfigLibrary,
    routing: RoutingConfigRecord,
    options: LocalProxyXrayConfigOptions,
    trafficConfigContentById: Map<Int, String>,
    subscriptionPingUrl: String,
    latencyByServerId: Map<Int, DesktopServerLatencyResult>,
): Result<String>? {
    val selectedStored = serverLibrary.selectedServerId
        ?.let { selectedId -> serverLibrary.servers.firstOrNull { it.id == selectedId } }
    val selectedServerResult = selectedStored?.decode()
    val selectedServer = selectedServerResult?.getOrNull()
    if (selectedServer is Custom) {
        return checkNotNull(selectedServerResult).mapCatching {
            buildDesktopCustomXrayConfig(selectedServer, options)
        }
    }

    val activeProfile = configLibrary.selectedConfigId
        ?.let { selectedId -> configLibrary.configs.firstOrNull { profile -> profile.id == selectedId } }
    if (activeProfile != null) {
        return runCatching {
            DesktopTrafficProfileXrayConfigFactory.build(
                profile = activeProfile,
                serverLibrary = serverLibrary,
                options = options,
            )
        }
    }

    if (selectedStored == null) return null
    return checkNotNull(selectedServerResult).mapCatching {
        DesktopSelectedServerXrayConfigFactory.build(
            serverLibrary = serverLibrary,
            routing = routing,
            trafficConfigContentById = trafficConfigContentById,
            options = options,
            subscriptionPingUrl = subscriptionPingUrl,
            latencyByServerId = latencyByServerId,
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
                LocalProxyXrayConfigFactory.buildSocksInbound(options).forEach { (key, value) ->
                    put(key, value)
                }
                put(
                    "sniffing",
                    buildJsonObject {
                        put("enabled", true)
                        put(
                            "destOverride",
                            buildJsonArray {
                                add(JsonPrimitive("http"))
                                add(JsonPrimitive("tls"))
                                add(JsonPrimitive("fakedns"))
                            },
                        )
                    },
                )
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
        put(
            "log",
            buildJsonObject {
                put("loglevel", options.logLevel)
            },
        )
        put("inbounds", inbounds)
        customJson.forEach { (key, value) ->
            if (key != "inbounds" && key != "log") {
                put(key, value)
            }
        }
    }
    return formatCustomXrayConfigJson(cleanConfig)
}
