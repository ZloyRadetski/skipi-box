// Copyright 2026, Radetski
// SPDX-License-Identifier: GPL-3.0

package features.config

import engine.xray.XrayTags
import kotlin.test.Test
import kotlin.test.assertEquals

class TrafficConfigRuntimePlannerTest {
    @Test
    fun dnsAndHostsKeepProfileKeyGatingAndFallbackPriority() {
        val plan = TrafficConfigRuntimePlanner.plan(
            TrafficConfigRuntimePlanInput(
                rawConfig = """
                    [General]
                    dns-server = system, 9.9.9.9, 9.9.9.9, 1.1.1.1
                    ipv6 = yes
                    prefer-ipv6 = false

                    [SKIPI]
                    proxy-dns = true
                    dns-hosts = true

                    [Host]
                    proxy.example = server:203.0.113.7
                    proxy.example = 203.0.113.7
                    # ignored.example = 192.0.2.1

                    [Rule]
                    FINAL,PROXY
                """.trimIndent(),
                configs = emptyList(),
                servers = emptyList(),
                enableIpv6Fallback = false,
                enableIpv6PreferFallback = true,
                dnsInputs = TrafficConfigDnsInputs(
                    profileProxyDns = listOf("https://profile-proxy/dns-query"),
                    profileDirectDns = listOf("https://profile-direct/dns-query"),
                    profileDirectDnsDomains = listOf("geosite:profile-direct"),
                    profileDnsHosts = listOf("profile.example:192.0.2.3"),
                    existingProxyDns = listOf("https://existing-proxy/dns-query"),
                    existingDirectDns = listOf("https://existing-direct/dns-query"),
                    existingDirectDnsDomains = listOf("geosite:existing-direct"),
                    existingDnsHosts = listOf("existing.example:192.0.2.4"),
                ),
            ),
        )

        assertEquals(listOf("https://profile-proxy/dns-query"), plan.proxyDns)
        assertEquals(listOf("9.9.9.9", "1.1.1.1"), plan.directDns)
        assertEquals(listOf("geosite:profile-direct"), plan.directDnsDomains)
        assertEquals(listOf("proxy.example:203.0.113.7"), plan.dnsHosts)
        assertEquals(true, plan.enableIpv6)
        assertEquals(false, plan.enableIpv6Prefer)
        assertEquals(XrayTags.PROXY, plan.defaultRouteOutboundTag)
    }

    @Test
    fun resolvesServerPoliciesAndRecursiveConfigFinalPoliciesWithProxyFallbackOnCycles() {
        val plan = TrafficConfigRuntimePlanner.plan(
            TrafficConfigRuntimePlanInput(
                rawConfig = """
                    [Rule]
                    DOMAIN,server.example,Alpha
                    DOMAIN,recursive.example,CONFIG:2
                    DOMAIN,cycle.example,CONFIG:4
                    DOMAIN,missing.example,CONFIG:99
                    FINAL,CONFIG:2
                """.trimIndent(),
                configs = listOf(
                    TrafficConfigReference(2, "[Rule]\nFINAL,CONFIG:3"),
                    TrafficConfigReference(3, "[Rule]\nFINAL,REJECT-DROP"),
                    TrafficConfigReference(4, "[Rule]\nFINAL,CONFIG:4"),
                ),
                servers = listOf(TrafficConfigServerTarget("Alpha", "42")),
                enableIpv6Fallback = false,
                enableIpv6PreferFallback = false,
            ),
        )

        assertEquals(listOf("42", XrayTags.BLOCK, XrayTags.PROXY, XrayTags.PROXY), plan.routeRules.map { it.outboundTag })
        assertEquals(XrayTags.BLOCK, plan.defaultRouteOutboundTag)
        assertEquals(listOf(1, 2, 3, 4), plan.routeRules.map { it.id })
        assertEquals(5, plan.nextRouteRuleId)
    }

    @Test
    fun groupNamePrecedesBuiltinPolicyAndIpv6DefaultsUseCallerFallback() {
        val plan = TrafficConfigRuntimePlanner.plan(
            TrafficConfigRuntimePlanInput(
                rawConfig = """
                    [Proxy Group]
                    PROXY = select, Alpha

                    [Rule]
                    DOMAIN,group.example,PROXY
                    FINAL,DIRECT
                """.trimIndent(),
                configs = emptyList(),
                servers = emptyList(),
                enableIpv6Fallback = true,
                enableIpv6PreferFallback = true,
            ),
        )

        assertEquals("shadowrocket-group:PROXY", plan.routeRules.single().outboundTag)
        assertEquals(true, plan.enableIpv6)
        assertEquals(true, plan.enableIpv6Prefer)
    }
}
