// Copyright 2026, Radetski
// SPDX-License-Identifier: GPL-3.0

package features.config

import kotlin.test.Test
import kotlin.test.assertEquals

class ShadowrocketAuditFixesTest {
    @Test
    fun finalRuleKeepsItsOptionsWhenWrittenBack() {
        val rule = "[Rule]\nFINAL,PROXY,dns-failed\n".analyzeShadowrocketConfig().rules.single()

        assertEquals("FINAL,PROXY,dns-failed", rule.toShadowrocketLine())
    }

    @Test
    fun disablingAFinalRuleMarksItDisabled() {
        val rule = "[Rule]\nFINAL,PROXY\n".analyzeShadowrocketConfig().rules.single()

        val disabled = rule.withDisabled(true)

        assertEquals("FINAL,PROXY,skipi-disabled", disabled)
    }

    @Test
    fun aBareLineNamedLikeASectionIsNotASectionHeader() {
        val config = "Rule\n[Rule]\nDOMAIN,a.com,DIRECT"

        val updated = config.withShadowrocketRuleAdded("DOMAIN,b.com,PROXY")

        assertEquals("Rule\n[Rule]\nDOMAIN,a.com,DIRECT\nDOMAIN,b.com,PROXY\n", updated)
    }

    @Test
    fun aBareLineNamedLikeASectionDoesNotCaptureASectionReplacement() {
        val config = "General\n[General]\ndns-server = 1.1.1.1\n[Rule]\nFINAL,PROXY\n"

        val updated = config.withShadowrocketSectionLines("General", listOf("dns-server = 9.9.9.9"))

        assertEquals("General\n[General]\ndns-server = 9.9.9.9\n[Rule]\nFINAL,PROXY\n", updated)
    }

    @Test
    fun aBareLineNamedLikeASectionDoesNotCaptureAProxyGroup() {
        val config = "Proxy Group\n[Proxy Group]\nA = select,DIRECT"

        val updated = config.withShadowrocketProxyGroupAdded("B = select,DIRECT")

        assertEquals("Proxy Group\n[Proxy Group]\nA = select,DIRECT\nB = select,DIRECT\n", updated)
    }
}
