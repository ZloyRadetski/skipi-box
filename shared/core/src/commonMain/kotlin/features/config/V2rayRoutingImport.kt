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
    var document = this
    val known = mutableSetOf<String>()
    when (mode) {
        ClipboardImportMode.Replace ->
            before.sortedByDescending(ShadowrocketRule::lineNumber).forEach { rule ->
                document = document.withoutShadowrocketRuleLine(rule.lineNumber)
            }

        ClipboardImportMode.Merge -> before.mapTo(known) { it.toShadowrocketLine().substringBefore(RuleRemarksSeparator) }
    }

    var added = 0
    val unwritable = mutableListOf<UnwritableRowValue>()
    for (row in rows) {
        row.unwritableValues().mapTo(unwritable) { value -> UnwritableRowValue(row.name, value) }
        val line = row.toShadowrocketLineOrNull()?.line ?: continue
        if (!known.add(line.substringBefore(RuleRemarksSeparator))) continue
        document = document.withShadowrocketRuleAdded(line)
        added++
    }

    if (mode == ClipboardImportMode.Replace) {
        val policy = finalPolicy ?: before.lastOrNull(ShadowrocketRule::isFinal)?.policy
        if (policy != null) document = document.withShadowrocketRuleAdded("FINAL,$policy")
    }
    return RoutingRowsImportResult(document, added, unwritable)
}
