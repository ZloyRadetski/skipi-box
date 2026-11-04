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
    fun andDistributesOverOrAndTheAlternativesShareOneRule() {
        // (a OR b) AND port 443 is one Xray rule with a domain list: the same match, without a rule per domain.
        val rule = convert("AND,((OR,((DOMAIN-SUFFIX,a.com),(DOMAIN-SUFFIX,b.com))),(DST-PORT,443)),DIRECT").rules.single()
        assertEquals("443", rule.port)
        assertEquals(listOf("domain:a.com", "domain:b.com"), rule.domain)
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

    private fun domains(count: Int) = (1..count).joinToString(",") { "(DOMAIN-SUFFIX,site$it.example)" }

    @Test
    fun hundredsOfDomainsInOneOrStayOneRule() {
        val rule = convert("OR,(${domains(300)}),DIRECT").rules.single()
        assertEquals(300, rule.domain.size)
        assertEquals("domain:site300.example", rule.domain.last())
    }

    @Test
    fun manyDomainsWithAPortStayOneRule() {
        val result = convert("AND,((OR,(${domains(100)})),(DST-PORT,443)),DIRECT")
        assertNull(result.unsupportedReason)
        val rule = result.rules.single()
        assertEquals(100, rule.domain.size)
        assertEquals("443", rule.port)
    }

    @Test
    fun domainsAndAddressesWithSeveralPortsAreOneRulePerKind() {
        val line = "AND,((OR,((DOMAIN-SUFFIX,a.com),(DOMAIN-SUFFIX,b.com),(IP-CIDR,10.0.0.0/8))),(OR,((DST-PORT,443),(DST-PORT,8000-8100)))),PROXY"
        val rules = convert(line).rules
        assertEquals(2, rules.size)
        assertEquals(listOf("domain:a.com", "domain:b.com"), rules[0].domain)
        assertEquals(listOf("10.0.0.0/8"), rules[1].ip)
        assertEquals(listOf("443,8000-8100", "443,8000-8100"), rules.map { it.port })
    }

    @Test
    fun severalProtocolsShareOneRule() {
        val rule = convert("AND,((DOMAIN-SUFFIX,a.com),(OR,((PROTOCOL,http),(PROTOCOL,tls)))),DIRECT").rules.single()
        assertEquals("http,tls", rule.protocol)
        assertEquals(listOf("domain:a.com"), rule.domain)
    }

    @Test
    fun rulesThatDifferInTwoFieldsAreNotMerged() {
        val rules = convert("OR,((AND,((DOMAIN-SUFFIX,a.com),(DST-PORT,443))),(AND,((DOMAIN-SUFFIX,b.com),(DST-PORT,80)))),DIRECT").rules
        assertEquals(2, rules.size)
        assertEquals(listOf("443", "80"), rules.map { it.port })
    }

    @Test
    fun combinationsThatCannotBeMergedStillHitTheCap() {
        val pairs = (1..70).joinToString(",") { "(AND,((DOMAIN-SUFFIX,site$it.example),(DST-PORT,${1000 + it}),(NETWORK,tcp)))" }
        // Seventy rules that differ in domain and port cannot be folded, so the conversion refuses rather than guesses.
        val result = convert("OR,($pairs),DIRECT")
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
