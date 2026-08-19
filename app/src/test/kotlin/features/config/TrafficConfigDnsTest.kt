// Copyright 2026, Radetski
// SPDX-License-Identifier: GPL-3.0

package features.config

import app.AppState
import app.withActiveTrafficConfigApplied
import engine.vpn.VpnDefaults
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class TrafficConfigDnsTest {

    @Test
    fun testParseAndSerializeDnsSettingsInTrafficConfig() {
        val confText = """
            [General]
            dns-server = 8.8.8.8,1.1.1.1
            ipv6 = true
            prefer-ipv6 = true

            [SKIPI]
            profile-name = Test DNS Config
            vpn-local-dns = true
            fake-dns = true
            direct-dns-fallback-proxy = true
            tun-dns = 1.1.1.1
            proxy-dns = https://1.1.1.1/dns-query,tls://1.0.0.1:853
            direct-dns = https://77.88.8.8/dns-query,77.88.8.1
            direct-dns-domains = geosite:cn,domain:ru
            dns-hosts = example.com:1.2.3.4
            dns-hosts = test.org:5.6.7.8
        """.trimIndent()

        val state = TrafficConfigState(
            id = 1,
            name = "Test DNS Config",
            rawConfig = confText,
        ).withSkipiSettingsReadFromRawConfig()

        val android = state.androidSettings
        assertTrue(android.enableVpnLocalDns)
        assertTrue(android.enableFakeDns)
        assertTrue(android.enableDirectDnsForProxyServerDomains)
        assertEquals("1.1.1.1", android.tunVpnDns)
        assertEquals(listOf("https://1.1.1.1/dns-query", "tls://1.0.0.1:853"), android.proxyDns)
        assertEquals(listOf("https://77.88.8.8/dns-query", "77.88.8.1"), android.directDns)
        assertEquals(listOf("geosite:cn", "domain:ru"), android.directDnsDomains)
        assertEquals(listOf("example.com:1.2.3.4", "test.org:5.6.7.8"), android.dnsHosts)

        val serializedState = state.withSkipiSettingsInRawConfig()
        val reparsedState = serializedState.withSkipiSettingsReadFromRawConfig()

        assertEquals(state.androidSettings, reparsedState.androidSettings)
    }

    @Test
    fun testWithActiveTrafficConfigAppliedProjectsDnsToAppState() {
        val config = TrafficConfigState(
            id = 42,
            name = "Active Config",
            rawConfig = "",
            androidSettings = TrafficConfigAndroidSettings(
                enableVpnLocalDns = true,
                enableFakeDns = true,
                enableDirectDnsForProxyServerDomains = true,
                tunVpnDns = "9.9.9.9",
                proxyDns = listOf("https://dns.google/dns-query"),
                directDns = listOf("https://77.88.8.8/dns-query"),
                directDnsDomains = listOf("geosite:category-gov-ru"),
                dnsHosts = listOf("myhost.local:192.168.1.1"),
            ),
        ).withSkipiSettingsInRawConfig()

        val appState = AppState(
            trafficConfigs = listOf(config),
            activeTrafficConfigId = 42,
        )

        val appliedState = appState.withActiveTrafficConfigApplied()

        assertTrue(appliedState.enableVpnLocalDns)
        assertTrue(appliedState.enableFakeDns)
        assertTrue(appliedState.enableDirectDnsForProxyServerDomains)
        assertEquals("9.9.9.9", appliedState.tunVpnDns)
        assertEquals(listOf("https://dns.google/dns-query"), appliedState.proxyDns)
        assertEquals(listOf("https://77.88.8.8/dns-query"), appliedState.directDns)
        assertEquals(listOf("geosite:category-gov-ru"), appliedState.directDnsDomains)
        assertEquals(listOf("myhost.local:192.168.1.1"), appliedState.dnsHosts)
    }
}
