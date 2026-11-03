// Copyright 2026, Radetski
// SPDX-License-Identifier: GPL-3.0

package features.config

import features.routing.model.RouteRule

/** Result of translating one rule to Xray; [unsupportedReason] explains why [rules] is empty for an OR / AND rule. */
class XrayRouteConversion(val rules: List<RouteRule>, val unsupportedReason: String? = null)

private const val MaxXrayRulesPerLogicalRule = 64

/**
 * Translates a plain or an OR / AND rule. A logical rule is flattened into "OR of ANDs": every AND of conditions on
 * different Xray fields becomes one Xray rule, and the ORed rules share the outbound, which is an OR because the
 * first match wins. Combinations Xray cannot express (two conditions on one field joined by AND, unsupported leaf
 * types) make the whole rule unsupported rather than silently narrowing it.
 */
fun ShadowrocketRule.convertToXrayRouteRules(
    firstId: Int,
    resolvePolicy: (String) -> String,
): XrayRouteConversion {
    val group = condition
    if (group == null) {
        return XrayRouteConversion(listOfNotNull(toXrayRouteRule(firstId, resolvePolicy)))
    }
    fun unsupported(reason: String) = XrayRouteConversion(emptyList(), reason)

    val conjunctions = group.toConjunctions() ?: return unsupported("Too many OR/AND combinations for Xray")
    val outboundTag = resolvePolicy(policy)
    val routes = mutableListOf<RouteRule>()
    for (conjunction in conjunctions) {
        var merged = RouteRule(outboundTag = outboundTag)
        for (leaf in conjunction) {
            val leafRoute = ShadowrocketRule(
                lineNumber = lineNumber,
                type = leaf.type,
                value = leaf.value,
                policy = policy,
                option = leaf.option,
                raw = "",
            ).toXrayRouteRule(0) { outboundTag } ?: return unsupported("${leaf.type} cannot be used in an OR/AND rule")
            merged = merged.andWith(leafRoute)
                ?: return unsupported("Xray cannot AND two conditions of the same kind (for example two domains)")
        }
        routes += merged
    }
    val remarks = "$type,$value"
    return XrayRouteConversion(
        routes.coalesceSingleFieldRules().mapIndexed { index, rule -> rule.copy(id = firstId + index, remarks = remarks) },
    )
}

fun ShadowrocketRule.toXrayRouteRules(firstId: Int, resolvePolicy: (String) -> String): List<RouteRule> =
    convertToXrayRouteRules(firstId, resolvePolicy).rules

/** Every leaf condition of an OR / AND rule (empty for a plain rule or one with too many combinations). */
fun ShadowrocketRule.logicalLeaves(): List<ShadowrocketCondition.Leaf> =
    condition?.toConjunctions()?.flatten()?.distinct().orEmpty()

private fun ShadowrocketCondition.toConjunctions(): List<List<ShadowrocketCondition.Leaf>>? {
    val result = when (this) {
        is ShadowrocketCondition.Leaf -> listOf(listOf(this))
        is ShadowrocketCondition.Group -> when (operator) {
            ShadowrocketLogicalOperator.Or -> children.flatMap { child -> child.toConjunctions() ?: return null }
            ShadowrocketLogicalOperator.And -> children.fold(listOf(emptyList<ShadowrocketCondition.Leaf>())) { acc, child ->
                val options = child.toConjunctions() ?: return null
                if (acc.size.toLong() * options.size > MaxXrayRulesPerLogicalRule) return null
                acc.flatMap { prefix -> options.map { option -> prefix + option } }
            }
        }
    }
    return result.takeIf { it.size <= MaxXrayRulesPerLogicalRule }
}

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

/** Folds ORed rules that only differ in one list field (all domains, all IPs, all processes) into one Xray rule. */
private fun List<RouteRule>.coalesceSingleFieldRules(): List<RouteRule> {
    fun RouteRule.plain() = port.isBlank() && protocol.isBlank() && network.isBlank()
    fun RouteRule.onlyDomain() = domain.isNotEmpty() && ip.isEmpty() && process.isEmpty() && plain()
    fun RouteRule.onlyIp() = ip.isNotEmpty() && domain.isEmpty() && process.isEmpty() && plain()
    fun RouteRule.onlyProcess() = process.isNotEmpty() && domain.isEmpty() && ip.isEmpty() && plain()

    val domains = filter { it.onlyDomain() }.flatMap(RouteRule::domain).distinct()
    val ips = filter { it.onlyIp() }.flatMap(RouteRule::ip).distinct()
    val processes = filter { it.onlyProcess() }.flatMap(RouteRule::process).distinct()
    val result = mutableListOf<RouteRule>()
    var domainDone = false
    var ipDone = false
    var processDone = false
    for (rule in this) {
        when {
            rule.onlyDomain() -> if (!domainDone) {
                domainDone = true
                result += rule.copy(domain = domains)
            }
            rule.onlyIp() -> if (!ipDone) {
                ipDone = true
                result += rule.copy(ip = ips)
            }
            rule.onlyProcess() -> if (!processDone) {
                processDone = true
                result += rule.copy(process = processes)
            }
            else -> result += rule
        }
    }
    return result
}
