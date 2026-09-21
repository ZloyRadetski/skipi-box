// Copyright 2026, Radetski
// SPDX-License-Identifier: GPL-3.0

package features.proxy.server.model

import engine.network.toPortOrNull

/** A validated network endpoint exposed by a portable proxy-server model. */
data class ProxyServerEndpoint(
    val host: String,
    val port: Int,
)

/**
 * Returns the direct endpoint of a server when the model has one.
 * Strategy and chain entries intentionally have no single endpoint.
 */
fun ProxyServer<*>.connectionEndpointOrNull(): ProxyServerEndpoint? {
    return when (this) {
        is HTTP -> endpoint(server, port)
        is Hysteria2 -> endpoint(server, port)
        is Shadowsocks -> endpoint(server, port)
        is Socks -> endpoint(server, port)
        is Trojan -> endpoint(server, port)
        is VLESS -> endpoint(server, port)
        is VMess -> endpoint(server, port)
        is Wireguard -> endpoint(server, port)
        is AmneziaWg -> endpoint(server, port)
        is OlcRtc -> signalingEndpoint()?.let { (host, port) -> ProxyServerEndpoint(host, port) }
        is Custom -> customXrayConfigProxyOutboundEndpoint(configJson)
            ?.let { endpoint -> ProxyServerEndpoint(endpoint.host, endpoint.port) }
        else -> null
    }
}

fun ProxyServer<*>.serverHost(): String {
    return when (this) {
        is HTTP -> server
        is Hysteria2 -> server
        is Shadowsocks -> server
        is Socks -> server
        is Trojan -> server
        is VLESS -> server
        is VMess -> server
        is Wireguard -> server
        is AmneziaWg -> server
        is OlcRtc -> signalingEndpoint()?.first.orEmpty()
        else -> ""
    }
}

fun String.normalizedServerHost(): String {
    return trim().trim('[', ']')
}

private fun endpoint(host: String, port: String): ProxyServerEndpoint? {
    val parsedPort = port.toPortOrNull() ?: return null
    return host.trim()
        .takeIf(String::isNotEmpty)
        ?.let { ProxyServerEndpoint(it, parsedPort) }
}
