// Copyright 2026, Radetski
// SPDX-License-Identifier: GPL-3.0

package features.config

import features.clipboard.ClipboardImportMode
import kotlin.test.Test
import kotlin.test.assertEquals

/** The batched import must write exactly what adding the rows one at a time writes. */
class RoutingRowsImportEquivalenceTest {
    private fun referenceImport(
        document: String,
        rows: List<RoutingTableRow>,
        finalPolicy: String?,
        mode: ClipboardImportMode,
    ): Pair<String, Int> {
        val before = document.analyzeShadowrocketConfig().rules
        var result = document
        val known = mutableSetOf<String>()
        when (mode) {
            ClipboardImportMode.Replace ->
                before.sortedByDescending(ShadowrocketRule::lineNumber).forEach { rule ->
                    result = result.withoutShadowrocketRuleLine(rule.lineNumber)
                }
            ClipboardImportMode.Merge -> before.mapTo(known) { it.toShadowrocketLine().substringBefore(RuleRemarksSeparator) }
        }
        var added = 0
        for (row in rows) {
            val line = row.toShadowrocketLineOrNull()?.line ?: continue
            if (!known.add(line.substringBefore(RuleRemarksSeparator))) continue
            result = result.withShadowrocketRuleAdded(line)
            added++
        }
        if (mode == ClipboardImportMode.Replace) {
            val policy = finalPolicy ?: before.lastOrNull(ShadowrocketRule::isFinal)?.policy
            if (policy != null) result = result.withShadowrocketRuleAdded("FINAL,$policy")
        }
        return result to added
    }

    private val documents = listOf(
        "[General]\nipv6 = true\n\n[Rule]\n# mine\nDOMAIN-SUFFIX,old.com,DIRECT // Old\nDST-PORT,22,DIRECT\nFINAL,CONFIG:2\n\n[Host]\na = 1.1.1.1\n",
        "[General]\nipv6 = true\n",
        "",
        "[Rule]\nDOMAIN,a.com,DIRECT",
        "[Rule]\nDOMAIN,a.com,DIRECT\n\n\n",
        "[Rule]\n",
        "[Rule]\nDOMAIN,a.com,DIRECT\n[Host]\nx = 1.1.1.1\n",
        "[Rule]\nDOMAIN,a.com,DIRECT\nFINAL,DIRECT\n[Host]\nx = 1.1.1.1\n",
        "[Host]\nx = 1.1.1.1\n[Rule]\nDOMAIN,a.com,DIRECT\n\n[Proxy Group]\nA = select,DIRECT",
        "# only a comment",
    )

    private fun rows(count: Int) = (0 until count).map { index ->
        RoutingTableRow(name = "Row $index", policy = if (index % 2 == 0) "PROXY" else "DIRECT", domains = listOf("domain:site$index.com"))
    } + RoutingTableRow(name = "Old", policy = "DIRECT", domains = listOf("domain:old.com"))

    @Test
    fun batchedImportMatchesAddingRowsOneByOne() {
        for (document in documents) {
            for (mode in ClipboardImportMode.entries) {
                for (finalPolicy in listOf(null, "DIRECT")) {
                    for (count in listOf(0, 1, 3, 40)) {
                        val rows = rows(count)
                        val (expected, expectedAdded) = referenceImport(document, rows, finalPolicy, mode)
                        val actual = document.withRoutingRowsImported(rows, finalPolicy, mode)
                        assertEquals(expected, actual.document, "document=${document.take(40)} mode=$mode final=$finalPolicy rows=$count")
                        assertEquals(expectedAdded, actual.added)
                    }
                }
            }
        }
    }
}
