// Copyright 2026, Radetski
// SPDX-License-Identifier: GPL-3.0

package features.subscription

import io.ktor.http.Url

fun String.isValidManualSubscriptionUrl(): Boolean {
    return isValidSubscriptionUrl(ManualSubscriptionUrlSchemes)
}

fun String.isPlainHttpSubscriptionUrl(): Boolean {
    val url = toSubscriptionUrlOrNull() ?: return false
    return url.protocol.name.equals("http", ignoreCase = true)
}

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
    return runCatching { Url(value) }.getOrNull()
}

private val HttpsSubscriptionUrlSchemes = setOf("https")
private val ManualSubscriptionUrlSchemes = setOf("http", "https")
