// Copyright 2026, Radetski
// SPDX-License-Identifier: GPL-3.0

package features.config

import features.config.ShadowrocketCondition.Group
import features.config.ShadowrocketCondition.Leaf
import features.config.ShadowrocketLogicalOperator.And
import features.config.ShadowrocketLogicalOperator.Or
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull

class ShadowrocketLogicalRuleTest {
    @Test
    fun parsesFlatOr() {
        val parts = assertNotNull(parseShadowrocketLogicalRule("OR,((GEOSITE,google),(DOMAIN-SUFFIX,youtube.com)),DIRECT"))
        assertEquals(Group(Or, listOf(Leaf("GEOSITE", "google"), Leaf("DOMAIN-SUFFIX", "youtube.com"))), parts.group)
        assertEquals("DIRECT", parts.policy)
        assertNull(parts.option)
    }

    @Test
    fun parsesNestedGroupsAndKeepsLeafFlags() {
        val line = "OR,((DOMAIN-SUFFIX,a.com),(AND,((DOMAIN-SUFFIX,b.com),(DST-PORT,443))),(IP-CIDR,10.0.0.0/8,no-resolve)),PROXY"
        val parts = assertNotNull(parseShadowrocketLogicalRule(line))
        assertEquals(
            Group(
                Or,
                listOf(
                    Leaf("DOMAIN-SUFFIX", "a.com"),
                    Group(And, listOf(Leaf("DOMAIN-SUFFIX", "b.com"), Leaf("DST-PORT", "443"))),
                    Leaf("IP-CIDR", "10.0.0.0/8", "no-resolve"),
                ),
            ),
            parts.group,
        )
        assertEquals("PROXY", parts.policy)
    }

    @Test
    fun toleratesWhitespaceAndKeepsTrailingOption() {
        val parts = assertNotNull(parseShadowrocketLogicalRule("and, ( ( DOMAIN , a.com ) , ( DST-PORT , 443 ) ) , DIRECT , force-remote-dns"))
        assertEquals(And, parts.group.operator)
        assertEquals(listOf(Leaf("DOMAIN", "a.com"), Leaf("DST-PORT", "443")), parts.group.children)
        assertEquals("force-remote-dns", parts.option)
    }

    @Test
    fun serialisesBackToTheSameLine() {
        val line = "OR,((DOMAIN-SUFFIX,a.com),(AND,((DOMAIN-SUFFIX,b.com),(DST-PORT,443)))),DIRECT"
        val rule = assertNotNull("[Rule]\n$line\n".analyzeShadowrocketConfig().rules.firstOrNull())
        assertEquals(line, rule.toShadowrocketLine())
    }

    @Test
    fun notAndMalformedLinesAreNotParsed() {
        listOf(
            "NOT,((DOMAIN,a.com)),DIRECT",
            "OR,((DOMAIN,a.com),DIRECT",
            "OR,(),DIRECT",
            "OR,((DOMAIN)),DIRECT",
            "OR,((DOMAIN,a.com))",
            "OR,((NOT,((DOMAIN,a.com)))),DIRECT",
            "OR,DOMAIN,a.com,DIRECT",
        ).forEach { assertNull(parseShadowrocketLogicalRule(it), it) }
    }

    @Test
    fun excessiveNestingIsRejected() {
        val deep = (1..40).fold("(DOMAIN,a.com)") { inner, _ -> "(OR,($inner))" }
        assertNull(parseShadowrocketLogicalRule("OR,($deep),DIRECT"))
    }

    @Test
    fun configKeepsLogicalRuleAsOneRuleAndOthersUntouched() {
        val text = "[Rule]\nOR,((GEOSITE,google),(DOMAIN-SUFFIX,youtube.com)),DIRECT\nDOMAIN,a.com,PROXY\nNOT,((DOMAIN,b.com)),DIRECT\nFINAL,PROXY\n"
        val analysis = text.analyzeShadowrocketConfig()
        assertEquals(listOf("OR", "DOMAIN", "FINAL"), analysis.rules.map { it.type })
        assertEquals(2, assertNotNull(analysis.rules.first().condition).children.size)
        assertEquals(1, analysis.diagnostics.count { it.lineNumber == 4 })
    }
}

class ShadowrocketLogicalRuleSummaryTest {
    private fun rule(line: String) = "[Rule]\n$line\n".analyzeShadowrocketConfig().rules.single()

    @Test
    fun plainRuleKeepsItsOldListText() {
        assertEquals("DOMAIN-SUFFIX, a.com", rule("DOMAIN-SUFFIX,a.com,DIRECT").listSummary())
        assertNull(rule("DOMAIN-SUFFIX,a.com,DIRECT").xrayUnsupportedReason())
    }

    @Test
    fun logicalRuleReadsAsAnExpression() {
        val line = "OR,((GEOSITE,google),(AND,((DOMAIN-SUFFIX,b.com),(DST-PORT,443)))),DIRECT"
        assertEquals("GEOSITE google OR (DOMAIN-SUFFIX b.com AND DST-PORT 443)", rule(line).listSummary())
        assertNull(rule(line).xrayUnsupportedReason())
    }

    @Test
    fun unsupportedRuleIsFlagged() {
        assertNotNull(rule("AND,((DOMAIN,a.com),(DOMAIN-KEYWORD,b)),DIRECT").xrayUnsupportedReason())
    }
}
