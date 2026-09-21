// Copyright 2026, Radetski
// SPDX-License-Identifier: GPL-3.0

package features.config

import engine.network.isIpOrCidrAddress
import features.routing.model.RouteRule

/**
 * Converts one portable Shadowrocket rule into the normalized Xray routing
 * representation. The caller resolves policy names because groups and server
 * tags are owned by its current runtime.
 */
fun ShadowrocketRule.toXrayRouteRule(
    id: Int,
    resolvePolicy: (String) -> String,
): RouteRule? {
    val cleanValue = value.trim()
    val ruleType = type.trim().uppercase()
    val domain = when (ruleType) {
        "DOMAIN" -> listOf("full:$cleanValue")
        "DOMAIN-SUFFIX" -> listOf("domain:$cleanValue")
        "DOMAIN-KEYWORD" -> listOf("keyword:$cleanValue")
        "DOMAIN-WILDCARD" -> listOf("regexp:${cleanValue.shadowrocketWildcardToRegex()}")
        // The value already carries its Xray form ("geosite:x"/"ext:file:tag").
        "DOMAIN-SET" -> listOf(cleanValue)
        "GEOSITE" -> listOf(
            if (cleanValue.startsWith("geosite:", ignoreCase = true) || cleanValue.startsWith("ext:", ignoreCase = true)) {
                cleanValue
            } else {
                "geosite:$cleanValue"
            },
        )

        "RULE-SET" -> {
            if (cleanValue.startsWith("http://", ignoreCase = true) || cleanValue.startsWith("https://", ignoreCase = true)) {
                emptyList()
            } else if (cleanValue.startsWith("geoip:", ignoreCase = true) || isIpOrCidrAddress(cleanValue)) {
                emptyList()
            } else {
                listOf(
                    if (cleanValue.startsWith("geosite:", ignoreCase = true) ||
                        cleanValue.startsWith("ext:", ignoreCase = true) ||
                        cleanValue.startsWith("domain:") ||
                        cleanValue.startsWith("full:") ||
                        cleanValue.startsWith("keyword:") ||
                        cleanValue.startsWith("regexp:")
                    ) {
                        cleanValue
                    } else {
                        "geosite:$cleanValue"
                    },
                )
            }
        }

        else -> emptyList()
    }
    val ip = when (ruleType) {
        "IP-CIDR", "IP-CIDR6" -> listOf(cleanValue).filter(::isIpOrCidrAddress)
        "IP-ASN" -> {
            val asn = cleanValue.removePrefix("AS").removePrefix("as").trim()
            if (cleanValue.startsWith("geoip:", ignoreCase = true) || cleanValue.startsWith("ext:", ignoreCase = true)) {
                listOf(cleanValue)
            } else if (asn.isNotEmpty() && asn.all(Char::isDigit)) {
                listOf("geoip:as$asn")
            } else {
                emptyList()
            }
        }

        "GEOIP" -> listOf(
            if (cleanValue.startsWith("geoip:", ignoreCase = true) || cleanValue.startsWith("ext:", ignoreCase = true)) {
                cleanValue
            } else {
                "geoip:$cleanValue"
            },
        )

        "RULE-SET" -> {
            if (cleanValue.startsWith("http://", ignoreCase = true) || cleanValue.startsWith("https://", ignoreCase = true)) {
                emptyList()
            } else if (cleanValue.startsWith("geoip:", ignoreCase = true) ||
                cleanValue.startsWith("ext:", ignoreCase = true) ||
                isIpOrCidrAddress(cleanValue)
            ) {
                listOf(cleanValue)
            } else {
                emptyList()
            }
        }

        else -> emptyList()
    }
    val process = if (ruleType == "PROCESS-NAME") listOf(cleanValue) else emptyList()
    val port = cleanValue.takeIf { ruleType == "DST-PORT" }.orEmpty()
    val network = cleanValue.takeIf { ruleType == "NETWORK" }.orEmpty()
    val protocol = cleanValue.takeIf { ruleType == "PROTOCOL" }.orEmpty()

    if (domain.isEmpty() && ip.isEmpty() && process.isEmpty() && port.isBlank() && network.isBlank() && protocol.isBlank()) {
        return null
    }
    return RouteRule(
        id = id,
        remarks = "$type,$value",
        outboundTag = resolvePolicy(policy),
        domain = domain,
        ip = ip,
        process = process,
        port = port,
        protocol = protocol,
        network = network,
    )
}

private fun String.shadowrocketWildcardToRegex(): String = buildString {
    append('^')
    this@shadowrocketWildcardToRegex.forEach { char ->
        when (char) {
            '*' -> append(".*")
            '?' -> append('.')
            '.', '+', '(', ')', '[', ']', '{', '}', '^', '$', '|', '\\' -> append('\\').append(char)
            else -> append(char)
        }
    }
    append('$')
}
