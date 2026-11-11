// Copyright 2026, Radetski
// SPDX-License-Identifier: GPL-3.0

package features.proxy.server.usecase.importer

import utils.toCsvValues

// What WireGuard and AmneziaWG nodes share: both name an address and three reserved bytes the same way.
// [protocol] is only the name an error gives for the node.

/** The node's `ip` and `ipv6` as one comma-separated list of CIDR addresses. */
internal fun MihomoYamlMap.wireguardAddressString(protocol: String): String {
    val addresses = listOfNotNull(
        string("ip")?.toWireguardCidrAddress(ipv6 = false),
        string("ipv6")?.toWireguardCidrAddress(ipv6 = true),
    )
    if (addresses.isEmpty()) {
        unsupported("$protocol ip or ipv6 is required")
    }
    return addresses.joinToString(",")
}

/** An address without a prefix is a single host: /32 for IPv4, /128 for IPv6. */
internal fun String.toWireguardCidrAddress(ipv6: Boolean): String {
    val value = trim()
    if ('/' in value) return value
    return if (ipv6) "$value/128" else "$value/32"
}

/** The node's `reserved` bytes as "a,b,c"; absent or empty means "0,0,0". */
internal fun MihomoYamlMap.wireguardReservedString(protocol: String): String {
    val value = this["reserved"] ?: return "0,0,0"
    val parts = when (value) {
        is Iterable<*> -> value.mapNotNull { item -> item.scalarString() }
        else -> value.scalarString().toCsvValues()
    }.filter(String::isNotBlank)
    if (parts.isEmpty()) {
        return "0,0,0"
    }
    if (parts.size != 3 || parts.any { part -> part.toIntOrNull() == null }) {
        unsupported("$protocol reserved must be three numeric bytes")
    }
    return parts.joinToString(",")
}
