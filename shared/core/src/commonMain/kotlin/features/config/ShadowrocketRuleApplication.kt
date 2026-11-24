// Copyright 2026, Radetski
// SPDX-License-Identifier: GPL-3.0

package features.config

import engine.xray.RuleApplicationStatus
import engine.xray.combinedRuleApplicationStatus

/**
 * Whether the last configuration applied this profile rule, from the `unappliedRules` of its build. The rule is
 * converted the way the runtime does (an OR / AND rule becomes several Xray rules); the policy does not affect
 * which values are dropped, so it is not resolved here.
 */
fun ShadowrocketRule.applicationStatus(unappliedRules: List<String>): RuleApplicationStatus {
    if (unappliedRules.isEmpty() || isFinal || isDisabled) return RuleApplicationStatus.Applied
    return combinedRuleApplicationStatus(toXrayRouteRules(firstId = 0) { it }, unappliedRules)
}
