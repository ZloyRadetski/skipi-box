// Copyright 2026, Radetski
// SPDX-License-Identifier: GPL-3.0

package features.proxy.server.parser

import features.proxy.server.model.Hysteria2
import features.proxy.server.model.ProxyServer
import features.proxy.server.model.Shadowsocks
import features.proxy.server.model.Trojan
import features.proxy.server.model.VLESS
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
}
