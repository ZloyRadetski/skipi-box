// Copyright 2026, Radetski
// SPDX-License-Identifier: GPL-3.0

package features.config

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class ShadowrocketRouteRuleConversionTest {
    @Test
    fun converts_portable_domain_and_ip_rules_with_the_caller_policy() {
        val wildcard = rule(type = "DOMAIN-WILDCARD", value = "*.example.test", policy = "Group")
            .toXrayRouteRule(id = 7) { policy -> "target:$policy" }
        val asn = rule(type = "IP-ASN", value = "AS13335", policy = "DIRECT")
            .toXrayRouteRule(id = 8) { policy -> policy.lowercase() }

        assertEquals(7, wildcard?.id)
        assertEquals("target:Group", wildcard?.outboundTag)
        assertEquals(listOf("regexp:^.*\\.example\\.test$"), wildcard?.domain)
        assertEquals(listOf("geoip:as13335"), asn?.ip)
        assertEquals("direct", asn?.outboundTag)
    }

    @Test
    fun rejects_empty_or_unsupported_rules_but_keeps_supported_rule_sets() {
        assertNull(rule(type = "URL-REGEX", value = "example").toXrayRouteRule(1) { it })
        assertNull(rule(type = "IP-CIDR", value = "not-an-ip").toXrayRouteRule(2) { it })

        val ruleSet = rule(type = "RULE-SET", value = "geosite:cn", policy = "PROXY")
            .toXrayRouteRule(3) { "proxy" }
        assertEquals(listOf("geosite:cn"), ruleSet?.domain)
        assertEquals(emptyList(), ruleSet?.ip)
    }

    private fun rule(
        type: String,
        value: String,
        policy: String = "PROXY",
    ) = ShadowrocketRule(
        lineNumber = 1,
        type = type,
        value = value,
        policy = policy,
        raw = "$type,$value,$policy",
    )
}
