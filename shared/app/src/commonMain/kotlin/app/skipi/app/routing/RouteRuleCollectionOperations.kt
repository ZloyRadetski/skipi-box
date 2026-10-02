// Copyright 2026, Radetski
// SPDX-License-Identifier: GPL-3.0

package app.skipi.app.routing

import features.routing.model.RouteRule

data class RouteRuleUpsertResult(
    val rules: List<RouteRule>,
    val nextRouteRuleId: Int,
)

/** Inserts a route rule at the end or replaces matching IDs without changing row order. */
fun upsertRouteRule(
    rules: List<RouteRule>,
    nextRouteRuleId: Int,
    rule: RouteRule,
): RouteRuleUpsertResult {
    val exists = rules.any { it.id == rule.id }
    return RouteRuleUpsertResult(
        rules = if (exists) {
            rules.map { current -> if (current.id == rule.id) rule else current }
        } else {
            rules + rule
        },
        nextRouteRuleId = if (exists) nextRouteRuleId else maxOf(nextRouteRuleId, rule.id + 1),
    )
}

fun removeRouteRule(
    rules: List<RouteRule>,
    ruleId: Int,
): List<RouteRule> {
    if (rules.none { it.id == ruleId }) return rules
    return rules.filterNot { it.id == ruleId }
}

fun setRouteRuleEnabled(
    rules: List<RouteRule>,
    ruleId: Int,
    enabled: Boolean,
): List<RouteRule> {
    if (rules.none { it.id == ruleId && it.enabled != enabled }) return rules
    return rules.map { rule ->
        if (rule.id == ruleId) rule.copy(enabled = enabled) else rule
    }
}

fun toggleRouteRuleEnabled(
    rules: List<RouteRule>,
    ruleId: Int,
): List<RouteRule> {
    val current = rules.firstOrNull { it.id == ruleId } ?: return rules
    return setRouteRuleEnabled(rules, ruleId, !current.enabled)
}

fun moveRouteRule(
    rules: List<RouteRule>,
    fromIndex: Int,
    toIndex: Int,
): List<RouteRule> {
    if (fromIndex !in rules.indices || toIndex !in rules.indices || fromIndex == toIndex) {
        return rules
    }

    return rules.toMutableList().apply {
        add(toIndex, removeAt(fromIndex))
    }
}
