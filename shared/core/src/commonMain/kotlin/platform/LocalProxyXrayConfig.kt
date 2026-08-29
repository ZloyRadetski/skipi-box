// Copyright 2026, Radetski
// SPDX-License-Identifier: GPL-3.0

package platform

import features.proxy.server.model.Custom
import features.proxy.server.model.ProxyServer
import features.proxy.server.model.isCompositeProxyServer
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

/** Minimal Xray process configuration for a selected, single server on desktop. */
data class LocalProxyXrayConfigOptions(
    val socksPort: Int = DefaultLocalSocksPort,
    val listenAddress: String = "127.0.0.1",
    val logLevel: String = "warning",
)

object LocalProxyXrayConfigFactory {
    const val ProxyTag = "skipi-proxy"
    const val SocksInboundTag = "skipi-socks"
    const val DirectTag = "direct"
    const val BlockTag = "block"

    fun build(
        server: ProxyServer<*>,
        options: LocalProxyXrayConfigOptions = LocalProxyXrayConfigOptions(),
    ): String {
        require(options.socksPort in 1..65_535) { "Local SOCKS port must be in 1..65535" }
        require(options.listenAddress.isNotBlank()) { "Local SOCKS listen address must not be blank" }
        require(server !is Custom) { "Raw custom Xray JSON needs a dedicated desktop adapter" }
        require(!server.isCompositeProxyServer()) { "Proxy groups need a resolved desktop member before starting Xray" }

        val validationIssues = server.validateFull()
        require(validationIssues.isEmpty()) {
            "Selected server is invalid: ${validationIssues.joinToString { it.error.name }}"
        }

        val config = buildJsonObject {
            put(
                "log",
                buildJsonObject {
                    put("loglevel", options.logLevel)
                },
            )
            put(
                "inbounds",
                buildJsonArray {
                    add(
                        buildJsonObject {
                            put("tag", SocksInboundTag)
                            put("listen", options.listenAddress)
                            put("port", options.socksPort)
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
                },
            )
            put(
                "outbounds",
                buildJsonArray {
                    add(server.toXrayOutbound(ProxyTag).toJsonObject())
                    add(
                        buildJsonObject {
                            put("tag", DirectTag)
                            put("protocol", "freedom")
                        },
                    )
                    add(
                        buildJsonObject {
                            put("tag", BlockTag)
                            put("protocol", "blackhole")
                        },
                    )
                },
            )
        }
        return XrayConfigJson.encodeToString(config) + "\n"
    }
}

const val DefaultLocalSocksPort = 10_808

private val XrayConfigJson = Json {
    encodeDefaults = true
}
