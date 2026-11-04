// Copyright 2026, Radetski
// SPDX-License-Identifier: GPL-3.0

package features.config

import kotlin.test.Test
import kotlin.test.assertEquals

class ShadowrocketRuleRemarksTest {
    private fun rules(vararg lines: String) =
        ("[Rule]\n" + lines.joinToString("\n") + "\nFINAL,PROXY\n").analyzeShadowrocketConfig().rules

    @Test
    fun plainRuleReadsItsRemarksAfterTheSlashes() {
        val rule = rules("DOMAIN-SUFFIX,hypixel.net,DIRECT // Hypixel, EU").first()
        assertEquals("Hypixel, EU", rule.remarks)
        assertEquals("hypixel.net", rule.value)
        assertEquals("DIRECT", rule.policy)
        assertEquals(null, rule.option)
    }

    @Test
    fun logicalRuleReadsItsRemarks() {
        val rule = rules("AND,((DOMAIN-SUFFIX,a.com),(DST-PORT,443)),DIRECT // Game").first()
        assertEquals("Game", rule.remarks)
        assertEquals("AND", rule.type)
        assertEquals("DIRECT", rule.policy)
        assertEquals(2, rule.condition?.children?.size)
    }

    @Test
    fun urlValuesAreNotMistakenForRemarks() {
        val rule = rules("RULE-SET,https://example.com/list.txt,DIRECT").first()
        assertEquals("", rule.remarks)
        assertEquals("https://example.com/list.txt", rule.value)
    }

    @Test
    fun editingLineKeepsRemarks() {
        val rule = rules("DOMAIN-SUFFIX,a.com,DIRECT // Name one").first()
        assertEquals("DOMAIN-SUFFIX,b.com,PROXY // Name one", rule.toShadowrocketLine(value = "b.com", policy = "PROXY"))
    }
}
