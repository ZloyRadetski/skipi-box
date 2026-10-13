// Copyright 2026, Radetski
// SPDX-License-Identifier: GPL-3.0

package platform

import features.proxy.server.model.Custom
import features.proxy.server.model.ProxyServer
import features.proxy.server.model.isCompositeProxyServer
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.add
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

/** Minimal Xray process configuration for a selected, single server on desktop. */
data class LocalProxyXrayConfigOptions(
    val socksPort: Int = DefaultLocalSocksPort,
    /** A separate HTTP CONNECT inbound used by Windows system-proxy integration. */
    val httpProxyPort: Int = DefaultLocalHttpProxyPort,
    /**
     * Windows Internet Settings always points to this loopback-only inbound.
     * It deliberately does not inherit [listenAddress], because SOCKS may be
     * shared with a LAN while a system proxy must never be published on it.
     */
    val httpProxyListenAddress: String = DefaultLocalHttpProxyListenAddress,
    val enableHttpProxy: Boolean = false,
    val listenAddress: String = "127.0.0.1",
    /** Blank usernames retain Android's unauthenticated SOCKS settings. */
    val username: String = "",
    val password: String = "",
    val logLevel: String = "warning",
    val dnsServers: List<String> = emptyList(),
    val includeDefaultRouting: Boolean = false,
)

object LocalProxyXrayConfigFactory {
    const val ProxyTag = "skipi-proxy"
    const val SocksInboundTag = "skipi-socks"
    const val HttpInboundTag = "skipi-http"
    const val DirectTag = "direct"
    const val BlockTag = "block"

    /** Builds the SOCKS inbound with the exact fields used by Android's local listener. */
    fun buildSocksInbound(
        options: LocalProxyXrayConfigOptions = LocalProxyXrayConfigOptions(),
        tag: String = SocksInboundTag,
    ) = buildJsonObject {
        require(options.socksPort in 1..65_535) { "Local SOCKS port must be in 1..65535" }
        require(options.listenAddress.isNotBlank()) { "Local SOCKS listen address must not be blank" }
        put("tag", tag)
        put("listen", options.listenAddress)
        put("port", options.socksPort)
        put("protocol", "socks")
        put(
            "settings",
            buildJsonObject {
                put("auth", if (options.username.isBlank()) "noauth" else "password")
                put("udp", true)
                put("userLevel", 0)
                if (options.listenAddress == "127.0.0.1") put("ip", "127.0.0.1")
                if (options.username.isNotBlank()) {
                    put(
                        "users",
                        buildJsonArray {
                            add(
                                buildJsonObject {
                                    put("user", options.username)
                                    put("pass", options.password)
                                },
                            )
                        },
                    )
                }
            },
        )
    }

    fun build(
        server: ProxyServer<*>,
        options: LocalProxyXrayConfigOptions = LocalProxyXrayConfigOptions(),
    ): String {
        require(options.socksPort in 1..65_535) { "Local SOCKS port must be in 1..65535" }
        if (options.enableHttpProxy) {
            require(options.httpProxyPort in 1..65_535) { "Local HTTP proxy port must be in 1..65535" }
            require(options.httpProxyPort != options.socksPort) {
                "Local HTTP proxy port must differ from the SOCKS port"
            }
            require(options.httpProxyListenAddress == DefaultLocalHttpProxyListenAddress) {
                "Windows system HTTP proxy must listen on $DefaultLocalHttpProxyListenAddress"
            }
        }
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
            if (options.dnsServers.isNotEmpty()) {
                put(
                    "dns",
                    buildJsonObject {
                        put(
                            "servers",
                            buildJsonArray {
                                options.dnsServers.forEach { add(JsonPrimitive(it)) }
                            },
                        )
                        put("queryStrategy", "UseIPv4")
                    },
                )
            }
            put(
                "inbounds",
                buildJsonArray {
                    add(buildSocksInbound(options))
                    if (options.enableHttpProxy) {
                        add(
                            buildJsonObject {
                                put("tag", HttpInboundTag)
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
            if (options.includeDefaultRouting) {
                put(
                    "routing",
                    buildJsonObject {
                        put("domainStrategy", "IPIfNonMatch")
                        put(
                            "rules",
                            buildJsonArray {
                                add(
                                    buildJsonObject {
                                        put("type", "field")
                                        put(
                                            "ip",
                                            buildJsonArray { add(JsonPrimitive("geoip:private")) },
                                        )
                                        put("outboundTag", DirectTag)
                                    },
                                )
                                add(
                                    buildJsonObject {
                                        put("type", "field")
                                        put("network", "tcp,udp")
                                        put("outboundTag", ProxyTag)
                                    },
                                )
                            },
                        )
                    },
                )
            }
        }
        return XrayConfigJson.encodeToString(config) + "\n"
    }

    fun buildStandaloneConfig(
        server: ProxyServer<*>,
        options: LocalProxyXrayConfigOptions = LocalProxyXrayConfigOptions(
            enableHttpProxy = true,
            dnsServers = listOf("1.1.1.1", "8.8.8.8"),
            includeDefaultRouting = true,
        ),
    ): String = build(server, options)
}

const val DefaultLocalSocksPort = 10_808
const val DefaultLocalHttpProxyPort = 10_809
const val DefaultLocalHttpProxyListenAddress = "127.0.0.1"

private val XrayConfigJson = Json {
    encodeDefaults = true
}
