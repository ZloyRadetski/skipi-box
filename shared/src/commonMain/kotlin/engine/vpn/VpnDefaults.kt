// Copyright 2026, Radetski
// SPDX-License-Identifier: GPL-3.0

package engine.vpn

object VpnDefaults {
    const val LOCAL_PROXY_PORT = 10_808
    const val VPN_APPEND_HTTP_PROXY_FALLBACK_PORT = 10_809
    const val MTU = 1500
    const val MTU_MIN = 1280
    const val MTU_MAX = 65_535
    const val IPV4_DNS = "8.8.8.8"
    val PROXY_DNS_SERVERS = listOf("https://8.8.8.8/dns-query")
    val DIRECT_DNS_SERVERS = listOf("https://1.1.1.1/dns-query", "https://8.8.8.8/dns-query")
    const val IPV4_CIDR = "172.19.0.1/30"
    const val IPV6_CIDR = "fdfe:dcba:9876::1/126"
    const val TCP_KEEP_ALIVE_INTERVAL = "60"
    const val TCP_KEEP_ALIVE_INTERVAL_MIN = 1
    const val TCP_KEEP_ALIVE_INTERVAL_MAX = 300
    const val TCP_USER_TIMEOUT = "10000"
    const val TCP_USER_TIMEOUT_MIN = 1000
    const val TCP_USER_TIMEOUT_MAX = 120_000
}
