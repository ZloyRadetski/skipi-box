// Copyright 2026, Radetski
// SPDX-License-Identifier: GPL-3.0

package features.config

import engine.network.isIpOrCidrAddress
import features.config.ShadowrocketCondition.Group
import features.config.ShadowrocketCondition.Leaf
import features.config.ShadowrocketLogicalOperator.And
import features.config.ShadowrocketLogicalOperator.Or

/**
 * One line of the routing table: the rule as v2rayN shows it, with a field for domains, addresses, processes, port,
 * network and protocol. A request matches when it hits any of the domains, addresses or processes (OR) and also fits
 * the port, the network and the protocol when those are set (AND). It is stored as an ordinary profile rule, so the
 * profile format stays what it was.
 */
data class RoutingTableRow(
    val name: String = "",
    val enabled: Boolean = true,
    /** The policy as written in the profile: PROXY, DIRECT, REJECT, a group or a server name. */
    val policy: String = "PROXY",
    /** Xray notation: `domain:`, `full:`, `keyword:`, `regexp:`, `geosite:`, `ext:`, a `*` pattern, or a bare substring. */
    val domains: List<String> = emptyList(),
    /** An address, a CIDR, `geoip:` or `ext:`. */
    val ips: List<String> = emptyList(),
    val processes: List<String> = emptyList(),
    /** Ports and ranges separated by commas, as in Xray. */
    val port: String = "",
    /** `tcp`, `udp`, or empty for both. */
    val network: String = "",
    val protocols: List<String> = emptyList(),
    /** Profile options other than the on/off switch (such as `no-resolve`), kept so that an edit does not drop them. */
    val extraOption: String? = null,
) {
    val hasConditions: Boolean
        get() = listOf(domains, ips, processes, protocols).any { values -> values.any(String::isNotBlank) } ||
            port.isNotBlank() || network.trim().lowercase() in setOf("tcp", "udp")
}

/** The rule line of a row, and the values that could not be written into it. */
class RoutingTableRowLine(val line: String, val unwritable: List<String>)

private enum class RowField { Address, Port, Network, Protocol }

private val AddressTypes = setOf(
    "DOMAIN", "DOMAIN-SUFFIX", "DOMAIN-KEYWORD", "DOMAIN-WILDCARD", "DOMAIN-SET", "GEOSITE",
    "IP-CIDR", "IP-CIDR6", "GEOIP", "PROCESS-NAME",
)

private fun String.rowField(): RowField? = when (this) {
    in AddressTypes -> RowField.Address
    "DST-PORT" -> RowField.Port
    "NETWORK" -> RowField.Network
    "PROTOCOL" -> RowField.Protocol
    else -> null
}

/** The rule as a table row, or null when its shape does not fit the table (it is then edited as a rule). */
fun ShadowrocketRule.toRoutingTableRow(): RoutingTableRow? {
    if (isFinal) return null
    val whole = condition
    val terms: List<ShadowrocketCondition> = when {
        whole == null -> listOf(Leaf(type.trim().uppercase(), value.trim()))
        whole.operator == And -> whole.children
        else -> listOf(whole)
    }

    val byField = mutableMapOf<RowField, List<Leaf>>()
    for (term in terms) {
        val leaves = when (term) {
            is Leaf -> listOf(term)
            is Group -> {
                if (term.operator != Or) return null
                term.children.map { it as? Leaf ?: return null }
            }
        }
        val field = leaves.map { it.type.rowField() ?: return null }.distinct().singleOrNull() ?: return null
        if (field in byField) return null
        byField[field] = leaves
    }
    if (byField.isEmpty()) return null

    val address = byField[RowField.Address].orEmpty()
    val networks = byField[RowField.Network].orEmpty().map { it.value.trim().lowercase() }.distinct()
    if (networks.any { it != "tcp" && it != "udp" }) return null

    return RoutingTableRow(
        name = remarks,
        enabled = !isDisabled,
        policy = policy,
        domains = address.filter { it.type in DomainTypes }.map(Leaf::toDomainToken),
        ips = address.filter { it.type in IpTypes }.map(Leaf::toIpToken),
        processes = address.filter { it.type == "PROCESS-NAME" }.map { it.value },
        port = byField[RowField.Port].orEmpty().joinToString(",") { it.value },
        network = networks.singleOrNull().orEmpty(),
        protocols = byField[RowField.Protocol].orEmpty().map { it.value },
        extraOption = option.orEmpty().split(',').map(String::trim)
            .filter { it.isNotEmpty() && !it.equals(DisabledRuleOption, ignoreCase = true) }
            .joinToString(", ").takeIf(String::isNotBlank),
    )
}

/** The line to store for [this] row, or null when no condition is left to write. */
fun RoutingTableRow.toShadowrocketLineOrNull(): RoutingTableRowLine? {
    val written = write()
    return written.line?.let { line -> RoutingTableRowLine(line, written.unwritable) }
}

/** The values of this row that cannot be written into a rule line, also when nothing else is left of the row. */
fun RoutingTableRow.unwritableValues(): List<String> = write().unwritable

private class RoutingTableRowWrite(val line: String?, val unwritable: List<String>)

private fun RoutingTableRow.write(): RoutingTableRowWrite {
    val unwritable = mutableListOf<String>()
    fun <T> List<String>.writeEach(convert: (String) -> T?): List<T> =
        map(String::trim).filter(String::isNotEmpty).distinct().mapNotNull { token ->
            convert(token).also { if (it == null) unwritable += token }
        }

    val addressLeaves = domains.writeEach(String::toDomainLeaf) + ips.writeEach(String::toIpLeaf) +
        processes.writeEach { name -> Leaf("PROCESS-NAME", name).takeIf { name.isSafeRuleValue() } }
    val portLeaves = port.split(',').writeEach { token ->
        Leaf("DST-PORT", token).takeIf { token.matches(PortToken) }
    }
    val networkValue = network.trim().lowercase()
    val networkLeaves = when (networkValue) {
        "", "tcp,udp", "udp,tcp" -> emptyList()
        "tcp", "udp" -> listOf(Leaf("NETWORK", networkValue))
        else -> emptyList<Leaf>().also { unwritable += network.trim() }
    }
    val protocolLeaves = protocols.map(String::lowercase).writeEach { token ->
        Leaf("PROTOCOL", token).takeIf { token.isSafeRuleValue() }
    }

    val terms = listOf(addressLeaves, portLeaves, networkLeaves, protocolLeaves)
        .filter(List<Leaf>::isNotEmpty)
        .map { leaves -> leaves.singleOrNull() ?: Group(Or, leaves) }
    if (terms.isEmpty()) return RoutingTableRowWrite(null, unwritable)

    val option = (extraOption.orEmpty().split(',').map(String::trim).filter(String::isNotEmpty) +
        listOfNotNull(DisabledRuleOption.takeIf { !enabled })).joinToString(", ").takeIf(String::isNotBlank)
    val single = terms.singleOrNull()
    val rule = when {
        single is Leaf -> ShadowrocketRule(0, single.type, single.value, policy, option, raw = "", remarks = name)
        else -> {
            val group = single as? Group ?: Group(And, terms)
            ShadowrocketRule(0, group.operator.keyword, group.toBodyText(), policy, option, raw = "", condition = group, remarks = name)
        }
    }
    return RoutingTableRowWrite(rule.toShadowrocketLine(), unwritable)
}

private val DomainTypes = setOf("DOMAIN", "DOMAIN-SUFFIX", "DOMAIN-KEYWORD", "DOMAIN-WILDCARD", "DOMAIN-SET", "GEOSITE")
private val IpTypes = setOf("IP-CIDR", "IP-CIDR6", "GEOIP")
private val PortToken = Regex("""\d{1,5}(-\d{1,5})?""")

// Commas and parentheses would split or close a rule line, so such values cannot be written.
private fun String.isSafeRuleValue(): Boolean = isNotBlank() && none { it == ',' || it == '(' || it == ')' }

private fun String.hasPrefixIgnoreCase(prefix: String) = startsWith(prefix, ignoreCase = true)

private fun Leaf.toDomainToken(): String = when (type) {
    "DOMAIN-SUFFIX" -> "domain:$value"
    "DOMAIN" -> "full:$value"
    // A bare entry is a substring match, so that is how a keyword is shown unless it would read as something else.
    "DOMAIN-KEYWORD" -> if (value.any { it == ':' || it == '*' || it == '?' }) "keyword:$value" else value
    "GEOSITE" -> if (value.hasPrefixIgnoreCase("geosite:") || value.hasPrefixIgnoreCase("ext:")) value else "geosite:$value"
    else -> value
}

private fun Leaf.toIpToken(): String = when {
    type == "GEOIP" && !value.hasPrefixIgnoreCase("geoip:") && !value.hasPrefixIgnoreCase("ext:") -> "geoip:$value"
    else -> value
}

private fun String.toDomainLeaf(): Leaf? {
    val leaf = when {
        hasPrefixIgnoreCase("geosite:") || hasPrefixIgnoreCase("ext:") || hasPrefixIgnoreCase("regexp:") -> Leaf("DOMAIN-SET", this)
        hasPrefixIgnoreCase("domain:") -> Leaf("DOMAIN-SUFFIX", substringAfter(':'))
        hasPrefixIgnoreCase("full:") -> Leaf("DOMAIN", substringAfter(':'))
        hasPrefixIgnoreCase("keyword:") -> Leaf("DOMAIN-KEYWORD", substringAfter(':'))
        '*' in this || '?' in this -> Leaf("DOMAIN-WILDCARD", this)
        // A bare entry is a substring match in Xray, not a suffix.
        else -> Leaf("DOMAIN-KEYWORD", this)
    }
    return leaf.takeIf { it.value.isSafeRuleValue() }
}

private fun String.toIpLeaf(): Leaf? {
    val leaf = when {
        hasPrefixIgnoreCase("geoip:") -> Leaf("GEOIP", substringAfter(':'))
        hasPrefixIgnoreCase("ext:") -> Leaf("GEOIP", this)
        isIpOrCidrAddress(this) -> Leaf(if (':' in this) "IP-CIDR6" else "IP-CIDR", this)
        else -> null
    }
    return leaf?.takeIf { it.value.isSafeRuleValue() }
}
