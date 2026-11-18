// Copyright 2026, Radetski
// SPDX-License-Identifier: GPL-3.0

package features.proxy.server.usecase.importer

import features.proxy.server.model.AmneziaWg
import features.proxy.server.model.Masque
import features.proxy.server.model.ProxyServer

/** Whether a Mihomo proxy `type` can be represented by the shared server model. */
fun String.isSupportedMihomoProxyType(): Boolean = lowercase() in SupportedMihomoProxyTypes

/**
 * Converts one already-decoded Mihomo proxy object without YAML, logging or
 * networking dependencies. Callers own rejection diagnostics.
 */
fun MihomoYamlMap.toMihomoProxyServer(): ProxyServer<*> {
    return when (requiredString("type").lowercase()) {
        "http" -> toMihomoHttpProxyServer()
        "socks", "socks5" -> toMihomoSocksProxyServer()
        "ss", "shadowsocks" -> toMihomoShadowsocksProxyServer()
        "vmess" -> toMihomoVMessProxyServer()
        "vless" -> toMihomoVlessProxyServer()
        "trojan" -> toMihomoTrojanProxyServer()
        "hy2", "hysteria2" -> toMihomoHysteria2ProxyServer()
        "masque" -> toMihomoMasqueProxyServer()
        "wg", "wireguard" -> toMihomoWireguardProxyServer()
        "amneziawg", "awg" -> toMihomoAmneziaWgProxyServer()
        else -> unsupported("unsupported proxy type")
    }.also { server ->
        // These two have keys and address lists the core refuses when malformed, so they get the full check.
        val issues = if (server is AmneziaWg || server is Masque) server.validateFull() else server.validateBasic()
        if (issues.isNotEmpty()) {
            unsupported("proxy validation failed: ${issues.joinToString { it.error.name }}")
        }
    }
}

private val SupportedMihomoProxyTypes = setOf(
    "http",
    "socks",
    "socks5",
    "ss",
    "shadowsocks",
    "vmess",
    "vless",
    "trojan",
    "hy2",
    "hysteria2",
    "masque",
    "wg",
    "wireguard",
    "amneziawg",
    "awg",
)
