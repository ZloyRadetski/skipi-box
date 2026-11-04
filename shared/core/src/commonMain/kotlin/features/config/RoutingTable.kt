// Copyright 2026, Radetski
// SPDX-License-Identifier: GPL-3.0

package features.config

/** One rule of the table: [row] is null when the rule does not fit the table and is edited as a rule. */
class RoutingTableEntry(val rule: ShadowrocketRule, val row: RoutingTableRow?) {
    val lineNumber: Int
        get() = rule.lineNumber
}

/** The rules of a profile as the routing table shows them, in document order, with FINAL apart. */
class RoutingTable(val entries: List<RoutingTableEntry>, val finalRule: ShadowrocketRule?)

fun String.toRoutingTable(): RoutingTable {
    val rules = analyzeShadowrocketConfig().rules
    return RoutingTable(
        entries = rules.filterNot(ShadowrocketRule::isFinal).map { rule -> RoutingTableEntry(rule, rule.toRoutingTableRow()) },
        finalRule = rules.lastOrNull(ShadowrocketRule::isFinal),
    )
}
