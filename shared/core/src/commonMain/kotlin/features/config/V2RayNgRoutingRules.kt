// Copyright 2026, Radetski
// SPDX-License-Identifier: GPL-3.0

package features.config

import engine.network.isIpOrCidrAddress
import features.config.ShadowrocketCondition.Group
import features.config.ShadowrocketCondition.Leaf
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.intOrNull

/**
 * Converter for the routing rule export of v2rayNG: a JSON array of Xray-style rules
 * (`outboundTag`, `domain`, `ip`, `port`, `network`, `protocol`, `enabled`, `ruleType`).
 *
 * Every field of one rule must match (AND) while the entries of one field are alternatives (OR),
 * which is how Xray reads the rule and how the OR / AND lines of a profile are flattened back.
 */
private val V2RayNgRulesFormat = Json { ignoreUnknownKeys = true }

private const val V2RayNgDnsOnlyRuleType = 2

/** Parses the v2rayNG rule array, or null when [this] is not one (so subscriptions and links pass through). */
private fun String.toV2RayNgRulesOrNull(): List<JsonObject>? {
    val trimmed = trim()
    val isArray = trimmed.startsWith("[") && trimmed.endsWith("]")
    val isObject = trimmed.startsWith("{") && trimmed.endsWith("}")
    if (!isArray && !isObject) return null
    val element = runCatching { V2RayNgRulesFormat.parseToJsonElement(trimmed) }.getOrNull() ?: return null
    // One copied rule is an object; an export is an array of them.
    val rules = when (element) {
        is JsonArray -> element.map { it as? JsonObject ?: return null }
        is JsonObject -> listOf(element)
        else -> return null
    }
    if (rules.isEmpty() || rules.any { rule -> rule.text("outboundTag") == null }) return null
    return rules
}

/**
 * Converts a v2rayNG rule export into a Shadowrocket profile, or returns null when the text is not one.
 * [fallbackName] names the profile unless it is blank or the generic `Config`.
 */
fun String.toV2RayNgRoutingConfOrNull(fallbackName: String): String? {
    val rules = toV2RayNgRulesOrNull() ?: return null
    val lines = mutableListOf<String>()
    for (rule in rules) {
        if (rule.flag("enabled") == false) continue
        if (rule.int("ruleType") == V2RayNgDnsOnlyRuleType) continue
        val policy = when (rule.text("outboundTag")?.lowercase()) {
            "block" -> "REJECT"
            "direct" -> "DIRECT"
            "proxy" -> "PROXY"
            else -> continue
        }
        val remarks = rule.text("remarks")?.replace(Regex("""\s+"""), " ")?.trim().orEmpty()
            .let { if (it.isEmpty()) "" else "$RuleRemarksSeparator$it" }
        val fields = rule.conditionFields()
        when {
            fields.isEmpty() -> {
                lines += "FINAL,$policy$remarks"
                break
            }
            fields.size == 1 && fields.single().size == 1 -> fields.single().single().let { leaf ->
                lines += "${leaf.type},${leaf.value},$policy$remarks"
            }
            fields.size == 1 -> lines += "OR,${Group(ShadowrocketLogicalOperator.Or, fields.single()).toBodyText()},$policy$remarks"
            else -> {
                val children = fields.map { leaves -> if (leaves.size == 1) leaves.single() else Group(ShadowrocketLogicalOperator.Or, leaves) }
                lines += "AND,${Group(ShadowrocketLogicalOperator.And, children).toBodyText()},$policy$remarks"
            }
        }
    }
    if (lines.none { it.startsWith("FINAL,") }) lines += "FINAL,PROXY"

    val name = fallbackName.trim().takeIf { it.isNotEmpty() && it != "Config" }
    return buildString {
        if (name != null) appendLine("# profile-name: $name")
        appendLine("[Rule]")
        lines.forEach(::appendLine)
        if (name != null) {
            appendLine("[SKIPI]")
            appendLine("profile-name = $name")
        }
    }
}

/** The non-empty condition fields of one rule, each a list of alternatives; empty means the rule matches everything. */
private fun JsonObject.conditionFields(): List<List<Leaf>> {
    val domains = mutableListOf<Leaf>()
    val ips = mutableListOf<Leaf>()
    stringList("ip").forEach { value -> value.ipLeaf()?.let(ips::add) }
    stringList("domain").forEach { value ->
        // Some exports put geoip tags into the domain list; they are still address conditions.
        if (value.startsWith("geoip:", ignoreCase = true)) value.ipLeaf()?.let(ips::add) else value.domainLeaf()?.let(domains::add)
    }
    val ports = text("port").orEmpty().split(',').map(String::trim).filter(String::isNotEmpty)
    val portLeaves = if (ports.any { it == "0-65535" || it == "1-65535" }) emptyList() else ports.map { Leaf("DST-PORT", it) }
    val networks = text("network").orEmpty().split(',').map { it.trim().lowercase() }.filter(String::isNotEmpty).toSet()
    val networkLeaves = if ("tcp" in networks && "udp" in networks) {
        emptyList()
    } else {
        networks.filter { it == "tcp" || it == "udp" }.map { Leaf("NETWORK", it) }
    }
    val protocols = stringList("protocol").flatMap { it.split(',') }.map { it.trim().lowercase() }.filter(String::isNotEmpty).distinct()
    return listOf(domains, ips, portLeaves, networkLeaves, protocols.map { Leaf("PROTOCOL", it) })
        .map { leaves -> leaves.filter { it.value.isSafeRuleValue() }.distinct() }
        .filter(List<Leaf>::isNotEmpty)
}

// Commas and parentheses would split or close a rule line, so such values cannot be written.
private fun String.isSafeRuleValue(): Boolean = none { it == ',' || it == '(' || it == ')' }

private fun String.domainLeaf(): Leaf? {
    val value = trim()
    return when {
        value.isEmpty() -> null
        value.startsWith("geosite:", ignoreCase = true) || value.startsWith("ext:", ignoreCase = true) ||
            value.startsWith("regexp:", ignoreCase = true) -> Leaf("DOMAIN-SET", value)
        value.startsWith("domain:", ignoreCase = true) -> Leaf("DOMAIN-SUFFIX", value.substringAfter(':'))
        value.startsWith("full:", ignoreCase = true) -> Leaf("DOMAIN", value.substringAfter(':'))
        value.startsWith("keyword:", ignoreCase = true) -> Leaf("DOMAIN-KEYWORD", value.substringAfter(':'))
        '*' in value || '?' in value -> Leaf("DOMAIN-WILDCARD", value)
        // A bare entry is a substring match in Xray, not a suffix.
        else -> Leaf("DOMAIN-KEYWORD", value)
    }.takeIf { leaf -> leaf == null || leaf.value.isNotBlank() }
}

private fun String.ipLeaf(): Leaf? {
    val value = trim()
    return when {
        value.startsWith("geoip:", ignoreCase = true) -> Leaf("GEOIP", value.substringAfter(':'))
        value.startsWith("ext:", ignoreCase = true) -> Leaf("GEOIP", value)
        isIpOrCidrAddress(value) -> Leaf(if (':' in value) "IP-CIDR6" else "IP-CIDR", value)
        else -> null
    }?.takeIf { it.value.isNotBlank() }
}

private fun JsonElement?.primitiveOrNull(): JsonPrimitive? = (this as? JsonPrimitive)?.takeIf { it !is JsonNull }

private fun JsonObject.text(key: String): String? = this[key].primitiveOrNull()?.content

private fun JsonObject.flag(key: String): Boolean? = this[key].primitiveOrNull()?.booleanOrNull

private fun JsonObject.int(key: String): Int? = this[key].primitiveOrNull()?.intOrNull

private fun JsonObject.stringList(key: String): List<String> = (this[key] as? JsonArray)
    ?.mapNotNull { it.primitiveOrNull()?.content?.trim() }
    .orEmpty()
    .filter(String::isNotEmpty)
