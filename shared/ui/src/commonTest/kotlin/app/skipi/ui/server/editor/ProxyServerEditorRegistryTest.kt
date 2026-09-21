// Copyright 2026, Radetski
// SPDX-License-Identifier: GPL-3.0

package app.skipi.ui.server.editor

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
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotSame

class ProxyServerEditorRegistryTest {

    @Test
    fun editableCopy_vlessClonesParms() {
        val original = VLESS().apply {
            remarks = "Original VLESS"
            server = "example.com"
            port = "443"
            id = "test-uuid"
            parms.security = "tls"
            parms.sni = "orig.example.com"
        }

        val copy = original.editableCopy() as VLESS
        assertNotSame(original, copy)
        assertNotSame(original.parms, copy.parms)
        assertEquals("Original VLESS", copy.remarks)
        assertEquals("orig.example.com", copy.parms.sni)

        copy.remarks = "Modified VLESS"
        copy.parms.sni = "modified.example.com"

        assertEquals("Original VLESS", original.remarks)
        assertEquals("orig.example.com", original.parms.sni)
    }

    @Test
    fun editableCopy_vmessClonesParms() {
        val original = VMess().apply {
            remarks = "Original VMess"
            id = "test-uuid"
            parms.type = "ws"
        }

        val copy = original.editableCopy() as VMess
        assertNotSame(original, copy)
        assertNotSame(original.parms, copy.parms)
        assertEquals("Original VMess", copy.remarks)

        copy.remarks = "Edited VMess"
        assertEquals("Original VMess", original.remarks)
    }

    @Test
    fun editableCopy_hysteria2() {
        val original = Hysteria2().apply {
            remarks = "Hy2"
            server = "hy2.example.com"
            port = "443"
            auth = "secret"
        }

        val copy = original.editableCopy() as Hysteria2
        assertNotSame(original, copy)
        copy.remarks = "Hy2 Changed"
        assertEquals("Hy2", original.remarks)
    }

    @Test
    fun editableCopy_amneziaWg() {
        val original = AmneziaWg().apply {
            remarks = "AWG"
            server = "1.2.3.4"
            port = "51820"
            jc = "4"
            jmin = "10"
            jmax = "50"
        }

        val copy = original.editableCopy() as AmneziaWg
        assertNotSame(original, copy)
        copy.jc = "8"
        assertEquals("4", original.jc)
    }

    @Test
    fun editableCopy_wireguard() {
        val original = Wireguard().apply {
            remarks = "WG"
            server = "1.2.3.4"
            port = "51820"
            secretKey = "priv"
        }

        val copy = original.editableCopy() as Wireguard
        assertNotSame(original, copy)
        copy.remarks = "WG Copy"
        assertEquals("WG", original.remarks)
    }

    @Test
    fun editableCopy_custom() {
        val original = Custom().apply {
            remarks = "Custom Xray"
            configJson = "{\"outbounds\":[]}"
        }

        val copy = original.editableCopy() as Custom
        assertNotSame(original, copy)
        copy.configJson = "{\"outbounds\":[1]}"
        assertEquals("{\"outbounds\":[]}", original.configJson)
    }

    @Test
    fun editableCopy_strategyGroup() {
        val original = StrategyGroup().apply {
            remarks = "Auto Select"
            proxyServerIds = listOf(1, 2, 3)
        }

        val copy = original.editableCopy() as StrategyGroup
        assertNotSame(original, copy)
        copy.remarks = "Least Ping"
        assertEquals("Auto Select", original.remarks)
    }

    @Test
    fun editableCopy_chainProxy() {
        val original = ChainProxy().apply {
            remarks = "Chain 1"
            proxyServerIds = listOf(10, 20)
        }

        val copy = original.editableCopy() as ChainProxy
        assertNotSame(original, copy)
        copy.remarks = "Chain 2"
        assertEquals("Chain 1", original.remarks)
    }

    @Test
    fun editableCopy_olcRtc() {
        val original = OlcRtc().apply {
            remarks = "OLC"
            provider = "jitsi"
            transport = "datachannel"
        }

        val copy = original.editableCopy() as OlcRtc
        assertNotSame(original, copy)
        copy.provider = "telemost"
        assertEquals("jitsi", original.provider)
    }
}
