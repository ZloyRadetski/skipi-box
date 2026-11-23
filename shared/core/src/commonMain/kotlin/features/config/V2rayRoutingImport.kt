// Copyright 2026, Radetski
// SPDX-License-Identifier: GPL-3.0

package features.config

import features.clipboard.ClipboardImportMode

/**
 * Import of the routing rules of v2rayN into table rows. A rule keeps its name and its switch. As in v2rayN, a request
 * matches when it hits any of the domains, addresses or processes of a rule and also fits its port, network and
 * protocol, which is how a [RoutingTableRow] reads. A rule that matches everything is the final policy, as in v2rayN.
 */
class V2rayRoutingImport(
    val rows: List<RoutingTableRow>,
    /** The policy of the rule that matches everything, or null when the export has none. */
    val finalPolicy: String?,
    val skipped: List<V2rayRoutingSkippedRule>,
    /** Rules after the catch-all rule: v2rayN never reaches them. */
    val droppedAfterFinal: Int,
    val totalRules: Int,
)

enum class V2rayRoutingSkipReason {
    /** The rule is only for DNS. */
    DnsOnly,

    /** The rule sends traffic to an outbound this profile does not know; the tag is in the detail. */
    UnknownOutbound,

    /** The rule applies to one inbound only, and rules here have no such field, so it would match more than intended. */
    InboundTag,
}

class V2rayRoutingSkippedRule(val name: String, val reason: V2rayRoutingSkipReason, val detail: String = "")

/** The three outbounds every export has; anything else is a server or a profile of the exporting client. */
fun defaultV2rayOutbound(tag: String): String? = when (tag.trim().lowercase()) {
    "block" -> "REJECT"
    "direct" -> "DIRECT"
    "proxy" -> "PROXY"
    else -> null
}

class UnwritableRowValue(val ruleName: String, val value: String)

class RoutingRowsImportResult(
    val document: String,
    /** Rows written as rules. */
    val added: Int,
    /** Values left out because a profile line cannot hold them, such as a regular expression with parentheses. */
    val unwritable: List<UnwritableRowValue>,
)

/**
 * Writes [rows] into the profile. Merge puts them before FINAL and leaves what is there, skipping a row that is
 * already a rule. Replace removes every rule first and writes FINAL again, with [finalPolicy] or else the current one.
 */
fun String.withRoutingRowsImported(
    rows: List<RoutingTableRow>,
    finalPolicy: String?,
    mode: ClipboardImportMode,
): RoutingRowsImportResult {
    val before = analyzeShadowrocketConfig().rules
    val known = mutableSetOf<String>()
    var lines: MutableList<String>? = null
    when (mode) {
        ClipboardImportMode.Replace -> if (before.isNotEmpty()) {
            // Remove from the bottom so the line numbers of the rules still to go stay valid.
            lines = lines().toMutableList().also { list ->
                before.sortedByDescending(ShadowrocketRule::lineNumber).forEach { rule -> list.removeAt(rule.lineNumber - 1) }
                list.normalizeTrailingBlankLines()
            }
        }

        ClipboardImportMode.Merge -> before.mapTo(known) { it.toShadowrocketLine().substringBefore(RuleRemarksSeparator) }
    }

    // One split and one join for the whole import: each rule goes where withShadowrocketRuleAdded would put it
    // (before the first FINAL, or else at the end of [Rule]), without re-reading the document per row.
    val inserter = ShadowrocketRuleInserter(
        lines = lines ?: lines().toMutableList(),
        finalLineIndex = if (mode == ClipboardImportMode.Merge) before.firstOrNull(ShadowrocketRule::isFinal)?.let { it.lineNumber - 1 } else null,
        alreadyEdited = lines != null,
    )
    var added = 0
    val unwritable = mutableListOf<UnwritableRowValue>()
    for (row in rows) {
        row.unwritableValues().mapTo(unwritable) { value -> UnwritableRowValue(row.name, value) }
        val line = row.toShadowrocketLineOrNull()?.line ?: continue
        if (!known.add(line.substringBefore(RuleRemarksSeparator))) continue
        inserter.add(line)
        added++
    }

    if (mode == ClipboardImportMode.Replace) {
        val policy = finalPolicy ?: before.lastOrNull(ShadowrocketRule::isFinal)?.policy
        if (policy != null) inserter.add("FINAL,$policy")
    }
    val document = if (inserter.changed) inserter.lines.joinToString("\n") else this
    return RoutingRowsImportResult(document, added, unwritable)
}

/** Drops trailing blank lines and ends the list with one empty element, as `joinToString("\n").trimEnd() + "\n"` does. */
private fun MutableList<String>.normalizeTrailingBlankLines() {
    while (isNotEmpty() && last().isBlank()) removeAt(lastIndex)
    if (isEmpty()) {
        add("")
    } else {
        this[lastIndex] = last().trimEnd()
    }
    add("")
}

/** [withShadowrocketRuleAdded] for many rules over one line list. */
private class ShadowrocketRuleInserter(
    val lines: MutableList<String>,
    private var finalLineIndex: Int?,
    alreadyEdited: Boolean,
) {
    var changed: Boolean = alreadyEdited
        private set
    private var ruleHeaderIndex = -1
    private var nextHeaderIndex = -1

    fun add(rawRule: String) {
        changed = true
        val rule = rawRule.trim()
        val finalIndex = finalLineIndex
        if (finalIndex != null) {
            lines.add(finalIndex, rule)
            finalLineIndex = finalIndex + 1
            lines.normalizeTrailingBlankLines()
            return
        }
        if (ruleHeaderIndex < 0) {
            ruleHeaderIndex = lines.indexOfFirst { line -> line.shadowrocketSectionHeaderNameOrNull().equals("Rule", ignoreCase = true) }
            if (ruleHeaderIndex >= 0) {
                nextHeaderIndex = (ruleHeaderIndex + 1 until lines.size).firstOrNull { index ->
                    val trimmed = lines[index].trim()
                    trimmed.startsWith("[") && trimmed.endsWith("]")
                } ?: -1
            }
        }
        if (ruleHeaderIndex >= 0) {
            lines.add(if (nextHeaderIndex < 0) lines.size else nextHeaderIndex, rule)
            if (nextHeaderIndex >= 0) nextHeaderIndex++
        } else {
            if (lines.isNotEmpty() && lines.last().isNotBlank()) lines += ""
            ruleHeaderIndex = lines.size
            lines += "[Rule]"
            lines += rule
        }
        lines.normalizeTrailingBlankLines()
    }
}
