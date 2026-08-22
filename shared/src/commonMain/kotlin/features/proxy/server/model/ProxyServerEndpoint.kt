// Copyright 2026, Radetski
// SPDX-License-Identifier: GPL-3.0

package features.proxy.server.model

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
        else -> ""
    }
}

fun String.normalizedServerHost(): String {
    return trim().trim('[', ']')
}
