// Copyright 2026, Radetski
// SPDX-License-Identifier: GPL-3.0

package features.config

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class ShadowrocketLogicalRouteConversionTest {
    private fun convert(line: String): XrayRouteConversion {
        val rule = assertNotNull("[Rule]\n$line\n".analyzeShadowrocketConfig().rules.firstOrNull(), line)
        return rule.convertToXrayRouteRules(firstId = 5) { policy -> policy.lowercase() }
    }

    @Test
    fun orOfDomainsBecomesOneRuleWithAList() {
        val rules = convert("OR,((GEOSITE,google),(DOMAIN-SUFFIX,youtube.com),(DOMAIN-KEYWORD,ads)),DIRECT").rules
        assertEquals(1, rules.size)
        assertEquals(listOf("geosite:google", "domain:youtube.com", "keyword:ads"), rules.single().domain)
        assertEquals("direct", rules.single().outboundTag)
        assertEquals(5, rules.single().id)
    }

    @Test
    fun orOfDifferentKindsBecomesOneRulePerKind() {
        val rules = convert("OR,((DOMAIN-SUFFIX,a.com),(IP-CIDR,10.0.0.0/8),(DOMAIN-SUFFIX,b.com)),PROXY").rules
        assertEquals(2, rules.size)
        assertEquals(listOf("domain:a.com", "domain:b.com"), rules[0].domain)
        assertEquals(listOf("10.0.0.0/8"), rules[1].ip)
        assertEquals(listOf(5, 6), rules.map { it.id })
    }

    @Test
    fun andOfDifferentFieldsIsOneRule() {
        val rules = convert("AND,((DOMAIN-SUFFIX,a.com),(DST-PORT,443),(NETWORK,tcp)),DIRECT").rules
        val rule = rules.single()
        assertEquals(listOf("domain:a.com"), rule.domain)
        assertEquals("443", rule.port)
        assertEquals("tcp", rule.network)
    }

    @Test
    fun nestedAndInsideOrIsFlattened() {
        val rules = convert("OR,((DOMAIN-SUFFIX,a.com),(AND,((DOMAIN-SUFFIX,b.com),(DST-PORT,443)))),DIRECT").rules
        assertEquals(2, rules.size)
        assertEquals(listOf("domain:a.com"), rules[0].domain)
        assertEquals("", rules[0].port)
        assertEquals(listOf("domain:b.com"), rules[1].domain)
        assertEquals("443", rules[1].port)
    }

    @Test
    fun andDistributesOverOr() {
        val rules = convert("AND,((OR,((DOMAIN-SUFFIX,a.com),(DOMAIN-SUFFIX,b.com))),(DST-PORT,443)),DIRECT").rules
        assertEquals(2, rules.size)
        assertTrue(rules.all { it.port == "443" })
        assertEquals(listOf(listOf("domain:a.com"), listOf("domain:b.com")), rules.map { it.domain })
    }

    @Test
    fun andOfTwoDomainsIsUnsupportedAndExplained() {
        val result = convert("AND,((DOMAIN-SUFFIX,a.com),(DOMAIN-KEYWORD,b)),DIRECT")
        assertTrue(result.rules.isEmpty())
        assertNotNull(result.unsupportedReason)
    }

    @Test
    fun unsupportedLeafMakesTheWholeRuleUnsupported() {
        val result = convert("OR,((DOMAIN-SUFFIX,a.com),(USER-AGENT,foo)),DIRECT")
        assertTrue(result.rules.isEmpty())
        assertNotNull(result.unsupportedReason)
    }

    @Test
    fun tooManyCombinationsAreRejected() {
        val ors = (1..4).joinToString(",") { group ->
            "(OR,(" + (1..4).joinToString(",") { "(DOMAIN-SUFFIX,g$group-$it.com)" } + "))"
        }
        // 4 OR groups of 4 domains ANDed would need 256 Xray rules, and AND of domains is unsupported anyway.
        val result = convert("AND,($ors),DIRECT")
        assertTrue(result.rules.isEmpty())
        assertNotNull(result.unsupportedReason)
    }

    @Test
    fun plainRuleStillConvertsAsBefore() {
        val result = convert("DOMAIN-SUFFIX,a.com,DIRECT")
        assertEquals(listOf("domain:a.com"), result.rules.single().domain)
        assertNull(result.unsupportedReason)
    }
}
