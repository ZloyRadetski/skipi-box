// Copyright 2026, Radetski
// SPDX-License-Identifier: GPL-3.0

package features.subscription

import android.net.Uri
import android.util.Base64
import features.subscription.runtime.SubscriptionFetchResponse
import java.nio.ByteBuffer
import java.nio.charset.CodingErrorAction
import java.nio.charset.StandardCharsets

/** Embedded traffic config payload discovered from subscription headers or body. */
internal data class SubscriptionEmbeddedConfig(
    val payload: String,
    val activate: Boolean,
    val isUrl: Boolean,
)

/** Metadata attached to a subscription response by common proxy subscription servers. */
internal data class SubscriptionMetadata(
    val profileTitle: String? = null,
    val profileDescription: String? = null,
    val announce: String? = null,
    val supportUrl: String? = null,
    val supportEmail: String? = null,
    val profileWebPageUrl: String? = null,
    val announceUrl: String? = null,
    val userInfoReceived: Boolean = false,
    val trafficUploadBytes: Long = -1L,
    val trafficDownloadBytes: Long = -1L,
    val trafficTotalBytes: Long = -1L,
    val trafficExpireAtSeconds: Long = -1L,
    /** Server-supplied interval in hours, used unless the user changes it manually. */
    val profileUpdateIntervalHours: String? = null,
    /** Optional embedded or remote traffic configuration discovered in response. */
    val embeddedConfig: SubscriptionEmbeddedConfig? = null,
)

internal fun SubscriptionFetchResponse.subscriptionMetadata(): SubscriptionMetadata {
    val normalizedHeaders = headers.mapKeys { (name, _) -> name.lowercase() }
    val userInfo = normalizedHeaders[SubscriptionUserInfoHeader]
        ?: normalizedHeaders[SubscriptionUserInfoHeaderWithHash]
    val values = userInfo?.split(';')
        ?.associate { entry ->
            val (key, value) = entry.trim().split('=', limit = 2).let { parts ->
                parts.firstOrNull().orEmpty().trim().lowercase() to parts.getOrElse(1) { "" }.trim()
            }
            key to value
        }
        .orEmpty()

    val bodyComments = parseBodyCommentMetadata(body)

    val profileTitle = normalizedHeaders[ProfileTitleHeader]?.decodeSubscriptionHeaderValue()
        ?: normalizedHeaders[SubscriptionNameHeader]?.decodeSubscriptionHeaderValue()
        ?: bodyComments[ProfileTitleHeader]?.decodeSubscriptionHeaderValue()

    val profileDescription = normalizedHeaders[ProfileDescriptionHeader]?.decodeSubscriptionHeaderValue()
        ?: bodyComments[ProfileDescriptionHeader]?.decodeSubscriptionHeaderValue()

    val announce = normalizedHeaders[AnnounceHeader]?.decodeSubscriptionHeaderValue()
        ?: bodyComments[AnnounceHeader]?.decodeSubscriptionHeaderValue()

    val supportUrl = normalizedHeaders[SupportUrlHeader]?.trim()
        ?.takeIf(String::isNotEmpty)
        ?: bodyComments[SupportUrlHeader]?.trim()?.takeIf(String::isNotEmpty)

    val supportEmail = normalizedHeaders[SupportEmailHeader]?.trim()
        ?.takeIf(String::isNotEmpty)
        ?: bodyComments[SupportEmailHeader]?.trim()?.takeIf(String::isNotEmpty)

    val profileWebPageUrl = normalizedHeaders[ProfileWebPageUrlHeader]?.trim()
        ?.takeIf(String::isNotEmpty)
        ?: normalizedHeaders[HomepageHeader]?.trim()?.takeIf(String::isNotEmpty)
        ?: bodyComments[ProfileWebPageUrlHeader]?.trim()?.takeIf(String::isNotEmpty)
        ?: bodyComments[HomepageHeader]?.trim()?.takeIf(String::isNotEmpty)

    val announceUrl = normalizedHeaders[AnnounceUrlHeader]?.trim()
        ?.takeIf(String::isNotEmpty)
        ?: bodyComments[AnnounceUrlHeader]?.trim()?.takeIf(String::isNotEmpty)

    val updateIntervalHours = normalizedHeaders[ProfileUpdateIntervalHeader]
        ?.trim()
        ?.toIntOrNull()
        ?.takeIf { it > 0 }
        ?.toString()
        ?: bodyComments[ProfileUpdateIntervalHeader]
            ?.trim()
            ?.toIntOrNull()
            ?.takeIf { it > 0 }
            ?.toString()

    val embeddedConfig = parseSubscriptionEmbeddedConfig(normalizedHeaders, bodyComments, body)

    return SubscriptionMetadata(
        profileTitle = profileTitle,
        profileDescription = profileDescription,
        announce = announce,
        supportUrl = supportUrl,
        supportEmail = supportEmail,
        profileWebPageUrl = profileWebPageUrl,
        announceUrl = announceUrl,
        userInfoReceived = userInfo != null,
        trafficUploadBytes = values["upload"].toSubscriptionLong(),
        trafficDownloadBytes = values["download"].toSubscriptionLong(),
        trafficTotalBytes = values["total"].toSubscriptionLong(),
        trafficExpireAtSeconds = values["expire"].toSubscriptionLong(),
        profileUpdateIntervalHours = updateIntervalHours,
        embeddedConfig = embeddedConfig,
    )
}

private fun parseBodyCommentMetadata(body: String): Map<String, String> {
    val results = mutableMapOf<String, String>()
    body.lineSequence().forEach { line ->
        val trimmed = line.trim()
        if (trimmed.startsWith("#") && trimmed.contains(':')) {
            val withoutHash = trimmed.removePrefix("#").trim()
            val key = withoutHash.substringBefore(':').trim().lowercase()
            val value = withoutHash.substringAfter(':').trim()
            if (key.isNotEmpty() && value.isNotEmpty() && !results.containsKey(key)) {
                results[key] = value
            }
        }
    }
    return results
}

private fun parseSubscriptionEmbeddedConfig(
    headers: Map<String, String>,
    bodyComments: Map<String, String>,
    body: String,
): SubscriptionEmbeddedConfig? {
    // 1. Check response headers
    val autoroutingHeader = headers[AutoroutingHeader]?.trim()?.takeIf(String::isNotEmpty)
        ?: bodyComments[AutoroutingHeader]?.trim()?.takeIf(String::isNotEmpty)
    if (autoroutingHeader != null) {
        val fromScheme = autoroutingHeader.toSubscriptionEmbeddedConfigOrNull()
        if (fromScheme != null) return fromScheme
        val isUrl = autoroutingHeader.startsWith("http://", ignoreCase = true) ||
            autoroutingHeader.startsWith("https://", ignoreCase = true)
        return SubscriptionEmbeddedConfig(payload = autoroutingHeader, activate = true, isUrl = isUrl)
    }

    val routingHeader = headers[RoutingHeader]?.trim()?.takeIf(String::isNotEmpty)
        ?: headers[SkipiConfigHeader]?.trim()?.takeIf(String::isNotEmpty)
        ?: headers[ConfigHeader]?.trim()?.takeIf(String::isNotEmpty)
        ?: bodyComments[RoutingHeader]?.trim()?.takeIf(String::isNotEmpty)
        ?: bodyComments[SkipiConfigHeader]?.trim()?.takeIf(String::isNotEmpty)
        ?: bodyComments[ConfigHeader]?.trim()?.takeIf(String::isNotEmpty)
    if (routingHeader != null) {
        val fromScheme = routingHeader.toSubscriptionEmbeddedConfigOrNull()
        if (fromScheme != null) return fromScheme
        val isUrl = routingHeader.startsWith("http://", ignoreCase = true) ||
            routingHeader.startsWith("https://", ignoreCase = true)
        return SubscriptionEmbeddedConfig(payload = routingHeader, activate = true, isUrl = isUrl)
    }

    // 2. Check body lines
    body.lineSequence().forEach { line ->
        val trimmed = line.trim()
        val embedded = trimmed.toSubscriptionEmbeddedConfigOrNull()
        if (embedded != null) {
            return embedded
        }
    }

    return null
}

private fun String.toSubscriptionEmbeddedConfigOrNull(): SubscriptionEmbeddedConfig? {
    val trimmed = trim()
    if (trimmed.isEmpty()) return null

    val prefixes = listOf(
        "skipi://conf/onadd/" to true,
        "skipi://conf/add/" to false,
        "skipi://routing/onadd/" to true,
        "skipi://routing/add/" to false,
        "://autorouting/onadd/" to true,
        "://autorouting/add/" to false,
        "://routing/onadd/" to true,
        "://routing/add/" to false,
        "://onadd/" to true,
        "://routing/" to true,
    )

    for ((prefix, activate) in prefixes) {
        if (trimmed.startsWith(prefix, ignoreCase = true)) {
            val payload = trimmed.substring(prefix.length).trim()
            if (payload.isNotEmpty()) {
                val isUrl = payload.startsWith("http://", ignoreCase = true) ||
                    payload.startsWith("https://", ignoreCase = true)
                return SubscriptionEmbeddedConfig(
                    payload = payload,
                    activate = activate,
                    isUrl = isUrl,
                )
            }
        }
    }

    if (trimmed.startsWith("http://", ignoreCase = true) || trimmed.startsWith("https://", ignoreCase = true)) {
        return SubscriptionEmbeddedConfig(
            payload = trimmed,
            activate = true,
            isUrl = true,
        )
    }

    return null
}

private fun String?.toSubscriptionLong(): Long {
    return this?.toLongOrNull()?.takeIf { it >= 0L } ?: -1L
}

private fun String.decodeSubscriptionHeaderValue(): String {
    val raw = trim()
    if (raw.isEmpty()) return ""
    val unquoted = raw.removeSurrounding("\"")
    val explicitlyEncoded = unquoted.startsWith("base64:", ignoreCase = true) ||
        unquoted.startsWith("base64,", ignoreCase = true)
    // Plain-text profile names commonly contain spaces.  Removing whitespace first made
    // values such as "TORVALDS VPN" look like Base64 and displayed them incorrectly.
    if (!explicitlyEncoded && unquoted.any(Char::isWhitespace)) return raw
    val encoded = unquoted
        .removeBase64Prefix()
        .let(Uri::decode)
        .filterNot(Char::isWhitespace)
    if (!encoded.isBase64HeaderValue()) return raw
    val padded = encoded.padEnd((encoded.length + 3) / 4 * 4, '=')
    return listOf(Base64.DEFAULT, Base64.URL_SAFE or Base64.NO_WRAP)
        .firstNotNullOfOrNull { flags ->
            runCatching {
                StandardCharsets.UTF_8.newDecoder()
                    .onMalformedInput(CodingErrorAction.REPORT)
                    .onUnmappableCharacter(CodingErrorAction.REPORT)
                    .decode(ByteBuffer.wrap(Base64.decode(padded, flags)))
                    .toString()
            }.getOrNull()?.takeIf(String::isReadableSubscriptionHeaderText)
        }
        ?: raw
}

private fun String.removeBase64Prefix(): String {
    return when {
        startsWith("base64:", ignoreCase = true) -> substringAfter(':')
        startsWith("base64,", ignoreCase = true) -> substringAfter(',')
        else -> this
    }
}

private fun String.isBase64HeaderValue(): Boolean {
    if (length % 4 == 1) return false
    return all { character ->
        character.isLetterOrDigit() || character in "+/=_-"
    }
}

private fun String.isReadableSubscriptionHeaderText(): Boolean {
    return all { character ->
        !character.isISOControl() || character in "\n\r\t"
    }
}

private const val ProfileTitleHeader = "profile-title"
private const val SubscriptionNameHeader = "subscription-name"
private const val ProfileDescriptionHeader = "profile-description"
private const val AnnounceHeader = "announce"
private const val AnnounceUrlHeader = "announce-url"
private const val SupportUrlHeader = "support-url"
private const val SupportEmailHeader = "support-email"
private const val ProfileWebPageUrlHeader = "profile-web-page-url"
private const val HomepageHeader = "homepage"
private const val ProfileUpdateIntervalHeader = "profile-update-interval"
private const val SubscriptionUserInfoHeader = "subscription-userinfo"
private const val SubscriptionUserInfoHeaderWithHash = "#subscription-userinfo"
private const val AutoroutingHeader = "autorouting"
private const val RoutingHeader = "routing"
private const val SkipiConfigHeader = "skipi-config"
private const val ConfigHeader = "config"
