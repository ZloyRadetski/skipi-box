// Copyright 2026, Radetski
// SPDX-License-Identifier: GPL-3.0

package engine.xray

import features.routing.model.RouteRule
import kotlin.test.Test
import kotlin.test.assertEquals

class RuleApplicationStatusTest {
    private val asn = "geoip:as13335"

    @Test
    fun nothingReportedMeansApplied() {
        assertEquals(RuleApplicationStatus.Applied, ruleApplicationStatus(RouteRule(ip = listOf(asn)), emptyList()))
    }

    @Test
    fun ruleWhoseOnlyValueIsDroppedIsSkipped() {
        val status = ruleApplicationStatus(RouteRule(remarks = "IP-ASN,13335", ip = listOf(asn)), listOf(asn))
        assertEquals(RuleApplication.Skipped, status.application)
        assertEquals(listOf(asn), status.values)
    }

    @Test
    fun ruleWithSomeValuesDroppedIsPartlySkipped() {
        val rule = RouteRule(domain = listOf("domain:a.com", "ext:missing.dat:x"))
        val status = ruleApplicationStatus(rule, listOf("ext:missing.dat:x"))
        assertEquals(RuleApplication.PartlySkipped, status.application)
        assertEquals(listOf("ext:missing.dat:x"), status.values)
    }

    @Test
    fun allValuesDroppedButAnotherConditionLeftIsPartlySkipped() {
        val rule = RouteRule(ip = listOf(asn), port = "443")
        assertEquals(RuleApplication.PartlySkipped, ruleApplicationStatus(rule, listOf(asn)).application)
    }

    @Test
    fun valuesMatchAfterTrimmingAndIgnoreDuplicates() {
        val rule = RouteRule(ip = listOf(" $asn ", asn))
        assertEquals(RuleApplication.Skipped, ruleApplicationStatus(rule, listOf(asn)).application)
    }

    @Test
    fun valueOfAnotherRuleDoesNotAffectThisOne() {
        val rule = RouteRule(domain = listOf("domain:a.com"))
        assertEquals(RuleApplication.Applied, ruleApplicationStatus(rule, listOf(asn)).application)
    }

    @Test
    fun unknownTargetMessageIsMatchedByRemarks() {
        val message = "Rule \"DOMAIN,a.com\": unknown target 'gone', routed to proxy"
        val rule = RouteRule(remarks = "DOMAIN,a.com", domain = listOf("full:a.com"))
        val status = ruleApplicationStatus(rule, listOf(message, "Rule \"DOMAIN,b.com\": unknown target 'x', routed to proxy"))
        assertEquals(RuleApplication.UnknownTarget, status.application)
        assertEquals(message, status.message)
    }

    @Test
    fun blankRemarksNeverMatchAnUnknownTargetMessage() {
        val message = "Rule \"\": unknown target 'gone', routed to proxy"
        assertEquals(RuleApplication.Applied, ruleApplicationStatus(RouteRule(domain = listOf("full:a.com")), listOf(message)).application)
    }

    @Test
    fun remarksPrefixIsNotMatchedAsASubstring() {
        val message = "Rule \"DOMAIN,a.com.ru\": unknown target 'gone', routed to proxy"
        val rule = RouteRule(remarks = "DOMAIN,a.com", domain = listOf("full:a.com"))
        assertEquals(RuleApplication.Applied, ruleApplicationStatus(rule, listOf(message)).application)
    }

    @Test
    fun combinedStatusIsSkippedOnlyWhenEveryPartIs() {
        val a = RouteRule(ip = listOf(asn))
        val b = RouteRule(ip = listOf("geoip:as1"))
        assertEquals(RuleApplication.Skipped, combinedRuleApplicationStatus(listOf(a, b), listOf(asn, "geoip:as1")).application)
        val partly = combinedRuleApplicationStatus(listOf(a, RouteRule(domain = listOf("full:x.com"))), listOf(asn))
        assertEquals(RuleApplication.PartlySkipped, partly.application)
        assertEquals(listOf(asn), partly.values)
    }

    @Test
    fun combinedStatusOfNoRulesIsApplied() {
        assertEquals(RuleApplicationStatus.Applied, combinedRuleApplicationStatus(emptyList(), listOf(asn)))
    }
}
