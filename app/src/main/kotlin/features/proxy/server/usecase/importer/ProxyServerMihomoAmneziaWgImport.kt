// Copyright 2026, Radetski
// SPDX-License-Identifier: GPL-3.0

package features.proxy.server.usecase.importer

import features.proxy.server.model.AmneziaWg
import utils.toCsvValues

/**
 * Imports an AmneziaWG proxy from Mihomo/Clash YAML format.
 *
 * Expected YAML keys (in addition to standard WG keys):
 *   amnezia-wg-option.jc, jmin, jmax, s1, s2, s3, s4, h1, h2, h3, h4, i1..i5
 */
internal fun MihomoYamlMap.toMihomoAmneziaWgProxyServer(): AmneziaWg {
    val peers = list("peers").orEmpty().mapNotNull { item -> item.asStringMap() }
    if (peers.size > 1) {
        unsupported("multiple AmneziaWG peers are not supported")
    }
    val peer = peers.firstOrNull()
    val endpoint = peer ?: this

    // AmneziaWG-specific obfuscation options may be nested under amnezia-wg-option
    val awgOptions = this["amnezia-wg-option"]?.asStringMap() ?: this

    return AmneziaWg(
        remarks = requiredString("name"),
        server = endpoint.requiredString("server"),
        port = endpoint.requiredString("port"),
        secretKey = requiredString("private-key", "privateKey"),
        publicKey = endpoint.requiredString("public-key", "publicKey"),
        preSharedKey = endpoint.string("pre-shared-key", "presharedkey", "preSharedKey").orEmpty(),
        reserved = endpoint.wireguardReservedString(),
        address = wireguardAddressString(),
        mtu = string("mtu") ?: "1420",
        jc = awgOptions.string("jc") ?: "4",
        jmin = awgOptions.string("jmin") ?: "40",
        jmax = awgOptions.string("jmax") ?: "70",
        s1 = awgOptions.string("s1") ?: "15",
        s2 = awgOptions.string("s2") ?: "30",
        s3 = awgOptions.string("s3") ?: "",
        s4 = awgOptions.string("s4") ?: "",
        h1 = awgOptions.string("h1") ?: "",
        h2 = awgOptions.string("h2") ?: "",
        h3 = awgOptions.string("h3") ?: "",
        h4 = awgOptions.string("h4") ?: "",
        i1 = awgOptions.string("i1") ?: "",
        i2 = awgOptions.string("i2") ?: "",
        i3 = awgOptions.string("i3") ?: "",
        i4 = awgOptions.string("i4") ?: "",
        i5 = awgOptions.string("i5") ?: "",
    )
}

private fun MihomoYamlMap.wireguardAddressString(): String {
    val addresses = listOfNotNull(
        string("ip")?.toWireguardCidrAddress(ipv6 = false),
        string("ipv6")?.toWireguardCidrAddress(ipv6 = true),
    )
    if (addresses.isEmpty()) {
        unsupported("AmneziaWG ip or ipv6 is required")
    }
    return addresses.joinToString(",")
}

private fun String.toWireguardCidrAddress(ipv6: Boolean): String {
    val value = trim()
    if ('/' in value) return value
    return if (ipv6) "$value/128" else "$value/32"
}

private fun MihomoYamlMap.wireguardReservedString(): String {
    val value = this["reserved"] ?: return "0,0,0"
    val parts = when (value) {
        is Iterable<*> -> value.mapNotNull { item -> item.scalarString() }
        else -> value.scalarString().toCsvValues()
    }.filter(String::isNotBlank)
    if (parts.isEmpty()) {
        return "0,0,0"
    }
    if (parts.size != 3 || parts.any { part -> part.toIntOrNull() == null }) {
        unsupported("AmneziaWG reserved must be three numeric bytes")
    }
    return parts.joinToString(",")
}
