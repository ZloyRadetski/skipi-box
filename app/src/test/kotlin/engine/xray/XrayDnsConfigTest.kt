// Copyright 2026, Radetski
// SPDX-License-Identifier: GPL-3.0

package engine.xray

import app.AppState
import app.ProxyServerState
import features.proxy.server.model.VLESS
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class XrayDnsConfigTest {

    @Test
    fun accepts_only_dns_transports_supported_by_bundled_xray() {
        assertTrue(isSupportedXrayDnsServer("https://dns.example/dns-query"))
        assertTrue(isSupportedXrayDnsServer("h2c://dns.example/dns-query"))
        assertTrue(isSupportedXrayDnsServer("quic+local://dns.example"))
        assertTrue(isSupportedXrayDnsServer("tcp://1.1.1.1:53"))
        assertTrue(isSupportedXrayDnsServer("localhost"))

        assertFalse(isSupportedXrayDnsServer("tls://dns.example:853"))
        assertFalse(isSupportedXrayDnsServer("tls+local://dns.example:853"))
        assertFalse(isSupportedXrayDnsServer("udp://1.1.1.1:53"))
        assertFalse(isSupportedXrayDnsServer("quic://dns.example"))
        assertFalse(isSupportedXrayDnsServer("fakedns"))
    }

    @Test
    fun planner_drops_unsupported_dns_servers_but_keeps_valid_fallbacks() {
        val state = AppState(
            proxyDns = listOf("tls://dns.example:853", "https://dns.example/dns-query", "fakedns"),
            directDns = listOf("udp://1.1.1.1:53", "1.1.1.1"),
        )

        assertEquals(
            listOf("https://dns.example/dns-query"),
            state.xrayProxyDnsServers(state.proxyDns, state.directDns),
        )
        assertEquals(listOf("1.1.1.1"), state.xrayDirectDnsServers(state.directDns))
    }

    @Test
    fun direct_dns_switch_controls_proxy_endpoint_bootstrap_domains() {
        val startupDomain = "domain:node.example"

        val disabled = AppState(enableDirectDnsForProxyServerDomains = false)
            .xrayDirectDnsDomains(
                directDnsDomains = emptyList(),
                startupProxyServerDomains = listOf(startupDomain),
            )
        val enabled = AppState(enableDirectDnsForProxyServerDomains = true)
            .xrayDirectDnsDomains(
                directDnsDomains = emptyList(),
                startupProxyServerDomains = listOf(startupDomain),
            )

        assertFalse(startupDomain in disabled)
        assertTrue(startupDomain in enabled)
    }

    @Test
    fun remote_dns_host_is_bootstrapped_through_direct_dns() {
        val domains = AppState(enableDirectDnsForProxyServerDomains = false)
            .xrayDirectDnsDomains(
                directDnsDomains = emptyList(),
                proxyDnsServers = listOf("tcp://dns.example:53", "dns.other.example", "tcp+local://dns.local:53"),
            )

        assertEquals(listOf("domain:dns.example", "domain:dns.other.example"), domains)
    }

    @Test
    fun same_proxy_and_direct_dns_uses_system_for_its_bootstrap_lookup() {
        val nullsDoh = "https://dns.nullsproxy.com/dns-query"
        val bootstrapDomains = systemDnsBootstrapDomains(
            proxyDnsServers = listOf(nullsDoh),
            directDnsServers = listOf(nullsDoh),
        )
        val directDomains = AppState(enableDirectDnsForProxyServerDomains = false)
            .xrayDirectDnsDomains(
                directDnsDomains = emptyList(),
                proxyDnsServers = listOf(nullsDoh),
                systemBootstrapDnsDomains = bootstrapDomains,
            )

        assertEquals(listOf("domain:dns.nullsproxy.com"), bootstrapDomains)
        assertFalse("domain:dns.nullsproxy.com" in directDomains)
        assertTrue(
            systemDnsBootstrapDomains(
                proxyDnsServers = listOf(nullsDoh),
                directDnsServers = listOf("localhost"),
            ).isEmpty(),
        )
    }

    @Test
    fun generated_config_uses_system_bootstrap_for_same_proxy_and_direct_doh() {
        val nullsDoh = "https://dns.nullsproxy.com/dns-query"
        val server = ProxyServerState(
            id = 1,
            groupId = 0,
            server = VLESS(
                remarks = "Test",
                id = "test-id",
                server = "node.example",
                port = "443",
            ),
        )
        val appState = AppState(
            proxyServers = listOf(server),
            selectedProxyServerId = server.id,
            proxyDns = listOf(nullsDoh),
            directDns = listOf(nullsDoh),
            directDnsDomains = emptyList(),
        )
        val config = XrayConfigFactory.buildXrayConfig(
            XrayConfigRequest(
                appState = appState,
                selectedServer = server,
                inbounds = emptyList(),
                coreLogPaths = XrayCoreLogPaths(
                    accessLogPath = "/tmp/access.log",
                    errorLogPath = "/tmp/error.log",
                ),
                dnsHosts = emptyList(),
            ),
        )

        assertTrue(config.contains("\"address\":\"localhost\""))
        assertTrue(config.contains("domain:dns.nullsproxy.com"))
    }
}
