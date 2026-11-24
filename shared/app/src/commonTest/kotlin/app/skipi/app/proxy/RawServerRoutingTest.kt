// Copyright 2026, Radetski
// SPDX-License-Identifier: GPL-3.0

package app.skipi.app.proxy

import features.proxy.server.model.Custom
import features.proxy.server.model.StrategyGroup
import features.proxy.server.model.StrategyGroupConstants
import features.proxy.server.model.VLESS
import features.routing.model.RouteRule
import engine.xray.XrayRoutingRuleValidator
import kotlinx.serialization.json.jsonPrimitive
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull

class RawServerRoutingTest {
    private val validator = object : XrayRoutingRuleValidator {
        override fun isDomainRuleValid(rule: String) = true
        override fun isIpRuleValid(rule: String) = true
    }

    private fun node(id: Int, name: String) = ProxyServerRow(
        id = id,
        groupId = 1,
        server = VLESS(remarks = name, id = "00000000-0000-0000-0000-${id.toString().padStart(12, '0')}", server = "h$id.example", port = "443"),
    )

    private val raw = ProxyServerRow(1, 1, Custom(remarks = "Raw", overrideInboundAndDns = true, configJson = "{}"))
    private val beta = node(2, "Beta")
    private val alpha = node(3, "Alpha")
    private val group = ProxyServerRow(
        4,
        1,
        StrategyGroup(
            remarks = "Auto",
            strategy = StrategyGroupConstants.TYPE_LEAST_PING,
            proxyServerIds = listOf(2, 3),
        ),
    )

    private fun routing(rules: List<RouteRule>, default: String = "proxy") = rawServerSkipiRouting(
        input = XrayOutboundPlanningInput(
            selectedServer = raw,
            servers = listOf(raw, beta, alpha, group),
            routeRules = rules,
            defaultRouteOutboundTag = default,
            shadowrocketPolicyGroups = emptyList(),
            trafficConfigContentById = emptyMap(),
            enableLocalDns = true,
            enableFragment = true,
            subscriptionPingUrl = "https://example.com/check",
            stripLeadingFlag = { it },
        ),
        routeDomainStrategy = 1,
        transport = null,
        ruleValidator = validator,
    )

    @Test
    fun rulesNamingAnotherServerOrAGroupGetRealOutboundsAndBalancers() {
        val skipi = routing(
            listOf(
                RouteRule(id = 1, domain = listOf("geosite:google"), outboundTag = "4"),
                RouteRule(id = 2, domain = listOf("geosite:steam"), outboundTag = "2"),
                RouteRule(id = 3, domain = listOf("geosite:claude"), outboundTag = "1"),
            ),
        )
        val rules = skipi.rules("my-proxy")
        val extras = skipi.extras("my-proxy")

        assertEquals("4", rules[0].getValue("balancerTag").jsonPrimitive.content)
        assertEquals("2", rules[1].getValue("outboundTag").jsonPrimitive.content)
        // The raw server itself, by id, and the default go to its own outbound.
        assertEquals("my-proxy", rules[2].getValue("outboundTag").jsonPrimitive.content)
        assertEquals("my-proxy", rules.last().getValue("outboundTag").jsonPrimitive.content)
        assertEquals(listOf("4"), extras.balancers.map { it.getValue("tag").jsonPrimitive.content })
        val tags = extras.outbounds.map { it.getValue("tag").jsonPrimitive.content }
        assertEquals(setOf("2", "4-policy-2", "4-policy-3"), tags.toSet())
        assertNotNull(extras.observatory)
        assertEquals(setOf("h2.example", "h3.example"), extras.serverHosts.toSet())
    }

    @Test
    fun aDefaultRouteToAnotherServerClosesTheRules() {
        val rules = routing(emptyList(), default = "2").rules("my-proxy")
        assertEquals("2", rules.last().getValue("outboundTag").jsonPrimitive.content)
        assertEquals(1, rules.size)
        assertEquals("tcp,udp", rules.last().getValue("network").jsonPrimitive.content)
        assertEquals(setOf("2"), routing(emptyList(), default = "2").extras("my-proxy").outbounds.map { it.getValue("tag").jsonPrimitive.content }.toSet())
    }
}
