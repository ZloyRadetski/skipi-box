// Copyright 2026, Radetski
// SPDX-License-Identifier: GPL-3.0

package features.proxy.server.usecase

import features.proxy.server.list.ProxyServerListAddAction
import features.proxy.server.model.AmneziaWg
import features.proxy.server.model.ChainProxy
import features.proxy.server.model.Custom
import features.proxy.server.model.HTTP
import features.proxy.server.model.Hysteria2
import features.proxy.server.model.OlcRtc
import features.proxy.server.model.Shadowsocks
import features.proxy.server.model.Socks
import features.proxy.server.model.StrategyGroup
import features.proxy.server.model.Trojan
import features.proxy.server.model.VLESS
import features.proxy.server.model.VMess
import features.proxy.server.model.Wireguard
import features.proxy.server.model.decodePersistedProxyServer
import features.proxy.server.model.encodePersistedProxyServer
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class ProxyServerListAddActionTest {

    @Test
    fun testCreateAmneziaWgProxyServer() {
        val server = createProxyServer(ProxyServerListAddAction.AmneziaWg)
        assertTrue(server is AmneziaWg)
        val awg = server as AmneziaWg
        assertEquals("", awg.server)
        assertEquals("", awg.port)
        assertEquals("", awg.secretKey)
        assertEquals("", awg.publicKey)
        assertEquals("Amnezia WG", awg.getInfo().protocol)

        val encoded = awg.encodePersistedProxyServer()
        val decoded = encoded.decodePersistedProxyServer()
        assertTrue(decoded is AmneziaWg)
        val decodedAwg = decoded as AmneziaWg
        assertEquals(awg.jc, decodedAwg.jc)
        assertEquals(awg.jmin, decodedAwg.jmin)
        assertEquals(awg.jmax, decodedAwg.jmax)
    }

    @Test
    fun testCreateOlcRtcProxyServer() {
        val server = createProxyServer(ProxyServerListAddAction.OlcRtc)
        assertTrue(server is OlcRtc)
        val olc = server as OlcRtc
        assertEquals("jitsi", olc.provider)
        assertEquals("datachannel", olc.transport)
        assertEquals("10808", olc.localSocksPort)
        assertEquals("OLCRTC", olc.getInfo().protocol)

        val encoded = olc.encodePersistedProxyServer()
        val decoded = encoded.decodePersistedProxyServer()
        assertTrue(decoded is OlcRtc)
        val decodedOlc = decoded as OlcRtc
        assertEquals("jitsi", decodedOlc.provider)
        assertEquals("datachannel", decodedOlc.transport)
    }

    @Test
    fun testAllManualActionsCreateExpectedProxyServers() {
        assertTrue(createProxyServer(ProxyServerListAddAction.Shadowsocks) is Shadowsocks)
        assertTrue(createProxyServer(ProxyServerListAddAction.ChainProxy) is ChainProxy)
        assertTrue(createProxyServer(ProxyServerListAddAction.StrategyGroup) is StrategyGroup)
        assertTrue(createProxyServer(ProxyServerListAddAction.HTTP) is HTTP)
        assertTrue(createProxyServer(ProxyServerListAddAction.VMess) is VMess)
        assertTrue(createProxyServer(ProxyServerListAddAction.VLESS) is VLESS)
        assertTrue(createProxyServer(ProxyServerListAddAction.Trojan) is Trojan)
        assertTrue(createProxyServer(ProxyServerListAddAction.Socks) is Socks)
        assertTrue(createProxyServer(ProxyServerListAddAction.Hysteria2) is Hysteria2)
        assertTrue(createProxyServer(ProxyServerListAddAction.Wireguard) is Wireguard)
        assertTrue(createProxyServer(ProxyServerListAddAction.AmneziaWg) is AmneziaWg)
        assertTrue(createProxyServer(ProxyServerListAddAction.OlcRtc) is OlcRtc)
        assertTrue(createProxyServer(ProxyServerListAddAction.Custom) is Custom)
    }

    @Test
    fun testImportActionsThrowError() {
        assertThrows(IllegalStateException::class.java) {
            createProxyServer(ProxyServerListAddAction.ScanQrCode)
        }
        assertThrows(IllegalStateException::class.java) {
            createProxyServer(ProxyServerListAddAction.Clipboard)
        }
        assertThrows(IllegalStateException::class.java) {
            createProxyServer(ProxyServerListAddAction.File)
        }
    }
}
