// Copyright 2026, Radetski
// SPDX-License-Identifier: GPL-3.0

package engine.xray

import features.routing.model.RouteRule
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class XrayRoutingPlanTest {
    @Test
    fun drops_rejected_rules_and_reports_them_as_unapplied() {
        val plan = planXrayRouting(
            request = request(
                routeRules = listOf(
                    RouteRule(
                        id = 1,
                        remarks = "Missing geo tag",
                        outboundTag = XrayTags.DIRECT,
                        domain = listOf("geosite:missing"),
                    ),
                ),
            ),
            ruleValidator = validator(
                validDomain = { it != "geosite:missing" },
            ),
        )

        assertEquals(listOf("geosite:missing"), plan.unappliedRules)
        assertFalse(
            plan.rules.any { rule ->
                rule.jsonObject["ruleTag"]?.jsonPrimitive?.content == "Missing geo tag"
            },
        )
    }

    @Test
    fun assigns_unique_tags_and_keeps_final_route_last() {
        val plan = planXrayRouting(
            request = request(
                routeRules = listOf(
                    RouteRule(id = 1, remarks = "same", outboundTag = XrayTags.DIRECT, domain = listOf("one.test")),
                    RouteRule(id = 2, remarks = "same", outboundTag = XrayTags.DIRECT, domain = listOf("two.test")),
                ),
            ),
            ruleValidator = validator(),
        )

        val ruleTags = plan.rules.mapNotNull { rule ->
            rule.jsonObject["ruleTag"]?.jsonPrimitive?.content
        }
        assertEquals(listOf("same", "same #2"), ruleTags)
        assertEquals(
            XrayTags.PROXY,
            plan.rules.last().jsonObject["outboundTag"]?.jsonPrimitive?.content,
        )
        assertEquals("tcp,udp", plan.rules.last().jsonObject["network"]?.jsonPrimitive?.content)
    }

    @Test
    fun adds_dns_hijack_only_when_local_dns_is_enabled() {
        val plan = planXrayRouting(
            request = request(
                enableLocalDns = true,
                dnsHijackInboundTags = listOf(" tun ", "tun", "socks"),
            ),
            ruleValidator = validator(),
        )

        val hijack = plan.rules.first { rule ->
            rule.jsonObject["outboundTag"]?.jsonPrimitive?.content == XrayTags.DNS_OUT
        }.jsonObject
        assertEquals("[\"tun\",\"socks\"]", hijack["inboundTag"].toString())
        assertEquals("53", hijack["port"]?.jsonPrimitive?.content)
        assertTrue(buildXrayDnsHijackRule(emptyList()) == null)
    }

    private fun request(
        routeRules: List<RouteRule> = emptyList(),
        enableLocalDns: Boolean = false,
        dnsHijackInboundTags: List<String> = emptyList(),
    ) = XrayRoutingRequest(
        routeDomainStrategy = 1,
        routeRules = routeRules,
        defaultRouteOutboundTag = XrayTags.PROXY,
        routeTargets = mapOf(
            XrayTags.PROXY to XrayRouteTarget(XrayTags.PROXY, XrayRouteTargetKind.Outbound),
            XrayTags.DIRECT to XrayRouteTarget(XrayTags.DIRECT, XrayRouteTargetKind.Outbound),
        ),
        balancers = emptyList(),
        enableLocalDns = enableLocalDns,
        routeProxyDns = false,
        routeDirectDns = false,
        dnsHijackInboundTags = dnsHijackInboundTags,
    )

    private fun validator(
        validDomain: (String) -> Boolean = { true },
        validIp: (String) -> Boolean = { true },
    ): XrayRoutingRuleValidator = object : XrayRoutingRuleValidator {
        override fun isDomainRuleValid(rule: String): Boolean = validDomain(rule)
        override fun isIpRuleValid(rule: String): Boolean = validIp(rule)
    }
}
