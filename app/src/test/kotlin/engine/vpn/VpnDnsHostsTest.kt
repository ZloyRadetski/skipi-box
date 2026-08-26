// Copyright 2026, Radetski
// SPDX-License-Identifier: GPL-3.0

package engine.vpn

import app.AppState
import app.ProxyServerState
import app.withActiveTrafficConfigApplied
import engine.xray.XrayConfigFactory
import engine.xray.XrayConfigRequest
import engine.xray.XrayCoreLogPaths
import features.config.TrafficConfigAndroidSettings
import features.config.TrafficConfigState
import features.proxy.server.model.VLESS
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class VpnDnsHostsTest {

    @Test
    fun enableResolveProxyServerDomain_defaults_to_true() {
        val state = AppState()
        assertTrue("enableResolveProxyServerDomain should default to true", state.enableResolveProxyServerDomain)
    }

    @Test
    fun trafficConfigAndroidSettings_enableResolveProxyServerDomain_defaults_to_true() {
        val settings = TrafficConfigAndroidSettings()
        assertTrue("enableResolveProxyServerDomain in settings should default to true", settings.enableResolveProxyServerDomain)
    }

    @Test
    fun withActiveTrafficConfigApplied_propagates_enableResolveProxyServerDomain() {
        val trafficConfig = TrafficConfigState(
            id = 1,
            name = "Test",
            rawConfig = "[General]\n",
            androidSettings = TrafficConfigAndroidSettings(enableResolveProxyServerDomain = true),
        )
        val state = AppState(
            trafficConfigs = listOf(trafficConfig),
            activeTrafficConfigId = 1,
            enableResolveProxyServerDomain = false,
        ).withActiveTrafficConfigApplied()

        assertTrue("withActiveTrafficConfigApplied should propagate enableResolveProxyServerDomain", state.enableResolveProxyServerDomain)
    }

    @Test
    fun xrayDnsHosts_when_disabled_returns_original_dnsHosts() {
        val state = AppState(
            enableResolveProxyServerDomain = false,
            dnsHosts = listOf("example.com:1.2.3.4"),
        )
        val result = state.xrayDnsHosts(listOf("node.example.org"))
        assertTrue(result.contains("example.com:1.2.3.4"))
        assertFalse(result.any { it.startsWith("node.example.org") })
    }

    @Test
    fun xrayConfig_hosts_block_contains_resolved_dns_hosts() {
        val vless = VLESS(remarks = "Node 1", id = "u1", server = "iron-central.duckdns.org", port = "443")
        val serverState = ProxyServerState(id = 1, server = vless, groupId = 0)
        val appState = AppState(
            proxyServers = listOf(serverState),
            selectedProxyServerId = 1,
            dnsHosts = listOf("iron-central.duckdns.org:194.135.25.10"),
        )
        val request = XrayConfigRequest(
            appState = appState,
            selectedServer = serverState,
            inbounds = emptyList(),
            coreLogPaths = XrayCoreLogPaths(accessLogPath = "/tmp/access.log", errorLogPath = "/tmp/core.log"),
            dnsHosts = appState.dnsHosts,
        )
        val xrayJson = XrayConfigFactory.buildXrayConfig(request)
        assertTrue("Xray config hosts must contain iron-central.duckdns.org", xrayJson.contains("\"iron-central.duckdns.org\""))
        assertTrue("Xray config hosts must contain 194.135.25.10", xrayJson.contains("194.135.25.10"))
    }
}
