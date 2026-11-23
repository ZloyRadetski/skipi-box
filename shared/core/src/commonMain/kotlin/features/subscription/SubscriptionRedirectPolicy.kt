// Copyright 2026, Radetski
// SPDX-License-Identifier: GPL-3.0

package features.subscription

sealed interface SubscriptionRedirectDecision {
    /** Same site: the request may keep its device headers. */
    data object Follow : SubscriptionRedirectDecision

    /** Another site: follow, but never send the hardware id or age key there. */
    data object FollowWithoutIdentity : SubscriptionRedirectDecision

    data class Reject(val reason: String) : SubscriptionRedirectDecision
}

/**
 * Subscription URLs are attacker-influenced (deep links, pasted text), so a
 * redirect must not downgrade to cleartext, leave HTTP(S), or point the app at
 * the device's own services or the local network by address literal.
 */
fun decideSubscriptionRedirect(fromUrl: String, toUrl: String): SubscriptionRedirectDecision {
    val from = fromUrl.toHttpUrlPartsOrNull()
    val to = toUrl.toHttpUrlPartsOrNull()
        ?: return SubscriptionRedirectDecision.Reject("redirect target is not an http(s) URL")
    if (from != null && from.scheme == "https" && to.scheme != "https") {
        return SubscriptionRedirectDecision.Reject("redirect downgrades https to http")
    }
    val staysOnSameHost = from != null && from.host == to.host
    // A host the user picked themselves (a panel on the LAN) may redirect
    // within itself; reaching an internal address from anywhere else is the
    // request-forgery case this refuses.
    if (!staysOnSameHost && to.host.isInternalHostLiteral()) {
        return SubscriptionRedirectDecision.Reject("redirect points to an internal address")
    }
    return if (staysOnSameHost) {
        SubscriptionRedirectDecision.Follow
    } else {
        SubscriptionRedirectDecision.FollowWithoutIdentity
    }
}

private data class HttpUrlParts(val scheme: String, val host: String)

private fun String.toHttpUrlPartsOrNull(): HttpUrlParts? {
    val scheme = substringBefore("://", missingDelimiterValue = "").lowercase()
    if (scheme != "http" && scheme != "https") return null
    val authority = substringAfter("://")
        .substringBefore('/')
        .substringBefore('?')
        .substringBefore('#')
        .substringAfterLast('@')
    val host = if (authority.startsWith("[")) {
        authority.substringBefore(']').removePrefix("[")
    } else {
        authority.substringBefore(':')
    }.lowercase().trimEnd('.')
    if (host.isEmpty()) return null
    return HttpUrlParts(scheme, host)
}

private fun String.isInternalHostLiteral(): Boolean {
    if (this == "localhost" || endsWith(".localhost") || endsWith(".local") || endsWith(".internal")) return true
    if (contains(':')) {
        val bytes = parseIpv6LiteralOrNull() ?: return true // unparsable literal: refuse rather than guess
        return bytes.isInternalIpv6()
    }
    val v4 = parseIpv4LiteralOrNull() ?: return false // a hostname, not an address literal
    return v4.isInternalIpv4()
}

private fun IntArray.isInternalIpv4(): Boolean {
    val a = this[0]
    val b = this[1]
    return a == 0 || a == 10 || a == 127 || (a == 169 && b == 254) ||
        (a == 172 && b in 16..31) || (a == 192 && b == 168) || (a == 100 && b in 64..127)
}

private fun IntArray.isInternalIpv6(): Boolean {
    if (all { it == 0 }) return true // ::
    if (take(15).all { it == 0 } && this[15] == 1) return true // ::1
    if (this[0] == 0xfe && (this[1] and 0xc0) == 0x80) return true // fe80::/10
    if ((this[0] and 0xfe) == 0xfc) return true // fc00::/7
    // IPv4-mapped (::ffff:a.b.c.d) and IPv4-compatible (::a.b.c.d) carry an IPv4 address.
    val mapped = take(10).all { it == 0 } && this[10] == 0xff && this[11] == 0xff
    val compatible = take(12).all { it == 0 }
    if (mapped || compatible) return intArrayOf(this[12], this[13], this[14], this[15]).isInternalIpv4()
    return false
}

/** inet_aton-style IPv4: 1-4 parts, decimal, 0x hex or leading-0 octal; null when this is not a numeric literal. */
private fun String.parseIpv4LiteralOrNull(): IntArray? {
    val parts = split('.')
    if (parts.size !in 1..4) return null
    val values = parts.map { it.parseIpv4PartOrNull() ?: return null }
    for (i in 0 until values.lastIndex) if (values[i] > 255L) return null
    val lastBytes = 5 - parts.size
    val last = values.last()
    if (last >= (1L shl (8 * lastBytes))) return null
    val result = IntArray(4)
    for (i in 0 until values.lastIndex) result[i] = values[i].toInt()
    for (j in 0 until lastBytes) result[4 - 1 - j] = ((last shr (8 * j)) and 0xff).toInt()
    return result
}

private fun String.parseIpv4PartOrNull(): Long? {
    if (isEmpty()) return null
    val (digits, radix) = when {
        startsWith("0x", ignoreCase = true) -> substring(2) to 16
        length > 1 && startsWith("0") -> substring(1) to 8
        else -> this to 10
    }
    if (digits.isEmpty()) return if (radix == 16) null else 0L
    return digits.toLongOrNull(radix)?.takeIf { it >= 0 }
}

/** Canonical 16 bytes of an IPv6 literal (any `::` / mixed IPv4 form), or null when malformed. */
private fun String.parseIpv6LiteralOrNull(): IntArray? {
    var text = substringBefore('%') // zone id
    if (text.isEmpty()) return null
    var tail: IntArray? = null
    val lastColon = text.lastIndexOf(':')
    if (text.substring(lastColon + 1).contains('.')) {
        tail = text.substring(lastColon + 1).parseIpv4LiteralOrNull() ?: return null
        text = text.substring(0, lastColon + 1) + "0:0"
    }
    val doubleColon = text.indexOf("::")
    if (doubleColon >= 0 && text.indexOf("::", doubleColon + 1) >= 0) return null
    fun groups(part: String): List<Int>? {
        if (part.isEmpty()) return emptyList()
        return part.split(':').map { g ->
            if (g.isEmpty() || g.length > 4) return null
            g.toIntOrNull(16) ?: return null
        }
    }
    val groupValues = if (doubleColon >= 0) {
        val head = groups(text.substring(0, doubleColon)) ?: return null
        val rest = groups(text.substring(doubleColon + 2)) ?: return null
        val missing = 8 - head.size - rest.size
        if (missing < 1) return null
        head + List(missing) { 0 } + rest
    } else {
        groups(text)?.takeIf { it.size == 8 } ?: return null
    }
    val bytes = IntArray(16)
    groupValues.forEachIndexed { i, g ->
        bytes[2 * i] = g shr 8
        bytes[2 * i + 1] = g and 0xff
    }
    if (tail != null) for (i in 0 until 4) bytes[12 + i] = tail[i]
    return bytes
}
