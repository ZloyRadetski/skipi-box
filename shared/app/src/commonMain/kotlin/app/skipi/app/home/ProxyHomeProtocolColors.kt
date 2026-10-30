// Copyright 2026, Radetski
// SPDX-License-Identifier: GPL-3.0

package app.skipi.app.home

/**
 * The user's per-protocol chip colors (packed ARGB) keyed by protocol family. Only configured
 * protocols appear, so every other chip keeps the theme's light/dark default.
 */
fun proxyHomeProtocolColors(
    vless: Long? = null,
    vmess: Long? = null,
    hysteria2: Long? = null,
    trojan: Long? = null,
    shadowsocks: Long? = null,
    wireguard: Long? = null,
    socks: Long? = null,
    http: Long? = null,
    strategy: Long? = null,
    chain: Long? = null,
    json: Long? = null,
): Map<String, Long> = buildMap {
    vless?.let { put("vless", it) }
    vmess?.let { put("vmess", it) }
    hysteria2?.let { put("hysteria2", it) }
    trojan?.let { put("trojan", it) }
    shadowsocks?.let { put("shadowsocks", it) }
    wireguard?.let { put("wireguard", it) }
    socks?.let { put("socks", it) }
    http?.let { put("http", it) }
    strategy?.let { put("strategy", it) }
    chain?.let { put("chain", it) }
    json?.let { put("json", it) }
}
