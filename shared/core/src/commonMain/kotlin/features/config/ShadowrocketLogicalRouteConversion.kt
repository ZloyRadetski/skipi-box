// Copyright 2026, Radetski
// SPDX-License-Identifier: GPL-3.0

package features.config

import features.routing.model.RouteRule

/** Result of translating one rule to Xray; [unsupportedReason] explains why [rules] is empty for an OR / AND rule. */
class XrayRouteConversion(val rules: List<RouteRule>, val unsupportedReason: String? = null)

private const val MaxXrayRulesPerLogicalRule = 64

private class UnsupportedLogicalRule(val reason: String) : Exception(reason)

/**
 * Translates a plain or an OR / AND rule. A logical rule is flattened into "OR of ANDs": every AND of conditions on
 * different Xray fields becomes one Xray rule, and the ORed rules share the outbound, which is an OR because the
 * first match wins. Alternatives that differ only in one list field (domains, addresses, processes, ports, protocols)
 * are folded into one Xray rule, so a long list of values stays one rule. Combinations Xray cannot express (two
 * conditions on one field joined by AND, unsupported leaf types, too many unfoldable alternatives) make the whole
 * rule unsupported rather than silently narrowing it.
 */
fun ShadowrocketRule.convertToXrayRouteRules(
    firstId: Int,
    resolvePolicy: (String) -> String,
): XrayRouteConversion {
    if (isDisabled) return XrayRouteConversion(emptyList())
    val group = condition
    if (group == null) {
        return XrayRouteConversion(listOfNotNull(toXrayRouteRule(firstId, resolvePolicy)))
    }
    val outboundTag = resolvePolicy(policy)
    val routes = try {
        group.toAlternatives(this, outboundTag)
    } catch (unsupported: UnsupportedLogicalRule) {
        return XrayRouteConversion(emptyList(), unsupported.reason)
    }
    val remarks = "$type,$value"
    return XrayRouteConversion(
        routes.mapIndexed { index, rule -> rule.copy(id = firstId + index, remarks = remarks) },
    )
}

fun ShadowrocketRule.toXrayRouteRules(firstId: Int, resolvePolicy: (String) -> String): List<RouteRule> =
    convertToXrayRouteRules(firstId, resolvePolicy).rules

/** Every leaf condition of an OR / AND rule (empty for a plain rule). */
fun ShadowrocketRule.logicalLeaves(): List<ShadowrocketCondition.Leaf> =
    condition?.leaves()?.distinct().orEmpty()

private fun ShadowrocketCondition.leaves(): List<ShadowrocketCondition.Leaf> = when (this) {
    is ShadowrocketCondition.Leaf -> listOf(this)
    is ShadowrocketCondition.Group -> children.flatMap { it.leaves() }
}

/** The Xray rules that match this condition, as alternatives (any of them matches). */
private fun ShadowrocketCondition.toAlternatives(rule: ShadowrocketRule, outboundTag: String): List<RouteRule> = when (this) {
    is ShadowrocketCondition.Leaf -> listOf(
        ShadowrocketRule(
            lineNumber = rule.lineNumber,
            type = type,
            value = value,
            policy = rule.policy,
            option = option,
            raw = "",
        ).toXrayRouteRule(0) { outboundTag }
            ?: throw UnsupportedLogicalRule("$type cannot be used in an OR/AND rule"),
    )

    is ShadowrocketCondition.Group -> when (operator) {
        ShadowrocketLogicalOperator.Or ->
            children.flatMap { child -> child.toAlternatives(rule, outboundTag) }
                .foldAlternatives()
                .also { alternatives -> if (alternatives.size > MaxXrayRulesPerLogicalRule) throw tooManyCombinations() }

        ShadowrocketLogicalOperator.And ->
            children.fold(listOf(RouteRule(outboundTag = outboundTag))) { accumulated, child ->
                val options = child.toAlternatives(rule, outboundTag)
                if (accumulated.size.toLong() * options.size > MaxXrayRulesPerLogicalRule) throw tooManyCombinations()
                accumulated.flatMap { prefix ->
                    options.map { option ->
                        prefix.andWith(option)
                            ?: throw UnsupportedLogicalRule("Xray cannot AND two conditions of the same kind (for example two domains)")
                    }
                }.foldAlternatives()
            }
    }
}

private fun tooManyCombinations() = UnsupportedLogicalRule("Too many OR/AND combinations for Xray")

/** AND of two matches; null when both constrain the same Xray field (a field is an OR list, so AND cannot be expressed). */
private fun RouteRule.andWith(other: RouteRule): RouteRule? {
    if (domain.isNotEmpty() && other.domain.isNotEmpty()) return null
    if (ip.isNotEmpty() && other.ip.isNotEmpty()) return null
    if (process.isNotEmpty() && other.process.isNotEmpty()) return null
    if (port.isNotBlank() && other.port.isNotBlank()) return null
    if (protocol.isNotBlank() && other.protocol.isNotBlank()) return null
    if (network.isNotBlank() && other.network.isNotBlank()) return null
    return copy(
        domain = domain.ifEmpty { other.domain },
        ip = ip.ifEmpty { other.ip },
        process = process.ifEmpty { other.process },
        port = port.ifBlank { other.port },
        protocol = protocol.ifBlank { other.protocol },
        network = network.ifBlank { other.network },
    )
}

/**
 * Folds alternatives that differ in one list field into one rule: ports and protocols are joined with commas, and
 * rules that only hold domains (or only addresses, or only processes) share one list. Repeats until nothing folds,
 * because a protocol list can only be joined once the ports of its rules are.
 */
private fun List<RouteRule>.foldAlternatives(): List<RouteRule> {
    var current = this
    while (true) {
        val folded = current
            .foldWhere(
                key = { rule -> rule.copy(port = "").takeIf { rule.port.isNotBlank() } },
                join = { a, b -> a.copy(port = joinCsv(a.port, b.port)) },
            )
            .foldWhere(
                key = { rule -> rule.copy(protocol = "").takeIf { rule.protocol.isNotBlank() } },
                join = { a, b -> a.copy(protocol = joinCsv(a.protocol, b.protocol)) },
            )
            .foldWhere(
                key = { rule -> rule.addressKey() },
                join = { a, b -> a.copy(domain = (a.domain + b.domain).distinct(), ip = (a.ip + b.ip).distinct(), process = (a.process + b.process).distinct()) },
            )
        if (folded.size == current.size) return folded
        current = folded
    }
}

/** Rules that hold exactly one kind of address share a key per kind, so only the lists are joined. */
private fun RouteRule.addressKey(): RouteRule? {
    val kinds = listOf(domain.isNotEmpty(), ip.isNotEmpty(), process.isNotEmpty()).count { it }
    return if (kinds == 1) copy(domain = domain.presence(), ip = ip.presence(), process = process.presence()) else null
}

/** A list reduced to whether it holds anything, so rules that differ only in the values of one list share a key. */
private fun List<String>.presence(): List<String> = if (isEmpty()) emptyList() else listOf("")

/** Joins every rule whose [key] (null: never joined) equals that of an earlier one into it, keeping first positions. */
private fun List<RouteRule>.foldWhere(
    key: (RouteRule) -> RouteRule?,
    join: (RouteRule, RouteRule) -> RouteRule,
): List<RouteRule> {
    val result = ArrayList<RouteRule>(size)
    val position = HashMap<RouteRule, Int>()
    for (rule in this) {
        val ruleKey = key(rule)?.copy(id = 0, remarks = "")
        val at = ruleKey?.let(position::get)
        when {
            ruleKey == null -> result += rule
            at == null -> {
                position[ruleKey] = result.size
                result += rule
            }
            else -> result[at] = join(result[at], rule)
        }
    }
    return result
}

private fun joinCsv(first: String, second: String): String =
    (first.split(',') + second.split(',')).map(String::trim).filter(String::isNotEmpty).distinct().joinToString(",")
