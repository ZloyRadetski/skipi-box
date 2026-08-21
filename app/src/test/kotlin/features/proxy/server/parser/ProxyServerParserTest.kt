// Copyright 2026, Radetski
// SPDX-License-Identifier: GPL-3.0

package features.proxy.server.parser

import features.proxy.server.model.Hysteria2
import features.proxy.server.model.ProxyServer
import features.proxy.server.model.Shadowsocks
import features.proxy.server.model.Trojan
import features.proxy.server.model.VLESS
import features.proxy.server.model.getTransportDisplay
import features.proxy.server.model.toXrayStreamSettings
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs

class ProxyServerParserTest {

    @Test
    fun testParseVlessUrl() {
        val link = "vless://4219d973-8792-462f-8747-df766f70f137@example.com:443?security=tls&encryption=none&type=tcp&sni=example.com#MyVless"
        val server = ProxyServer.parse(link)

        assertIs<VLESS>(server)
        assertEquals("4219d973-8792-462f-8747-df766f70f137", server.id)
        assertEquals("example.com", server.server)
        assertEquals("443", server.port)
        assertEquals("MyVless", server.remarks)
    }

    @Test
    fun testParseTrojanUrl() {
        val link = "trojan://my-trojan-pass@trojan.example.com:443?security=tls&sni=trojan.example.com#MyTrojan"
        val server = ProxyServer.parse(link)

        assertIs<Trojan>(server)
        assertEquals("my-trojan-pass", server.password)
        assertEquals("trojan.example.com", server.server)
        assertEquals("443", server.port)
        assertEquals("MyTrojan", server.remarks)
    }

    @Test
    fun testParseHysteria2Url() {
        val link = "hysteria2://my-auth-pass@hy2.example.com:8443?sni=hy2.example.com#MyHy2"
        val server = ProxyServer.parse(link)

        assertIs<Hysteria2>(server)
        assertEquals("my-auth-pass", server.auth)
        assertEquals("hy2.example.com", server.server)
        assertEquals("8443", server.port)
        assertEquals("MyHy2", server.remarks)
    }

    @Test
    fun testParseShadowsocksUrl() {
        val link = "ss://Y2hhY2hhMjAtaWV0Zi1wb2x5MTMwNTpNeVBhc3N3b3Jk@ss.example.com:8388#MyShadowsocks"
        val server = ProxyServer.parse(link)

        assertIs<Shadowsocks>(server)
        assertEquals("ss.example.com", server.server)
        assertEquals("8388", server.port)
        assertEquals("chacha20-ietf-poly1305", server.method)
        assertEquals("MyPassword", server.password)
        assertEquals("MyShadowsocks", server.remarks)
    }

    @Test
    fun testParseVlessMkcpUrlAndStreamSettings() {
        val link = "vless://4219d973-8792-462f-8747-df766f70f137@nl02-mk01.tcp-reset-club.net:48007?type=kcp&headerType=srtp&seed=test-seed&mtu=1350&tti=40#VlessKcp"
        val server = ProxyServer.parse(link)

        assertIs<VLESS>(server)
        assertEquals("4219d973-8792-462f-8747-df766f70f137", server.id)
        assertEquals("nl02-mk01.tcp-reset-club.net", server.server)
        assertEquals("48007", server.port)
        assertEquals("srtp", server.parms.headerType)
        assertEquals("test-seed", server.parms.seed)
        assertEquals("1350", server.parms.mtu)
        assertEquals("40", server.parms.tti)

        val streamSettings = server.parms.toXrayStreamSettings()
        assertEquals("mkcp", streamSettings["network"]?.toString()?.replace("\"", ""))
        val kcpSettings = streamSettings["kcpSettings"] as kotlinx.serialization.json.JsonObject
        assertEquals("1350", kcpSettings["mtu"]?.toString())
        assertEquals("40", kcpSettings["tti"]?.toString())
        assertEquals("test-seed", kcpSettings["seed"]?.toString()?.replace("\"", ""))
        val header = kcpSettings["header"] as kotlinx.serialization.json.JsonObject
        assertEquals("srtp", header["type"]?.toString()?.replace("\"", ""))
        kotlin.test.assertNull(streamSettings["finalmask"])
    }

    @Test
    fun testGetTransportDisplay() {
        val vlessReality = ProxyServer.parse("vless://uuid@example.com:443?security=reality&type=tcp&sni=example.com#VlessReality")
        assertEquals("Reality", vlessReality.getTransportDisplay())

        val vlessGrpcReality = ProxyServer.parse("vless://uuid@example.com:443?security=reality&type=grpc&serviceName=grpc-service#VlessGrpcReality")
        assertEquals("gRPC • Reality", vlessGrpcReality.getTransportDisplay())

        val vlessWsTls = ProxyServer.parse("vless://uuid@example.com:443?security=tls&type=ws&path=%2Fws#VlessWs")
        assertEquals("WS • TLS", vlessWsTls.getTransportDisplay())

        val hy2 = ProxyServer.parse("hysteria2://pass@example.com:8443#Hy2")
        assertEquals("QUIC", hy2.getTransportDisplay())

        val ss = ProxyServer.parse("ss://Y2hhY2hhMjAtaWV0Zi1wb2x5MTMwNTpNeVBhc3N3b3Jk@ss.example.com:8388#SS")
        assertEquals("TCP", ss.getTransportDisplay())

        val wireguard = features.proxy.server.model.Wireguard()
        assertEquals("UDP", wireguard.getTransportDisplay())

        val strategy = features.proxy.server.model.StrategyGroup()
        assertEquals(null, strategy.getTransportDisplay())

        val jsonWsTls = features.proxy.server.model.Custom(
            remarks = "JSON WS",
            configJson = """
                {
                  "outbounds": [
                    {
                      "tag": "proxy",
                      "protocol": "vless",
                      "settings": { "vnext": [{ "address": "1.2.3.4", "port": 443 }] },
                      "streamSettings": {
                        "network": "ws",
                        "security": "tls"
                      }
                    }
                  ]
                }
            """.trimIndent()
        )
        assertEquals("WS • TLS", jsonWsTls.getTransportDisplay())

        val jsonGrpcReality = features.proxy.server.model.Custom(
            remarks = "JSON gRPC Reality",
            configJson = """
                {
                  "outbounds": [
                    {
                      "tag": "proxy",
                      "protocol": "vless",
                      "settings": { "vnext": [{ "address": "1.2.3.4", "port": 443 }] },
                      "streamSettings": {
                        "network": "grpc",
                        "security": "reality"
                      }
                    }
                  ]
                }
            """.trimIndent()
        )
        assertEquals("gRPC • Reality", jsonGrpcReality.getTransportDisplay())

        val jsonXhttp = features.proxy.server.model.Custom(
            remarks = "JSON xHTTP",
            configJson = """
                {
                  "outbounds": [
                    {
                      "tag": "proxy",
                      "protocol": "vless",
                      "streamSettings": {
                        "network": "xhttp",
                        "security": "reality"
                      }
                    }
                  ]
                }
            """.trimIndent()
        )
        assertEquals("xHTTP • Reality", jsonXhttp.getTransportDisplay())

        val jsonHttpObfs = features.proxy.server.model.Custom(
            remarks = "JSON HTTP-OBFS",
            configJson = """
                {
                  "outbounds": [
                    {
                      "tag": "proxy",
                      "protocol": "shadowsocks",
                      "streamSettings": {
                        "network": "tcp",
                        "tcpSettings": {
                          "header": { "type": "http" }
                        }
                      }
                    }
                  ]
                }
            """.trimIndent()
        )
        assertEquals("HTTP-OBFS", jsonHttpObfs.getTransportDisplay())

        val jsonWireguard = features.proxy.server.model.Custom(
            remarks = "JSON WireGuard",
            configJson = """
                {
                  "outbounds": [
                    {
                      "tag": "proxy",
                      "protocol": "wireguard"
                    }
                  ]
                }
            """.trimIndent()
        )
        assertEquals("UDP", jsonWireguard.getTransportDisplay())
    }
}
