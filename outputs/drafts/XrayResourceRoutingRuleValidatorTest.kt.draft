// Copyright 2026, Radetski
// SPDX-License-Identifier: GPL-3.0

package engine.xray

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertSame
import kotlin.test.assertTrue

/** Install only after the Android baseline oracle passes and before shared API RED. */
class XrayResourceRoutingRuleValidatorTest {
    @Test
    fun nullResourceLookupMatchesAndroidNoDirectoryCompatibility() {
        val invalidIps = mutableListOf<String>()
        val validator = XrayResourceRoutingRuleValidator(
            hasResourceTag = null,
            onInvalidIpRule = invalidIps::add,
        )
        val domains = mutableListOf("", "geosite:missing", "ext:malformed", "plain.example")
        val ips = mutableListOf("", "not-an-ip", "geoip:missing", "ext:malformed")

        assertTrue(validator.isDomainRuleValid(""))
        assertTrue(validator.isDomainRuleValid("geosite:missing"))
        assertFalse(validator.isIpRuleValid(""))
        assertFalse(validator.isIpRuleValid("not-an-ip"))
        assertTrue(validator.isIpRuleValid("geoip:missing"))
        assertTrue(validator.isIpRuleValid("ext:malformed"))
        assertSame(domains, validator.filterValidDomainRules(domains))
        assertSame(ips, validator.filterValidIpRules(ips))
        assertEquals(listOf("not-an-ip"), invalidIps)
    }

    @Test
    fun resourceLookupValidatesTagsFiltersRulesAndReportsInvalidRawIps() {
        val lookups = mutableListOf<Triple<String, String, String>>()
        val invalidIps = mutableListOf<String>()
        val validator = XrayResourceRoutingRuleValidator(
            hasResourceTag = { fileName, tag, originalRule ->
                lookups += Triple(fileName, tag, originalRule)
                when (fileName.lowercase() to tag.lowercase()) {
                    "geosite.dat" to "openai" -> true
                    "geoip.dat" to "us" -> true
                    "custom.dat" to "region" -> true
                    else -> false
                }
            },
            onInvalidIpRule = invalidIps::add,
        )
        assertFalse(validator.isIpRuleValid(" "))
        assertTrue(invalidIps.isEmpty())

        assertEquals(
            listOf("plain.example", "GEOSITE:OpenAI", "EXT:custom:Region"),
            validator.filterValidDomainRules(
                listOf("plain.example", "GEOSITE:OpenAI", "geosite:missing", "EXT:custom:Region", "ext:bad"),
            ),
        )
        assertEquals(
            listOf("192.0.2.8", "geoip:US", "geoip:private", "ext:custom:region"),
            validator.filterValidIpRules(
                listOf(
                    "192.0.2.8",
                    " ",
                    " not-an-ip ",
                    "geoip:US",
                    "geoip:missing",
                    "geoip:private",
                    "ext:custom:region",
                ),
            ),
        )
        assertEquals(listOf("not-an-ip"), invalidIps)
        assertTrue(lookups.contains(Triple("geosite.dat", "OpenAI", "GEOSITE:OpenAI")))
        assertTrue(lookups.contains(Triple("custom.dat", "Region", "EXT:custom:Region")))
        assertTrue(lookups.contains(Triple("geoip.dat", "US", "geoip:US")))
        assertFalse(lookups.any { (_, _, originalRule) -> originalRule == "geoip:private" })
        assertFalse(lookups.any { (_, _, originalRule) -> originalRule == "not-an-ip" })
    }
}
