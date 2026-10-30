// Copyright 2026, Radetski
// SPDX-License-Identifier: GPL-3.0

package app.skipi.app.home

import kotlin.test.Test
import kotlin.test.assertEquals

class ProxyHomeProtocolColorsTest {
    @Test
    fun onlyConfiguredProtocolsAreMappedUnderTheKeysTheUiResolves() {
        val colors = proxyHomeProtocolColors(
            vless = 0xFF112233L,
            hysteria2 = 0xFF445566L,
            json = 0xFF778899L,
        )

        assertEquals(
            mapOf("vless" to 0xFF112233L, "hysteria2" to 0xFF445566L, "json" to 0xFF778899L),
            colors,
        )
    }

    @Test
    fun noCustomizationYieldsAnEmptyMapSoThemeDefaultsApply() {
        assertEquals(emptyMap(), proxyHomeProtocolColors())
    }

    @Test
    fun everyProtocolFamilyHasItsOwnKey() {
        val all = proxyHomeProtocolColors(
            vless = 1, vmess = 2, hysteria2 = 3, trojan = 4, shadowsocks = 5, wireguard = 6,
            socks = 7, http = 8, strategy = 9, chain = 10, json = 11,
        )
        assertEquals(
            setOf("vless", "vmess", "hysteria2", "trojan", "shadowsocks", "wireguard", "socks", "http", "strategy", "chain", "json"),
            all.keys,
        )
    }
}
