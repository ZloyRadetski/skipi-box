// Copyright 2026, Radetski
// SPDX-License-Identifier: GPL-3.0

package engine.xray

import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class XrayDnsPlanningTest {
    @Test
    fun plansValidatedDnsServersAndRequiredBootstrapDomains() {
        val plan = planXrayDns(
            XrayDnsPlanRequest(
                proxyDnsServers = listOf("tls://dns.example:853", "https://dns.example/dns-query"),
                directDnsServers = listOf("udp://1.1.1.1:53", "1.1.1.1"),
                appDirectDnsServers = emptyList(),
                defaultDirectDnsServers = listOf("8.8.8.8"),
                directDnsDomains = listOf("domain:direct.example"),
                dnsHosts = listOf("custom.example:10.0.0.1,10.0.0.2"),
                startupProxyServerDomains = listOf("domain:node.example"),
                enableIpv6 = false,
                enableFakeDns = false,
                fakeDnsIpPool = "",
                fakeDnsPoolSize = 0,
                tunDns = "172.19.0.1",
                fallbackDnsServer = "8.8.8.8",
            ),
        )

        val directEntry = plan.servers[0].jsonObject
        assertEquals("1.1.1.1", directEntry.getValue("address").jsonPrimitive.content)
        assertEquals(
            listOf("domain:direct.example", "domain:node.example", "domain:dns.example"),
            directEntry.getValue("domains").jsonArray.map { it.jsonPrimitive.content },
        )
        assertEquals("https://dns.example/dns-query", plan.servers[1].jsonPrimitive.content)
        assertEquals("UseIPv4", plan.queryStrategy)
        assertTrue(plan.routingOptions.routeProxyDns)
        assertTrue(plan.routingOptions.routeDirectDns)
        assertEquals("172.19.0.1", plan.nonIpQueryFallback.address)
        assertTrue(buildXrayDnsConfig(plan).getValue("hosts").jsonObject.containsKey("custom.example"))
    }

    @Test
    fun keepsSystemBootstrapOutsideTheDirectDnsLoop() {
        val server = "https://dns.nullsproxy.com/dns-query"
        val bootstrapDomains = systemDnsBootstrapDomains(
            proxyDnsServers = listOf(server),
            directDnsServers = listOf(server),
        )

        assertEquals(listOf("domain:dns.nullsproxy.com"), bootstrapDomains)
        assertFalse(
            "domain:dns.nullsproxy.com" in resolveXrayDirectDnsDomains(
                directDnsDomains = emptyList(),
                proxyDnsServers = listOf(server),
                systemBootstrapDnsDomains = bootstrapDomains,
            ),
        )
    }

    @Test
    fun fakeDnsUsesCoreDefaultsAndKeepsTheRequiredDnsFallback() {
        val plan = planXrayDns(
            XrayDnsPlanRequest(
                proxyDnsServers = emptyList(),
                directDnsServers = emptyList(),
                appDirectDnsServers = emptyList(),
                defaultDirectDnsServers = emptyList(),
                directDnsDomains = emptyList(),
                dnsHosts = emptyList(),
                startupProxyServerDomains = emptyList(),
                enableIpv6 = true,
                enableFakeDns = true,
                fakeDnsIpPool = "",
                fakeDnsPoolSize = 0,
                tunDns = "",
                fallbackDnsServer = "8.8.8.8",
            ),
        )

        assertEquals("UseIP", plan.queryStrategy)
        assertEquals(
            listOf("fakedns", "8.8.8.8"),
            plan.servers.map { it.jsonPrimitive.content },
        )
        val fakeDns = requireNotNull(plan.fakeDns).jsonObject
        assertEquals(XrayFakeDnsIpv4Pool, fakeDns.getValue("ipPool").jsonPrimitive.content)
        assertEquals(
            XrayFakeDnsIpv4OnlyPoolSize.toString(),
            fakeDns.getValue("poolSize").jsonPrimitive.content,
        )
    }
}
