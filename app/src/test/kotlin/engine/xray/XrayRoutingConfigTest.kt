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
}
