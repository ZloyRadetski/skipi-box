// Copyright 2026, Radetski
// SPDX-License-Identifier: GPL-3.0

package engine.xray

import app.AppState
import features.routing.model.RouteRule
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class XrayRoutingConfigTest {

    @Test
    fun testEmptyCriteriaRuleWithRemarksIsNotIncludedInRoutingPlan() {
        val emptyRule = RouteRule(
            id = 1,
            remarks = "Empty rule with remarks",
            outboundTag = "direct",
            domain = emptyList(),
            ip = emptyList(),
            process = emptyList(),
            port = "",
            protocol = "",
            network = "",
            enabled = true,
        )

        val appState = AppState(
            routeRules = listOf(emptyRule),
            defaultRouteOutboundTag = "proxy",
        )

        val targets = mapOf(
            "proxy" to XrayRouteTarget("proxy", XrayRouteTargetKind.Outbound),
            "direct" to XrayRouteTarget("direct", XrayRouteTargetKind.Outbound),
        )

        val plan = appState.buildXrayRoutingPlan(
            routeTargets = targets,
            balancers = emptyList(),
            routeProxyDns = false,
            routeDirectDns = false,
            dnsHijackInboundTags = emptyList(),
        )

        // The empty rule must NOT be converted to a wildcard catch-all rule in Xray.
        // Only the default final route should be present.
        val nonFinalRules = plan.rules.filter { element ->
            val obj = element.jsonObject
            obj["ruleTag"]?.jsonPrimitive?.content == "Empty rule with remarks"
        }
        assertEquals(0, nonFinalRules.size)
    }

    @Test
    fun testSanitizerFilteredRuleWithNoRemainingConditionsIsDropped() {
        // A rule with a non-existent geo tag in an empty temp dataDir
        val tempDir = File.createTempFile("geotest", "dir")
        tempDir.delete()
        tempDir.mkdirs()

        try {
            val invalidGeoRule = RouteRule(
                id = 2,
                remarks = "Non-existent geosite",
                outboundTag = "direct",
                domain = listOf("geosite:nonexistent_custom_tag"),
                enabled = true,
            )

            val appState = AppState(
                routeRules = listOf(invalidGeoRule),
                defaultRouteOutboundTag = "proxy",
            )

            val targets = mapOf(
                "proxy" to XrayRouteTarget("proxy", XrayRouteTargetKind.Outbound),
                "direct" to XrayRouteTarget("direct", XrayRouteTargetKind.Outbound),
            )

            val plan = appState.buildXrayRoutingPlan(
                routeTargets = targets,
                balancers = emptyList(),
                routeProxyDns = false,
                routeDirectDns = false,
                dnsHijackInboundTags = emptyList(),
                dataDir = tempDir.absolutePath,
            )

            val leakedRules = plan.rules.filter { element ->
                val obj = element.jsonObject
                obj["ruleTag"]?.jsonPrimitive?.content == "Non-existent geosite"
            }
            assertEquals(0, leakedRules.size)
            assertTrue(plan.unappliedRules.contains("geosite:nonexistent_custom_tag"))
        } finally {
            tempDir.deleteRecursively()
        }
    }

    @Test
    fun testValidRulesProduceEmptyUnappliedRules() {
        val validRule = RouteRule(
            id = 3,
            remarks = "Valid IP rule",
            outboundTag = "direct",
            ip = listOf("1.1.1.1", "10.0.0.0/8"),
            enabled = true,
        )

        val appState = AppState(
            routeRules = listOf(validRule),
            defaultRouteOutboundTag = "proxy",
        )

        val targets = mapOf(
            "proxy" to XrayRouteTarget("proxy", XrayRouteTargetKind.Outbound),
            "direct" to XrayRouteTarget("direct", XrayRouteTargetKind.Outbound),
        )

        val plan = appState.buildXrayRoutingPlan(
            routeTargets = targets,
            balancers = emptyList(),
            routeProxyDns = false,
            routeDirectDns = false,
            dnsHijackInboundTags = emptyList(),
        )

        assertTrue(plan.unappliedRules.isEmpty())
    }

    @Test
    fun testXrayGeoRuleSanitizerIpValidation() {
        // Valid IP and CIDR addresses
        assertTrue(XrayGeoRuleSanitizer.isIpRuleValid("1.1.1.1", null))
        assertTrue(XrayGeoRuleSanitizer.isIpRuleValid("10.0.0.0/8", null))
        assertTrue(XrayGeoRuleSanitizer.isIpRuleValid("2001:db8::1", null))
        assertTrue(XrayGeoRuleSanitizer.isIpRuleValid("2001:db8::/32", null))
        assertTrue(XrayGeoRuleSanitizer.isIpRuleValid("geoip:private", null))

        // Invalid IP strings that must be rejected to prevent Xray crash
        assertFalse(XrayGeoRuleSanitizer.isIpRuleValid("https://raw.githubusercontent.com/test.txt", null))
        assertFalse(XrayGeoRuleSanitizer.isIpRuleValid("not_an_ip", null))
        assertFalse(XrayGeoRuleSanitizer.isIpRuleValid("300.400.500.600", null))
        assertFalse(XrayGeoRuleSanitizer.isIpRuleValid("", null))
    }

    @Test
    fun testDuplicateRuleRemarksProduceUniqueRuleTags() {
        val rule1 = RouteRule(
            id = 101,
            remarks = "DOMAIN-SUFFIX,domain:yoomoney.ru",
            outboundTag = "direct",
            domain = listOf("yoomoney.ru"),
            enabled = true,
        )
        val rule2 = RouteRule(
            id = 102,
            remarks = "DOMAIN-SUFFIX,domain:yoomoney.ru",
            outboundTag = "direct",
            domain = listOf("*.yoomoney.ru"),
            enabled = true,
        )
        val rule3 = RouteRule(
            id = 0,
            remarks = "DOMAIN-SUFFIX,domain:yoomoney.ru",
            outboundTag = "direct",
            domain = listOf("api.yoomoney.ru"),
            enabled = true,
        )

        val appState = AppState(
            routeRules = listOf(rule1, rule2, rule3),
            defaultRouteOutboundTag = "proxy",
        )

        val targets = mapOf(
            "proxy" to XrayRouteTarget("proxy", XrayRouteTargetKind.Outbound),
            "direct" to XrayRouteTarget("direct", XrayRouteTargetKind.Outbound),
        )

        val plan = appState.buildXrayRoutingPlan(
            routeTargets = targets,
            balancers = emptyList(),
            routeProxyDns = false,
            routeDirectDns = false,
            dnsHijackInboundTags = emptyList(),
        )

        val ruleTags = plan.rules.mapNotNull { element ->
            element.jsonObject["ruleTag"]?.jsonPrimitive?.content
        }

        assertEquals(3, ruleTags.size)
        // All ruleTags must be distinct to prevent Xray duplicate ruleTag crash
        assertEquals(ruleTags.size, ruleTags.distinct().size)
        assertEquals("DOMAIN-SUFFIX,domain:yoomoney.ru", ruleTags[0])
        assertEquals("DOMAIN-SUFFIX,domain:yoomoney.ru #102", ruleTags[1])
        assertEquals("DOMAIN-SUFFIX,domain:yoomoney.ru #2", ruleTags[2])
    }

    @Test
    fun testResolveUniqueRuleTagHelper() {
        val tags = mutableSetOf<String>()
        assertEquals("tag", resolveUniqueRuleTag("tag", 0, tags))
        assertEquals("tag #10", resolveUniqueRuleTag("tag", 10, tags))
        assertEquals("tag #2", resolveUniqueRuleTag("tag", 0, tags))
        assertEquals("tag #3", resolveUniqueRuleTag("tag", 0, tags))
        assertEquals(null, resolveUniqueRuleTag("", 0, tags))
        assertEquals(null, resolveUniqueRuleTag("   ", 0, tags))
    }

    @Test
    fun testIpv6BlackholeRouteAddedWhenEnableIpv6IsFalse() {
        val appState = AppState(
            enableIpv6 = false,
            defaultRouteOutboundTag = "proxy",
        )
        val targets = mapOf(
            "proxy" to XrayRouteTarget("proxy", XrayRouteTargetKind.Outbound),
            "block" to XrayRouteTarget("block", XrayRouteTargetKind.Outbound),
        )
        val plan = appState.buildXrayRoutingPlan(
            routeTargets = targets,
            balancers = emptyList(),
            routeProxyDns = false,
            routeDirectDns = false,
            dnsHijackInboundTags = emptyList(),
        )

        val ipv6BlockRule = plan.rules.find { element ->
            val obj = element.jsonObject
            obj["outboundTag"]?.jsonPrimitive?.content == "block" &&
                obj["ip"]?.toString()?.contains("::/0") == true
        }
        assertTrue("Expected ::/0 blackhole rule when enableIpv6 is false", ipv6BlockRule != null)
    }

    @Test
    fun testIpv6BlackholeRouteOmittedWhenEnableIpv6IsTrue() {
        val appState = AppState(
            enableIpv6 = true,
            defaultRouteOutboundTag = "proxy",
        )
        val targets = mapOf(
            "proxy" to XrayRouteTarget("proxy", XrayRouteTargetKind.Outbound),
            "block" to XrayRouteTarget("block", XrayRouteTargetKind.Outbound),
        )
        val plan = appState.buildXrayRoutingPlan(
            routeTargets = targets,
            balancers = emptyList(),
            routeProxyDns = false,
            routeDirectDns = false,
            dnsHijackInboundTags = emptyList(),
        )

        val ipv6BlockRule = plan.rules.find { element ->
            val obj = element.jsonObject
            obj["outboundTag"]?.jsonPrimitive?.content == "block" &&
                obj["ip"]?.toString()?.contains("::/0") == true
        }
        assertTrue("Expected no ::/0 blackhole rule when enableIpv6 is true", ipv6BlockRule == null)
    }
}

