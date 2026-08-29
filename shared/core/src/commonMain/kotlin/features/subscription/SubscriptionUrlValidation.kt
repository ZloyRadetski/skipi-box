// Copyright 2026, Radetski
// SPDX-License-Identifier: GPL-3.0

package features.subscription

import io.ktor.http.Url

/** Allows the HTTP URLs historically accepted when a user enters a subscription manually. */
fun String.isValidManualSubscriptionUrl(): Boolean {
    return isValidSubscriptionUrl(ManualSubscriptionUrlSchemes)
}

fun String.isPlainHttpSubscriptionUrl(): Boolean {
    val url = toSubscriptionUrlOrNull() ?: return false
    return url.protocol.name.equals("http", ignoreCase = true)
}

/** Installation links must use HTTPS because they are received from external sources. */
fun String.isValidSubscriptionInstallUrl(): Boolean {
    return isValidSubscriptionUrl(HttpsSubscriptionUrlSchemes)
}

private fun String.isValidSubscriptionUrl(schemes: Set<String>): Boolean {
    val url = toSubscriptionUrlOrNull() ?: return false
    val scheme = url.protocol.name.lowercase()
    return url.host.isNotBlank() && scheme in schemes
}

private fun String.toSubscriptionUrlOrNull(): Url? {
    val value = trim()
    if (value.isBlank() || value.any(Char::isWhitespace)) return null
    val authorityWithPath = value.substringAfter("://", missingDelimiterValue = "")
    val authorityEnd = authorityWithPath.indexOfAny(charArrayOf('/', '?', '#'))
    val authority = if (authorityEnd < 0) authorityWithPath else authorityWithPath.substring(0, authorityEnd)
    if (authority.isBlank()) return null
    return runCatching { Url(value) }.getOrNull()
}

private val HttpsSubscriptionUrlSchemes = setOf("https")
private val ManualSubscriptionUrlSchemes = setOf("http", "https")
