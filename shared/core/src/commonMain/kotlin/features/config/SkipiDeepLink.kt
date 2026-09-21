// Copyright 2026, Radetski
// SPDX-License-Identifier: GPL-3.0

package features.config

import utils.decodeUrlComponentPreservingPlus

/** An action encoded by one of SKIPI's documented custom links. */
sealed interface SkipiDeepLink {
    data class Subscription(val url: String) : SkipiDeepLink
    data class ManualServer(val url: String) : SkipiDeepLink
    data class TrafficConfig(
        val content: String,
        val activate: Boolean,
        val sourceUrl: String = "",
    ) : SkipiDeepLink

    data object Connect : SkipiDeepLink
    data object Open : SkipiDeepLink
    data object Disconnect : SkipiDeepLink
    data object Close : SkipiDeepLink
    data object Toggle : SkipiDeepLink
}

/**
 * Parses only SKIPI's documented links; unrelated custom URI schemes are ignored.
 *
 * The parser operates on the original URI text so platform code only needs to
 * supply its URI representation. Payload percent-decoding deliberately keeps
 * literal plus signs intact, matching custom-link payload semantics.
 */
fun parseSkipiDeepLinkOrNull(rawUri: String): SkipiDeepLink? {
    return when (rawUri.skipiDeepLinkHostOrNull()) {
        "add" -> rawUri.rawPayloadAfterSkipiPrefix("skipi://add/")
            ?.toSkipiSubscriptionOrServerLink()

        "import" -> rawUri.rawPayloadAfterSkipiPrefix("skipi://import/")
            ?.toSkipiSubscriptionOrServerLink()

        "routing", "conf" -> rawUri.toSkipiTrafficConfigDeepLinkOrNull()
        "connect" -> SkipiDeepLink.Connect
        "open" -> SkipiDeepLink.Open
        "disconnect" -> SkipiDeepLink.Disconnect
        "close" -> SkipiDeepLink.Close
        "toggle" -> SkipiDeepLink.Toggle
        else -> null
    }
}

/** Decodes a Base64 payload embedded by a SKIPI link, if one is present. */
fun String.decodeSkipiPayload(): String? = decodeSkipiConfigPayloadOrNull()

private fun String.toSkipiTrafficConfigDeepLinkOrNull(): SkipiDeepLink.TrafficConfig? {
    val route = when {
        startsWith("skipi://routing/add/", ignoreCase = true) -> SkipiConfigLinkRoute(
            prefix = "skipi://routing/add/",
            activate = false,
        )

        startsWith("skipi://conf/add/", ignoreCase = true) -> SkipiConfigLinkRoute(
            prefix = "skipi://conf/add/",
            activate = false,
        )

        startsWith("skipi://routing/onadd/", ignoreCase = true) -> SkipiConfigLinkRoute(
            prefix = "skipi://routing/onadd/",
            activate = true,
        )

        startsWith("skipi://conf/onadd/", ignoreCase = true) -> SkipiConfigLinkRoute(
            prefix = "skipi://conf/onadd/",
            activate = true,
        )

        else -> return null
    }
    val payload = rawPayloadAfterSkipiPrefix(route.prefix) ?: return null
    val decoded = payload.decodeSkipiPayload() ?: payload.trim()
    val sourceUrl = decoded.takeIf(String::isHttpUrl).orEmpty()
    return SkipiDeepLink.TrafficConfig(
        content = decoded,
        activate = route.activate,
        sourceUrl = sourceUrl,
    )
}

private fun String.toSkipiSubscriptionOrServerLink(): SkipiDeepLink? {
    val decoded = decodeSkipiPayload() ?: trim()
    return when {
        decoded.isHttpUrl() -> SkipiDeepLink.Subscription(decoded)
        decoded.contains("://") -> SkipiDeepLink.ManualServer(decoded)
        else -> null
    }
}

private fun String.rawPayloadAfterSkipiPrefix(prefix: String): String? = takeIf {
    startsWith(prefix, ignoreCase = true)
}?.substring(prefix.length)
    ?.takeIf(String::isNotBlank)
    ?.decodeUrlComponentPreservingPlus()

private fun String.skipiDeepLinkHostOrNull(): String? {
    val schemePrefix = "skipi://"
    if (!startsWith(schemePrefix, ignoreCase = true)) return null
    return substring(schemePrefix.length)
        .substringBefore('/')
        .substringBefore('?')
        .substringBefore('#')
        .substringAfterLast('@')
        .substringBefore(':')
        .lowercase()
        .takeIf(String::isNotBlank)
}

private fun String.isHttpUrl(): Boolean = startsWith("http://", ignoreCase = true) ||
    startsWith("https://", ignoreCase = true)

private data class SkipiConfigLinkRoute(
    val prefix: String,
    val activate: Boolean,
)
