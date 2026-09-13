// Copyright 2026, Radetski
// SPDX-License-Identifier: GPL-3.0

package engine.xray

import app.AppState
import app.ProxyServerState
import features.proxy.server.model.Custom
import features.proxy.server.model.StrategyGroup
import features.proxy.server.model.StrategyGroupConstants
import features.proxy.server.model.VLESS
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
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
    fun proxy_endpoint_bootstrap_domains_are_always_resolved_by_direct_dns() {
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

        assertTrue(startupDomain in disabled)
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

    @Test
    fun dns_outbound_hijacks_ip_queries_and_proxies_other_types_to_nulls_tcp_dns() {
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
        val config = XrayConfigFactory.buildXrayConfig(
            XrayConfigRequest(
                appState = AppState(
                    proxyServers = listOf(server),
                    selectedProxyServerId = server.id,
                    proxyDns = listOf(nullsDoh),
                    directDns = listOf("77.88.8.8"),
                ),
                selectedServer = server,
                inbounds = emptyList(),
                coreLogPaths = XrayCoreLogPaths(
                    accessLogPath = "/tmp/access.log",
                    errorLogPath = "/tmp/error.log",
                ),
            ),
        )

        val root = XrayConfigJson.parseToJsonElement(config).jsonObject
        val dnsOutbound = root["outbounds"]
            ?.jsonArray
            ?.map(JsonElement::jsonObject)
            ?.first { outbound -> outbound["tag"]?.jsonPrimitive?.content == XrayTags.DNS_OUT }
            ?: error("DNS outbound is missing")
        val settings = dnsOutbound["settings"]?.jsonObject ?: error("DNS outbound settings are missing")
        val rules = settings["rules"]?.jsonArray?.map(JsonElement::jsonObject) ?: error("DNS outbound rules are missing")

        assertEquals("tcp", settings["rewriteNetwork"]?.jsonPrimitive?.content)
        assertEquals("dns.nullsproxy.com", settings["rewriteAddress"]?.jsonPrimitive?.content)
        assertEquals(53, settings["rewritePort"]?.jsonPrimitive?.content?.toInt())
        assertEquals("hijack", rules[0]["action"]?.jsonPrimitive?.content)
        assertEquals("1,28", rules[0]["qType"]?.jsonPrimitive?.content)
        assertEquals("direct", rules[1]["action"]?.jsonPrimitive?.content)
        assertEquals(
            "proxy",
            dnsOutbound["streamSettings"]
                ?.jsonObject
                ?.get("sockopt")
                ?.jsonObject
                ?.get("dialerProxy")
                ?.jsonPrimitive
                ?.content,
        )
        assertEquals(null, dnsOutbound["proxySettings"])
    }

    @Test
    fun tcp_fallback_only_assumes_a_raw_tcp_endpoint_when_the_transport_proves_it() {
        assertEquals(
            XrayDnsTcpFallback(address = "dns.nullsproxy.com"),
            "https://dns.nullsproxy.com/dns-query".toXrayTcpDnsFallbackOrNull(),
        )
        assertEquals(
            XrayDnsTcpFallback(address = "1.1.1.1", port = 5353),
            "tcp://1.1.1.1:5353".toXrayTcpDnsFallbackOrNull(),
        )
        assertEquals(null, "https://dns.example/dns-query".toXrayTcpDnsFallbackOrNull())
    }

    @Test
    fun generated_fragment_chain_uses_current_xray_dialer_proxy_field() {
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
        val config = XrayConfigFactory.buildXrayConfig(
            XrayConfigRequest(
                appState = AppState(
                    proxyServers = listOf(server),
                    selectedProxyServerId = server.id,
                    enableFragment = true,
                ),
                selectedServer = server,
                inbounds = emptyList(),
                coreLogPaths = XrayCoreLogPaths(
                    accessLogPath = "/tmp/access.log",
                    errorLogPath = "/tmp/error.log",
                ),
            ),
        )

        val outbounds = XrayConfigJson.parseToJsonElement(config).jsonObject["outbounds"]?.jsonArray
            ?.map(JsonElement::jsonObject)
            ?: error("Outbounds are missing")
        val proxyOutbound = outbounds.first { outbound -> outbound["tag"]?.jsonPrimitive?.content == "proxy" }

        assertEquals(
            XrayTags.FRAGMENT,
            proxyOutbound["streamSettings"]
                ?.jsonObject
                ?.get("sockopt")
                ?.jsonObject
                ?.get("dialerProxy")
                ?.jsonPrimitive
                ?.content,
        )
        assertEquals(null, proxyOutbound["proxySettings"])
    }

    @Test
    fun dns_dialer_reenters_the_live_balancer_instead_of_pinning_its_startup_fallback() {
        val plan = XrayOutboundPlan(
            proxyOutbounds = listOf(
                XrayProxyOutboundServer(tag = "proxy-policy-1"),
                XrayProxyOutboundServer(tag = "proxy-policy-2"),
            ),
            balancers = listOf(
                XrayBalancerPlan(
                    tag = XrayTags.PROXY,
                    selector = "proxy-policy-",
                    strategy = "leastPing",
                    fallbackTag = "proxy-policy-2",
                ),
            ),
            observatorySelectors = emptyList(),
            routeTargets = mapOf(
                XrayTags.PROXY to XrayRouteTarget(XrayTags.PROXY, XrayRouteTargetKind.Balancer),
            ),
            dnsHostServers = emptyList(),
        )

        assertEquals(XrayTags.DNS_PROXY_LOOPBACK, plan.dnsDialerProxyTag())
    }

    @Test
    fun generated_balancer_dns_dialer_uses_loopback_and_routes_back_to_balancer() {
        val first = ProxyServerState(
            id = 1,
            groupId = 0,
            server = VLESS(remarks = "First", id = "id-1", server = "first.example", port = "443"),
        )
        val second = ProxyServerState(
            id = 2,
            groupId = 0,
            server = VLESS(remarks = "Second", id = "id-2", server = "second.example", port = "443"),
        )
        val balancer = ProxyServerState(
            id = 100,
            groupId = 0,
            server = StrategyGroup(
                remarks = "Balancer",
                strategy = StrategyGroupConstants.TYPE_LEAST_PING,
                proxyServerIds = listOf(first.id, second.id),
            ),
        )
        val config = XrayConfigFactory.buildXrayConfig(
            XrayConfigRequest(
                appState = AppState(
                    proxyServers = listOf(first, second, balancer),
                    selectedProxyServerId = balancer.id,
                    proxyDns = listOf("https://dns.example/dns-query"),
                    directDns = listOf("1.1.1.1"),
                    enableDirectDnsForProxyServerDomains = false,
                ),
                selectedServer = balancer,
                inbounds = emptyList(),
                coreLogPaths = XrayCoreLogPaths("/tmp/access.log", "/tmp/error.log"),
            ),
        )
        val root = XrayConfigJson.parseToJsonElement(config).jsonObject
        val outbounds = root["outbounds"]?.jsonArray?.map(JsonElement::jsonObject) ?: error("Outbounds are missing")
        val dnsOutbound = outbounds.first { it["tag"]?.jsonPrimitive?.content == XrayTags.DNS_OUT }
        val dnsLoopback = outbounds.first { it["tag"]?.jsonPrimitive?.content == XrayTags.DNS_PROXY_LOOPBACK }
        val rules = root["routing"]?.jsonObject?.get("rules")?.jsonArray?.map(JsonElement::jsonObject)
            ?: error("Routing rules are missing")

        assertEquals(XrayProtocols.LOOPBACK, dnsLoopback["protocol"]?.jsonPrimitive?.content)
        assertEquals(
            XrayTags.DNS_PROXY_LOOPBACK,
            dnsOutbound["streamSettings"]
                ?.jsonObject
                ?.get("sockopt")
                ?.jsonObject
                ?.get("dialerProxy")
                ?.jsonPrimitive
                ?.content,
        )
        assertTrue(
            rules.any { rule ->
                rule["balancerTag"]?.jsonPrimitive?.content == XrayTags.PROXY &&
                    rule["inboundTag"]?.jsonArray?.any { tag ->
                        tag.jsonPrimitive.content == XrayTags.DNS_PROXY_LOOPBACK_INBOUND
                    } == true
            },
        )
        assertTrue(
            config.contains("domain:first.example") && config.contains("domain:second.example"),
            "Every balancer endpoint must be direct-DNS bootstrapped even when the legacy preference is off",
        )
    }

    @Test
    fun custom_profile_dns_override_uses_the_same_proxied_tcp_fallback() {
        val nullsDoh = "https://dns.nullsproxy.com/dns-query"
        val server = ProxyServerState(
            id = 1,
            groupId = 0,
            server = Custom(
                remarks = "Custom",
                configJson = """
                    {
                      "outbounds": [{"tag": "proxy", "protocol": "freedom"}]
                    }
                """.trimIndent(),
            ),
        )
        val config = XrayConfigFactory.buildXrayConfig(
            XrayConfigRequest(
                appState = AppState(
                    proxyDns = listOf(nullsDoh),
                    directDns = listOf("77.88.8.8"),
                ),
                selectedServer = server,
                inbounds = emptyList(),
                coreLogPaths = XrayCoreLogPaths(
                    accessLogPath = "/tmp/access.log",
                    errorLogPath = "/tmp/error.log",
                ),
                collectTrafficStats = false,
            ),
        )

        val dnsOutbound = XrayConfigJson.parseToJsonElement(config).jsonObject["outbounds"]
            ?.jsonArray
            ?.map(JsonElement::jsonObject)
            ?.first { outbound -> outbound["tag"]?.jsonPrimitive?.content == XrayTags.DNS_OUT }
            ?: error("DNS outbound is missing")
        val settings = dnsOutbound["settings"]?.jsonObject ?: error("DNS outbound settings are missing")

        assertEquals("dns.nullsproxy.com", settings["rewriteAddress"]?.jsonPrimitive?.content)
        assertEquals(53, settings["rewritePort"]?.jsonPrimitive?.content?.toInt())
        assertEquals(
            "proxy",
            dnsOutbound["streamSettings"]
                ?.jsonObject
                ?.get("sockopt")
                ?.jsonObject
                ?.get("dialerProxy")
                ?.jsonPrimitive
                ?.content,
        )
    }
}
