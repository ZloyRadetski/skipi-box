// Copyright 2026, Radetski
// SPDX-License-Identifier: GPL-3.0

package engine.xray

import app.AppState
import features.proxy.server.model.Socks
import features.proxy.server.model.Wireguard
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import org.junit.Test
import kotlin.test.assertEquals

class XrayOutboundJsonAndroidOracleTest {

    @Test
    fun proxyOverlaysKeepFragmentPrecedenceMuxAndPrimaryReorder() {
        val actual = buildXrayOutbounds(
            appState = AppState(
                enableVpnLocalDns = false,
                enableIpv6 = true,
                enableIpv6Prefer = true,
                tunTcpKeepAliveInterval = "45",
                tunTcpUserTimeout = "3500",
                enableMux = true,
                muxConcurrency = "7",
                muxXudpConcurrency = "2",
                muxXudpProxyUdp443 = 1,
                enableFragment = true,
                fragmentPackets = "tlshello",
                fragmentLength = "5-10",
                fragmentInterval = "2-3",
            ),
            proxyOutbounds = listOf(
                XrayProxyOutboundServer(
                    tag = "proxy",
                    server = Socks(remarks = "Proxy", server = "proxy.example", port = "1080", user = "alice", password = "secret"),
                    dialerProxyTag = "hop",
                ),
                XrayProxyOutboundServer(
                    tag = "hop",
                    server = Socks(remarks = "Hop", server = "hop.example", port = "1081"),
                    allowFragment = false,
                ),
            ),
            primaryOutboundTag = "hop",
            dnsPlan = null,
            dnsProxyOutboundTag = "hop",
        )

        assertEquals(
            Json.parseToJsonElement(
                """
                [
                  {
                    "tag": "hop",
                    "protocol": "socks",
                    "settings": {"address": "hop.example", "port": 1081},
                    "streamSettings": {"sockopt": {"domainStrategy": "UseIPv6v4", "tcpKeepAliveInterval": 45, "tcpKeepAliveIdle": 45, "tcpUserTimeout": 3500}},
                    "mux": {"enabled": true, "concurrency": 7, "xudpConcurrency": 2, "xudpProxyUDP443": "allow"}
                  },
                  {
                    "tag": "proxy",
                    "protocol": "socks",
                    "settings": {"address": "proxy.example", "port": 1080, "user": "alice", "pass": "secret"},
                    "streamSettings": {"sockopt": {"domainStrategy": "UseIPv6v4", "tcpKeepAliveInterval": 45, "tcpKeepAliveIdle": 45, "tcpUserTimeout": 3500, "dialerProxy": "fragment"}},
                    "mux": {"enabled": true, "concurrency": 7, "xudpConcurrency": 2, "xudpProxyUDP443": "allow"}
                  },
                  {
                    "tag": "direct",
                    "protocol": "freedom",
                    "settings": {"domainStrategy": "UseIPv6v4"},
                    "streamSettings": {"sockopt": {"tcpKeepAliveInterval": 45, "tcpKeepAliveIdle": 45, "tcpUserTimeout": 3500}}
                  },
                  {"tag": "block", "protocol": "blackhole"},
                  {
                    "tag": "fragment",
                    "protocol": "freedom",
                    "settings": {"domainStrategy": "UseIPv6v4", "fragment": {"packets": "tlshello", "length": "5-10", "interval": "2-3"}}
                  }
                ]
                """.trimIndent(),
            ).jsonArray,
            actual,
        )
    }

    @Test
    fun wireguardReceivesItsSettingsOverlayAndNoSockoptOverlay() {
        val actual = buildXrayOutbounds(
            appState = AppState(
                enableVpnLocalDns = false,
                enableIpv6 = true,
                enableIpv6Prefer = true,
                tunTcpKeepAliveInterval = "45",
                tunTcpUserTimeout = "3500",
            ),
            proxyOutbounds = listOf(
                XrayProxyOutboundServer(
                    tag = "wg",
                    server = Wireguard(
                        remarks = "WG",
                        server = "wg.example",
                        port = "51820",
                        secretKey = "secret",
                        publicKey = "public",
                        address = "10.8.0.2/32",
                        mtu = "1420",
                        reserved = "1,2,3",
                    ),
                ),
            ),
            primaryOutboundTag = "wg",
            dnsPlan = null,
            dnsProxyOutboundTag = "wg",
        )

        assertEquals(
            Json.parseToJsonElement(
                """
                [
                  {
                    "tag": "wg",
                    "protocol": "wireguard",
                    "settings": {
                      "secretKey": "secret",
                      "address": ["10.8.0.2/32"],
                      "peers": [{"endpoint": "wg.example:51820", "publicKey": "public"}],
                      "mtu": 1420,
                      "reserved": [1, 2, 3],
                      "domainStrategy": "ForceIPv6v4",
                      "keepAlive": 45
                    }
                  },
                  {
                    "tag": "direct",
                    "protocol": "freedom",
                    "settings": {"domainStrategy": "UseIPv6v4"},
                    "streamSettings": {"sockopt": {"tcpKeepAliveInterval": 45, "tcpKeepAliveIdle": 45, "tcpUserTimeout": 3500}}
                  },
                  {"tag": "block", "protocol": "blackhole"}
                ]
                """.trimIndent(),
            ).jsonArray,
            actual,
        )
    }

    @Test
    fun localDnsUsesBothLoopbackOutboundsAndTheSuppliedDnsPlan() {
        val actual = buildXrayOutbounds(
            appState = AppState(enableVpnLocalDns = true),
            proxyOutbounds = listOf(
                XrayProxyOutboundServer(
                    tag = "proxy",
                    server = Socks(remarks = "Proxy", server = "proxy.example", port = "1080"),
                ),
            ),
            primaryOutboundTag = XrayTags.DEFAULT_ROUTE_LOOPBACK,
            dnsPlan = dnsPlan(),
            dnsProxyOutboundTag = XrayTags.DNS_PROXY_LOOPBACK,
        )

        assertEquals(
            Json.parseToJsonElement(
                """
                [
                  {
                    "tag": "skipi-internal-default-route-loopback",
                    "protocol": "loopback",
                    "settings": {"inboundTag": "skipi-internal-default-route-loopback-in"}
                  },
                  {
                    "tag": "skipi-internal-dns-proxy-loopback",
                    "protocol": "loopback",
                    "settings": {"inboundTag": "skipi-internal-dns-proxy-loopback-in"}
                  },
                  {
                    "tag": "proxy",
                    "protocol": "socks",
                    "settings": {"address": "proxy.example", "port": 1080},
                    "streamSettings": {"sockopt": {"domainStrategy": "UseIPv4", "tcpKeepAliveInterval": 60, "tcpKeepAliveIdle": 60, "tcpUserTimeout": 10000}}
                  },
                  {
                    "tag": "direct",
                    "protocol": "freedom",
                    "settings": {"domainStrategy": "UseIPv4"},
                    "streamSettings": {"sockopt": {"tcpKeepAliveInterval": 60, "tcpKeepAliveIdle": 60, "tcpUserTimeout": 10000}}
                  },
                  {"tag": "block", "protocol": "blackhole"},
                  {
                    "tag": "dns-out",
                    "protocol": "dns",
                    "settings": {
                      "rewriteNetwork": "tcp",
                      "rewriteAddress": "9.9.9.9",
                      "rewritePort": 5353,
                      "rules": [{"action": "hijack", "qType": "1,28"}, {"action": "direct"}]
                    },
                    "streamSettings": {"sockopt": {"dialerProxy": "skipi-internal-dns-proxy-loopback"}}
                  }
                ]
                """.trimIndent(),
            ).jsonArray,
            actual,
        )
    }

    @Test
    fun disabledLocalDnsOmitsLoopsAndDnsOutboundEvenWhenGivenAPlan() {
        val actual = buildXrayOutbounds(
            appState = AppState(enableVpnLocalDns = false),
            proxyOutbounds = emptyList(),
            primaryOutboundTag = XrayTags.PROXY,
            dnsPlan = dnsPlan(),
            dnsProxyOutboundTag = XrayTags.DNS_PROXY_LOOPBACK,
        )

        assertEquals(
            Json.parseToJsonElement(
                """
                [
                  {
                    "tag": "direct",
                    "protocol": "freedom",
                    "settings": {"domainStrategy": "UseIPv4"},
                    "streamSettings": {"sockopt": {"tcpKeepAliveInterval": 60, "tcpKeepAliveIdle": 60, "tcpUserTimeout": 10000}}
                  },
                  {"tag": "block", "protocol": "blackhole"}
                ]
                """.trimIndent(),
            ).jsonArray,
            actual,
        )
    }

    private fun dnsPlan() = XrayDnsPlan(
        servers = Json.parseToJsonElement("[\"1.1.1.1\"]").jsonArray,
        queryStrategy = "UseIPv4",
        tag = XrayTags.PROXY_DNS,
        hosts = Json.parseToJsonElement("{}").jsonObject,
        fakeDns = null,
        nonIpQueryFallback = XrayDnsTcpFallback("9.9.9.9", 5353),
        routingOptions = XrayDnsRoutingOptions(routeProxyDns = true, routeDirectDns = false),
    )
}
