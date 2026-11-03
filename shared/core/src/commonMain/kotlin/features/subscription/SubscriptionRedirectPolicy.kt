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
        return this == "::" || this == "::1" || startsWith("fe8") || startsWith("fe9") ||
            startsWith("fea") || startsWith("feb") || startsWith("fc") || startsWith("fd") ||
            startsWith("::ffff:")
    }
    val octets = split('.')
    if (octets.size != 4) return false
    val numbers = octets.map { it.toIntOrNull() ?: return false }
    if (numbers.any { it !in 0..255 }) return false
    val (a, b) = numbers
    return a == 0 || a == 10 || a == 127 || (a == 169 && b == 254) ||
        (a == 172 && b in 16..31) || (a == 192 && b == 168) || (a == 100 && b in 64..127)
}
