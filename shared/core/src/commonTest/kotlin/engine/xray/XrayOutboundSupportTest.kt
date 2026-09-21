// Copyright 2026, Radetski
// SPDX-License-Identifier: GPL-3.0

package engine.xray

import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class XrayOutboundSupportTest {
    @Test
    fun buildsDnsOutboundWithAProxiedTcpFallback() {
        val outbound = buildXrayDnsOutbound(
            fallback = XrayDnsTcpFallback(address = "1.1.1.1", port = 5353),
            proxyOutboundTag = "proxy-main",
        )

        val settings = outbound.getValue("settings").jsonObject
        assertEquals("1.1.1.1", settings.getValue("rewriteAddress").jsonPrimitive.content)
        assertEquals("5353", settings.getValue("rewritePort").jsonPrimitive.content)
        assertEquals(
            "proxy-main",
            outbound.getValue("streamSettings").jsonObject
                .getValue("sockopt").jsonObject
                .getValue("dialerProxy").jsonPrimitive.content,
        )
        assertEquals(
            listOf("1,28", null),
            settings.getValue("rules").jsonArray.map { rule ->
                rule.jsonObject["qType"]?.jsonPrimitive?.content
            },
        )
    }

    @Test
    fun rendersFreedomAndLoopbackOutboundsFromSharedValues() {
        val freedom = buildFreedomOutbound(
            tag = XrayTags.DIRECT,
            domainStrategy = "UseIPv4",
            tcpSockopt = XrayTcpSockopt(60, 60, 10_000),
        )

        assertEquals(
            "10000",
            freedom.getValue("streamSettings").jsonObject
                .getValue("sockopt").jsonObject
                .getValue("tcpUserTimeout").jsonPrimitive.content,
        )
        assertEquals(
            XrayTags.DEFAULT_ROUTE_LOOPBACK_INBOUND,
            buildDefaultRouteOutbound().getValue("settings").jsonObject
                .getValue("inboundTag").jsonPrimitive.content,
        )
        assertEquals(
            XrayTags.DNS_PROXY_LOOPBACK_INBOUND,
            buildDnsProxyLoopbackOutbound().getValue("settings").jsonObject
                .getValue("inboundTag").jsonPrimitive.content,
        )
    }

    @Test
    fun resolvesDnsDialerForBalancersAndSimpleOutbounds() {
        assertEquals(
            XrayTags.DNS_PROXY_LOOPBACK,
            XrayOutboundPlan(
                proxyOutbounds = emptyList(),
                balancers = emptyList(),
                observatorySelectors = emptyList(),
                routeTargets = mapOf(
                    XrayTags.PROXY to XrayRouteTarget(XrayTags.PROXY, XrayRouteTargetKind.Balancer),
                ),
                dnsHostServers = emptyList(),
            ).dnsDialerProxyTag(),
        )
        assertNull(
            XrayOutboundPlan(
                proxyOutbounds = emptyList(),
                balancers = emptyList(),
                observatorySelectors = emptyList(),
                routeTargets = emptyMap(),
                dnsHostServers = emptyList(),
            ).dnsDialerProxyTag(),
        )
    }

    @Test
    fun normalizesMuxAndFragmentSettingsInSharedCode() {
        val mux = buildXrayMuxConfig(
            concurrency = "999",
            xudpConcurrency = "not-a-number",
            xudpProxyUdp443Mode = 99,
        )
        val fragment = buildXrayFragmentOutbound(
            XrayFragmentOutboundOptions(
                domainStrategy = xrayDirectOutboundDomainStrategy(
                    enableIpv6 = true,
                    enableIpv6Prefer = true,
                ),
                packets = "",
                length = "",
                interval = "",
            ),
        )

        assertEquals("128", mux.getValue("concurrency").jsonPrimitive.content)
        assertEquals("16", mux.getValue("xudpConcurrency").jsonPrimitive.content)
        assertEquals("reject", mux.getValue("xudpProxyUDP443").jsonPrimitive.content)
        assertEquals(
            "UseIPv6v4",
            fragment.getValue("settings").jsonObject.getValue("domainStrategy").jsonPrimitive.content,
        )
        assertEquals(
            DefaultFragmentPackets,
            fragment.getValue("settings").jsonObject
                .getValue("fragment").jsonObject
                .getValue("packets").jsonPrimitive.content,
        )
    }
}
