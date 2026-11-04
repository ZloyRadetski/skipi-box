// Copyright 2026, Radetski
// SPDX-License-Identifier: GPL-3.0

package features.config

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class ShadowrocketDisabledRuleTest {
    private fun rule(line: String) = "[Rule]\n$line\nFINAL,PROXY\n".analyzeShadowrocketConfig().rules.first()

    private fun convert(line: String) = rule(line).convertToXrayRouteRules(firstId = 1) { it.lowercase() }

    @Test
    fun aRuleWithoutTheOptionIsEnabled() {
        assertFalse(rule("DOMAIN-SUFFIX,a.com,DIRECT").isDisabled)
        assertFalse(rule("IP-CIDR,10.0.0.0/8,DIRECT,no-resolve").isDisabled)
        assertFalse(rule("OR,((DOMAIN,a.com),(DST-PORT,443)),DIRECT").isDisabled)
    }

    @Test
    fun theOptionDisablesPlainAndLogicalRules() {
        assertTrue(rule("DOMAIN-SUFFIX,a.com,DIRECT,skipi-disabled").isDisabled)
        assertTrue(rule("AND,((DOMAIN,a.com),(DST-PORT,443)),DIRECT,skipi-disabled").isDisabled)
    }

    @Test
    fun theOptionIsFoundNextToOtherOptions() {
        assertTrue(rule("IP-CIDR,10.0.0.0/8,DIRECT,no-resolve,skipi-disabled").isDisabled)
        assertTrue(rule("IP-CIDR,10.0.0.0/8,DIRECT,skipi-disabled,no-resolve").isDisabled)
    }

    @Test
    fun theOptionIsMatchedCaseInsensitivelyAndAsAWholeWord() {
        assertTrue(rule("DOMAIN,a.com,DIRECT,Skipi-Disabled").isDisabled)
        assertFalse(rule("DOMAIN,a.com,DIRECT,skipi-disabled-later").isDisabled)
    }

    @Test
    fun aDisabledRuleProducesNoXrayRuleAndIsNotCalledUnsupported() {
        listOf(
            "DOMAIN-SUFFIX,a.com,DIRECT,skipi-disabled",
            "OR,((DOMAIN,a.com),(IP-CIDR,10.0.0.0/8)),DIRECT,skipi-disabled",
        ).forEach { line ->
            val result = convert(line)
            assertTrue(result.rules.isEmpty(), line)
            assertNull(result.unsupportedReason, line)
        }
    }

    @Test
    fun anEnabledRuleStillProducesItsXrayRule() {
        assertEquals(listOf("domain:a.com"), convert("DOMAIN-SUFFIX,a.com,DIRECT").rules.single().domain)
    }

    @Test
    fun theLineKeepsTheOptionWhenItIsWrittenBack() {
        val line = "DOMAIN-SUFFIX,a.com,DIRECT,skipi-disabled"
        assertEquals(line, rule(line).toShadowrocketLine())
    }

    @Test
    fun switchingTheOptionKeepsOtherOptionsAndTheName() {
        val off = rule("IP-CIDR,10.0.0.0/8,DIRECT,no-resolve // Local").withDisabled(true)
        assertEquals("IP-CIDR,10.0.0.0/8,DIRECT,no-resolve, skipi-disabled // Local", off)
        val on = rule(off).withDisabled(false)
        assertEquals("IP-CIDR,10.0.0.0/8,DIRECT,no-resolve // Local", on)
    }

    @Test
    fun switchingOnAnEnabledRuleChangesNothing() {
        assertEquals("DOMAIN,a.com,DIRECT", rule("DOMAIN,a.com,DIRECT").withDisabled(false))
    }

    @Test
    fun switchingWorksOnLogicalRules() {
        val off = rule("OR,((DOMAIN,a.com),(DST-PORT,443)),DIRECT").withDisabled(true)
        assertEquals("OR,((DOMAIN,a.com),(DST-PORT,443)),DIRECT,skipi-disabled", off)
        assertTrue(rule(off).isDisabled)
    }
}
