// Copyright 2026, Radetski
// SPDX-License-Identifier: GPL-3.0

package features.config

/** Parses the `((A),(B))` body of an OR / AND rule as written by [toBodyText]; null when it is not a valid body. */
fun parseShadowrocketConditionBody(operator: ShadowrocketLogicalOperator, body: String): ShadowrocketCondition.Group? =
    parseShadowrocketLogicalRule("${operator.keyword},${body.trim()},_")?.group

/** Drops blank leaves and groups left empty, so a half-typed editor tree still serialises to a valid body. */
fun ShadowrocketCondition.Group.pruned(): ShadowrocketCondition.Group = copy(
    children = children.mapNotNull { child ->
        when (child) {
            is ShadowrocketCondition.Leaf -> child.takeIf { it.type.isNotBlank() && it.value.isNotBlank() }
            is ShadowrocketCondition.Group -> child.pruned().takeIf { it.children.isNotEmpty() }
        }
    },
)

/** Body text to store in the rule value; blank when nothing usable is left (the editor then skips the save). */
fun ShadowrocketCondition.Group.toStoredBodyText(): String =
    pruned().takeIf { it.children.isNotEmpty() }?.toBodyText().orEmpty()

/** Why Xray cannot route this OR / AND body, or null when it can. */
fun xrayUnsupportedReasonForCondition(operator: ShadowrocketLogicalOperator, body: ShadowrocketCondition.Group): String? {
    val pruned = body.pruned()
    if (pruned.children.isEmpty()) return null
    val rule = ShadowrocketRule(
        lineNumber = 0,
        type = operator.keyword,
        value = pruned.toBodyText(),
        policy = "DIRECT",
        raw = "",
        condition = ShadowrocketCondition.Group(operator, pruned.children),
    )
    return rule.convertToXrayRouteRules(firstId = 0) { it }.unsupportedReason
}
