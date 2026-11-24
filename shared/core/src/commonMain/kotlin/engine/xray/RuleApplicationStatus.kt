// Copyright 2026, Radetski
// SPDX-License-Identifier: GPL-3.0

package engine.xray

import features.routing.model.RouteRule

/** How far the generated Xray configuration applies one routing rule. */
enum class RuleApplication {
    /** Nothing about the rule was dropped or redirected. */
    Applied,

    /** Some of the rule's domain or address values were dropped; the rest of the rule still routes. */
    PartlySkipped,

    /** The rule has nothing left to match, so it is not in the configuration at all. */
    Skipped,

    /** The rule is in the configuration, but its target is unknown and it routes to the proxy instead. */
    UnknownTarget,
}

/** [values] are the dropped domain/address values; [message] is the unknown-target text when [application] is [RuleApplication.UnknownTarget]. */
data class RuleApplicationStatus(
    val application: RuleApplication,
    val values: List<String> = emptyList(),
    val message: String? = null,
) {
    val isApplied: Boolean get() = application == RuleApplication.Applied

    companion object {
        val Applied = RuleApplicationStatus(RuleApplication.Applied)
    }
}

private const val UnknownTargetMarker = "\": unknown target '"
private const val UnknownTargetPrefix = "Rule \""

/**
 * Tells whether the configuration applied [rule], from the [unappliedRules] its build reported (values whose
 * resource is missing, and "Rule "<remarks>": unknown target ..." messages). A rule whose every domain and address
 * value was dropped and that has no other condition is skipped; with other conditions, or with only some values
 * dropped, it is partly skipped. A rule with blank remarks cannot be matched to an unknown-target message.
 */
fun ruleApplicationStatus(rule: RouteRule, unappliedRules: List<String>): RuleApplicationStatus {
    if (unappliedRules.isEmpty()) return RuleApplicationStatus.Applied
    val unapplied = unappliedRules.map(String::trim).toSet()
    val values = (rule.domain + rule.ip).map(String::trim).filter(String::isNotEmpty).distinct()
    val dropped = values.filter { it in unapplied }
    val hasOtherConditions = rule.process.any { it.isNotBlank() } ||
        rule.port.isNotBlank() || rule.network.isNotBlank() || rule.protocol.isNotBlank()
    if (dropped.isNotEmpty()) {
        val application = if (dropped.size == values.size && !hasOtherConditions) {
            RuleApplication.Skipped
        } else {
            RuleApplication.PartlySkipped
        }
        return RuleApplicationStatus(application, dropped)
    }
    val remarks = rule.remarks.trim()
    if (remarks.isNotEmpty()) {
        val prefix = "$UnknownTargetPrefix$remarks$UnknownTargetMarker"
        val message = unappliedRules.firstOrNull { it.startsWith(prefix) }
        if (message != null) return RuleApplicationStatus(RuleApplication.UnknownTarget, message = message)
    }
    return RuleApplicationStatus.Applied
}

/**
 * One status for a rule that expands to several Xray rules (an OR / AND rule): skipped when every part is,
 * applied when none is affected, partly skipped otherwise.
 */
fun combinedRuleApplicationStatus(rules: List<RouteRule>, unappliedRules: List<String>): RuleApplicationStatus {
    if (rules.isEmpty() || unappliedRules.isEmpty()) return RuleApplicationStatus.Applied
    val statuses = rules.map { ruleApplicationStatus(it, unappliedRules) }
    val affected = statuses.filterNot(RuleApplicationStatus::isApplied)
    if (affected.isEmpty()) return RuleApplicationStatus.Applied
    val values = affected.flatMap(RuleApplicationStatus::values).distinct()
    val message = affected.firstNotNullOfOrNull(RuleApplicationStatus::message)
    val application = when {
        statuses.all { it.application == RuleApplication.Skipped } -> RuleApplication.Skipped
        affected.all { it.application == RuleApplication.UnknownTarget } -> RuleApplication.UnknownTarget
        else -> RuleApplication.PartlySkipped
    }
    return RuleApplicationStatus(application, values, message)
}
