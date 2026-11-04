// Copyright 2026, Radetski
// SPDX-License-Identifier: GPL-3.0

package features.config

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class RoutingTableTest {
    private val document = """
        [General]
        ipv6 = true

        [Rule]
        # a comment
        DOMAIN-SUFFIX,a.com,DIRECT // Alpha
        RULE-SET,https://example.com/list.txt,PROXY
        AND,((DOMAIN,b.com),(DST-PORT,443)),DIRECT,skipi-disabled // Beta
        NOT,((DOMAIN,c.com)),DIRECT
        FINAL,CONFIG:2

        [Host]
        a = 1.1.1.1
    """.trimIndent() + "\n"

    @Test
    fun everyRuleIsOneEntryInDocumentOrder() {
        val table = document.toRoutingTable()
        assertEquals(listOf("Alpha", "", "Beta"), table.entries.map { it.rule.remarks })
        assertEquals(listOf(6, 7, 8), table.entries.map { it.lineNumber })
    }

    @Test
    fun aRuleThatFitsTheTableHasARowAndOthersDoNot() {
        val entries = document.toRoutingTable().entries
        assertEquals(RoutingTableRow(name = "Alpha", policy = "DIRECT", domains = listOf("domain:a.com")), entries[0].row)
        assertNull(entries[1].row)
        val beta = assertNotNull(entries[2].row)
        assertEquals(false, beta.enabled)
        assertEquals("443", beta.port)
    }

    @Test
    fun finalIsKeptApartFromTheEntries() {
        val table = document.toRoutingTable()
        assertEquals("CONFIG:2", table.finalRule?.policy)
        assertTrue(table.entries.none { it.rule.isFinal })
    }

    @Test
    fun aDocumentWithoutRulesIsAnEmptyTable() {
        val table = "[General]\nipv6 = true\n".toRoutingTable()
        assertTrue(table.entries.isEmpty())
        assertNull(table.finalRule)
    }
}
